package dev.fishclient.hud;

import dev.fishclient.feature.Money;
import dev.fishclient.feature.Transactions;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.L;
import dev.fishclient.ui.Smooth;
import dev.fishclient.ui.Theme;

/** Session money: the numbers count towards their new value instead of jumping. */
public final class ProfitHud extends HudElement {
	private final Smooth received = new Smooth(0d, 110d);
	private final Smooth sent = new Smooth(0d, 110d);
	private final Smooth profit = new Smooth(0d, 110d);

	ProfitHud() {
		super("profit", true, 0.01f, 0.25f);
	}

	@Override
	public float width(Draw d) {
		return 124f;
	}

	@Override
	public float height(Draw d) {
		return 56f;
	}

	@Override
	public void draw(Draw d) {
		float w = width(d);
		HudStyle.titled(d, w, height(d), "coin", L.t("hud.profit"));
		this.received.target(Transactions.sessionReceived());
		this.sent.target(Transactions.sessionSent());
		this.profit.target(Transactions.sessionProfit());
		double total = this.profit.get();
		row(d, w, 18f, L.t("transactions.received"), Money.format(this.received.get()), Theme.GOOD);
		row(d, w, 29f, L.t("transactions.sent"), Money.format(this.sent.get()), Theme.BAD);
		d.rect(HudStyle.PAD, 40f, w - HudStyle.PAD * 2f, 0.5f, Theme.line());
		row(d, w, 43f, L.t("transactions.profit"), Money.signed(total), total >= 0d ? Theme.GOOD : Theme.BAD);
	}

	private static void row(Draw d, float w, float y, String label, String value, int color) {
		d.text(label, HudStyle.PAD, y, Theme.muted());
		d.right(value, w - HudStyle.PAD, y, 1f, color, true);
	}
}
