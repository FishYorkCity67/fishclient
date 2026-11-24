package dev.fishclient.hud;

import dev.fishclient.feature.Money;
import dev.fishclient.feature.Transactions;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.L;
import dev.fishclient.ui.Theme;
import java.util.List;

/** Whoever paid the most this session, with the two runners-up. */
public final class TopPayerHud extends HudElement {
	private static final int ROWS = 3;

	TopPayerHud() {
		super("top_payer", true, 0.01f, 0.45f);
	}

	@Override
	public float width(Draw d) {
		return 124f;
	}

	@Override
	public float height(Draw d) {
		return 19f + Math.max(1, Math.min(ROWS, Transactions.topPayers(true).size())) * 11f;
	}

	@Override
	public void draw(Draw d) {
		float w = width(d);
		HudStyle.titled(d, w, height(d), "star", L.t("hud.top_payer"));
		List<Transactions.Payer> payers = Transactions.topPayers(true);
		if (payers.isEmpty()) {
			d.text(L.t("transactions.no_payers"), HudStyle.PAD, 18f, Theme.faint());
			return;
		}
		for (int i = 0; i < Math.min(ROWS, payers.size()); i++) {
			Transactions.Payer p = payers.get(i);
			float y = 18f + i * 11f;
			int color = i == 0 ? Theme.text() : Theme.muted();
			String amount = Money.format(p.total());
			d.text(d.fit(p.name(), w - HudStyle.PAD * 2f - d.widthBold(amount) - 4f), HudStyle.PAD, y, color);
			d.right(amount, w - HudStyle.PAD, y, 1f, i == 0 ? Theme.accent() : Theme.muted(), i == 0);
		}
	}
}
