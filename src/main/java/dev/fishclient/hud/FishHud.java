package dev.fishclient.hud;

import dev.fishclient.FishClient;
import dev.fishclient.config.FishConfig;
import dev.fishclient.feature.PortalTransition;
import dev.fishclient.feature.Screenshots;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.Easing;
import dev.fishclient.ui.L;
import dev.fishclient.ui.Theme;
import java.io.File;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

/** Registers the mod's HUD with Fabric and draws every enabled element. */
public final class FishHud {
	public static final ProfitHud PROFIT = new ProfitHud();
	public static final FpsPingHud FPS_PING = new FpsPingHud();
	public static final LiveAuctionHud LIVE_AUCTION = new LiveAuctionHud();
	// Drawn in this order, so the auction card comes last and stays on top of everything else.
	public static final List<HudElement> ELEMENTS = List.of(
		PROFIT,
		new TopPayerHud(),
		FPS_PING,
		new KeystrokesHud(),
		new CpsHud(),
		new ArmorHud(),
		new FishingHud(),
		new MusicHud(),
		new MinimapHud(),
		new ScoreboardHud(),
		LIVE_AUCTION
	);

	public static void register() {
		HudElementRegistry.addLast(FishClient.id("hud"), FishHud::extract);
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (graphics, delta) -> {
			if (FishConfig.get().crosshair) {
				CrosshairRenderer.draw(graphics);
			} else {
				original.extractRenderState(graphics, delta);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, original -> (graphics, delta) -> {
			if (!FishConfig.get().scoreboard) {
				original.extractRenderState(graphics, delta);
			}
		});
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}
		if (!mc.options.hideGui) {
			Draw d = new Draw(graphics);
			FloatingLyrics.draw(d, delta);
			WaypointMarkers.draw(d, delta);
			for (HudElement element : ELEMENTS) {
				if (element.pos().enabled && element.visible()) {
					element.render(d);
				}
			}
			drawScreenshotToast(d);
		}
		// Above everything else, so the portal picture can fade away over the whole screen.
		PortalTransition.drawOverlay(graphics);
	}

	private static void drawScreenshotToast(Draw d) {
		File file = Screenshots.preview();
		if (file == null) {
			return;
		}
		long age = Util.getMillis() - Screenshots.previewStart();
		if (age > 5000L) {
			Screenshots.clearPreview();
			return;
		}
		float in = Easing.OUT_CUBIC.apply(Math.min(age / 240f, 1f));
		float out = age > 4600L ? 1f - (age - 4600L) / 400f : 1f;
		Screenshots.Thumb thumb = Screenshots.thumb(file);
		float unit = Draw.unit();
		float w = 136f;
		float h = 98f;
		float x = d.g.guiWidth() / unit - w - 8f + (1f - in) * 30f;
		float y = 8f;
		float previous = d.alpha;
		d.alpha = in * out;
		d.g.pose().pushMatrix();
		d.g.pose().scale(unit, unit);
		d.round(x, y, w, h, 8f, Theme.hudPanel());
		d.roundOutline(x, y, w, h, 8f, 1f, Theme.panelEdge());
		if (thumb != null) {
			float tw = w - 12f;
			float th = Math.min(tw * thumb.height() / thumb.width(), 66f);
			d.texture(thumb.id(), x + 6f, y + 6f, tw, th, 0xFFFFFFFF);
		}
		d.bold(L.t("screenshot.saved"), x + 7f, y + 74f, Theme.text());
		d.text(L.t("screenshot.saved_hint"), x + 7f, y + 85f, 0.8f, Theme.muted(), false);
		d.g.pose().popMatrix();
		d.alpha = previous;
	}

	private FishHud() {
	}
}
