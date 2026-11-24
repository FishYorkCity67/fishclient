package dev.fishclient.hud;

import dev.fishclient.config.FishConfig;
import dev.fishclient.feature.Lyrics;
import dev.fishclient.feature.MusicService;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.Theme;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The current lyric line floats in the air around the player. Each line gets a spot a few blocks away, left and right
 * in turn, rises slowly and fades out once the next line takes over. The spots move along when the player walks, but
 * keep their direction when the camera turns, so the words feel like they hang in the world.
 */
final class FloatingLyrics {
	private static final int MAX_LINES = 4;
	private static final long FADE_IN = 450L;
	private static final long FADE_OUT = 1400L;
	private static final float WORLD_HEIGHT = 0.42f;

	private static final class Floating {
		final FormattedCharSequence text;
		final Vec3 offset;
		final long born;
		long fadeFrom = Long.MAX_VALUE;
		final float sway;

		Floating(FormattedCharSequence text, Vec3 offset, long born, float sway) {
			this.text = text;
			this.offset = offset;
			this.born = born;
			this.sway = sway;
		}
	}

	private static final List<Floating> LINES = new ArrayList<>();
	private static final Random RANDOM = new Random();
	private static int shownIndex = -1;
	private static List<Lyrics.Line> shownSource = List.of();
	private static int spawnCount;

	static void draw(Draw d, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		FishConfig config = FishConfig.get();
		long now = Util.getMillis();
		boolean active = config.music && config.musicLyrics && player != null && MusicService.playing() && Lyrics.status() == Lyrics.Status.FOUND;
		if (active) {
			follow(mc, now);
		} else {
			fadeAll(now);
			shownIndex = -1;
		}
		LINES.removeIf(f -> now - f.fadeFrom > FADE_OUT);
		if (LINES.isEmpty() || player == null) {
			return;
		}
		render(d, mc, player, delta, now);
	}

	private static void follow(Minecraft mc, long now) {
		List<Lyrics.Line> lines = Lyrics.lines();
		if (lines != shownSource) {
			shownSource = lines;
			shownIndex = -1;
			fadeAll(now);
		}
		int index = Lyrics.indexAt(lines, MusicService.positionNow() + FishConfig.get().lyricsOffsetMs);
		if (index == shownIndex) {
			return;
		}
		shownIndex = index;
		fadeAll(now);
		if (index < 0) {
			return;
		}
		String text = lines.get(index).text();
		if (text.isBlank()) {
			return;
		}
		spawn(mc, text, now);
	}

	private static void fadeAll(long now) {
		for (Floating f : LINES) {
			f.fadeFrom = Math.min(f.fadeFrom, now);
		}
	}

	private static void spawn(Minecraft mc, String text, long now) {
		Camera camera = mc.gameRenderer.getMainCamera();
		// Alternate sides, so consecutive lines do not stack on top of each other.
		float side = (spawnCount++ % 2 == 0) ? -1f : 1f;
		float angle = camera.yRot() + side * (18f + RANDOM.nextFloat() * 32f);
		float distance = 5.5f + RANDOM.nextFloat() * 2.5f;
		float rad = angle * Mth.DEG_TO_RAD;
		double dx = -Mth.sin(rad) * distance;
		double dz = Mth.cos(rad) * distance;
		double dy = 0.4d + RANDOM.nextFloat() * 2.2d;
		Component component = Component.literal(text);
		LINES.add(new Floating(component.getVisualOrderText(), new Vec3(dx, dy, dz), now, RANDOM.nextFloat() * 6.28f));
		while (LINES.size() > MAX_LINES) {
			LINES.removeFirst();
		}
	}

	private static void render(Draw d, Minecraft mc, LocalPlayer player, DeltaTracker delta, long now) {
		Camera camera = mc.gameRenderer.getMainCamera();
		Vec3 eye = camera.position();
		float partial = delta.getGameTimeDeltaPartialTick(true);
		Vec3 anchor = player.getEyePosition(partial);
		Quaternionf inverse = new Quaternionf(camera.rotation()).conjugate();
		float width = d.g.guiWidth();
		float height = d.g.guiHeight();
		float tan = (float) Math.tan(Math.toRadians(camera.getFov()) / 2d);
		float aspect = width / height;
		FishConfig config = FishConfig.get();
		int base = config.lyricsAccent ? Theme.accent2() : 0xFFFFFFFF;
		Iterator<Floating> it = LINES.iterator();
		while (it.hasNext()) {
			Floating f = it.next();
			float age = (now - f.born) / 1000f;
			float alpha = Math.min(1f, (now - f.born) / (float) FADE_IN);
			if (now > f.fadeFrom) {
				alpha *= 1f - Math.min(1f, (now - f.fadeFrom) / (float) FADE_OUT);
			}
			if (alpha <= 0.02f) {
				continue;
			}
			// Slow rise with a gentle sideways sway.
			Vec3 pos = anchor.add(f.offset).add(Math.sin(age * 0.7f + f.sway) * 0.25d, age * 0.12d, Math.cos(age * 0.6f + f.sway) * 0.25d);
			Vector3f rel = new Vector3f((float) (pos.x - eye.x), (float) (pos.y - eye.y), (float) (pos.z - eye.z));
			float distance = rel.length();
			rel.rotate(inverse);
			if (rel.z >= -0.3f) {
				continue;
			}
			float depth = -rel.z;
			float sx = (rel.x / (depth * tan * aspect) + 1f) * 0.5f * width;
			float sy = (1f - rel.y / (depth * tan)) * 0.5f * height;
			// Text height on screen, so a line keeps the same size in the world whatever the GUI scale.
			float pixels = WORLD_HEIGHT * config.lyricsSize / (depth * tan) * 0.5f * height;
			float scale = Mth.clamp(pixels / 9f, 0.35f, 6f);
			int textWidth = d.font.width(f.text);
			int color = Draw.mulAlpha(base, alpha * Mth.clamp(1.4f - distance / 14f, 0.45f, 1f));
			d.g.pose().pushMatrix();
			d.g.pose().translate(sx, sy);
			d.g.pose().scale(scale, scale);
			d.g.text(d.font, f.text, -textWidth / 2, -4, color, true);
			d.g.pose().popMatrix();
		}
	}

	private FloatingLyrics() {
	}
}
