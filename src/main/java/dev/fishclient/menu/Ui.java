package dev.fishclient.menu;

import dev.fishclient.config.FishConfig;
import dev.fishclient.config.Profiles;
import dev.fishclient.ui.Anim;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.Easing;
import dev.fishclient.ui.L;
import dev.fishclient.ui.Smooth;
import dev.fishclient.ui.Sounds;
import dev.fishclient.ui.Theme;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Util;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * An immediate-mode widget kit: pages redraw every frame and widgets answer "was I clicked?" directly. Animation
 * state lives here, keyed by widget id, which keeps every hover, toggle and scroll time-based and independent.
 */
public final class Ui {
	public static final float ROW_GAP = 6f;

	/** Receives each widget's id and centre in GUI coordinates. Only the automated test sets this. */
	@FunctionalInterface
	public interface WidgetListener {
		void widget(String id, float guiX, float guiY);
	}

	@Nullable
	public static WidgetListener widgetListener;

	public Draw d;
	public float mouseX;
	public float mouseY;
	private boolean clicked;
	private boolean releasePending;
	private float wheel;
	private boolean inputEnabled = true;

	private final Map<String, Anim> anims = new HashMap<>();
	private final Map<String, Smooth> smooths = new HashMap<>();
	private final Map<String, Float> dragOffsets = new HashMap<>();
	private final Deque<float[]> clips = new ArrayDeque<>();
	@Nullable
	private String focus;
	private int caret;
	private long caretBlink;
	private final List<Integer> typed = new ArrayList<>();
	private final List<KeyEvent> keys = new ArrayList<>();
	@Nullable
	private String draggingSlider;
	@Nullable
	private KeyMapping listening;
	private String toast = "";
	private long toastStart;

	// ---- Frame and input plumbing ----

	public void begin(Draw draw, float mx, float my, boolean enabled) {
		this.d = draw;
		this.mouseX = mx;
		this.mouseY = my;
		this.inputEnabled = enabled;
		this.clips.clear();
	}

	public void end() {
		// A click nothing used removes the focus from a text field.
		if (this.clicked && this.focus != null && !this.focusHitThisFrame) {
			this.focus = null;
		}
		// Applied after drawing: a press and release within one frame must not leave a slider attached to the mouse.
		if (this.releasePending) {
			this.releasePending = false;
			this.draggingSlider = null;
		}
		this.focusHitThisFrame = false;
		this.clicked = false;
		this.wheel = 0f;
		this.typed.clear();
		this.keys.clear();
	}

	private boolean focusHitThisFrame;

	public void onClick(int button) {
		if (button == 0) {
			this.clicked = true;
		}
	}

	public void onRelease(int button) {
		if (button == 0) {
			this.releasePending = true;
		}
	}

	public void onWheel(float amount) {
		this.wheel += amount;
	}

	public void onChar(int codepoint) {
		this.typed.add(codepoint);
	}

