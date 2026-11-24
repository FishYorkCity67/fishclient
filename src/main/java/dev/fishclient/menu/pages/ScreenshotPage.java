package dev.fishclient.menu.pages;

import dev.fishclient.config.FishConfig;
import dev.fishclient.feature.Screenshots;
import dev.fishclient.menu.Ui;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.L;
import dev.fishclient.ui.Theme;
import java.io.File;
import java.util.List;

public final class ScreenshotPage {
	private static String confirmDelete = "";

	public static float draw(Ui ui, float x, float y, float w) {
		FishConfig c = FishConfig.get();
		Draw d = ui.d;
		float top = y;
		y = ui.heading(L.t("tab.screenshots"), x, y);
		y = ui.toggleRow("shot_preview", x, y, w, L.t("screenshot.preview"), L.t("screenshot.preview.desc"), c.screenshotPreview, true, v -> c.screenshotPreview = v);
		float bw = ui.buttonWidth(L.t("screenshot.open_folder"));
		if (ui.button("shot_folder", x, y, bw, 18f, L.t("screenshot.open_folder"), false)) {
			Screenshots.openFolder();
		}
		y += 25f;

		List<File> files = Screenshots.list();
		if (files.isEmpty()) {
			d.text(L.t("screenshot.empty"), x, y + 2f, Theme.faint());
			return y + 18f - top;
		}
		int columns = w < 340f ? 2 : 3;
		float gap = 6f;
		float cw = (w - gap * (columns - 1)) / columns;
		float imageH = cw * 9f / 16f;
		float ch = imageH + 22f;
		int count = Math.min(files.size(), 30);
		File toDelete = null;
		for (int i = 0; i < count; i++) {
			File file = files.get(i);
			float cx = x + (i % columns) * (cw + gap);
			float cy = y + (i / columns) * (ch + gap);
			ui.card(cx, cy, cw, ch);
			// Only cards near the visible area load their thumbnail.
			Screenshots.Thumb thumb = Screenshots.thumb(file);
			if (thumb != null) {
				d.texture(thumb.id(), cx + 3f, cy + 3f, cw - 6f, imageH - 3f, 0xFFFFFFFF);
			}
			if (ui.click(cx + 3f, cy + 3f, cw - 6f, imageH - 3f)) {
				Screenshots.open(file);
			}
			float iy = cy + imageH + 3f;
			if (ui.iconButton("shot_copy:" + i, cx + 4f, iy, 15f, "copy", L.t("screenshot.copy"))) {
				Screenshots.copyToClipboard(file);
				ui.toast(L.t("screenshot.copied"));
			}
			if (ui.iconButton("shot_open:" + i, cx + 21f, iy, 15f, "external", L.t("screenshot.open"))) {
				Screenshots.open(file);
			}
			boolean confirming = confirmDelete.equals(file.getName());
			if (confirming) {
				float cdw = 54f;
				if (ui.button("shot_conf:" + i, cx + cw - cdw - 4f, iy + 1f, cdw, 13f, L.t("common.confirm"), false)) {
					toDelete = file;
					confirmDelete = "";
				}
			} else if (ui.iconButton("shot_del:" + i, cx + cw - 19f, iy, 15f, "trash", L.t("common.delete"))) {
				confirmDelete = file.getName();
			}
		}
		if (toDelete != null) {
			Screenshots.delete(toDelete);
		}
		y += (int) Math.ceil(count / (float) columns) * (ch + gap);
		return y - top;
	}

	private ScreenshotPage() {
	}
}
