package dev.fishclient.ui;

import net.minecraft.util.Util;

/** Follows a moving target with exponential smoothing, used for HUD counters and scrolling. */
public final class Smooth {
	private double value;
	private double target;
	private long last = Util.getMillis();
	private final double halfLifeMs;

	public Smooth(double initial, double halfLifeMs) {
		this.value = initial;
		this.target = initial;
		this.halfLifeMs = halfLifeMs;
	}

	public void target(double target) {
		this.target = target;
	}

	public double target() {
		return this.target;
	}

	public void snap(double value) {
		this.value = value;
		this.target = value;
	}

	public double get() {
		long now = Util.getMillis();
		long dt = Math.min(now - this.last, 250L);
		this.last = now;
		if (dt > 0) {
			double keep = Math.pow(0.5, dt / this.halfLifeMs);
			this.value = this.target + (this.value - this.target) * keep;
			if (Math.abs(this.value - this.target) < 0.01) {
				this.value = this.target;
			}
		}
		return this.value;
	}
}
