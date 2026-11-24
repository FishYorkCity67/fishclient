package dev.fishclient.hud;

import dev.fishclient.config.FishConfig;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.L;

/** A movable HUD box. Subclasses draw at the origin; scale and position come from the profile. */
public abstract class HudElement {
	public final String id;
	private final boolean defaultEnabled;
	private final float defaultX;
	private final float defaultY;

	protected HudElement(String id, boolean defaultEnabled, float defaultX, float defaultY) {
		this.id = id;
		this.defaultEnabled = defaultEnabled;
		this.defaultX = defaultX;
		this.defaultY = defaultY;
	}

	public String title() {
		return L.t("hud." + this.id);
	}

	public FishConfig.HudPos pos() {
		return FishConfig.get().hud.computeIfAbsent(this.id, k -> new FishConfig.HudPos(this.defaultEnabled, this.defaultX, this.defaultY));
	}

	public void resetPosition() {
		FishConfig.HudPos p = pos();
		p.x = this.defaultX;
		p.y = this.defaultY;
		p.scale = 1f;
	}

	/** Whether there is anything to show right now, for example a scoreboard being present. */
	public boolean visible() {
		return true;
	}

	public abstract float width(Draw d);

	public abstract float height(Draw d);

	public abstract void draw(Draw d);

	/** The element's own size setting on top of the GUI-scale-independent unit. */
	public final float scale() {
		return pos().scale * Draw.unit();
	}

	public final float screenX(Draw d) {
		return pos().x * Math.max(0f, d.g.guiWidth() - width(d) * scale());
	}

	public final float screenY(Draw d) {
		return pos().y * Math.max(0f, d.g.guiHeight() - height(d) * scale());
	}

	public final void render(Draw d) {
		float scale = scale();
		d.g.pose().pushMatrix();
		d.g.pose().translate(screenX(d), screenY(d));
		d.g.pose().scale(scale, scale);
		draw(d);
		d.g.pose().popMatrix();
	}
}
