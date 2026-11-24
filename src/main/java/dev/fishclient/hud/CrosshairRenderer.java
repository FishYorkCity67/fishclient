package dev.fishclient.hud;

import dev.fishclient.config.FishConfig;
import dev.fishclient.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A custom crosshair: cross, T-shape, circle or dot, with free size, gap, thickness and colour. */
public final class CrosshairRenderer {
	public static final String[] SHAPES = {"cross", "t", "circle", "dot"};

	public static void draw(GuiGraphicsExtractor graphics) {
		Minecraft mc = Minecraft.getInstance();
		if (!mc.options.getCameraType().isFirstPerson() && !dev.fishclient.feature.Freelook.active()) {
			return;
		}
		Draw d = new Draw(graphics);
		float unit = Draw.unit();
		graphics.pose().pushMatrix();
		graphics.pose().translate(graphics.guiWidth() / 2f, graphics.guiHeight() / 2f);
		graphics.pose().scale(unit, unit);
		paint(d, 0f, 0f);
		graphics.pose().popMatrix();
	}

	/** Also used for the live preview in the menu. */
	public static void paint(Draw d, float cx, float cy) {
		FishConfig c = FishConfig.get();
		float size = c.crosshairSize;
		float gap = c.crosshairGap;
		float t = c.crosshairThickness;
		int color = c.crosshairColor;
		switch (Math.clamp(c.crosshairShape, 0, SHAPES.length - 1)) {
			case 1 -> {
				d.rect(cx - gap - size, cy - t / 2f, size, t, color);
				d.rect(cx + gap, cy - t / 2f, size, t, color);
				d.rect(cx - t / 2f, cy + gap, t, size, color);
			}
			case 2 -> d.roundOutline(cx - size, cy - size, size * 2f, size * 2f, size, Math.max(0.5f, t), color);
			case 3 -> d.round(cx - t * 1.5f, cy - t * 1.5f, t * 3f, t * 3f, t * 1.5f, color);
			default -> {
				d.rect(cx - gap - size, cy - t / 2f, size, t, color);
				d.rect(cx + gap, cy - t / 2f, size, t, color);
				d.rect(cx - t / 2f, cy - gap - size, t, size, color);
				d.rect(cx - t / 2f, cy + gap, t, size, color);
			}
		}
		if (c.crosshairDot && c.crosshairShape != 3) {
			d.round(cx - t * 0.75f, cy - t * 0.75f, t * 1.5f, t * 1.5f, t * 0.75f, color);
		}
	}

	private CrosshairRenderer() {
	}
}
