package dev.fishclient.menu.pages;

import dev.fishclient.FishKeys;
import dev.fishclient.config.FishConfig;
import dev.fishclient.feature.CustomSky;
import dev.fishclient.hud.CrosshairRenderer;
import dev.fishclient.hud.FishHud;
import dev.fishclient.hud.HudElement;
import dev.fishclient.menu.Ui;
import dev.fishclient.ui.Anim;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.L;
import dev.fishclient.ui.Sounds;
import dev.fishclient.ui.Theme;
import java.util.Locale;

public final class UtilityPages {
	public static float utilities(Ui ui, float x, float y, float w) {
		FishConfig c = FishConfig.get();
		float top = y;
		y = ui.heading(L.t("tab.utilities"), x, y + 0f);

		y = ui.toggleRow("freelook", x, y, w, L.t("util.freelook"), L.t("util.freelook.desc"), c.freelook, true, v -> c.freelook = v);
		String[] mode = {L.t("util.hold"), L.t("util.toggle")};
		float[] next = new float[1];
		int m = ui.pills("fl_mode", x, y, w - 90f, mode, c.freelookToggle ? 1 : 0, next);
		if ((m == 1) != c.freelookToggle) {
			c.freelookToggle = m == 1;
			Ui.changed();
		}
		ui.keybind("fl_key", x + w - 84f, y - 1f, 84f, 17f, FishKeys.FREELOOK);
		y = next[0];
		y = ui.toggleRow("fl_third", x, y, w, L.t("util.freelook_third"), L.t("util.freelook_third.desc"), c.freelookThirdPerson, true, v -> c.freelookThirdPerson = v);

		y += 2f;
		y = ui.toggleRow("zoom", x, y, w, L.t("util.zoom"), L.t("util.zoom.desc"), c.zoom, true, v -> c.zoom = v);
		c.zoomLevel = ui.slider("zoom_level", x, y, w - 94f, L.t("util.zoom_level"), String.format(Locale.ROOT, "%.1fx", c.zoomLevel), c.zoomLevel, 1.5f, 15f, 0.5f);
		ui.keybind("zoom_key", x + w - 84f, y + 1f, 84f, 17f, FishKeys.ZOOM);
		y += 26f;
		c.zoomSensitivity = ui.slider("zoom_sens", x, y, w, L.t("util.zoom_sensitivity"), Math.round(c.zoomSensitivity * 100f) + "%", c.zoomSensitivity, 0.1f, 1f, 0.05f);
		y += 26f;
		y = ui.toggleRow("zoom_scroll", x, y, w, L.t("util.zoom_scroll"), L.t("util.zoom_scroll.desc"), c.zoomScroll, true, v -> c.zoomScroll = v);
		y = ui.toggleRow("zoom_click", x, y, w, L.t("util.zoom_click"), L.t("util.zoom_click.desc"), c.zoomClick, false, v -> c.zoomClick = v);

		y += 2f;
		y = ui.toggleRow("fullbright", x, y, w, L.t("util.fullbright"), L.t("util.fullbright.desc"), c.fullbright, false, v -> c.fullbright = v);
		y = ui.toggleRow("fps_ping", x, y, w, L.t("util.fps_ping"), L.t("util.fps_ping.desc"), FishHud.FPS_PING.pos().enabled, true, v -> FishHud.FPS_PING.pos().enabled = v);
		c.fpsGood = Math.round(ui.slider("fps_good", x, y, (w - 8f) / 2f, L.t("util.fps_green"), c.fpsGood + "+", c.fpsGood, 30f, 240f, 5f));
		c.fpsOkay = Math.round(ui.slider("fps_ok", x + (w + 8f) / 2f, y, (w - 8f) / 2f, L.t("util.fps_yellow"), c.fpsOkay + "+", c.fpsOkay, 10f, 120f, 5f));
		y += 26f;
		c.pingGood = Math.round(ui.slider("ping_good", x, y, (w - 8f) / 2f, L.t("util.ping_green"), "≤ " + c.pingGood, c.pingGood, 20f, 200f, 5f));
		c.pingOkay = Math.round(ui.slider("ping_ok", x + (w + 8f) / 2f, y, (w - 8f) / 2f, L.t("util.ping_yellow"), "≤ " + c.pingOkay, c.pingOkay, 50f, 400f, 10f));
		y += 26f;
		return y - top;
	}

