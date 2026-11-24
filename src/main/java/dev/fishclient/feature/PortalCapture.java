package dev.fishclient.feature;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.mojang.logging.LogUtils;
import dev.fishclient.config.FishConfig;
import java.util.function.Consumer;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Takes a six-sided panorama in front of a portal, the same way the game takes its own panorama screenshots, but into
 * memory and at a modest size. The first pass only renders, so chunks in every direction get built; the real pass
 * follows a few ticks later.
 */
public final class PortalCapture {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int WARM_UP_TICKS = 12;

	@Nullable
	private static Vec3 cameraOverride;
	private static PortalViews.@Nullable Portal portal;
	@Nullable
	private static Consumer<PortalViews.View> done;
	private static int yaw;
	private static Vec3 position = Vec3.ZERO;
	private static int countdown = -1;
	private static int size;
	private static final NativeImage[] FACES = new NativeImage[6];
	private static int received;

	/** Where the camera stands while a panorama is rendered, or null the rest of the time. */
	@Nullable
	public static Vec3 cameraOverride() {
		return cameraOverride;
	}

	public static boolean busy() {
		return portal != null;
	}

	public static void start(ClientLevel level, PortalViews.Portal target, Consumer<PortalViews.View> onDone) {
		if (busy()) {
			return;
		}
		portal = target;
		done = onDone;
		yaw = PortalViews.openSideYaw(level, target);
		// 1.6 blocks out of the portal on its open side, at eye height above the portal floor.
		double out = 1.6d;
		Vec3 center = target.center();
		double dx = -Math.sin(Math.toRadians(yaw)) * out;
		double dz = Math.cos(Math.toRadians(yaw)) * out;
		position = new Vec3(center.x + dx, target.minY + 1.62d, center.z + dz);
		size = Math.clamp(FishConfig.get().portalViewSize, 128, 1024);
		received = 0;
		countdown = 0;
	}

	/** Runs between frames, at the end of the client tick. */
	public static void tick(Minecraft mc) {
		if (portal == null) {
			return;
		}
		if (mc.player == null || mc.level == null || Freelook.active()) {
			cancel();
			return;
		}
		if (countdown == 0) {
			render(mc, false);
		} else if (countdown == WARM_UP_TICKS) {
			render(mc, true);
		}
		countdown++;
		if (countdown > WARM_UP_TICKS + 200) {
			LOGGER.warn("FishClient gave up on a portal panorama that never finished");
			cancel();
		}
	}

	private static void cancel() {
		for (int i = 0; i < 6; i++) {
			if (FACES[i] != null) {
				FACES[i].close();
				FACES[i] = null;
			}
		}
		portal = null;
		done = null;
		countdown = -1;
	}

	private static void render(Minecraft mc, boolean grab) {
		LocalPlayer player = mc.player;
		Window window = mc.getWindow();
		RenderTarget target = mc.getMainRenderTarget();
		Camera camera = mc.gameRenderer.getMainCamera();
		int ow = window.getWidth();
		int oh = window.getHeight();
		float xRot = player.getXRot();
		float yRot = player.getYRot();
		float xRotO = player.xRotO;
		float yRotO = player.yRotO;
		CameraType type = mc.options.getCameraType();
		mc.gameRenderer.setRenderBlockOutline(false);
		try {
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			camera.enablePanoramicMode();
			cameraOverride = position;
			window.setWidth(size);
			window.setHeight(size);
			target.resize(size, size);
			for (int i = 0; i < 6; i++) {
				// Faces: outward, then turning 90 degrees three times, then up and down.
				float faceYaw = i < 4 ? yaw + (i == 3 ? -90f : i * 90f) : yaw;
				float facePitch = i == 4 ? -90f : i == 5 ? 90f : 0f;
				player.setYRot(faceYaw);
				player.setXRot(facePitch);
				player.yRotO = faceYaw;
				player.xRotO = facePitch;
				mc.gameRenderer.update(DeltaTracker.ONE, true);
				mc.gameRenderer.extract(DeltaTracker.ONE, true);
				mc.gameRenderer.renderLevel(DeltaTracker.ONE);
				if (grab) {
					int face = i;
					Screenshot.takeScreenshot(target, 1, image -> collect(face, image));
				}
			}
		} catch (Exception e) {
			LOGGER.warn("FishClient could not render a portal panorama", e);
			cancel();
		} finally {
			player.setXRot(xRot);
			player.setYRot(yRot);
			player.xRotO = xRotO;
			player.yRotO = yRotO;
			cameraOverride = null;
			mc.options.setCameraType(type);
			mc.gameRenderer.setRenderBlockOutline(true);
			window.setWidth(ow);
			window.setHeight(oh);
			target.resize(ow, oh);
			camera.disablePanoramicMode();
		}
	}

	private static void collect(int face, NativeImage image) {
		if (portal == null) {
			image.close();
			return;
		}
		FACES[face] = image;
		if (++received < 6) {
			return;
		}
		// A 3 x 2 atlas: outward, left turn, back, right turn in the top row and on, then up and down.
		int s = FACES[0].getWidth();
		NativeImage atlas = new NativeImage(s * 3, s * 2, false);
		for (int i = 0; i < 6; i++) {
			NativeImage f = FACES[i];
			int ox = (i % 3) * s;
			int oy = (i / 3) * s;
			for (int y = 0; y < s; y++) {
				for (int x = 0; x < s; x++) {
					atlas.setPixel(ox + x, oy + y, f.getPixel(x, y));
				}
			}
			f.close();
			FACES[i] = null;
		}
		PortalViews.Portal finished = portal;
		Consumer<PortalViews.View> callback = done;
		int facing = yaw;
		portal = null;
		done = null;
		countdown = -1;
		String file = PortalViews.store(atlas, finished);
		if (callback != null) {
			callback.accept(new PortalViews.View(file, facing));
		}
	}

	private PortalCapture() {
	}
}
