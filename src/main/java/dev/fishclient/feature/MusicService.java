package dev.fishclient.feature;

import com.google.gson.Gson;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import dev.fishclient.FishClient;
import dev.fishclient.config.FishConfig;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Reads what Windows reports as currently playing, so Spotify, Apple Music and browsers work without a login.
 * Java has no access to that API, so a small bundled PowerShell script polls it and prints one JSON line per update.
 */
public final class MusicService {
	public static final class Track {
		public boolean playing;
		public String title = "";
		public String artist = "";
		public String app = "";
		public String cover = "";
		public long positionMs;
		public long durationMs;
	}

	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Identifier COVER = FishClient.id("dynamic/music_cover");
	private static final Gson GSON = new Gson();
	private static volatile Track current = new Track();
	@Nullable
	private static Process process;
	private static String loadedCover = "";
	private static boolean hasCover;
	private static long lastStart;
	private static volatile long lastUpdate;

	/** When the helper last reported, or 0 if it never did. */
	public static long lastUpdate() {
		return lastUpdate;
	}

	public static boolean supported() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}

	public static boolean playing() {
		return current.playing;
	}

	public static Track track() {
		return current;
	}

	/** The playback position right now: the helper reports once a second, so it is counted forward in between. */
	public static long positionNow() {
		Track t = current;
		long position = t.positionMs;
		if (t.playing && lastUpdate > 0L) {
			position += System.currentTimeMillis() - lastUpdate;
		}
		return t.durationMs > 0L ? Math.min(position, t.durationMs) : position;
	}

	/** A readable name for the app that plays, such as "Spotify". */
	public static String source() {
		String app = current.app.toLowerCase(java.util.Locale.ROOT);
		if (app.contains("spotify")) {
			return "Spotify";
		}
		if (app.contains("applemusic") || app.contains("apple music")) {
			return "Apple Music";
		}
		if (app.contains("chrome")) {
			return "Chrome";
		}
		if (app.contains("msedge") || app.contains("edge")) {
			return "Edge";
		}
		if (app.contains("firefox")) {
			return "Firefox";
		}
		if (app.contains("youtube")) {
			return "YouTube Music";
		}
		String raw = current.app;
		int bang = raw.indexOf('!');
		String name = bang > 0 ? raw.substring(bang + 1) : raw;
		return name.replaceAll("(?i)\\.exe$", "");
	}

	@Nullable
	public static Identifier cover() {
		return hasCover ? COVER : null;
	}

	public static void tick(Minecraft mc) {
		boolean wanted = supported() && (FishConfig.get().music || FishConfig.get().headphones && FishConfig.get().headphonesOnlyWithMusic);
		Process running = process;
		if (wanted && (running == null || !running.isAlive())) {
			// A crashing helper must not be restarted in a tight loop.
			if (System.currentTimeMillis() - lastStart > 15000L) {
				start();
			}
		} else if (!wanted && running != null) {
			stop();
		}
		Track t = current;
		if (!t.cover.equals(loadedCover)) {
			loadedCover = t.cover;
			loadCover(mc, t.cover);
		}
	}

	private static void start() {
		lastStart = System.currentTimeMillis();
		try {
			Path script = Files.createTempFile("fishclient-music", ".ps1");
			script.toFile().deleteOnExit();
			try (InputStream in = MusicService.class.getResourceAsStream("/assets/fishclient/music_watcher.ps1")) {
				if (in == null) {
					LOGGER.warn("FishClient is missing its music helper script");
					return;
				}
				Files.copy(in, script, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
			ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass", "-File", script.toString());
			builder.environment().put("FISHCLIENT_PID", Long.toString(ProcessHandle.current().pid()));
			builder.redirectError(ProcessBuilder.Redirect.DISCARD);
			Process started = builder.start();
			process = started;
			Thread reader = new Thread(() -> read(started), "FishClient music reader");
			reader.setDaemon(true);
			reader.start();
		} catch (IOException e) {
			LOGGER.warn("FishClient could not start the music helper", e);
		}
	}

	private static void read(Process running) {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(running.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				int json = line.indexOf('{');
				if (json >= 0) {
					try {
						Track t = GSON.fromJson(line.substring(json), Track.class);
						if (t != null) {
							if (t.title == null) t.title = "";
							if (t.artist == null) t.artist = "";
							if (t.app == null) t.app = "";
							if (t.cover == null) t.cover = "";
							current = t;
							lastUpdate = System.currentTimeMillis();
						}
					} catch (RuntimeException e) {
						LOGGER.debug("FishClient ignored a malformed music line", e);
					}
				}
			}
		} catch (IOException e) {
			LOGGER.debug("FishClient music reader stopped", e);
		}
		current = new Track();
	}

	public static void stop() {
		Process running = process;
		process = null;
		if (running != null) {
			running.destroy();
		}
		current = new Track();
	}

	private static void loadCover(Minecraft mc, String file) {
		hasCover = false;
		if (file.isEmpty()) {
			return;
		}
		try (InputStream in = Files.newInputStream(Path.of(file))) {
			NativeImage image = NativeImage.read(in);
			mc.getTextureManager().register(COVER, new DynamicTexture(() -> "fishclient cover", image));
			hasCover = true;
		} catch (IOException | RuntimeException e) {
			LOGGER.debug("FishClient could not load the album cover", e);
		}
	}

	private MusicService() {
	}
}