	public static float scoreboard(Ui ui, float x, float y, float w) {
		FishConfig c = FishConfig.get();
		float top = y;
		y = ui.heading(L.t("tab.scoreboard"), x, y);
		y = ui.toggleRow("sb_custom", x, y, w, L.t("score.custom"), L.t("score.custom.desc"), c.scoreboard, true, v -> c.scoreboard = v);
		y = ui.toggleRow("sb_numbers", x, y, w, L.t("score.hide_numbers"), L.t("score.hide_numbers.desc"), c.scoreboardHideNumbers, true, v -> c.scoreboardHideNumbers = v);

		y = ui.section(L.t("common.preview"), x, y + 2f);
		float pw = Math.min(w, 170f);
		float px = x + (w - pw) / 2f;
		String[][] rows = {{"Rank", "3"}, {"Money", "12"}, {"Fish", "41"}, {"fishsmp.net", "0"}};
		float ph = 25f + rows.length * 11f;
		ui.d.round(px, y, pw, ph, 6f, Theme.hudPanel());
		ui.d.roundOutline(px, y, pw, ph, 6f, 1f, Draw.withAlpha(Theme.panelEdge(), 0x60));
		ui.d.centered("FishSMP", px + pw / 2f, y + 5.5f, 1f, Theme.text(), true);
		ui.d.rect(px + 6f, y + 17f, pw - 12f, 0.5f, Draw.withAlpha(Theme.accent(), 0x90));
		float ry = y + 21f;
		for (String[] row : rows) {
			ui.d.text(row[0], px + 6f, ry, Theme.text());
			if (!c.scoreboardHideNumbers) {
				ui.d.right(row[1], px + pw - 6f, ry, 1f, 0xFFFF5555, false);
			}
			ry += 11f;
		}
		y += ph + 8f;
		return y - top;
	}

	public static float crosshair(Ui ui, float x, float y, float w) {
		FishConfig c = FishConfig.get();
		float top = y;
		y = ui.heading(L.t("tab.crosshair"), x, y);
		y = ui.toggleRow("ch_on", x, y, w, L.t("crosshair.custom"), L.t("crosshair.custom.desc"), c.crosshair, false, v -> c.crosshair = v);

		ui.card(x, y, w, 56f);
		ui.d.gradientV(x + 1f, y + 1f, w - 2f, 54f, 0x402E8BC0, 0x40102030);
		CrosshairRenderer.paint(ui.d, x + w / 2f, y + 28f);
		y += 62f;

		String[] shapes = new String[CrosshairRenderer.SHAPES.length];
		for (int i = 0; i < shapes.length; i++) {
			shapes[i] = L.t("crosshair.shape." + CrosshairRenderer.SHAPES[i]);
		}
		float[] next = new float[1];
		int shape = ui.pills("ch_shape", x, y, w, shapes, c.crosshairShape, next);
		if (shape != c.crosshairShape) {
			c.crosshairShape = shape;
			Ui.changed();
		}
		y = next[0];
		c.crosshairSize = ui.slider("ch_size", x, y, w, L.t("crosshair.size"), String.format(Locale.ROOT, "%.1f", c.crosshairSize), c.crosshairSize, 1f, 15f, 0.5f);
		y += 26f;
		c.crosshairGap = ui.slider("ch_gap", x, y, w, L.t("crosshair.gap"), String.format(Locale.ROOT, "%.1f", c.crosshairGap), c.crosshairGap, 0f, 10f, 0.5f);
		y += 26f;
		c.crosshairThickness = ui.slider("ch_thick", x, y, w, L.t("crosshair.thickness"), String.format(Locale.ROOT, "%.1f", c.crosshairThickness), c.crosshairThickness, 0.5f, 4f, 0.5f);
		y += 26f;
		y = ui.toggleRow("ch_dot", x, y, w, L.t("crosshair.dot"), L.t("crosshair.dot.desc"), c.crosshairDot, false, v -> c.crosshairDot = v);
		ui.d.text(L.t("crosshair.color"), x, y, Theme.text());
		y += 13f;
		int color = ui.swatches("ch_color", x, y, new int[]{0xFFFFFFFF, 0xFF2FD6C3, 0xFF3ADB8F, 0xFFF2C14E, 0xFFFF8C40, 0xFFF0565B, 0xFFD04CC8, 0xFF30C6FF, 0xFF000000}, c.crosshairColor);
		if (color != c.crosshairColor) {
			c.crosshairColor = color;
			Ui.changed();
		}
		y += 24f;
		return y - top;
	}

