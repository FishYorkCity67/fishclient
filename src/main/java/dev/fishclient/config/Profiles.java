package dev.fishclient.config;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import dev.fishclient.feature.ServerRules;
import dev.fishclient.ui.Theme;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

/** Named settings profiles: each one is a JSON file holding a {@link FishConfig} and its keybinds. */
public final class Profiles {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String DEFAULT_NAME = "Default";
	private static String activeName = DEFAULT_NAME;
	private static boolean dirty;
	private static long lastChange;

	private static final class State {
		String active = DEFAULT_NAME;
	}

	private static Path dir() {
		return Json.file("profiles");
	}

	private static Path path(String name) {
		return dir().resolve(sanitize(name) + ".json");
	}

	public static String sanitize(String name) {
		String cleaned = name.replaceAll("[^\\p{L}\\p{N} _-]", "").trim();
		return cleaned.isEmpty() ? DEFAULT_NAME : cleaned;
	}

	public static void init() {
		State state = Json.read(Json.file("state.json"), State.class);
		activeName = state != null ? sanitize(state.active) : DEFAULT_NAME;
		FishConfig loaded = Json.read(path(activeName), FishConfig.class);
		if (loaded == null) {
			activeName = DEFAULT_NAME;
			loaded = Json.read(path(activeName), FishConfig.class);
		}
		FishConfig.set(normalize(loaded != null ? loaded : new FishConfig()));
		Theme.apply(FishConfig.get().theme, true);
		save();
	}

	private static FishConfig normalize(FishConfig config) {
		ServerRules.addDefaults(config);
		return config;
	}

	public static String active() {
		return activeName;
	}

	public static List<String> names() {
		List<String> names = new ArrayList<>();
		try {
			Files.createDirectories(dir());
			try (Stream<Path> files = Files.list(dir())) {
				files.forEach(p -> {
					String f = p.getFileName().toString();
					if (f.endsWith(".json")) {
						names.add(f.substring(0, f.length() - 5));
					}
				});
			}
		} catch (IOException e) {
			LOGGER.warn("FishClient could not list profiles", e);
		}
		if (!names.contains(activeName)) {
			names.add(activeName);
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	/** Marks settings as changed; they are written a moment later so dragging a slider does not spam the disk. */
	public static void markDirty() {
		dirty = true;
		lastChange = System.currentTimeMillis();
	}

	public static void tick() {
		if (dirty && System.currentTimeMillis() - lastChange > 800L) {
			save();
		}
	}

	public static void save() {
		dirty = false;
		captureKeybinds();
		Json.write(path(activeName), FishConfig.get());
		State state = new State();
		state.active = activeName;
		Json.write(Json.file("state.json"), state);
	}

	public static void create(String name) {
		save();
		activeName = sanitize(name);
		if (Files.exists(path(activeName))) {
			load(activeName);
		} else {
			save();
		}
	}

	public static void load(String name) {
		save();
		FishConfig loaded = Json.read(path(name), FishConfig.class);
		if (loaded == null) {
			return;
		}
		activeName = sanitize(name);
		FishConfig.set(normalize(loaded));
		Theme.apply(loaded.theme, false);
		applyKeybinds();
		save();
	}

	public static void delete(String name) {
		if (name.equals(activeName)) {
			return;
		}
		try {
			Files.deleteIfExists(path(name));
		} catch (IOException e) {
			LOGGER.warn("FishClient could not delete profile {}", name, e);
		}
	}

	public static void exportTo(Path target) {
		save();
		Json.write(target, FishConfig.get());
	}

	/** Reads a profile file into a new profile named after that file. Returns false if it is not valid JSON. */
	public static boolean importFrom(Path source) {
		FishConfig loaded = Json.read(source, FishConfig.class);
		if (loaded == null) {
			return false;
		}
		String file = source.getFileName().toString();
		String name = sanitize(file.endsWith(".json") ? file.substring(0, file.length() - 5) : file);
		save();
		activeName = name;
		FishConfig.set(normalize(loaded));
		Theme.apply(loaded.theme, false);
		applyKeybinds();
		save();
		return true;
	}

	private static void captureKeybinds() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.options == null) {
			return;
		}
		FishConfig config = FishConfig.get();
		config.keybinds.clear();
		for (KeyMapping mapping : mc.options.keyMappings) {
			config.keybinds.put(mapping.getName(), mapping.saveString());
		}
	}

	/** Applies the keybinds stored in the active profile. Older profiles without keybinds change nothing. */
	public static void applyKeybinds() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.options == null) {
			return;
		}
		FishConfig config = FishConfig.get();
		if (config.keybinds.isEmpty()) {
			return;
		}
		for (KeyMapping mapping : mc.options.keyMappings) {
			String saved = config.keybinds.get(mapping.getName());
			if (saved != null) {
				try {
					mapping.setKey(InputConstants.getKey(saved));
				} catch (IllegalArgumentException e) {
					LOGGER.warn("FishClient skipped unknown key {}", saved);
				}
			}
		}
		KeyMapping.resetMapping();
		mc.options.save();
	}

	private Profiles() {
	}
}
