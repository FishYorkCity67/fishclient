package dev.fishclient.hud;

import dev.fishclient.feature.Cps;
import dev.fishclient.ui.Anim;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.Easing;
import dev.fishclient.ui.L;
import dev.fishclient.ui.Theme;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public final class KeystrokesHud extends HudElement {
	private static final float KEY = 18f;
	private static final float GAP = 2f;
	private final Map<String, Anim> pressed = new HashMap<>();

	KeystrokesHud() {
		super("keystrokes", false, 0.01f, 0.62f);
	}

	@Override
	public float width(Draw d) {
		return KEY * 3f + GAP * 2f;
	}

	@Override
	public float height(Draw d) {
		return KEY * 3f + GAP * 3f + 11f;
	}

	@Override
	public void draw(Draw d) {
		Minecraft mc = Minecraft.getInstance();
		var o = mc.options;
		key(d, "W", KEY + GAP, 0f, KEY, KEY, o.keyUp);
		key(d, "A", 0f, KEY + GAP, KEY, KEY, o.keyLeft);
		key(d, "S", KEY + GAP, KEY + GAP, KEY, KEY, o.keyDown);
		key(d, "D", (KEY + GAP) * 2f, KEY + GAP, KEY, KEY, o.keyRight);
		float mouseWidth = (width(d) - GAP) / 2f;
		mouse(d, "LMB", 0f, (KEY + GAP) * 2f, mouseWidth, o.keyAttack, Cps.left());
		mouse(d, "RMB", mouseWidth + GAP, (KEY + GAP) * 2f, mouseWidth, o.keyUse, Cps.right());
		float spaceY = (KEY + GAP) * 3f;
		key(d, L.t("hud.keystrokes.space"), 0f, spaceY, width(d), 11f, o.keyJump);
	}

	private float press(String id, KeyMapping mapping) {
		Anim anim = this.pressed.computeIfAbsent(id, k -> new Anim(0f, 110, Easing.OUT_CUBIC));
		anim.to(mapping.isDown() ? 1f : 0f);
		return anim.get();
	}

	private void key(Draw d, String label, float x, float y, float w, float h, KeyMapping mapping) {
		float p = press(label, mapping);
		d.round(x, y, w, h, 4f, Draw.lerpColor(Theme.hudPanel(), Theme.accent(), p));
		float textY = y + (h - 8f) / 2f;
		d.centered(label, x + w / 2f, textY, h < 12f ? 0.8f : 1f, Draw.lerpColor(Theme.text(), 0xFF06121F, p), true);
	}

	private void mouse(Draw d, String label, float x, float y, float w, KeyMapping mapping, int cps) {
		float p = press(label, mapping);
		d.round(x, y, w, KEY, 4f, Draw.lerpColor(Theme.hudPanel(), Theme.accent(), p));
		int text = Draw.lerpColor(Theme.text(), 0xFF06121F, p);
		d.centered(label, x + w / 2f, y + 2.5f, 0.8f, text, true);
		d.centered(cps + " CPS", x + w / 2f, y + 10f, 0.65f, Draw.lerpColor(Theme.muted(), 0xFF06121F, p), false);
	}
}
