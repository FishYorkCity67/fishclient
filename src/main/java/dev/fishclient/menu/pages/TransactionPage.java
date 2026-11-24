package dev.fishclient.menu.pages;

import dev.fishclient.config.FishConfig;
import dev.fishclient.feature.Auctions;
import dev.fishclient.feature.Money;
import dev.fishclient.feature.Transactions;
import dev.fishclient.hud.FishHud;
import dev.fishclient.menu.Ui;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.L;
import dev.fishclient.ui.Smooth;
import dev.fishclient.ui.Theme;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public final class TransactionPage {
	private static final SimpleDateFormat TIME = new SimpleDateFormat("dd.MM. HH:mm");

	public static float draw(Ui ui, float x, float y, float w) {
		FishConfig c = FishConfig.get();
		Draw d = ui.d;
		float top = y;
		y = ui.heading(L.t("tab.transactions"), x, y);
		y = ui.paragraph(L.t("transactions.desc"), x, y, w) + 4f;

		float gap = 6f;
		float tile = (w - gap * 2f) / 3f;
		stat(ui, "rx", x, y, tile, L.t("transactions.received"), Transactions.sessionReceived(), Theme.GOOD);
		stat(ui, "tx", x + tile + gap, y, tile, L.t("transactions.sent"), Transactions.sessionSent(), Theme.BAD);
		double profit = Transactions.sessionProfit();
		stat(ui, "pf", x + (tile + gap) * 2f, y, tile, L.t("transactions.profit"), profit, profit >= 0d ? Theme.GOOD : Theme.BAD);
		y += 40f;

		y = ui.toggleRow("profit_hud", x, y, w, L.t("transactions.profit_display"), L.t("transactions.profit_display.desc"), FishHud.PROFIT.pos().enabled, true, v -> FishHud.PROFIT.pos().enabled = v);

		y = ui.section(L.t("transactions.top_payers"), x, y);
		List<Transactions.Payer> payers = Transactions.topPayers(false);
		if (payers.isEmpty()) {
			d.text(L.t("transactions.no_payers"), x, y, Theme.faint());
			y += 14f;
		} else {
			for (int i = 0; i < Math.min(5, payers.size()); i++) {
				Transactions.Payer p = payers.get(i);
				ui.card(x, y, w, 19f);
				d.bold("#" + (i + 1), x + 8f, y + 5.5f, i == 0 ? Theme.accent() : Theme.muted());
				d.text(p.name(), x + 30f, y + 5.5f, Theme.text());
				d.right(Money.format(p.total()) + "  ·  " + L.f("transactions.times", p.count()), x + w - 8f, y + 5.5f, 1f, Theme.muted(), false);
				y += 22f;
			}
		}
		y += 4f;

		y = ui.section(L.t("transactions.history"), x, y);
		float[] next = new float[1];
		int picked = ui.pills("min_filter", x, y, w, Transactions.MIN_LABELS, c.minAmountFilter, next);
		if (picked != c.minAmountFilter) {
			c.minAmountFilter = picked;
			Ui.changed();
		}
		y = next[0];
		String filter = ui.input("player_filter", x, y, w - 150f, 18f, c.playerFilter, L.t("transactions.player_placeholder"));
		if (!filter.equals(c.playerFilter)) {
			c.playerFilter = filter;
			Ui.changed();
		}
		float bw = 70f;
		if (ui.button("reset_session", x + w - bw * 2f - 4f, y, bw, 18f, L.t("transactions.reset"), false)) {
			Transactions.resetSession();
			Auctions.resetSession();
			ui.toast(L.t("transactions.reset_done"));
		}
		if (ui.button("clear_history", x + w - bw, y, bw, 18f, L.t("transactions.clear"), false)) {
			Transactions.clearHistory();
			ui.toast(L.t("transactions.clear_done"));
		}
		y += 24f;

		List<Transactions.Entry> rows = Transactions.filtered();
		if (rows.isEmpty()) {
			d.text(L.t("transactions.empty"), x, y + 2f, Theme.faint());
			y += 16f;
		}
		for (int i = 0; i < Math.min(rows.size(), 60); i++) {
			Transactions.Entry e = rows.get(i);
			ui.card(x, y, w, 19f);
			d.round(x + 7f, y + 7f, 5f, 5f, 2.5f, e.received ? Theme.GOOD : Theme.BAD);
			d.text(e.player, x + 17f, y + 5.5f, Theme.text());
			d.right(TIME.format(new Date(e.time)), x + w - 86f, y + 5.5f, 0.85f, Theme.faint(), false);
			d.right((e.received ? "+" : "-") + Money.format(e.amount), x + w - 8f, y + 5.5f, 1f, e.received ? Theme.GOOD : Theme.BAD, true);
			y += 22f;
		}
		return y - top;
	}

	private static void stat(Ui ui, String id, float x, float y, float w, String label, double value, int color) {
		ui.card(x, y, w, 34f);
		ui.d.text(label.toUpperCase(java.util.Locale.ROOT), x + 8f, y + 7f, 0.75f, Theme.muted(), false);
		Smooth s = ui.smooth("stat:" + id, value, 110d);
		s.target(value);
		ui.d.bold(Money.format(s.get()), x + 8f, y + 18f, color);
	}

	private TransactionPage() {
	}
}
