package dev.fishclient.hud;

import dev.fishclient.config.FishConfig;
import dev.fishclient.feature.MusicService;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.Theme;
import java.util.Locale;
import net.minecraft.resources.Identifier;

/** A "now playing" card with the album cover, the app it plays in and the running time. */
public final class MusicHud extends HudElement {
	MusicHud() {
		super("music", true, 0.99f, 0.01f);
	}

	@Override
	public boolean visible() {
		return FishConfig.get().music && !MusicService.track().title.isEmpty();
	}

	@Override
	public float width(Draw d) {
		return 156f;
	}

	@Override
	public float height(Draw d) {
		return 40f;
	}

	@Override
	public void draw(Draw d) {
		float w = width(d);
		HudStyle.box(d, w, height(d));
		MusicService.Track t = MusicService.track();
		drawCover(d, 5f, 5f, 30f);
		float textX = 40f;
		float room = w - textX - 6f;
		d.bold(d.fit(t.title, room), textX, 5f, Theme.text());
		String source = MusicService.source();
		d.text(d.fit(t.artist + (source.isEmpty() ? "" : "  ·  " + source), room / 0.85f), textX, 15f, 0.85f, Theme.muted(), false);
		if (t.durationMs > 0L) {
			long position = MusicService.positionNow();
			String elapsed = time(position);
			String left = "-" + time(t.durationMs - position);
			float timeW = 22f;
			float barX = textX + timeW;
			float barW = room - timeW * 2f;
			float fraction = Math.clamp(position / (float) t.durationMs, 0f, 1f);
			d.text(elapsed, textX, 27f, 0.65f, Theme.faint(), false);
			d.right(left, w - 6f, 27f, 0.65f, Theme.faint(), false);
			d.round(barX, 29f, barW, 2f, 1f, Theme.line());
			d.round(barX, 29f, Math.max(2f, barW * fraction), 2f, 1f, t.playing ? Theme.accent() : Theme.faint());
			d.round(barX + barW * fraction - 2f, 28f, 4f, 4f, 2f, 0xFFFFFFFF);
		}
	}

	private static String time(long ms) {
		long seconds = Math.max(0L, ms / 1000L);
		return String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
	}

	public static void drawCover(Draw d, float x, float y, float size) {
		Identifier cover = MusicService.cover();
		if (cover != null) {
			d.texture(cover, x, y, size, size, 0xFFFFFFFF);
		} else {
			d.round(x, y, size, size, 3f, Theme.cardHover());
			d.icon("music", x + size * 0.25f, y + size * 0.25f, size * 0.5f, Theme.accent());
		}
	}
}
