package dev.fishclient.menu.pages;

import dev.fishclient.config.FishConfig;
import dev.fishclient.feature.FishingTracker;
import dev.fishclient.menu.Ui;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.L;
import dev.fishclient.ui.Smooth;
import dev.fishclient.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FishingPage {
	public static float draw(Ui ui, float x, float y, float w) {
		FishConfig c = FishConfig.get();
		Draw d = ui.d;
		float top = y;
		y = ui.heading(L.t("tab.fishing"), x, y);
		y = ui.paragraph(L.t("fishing.desc"), x, y, w) + 4f;

		FishingTracker.Stats stats = FishingTracker.stats();
		float gap = 6f;
		float tile = (w - gap * 3f) / 4f;
		stat(ui, "s", x, y, tile, L.t("fishing.session"), FishingTracker.sessionCatches());
		stat(ui, "h", x + (tile + gap), y, tile, L.t("fishing.per_hour"), FishingTracker.catchesPerHour());
		stat(ui, "t", x + (tile + gap) * 2f, y, tile, L.t("fishing.total"), stats.totalCatches);
		stat(ui, "c", x + (tile + gap) * 3f, y, tile, L.t("fishing.casts"), stats.totalCasts);
		y += 40f;

		y = ui.section(L.t("fishing.cosmetics"), x, y);
		y = ui.toggleRow("fish_bubbles", x, y, w, L.t("fishing.bubbles"), L.t("fishing.bubbles.desc"), c.fishBubbles, false, v -> c.fishBubbles = v);
		y = ui.toggleRow("fish_rod", x, y, w, L.t("fishing.rod_skin"), L.t("fishing.rod_skin.desc"), c.rodSkin, false, v -> c.rodSkin = v);
		int color = ui.swatches("rod_color", x, y, new int[]{0xFF2FD6C3, 0xFF30C6FF, 0xFF3ADB8F, 0xFFF2C14E, 0xFFFF8C40, 0xFFF0565B, 0xFFD04CC8, 0xFFEEF2FB}, c.rodColor);
		if (color != c.rodColor) {
			c.rodColor = color;
			Ui.changed();
		}
		y += 24f;
		d.text(L.t("fishing.hat_hint"), x, y, 0.85f, Theme.muted(), false);
		y += 14f;

		float labelY = y;
		y = ui.section(L.t("fishing.top_catches"), x, y);
		float rw = ui.buttonWidth(L.t("fishing.reset"));
		if (ui.button("fish_reset", x + w - rw, labelY - 3f, rw, 15f, L.t("fishing.reset"), false)) {
			FishingTracker.resetAll();
			ui.toast(L.t("fishing.reset_done"));
		}
		List<Map.Entry<String, Long>> entries = new ArrayList<>(stats.items.entrySet());
		entries.sort(Map.Entry.<String, Long>comparingByValue().reversed());
		if (entries.isEmpty()) {
			d.text(L.t("fishing.empty"), x, y, Theme.faint());
			y += 16f;
		}
		for (int i = 0; i < Math.min(entries.size(), 12); i++) {
			ui.card(x, y, w, 19f);
			d.text(entries.get(i).getKey(), x + 9f, y + 5.5f, Theme.text());
			d.right(Long.toString(entries.get(i).getValue()), x + w - 9f, y + 5.5f, 1f, Theme.accent(), true);
			y += 22f;
		}
		List<String> rare = FishingTracker.rareFinds();
		if (!rare.isEmpty()) {
			y = ui.section(L.t("fishing.rare"), x, y + 4f);
			for (String name : rare) {
				d.text(name, x, y, Theme.text());
				y += 12f;
			}
		}
		return y - top;
	}

	private static void stat(Ui ui, String id, float x, float y, float w, String label, double value) {
		ui.card(x, y, w, 34f);
		ui.d.text(ui.d.fit(label.toUpperCase(Locale.ROOT), (w - 12f) / 0.75f), x + 7f, y + 7f, 0.75f, Theme.muted(), false);
		Smooth s = ui.smooth("fishstat:" + id, value, 110d);
		s.target(value);
		ui.d.bold(Long.toString(Math.round(s.get())), x + 7f, y + 18f, Theme.text());
	}

	private FishingPage() {
	}
}
