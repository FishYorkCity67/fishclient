package dev.fishclient.feature;

import dev.fishclient.config.Json;
import dev.fishclient.mixin.FishingHookAccessor;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Counts catches from what the client can see: a biting hook was reeled in and an item count rose right after.
 * It never reels in by itself.
 */
public final class FishingTracker {
	public static final class Stats {
		public long totalCatches;
		public long totalCasts;
		public Map<String, Long> items = new LinkedHashMap<>();
	}

	private static final Set<Item> RARE = Set.of(
		Items.ENCHANTED_BOOK, Items.NAME_TAG, Items.SADDLE, Items.NAUTILUS_SHELL, Items.BOW, Items.FISHING_ROD, Items.PUFFERFISH, Items.TROPICAL_FISH
	);
	// A caught item flies to the player first, which can take a few seconds from far out.
	private static final int CATCH_WINDOW_TICKS = 80;

	private static Stats stats = new Stats();
	private static int sessionCatches;
	private static long sessionStart = System.currentTimeMillis();
	private static boolean dirty;
	@Nullable
	private static FishingHook lastHook;
	private static boolean sawBite;
	private static long castStart;
	private static int windowTicks;
	@Nullable
	private static Map<Item, Integer> before;
	private static final List<String> RARE_FINDS = new ArrayList<>();

	private static Path path() {
		return Json.file("fishing.json");
	}

	public static void load() {
		Stats saved = Json.read(path(), Stats.class);
		stats = saved != null ? saved : new Stats();
	}

	public static void saveIfDirty() {
		if (dirty) {
			dirty = false;
			Json.write(path(), stats);
		}
	}

	public static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null) {
			lastHook = null;
			before = null;
			return;
		}
		FishingHook hook = player.fishing;
		if (hook != null) {
			if (hook != lastHook) {
				castStart = System.currentTimeMillis();
				sawBite = false;
				stats.totalCasts++;
				dirty = true;
			}
			if (((FishingHookAccessor) hook).fishclient$isBiting()) {
				sawBite = true;
			}
		} else if (lastHook != null && sawBite) {
			before = snapshot(player.getInventory());
			windowTicks = CATCH_WINDOW_TICKS;
		}
		lastHook = hook;

		if (before != null) {
			Map<Item, Integer> now = snapshot(player.getInventory());
			Item gained = gained(before, now);
			if (gained != null) {
				record(gained);
				before = null;
			} else if (--windowTicks <= 0) {
				before = null;
			}
		}
	}

	private static Map<Item, Integer> snapshot(Inventory inventory) {
		Map<Item, Integer> counts = new HashMap<>();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty()) {
				counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
		return counts;
	}

	@Nullable
	private static Item gained(Map<Item, Integer> before, Map<Item, Integer> now) {
		for (Map.Entry<Item, Integer> e : now.entrySet()) {
			if (e.getValue() > before.getOrDefault(e.getKey(), 0)) {
				return e.getKey();
			}
		}
		return null;
	}

	private static void record(Item item) {
		String name = new ItemStack(item).getHoverName().getString();
		stats.totalCatches++;
		stats.items.merge(name, 1L, Long::sum);
		sessionCatches++;
		if (RARE.contains(item)) {
			RARE_FINDS.addFirst(name);
			if (RARE_FINDS.size() > 5) {
				RARE_FINDS.removeLast();
			}
		}
		dirty = true;
	}

	public static Stats stats() {
		return stats;
	}

	public static int sessionCatches() {
		return sessionCatches;
	}

	public static List<String> rareFinds() {
		return RARE_FINDS;
	}

	public static double catchesPerHour() {
		double hours = (System.currentTimeMillis() - sessionStart) / 3_600_000d;
		return hours > 0.002d ? sessionCatches / hours : 0d;
	}

	public static long castSeconds() {
		return lastHook != null ? (System.currentTimeMillis() - castStart) / 1000L : 0L;
	}

	public static void resetSession() {
		sessionCatches = 0;
		sessionStart = System.currentTimeMillis();
		RARE_FINDS.clear();
	}

	public static void resetAll() {
		stats = new Stats();
		resetSession();
		dirty = true;
	}

	private FishingTracker() {
	}
}
