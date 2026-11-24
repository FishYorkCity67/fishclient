package dev.fishclient.feature;

import dev.fishclient.mixin.AbstractContainerScreenAccessor;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.L;
import dev.fishclient.ui.Sounds;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Marks watch-list items below their price in an open auction window. It only reads what is displayed and draws
 * a frame: it never clicks a slot, so buying stays a deliberate action by the player.
 */
public final class AuctionWatcher {
	private static final Pattern PRICE = Pattern.compile("\\$\\s?([0-9][0-9.,]*\\s?[kKmMbBtT]?)");
	private static final List<Slot> MARKED = new ArrayList<>();
	private static final Set<String> ANNOUNCED = new HashSet<>();

	public static void attach(Screen screen) {
		if (screen instanceof AbstractContainerScreen<?> container) {
			ANNOUNCED.clear();
			ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, tickDelta) -> {
				if (MARKED.isEmpty()) {
					return;
				}
				Draw d = new Draw(graphics);
				AbstractContainerScreenAccessor access = (AbstractContainerScreenAccessor) container;
				for (Slot slot : MARKED) {
					float x = access.fishclient$leftPos() + slot.x;
					float y = access.fishclient$topPos() + slot.y;
					d.roundOutline(x - 1f, y - 1f, 18f, 18f, 2f, 1f, 0xFF3ADB8F);
				}
			});
		}
	}

	public static int markedCount() {
		return MARKED.size();
	}

	private static boolean looksLikeAuction(Screen screen) {
		String title = screen.getTitle().getString().toLowerCase(Locale.ROOT);
		return title.contains("auction") || title.contains("auktion") || title.contains("market") || title.contains("/ah");
	}

	public static void tick(Minecraft mc) {
		MARKED.clear();
		if (!(mc.screen instanceof AbstractContainerScreen<?> container) || !looksLikeAuction(mc.screen) || Auctions.watches().isEmpty() || mc.player == null) {
			return;
		}
		for (Slot slot : container.getMenu().slots) {
			if (slot.container == mc.player.getInventory()) {
				continue;
			}
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) {
				continue;
			}
			String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
			for (Auctions.Watch watch : Auctions.watches()) {
				if (!name.contains(watch.item.toLowerCase(Locale.ROOT))) {
					continue;
				}
				double price = lowestPrice(mc, stack);
				if (price > 0d && price <= watch.maxPrice) {
					MARKED.add(slot);
					String key = name + "|" + price;
					if (ANNOUNCED.add(key)) {
						mc.gui.setOverlayMessage(Component.literal(L.f("auction.watch_hit", stack.getHoverName().getString(), Money.format(price))), false);
						Sounds.ui(Sounds.TOGGLE_ON);
					}
				}
			}
		}
	}

	private static double lowestPrice(Minecraft mc, ItemStack stack) {
		double best = 0d;
		for (Component line : Screen.getTooltipFromItem(mc, stack)) {
			Matcher m = PRICE.matcher(line.getString());
			while (m.find()) {
				double value = Money.parse(m.group(1));
				if (value > 0d && (best == 0d || value < best)) {
					best = value;
				}
			}
		}
		return best;
	}

	private AuctionWatcher() {
	}
}
