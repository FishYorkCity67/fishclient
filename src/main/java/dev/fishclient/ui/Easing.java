package dev.fishclient.ui;

/** Easing curves. Every input and output is a progress value from 0 to 1. */
public final class Easing {
	@FunctionalInterface
	public interface Fn {
		float apply(float t);
	}

	public static final Fn LINEAR = t -> t;
	public static final Fn OUT_CUBIC = t -> 1f - cube(1f - t);
	public static final Fn OUT_QUINT = t -> 1f - cube(1f - t) * (1f - t) * (1f - t);
	public static final Fn IN_OUT_CUBIC = t -> t < 0.5f ? 4f * cube(t) : 1f - cube(-2f * t + 2f) / 2f;
	public static final Fn IN_CUBIC = Easing::cube;
	public static final Fn OUT_BACK = t -> {
		float c1 = 1.15f;
		float c3 = c1 + 1f;
		float u = t - 1f;
		return 1f + c3 * cube(u) + c1 * u * u;
	};

	private static float cube(float v) {
		return v * v * v;
	}

	private Easing() {
	}
}
