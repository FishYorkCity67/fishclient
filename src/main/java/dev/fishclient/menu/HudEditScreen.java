package dev.fishclient.menu;

import dev.fishclient.config.Profiles;
import dev.fishclient.hud.FishHud;
import dev.fishclient.hud.HudElement;
import dev.fishclient.ui.Anim;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.Easing;
import dev.fishclient.ui.L;
import dev.fishclient.ui.Theme;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Drag HUD elements around, scroll over one to scale it, middle-click to reset it, right-click to hide it. */
public final class HudEditScreen extends Screen {
	private record Box(float x, float y, float w, float h) {
		boolean contains(double mx, double my) {
			return mx >= this.x && my >= this.y && mx < this.x + this.w && my < this.y + this.h;
		}
	}

	private final Screen parent;
	private final Map<String, Anim> hovers = new HashMap<>();
	private final Map<String, Box> boxes = new HashMap<>();
	@Nullable
	private HudElement dragging;
	private float grabX;
	private float grabY;

	public HudEditScreen(Screen parent) {
		super(Component.literal("FishClient HUD"));
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Draw d = new Draw(graphics);
		d.rect(0f, 0f, this.width, this.height, 0x38000000);

		this.boxes.clear();
		for (HudElement element : FishHud.ELEMENTS) {
			if (!element.pos().enabled) {
				continue;
			}
			float scale = element.scale();
			Box box = new Box(element.screenX(d), element.screenY(d), element.width(d) * scale, element.height(d) * scale);
			this.boxes.put(element.id, box);
			boolean over = box.contains(mouseX, mouseY);
			Anim hover = this.hovers.computeIfAbsent(element.id, k -> new Anim(0f, 130, Easing.OUT_CUBIC));
			hover.to(over || element == this.dragging ? 1f : 0f);
			float r = 6f * scale;
			if (!element.visible()) {
				// Elements with nothing to show right now still need a handle to be movable.
				d.round(box.x, box.y, box.w, box.h, r, Theme.hudPanel());
				d.centered(element.title(), box.x + box.w / 2f, box.y + box.h / 2f - 4f * scale, scale, Theme.muted(), false);
			}
			d.round(box.x, box.y, box.w, box.h, r, Draw.mulAlpha(Draw.withAlpha(Theme.accent(), 0x30), hover.get()));
			d.roundOutline(box.x, box.y, box.w, box.h, r, 1f, Draw.lerpColor(Draw.withAlpha(Theme.line(), 0xFF), Theme.accent(), hover.get()));
		}

		float unit = Draw.unit();
		float cx = this.width / 2f;
		graphics.pose().pushMatrix();
		graphics.pose().translate(cx, 8f);
		graphics.pose().scale(unit, unit);
		float bannerW = Math.max(d.width(L.t("hud.edit_hint")) * 0.85f + 24f, 200f);
		d.round(-bannerW / 2f, 0f, bannerW, 28f, 8f, Theme.panel());
		d.roundOutline(-bannerW / 2f, 0f, bannerW, 28f, 8f, 1f, Theme.panelEdge());
		d.centered(L.t("hud.edit_title"), 0f, 5f, 1f, Theme.text(), true);
		d.centered(L.t("hud.edit_hint"), 0f, 16f, 0.85f, Theme.muted(), false);
		graphics.pose().popMatrix();
	}

	@Nullable
	private HudElement at(double mx, double my) {
		for (int i = FishHud.ELEMENTS.size() - 1; i >= 0; i--) {
			HudElement element = FishHud.ELEMENTS.get(i);
			Box box = this.boxes.get(element.id);
			if (element.pos().enabled && box != null && box.contains(mx, my)) {
				return element;
			}
		}
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		HudElement element = at(event.x(), event.y());
		if (element == null) {
			return false;
		}
		if (event.button() == 1) {
			element.pos().enabled = false;
			Profiles.markDirty();
		} else if (event.button() == 2) {
			element.resetPosition();
			Profiles.markDirty();
		} else if (event.button() == 0) {
			Box box = this.boxes.get(element.id);
			this.dragging = element;
			this.grabX = (float) event.x() - box.x;
			this.grabY = (float) event.y() - box.y;
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.dragging == null) {
			return false;
		}
		Box box = this.boxes.get(this.dragging.id);
		if (box != null) {
			float freeX = Math.max(1f, this.width - box.w);
			float freeY = Math.max(1f, this.height - box.h);
			this.dragging.pos().x = Math.clamp(((float) event.x() - this.grabX) / freeX, 0f, 1f);
			this.dragging.pos().y = Math.clamp(((float) event.y() - this.grabY) / freeY, 0f, 1f);
			Profiles.markDirty();
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		this.dragging = null;
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		HudElement element = at(x, y);
		if (element != null) {
			element.pos().scale = Math.clamp(element.pos().scale + (float) scrollY * 0.05f, 0.5f, 2.5f);
			Profiles.markDirty();
		}
		return true;
	}

	@Override
	public void onClose() {
		Profiles.save();
		this.minecraft.setScreen(this.parent);
	}
}
