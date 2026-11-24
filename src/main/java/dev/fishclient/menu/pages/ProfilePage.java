package dev.fishclient.menu.pages;

import dev.fishclient.config.Profiles;
import dev.fishclient.feature.FileDialogs;
import dev.fishclient.menu.Ui;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.L;
import dev.fishclient.ui.Theme;
import java.util.List;

public final class ProfilePage {
	private static String newName = "";
	private static String confirmDelete = "";

	public static float draw(Ui ui, float x, float y, float w) {
		Draw d = ui.d;
		float top = y;
		y = ui.heading(L.t("tab.profiles"), x, y);
		y = ui.paragraph(L.t("profiles.desc"), x, y, w) + 4f;

		float createW = 86f;
		newName = ui.input("profile_new", x, y, w - createW - 5f, 18f, newName, L.t("profiles.name_placeholder"));
		if (ui.button("profile_create", x + w - createW, y, createW, 18f, L.t("profiles.create"), true) && !newName.isBlank()) {
			Profiles.create(newName);
			ui.toast(L.f("profiles.created", Profiles.active()));
			newName = "";
		}
		y += 24f;

		float half = (w - 5f) / 2f;
		if (ui.button("profile_export", x, y, half, 18f, L.t("profiles.export"), false)) {
			FileDialogs.save(L.t("profiles.export"), Profiles.active() + ".json", "*.json", "JSON", path -> {
				Profiles.exportTo(path);
				ui.toast(L.t("profiles.exported"));
			});
		}
		if (ui.button("profile_import", x + half + 5f, y, half, 18f, L.t("profiles.import"), false)) {
			FileDialogs.open(L.t("profiles.import"), "*.json", "JSON", path -> {
				boolean ok = Profiles.importFrom(path);
				ui.toast(ok ? L.f("profiles.imported", Profiles.active()) : L.t("profiles.import_failed"));
			});
		}
		y += 26f;

		y = ui.section(L.t("profiles.list"), x, y);
		List<String> names = Profiles.names();
		String active = Profiles.active();
		for (String name : names) {
			boolean on = name.equals(active);
			ui.card(x, y, w, 24f);
			if (on) {
				ui.d.roundOutline(x, y, w, 24f, Ui.radius(), 1f, Theme.accent());
			}
			d.text(name, x + 10f, y + 8f, on ? Theme.text() : Theme.muted());
			if (on) {
				d.right(L.t("profiles.active"), x + w - 10f, y + 8f, 0.85f, Theme.accent(), false);
			} else {
				if (ui.button("profile_use:" + name, x + w - 116f, y + 4f, 60f, 16f, L.t("profiles.load"), false)) {
					Profiles.load(name);
					ui.toast(L.f("profiles.loaded", name));
				}
				boolean confirming = confirmDelete.equals(name);
				if (ui.button("profile_del:" + name, x + w - 52f, y + 4f, 48f, 16f, confirming ? L.t("common.confirm") : L.t("common.delete"), false)) {
					if (confirming) {
						Profiles.delete(name);
						confirmDelete = "";
					} else {
						confirmDelete = name;
					}
				}
			}
			y += 28f;
		}
		return y - top;
	}

	private ProfilePage() {
	}
}
