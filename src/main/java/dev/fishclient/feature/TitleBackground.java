package dev.fishclient.feature;

import dev.fishclient.FishClient;
import dev.fishclient.ui.Anim;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.Easing;
import dev.fishclient.ui.Sounds;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/** The title screen: a calm ocean gradient with rising bubbles, and the FishClient logo. */
public final class TitleBackground {
	private static final int BUBBLES = 60;
	private static final float[][] DATA = new float[BUBBLES][4];
	private static final Identifier LOGO = FishClient.id("textures/gui/logo.png");
	private static final Identifier WORDMARK = FishClient.id("textures/gui/wordmark.png");
	private static final Map<AbstractButton, Anim> HOVERS = new WeakHashMap<>();
	private static long lastDraw;

	static {
		Random r = new Random(99L);
		for (float[] b : DATA) {
			b[0] = r.nextFloat();
			b[1] = r.nextFloat();
			b[2] = 1.2f + r.nextFloat() * r.nextFloat() * 6f;
			b[3] = 0.018f + r.nextFloat() * 0.05f;
		}
	}

	public static void draw(GuiGraphicsExtractor g, int width, int height) {
		long now = Util.getMillis();
		// Back on the title screen after being away: greet with the water sound once.
		if (now - lastDraw > 5000L) {
			Sounds.water(Sounds.BUBBLES);
		}
		lastDraw = now;
		Draw d = new Draw(g);
		d.gradientV(0, 0, width, height * 0.55f, 0xFF0B4A70, 0xFF072C4C);
		d.gradientV(0, height * 0.55f, width, height * 0.45f + 1f, 0xFF072C4C, 0xFF030F22);
		float time = now / 1000f;
		for (float[] b : DATA) {
			float travel = (b[1] + time * b[3]) % 1.15f;
			float y = height * (1.08f - travel);
			float x = b[0] * width + (float) Math.sin(time * 0.9f + b[1] * 40f) * (3f + b[2]);
			float size = b[2] * 2f;
			// Bubbles fade in at the bottom and thin out towards the surface.
			float fade = Math.min(travel / 0.15f, 1f) * (1f - Math.max(travel - 0.9f, 0f) / 0.25f);
			int ring = Draw.mulAlpha(0x80BFEFFF, fade);
			int fill = Draw.mulAlpha(0x1CBFEFFF, fade);
			d.round(x, y, size, size, size / 2f, fill);
			d.roundOutline(x, y, size, size, size / 2f, 0.5f, ring);
			d.round(x + size * 0.22f, y + size * 0.2f, size * 0.22f, size * 0.22f, size * 0.11f, Draw.mulAlpha(0xB0FFFFFF, fade));
		}
	}

	public static void drawButton(GuiGraphicsExtractor g, AbstractButton button) {
		Anim hover = HOVERS.computeIfAbsent(button, b -> new Anim(0f, 130, Easing.OUT_CUBIC));
		hover.to(button.isHoveredOrFocused() && button.active ? 1f : 0f);
		float t = hover.get();
		Draw d = new Draw(g);
		d.alpha = button.getAlpha();
		float x = button.getX();
		float y = button.getY();
		float w = button.getWidth();
		float h = button.getHeight();
		float r = Math.min(7f, h / 2f);
		int fill = button.active ? Draw.lerpColor(0xB00A1B33, 0xE0123A5A, t) : 0x70081424;
		d.round(x, y, w, h, r, fill);
		d.roundOutline(x, y, w, h, r, 1f, Draw.lerpColor(0x803A6C8C, 0xFF2FD6C3, t));
	}

	public static Component styledLabel(Component message) {
		return Component.empty().withStyle(Style.EMPTY.withFont(new FontDescription.Resource(FishClient.id("inter")))).append(message);
	}

	public static void drawLogo(GuiGraphicsExtractor g, int width, float alpha) {
		Draw d = new Draw(g);
		d.alpha = alpha;
		float wordW = 170f;
		float wordH = wordW * 111f / 487f;
		float icon = 44f;
		float total = icon + 8f + wordW;
		float x = width / 2f - total / 2f;
		float y = 32f;
		d.texture(LOGO, x, y, icon, icon, 0xFFFFFFFF);
		d.texture(WORDMARK, x + icon + 8f, y + (icon - wordH) / 2f, wordW, wordH, 0xFFFFFFFF);
	}

	private TitleBackground() {
	}
}
