package dev.fishclient.feature;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.fishclient.config.FishConfig;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Time-synced lyrics for the song Windows reports, looked up on lrclib.net at runtime. Only title, artist and
 * duration are sent; nothing is stored beyond a small in-memory cache for the current session.
 */
public final class Lyrics {
	public record Line(long timeMs, String text) {
	}

	public enum Status {
		OFF, LOADING, FOUND, NOT_FOUND, FAILED
	}

	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String API = "https://lrclib.net/api/";
	private static final String USER_AGENT = "FishClient/1.0.0 (Minecraft Fabric mod)";
	private static final Pattern STAMP = Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)]");
	private static final int CACHE_SIZE = 40;
	private static final Map<String, List<Line>> CACHE = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, List<Line>> eldest) {
			return size() > CACHE_SIZE;
		}
	});
	@Nullable
	private static HttpClient client;
	private static volatile String currentKey = "";
	private static volatile List<Line> current = List.of();
	private static volatile Status status = Status.OFF;

	public static Status status() {
		return status;
	}

	public static List<Line> lines() {
		return current;
	}

	private static String key(MusicService.Track t) {
		return t.artist + "\n" + t.title;
	}

	/** Follows track changes; called every tick. */
	public static void tick() {
		FishConfig config = FishConfig.get();
		MusicService.Track t = MusicService.track();
		// Only with the music module on: nothing goes to lrclib.net unless the player switched it on.
		if (!config.music || !config.musicLyrics || t.title.isEmpty()) {
			currentKey = "";
			current = List.of();
			status = Status.OFF;
			return;
		}
		String key = key(t);
		if (key.equals(currentKey)) {
			return;
		}
		currentKey = key;
		List<Line> cached = CACHE.get(key);
		if (cached != null) {
			current = cached;
			status = cached.isEmpty() ? Status.NOT_FOUND : Status.FOUND;
			return;
		}
		current = List.of();
		status = Status.LOADING;
		String title = t.title;
		String artist = t.artist;
		long durationMs = t.durationMs;
		Util.ioPool().execute(() -> {
			try {
				List<Line> found = fetch(title, artist, durationMs);
				CACHE.put(key, found);
				if (key.equals(currentKey)) {
					current = found;
					status = found.isEmpty() ? Status.NOT_FOUND : Status.FOUND;
				}
			} catch (Exception e) {
				LOGGER.warn("FishClient could not load lyrics from lrclib.net: {}", e.toString());
				if (key.equals(currentKey)) {
					status = Status.FAILED;
				}
			}
		});
	}

	/** Asks for an exact match first, then falls back to a search with a cleaned-up title. */
	public static List<Line> fetch(String title, String artist, long durationMs) throws Exception {
		String query = "track_name=" + enc(title) + "&artist_name=" + enc(artist);
		if (durationMs > 0L) {
			JsonElement exact = request("get?" + query + "&duration=" + Math.round(durationMs / 1000d));
			List<Line> lines = exact != null && exact.isJsonObject() ? parse(exact.getAsJsonObject()) : List.of();
			if (!lines.isEmpty()) {
				return lines;
			}
		}
		JsonElement search = request("search?track_name=" + enc(clean(title)) + "&artist_name=" + enc(artist));
		if (search != null && search.isJsonArray()) {
			JsonArray results = search.getAsJsonArray();
			// Prefer an entry whose length matches the playing song, then any entry that has timings at all.
			List<Line> fallback = List.of();
			for (JsonElement element : results) {
				if (!element.isJsonObject()) {
					continue;
				}
				JsonObject o = element.getAsJsonObject();
				List<Line> lines = parse(o);
				if (lines.isEmpty()) {
					continue;
				}
				double length = o.has("duration") && !o.get("duration").isJsonNull() ? o.get("duration").getAsDouble() : 0d;
				if (durationMs <= 0L || Math.abs(length * 1000d - durationMs) < 3000d) {
					return lines;
				}
				if (fallback.isEmpty()) {
					fallback = lines;
				}
			}
			return fallback;
		}
		return List.of();
	}

	/** One API call. The service answers 503 now and then under load, so busy answers are tried again a little later. */
	@Nullable
	private static JsonElement request(String path) throws Exception {
		HttpClient http = client;
		if (http == null) {
			http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
			client = http;
		}
		HttpRequest request = HttpRequest.newBuilder(URI.create(API + path))
			.timeout(Duration.ofSeconds(12))
			.header("User-Agent", USER_AGENT)
			.GET()
			.build();
		Exception last = null;
		for (int attempt = 0; attempt < 3; attempt++) {
			if (attempt > 0) {
				Thread.sleep(1500L * attempt);
			}
			try {
				HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
				int code = response.statusCode();
				if (code == 404) {
					return null;
				}
				if (code / 100 == 2) {
					return JsonParser.parseString(response.body());
				}
				last = new IllegalStateException("HTTP " + code);
				if (code != 429 && code / 100 != 5) {
					break;
				}
			} catch (java.io.IOException e) {
				last = e;
			}
		}
		throw last;
	}

	private static List<Line> parse(JsonObject o) {
		if (!o.has("syncedLyrics") || o.get("syncedLyrics").isJsonNull()) {
			return List.of();
		}
		return parseLrc(o.get("syncedLyrics").getAsString());
	}

	/** Reads "[mm:ss.xx] text" lines; a line may carry several timestamps. Empty lines mark pauses. */
	public static List<Line> parseLrc(String lrc) {
		List<Line> lines = new ArrayList<>();
		for (String raw : lrc.split("\\r?\\n")) {
			Matcher m = STAMP.matcher(raw);
			int end = 0;
			List<Long> times = new ArrayList<>();
			while (m.find() && m.start() == end) {
				long minutes = Long.parseLong(m.group(1));
				double seconds = Double.parseDouble(m.group(2));
				times.add(minutes * 60_000L + Math.round(seconds * 1000d));
				end = m.end();
			}
			String text = raw.substring(end).trim();
			for (long time : times) {
				lines.add(new Line(time, text));
			}
		}
		lines.sort((a, b) -> Long.compare(a.timeMs(), b.timeMs()));
		return List.copyOf(lines);
	}

	/** The index of the line playing at {@code positionMs}, or -1 before the first one. */
	public static int indexAt(List<Line> lines, long positionMs) {
		int lo = 0;
		int hi = lines.size() - 1;
		int found = -1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			if (lines.get(mid).timeMs() <= positionMs) {
				found = mid;
				lo = mid + 1;
			} else {
				hi = mid - 1;
			}
		}
		return found;
	}

	/** Strips additions such as "(feat. X)" or "- Remastered 2011" that rarely match the database. */
	static String clean(String title) {
		String t = title.replaceAll("(?i)\\s*[(\\[](feat\\.?|ft\\.?|with|official|lyric|audio|video|remaster)[^)\\]]*[)\\]]", "");
		t = t.replaceAll("(?i)\\s+-\\s+.*(remaster|version|edit|mix|live|mono|stereo).*$", "");
		return t.trim().isEmpty() ? title : t.trim();
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	/** Shows the given lines for the current song instead of looking them up; used by the automated test. */
	public static void useLines(List<Line> lines) {
		MusicService.Track t = MusicService.track();
		currentKey = key(t);
		CACHE.put(currentKey, lines);
		current = lines;
		status = lines.isEmpty() ? Status.NOT_FOUND : Status.FOUND;
	}

	private Lyrics() {
	}
}
