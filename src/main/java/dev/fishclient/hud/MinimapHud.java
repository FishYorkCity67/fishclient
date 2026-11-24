package dev.fishclient.hud;

import dev.fishclient.config.FishConfig;
import dev.fishclient.feature.Minimap;
import dev.fishclient.feature.Waypoints;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.player.LocalPlayer;

public final class MinimapHud extends HudElement {
	MinimapHud() {
		super("minimap", false, 0.99f, 0.11f);
	}

	@Override
	public float width(Draw d) {
		return FishConfig.get().minimapSize;
	}

	@Override
	public float height(Draw d) {
		return FishConfig.get().minimapSize + (FishConfig.get().minimapCoords ? 13f : 0f);
	}

	@Override
	public void draw(Draw d) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		FishConfig config = FishConfig.get();
		Minimap.update(mc);
		float size = config.minimapSize;
		float radius = size / 2f;
		float cx = radius;
		float cy = radius;
		boolean disc = config.minimapRound;
		float yaw = player.getYRot();
		float rotation = config.minimapRotate ? (float) Math.toRadians(-yaw - 180f) : 0f;

		d.glow(0f, 0f, size, size, disc ? radius : 8f, 4f, Draw.withAlpha(0xFF000000, 0x50));
		d.round(0f, 0f, size, size, disc ? radius : 8f, 0xFF0B1220);

		float step = Minimap.blocksPerPixel();
		// Pixels on screen per map pixel: the whole 128px image would be 2x larger than the widget.
		float scale = size / (Minimap.SIZE * 0.5f);
		float uvPerUnit = 1f / (Minimap.SIZE * scale);
		// The player sits between map cells; shifting the centre keeps movement smooth.
		float offsetX = (float) ((player.getX() - Minimap.centerWorldX()) / step) / Minimap.SIZE;
		float offsetZ = (float) ((player.getZ() - Minimap.centerWorldZ()) / step) / Minimap.SIZE;
		TextureSetup setup = Minimap.setup();
		if (setup != null) {
			d.map(setup, cx, cy, radius - 1f, disc, 0.5f + offsetX, 0.5f + offsetZ, uvPerUnit, rotation, 0xFFFFFFFF);
		}
		d.roundOutline(0f, 0f, size, size, disc ? radius : 8f, 1f, Draw.withAlpha(Theme.accent(), 0xC0));

		for (Waypoints.Point p : Waypoints.here()) {
			if (p.visible) {
				waypoint(d, p, player, cx, cy, radius, scale / step, rotation, disc);
			}
		}

		// Player marker: the arrow turns in a north-up map and stays upright in a rotating one.
		d.g.pose().pushMatrix();
		d.g.pose().translate(cx, cy);
		d.g.pose().rotate(config.minimapRotate ? 0f : (float) Math.toRadians(yaw + 180f));
		d.icon("arrow", -4.5f, -4.5f, 9f, 0xFFFFFFFF);
		d.g.pose().popMatrix();

		if (config.minimapCoords) {
			String coords = player.getBlockX() + ", " + player.getBlockY() + ", " + player.getBlockZ();
			d.centered(coords, radius, size + 3f, 0.8f, Theme.text(), false);
		}
	}

	private static void waypoint(Draw d, Waypoints.Point p, LocalPlayer player, float cx, float cy, float radius, float pixelsPerBlock, float rotation, boolean disc) {
		float dx = (float) (p.x + 0.5d - player.getX()) * pixelsPerBlock;
		float dz = (float) (p.z + 0.5d - player.getZ()) * pixelsPerBlock;
		float cos = (float) Math.cos(rotation);
		float sin = (float) Math.sin(rotation);
		float x = dx * cos - dz * sin;
		float y = dx * sin + dz * cos;
		float limit = radius - 5f;
		if (disc) {
			float length = (float) Math.sqrt(x * x + y * y);
			if (length > limit) {
				x *= limit / length;
				y *= limit / length;
			}
		} else {
			x = Math.clamp(x, -limit, limit);
			y = Math.clamp(y, -limit, limit);
		}
		d.round(cx + x - 2.5f, cy + y - 2.5f, 5f, 5f, 2.5f, p.color);
		d.roundOutline(cx + x - 2.5f, cy + y - 2.5f, 5f, 5f, 2.5f, 0.5f, 0xFF000000);
	}
}
