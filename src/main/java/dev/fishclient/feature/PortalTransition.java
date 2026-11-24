package dev.fishclient.feature;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import dev.fishclient.FishClient;
import dev.fishclient.config.FishConfig;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.Easing;
import dev.fishclient.ui.L;
import dev.fishclient.ui.Textures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Replaces the loading screen of a portal trip: the last frame zooms into the portal and fades into the portal's
 * colour, then into the remembered view of the destination when there is one. Once the world is ready, that picture
 * fades away over the real game.
 */
public final class PortalTransition {
	private static final Identifier LAST_FRAME = FishClient.id("dynamic/portal_last_frame");
	private static final long FADE_OUT_MS = 700L;
	private static final long MIN_MS = 1400L;

	private static boolean active;
	private static boolean nether;
	private static long start;
	private static long loadedAt;
	private static boolean hasFrame;
	private static float frameAspect = 16f / 9f;
	private static PortalViews.@Nullable Entry destination;
	private static float yaw;

	private static int trips;

	public static boolean active() {
		return active;
	}

	/** How many portal trips used the transition so far. Only the automated test reads this. */
	public static int trips() {
		return trips;
	}

	/** Whether the current transition had a remembered view of the destination to show. */
	public static boolean hasDestination() {
		return destination != null;
	}

	/** Called when a portal loading screen appears. */
	public static void begin(LevelLoadingScreen.Reason reason) {
		if (!FishConfig.get().portalTransition || reason == LevelLoadingScreen.Reason.OTHER) {
			return;
		}
		active = true;
		trips++;
		nether = reason == LevelLoadingScreen.Reason.NETHER_PORTAL;
		start = Util.getMillis();
		loadedAt = 0L;
		hasFrame = false;
		destination = nether ? PortalViews.departureEntry() : null;
		yaw = PortalViews.departureYaw();
		Minecraft mc = Minecraft.getInstance();
		RenderTarget target = mc.getMainRenderTarget();
		int factor = target.width % 2 == 0 && target.height % 2 == 0 ? 2 : 1;
		frameAspect = target.width / (float) Math.max(1, target.height);
		// The main target still holds the last frame of the old dimension.
		Screenshot.takeScreenshot(target, factor, image -> {
			mc.getTextureManager().register(LAST_FRAME, new DynamicTexture(() -> "fishclient portal frame", image));
			hasFrame = true;
		});
	}

	public static void tick(Minecraft mc) {
		if (active && loadedAt == 0L && !(mc.screen instanceof LevelLoadingScreen)) {
			loadedAt = Util.getMillis();
		}
	}

	/** Draws the transition in place of the loading screen. */
	public static void drawLoading(GuiGraphicsExtractor g) {
		Draw d = new Draw(g);
		float w = g.guiWidth();
		float h = g.guiHeight();
		long t = Util.getMillis() - start;
		int tint = nether ? 0xFF5A1E8C : 0xFF06121F;
		// Until the copy of the last frame arrives (a frame later) the screen shows the portal colour, never black.
		d.rect(0f, 0f, w, h, hasFrame ? 0xFF000000 : Draw.mulAlpha(tint, 0.85f));
		// 1. The last frame grows as if the camera flew into the portal.
		if (hasFrame) {
			float zoom = 1f + 0.5f * Easing.IN_CUBIC.apply(Math.min(t / 1100f, 1f));
			float alpha = 1f - Math.clamp((t - 350f) / 800f, 0f, 1f);
			drawCover(d, LAST_FRAME, w, h, frameAspect, zoom, alpha, 0f, 0f, 1f, 1f);
		}
		// 2. The portal's own colour washes over it.
		float glow = Math.clamp((t - 200f) / 700f, 0f, 1f);
		boolean preview = destination != null && PortalViews.texture(destination.view) != null;
		if (preview) {
			glow *= 1f - Math.clamp((t - 900f) / 600f, 0f, 1f);
		}
		d.rect(0f, 0f, w, h, Draw.mulAlpha(tint, glow * 0.85f));
		// 3. Then the remembered destination comes towards the player.
		if (preview) {
			drawDestination(d, w, h, Math.clamp((t - 700f) / 700f, 0f, 1f), 1.3f - 0.22f * Easing.OUT_CUBIC.apply(Math.min(t / 2500f, 1f)));
		}
		float textAlpha = Math.clamp((t - 600f) / 400f, 0f, 1f) * (preview ? 0.6f : 1f);
		int dots = (int) (t / 400L % 4L);
		d.centered(L.t("portals.travelling") + ".".repeat(dots), w / 2f, h - 28f, 1f, Draw.mulAlpha(0xFFFFFFFF, textAlpha), false);
	}

