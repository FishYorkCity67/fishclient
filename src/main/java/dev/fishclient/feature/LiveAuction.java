package dev.fishclient.feature;

import com.google.gson.reflect.TypeToken;
import dev.fishclient.config.FishConfig;
import dev.fishclient.config.Json;
import dev.fishclient.ui.Sounds;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * A stream auction: while it runs, every payment that reaches the player counts as a bid, and whoever paid the
 * most wins. The item stays with the streamer until they hand it over. Nothing here moves money on its own: a
 * refund only goes out when the player confirms it on its button.
 */
public final class LiveAuction {
	/** One item of the lot, kept by id so history entries can still show an icon. */
	public static final class Lot {
		public String item = "minecraft:air";
		public int count = 1;
		public String name = "";

		public ItemStack stack() {
			Identifier id = Identifier.tryParse(this.item);
			Item value = id != null ? BuiltInRegistries.ITEM.getValue(id) : Items.AIR;
			return new ItemStack(value, Math.max(1, Math.min(this.count, 99)));
		}

		static Lot of(ItemStack stack) {
			Lot lot = new Lot();
			lot.item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			lot.count = stack.getCount();
			lot.name = stack.getHoverName().getString();
			return lot;
		}
	}

	/** Everything a bidder paid during one auction. */
	public static final class Bid {
		public String player = "";
		public double total;
		public double highest;
		public int payments;
		/** When this bid last went up, used to break ties in favour of whoever got there first. */
		public long reachedAt;

		/** The amount that competes, depending on whether payments are added up. */
		public double value(boolean sum) {
			return sum ? this.total : this.highest;
		}
	}

	public static final class Refund {
		public String player = "";
		public double amount;
		public boolean late;
		public boolean sent;
	}

	public static final class Settings {
		public List<Lot> lots = new ArrayList<>();
		public String title = "";
		public String quantity = "";
		public double minBid;
		public double worth;
		public String note = "";
		public int seconds = 60;
	}

	public static final class Result {
		public long time;
		public Settings settings = new Settings();
		public boolean summed = true;
		public boolean cancelled;
		@Nullable
		public String winner;
		public double price;
		public List<Bid> bids = new ArrayList<>();
		public List<Refund> refunds = new ArrayList<>();
	}

	private static final int MAX_HISTORY = 100;
	/** Payments this long after the end are still listed for refunding. */
	private static final long LATE_WINDOW_MS = 60_000L;
	private static final long REFUND_SPACING_MS = 1500L;

	@Nullable
	private static Settings running;
	private static long startedAt;
	private static long endsAt;
	private static boolean summed = true;
	private static final Map<String, Bid> BIDS = new LinkedHashMap<>();
	private static long lastBidAt;
	@Nullable
	private static String lastBidder;
	private static int lastTickSecond = -1;
	@Nullable
	private static Result lastResult;
	private static long lastEndedAt;
	private static List<Result> history = new ArrayList<>();
	private static final Deque<Refund> REFUND_QUEUE = new ArrayDeque<>();
	@Nullable
	private static Result refundQueueOwner;
	private static long nextRefundAt;

	private static Path path() {
		return Json.file("live_auctions.json");
	}

	public static void load() {
		List<Result> saved = Json.read(path(), new TypeToken<List<Result>>() { }.getType());
		history = saved != null ? saved : new ArrayList<>();
		lastResult = history.isEmpty() ? null : history.getFirst();
	}

	private static void save() {
		Json.write(path(), history);
	}

	// ---- Running an auction ----

	public static boolean running() {
		return running != null;
	}

	@Nullable
	public static Settings current() {
		return running;
	}

	public static void start(Settings settings) {
		running = settings;
		summed = FishConfig.get().auctionSumPayments;
		BIDS.clear();
		lastBidder = null;
		lastBidAt = 0L;
		lastTickSecond = -1;
		startedAt = Util.getMillis();
		endsAt = startedAt + Math.max(5, settings.seconds) * 1000L;
		REFUND_QUEUE.clear();
		if (FishConfig.get().auctionSounds) {
			Sounds.play(Sounds.AUCTION_START, 1f, 0.6f);
		}
	}

