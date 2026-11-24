package dev.fishclient.menu.pages;

import dev.fishclient.config.FishConfig;
import dev.fishclient.feature.Lyrics;
import dev.fishclient.feature.MusicService;
import dev.fishclient.hud.MusicHud;
import dev.fishclient.menu.Ui;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.L;
import dev.fishclient.ui.Theme;
import java.util.Locale;

public final class MusicPage {
	public static float draw(Ui ui, float x, float y, float w) {
		FishConfig c = FishConfig.get();
		Draw d = ui.d;
		float top = y;
		y = ui.heading(L.t("tab.music"), x, y);
		if (!MusicService.supported()) {
			y = ui.paragraph(L.t("music.windows_only"), x, y, w) + 4f;
		}
		y = ui.toggleRow("music_on", x, y, w, L.t("music.now_playing"), L.t("music.now_playing.desc"), c.music, false, v -> c.music = v);

		ui.card(x, y, w, 62f);
		MusicService.Track t = MusicService.track();
		MusicHud.drawCover(d, x + 8f, y + 8f, 46f);
		float tx = x + 62f;
		float room = w - 72f;
		if (t.title.isEmpty()) {
			d.bold(L.t("music.nothing"), tx, y + 14f, Theme.muted());
			d.text(L.t("music.nothing.desc"), tx, y + 27f, 0.85f, Theme.faint(), false);
		} else {
			d.bold(d.fit(t.title, room), tx, y + 10f, Theme.text());
			d.text(d.fit(t.artist + "  ·  " + MusicService.source(), room), tx, y + 22f, Theme.muted());
			d.text(t.playing ? L.t("music.playing") : L.t("music.paused"), tx, y + 34f, 0.85f, t.playing ? Theme.GOOD : Theme.faint(), false);
			if (t.durationMs > 0L) {
				float fraction = Math.clamp(MusicService.positionNow() / (float) t.durationMs, 0f, 1f);
				d.round(tx, y + 48f, room, 3f, 1.5f, Theme.line());
				d.round(tx, y + 48f, Math.max(3f, room * fraction), 3f, 1.5f, Theme.accent());
			}
		}
		y += 68f;

		y = ui.section(L.t("music.lyrics_section"), x, y + 2f);
		y = ui.toggleRow("lyrics_on", x, y, w, L.t("music.lyrics"), L.t("music.lyrics.desc"), c.musicLyrics, true, v -> c.musicLyrics = v);
		if (c.music && c.musicLyrics) {
			String status = switch (Lyrics.status()) {
				case LOADING -> L.t("music.lyrics_loading");
				case FOUND -> L.f("music.lyrics_found", Lyrics.lines().size());
				case NOT_FOUND -> L.t("music.lyrics_not_found");
				case FAILED -> L.t("music.lyrics_failed");
				case OFF -> L.t("music.lyrics_waiting");
			};
			int color = Lyrics.status() == Lyrics.Status.FOUND ? Theme.GOOD : Lyrics.status() == Lyrics.Status.FAILED ? Theme.BAD : Theme.muted();
			d.text(status, x, y, 0.9f, color, false);
			y += 14f;
		}
		c.lyricsOffsetMs = Math.round(ui.slider("lyrics_offset", x, y, w, L.t("music.lyrics_offset"), String.format(Locale.ROOT, "%+.1f s", c.lyricsOffsetMs / 1000f), c.lyricsOffsetMs, -3000f, 3000f, 100f));
		y += 26f;
		c.lyricsSize = ui.slider("lyrics_size", x, y, w, L.t("music.lyrics_size"), Math.round(c.lyricsSize * 100f) + "%", c.lyricsSize, 0.5f, 2.5f, 0.1f);
		y += 26f;
		y = ui.toggleRow("lyrics_accent", x, y, w, L.t("music.lyrics_accent"), L.t("music.lyrics_accent.desc"), c.lyricsAccent, false, v -> c.lyricsAccent = v);
		y = ui.paragraph(L.t("music.how"), x, y, w) + 4f;
		return y - top;
	}

	private MusicPage() {
	}
}