	/** After loading, the picture fades out over the game. Drawn as part of the HUD. */
	public static void drawOverlay(GuiGraphicsExtractor g) {
		if (!active || loadedAt == 0L) {
			return;
		}
		// A fast load (singleplayer, a nearby server) still gets the whole fly-in: it carries on over the game.
		long fadeStart = Math.max(loadedAt, start + MIN_MS);
		long now = Util.getMillis();
		if (now < fadeStart) {
			drawLoading(g);
			return;
		}
		long t = now - fadeStart;
		if (t >= FADE_OUT_MS) {
			active = false;
			return;
		}
		Draw d = new Draw(g);
		float a = 1f - Easing.OUT_CUBIC.apply(t / (float) FADE_OUT_MS);
		float w = g.guiWidth();
		float h = g.guiHeight();
		if (destination != null && PortalViews.texture(destination.view) != null) {
			drawDestination(d, w, h, a, 1.08f - 0.08f * (t / (float) FADE_OUT_MS));
		} else {
			// The same layers as the loading picture, so the hand-over has no jump.
			d.rect(0f, 0f, w, h, Draw.mulAlpha(0xFF000000, a));
			d.rect(0f, 0f, w, h, Draw.mulAlpha(nether ? 0xFF5A1E8C : 0xFF06121F, a * 0.85f));
		}
	}

	/** The panorama face that matches where the player was looking, cropped to the screen. */
	private static void drawDestination(Draw d, float w, float h, float alpha, float zoom) {
		PortalViews.Entry entry = destination;
		Identifier texture = entry != null ? PortalViews.texture(entry.view) : null;
		if (texture == null || alpha <= 0f) {
			return;
		}
		// Face 0 looks straight out of the far portal; the others turn by quarters from there.
		int quarter = Math.floorMod(Math.round((yaw - entry.viewYaw) / 90f), 4);
		int face = switch (quarter) {
			case 1 -> 1;
			case 2 -> 2;
			case 3 -> 3;
			default -> 0;
		};
		float u0 = (face % 3) / 3f;
		float v0 = (face / 3) / 2f;
		drawCover(d, texture, w, h, 1f, zoom, alpha, u0, v0, u0 + 1f / 3f, v0 + 0.5f);
	}

	/** Draws a texture region so it covers the screen, cropped to keep its proportions. */
	private static void drawCover(Draw d, Identifier texture, float w, float h, float aspect, float zoom, float alpha,
		float u0, float v0, float u1, float v1) {
		float screen = w / h;
		float du = u1 - u0;
		float dv = v1 - v0;
		if (aspect > screen) {
			float keep = screen / aspect;
			u0 += du * (1f - keep) / 2f;
			du *= keep;
		} else {
			float keep = aspect / screen;
			v0 += dv * (1f - keep) / 2f;
			dv *= keep;
		}
		float cropU = du * (1f - 1f / zoom) / 2f;
		float cropV = dv * (1f - 1f / zoom) / 2f;
		d.textured(Textures.of(texture), 0f, 0f, w, h, u0 + cropU, v0 + cropV, u0 + du - cropU, v0 + dv - cropV, Draw.mulAlpha(0xFFFFFFFF, alpha));
	}

	private PortalTransition() {
	}
}
