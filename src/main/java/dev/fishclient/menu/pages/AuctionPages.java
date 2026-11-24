package dev.fishclient.menu.pages;

import dev.fishclient.config.FishConfig;
import dev.fishclient.feature.Auctions;
import dev.fishclient.feature.LiveAuction;
import dev.fishclient.feature.Money;
import dev.fishclient.hud.FishHud;
import dev.fishclient.menu.Ui;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.L;
import dev.fishclient.ui.Sounds;
import dev.fishclient.ui.Theme;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class AuctionPages {
	private static final SimpleDateFormat TIME = new SimpleDateFormat("dd.MM. HH:mm");
	private static final Set<Integer> SLOTS = new TreeSet<>();
	private static String titleText = "";
	private static String quantityText = "";
	private static String minText = "";
	private static String worthText = "";
	private static String noteText = "";
	@Nullable
	private static String secondsText;
	private static String watchItem = "";
	private static String watchPrice = "";
	/** The button that was clicked once and now waits for its confirming second click. */
	private static String armed = "";
	private static long expanded = -1L;

	/** A button that has to be pressed twice, for anything that cannot be taken back. */
	private static boolean confirmed(Ui ui, String id, float x, float y, float w, float h, String label, boolean primary) {
		boolean waiting = armed.equals(id);
		if (ui.button(id, x, y, w, h, waiting ? L.t("common.confirm") : label, primary || waiting)) {
			if (waiting) {
				armed = "";
				return true;
			}
			armed = id;
		}
		return false;
	}

	// ---- Live auction tab ----

	public static float auction(Ui ui, float x, float y, float w) {
		float top = y;
		if (LiveAuction.running()) {
			y = running(ui, x, y, w);
		} else {
			LiveAuction.Result last = LiveAuction.lastResult();
			if (last != null && LiveAuction.lastEndedAt() > 0L) {
				y = result(ui, x, y, w, last, true);
				y += 6f;
			}
			y = form(ui, x, y, w);
		}
		y = settings(ui, x, y + 4f, w);
		return y - top;
	}

	private static float form(Ui ui, float x, float y, float w) {
		Draw d = ui.d;
		FishConfig c = FishConfig.get();
		y = ui.heading(L.t("live.new"), x, y);
		y = ui.paragraph(L.t("live.new.desc"), x, y, w) + 4f;

		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		d.text(L.t("live.pick_items"), x, y, 0.85f, Theme.muted(), false);
		y += 12f;
		float slot = 22f;
		for (int i = 0; i < 9; i++) {
			float sx = x + i * (slot + 4f);
			ItemStack stack = player != null ? player.getInventory().getItem(i) : ItemStack.EMPTY;
			boolean on = SLOTS.contains(i) && !stack.isEmpty();
			String id = "la_slot:" + i;
			ui.note(id, sx, y, slot, slot);
			boolean over = ui.hovered(sx, y, slot, slot);
			d.round(sx, y, slot, slot, 5f, on ? Draw.withAlpha(Theme.accent(), 0x40) : over ? Theme.cardHover() : Theme.card());
			d.roundOutline(sx, y, slot, slot, 5f, 1f, on ? Theme.accent() : Theme.line());
			if (!stack.isEmpty()) {
				d.g.item(stack, Math.round(sx + 3f), Math.round(y + 3f));
				if (stack.getCount() > 1) {
					d.right(Integer.toString(stack.getCount()), sx + slot - 2f, y + slot - 8f, 0.7f, Theme.text(), true);
				}
			}
			if (ui.click(sx, y, slot, slot) && !stack.isEmpty()) {
				if (!SLOTS.remove(i)) {
					SLOTS.add(i);
				}
				Sounds.ui(Sounds.CLICK);
			}
		}
		y += slot + 6f;

		List<ItemStack> picked = pickedStacks(player);
		String suggested = picked.isEmpty() ? L.t("live.title_placeholder") : picked.getFirst().getHoverName().getString();
		float gap = 6f;
		float half = (w - gap) / 2f;
		titleText = ui.input("la_title", x, y, half, 18f, titleText, suggested);
		quantityText = ui.input("la_qty", x + half + gap, y, half, 18f, quantityText, L.t("live.quantity"));
		y += 23f;
		minText = ui.input("la_min", x, y, half, 18f, minText, L.t("live.min_bid"));
		if (secondsText == null) {
			secondsText = Integer.toString(c.auctionSeconds);
		}
		secondsText = ui.input("la_seconds", x + half + gap, y, half, 18f, secondsText, L.t("live.seconds"));
		y += 23f;
		worthText = ui.input("la_worth", x, y, half, 18f, worthText, L.t("live.worth_field"));
		noteText = ui.input("la_note", x + half + gap, y, half, 18f, noteText, L.t("live.note"));
		y += 25f;

		float startW = 120f;
		if (ui.button("la_start", x + w - startW, y, startW, 20f, L.t("live.start"), true)) {
			int seconds = parseInt(secondsText, c.auctionSeconds);
			if (picked.isEmpty() && titleText.isBlank()) {
				ui.toast(L.t("live.need_item"));
			} else if (seconds < 5) {
				ui.toast(L.t("live.need_seconds"));
			} else {
				c.auctionSeconds = seconds;
				Ui.changed();
				LiveAuction.start(LiveAuction.settings(picked, titleText, quantityText, Money.parse(minText), Money.parse(worthText), noteText, seconds));
				armed = "";
			}
		}
		d.text(d.fit(L.t("live.start_hint"), (w - startW - 10f) / 0.85f), x, y + 6f, 0.85f, Theme.faint(), false);
		y += 28f;
		return y;
	}

	private static List<ItemStack> pickedStacks(@Nullable LocalPlayer player) {
		List<ItemStack> stacks = new ArrayList<>();
		if (player == null) {
			return stacks;
		}
		for (int i : SLOTS) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!stack.isEmpty()) {
				stacks.add(stack.copy());
			}
		}
		if (stacks.isEmpty() && !player.getMainHandItem().isEmpty()) {
			stacks.add(player.getMainHandItem().copy());
		}
		return stacks;
	}

	private static float running(Ui ui, float x, float y, float w) {
		Draw d = ui.d;
		LiveAuction.Settings s = LiveAuction.current();
		if (s == null) {
			return y;
		}
		y = ui.heading(L.t("live.running"), x, y);
		ui.card(x, y, w, 46f);
		long remaining = LiveAuction.remainingMs();
		long seconds = (remaining + 999L) / 1000L;
		String clock = String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
		d.text(clock, x + 10f, y + 9f, 2f, remaining <= 10_000L ? Theme.BAD : Theme.text(), true);
		d.bold(d.fit(LiveAuction.label(s), w - 200f), x + 80f, y + 9f, Theme.text());
		LiveAuction.Bid leader = LiveAuction.leader();
		String lead = leader != null ? leader.player + "  " + Money.format(leader.value(LiveAuction.summed())) : L.t("live.no_bids");
		d.text(d.fit(lead, w - 200f), x + 80f, y + 23f, leader != null ? Theme.GOOD : Theme.faint());
		float fraction = Math.clamp(remaining / (float) LiveAuction.totalMs(), 0f, 1f);
		d.round(x + 10f, y + 38f, w - 20f, 3f, 1.5f, Theme.line());
		d.roundH(x + 10f, y + 38f, Math.max(3f, (w - 20f) * fraction), 3f, 1.5f, Theme.accent(), Theme.accent2());
		y += 52f;

		float bw = 90f;
		if (ui.button("la_add30", x, y, bw, 18f, L.t("live.add_time"), false)) {
			LiveAuction.addTime(30);
		}
		if (ui.button("la_end", x + bw + 5f, y, bw, 18f, L.t("live.end_now"), true)) {
			LiveAuction.endNow();
		}
		if (confirmed(ui, "la_cancel", x + w - bw, y, bw, 18f, L.t("live.cancel"), false)) {
			LiveAuction.cancel();
		}
		y += 26f;

		y = ui.section(L.t("live.bids"), x, y);
		List<LiveAuction.Bid> bids = LiveAuction.ranking();
		if (bids.isEmpty()) {
			d.text(L.t("live.waiting"), x, y + 2f, Theme.faint());
			y += 16f;
		}
		for (int i = 0; i < bids.size(); i++) {
			y = bidRow(ui, x, y, w, i, bids.get(i), LiveAuction.summed(), LiveAuction.eligible(bids.get(i)));
		}
		return y;
	}

	private static float bidRow(Ui ui, float x, float y, float w, int rank, LiveAuction.Bid bid, boolean summed, boolean eligible) {
		Draw d = ui.d;
		ui.card(x, y, w, 19f);
		d.bold("#" + (rank + 1), x + 8f, y + 5.5f, rank == 0 && eligible ? Theme.accent() : Theme.muted());
		d.text(bid.player, x + 34f, y + 5.5f, eligible ? Theme.text() : Theme.faint());
		String info = bid.payments > 1 ? L.f("live.payments", bid.payments) : "";
		if (!eligible) {
			info = L.t("live.below_min");
		}
		d.right(info, x + w - 90f, y + 6f, 0.8f, Theme.faint(), false);
		d.right(Money.format(bid.value(summed)), x + w - 8f, y + 5.5f, 1f, eligible ? Theme.GOOD : Theme.faint(), true);
		return y + 22f;
	}

	/** The outcome of an auction with its refund list. */
	private static float result(Ui ui, float x, float y, float w, LiveAuction.Result r, boolean showHide) {
		Draw d = ui.d;
		String key = Long.toString(r.time);
		ui.card(x, y, w, 40f);
		if (r.cancelled) {
			d.bold(L.t("live.cancelled"), x + 10f, y + 9f, Theme.text());
		} else if (r.winner == null) {
			d.bold(L.t("live.no_winner"), x + 10f, y + 9f, Theme.text());
		} else {
			d.icon("star", x + 10f, y + 8f, 10f, Theme.accent());
			d.bold(L.f("live.winner_is", r.winner), x + 24f, y + 9f, Theme.text());
			d.right(Money.format(r.price), x + w - 10f, y + 9f, 1.2f, Theme.GOOD, true);
		}
		d.text(d.fit(LiveAuction.label(r.settings) + "  ·  " + L.f("live.bidders", r.bids.size()), (w - 20f) / 0.85f), x + 10f, y + 24f, 0.85f, Theme.muted(), false);
		y += 46f;
		if (showHide && LiveAuction.showingWinner()) {
			if (ui.button("la_hide:" + key, x, y, 150f, 16f, L.t("live.hide_winner"), false)) {
				LiveAuction.hideWinner();
			}
			y += 22f;
		}
		if (r.refunds.isEmpty()) {
			return y;
		}
		float labelY = y;
		y = ui.section(L.f("live.refunds", Money.format(LiveAuction.openRefunds(r))), x, y);
		float allW = ui.buttonWidth(L.t("live.refund_all"));
		if (LiveAuction.openRefunds(r) > 0d && confirmed(ui, "la_refund_all:" + key, x + w - allW, labelY - 3f, allW, 15f, L.t("live.refund_all"), false)) {
			LiveAuction.refundAll(r);
			ui.toast(L.t("live.refunds_queued"));
		}
		for (int i = 0; i < r.refunds.size(); i++) {
			LiveAuction.Refund refund = r.refunds.get(i);
			ui.card(x, y, w, 19f);
			d.text(refund.player, x + 9f, y + 5.5f, refund.sent ? Theme.faint() : Theme.text());
			if (refund.late) {
				d.text(L.t("live.late"), x + 14f + d.width(refund.player), y + 6f, 0.8f, Theme.WARN, false);
			}
			d.right(Money.format(refund.amount), x + w - 96f, y + 5.5f, 1f, refund.sent ? Theme.faint() : Theme.text(), true);
			if (refund.sent) {
				d.right(L.t("live.refund_sent"), x + w - 8f, y + 6f, 0.85f, Theme.GOOD, false);
			} else if (confirmed(ui, "la_refund:" + key + ":" + i, x + w - 86f, y + 2f, 80f, 15f, L.t("live.refund"), false)) {
				LiveAuction.refund(refund);
			}
			y += 22f;
		}
		return y;
	}

	private static float settings(Ui ui, float x, float y, float w) {
		FishConfig c = FishConfig.get();
		y = ui.section(L.t("live.settings"), x, y);
		y = ui.toggleRow("la_hud", x, y, w, L.t("live.hud"), L.t("live.hud.desc"), FishHud.LIVE_AUCTION.pos().enabled, true, v -> FishHud.LIVE_AUCTION.pos().enabled = v);
		y = ui.toggleRow("la_sum", x, y, w, L.t("live.sum"), L.t("live.sum.desc"), c.auctionSumPayments, true, v -> c.auctionSumPayments = v);
		y = ui.toggleRow("la_extend", x, y, w, L.t("live.extend"), L.t("live.extend.desc"), c.auctionExtend, false, v -> c.auctionExtend = v);
		c.auctionExtendSeconds = Math.round(ui.slider("la_extend_s", x, y, w, L.t("live.extend_seconds"), c.auctionExtendSeconds + " s", c.auctionExtendSeconds, 5f, 60f, 5f));
		y += 26f;
		c.auctionWinnerSeconds = Math.round(ui.slider("la_winner_s", x, y, w, L.t("live.winner_seconds"), c.auctionWinnerSeconds + " s", c.auctionWinnerSeconds, 5f, 60f, 5f));
		y += 26f;
		y = ui.toggleRow("la_sounds", x, y, w, L.t("live.sounds"), L.t("live.sounds.desc"), c.auctionSounds, true, v -> c.auctionSounds = v);
		return y;
	}

	private static int parseInt(@Nullable String text, int fallback) {
		try {
			return text == null ? fallback : Integer.parseInt(text.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	// ---- History and auction house tab ----

	public static float recent(Ui ui, float x, float y, float w) {
		Draw d = ui.d;
		float top = y;
		y = ui.heading(L.t("tab.recent"), x, y);
		y = ui.paragraph(L.t("live.history.desc"), x, y, w) + 4f;

		List<LiveAuction.Result> history = LiveAuction.history();
		if (history.isEmpty()) {
			d.text(L.t("live.history_empty"), x, y, Theme.faint());
			y += 16f;
		}
		LiveAuction.Result remove = null;
		for (int i = 0; i < Math.min(history.size(), 30); i++) {
			LiveAuction.Result r = history.get(i);
			String key = Long.toString(r.time);
			ui.card(x, y, w, 30f);
			if (!r.settings.lots.isEmpty()) {
				d.g.item(r.settings.lots.getFirst().stack(), Math.round(x + 6f), Math.round(y + 7f));
			}
			d.text(d.fit(LiveAuction.label(r.settings), w - 230f), x + 27f, y + 6f, Theme.text());
			String outcome = r.cancelled ? L.t("live.cancelled") : r.winner == null ? L.t("live.no_winner") : r.winner + "  " + Money.format(r.price);
			d.text(d.fit(outcome + "  ·  " + TIME.format(new Date(r.time)), (w - 230f) / 0.85f), x + 27f, y + 17f, 0.85f, r.winner != null ? Theme.GOOD : Theme.muted(), false);
			if (ui.button("redo:" + key, x + w - 196f, y + 7f, 70f, 16f, L.t("auction.redo"), false)) {
				if (LiveAuction.running()) {
					ui.toast(L.t("live.already_running"));
				} else {
					LiveAuction.Settings again = copy(r.settings);
					LiveAuction.start(again);
					ui.toast(L.t("live.started_again"));
				}
			}
			boolean open = expanded == r.time;
			if (ui.button("details:" + key, x + w - 122f, y + 7f, 70f, 16f, open ? L.t("live.less") : L.t("live.details"), false)) {
				expanded = open ? -1L : r.time;
			}
			if (confirmed(ui, "remove:" + key, x + w - 48f, y + 7f, 42f, 16f, L.t("common.delete"), false)) {
				remove = r;
			}
			y += 34f;
			if (open) {
				for (int b = 0; b < r.bids.size(); b++) {
					LiveAuction.Bid bid = r.bids.get(b);
					y = bidRow(ui, x + 12f, y, w - 12f, b, bid, r.summed, bid.value(r.summed) >= r.settings.minBid);
				}
				y = result(ui, x + 12f, y, w - 12f, r, false) + 4f;
			}
		}
		if (remove != null) {
			LiveAuction.remove(remove);
		}

		y = auctionHouse(ui, x, y + 8f, w);
		return y - top;
	}

	private static LiveAuction.Settings copy(LiveAuction.Settings s) {
		LiveAuction.Settings c = new LiveAuction.Settings();
		c.lots = new ArrayList<>(s.lots);
		c.title = s.title;
		c.quantity = s.quantity;
		c.minBid = s.minBid;
		c.worth = s.worth;
		c.note = s.note;
		c.seconds = s.seconds;
		return c;
	}

	/** The read-only auction house helpers: flips, sales seen in chat, and the watch list. */
	private static float auctionHouse(Ui ui, float x, float y, float w) {
		Draw d = ui.d;
		y = ui.heading(L.t("auction.house"), x, y);

		y = ui.section(L.t("auction.flips"), x, y);
		List<Auctions.Flip> flips = Auctions.flips();
		double sessionProfit = Auctions.sessionFlipProfit();
		d.text(L.f("auction.flips_session", Money.signed(sessionProfit), flips.size()), x, y, sessionProfit >= 0d ? Theme.GOOD : Theme.BAD);
		y += 14f;
		for (int i = 0; i < Math.min(flips.size(), 8); i++) {
			Auctions.Flip f = flips.get(i);
			ui.card(x, y, w, 19f);
			d.text(d.fit(f.item(), w - 170f), x + 9f, y + 5.5f, Theme.text());
			d.right(Money.format(f.bought()) + " → " + Money.format(f.sold()), x + w - 70f, y + 5.5f, 0.85f, Theme.muted(), false);
			d.right(Money.signed(f.profit()), x + w - 8f, y + 5.5f, 1f, f.profit() >= 0d ? Theme.GOOD : Theme.BAD, true);
			y += 22f;
		}

		float labelY = y + 4f;
		y = ui.section(L.t("auction.sales"), x, labelY);
		float clearW = ui.buttonWidth(L.t("common.clear"));
		if (ui.button("sales_clear", x + w - clearW, labelY - 3f, clearW, 15f, L.t("common.clear"), false)) {
			Auctions.clearSales();
		}
		List<Auctions.Sale> sales = Auctions.recentSales();
		if (sales.isEmpty()) {
			d.text(L.t("auction.sales_empty"), x, y, Theme.faint());
			y += 16f;
		}
		for (int i = 0; i < Math.min(sales.size(), 20); i++) {
			Auctions.Sale s = sales.get(i);
			ui.card(x, y, w, 19f);
			d.round(x + 7f, y + 7f, 5f, 5f, 2.5f, s.sold ? Theme.GOOD : Theme.WARN);
			d.text(d.fit(s.item, w - 190f), x + 17f, y + 5.5f, Theme.text());
			d.right(TIME.format(new Date(s.time)), x + w - 70f, y + 5.5f, 0.85f, Theme.faint(), false);
			d.right(Money.format(s.price), x + w - 8f, y + 5.5f, 1f, Theme.text(), true);
			y += 22f;
		}

		y = ui.section(L.t("auction.watchlist"), x, y + 4f);
		y = ui.paragraph(L.t("auction.watchlist.desc"), x, y, w) + 4f;
		float addW = ui.buttonWidth(L.t("common.add"));
		float priceW = 86f;
		float itemW = w - addW - priceW - 8f;
		watchItem = ui.input("w_item", x, y, itemW, 18f, watchItem, L.t("auction.watch_item"));
		watchPrice = ui.input("w_price", x + itemW + 4f, y, priceW, 18f, watchPrice, L.t("auction.watch_price"));
		if (ui.button("w_add", x + w - addW, y, addW, 18f, L.t("common.add"), false)) {
			double max = Money.parse(watchPrice);
			if (watchItem.isBlank() || max <= 0d) {
				ui.toast(L.t("auction.watch_invalid"));
			} else {
				Auctions.addWatch(watchItem.trim(), max);
				watchItem = "";
				watchPrice = "";
			}
		}
		y += 24f;
		Auctions.Watch removeWatch = null;
		for (Auctions.Watch watch : Auctions.watches()) {
			ui.card(x, y, w, 19f);
			d.text(watch.item, x + 9f, y + 5.5f, Theme.text());
			d.right("≤ " + Money.format(watch.maxPrice), x + w - 26f, y + 5.5f, 1f, Theme.accent(), false);
			if (ui.iconButton("w_del:" + watch.item + watch.maxPrice, x + w - 21f, y + 2f, 15f, "trash", L.t("common.delete"))) {
				removeWatch = watch;
			}
			y += 22f;
		}
		if (removeWatch != null) {
			Auctions.removeWatch(removeWatch);
		}
		return y;
	}

	private AuctionPages() {
	}
}
