package dev.fishclient.hud;

import dev.fishclient.ui.Draw;
import dev.fishclient.ui.Theme;

/** The shared look of HUD boxes. */
final class HudStyle {
	static final float PAD = 6f;

	static void box(Draw d, float w, float h) {
		d.round(0f, 0f, w, h, 6f, Theme.hudPanel());
		d.roundOutline(0f, 0f, w, h, 6f, 1f, Draw.withAlpha(Theme.panelEdge(), 0x60));
	}

	static void titled(Draw d, float w, float h, String icon, String title) {
		box(d, w, h);
		d.icon(icon, PAD, PAD - 0.5f, 9f, Theme.accent());
		d.bold(title, PAD + 12f, PAD, Theme.text());
	}

	private HudStyle() {
	}
}
