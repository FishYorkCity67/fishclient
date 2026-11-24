package dev.fishclient.menu.pages;

import dev.fishclient.FishKeys;
import dev.fishclient.config.FishConfig;
import dev.fishclient.feature.Minimap;
import dev.fishclient.feature.Waypoints;
import dev.fishclient.hud.FishHud;
import dev.fishclient.hud.HudElement;
import dev.fishclient.menu.Ui;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.L;
import dev.fishclient.ui.Theme;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

public final class MapPages {
	private static String newName = "";
	private static Waypoints.@Nullable Point editing;

	public static float waypoints(Ui ui, float x, float y, float w) {
		FishConfig c = FishConfig.get();
		Draw d = ui.d;
		Minecraft mc = Minecraft.getInstance();
		float top = y;
		y = ui.heading(L.t("tab.waypoints"), x, y);
		y = ui.toggleRow("wp_world", x, y, w, L.t("waypoint.world"), L.t("waypoint.world.desc"), c.waypointsWorld, true, v -> c.waypointsWorld = v);
		y = ui.toggleRow("wp_dist", x, y, w, L.t("waypoint.distance"), L.t("waypoint.distance.desc"), c.waypointsDistance, true, v -> c.waypointsDistance = v);
		c.waypointMaxDistance = Math.round(ui.slider("wp_max", x, y, w - 94f, L.t("waypoint.max_distance"), c.waypointMaxDistance + " m", c.waypointMaxDistance, 100f, 30000f, 100f));
		ui.keybind("wp_key", x + w - 84f, y + 1f, 84f, 17f, FishKeys.WAYPOINT);
		y += 28f;

		float addW = 96f;
		newName = ui.input("wp_new", x, y, w - addW - 5f, 18f, newName, L.t("waypoint.name_placeholder"));
		if (ui.button("wp_add", x + w - addW, y, addW, 18f, L.t("waypoint.add_here"), true)) {
			if (mc.player == null) {
				ui.toast(L.t("auction.no_world"));
			} else {
				String name = newName.isBlank() ? L.f("waypoint.default_name", Waypoints.all().size() + 1) : newName.trim();
				Waypoints.addHere(name);
				newName = "";
				ui.toast(L.f("waypoint.added", name));
			}
		}
		y += 25f;

		List<Waypoints.Point> here = mc.level != null ? Waypoints.here() : Waypoints.all();
		if (here.isEmpty()) {
			d.text(L.t("waypoint.empty"), x, y + 2f, Theme.faint());
			y += 18f;
		}
		Waypoints.Point remove = null;
		int index = 0;
		for (Waypoints.Point p : here) {
			String id = "wp" + index++;
			boolean edit = p == editing;
			float h = edit ? 48f : 26f;
			ui.card(x, y, w, h);
			d.round(x + 8f, y + 10f, 6f, 6f, 3f, p.color);
			d.text(d.fit(p.name, w - 190f), x + 20f, y + 6f, Theme.text());
			d.text(p.x + ", " + p.y + ", " + p.z, x + 20f, y + 16f, 0.8f, Theme.muted(), false);
			if (ui.toggleSwitch(id + "_vis", x + w - 88f, y + 6.5f, p.visible)) {
				p.visible = !p.visible;
				Waypoints.save();
			}
			if (ui.iconButton(id + "_edit", x + w - 60f, y + 5f, 16f, "gear", L.t("common.edit"))) {
				editing = edit ? null : p;
			}
			if (ui.iconButton(id + "_del", x + w - 40f, y + 5f, 16f, "trash", L.t("common.delete"))) {
				remove = p;
			}
			if (edit) {
				String renamed = ui.input(id + "_name", x + 8f, y + 27f, 110f, 16f, p.name, L.t("waypoint.name_placeholder"));
				if (!renamed.equals(p.name)) {
					p.name = renamed;
					Waypoints.save();
				}
				int color = ui.swatches(id + "_color", x + 126f, y + 27f, Waypoints.COLORS, p.color);
				if (color != p.color) {
					p.color = color;
					Waypoints.save();
				}
			}
			y += h + 4f;
		}
		if (remove != null) {
			Waypoints.remove(remove);
		}
		return y - top;
	}

	public static float minimap(Ui ui, float x, float y, float w) {
		FishConfig c = FishConfig.get();
		float top = y;
		y = ui.heading(L.t("tab.minimap"), x, y);
		HudElement map = null;
		for (HudElement element : FishHud.ELEMENTS) {
			if (element.id.equals("minimap")) {
				map = element;
			}
		}
		FishConfig.HudPos pos = map != null ? map.pos() : new FishConfig.HudPos(false, 0f, 0f);
		y = ui.toggleRow("mm_on", x, y, w, L.t("minimap.show"), L.t("minimap.show.desc"), pos.enabled, false, v -> pos.enabled = v);
		y = ui.toggleRow("mm_round", x, y, w, L.t("minimap.round"), L.t("minimap.round.desc"), c.minimapRound, true, v -> c.minimapRound = v);
		y = ui.toggleRow("mm_rotate", x, y, w, L.t("minimap.rotate"), L.t("minimap.rotate.desc"), c.minimapRotate, false, v -> c.minimapRotate = v);
		y = ui.toggleRow("mm_coords", x, y, w, L.t("minimap.coords"), L.t("minimap.coords.desc"), c.minimapCoords, true, v -> c.minimapCoords = v);
		c.minimapSize = Math.round(ui.slider("mm_size", x, y, w, L.t("minimap.size"), c.minimapSize + " px", c.minimapSize, 60f, 180f, 4f));
		y += 26f;
		String[] zoom = new String[Minimap.BLOCKS_PER_PIXEL.length];
		for (int i = 0; i < zoom.length; i++) {
			zoom[i] = Math.round(Minimap.SIZE * 0.5f * Minimap.BLOCKS_PER_PIXEL[i]) + " " + L.t("minimap.blocks");
		}
		ui.d.text(L.t("minimap.range"), x, y, Theme.text());
		y += 13f;
		float[] next = new float[1];
		int picked = ui.pills("mm_zoom", x, y, w, zoom, c.minimapZoom, next);
		if (picked != c.minimapZoom) {
			c.minimapZoom = picked;
			Ui.changed();
		}
		y = next[0];
		y = ui.paragraph(L.t("minimap.note"), x, y, w) + 4f;
		return y - top;
	}

	private MapPages() {
	}
}