	/** Builds the settings for a new auction from the selected stacks. */
	public static Settings settings(List<ItemStack> stacks, String title, String quantity, double minBid, double worth, String note, int seconds) {
		Settings s = new Settings();
		for (ItemStack stack : stacks) {
			if (!stack.isEmpty()) {
				s.lots.add(Lot.of(stack));
			}
		}
		s.title = title.trim();
		s.quantity = quantity.trim();
		s.minBid = minBid;
		s.worth = worth;
		s.note = note.trim();
		s.seconds = seconds;
		return s;
	}

	public static long startedAt() {
		return startedAt;
	}

	public static long remainingMs() {
		return running != null ? Math.max(0L, endsAt - Util.getMillis()) : 0L;
	}

	public static long totalMs() {
		return running != null ? Math.max(1L, endsAt - startedAt) : 1L;
	}

	public static void addTime(int seconds) {
		if (running != null) {
			endsAt += seconds * 1000L;
		}
	}

	public static boolean summed() {
		return summed;
	}

	public static long lastBidAt() {
		return lastBidAt;
	}

	@Nullable
	public static String lastBidder() {
		return lastBidder;
	}

	/** Bids, highest first; ties go to whoever reached the amount first. */
	public static List<Bid> ranking() {
		return rank(new ArrayList<>(BIDS.values()), summed);
	}

	private static List<Bid> rank(List<Bid> bids, boolean sum) {
		bids.sort(Comparator.comparingDouble((Bid b) -> b.value(sum)).reversed().thenComparingLong(b -> b.reachedAt));
		return bids;
	}

	public static boolean eligible(Bid bid) {
		Settings s = running != null ? running : lastResult != null ? lastResult.settings : null;
		return s == null || bid.value(summed) >= s.minBid;
	}

	@Nullable
	public static Bid leader() {
		for (Bid bid : ranking()) {
			if (eligible(bid)) {
				return bid;
			}
		}
		return null;
	}

	/** Called for every recognized incoming payment. */
	public static void onPayment(String player, double amount) {
		long now = Util.getMillis();
		if (running == null) {
			// Money that arrives just after the end still has to be given back.
			if (lastResult != null && now - lastEndedAt < LATE_WINDOW_MS) {
				Refund late = new Refund();
				late.player = player;
				late.amount = amount;
				late.late = true;
				lastResult.refunds.add(late);
				save();
			}
			return;
		}
		Bid bid = BIDS.computeIfAbsent(player.toLowerCase(Locale.ROOT), k -> {
			Bid b = new Bid();
			b.player = player;
			return b;
		});
		double before = bid.value(summed);
		bid.total += amount;
		bid.highest = Math.max(bid.highest, amount);
		bid.payments++;
		if (bid.value(summed) > before) {
			bid.reachedAt = now;
		}
		lastBidAt = now;
		lastBidder = bid.player;
		FishConfig config = FishConfig.get();
		if (config.auctionExtend && endsAt - now < config.auctionExtendSeconds * 1000L) {
			endsAt = now + config.auctionExtendSeconds * 1000L;
		}
		if (config.auctionSounds) {
			Sounds.play(Sounds.BID, 1f, 0.7f);
		}
	}

	public static void tick(Minecraft mc) {
		if (running != null) {
			long remaining = remainingMs();
			if (remaining <= 0L) {
				finish(false);
			} else if (remaining <= 5000L && FishConfig.get().auctionSounds) {
				int second = (int) (remaining / 1000L);
				if (second != lastTickSecond) {
					lastTickSecond = second;
					Sounds.play(Sounds.TICK, 1f, 0.5f);
				}
			}
		}
		sendQueuedRefund(mc);
	}

	public static void endNow() {
		if (running != null) {
			finish(false);
		}
	}

	public static void cancel() {
		if (running != null) {
			finish(true);
		}
	}

