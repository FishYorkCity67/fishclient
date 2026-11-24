package dev.fishclient.feature;

import com.google.gson.reflect.TypeToken;
import dev.fishclient.config.FishConfig;
import dev.fishclient.config.Json;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Payments read from the chat. The history persists on disk, the session totals restart with each launch or reset. */
public final class Transactions {
	public static final class Entry {
		public long time;
		public String player = "";
		public double amount;
		public boolean received;
		public boolean session;

		Entry(long time, String player, double amount, boolean received) {
			this.time = time;
			this.player = player;
			this.amount = amount;
			this.received = received;
			this.session = true;
		}
	}

	public record Payer(String name, double total, int count) {
	}

	public static final double[] MIN_AMOUNTS = {0d, 1e5, 1e6, 5e6, 1e7};
	public static final String[] MIN_LABELS = {"Any", "100k+", "1m+", "5m+", "10m+"};
	private static final int MAX_ENTRIES = 5000;

	private static final List<Entry> ALL = new ArrayList<>();
	private static double sessionReceived;
	private static double sessionSent;
	private static boolean dirty;

	private static Path path() {
		return Json.file("transactions.json");
	}

	public static void load() {
		List<Entry> saved = Json.read(path(), new TypeToken<List<Entry>>() { }.getType());
		ALL.clear();
		if (saved != null) {
			for (Entry e : saved) {
				e.session = false;
				ALL.add(e);
			}
		}
	}

	public static void add(String player, double amount, boolean received) {
		ALL.add(new Entry(System.currentTimeMillis(), player, amount, received));
		if (ALL.size() > MAX_ENTRIES) {
			ALL.removeFirst();
		}
		if (received) {
			sessionReceived += amount;
		} else {
			sessionSent += amount;
		}
		dirty = true;
	}

	public static void saveIfDirty() {
		if (dirty) {
			dirty = false;
			Json.write(path(), ALL);
		}
	}

	public static double sessionReceived() {
		return sessionReceived;
	}

	public static double sessionSent() {
		return sessionSent;
	}

	public static double sessionProfit() {
		return sessionReceived - sessionSent;
	}

	public static void resetSession() {
		sessionReceived = 0d;
		sessionSent = 0d;
		for (Entry e : ALL) {
			e.session = false;
		}
	}

	public static void clearHistory() {
		ALL.clear();
		resetSession();
		dirty = true;
	}

	/** The newest entries first, narrowed by the minimum-amount and player filters. */
	public static List<Entry> filtered() {
		FishConfig config = FishConfig.get();
		double min = MIN_AMOUNTS[Math.clamp(config.minAmountFilter, 0, MIN_AMOUNTS.length - 1)];
		String player = config.playerFilter.trim().toLowerCase(Locale.ROOT);
		List<Entry> out = new ArrayList<>();
		for (Entry e : ALL) {
			if (e.amount >= min && (player.isEmpty() || e.player.toLowerCase(Locale.ROOT).contains(player))) {
				out.add(e);
			}
		}
		Collections.reverse(out);
		return out;
	}

	/** Everyone who paid money, biggest total first. */
	public static List<Payer> topPayers(boolean sessionOnly) {
		Map<String, double[]> totals = new HashMap<>();
		for (Entry e : ALL) {
			if (e.received && (!sessionOnly || e.session)) {
				double[] t = totals.computeIfAbsent(e.player, k -> new double[2]);
				t[0] += e.amount;
				t[1]++;
			}
		}
		List<Payer> payers = new ArrayList<>();
		totals.forEach((name, t) -> payers.add(new Payer(name, t[0], (int) t[1])));
		payers.sort(Comparator.comparingDouble(Payer::total).reversed());
		return payers;
	}

	private Transactions() {
	}
}