	/** Returns true when a focused field or a key-assignment took the key, so the screen must not act on it. */
	public boolean onKey(KeyEvent event) {
		if (this.listening != null) {
			if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
				this.listening.setKey(com.mojang.blaze3d.platform.InputConstants.UNKNOWN);
			} else {
				this.listening.setKey(com.mojang.blaze3d.platform.InputConstants.getKey(event));
			}
			KeyMapping.resetMapping();
			Minecraft.getInstance().options.save();
			this.listening = null;
			Profiles.markDirty();
			return true;
		}
		if (this.focus != null) {
			if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
				this.focus = null;
			} else {
				this.keys.add(event);
			}
			return true;
		}
		return false;
	}

	public void toast(String message) {
		this.toast = message;
		this.toastStart = Util.getMillis();
	}

	void drawToast(float centerX, float y) {
		long age = Util.getMillis() - this.toastStart;
		if (this.toast.isEmpty() || age > 3400L) {
			return;
		}
		float a = Math.min(age / 160f, 1f) * (age > 3000L ? 1f - (age - 3000L) / 400f : 1f);
		float previous = this.d.alpha;
		this.d.alpha *= a;
		float w = this.d.width(this.toast) + 18f;
		this.d.round(centerX - w / 2f, y, w, 17f, 8.5f, Draw.withAlpha(Theme.accent3(), 0xF0));
		this.d.centered(this.toast, centerX, y + 4.5f, 1f, 0xFFFFFFFF, false);
		this.d.alpha = previous;
	}

	// ---- Helpers ----

	public Anim anim(String id, float initial, int ms) {
		return this.anims.computeIfAbsent(id, k -> new Anim(initial, ms, Easing.OUT_CUBIC));
	}

	public Smooth smooth(String id, double initial, double halfLife) {
		return this.smooths.computeIfAbsent(id, k -> new Smooth(initial, halfLife));
	}

	public void pushClip(float x, float y, float w, float h) {
		this.clips.push(new float[]{x, y, x + w, y + h});
		this.d.clip(x, y, w, h);
	}

	public void popClip() {
		this.clips.pop();
		this.d.unclip();
	}

	public boolean hovered(float x, float y, float w, float h) {
		if (!this.inputEnabled) {
			return false;
		}
		float[] clip = this.clips.peek();
		if (clip != null && (this.mouseX < clip[0] || this.mouseY < clip[1] || this.mouseX >= clip[2] || this.mouseY >= clip[3])) {
			return false;
		}
		return this.mouseX >= x && this.mouseY >= y && this.mouseX < x + w && this.mouseY < y + h;
	}

	private float hover(String id, boolean over) {
		Anim a = anim("hover:" + id, 0f, 130);
		a.to(over ? 1f : 0f);
		return a.get();
	}

	public boolean click(float x, float y, float w, float h) {
		return this.clicked && hovered(x, y, w, h);
	}

	/** Tells the test listener where a widget is. Costs nothing in normal play. */
	public void note(String id, float x, float y, float w, float h) {
		if (widgetListener != null) {
			Vector2f p = this.d.g.pose().transformPosition(x + w / 2f, y + h / 2f, new Vector2f());
			widgetListener.widget(id, p.x, p.y);
		}
	}

	public float takeWheel(float x, float y, float w, float h) {
		if (this.wheel != 0f && hovered(x, y, w, h)) {
			float amount = this.wheel;
			this.wheel = 0f;
			return amount;
		}
		return 0f;
	}

	public static float radius() {
		return FishConfig.get().radius;
	}

	public static void changed() {
		Profiles.markDirty();
	}

	// ---- Text blocks ----

	public float heading(String text, float x, float y) {
		this.d.bold(text, x, y, Theme.text());
		return y + 14f;
	}

	/** A small caps-style section label with a sparkle, like "EFFECTS". */
	public float section(String text, float x, float y) {
		this.d.icon("star", x, y + 0.5f, 7f, Theme.accent());
		this.d.text(text.toUpperCase(java.util.Locale.ROOT), x + 11f, y, 0.85f, Theme.muted(), true);
		return y + 14f;
	}

	/** Wrapped description text; returns the y below it. */
	public float paragraph(String text, float x, float y, float w) {
		List<String> lines = wrap(text, w / 0.85f);
		for (String line : lines) {
			this.d.text(line, x, y, 0.85f, Theme.muted(), false);
			y += 10f;
		}
		return y;
	}

	private List<String> wrap(String text, float w) {
		List<String> lines = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (String word : text.split(" ")) {
			String attempt = current.isEmpty() ? word : current + " " + word;
			if (this.d.width(attempt) > w && !current.isEmpty()) {
				lines.add(current.toString());
				current = new StringBuilder(word);
			} else {
				current = new StringBuilder(attempt);
			}
		}
		if (!current.isEmpty()) {
			lines.add(current.toString());
		}
		return lines;
	}

	/** Corners for small controls: rounded in proportion, so a thin row never turns into a pill. */
	public static float smallRadius(float h) {
		return Math.min(radius(), h * 0.38f);
	}

	public void card(float x, float y, float w, float h) {
		float r = smallRadius(h);
		this.d.round(x, y, w, h, r, Theme.card());
		this.d.roundOutline(x, y, w, h, r, 1f, Theme.line());
	}

	// ---- Controls ----

	public boolean toggleSwitch(String id, float x, float y, boolean value) {
		float w = 24f;
		float h = 13f;
		note(id, x, y, w, h);
		boolean over = hovered(x - 2f, y - 2f, w + 4f, h + 4f);
		float hv = hover(id, over);
		Anim knob = anim("toggle:" + id, value ? 1f : 0f, 170);
		knob.to(value ? 1f : 0f);
		float t = knob.get();
		int off = Draw.lerpColor(Theme.line(), Theme.faint(), hv * 0.5f);
		this.d.round(x, y, w, h, h / 2f, Draw.lerpColor(off, Theme.accent(), t));
		float kx = x + 1.5f + (w - h) * t;
		this.d.round(kx, y + 1.5f, h - 3f, h - 3f, (h - 3f) / 2f, 0xFFFFFFFF);
		if (over && this.clicked) {
			Sounds.ui(value ? Sounds.TOGGLE_OFF : Sounds.TOGGLE_ON);
			return true;
		}
		return false;
	}

	/**
	 * The standard settings row: title, description, switch and a reset arrow that restores the default.
	 * Returns the y below the row.
	 */
	public float toggleRow(String id, float x, float y, float w, String title, String desc, boolean value, boolean defaultValue, Consumer<Boolean> set) {
		float h = 34f;
		boolean over = hovered(x, y, w, h);
		float hv = hover("row:" + id, over);
		float r = smallRadius(h);
		this.d.round(x, y, w, h, r, Draw.lerpColor(Theme.card(), Theme.cardHover(), hv));
		this.d.roundOutline(x, y, w, h, r, 1f, Theme.line());
		this.d.bold(title, x + 10f, y + 7f, Theme.text());
		float room = w - 62f;
		this.d.text(this.d.fit(desc, room / 0.85f), x + 10f, y + 19f, 0.85f, Theme.muted(), false);
		float sx = x + w - 48f;
		if (toggleSwitch(id, sx, y + 10.5f, value)) {
			set.accept(!value);
			changed();
		}
		if (resetArrow(id, x + w - 20f, y + 11f, value != defaultValue)) {
			set.accept(defaultValue);
			changed();
		}
		return y + h + ROW_GAP;
	}

	public boolean resetArrow(String id, float x, float y, boolean active) {
		float size = 12f;
		note("reset:" + id, x, y, size, size);
		boolean over = active && hovered(x, y, size, size);
		float hv = hover("reset:" + id, over);
		int color = active ? Draw.lerpColor(Theme.muted(), Theme.text(), hv) : Draw.withAlpha(Theme.faint(), 0x80);
		this.d.icon("reset", x + 2f, y + 2f, 8f, color);
		if (over && this.clicked) {
			Sounds.ui(Sounds.CLICK);
			return true;
		}
		return false;
	}

	/** Returns the pill's width through {@code outWidth} when non-null. */
	public boolean pill(String id, float x, float y, String label, boolean active, float @Nullable [] outWidth) {
		float w = this.d.width(label) + 14f;
		float h = 15f;
		if (outWidth != null) {
			outWidth[0] = w;
		}
		note(id, x, y, w, h);
		boolean over = hovered(x, y, w, h);
		float hv = hover(id, over);
		Anim on = anim("pill:" + id, active ? 1f : 0f, 170);
		on.to(active ? 1f : 0f);
		float t = on.get();
		this.d.round(x, y, w, h, h / 2f, Draw.lerpColor(Draw.lerpColor(Theme.card(), Theme.cardHover(), hv), Draw.withAlpha(Theme.accent(), 0x38), t));
		this.d.roundOutline(x, y, w, h, h / 2f, 1f, Draw.lerpColor(Theme.line(), Theme.accent(), t));
		this.d.text(label, x + 7f, y + 3.5f, Draw.lerpColor(Theme.muted(), Theme.text(), Math.max(hv, t)));
		if (over && this.clicked) {
			Sounds.ui(Sounds.CLICK);
			return true;
		}
		return false;
	}

	/** A row of mutually exclusive pills. Returns the selected index, and the wrapped height via {@code outY}. */
	public int pills(String id, float x, float y, float maxW, String[] labels, int selected, float[] outY) {
		float cx = x;
		float cy = y;
		int result = selected;
		float[] width = new float[1];
		for (int i = 0; i < labels.length; i++) {
			float w = this.d.width(labels[i]) + 14f;
			if (cx + w > x + maxW && cx > x) {
				cx = x;
				cy += 19f;
			}
			if (pill(id + ":" + i, cx, cy, labels[i], i == selected, width)) {
				result = i;
			}
			cx += width[0] + 4f;
		}
		outY[0] = cy + 15f + ROW_GAP;
		return result;
	}

	public boolean button(String id, float x, float y, float w, float h, String label, boolean primary) {
		note(id, x, y, w, h);
		boolean over = hovered(x, y, w, h);
		float hv = hover(id, over);
		float r = smallRadius(h);
		if (primary) {
			this.d.glow(x, y, w, h, r, 4f, Draw.withAlpha(Theme.accent(), Math.round(0x18 + 0x38 * hv)));
			this.d.roundH(x, y, w, h, r, Draw.lerpColor(Theme.accent(), Theme.accent2(), hv * 0.5f), Theme.accent2());
			this.d.centered(label, x + w / 2f, y + (h - 8f) / 2f, 1f, 0xFF06121F, true);
		} else {
			this.d.round(x, y, w, h, r, Draw.lerpColor(Theme.card(), Theme.cardHover(), hv));
			this.d.roundOutline(x, y, w, h, r, 1f, Draw.lerpColor(Theme.line(), Theme.accent(), hv * 0.5f));
			this.d.centered(label, x + w / 2f, y + (h - 8f) / 2f, 1f, Theme.text(), false);
		}
		if (over && this.clicked) {
			Sounds.ui(Sounds.CLICK);
			return true;
		}
		return false;
	}

	public float buttonWidth(String label) {
		return this.d.width(label) + 18f;
	}

	public boolean iconButton(String id, float x, float y, float size, String icon, String tooltip) {
		note(id, x, y, size, size);
		boolean over = hovered(x, y, size, size);
		float hv = hover(id, over);
		this.d.round(x, y, size, size, Math.min(radius(), size / 2f), Draw.lerpColor(Draw.withAlpha(Theme.card(), 0), Theme.cardHover(), hv));
		float pad = size * 0.24f;
		this.d.icon(icon, x + pad, y + pad, size - pad * 2f, Draw.lerpColor(Theme.muted(), Theme.text(), hv));
		if (over && !tooltip.isEmpty()) {
			this.pendingTooltip = tooltip;
		}
		if (over && this.clicked) {
			Sounds.ui(Sounds.CLICK);
			return true;
		}
		return false;
	}

	@Nullable
	private String pendingTooltip;

	void drawTooltip() {
		if (this.pendingTooltip != null) {
			float w = this.d.width(this.pendingTooltip) * 0.85f + 10f;
			float x = this.mouseX + 8f;
			float y = this.mouseY - 14f;
			this.d.round(x, y, w, 13f, 4f, 0xF0101828);
			this.d.text(this.pendingTooltip, x + 5f, y + 3f, 0.85f, Theme.text(), false);
			this.pendingTooltip = null;
		}
	}

	/** A one-line text field with a placeholder. Returns the possibly edited value. */
	public String input(String id, float x, float y, float w, float h, String value, String placeholder) {
		note(id, x, y, w, h);
		boolean over = hovered(x, y, w, h);
		boolean focused = id.equals(this.focus);
		if (over && this.clicked) {
			this.focusHitThisFrame = true;
			if (!focused) {
				this.focus = id;
				this.caret = value.length();
				this.caretBlink = Util.getMillis();
				focused = true;
			}
		}
		float hv = hover(id, over || focused);
		Anim focusAnim = anim("focus:" + id, 0f, 150);
		focusAnim.to(focused ? 1f : 0f);
		float r = smallRadius(h);
		this.d.round(x, y, w, h, r, Draw.lerpColor(Theme.card(), Theme.cardHover(), hv));
		this.d.roundOutline(x, y, w, h, r, 1f, Draw.lerpColor(Theme.line(), Theme.accent(), focusAnim.get()));
		String text = value;
		if (focused) {
			text = edit(text);
		}
		float textY = y + (h - 8f) / 2f;
		float room = w - 14f;
		pushClip(x + 4f, y, w - 8f, h);
		if (text.isEmpty() && !focused) {
			this.d.text(placeholder, x + 7f, textY, Theme.faint());
		} else {
			String before = text.substring(0, Math.min(this.caret, text.length()));
			float caretX = focused ? this.d.width(before) : 0f;
			float scroll = Math.max(0f, caretX - room);
			this.d.text(text, x + 7f - scroll, textY, Theme.text());
			if (focused && (Util.getMillis() - this.caretBlink) % 1000L < 520L) {
				this.d.rect(x + 7f - scroll + caretX, textY - 1f, 0.75f, 10f, Theme.accent());
			}
		}
		popClip();
		return text;
	}

	private String edit(String text) {
		StringBuilder b = new StringBuilder(text);
		this.caret = Math.clamp(this.caret, 0, b.length());
		for (int codepoint : this.typed) {
			b.insert(this.caret, new String(Character.toChars(codepoint)));
			this.caret += Character.charCount(codepoint);
			this.caretBlink = Util.getMillis();
		}
		for (KeyEvent event : this.keys) {
			boolean ctrl = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
			switch (event.key()) {
				case GLFW.GLFW_KEY_BACKSPACE -> {
					if (ctrl) {
						b.setLength(0);
						this.caret = 0;
					} else if (this.caret > 0) {
						b.deleteCharAt(--this.caret);
					}
				}
				case GLFW.GLFW_KEY_DELETE -> {
					if (this.caret < b.length()) {
						b.deleteCharAt(this.caret);
					}
				}
				case GLFW.GLFW_KEY_LEFT -> this.caret = Math.max(0, this.caret - 1);
				case GLFW.GLFW_KEY_RIGHT -> this.caret = Math.min(b.length(), this.caret + 1);
				case GLFW.GLFW_KEY_HOME -> this.caret = 0;
				case GLFW.GLFW_KEY_END -> this.caret = b.length();
				case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> this.focus = null;
				case GLFW.GLFW_KEY_V -> {
					if (ctrl) {
						String paste = Minecraft.getInstance().keyboardHandler.getClipboard().replaceAll("[\\r\\n\\t]", " ");
						b.insert(this.caret, paste);
						this.caret += paste.length();
					}
				}
				case GLFW.GLFW_KEY_C -> {
					if (ctrl) {
						Minecraft.getInstance().keyboardHandler.setClipboard(b.toString());
					}
				}
				default -> {
				}
			}
			this.caretBlink = Util.getMillis();
		}
		return b.toString();
	}

	/** A labelled slider row. Returns the possibly changed value, snapped to {@code step}. */
	public float slider(String id, float x, float y, float w, String label, String shown, float value, float min, float max, float step) {
		this.d.text(label, x, y, Theme.text());
		this.d.right(shown, x + w, y, 1f, Theme.muted(), false);
		float trackY = y + 13f;
		float trackH = 4f;
		note(id, x, trackY, w, trackH);
		boolean over = hovered(x, trackY - 4f, w, trackH + 8f);
		if (over && this.clicked) {
			this.draggingSlider = id;
		}
		float result = value;
		if (id.equals(this.draggingSlider)) {
			float t = Math.clamp((this.mouseX - x) / w, 0f, 1f);
			result = Math.round((min + (max - min) * t) / step) * step;
			result = Math.clamp(result, min, max);
			if (result != value) {
				changed();
			}
		}
		Anim pos = anim("slider:" + id, (value - min) / (max - min), 90);
		pos.to((result - min) / (max - min));
		float t = Math.clamp(pos.get(), 0f, 1f);
		float hv = hover(id, over || id.equals(this.draggingSlider));
		this.d.round(x, trackY, w, trackH, 2f, Theme.line());
		this.d.roundH(x, trackY, Math.max(trackH, w * t), trackH, 2f, Theme.accent(), Theme.accent2());
		float knob = 8f + hv * 2f;
		this.d.round(x + w * t - knob / 2f, trackY + trackH / 2f - knob / 2f, knob, knob, knob / 2f, 0xFFFFFFFF);
		return result;
	}

	/** Colour swatches in a row. Returns the picked colour. */
	public int swatches(String id, float x, float y, int[] colors, int selected) {
		int result = selected;
		float size = 15f;
		for (int i = 0; i < colors.length; i++) {
			float sx = x + i * (size + 4f);
			boolean over = hovered(sx, y, size, size);
			float hv = hover(id + ":" + i, over);
			boolean on = (colors[i] & 0xFFFFFF) == (selected & 0xFFFFFF);
			this.d.round(sx, y, size, size, 5f, colors[i]);
			if (on) {
				this.d.roundOutline(sx - 1.5f, y - 1.5f, size + 3f, size + 3f, 6f, 1f, 0xFFFFFFFF);
			} else if (hv > 0.01f) {
				this.d.roundOutline(sx - 1f, y - 1f, size + 2f, size + 2f, 6f, 1f, Draw.mulAlpha(0xFFFFFFFF, hv * 0.6f));
			}
			if (over && this.clicked) {
				Sounds.ui(Sounds.CLICK);
				result = colors[i];
			}
		}
		return result;
	}

	public void keybind(String id, float x, float y, float w, float h, KeyMapping mapping) {
		boolean listen = this.listening == mapping;
		String label = listen ? L.t("settings.press_key") : mapping.getTranslatedKeyMessage().getString();
		if (button(id, x, y, w, h, label, listen)) {
			this.listening = listen ? null : mapping;
		}
	}

	/** A vertical scroll region. Call {@link #endScroll} afterwards with the content's height. */
	public float beginScroll(String id, float x, float y, float w, float h) {
		Smooth s = smooth("scroll:" + id, 0d, 55d);
		pushClip(x, y, w, h);
		float amount = takeWheel(x, y, w, h);
		if (amount != 0f) {
			s.target(s.target() - amount * 26f);
		}
		return y - (float) s.get();
	}

	public void endScroll(String id, float x, float y, float w, float h, float contentHeight) {
		popClip();
		Smooth s = smooth("scroll:" + id, 0d, 55d);
		float max = Math.max(0f, contentHeight - h);
		if (s.target() < 0d) {
			s.target(0d);
		} else if (s.target() > max) {
			s.target(max);
		}
		if (max > 0f) {
			float barH = Math.max(18f, h * h / contentHeight);
			float barY = y + (h - barH) * (float) (s.get() / max);
			float barX = x + w - 3f;
			this.d.round(barX, Math.clamp(barY, y, y + h - barH), 2f, barH, 1f, Draw.withAlpha(Theme.accent3(), 0xA0));
		}
	}

	public void resetScroll(String id) {
		Smooth s = this.smooths.get("scroll:" + id);
		if (s != null) {
			s.snap(0d);
		}
	}
}
