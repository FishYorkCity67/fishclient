package dev.fishclient.feature;

import dev.fishclient.config.Json;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Auction house records read from the chat: what was bought and sold, the flips that came out of it, and a watch
 * list. Nothing in here clicks, buys or bids.
 */
public final class Auctions {
	public static final class Sale {
		public long time;
		public String item = "";
		public String player = "";
		public double price;
		public boolean sold;

		Sale(long time, String item, String player, double price, boolean sold) {
			this.time = time;
			this.item = item;
			this.player = player;
			this.price = price;
			this.sold = sold;
		}
	}

	public static final class Watch {
		public String item = "";
		public double maxPrice;
	}

	public record Flip(String item, double bought, double sold, long time) {
		public double profit() {
			return this.sold - this.bought;
		}
	}

	private static final class Data {
		List<Sale> sales = new ArrayList<>();
		List<Watch> watches = new ArrayList<>();
	}

	private static final int MAX_SALES = 2000;
	private static Data data = new Data();
	private static boolean dirty;
	private static double sessionFlipProfit;

	private static Path path() {
		return Json.file("auctions.json");
	}

	public static void load() {
		Data saved = Json.read(path(), Data.class);
		data = saved != null ? saved : new Data();
	}

	public static void saveIfDirty() {
		if (dirty) {
			dirty = false;
			Json.write(path(), data);
		}
	}

	public static void addSale(String item, String player, double price, boolean sold) {
		// A sale closes the oldest open purchase of the same item, which is what makes it a flip.
		if (sold) {
			Sale buy = openPurchase(item);
			if (buy != null) {
				sessionFlipProfit += price - buy.price;
			}
		}
		data.sales.add(new Sale(System.currentTimeMillis(), item, player, price, sold));
		if (data.sales.size() > MAX_SALES) {
			data.sales.removeFirst();
		}
		dirty = true;
	}

	/** The oldest purchase of this item no sale has used yet, the same pairing {@link #flips()} shows. */
	@Nullable
	private static Sale openPurchase(String item) {
		Deque<Sale> open = new ArrayDeque<>();
		for (Sale s : data.sales) {
			if (s.item.equalsIgnoreCase(item)) {
				if (s.sold) {
					open.pollFirst();
				} else {
					open.addLast(s);
				}
			}
		}
		return open.peekFirst();
	}

	/** Pairs purchases and later sales of the same item, oldest purchase first. Newest flips come first. */
	public static List<Flip> flips() {
		Map<String, List<Sale>> open = new HashMap<>();
		List<Flip> flips = new ArrayList<>();
		for (Sale s : data.sales) {
			String key = s.item.toLowerCase(Locale.ROOT);
			if (!s.sold) {
				open.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
			} else {
				List<Sale> buys = open.get(key);
				if (buys != null && !buys.isEmpty()) {
					Sale buy = buys.removeFirst();
					flips.add(new Flip(s.item, buy.price, s.price, s.time));
				}
			}
		}
		flips.sort(Comparator.comparingLong(Flip::time).reversed());
		return flips;
	}

	public static double sessionFlipProfit() {
		return sessionFlipProfit;
	}

	public static void resetSession() {
		sessionFlipProfit = 0d;
	}

	public static List<Sale> recentSales() {
		List<Sale> list = new ArrayList<>(data.sales);
		list.sort(Comparator.comparingLong((Sale s) -> s.time).reversed());
		return list;
	}

	public static List<Watch> watches() {
		return data.watches;
	}

	public static void addWatch(String item, double maxPrice) {
		Watch w = new Watch();
		w.item = item;
		w.maxPrice = maxPrice;
		data.watches.add(w);
		dirty = true;
	}

	public static void removeWatch(Watch watch) {
		data.watches.remove(watch);
		dirty = true;
	}

	public static void clearSales() {
		data.sales.clear();
		dirty = true;
	}

	private Auctions() {
	}
}