	private static void finish(boolean cancelled) {
		Settings settings = running;
		if (settings == null) {
			return;
		}
		Result result = new Result();
		result.time = System.currentTimeMillis();
		result.settings = settings;
		result.summed = summed;
		result.cancelled = cancelled;
		List<Bid> ranked = ranking();
		result.bids = ranked;
		Bid winner = null;
		if (!cancelled) {
			for (Bid bid : ranked) {
				if (bid.value(summed) >= settings.minBid) {
					winner = bid;
					break;
				}
			}
		}
		if (winner != null) {
			result.winner = winner.player;
			result.price = winner.value(summed);
		}
		for (Bid bid : ranked) {
			// The winner keeps paying their winning amount; anything above it, and everyone else's money, goes back.
			double keep = bid == winner ? result.price : 0d;
			double back = bid.total - keep;
			if (back > 0.5d) {
				Refund refund = new Refund();
				refund.player = bid.player;
				refund.amount = back;
				result.refunds.add(refund);
			}
		}
		running = null;
		lastResult = result;
		lastEndedAt = Util.getMillis();
		history.addFirst(result);
		if (history.size() > MAX_HISTORY) {
			history.removeLast();
		}
		save();
		if (FishConfig.get().auctionSounds) {
			Sounds.play(cancelled ? Sounds.MENU_CLOSE : Sounds.AUCTION_END, 1f, 0.8f);
		}
	}

	// ---- After the auction ----

	@Nullable
	public static Result lastResult() {
		return lastResult;
	}

	public static long lastEndedAt() {
		return lastEndedAt;
	}

	/** Whether the winner card should still be on screen. */
	public static boolean showingWinner() {
		return running == null && lastResult != null && lastEndedAt > 0L
			&& Util.getMillis() - lastEndedAt < FishConfig.get().auctionWinnerSeconds * 1000L;
	}

	public static void hideWinner() {
		lastEndedAt = 0L;
	}

	public static List<Result> history() {
		return history;
	}

	public static void remove(Result result) {
		history.remove(result);
		if (result == lastResult) {
			lastResult = history.isEmpty() ? null : history.getFirst();
		}
		save();
	}

	public static String payCommand(Refund refund) {
		FishConfig.ServerPatterns rules = ServerRules.current();
		String template = rules != null && !rules.payCommand.isBlank() ? rules.payCommand : "/pay {player} {amount}";
		return template.replace("{player}", refund.player).replace("{amount}", Long.toString(Math.round(refund.amount)));
	}

	/** Sends one refund now. Only ever called from a confirmed button press. */
	public static void refund(Refund refund) {
		Minecraft mc = Minecraft.getInstance();
		if (refund.sent || mc.getConnection() == null) {
			return;
		}
		String command = payCommand(refund);
		mc.getConnection().sendCommand(command.startsWith("/") ? command.substring(1) : command);
		refund.sent = true;
		save();
	}

	/** Queues every open refund of a result; they go out one by one so the server does not see a burst. */
	public static void refundAll(Result result) {
		REFUND_QUEUE.clear();
		refundQueueOwner = result;
		for (Refund refund : result.refunds) {
			if (!refund.sent) {
				REFUND_QUEUE.add(refund);
			}
		}
		nextRefundAt = 0L;
	}

	public static int queuedRefunds() {
		return REFUND_QUEUE.size();
	}

	private static void sendQueuedRefund(Minecraft mc) {
		if (REFUND_QUEUE.isEmpty() || mc.getConnection() == null) {
			return;
		}
		long now = Util.getMillis();
		if (now < nextRefundAt) {
			return;
		}
		Refund next = REFUND_QUEUE.pollFirst();
		if (next != null && refundQueueOwner != null && refundQueueOwner.refunds.contains(next)) {
			refund(next);
			nextRefundAt = now + REFUND_SPACING_MS;
		}
	}

	public static double openRefunds(Result result) {
		double sum = 0d;
		for (Refund refund : result.refunds) {
			if (!refund.sent) {
				sum += refund.amount;
			}
		}
		return sum;
	}

	/** The lot as one line, such as "64x Diamond" or a custom title. */
	public static String label(Settings s) {
		if (!s.title.isEmpty()) {
			return s.title;
		}
		if (s.lots.isEmpty()) {
			return "?";
		}
		Lot first = s.lots.getFirst();
		String quantity = !s.quantity.isEmpty() ? s.quantity : first.count > 1 ? first.count + "x" : "";
		String name = (quantity.isEmpty() ? "" : quantity + " ") + first.name;
		return s.lots.size() > 1 ? name + " +" + (s.lots.size() - 1) : name;
	}

	private LiveAuction() {
	}
}
