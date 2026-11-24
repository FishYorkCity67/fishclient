package dev.fishclient.feature;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import dev.fishclient.FishClient;
import dev.fishclient.config.FishConfig;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/** Preview toast and gallery for the game's own screenshot folder. Thumbnails are scaled down on an IO thread. */
public final class Screenshots {
	public record Thumb(Identifier id, int width, int height) {
	}

	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int THUMB_WIDTH = 256;
	private static final Map<String, Thumb> THUMBS = new HashMap<>();
	private static final Set<String> LOADING = new HashSet<>();
	private static int nextId;
	@Nullable
	private static File preview;
	private static long previewStart;

	public static File dir() {
		return new File(Minecraft.getInstance().gameDirectory, "screenshots");
	}

	public static List<File> list() {
		File[] files = dir().listFiles((d, name) -> name.endsWith(".png"));
		if (files == null) {
			return new ArrayList<>();
		}
		List<File> sorted = new ArrayList<>(Arrays.asList(files));
		sorted.sort(Comparator.comparingLong(File::lastModified).reversed());
		return sorted;
	}

	/** Called on any thread once the game has written a screenshot. */
	public static void onSaved() {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			if (!FishConfig.get().screenshotPreview) {
				return;
			}
			List<File> files = list();
			if (!files.isEmpty()) {
				preview = files.getFirst();
				previewStart = Util.getMillis();
			}
		});
	}

	@Nullable
	public static File preview() {
		return preview;
	}

	public static long previewStart() {
		return previewStart;
	}

	public static void clearPreview() {
		preview = null;
	}

	/** The thumbnail, or null while it is still loading. */
	@Nullable
	public static Thumb thumb(File file) {
		String key = file.getAbsolutePath() + "|" + file.lastModified();
		Thumb known = THUMBS.get(key);
		if (known != null) {
			return known;
		}
		if (LOADING.add(key)) {
			Minecraft mc = Minecraft.getInstance();
			Util.ioPool().execute(() -> {
				try (InputStream in = Files.newInputStream(file.toPath()); NativeImage full = NativeImage.read(in)) {
					int w = THUMB_WIDTH;
					int h = Math.max(1, full.getHeight() * w / Math.max(1, full.getWidth()));
					NativeImage small = new NativeImage(w, h, true);
					for (int y = 0; y < h; y++) {
						for (int x = 0; x < w; x++) {
							small.setPixel(x, y, full.getPixel(x * full.getWidth() / w, y * full.getHeight() / h));
						}
					}
					mc.execute(() -> {
						Identifier id = FishClient.id("dynamic/thumb/" + nextId++);
						mc.getTextureManager().register(id, new DynamicTexture(() -> "fishclient thumbnail", small));
						THUMBS.put(key, new Thumb(id, w, h));
					});
				} catch (IOException | RuntimeException e) {
					LOGGER.warn("FishClient could not read the screenshot {}", file, e);
				}
			});
		}
		return null;
	}

	/** Puts the image on the Windows clipboard. A separate process avoids AWT inside the game. */
	public static void copyToClipboard(File file) {
		Util.ioPool().execute(() -> {
			try {
				ProcessBuilder builder = new ProcessBuilder(
					"powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-STA", "-Command",
					"Add-Type -AssemblyName System.Windows.Forms,System.Drawing;"
						+ "$i=[System.Drawing.Image]::FromFile($env:FISHCLIENT_SHOT);"
						+ "[System.Windows.Forms.Clipboard]::SetImage($i);$i.Dispose()"
				);
				builder.environment().put("FISHCLIENT_SHOT", file.getAbsolutePath());
				builder.redirectErrorStream(true);
				builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
				builder.start();
			} catch (IOException e) {
				LOGGER.warn("FishClient could not copy the screenshot", e);
			}
		});
	}

	public static void open(File file) {
		Util.getPlatform().openFile(file);
	}

	public static void openFolder() {
		Util.getPlatform().openFile(dir());
	}

	public static void delete(File file) {
		try {
			Path path = file.toPath();
			Files.deleteIfExists(path);
		} catch (IOException e) {
			LOGGER.warn("FishClient could not delete the screenshot {}", file, e);
		}
		if (file.equals(preview)) {
			preview = null;
		}
	}

	private Screenshots() {
	}
}
