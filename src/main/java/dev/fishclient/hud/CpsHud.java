package dev.fishclient.hud;

import dev.fishclient.feature.Cps;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.Theme;

public final class CpsHud extends HudElement {
	CpsHud() {
		super("cps", false, 0.01f, 0.09f);
	}

	@Override
	public float width(Draw d) {
		return 68f;
	}

	@Override
	public float height(Draw d) {
		return 20f;
	}

	@Override
	public void draw(Draw d) {
		HudStyle.box(d, width(d), height(d));
		d.bold(Cps.left() + " | " + Cps.right(), HudStyle.PAD, 6f, Theme.text());
		d.right("CPS", width(d) - HudStyle.PAD, 6f, 1f, Theme.accent(), true);
	}
}
