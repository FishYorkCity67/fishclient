package dev.fishclient.hud;

import dev.fishclient.ui.Draw;
import dev.fishclient.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Worn armour and held items, each with its remaining durability. */
public final class ArmorHud extends HudElement {
	private static final EquipmentSlot[] SLOTS = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
	};
	private static final float ROW = 18f;

	ArmorHud() {
		super("armor", false, 0.99f, 0.7f);
	}

	/** An empty box would only be in the way. */
	@Override
	public boolean visible() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return false;
		}
		for (EquipmentSlot slot : SLOTS) {
			if (!player.getItemBySlot(slot).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public float width(Draw d) {
		return 62f;
	}

	@Override
	public float height(Draw d) {
		return SLOTS.length * ROW + 4f;
	}

	@Override
	public void draw(Draw d) {
		HudStyle.box(d, width(d), height(d));
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		float y = 2f;
		for (EquipmentSlot slot : SLOTS) {
			ItemStack stack = player.getItemBySlot(slot);
			if (!stack.isEmpty()) {
				d.g.item(stack, 3, Math.round(y) + 1);
				if (stack.isDamageableItem()) {
					int left = stack.getMaxDamage() - stack.getDamageValue();
					float fraction = left / (float) stack.getMaxDamage();
					int color = fraction > 0.5f ? Theme.GOOD : fraction > 0.2f ? Theme.WARN : Theme.BAD;
					d.text(Integer.toString(left), 22f, y + 5f, color);
				} else if (stack.getCount() > 1) {
					d.text("x" + stack.getCount(), 22f, y + 5f, Theme.muted());
				}
			}
			y += ROW;
		}
	}
}
