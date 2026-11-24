package dev.fishclient.feature;

import dev.fishclient.FishKeys;
import dev.fishclient.config.FishConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/** Moves only the camera: the player keeps facing the same way and the server sees no rotation. */
public final class Freelook {
	private static boolean active;
	private static boolean toggled;
	private static float yaw;
	private static float pitch;
	@Nullable
	private static CameraType restoreType;

	public static boolean active() {
		return active;
	}

	public static float yaw() {
		return yaw;
	}

	public static float pitch() {
		return pitch;
	}

	public static void tick(Minecraft mc) {
		FishConfig config = FishConfig.get();
		LocalPlayer player = mc.player;
		if (FishKeys.FREELOOK.consumeClick() && config.freelookToggle) {
			toggled = !toggled;
		}
		boolean want = config.freelook && player != null && mc.screen == null
			&& (config.freelookToggle ? toggled : FishKeys.FREELOOK.isDown());
		if (want && !active) {
			start(mc, player);
		} else if (!want && active && mc.screen == null) {
			stop(mc);
		}
		// Leaving the world ends freelook, otherwise the next world would start with a turned camera.
		if (player == null) {
			toggled = false;
			if (active) {
				stop(mc);
			}
		}
	}

	private static void start(Minecraft mc, LocalPlayer player) {
		active = true;
		yaw = player.getYRot();
		pitch = player.getXRot();
		if (FishConfig.get().freelookThirdPerson && mc.options.getCameraType().isFirstPerson()) {
			restoreType = mc.options.getCameraType();
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		}
	}

	private static void stop(Minecraft mc) {
		active = false;
		if (restoreType != null) {
			mc.options.setCameraType(restoreType);
			restoreType = null;
		}
	}

	/** Takes the same deltas {@code Entity.turn} would get. */
	public static void turn(double dx, double dy) {
		yaw += (float) dx * 0.15f;
		pitch = Mth.clamp(pitch + (float) dy * 0.15f, -90f, 90f);
	}

	private Freelook() {
	}
}
