package dev.fishclient.ui;

import dev.fishclient.config.FishConfig;
import java.util.List;
import net.minecraft.util.Util;

/** The selectable colour themes and the blended colours currently in use. */
public final class Theme {
	/** Colours are opaque RGB. {@code deep} tints the panels so every theme keeps a navy base. */
	public record Palette(String id, String name, int accent, int accent2, int accent3, int deep) {
	}

	public static final List<Palette> ALL = List.of(
		new Palette("deep_ocean", "Deep Ocean", 0x2FD6C3, 0x1E9FD8, 0x1867B8, 0x06202E),
		new Palette("limelight", "Limelight", 0xA5E22E, 0xD8F55F, 0x38D57C, 0x15220C),
		new Palette("sunset", "Sunset", 0xFF5C70, 0xFF8C40, 0xB358E6, 0x2A1024),
		new Palette("electric", "Electric", 0x307CFF, 0x30C6FF, 0x6C8DFF, 0x0B1530),
		new Palette("nebula", "Nebula", 0xA06CFF, 0xD1A9FF, 0x7040D1, 0x180E33),
		new Palette("goldrush", "Goldrush", 0xFFB91D, 0xFFDE82, 0xF19B1B, 0x2A1D08),
		new Palette("emerald", "Emerald", 0x20D18C, 0x67F0BF, 0x13A56C, 0x07211A),
		new Palette("plasma", "Plasma", 0xFF30B1, 0x8D5DFF, 0x30E6FF, 0x170B2E),
		new Palette("crimson", "Crimson", 0xFF4C4C, 0xFF8474, 0xD2202B, 0x260B0F)
	);

	private static final int BLEND_MS = 420;
	private static Palette from = ALL.getFirst();
	private static Palette to = ALL.getFirst();
	private static long blendStart = 0L;

	public static final int GOOD = 0xFF3ADB8F;
	public static final int WARN = 0xFFF2C14E;
	public static final int BAD = 0xFFF0565B;

	public static Palette byId(String id) {
		for (Palette p : ALL) {
			if (p.id().equals(id)) {
				return p;
			}
		}
		return ALL.getFirst();
	}

	public static void apply(String id, boolean instant) {
		Palette next = byId(id);
		if (instant) {
			from = next;
			to = next;
			blendStart = 0L;
		} else if (next != to) {
			from = to;
			to = next;
			blendStart = Util.getMillis();
		}
	}

	public static Palette current() {
		return to;
	}

	private static float progress() {
		float t = (Util.getMillis() - blendStart) / (float) BLEND_MS;
		return t >= 1f ? 1f : Easing.IN_OUT_CUBIC.apply(Math.max(0f, t));
	}

	private static int blended(int a, int b) {
		return 0xFF000000 | Draw.lerpColor(a, b, progress()) & 0xFFFFFF;
	}

	public static int accent() {
		return blended(from.accent, to.accent);
	}

	public static int accent2() {
		return blended(from.accent2, to.accent2);
	}

	public static int accent3() {
		return blended(from.accent3, to.accent3);
	}

	public static int deep() {
		return blended(from.deep, to.deep);
	}

	private static int navy(int rgb, float tint) {
		return Draw.lerpColor(0xFF000000 | rgb, deep(), tint);
	}

	/** The menu's outer panels; the See-Through switch makes them translucent. */
	public static int panel() {
		int alpha = FishConfig.get().seeThrough ? 0xD8 : 0xFF;
		return Draw.withAlpha(navy(0x0A1022, 0.25f), alpha);
	}

	public static int panelEdge() {
		return Draw.withAlpha(navy(0x2A3350, 0.3f), 0xC0);
	}

	public static int card() {
		int alpha = FishConfig.get().seeThrough ? 0xB0 : 0xFF;
		return Draw.withAlpha(navy(0x121A30, 0.2f), alpha);
	}

	public static int cardHover() {
		int alpha = FishConfig.get().seeThrough ? 0xD0 : 0xFF;
		return Draw.withAlpha(navy(0x1A2440, 0.25f), alpha);
	}

	public static int line() {
		return Draw.withAlpha(navy(0x2A3350, 0.25f), 0xA0);
	}

	public static int hudPanel() {
		return Draw.withAlpha(navy(0x0A1022, 0.3f), 0xA8);
	}

	public static int text() {
		return 0xFFEEF2FB;
	}

	public static int muted() {
		return 0xFF9CA6BF;
	}

	public static int faint() {
		return 0xFF67728F;
	}

	private Theme() {
	}
}
