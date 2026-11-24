package dev.fishclient.hud;

import dev.fishclient.config.FishConfig;
import dev.fishclient.feature.Waypoints;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.Theme;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Projects waypoints onto the screen, so they show through walls with their name and distance. */
final class WaypointMarkers {
	static void draw(Draw d, DeltaTracker delta) {
		FishConfig config = FishConfig.get();
		if (!config.waypointsWorld) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		Camera camera = mc.gameRenderer.getMainCamera();
		Vec3 eye = camera.position();
		Quaternionf inverse = new Quaternionf(camera.rotation()).conjugate();
		float width = d.g.guiWidth();
		float height = d.g.guiHeight();
		float tan = (float) Math.tan(Math.toRadians(camera.getFov()) / 2d);
		float aspect = width / height;
		for (Waypoints.Point p : Waypoints.here()) {
			if (!p.visible) {
				continue;
			}
			Vector3f rel = new Vector3f((float) (p.x + 0.5d - eye.x), (float) (p.y + 1d - eye.y), (float) (p.z + 0.5d - eye.z));
			float distance = rel.length();
			if (distance > config.waypointMaxDistance) {
				continue;
			}
			rel.rotate(inverse);
			// The camera looks down -Z, so anything with z >= 0 is behind it.
			if (rel.z >= -0.1f) {
				continue;
			}
			float sx = (rel.x / (-rel.z * tan * aspect) + 1f) * 0.5f * width;
			float sy = (1f - rel.y / (-rel.z * tan)) * 0.5f * height;
			if (sx < -40f || sx > width + 40f || sy < -40f || sy > height + 40f) {
				continue;
			}
			marker(d, p, sx, sy, distance, config.waypointsDistance);
		}
	}

	private static void marker(Draw d, Waypoints.Point p, float x, float y, float distance, boolean showDistance) {
		float unit = Draw.unit();
		d.g.pose().pushMatrix();
		d.g.pose().translate(x, y);
		d.g.pose().scale(unit, unit);
		d.round(-3f, -3f, 6f, 6f, 3f, p.color);
		d.roundOutline(-3f, -3f, 6f, 6f, 3f, 0.5f, 0xFF000000);
		String label = showDistance ? p.name + "  " + Math.round(distance) + "m" : p.name;
		float w = d.width(label) * 0.85f + 8f;
		d.round(-w / 2f, -16f, w, 11f, 3f, Theme.hudPanel());
		d.centered(label, 0f, -14f, 0.85f, Theme.text(), false);
		d.g.pose().popMatrix();
	}

	private WaypointMarkers() {
	}
}
