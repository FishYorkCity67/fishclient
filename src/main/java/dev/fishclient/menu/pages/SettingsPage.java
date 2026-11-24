package dev.fishclient.menu.pages;

import dev.fishclient.FishKeys;
import dev.fishclient.config.FishConfig;
import dev.fishclient.feature.ChatWatcher;
import dev.fishclient.feature.Money;
import dev.fishclient.menu.Ui;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.L;
import dev.fishclient.ui.Theme;
import java.util.List;

public final class SettingsPage {
	private static int serverIndex;
	private static String testLine = "";

	public static float draw(Ui ui, float x, float y, float w) {
		FishConfig c = FishConfig.get();
		Draw d = ui.d;
		float top = y;
		y = ui.heading(L.t("tab.settings"), x, y);

		ui.card(x, y, w, 26f);
		d.text(L.t("settings.menu_key"), x + 10f, y + 9f, Theme.text());
		ui.keybind("menu_key", x + w - 100f, y + 4f, 94f, 18f, FishKeys.MENU);
		y += 32f;
		y = ui.toggleRow("ui_sounds", x, y, w, L.t("settings.ui_sounds"), L.t("settings.ui_sounds.desc"), c.uiSounds, true, v -> c.uiSounds = v);
		y = ui.toggleRow("water_sounds", x, y, w, L.t("settings.water_sounds"), L.t("settings.water_sounds.desc"), c.waterSounds, true, v -> c.waterSounds = v);
		d.text(L.t("settings.rank"), x, y + 5f, Theme.text());
		String rank = ui.input("rank", x + 90f, y, w - 90f, 18f, c.rank, L.t("settings.rank"));
		if (!rank.equals(c.rank)) {
			c.rank = rank;
			Ui.changed();
		}
		y += 28f;

		y = ui.section(L.t("settings.patterns"), x, y);
		y = ui.paragraph(L.t("settings.patterns.desc"), x, y, w) + 4f;
		List<FishConfig.ServerPatterns> servers = c.servers;
		if (servers.isEmpty()) {
			return y - top;
		}
		serverIndex = Math.clamp(serverIndex, 0, servers.size() - 1);
		String[] names = new String[servers.size()];
		for (int i = 0; i < names.length; i++) {
			names[i] = servers.get(i).name;
		}
		float[] next = new float[1];
		serverIndex = ui.pills("server_pick", x, y, w, names, serverIndex, next);
		y = next[0];
		FishConfig.ServerPatterns s = servers.get(serverIndex);

		String id = "srv" + serverIndex;
		s.addressContains = field(ui, id + "_addr", x, y, w, L.t("settings.address"), s.addressContains);
		y += 22f;
		s.payCommand = field(ui, id + "_pay", x, y, w, L.t("settings.pay_command"), s.payCommand);
		y += 26f;
		y = list(ui, id + "_rx", x, y, w, L.t("transactions.received"), s.received);
		y = list(ui, id + "_tx", x, y, w, L.t("transactions.sent"), s.sent);
		y = list(ui, id + "_sold", x, y, w, L.t("settings.sold"), s.sold);
		y = list(ui, id + "_bought", x, y, w, L.t("settings.bought"), s.bought);

		y = ui.section(L.t("settings.tester"), x, y + 2f);
		testLine = ui.input("pattern_test", x, y, w, 18f, testLine, L.t("settings.tester_placeholder"));
		y += 22f;
		if (!testLine.isBlank()) {
			ChatWatcher.Match m = ChatWatcher.parse(testLine, s);
			if (m == null) {
				d.text(L.t("settings.tester_none"), x, y, Theme.BAD);
			} else {
				d.text(describe(m), x, y, Theme.GOOD);
			}
			y += 14f;
		}
		return y - top;
	}

	private static String describe(ChatWatcher.Match m) {
		StringBuilder b = new StringBuilder(L.t("settings.kind." + m.kind())).append(": ").append(Money.format(m.amount()));
		if (m.player() != null) {
			b.append("  ·  ").append(m.player());
		}
		if (m.item() != null) {
			b.append("  ·  ").append(m.item());
		}
		return b.toString();
	}

	private static String field(Ui ui, String id, float x, float y, float w, String label, String value) {
		ui.d.text(label, x, y + 5f, 0.85f, Theme.muted(), false);
		String edited = ui.input(id, x + 90f, y, w - 90f, 18f, value, "");
		if (!edited.equals(value)) {
			Ui.changed();
		}
		return edited;
	}

	private static float list(Ui ui, String id, float x, float y, float w, String label, List<String> patterns) {
		ui.d.text(label, x, y, Theme.text());
		if (ui.iconButton(id + "_add", x + w - 15f, y - 3f, 14f, "plus", L.t("common.add"))) {
			patterns.add("");
			Ui.changed();
		}
		y += 13f;
		Integer remove = null;
		for (int i = 0; i < patterns.size(); i++) {
			String edited = ui.input(id + i, x, y, w - 20f, 17f, patterns.get(i), L.t("settings.regex"));
			if (!edited.equals(patterns.get(i))) {
				patterns.set(i, edited);
				Ui.changed();
			}
			if (!ChatWatcher.validRegex(edited)) {
				ui.d.round(x + w - 26f, y + 6f, 5f, 5f, 2.5f, Theme.BAD);
			}
			if (ui.iconButton(id + i + "_del", x + w - 17f, y + 1f, 15f, "trash", L.t("common.delete"))) {
				remove = i;
			}
			y += 20f;
		}
		if (remove != null) {
			patterns.remove(remove.intValue());
			Ui.changed();
		}
		return y + 3f;
	}

	private SettingsPage() {
	}
}
