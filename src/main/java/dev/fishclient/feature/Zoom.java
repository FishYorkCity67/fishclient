package dev.fishclient.feature;

import dev.fishclient.FishKeys;
import dev.fishclient.config.FishConfig;
import dev.fishclient.config.Profiles;
import dev.fishclient.ui.Anim;
import dev.fishclient.ui.Easing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/** Smooth field-of-view zoom. The eased progress is time-based, so it stays fluid at any frame rate. */
public final class Zoom {
	private static final Anim PROGRESS = new Anim(0f, 220, Easing.OUT_CUBIC);
	private static final Anim LEVEL = new Anim(4f, 140, Easing.OUT_CUBIC);
	private static boolean held;

	public static void tick(Minecraft mc) {
		FishConfig config = FishConfig.get();
		boolean want = config.zoom && mc.player != null && mc.screen == null && FishKeys.ZOOM.isDown();
		if (want != held) {
			held = want;
			PROGRESS.to(want ? 1f : 0f);
			if (want) {
				LEVEL.snap(config.zoomLevel);
			}
			if (config.zoomClick && mc.player != null) {
				mc.getSoundManager().play(SimpleSoundInstance.forUI(want ? SoundEvents.SPYGLASS_USE : SoundEvents.SPYGLASS_STOP_USING, 1f, 0.7f));
			}
		}
		// The key may be released while a screen is open, and no click is ever pending for it.
		while (FishKeys.ZOOM.consumeClick()) {
		}
	}

	public static boolean held() {
		return held;
	}

	/** Divides the field of view: 1 means no zoom. */
	public static float fovDivisor() {
		float p = PROGRESS.get();
		if (p <= 0.001f) {
			return 1f;
		}
		return Mth.lerp(p, 1f, Math.max(1f, LEVEL.get()));
	}

	/** Mouse speed factor while zoomed. */
	public static double sensitivity() {
		float p = PROGRESS.get();
		if (p <= 0.001f) {
			return 1d;
		}
		float slowest = FishConfig.get().zoomSensitivity / Math.max(1f, LEVEL.get() / 4f);
		return Mth.lerp(p, 1f, slowest);
	}

	/** Returns true when the wheel was used for zooming and must not change the hotbar slot. */
	public static boolean scroll(double amount) {
		FishConfig config = FishConfig.get();
		if (!held || !config.zoomScroll || amount == 0d) {
			return false;
		}
		float next = Mth.clamp(config.zoomLevel * (amount > 0d ? 1.2f : 1f / 1.2f), 1.2f, 40f);
		config.zoomLevel = next;
		LEVEL.to(next);
		Profiles.markDirty();
		return true;
	}

	private Zoom() {
	}
}
