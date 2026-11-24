package dev.fishclient.hud;

import dev.fishclient.feature.FishingTracker;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.L;
import dev.fishclient.ui.Theme;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Catches, catch rate and the condition of the rod. */
public final class FishingHud extends HudElement {
	FishingHud() {
		super("fishing", true, 0.99f, 0.98f);
	}

	@Override
	public float width(Draw d) {
		return 118f;
	}

	@Override
	public float height(Draw d) {
		return 56f;
	}

	/** Only worth the space while fishing or holding a rod. */
	@Override
	public boolean visible() {
		LocalPlayer p = Minecraft.getInstance().player;
		return p != null && (p.fishing != null || rod(p) != null);
	}

	private static ItemStack rod(LocalPlayer p) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = p.getItemInHand(hand);
			if (stack.is(Items.FISHING_ROD)) {
				return stack;
			}
		}
		return null;
	}

	@Override
	public void draw(Draw d) {
		float w = width(d);
		HudStyle.titled(d, w, height(d), "fish", L.t("hud.fishing"));
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) {
			return;
		}
		String status = p.fishing != null ? L.f("fishing.casting", FishingTracker.castSeconds()) : L.t("fishing.ready");
		d.text(status, HudStyle.PAD, 18f, p.fishing != null ? Theme.accent() : Theme.muted());
		row(d, w, 29f, L.t("fishing.catches"), FishingTracker.sessionCatches() + " (" + String.format(Locale.ROOT, "%.0f", FishingTracker.catchesPerHour()) + "/h)");
		ItemStack rod = rod(p);
		String durability = rod != null && rod.isDamageableItem() ? Integer.toString(rod.getMaxDamage() - rod.getDamageValue()) : "-";
		row(d, w, 40f, L.t("fishing.durability"), durability);
	}

	private static void row(Draw d, float w, float y, String label, String value) {
		d.text(label, HudStyle.PAD, y, Theme.muted());
		d.right(value, w - HudStyle.PAD, y, 1f, Theme.text(), true);
	}
}
