package dev.fishclient.menu.pages;

import dev.fishclient.config.FishConfig;
import dev.fishclient.menu.Ui;
import dev.fishclient.ui.Anim;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.L;
import dev.fishclient.ui.Sounds;
import dev.fishclient.ui.Theme;
import java.util.Locale;

public final class ThemePage {
	public static float draw(Ui ui, float x, float y, float w) {
		FishConfig c = FishConfig.get();
		Draw d = ui.d;
		float top = y;
		int columns = w < 300f ? 2 : w < 400f ? 3 : 4;
		float gap = 6f;
		float cardW = (w - gap * (columns - 1)) / columns;
		float cardH = 42f;
		int rows = (int) Math.ceil(Theme.ALL.size() / (float) columns);
		for (int i = 0; i < Theme.ALL.size(); i++) {
			Theme.Palette p = Theme.ALL.get(i);
			float cx = x + (i % columns) * (cardW + gap);
			float cy = y + (i / columns) * (cardH + gap);
			ui.note("theme:" + p.id(), cx, cy, cardW, cardH);
			boolean over = ui.hovered(cx, cy, cardW, cardH);
			Anim hover = ui.anim("theme_h:" + p.id(), 0f, 130);
			hover.to(over ? 1f : 0f);
			boolean on = p.id().equals(c.theme);
			Anim active = ui.anim("theme_a:" + p.id(), on ? 1f : 0f, 200);
			active.to(on ? 1f : 0f);
			d.round(cx, cy, cardW, cardH, Ui.radius(), Draw.lerpColor(Theme.card(), Theme.cardHover(), hover.get()));
			d.roundOutline(cx, cy, cardW, cardH, Ui.radius(), 1f, Draw.lerpColor(Theme.line(), 0xFFFFFFFF, active.get()));
			d.text(d.fit(p.name(), cardW - 26f), cx + 8f, cy + 7f, Theme.text());
			float radio = 8f;
			float rx = cx + cardW - radio - 7f;
			d.roundOutline(rx, cy + 7f, radio, radio, radio / 2f, 1f, Draw.lerpColor(Theme.faint(), 0xFFFFFFFF, active.get()));
			float dot = 4f * active.get();
			if (dot > 0.2f) {
				d.round(rx + radio / 2f - dot / 2f, cy + 7f + radio / 2f - dot / 2f, dot, dot, dot / 2f, 0xFFFFFFFF);
			}
			swatch(d, cx + 8f, cy + 21f, cardW - 16f, 14f, p);
			if (over && ui.click(cx, cy, cardW, cardH) && !on) {
				c.theme = p.id();
				Theme.apply(p.id(), false);
				Sounds.ui(Sounds.CLICK);
				Ui.changed();
			}
		}
		y += rows * (cardH + gap) + 4f;

		float headerY = y;
		y = ui.section(L.t("theme.effects"), x, y);
		float resetW = ui.buttonWidth(L.t("common.reset"));
		if (ui.button("effects_reset", x + w - resetW, headerY - 3f, resetW, 15f, L.t("common.reset"), false)) {
			c.seeThrough = true;
			c.frostedBlur = true;
			c.ambient = true;
			c.radius = 10f;
			c.blur = 5;
			Ui.changed();
		}
		y = ui.toggleRow("see_through", x, y, w, L.t("theme.see_through"), L.t("theme.see_through.desc"), c.seeThrough, true, v -> c.seeThrough = v);
		y = ui.toggleRow("frosted", x, y, w, L.t("theme.frosted"), L.t("theme.frosted.desc"), c.frostedBlur, true, v -> c.frostedBlur = v);
		y = ui.toggleRow("ambient", x, y, w, L.t("theme.ambient"), L.t("theme.ambient.desc"), c.ambient, true, v -> c.ambient = v);

		y += 4f;
		y = ui.section(L.t("theme.shape"), x, y);
		c.radius = ui.slider("radius", x, y, w, L.t("theme.radius"), String.format(Locale.ROOT, "%.0f px", c.radius), c.radius, 2f, 16f, 1f);
		y += 26f;
		c.blur = Math.round(ui.slider("blur", x, y, w, L.t("theme.blur"), Integer.toString(c.blur), c.blur, 0f, 10f, 1f));
		y += 26f;
		return y - top;
	}

	private static void swatch(Draw d, float x, float y, float w, float h, Theme.Palette p) {
		float part = w / 4f;
		d.round(x, y, part + 2f, h, 3f, 0xFF000000 | p.accent());
		d.rect(x + part, y, part, h, 0xFF000000 | p.accent2());
		d.rect(x + part * 2f, y, part, h, 0xFF000000 | p.accent3());
		d.round(x + part * 3f - 2f, y, part + 2f, h, 3f, 0xFF000000 | p.deep());
		d.rect(x + part * 3f - 1f, y, 2f, h, 0xFF000000 | p.deep());
	}

	private ThemePage() {
	}
}
