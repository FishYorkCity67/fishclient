package dev.fishclient.ui;

import net.minecraft.util.Util;

/** A time-based tween that never depends on game ticks. */
public final class Anim {
	private float from;
	private float to;
	private long start;
	private final int durationMs;
	private final Easing.Fn easing;

	public Anim(float initial, int durationMs, Easing.Fn easing) {
		this.from = initial;
		this.to = initial;
		this.durationMs = durationMs;
		this.easing = easing;
		this.start = Util.getMillis() - durationMs;
	}

	public void to(float target) {
		if (target == this.to) {
			return;
		}
		this.from = get();
		this.to = target;
		this.start = Util.getMillis();
	}

	public void snap(float value) {
		this.from = value;
		this.to = value;
		this.start = Util.getMillis() - this.durationMs;
	}

	public float get() {
		float t = (Util.getMillis() - this.start) / (float) this.durationMs;
		if (t >= 1f) {
			return this.to;
		}
		return this.from + (this.to - this.from) * this.easing.apply(Math.max(t, 0f));
	}

	public float target() {
		return this.to;
	}

	public boolean done() {
		return Util.getMillis() - this.start >= this.durationMs;
	}
}
