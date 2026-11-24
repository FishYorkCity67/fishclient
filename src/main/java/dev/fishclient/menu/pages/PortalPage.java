package dev.fishclient.menu.pages;

import dev.fishclient.config.FishConfig;
import dev.fishclient.feature.PortalViews;
import dev.fishclient.menu.Ui;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.L;
import dev.fishclient.ui.Theme;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class PortalPage {
	private static final SimpleDateFormat TIME = new SimpleDateFormat("dd.MM. HH:mm");
	private static final int[] SIZES = {256, 512, 1024};
	private static String armed = "";

	public static float draw(Ui ui, float x, float y, float w) {
		FishConfig c = FishConfig.get();
		Draw d = ui.d;
		float top = y;
		y = ui.heading(L.t("tab.portals"), x, y);
		y = ui.paragraph(L.t("portals.desc"), x, y, w) + 4f;
		y = ui.toggleRow("portal_views", x, y, w, L.t("portals.views"), L.t("portals.views.desc"), c.portalViews, true, v -> c.portalViews = v);
		y = ui.toggleRow("portal_transition", x, y, w, L.t("portals.transition"), L.t("portals.transition.desc"), c.portalTransition, true, v -> c.portalTransition = v);

		d.text(L.t("portals.quality"), x, y, Theme.text());
		y += 13f;
		String[] labels = {L.t("portals.quality.0"), L.t("portals.quality.1"), L.t("portals.quality.2")};
		int selected = 1;
		for (int i = 0; i < SIZES.length; i++) {
			if (SIZES[i] == c.portalViewSize) {
				selected = i;
			}
		}
		float[] next = new float[1];
		int picked = ui.pills("portal_size", x, y, w, labels, selected, next);
		if (picked != selected) {
			c.portalViewSize = SIZES[picked];
			Ui.changed();
		}
		y = next[0];
		c.portalViewDistance = Math.round(ui.slider("portal_distance", x, y, w, L.t("portals.distance"), c.portalViewDistance + " m", c.portalViewDistance, 16f, 128f, 8f));
		y += 28f;

		List<PortalViews.Entry> entries = PortalViews.entries();
		float labelY = y;
		y = ui.section(L.f("portals.saved", entries.size()), x, y);
		if (!entries.isEmpty()) {
			float clearW = ui.buttonWidth(L.t("portals.clear"));
			boolean waiting = armed.equals("all");
			if (ui.button("portal_clear", x + w - clearW, labelY - 3f, clearW, 15f, waiting ? L.t("common.confirm") : L.t("portals.clear"), waiting)) {
				if (waiting) {
					PortalViews.clear();
					armed = "";
				} else {
					armed = "all";
				}
			}
		}
		if (entries.isEmpty()) {
			y = ui.paragraph(L.t("portals.empty"), x, y, w) + 4f;
		}
		PortalViews.Entry remove = null;
		for (PortalViews.Entry e : new ArrayList<>(entries)) {
			PortalViews.Portal p = e.portal;
			String id = "portal_del:" + p.id();
			ui.card(x, y, w, 30f);
			d.icon("portal", x + 8f, y + 9f, 12f, Theme.accent());
			d.text(dimension(p.dim) + "  " + p.minX + ", " + p.minY + ", " + p.minZ, x + 26f, y + 6f, Theme.text());
			d.text(L.f("portals.shows", dimension(e.target)) + "  ·  " + TIME.format(new Date(e.time)), x + 26f, y + 17f, 0.85f, Theme.muted(), false);
			boolean waiting = armed.equals(id);
			if (ui.button(id, x + w - 64f, y + 7f, 56f, 16f, waiting ? L.t("common.confirm") : L.t("common.delete"), waiting)) {
				if (waiting) {
					remove = e;
					armed = "";
				} else {
					armed = id;
				}
			}
			y += 34f;
		}
		if (remove != null) {
			PortalViews.remove(remove);
		}
		return y - top;
	}

	private static String dimension(String id) {
		return switch (id) {
			case "minecraft:overworld" -> L.t("portals.dim.overworld");
			case "minecraft:the_nether" -> L.t("portals.dim.nether");
			case "minecraft:the_end" -> L.t("portals.dim.end");
			default -> id;
		};
	}

	private PortalPage() {
	}
}