	public static float sky(Ui ui, float x, float y, float w) {
		FishConfig c = FishConfig.get();
		Draw d = ui.d;
		float top = y;
		y = ui.heading(L.t("tab.sky"), x, y);
		y = ui.toggleRow("sky_on", x, y, w, L.t("sky.custom"), L.t("sky.custom.desc"), c.sky, false, v -> c.sky = v);

		int columns = w < 340f ? 2 : 4;
		float gap = 6f;
		float cw = (w - gap * (columns - 1)) / columns;
		float ch = 44f;
		int i = 0;
		for (CustomSky.Preset p : CustomSky.PRESETS) {
			float cx = x + (i % columns) * (cw + gap);
			float cy = y + (i / columns) * (ch + gap);
			ui.note("sky:" + p.id(), cx, cy, cw, ch);
			boolean over = ui.hovered(cx, cy, cw, ch);
			boolean on = c.sky && p.id().equals(c.skyPreset);
			Anim active = ui.anim("sky_a:" + p.id(), on ? 1f : 0f, 200);
			active.to(on ? 1f : 0f);
			Anim hover = ui.anim("sky_h:" + p.id(), 0f, 130);
			hover.to(over ? 1f : 0f);
			int zenith = 0xFF000000 | p.zenith();
			int horizon = 0xFF000000 | p.horizon();
			d.roundV(cx, cy, cw, ch, Ui.radius(), zenith, horizon);
			if (p.aurora() > 0f) {
				d.gradientH(cx + 4f, cy + 22f, cw - 8f, 3f, Draw.withAlpha(p.aurora1(), 0x90), Draw.withAlpha(p.aurora2(), 0x90));
			}
			d.round(cx, cy, cw, ch, Ui.radius(), Draw.mulAlpha(0x30FFFFFF, hover.get()));
			d.roundOutline(cx, cy, cw, ch, Ui.radius(), 1f, Draw.lerpColor(Theme.line(), 0xFFFFFFFF, active.get()));
			d.text(d.fit(L.t("sky." + p.id()), cw - 12f), cx + 6f, cy + ch - 13f, 0xFFFFFFFF);
			if (over && ui.click(cx, cy, cw, ch)) {
				c.skyPreset = p.id();
				c.sky = true;
				Sounds.ui(Sounds.CLICK);
				Ui.changed();
			}
			i++;
		}
		y += (int) Math.ceil(i / (float) columns) * (ch + gap) + 2f;
		y = ui.toggleRow("sky_stars", x, y, w, L.t("sky.shooting_stars"), L.t("sky.shooting_stars.desc"), c.shootingStars, true, v -> c.shootingStars = v);
		y = ui.toggleRow("sky_sun", x, y, w, L.t("sky.hide_sun"), L.t("sky.hide_sun.desc"), c.skyHideSun, true, v -> c.skyHideSun = v);
		y = ui.toggleRow("sky_clouds", x, y, w, L.t("sky.hide_clouds"), L.t("sky.hide_clouds.desc"), c.skyHideClouds, true, v -> c.skyHideClouds = v);
		return y - top;
	}

	public static float hud(Ui ui, float x, float y, float w) {
		float top = y;
		y = ui.heading(L.t("tab.hud"), x, y);
		y = ui.paragraph(L.t("hud.page_desc"), x, y, w) + 4f;
		for (HudElement element : FishHud.ELEMENTS) {
			FishConfig.HudPos pos = element.pos();
			y = ui.toggleRow("hud:" + element.id, x, y, w, element.title(), L.t("hud." + element.id + ".desc"), pos.enabled, defaultEnabled(element), v -> pos.enabled = v);
		}
		y += 2f;
		if (ui.button("hud_reset", x, y, 150f, 18f, L.t("hud.reset_positions"), false)) {
			for (HudElement element : FishHud.ELEMENTS) {
				element.resetPosition();
			}
			Ui.changed();
			ui.toast(L.t("hud.reset_done"));
		}
		y += 24f;
		return y - top;
	}

	private static boolean defaultEnabled(HudElement element) {
		return switch (element.id) {
			case "keystrokes", "cps", "armor", "minimap" -> false;
			default -> true;
		};
	}

	private UtilityPages() {
	}
}
