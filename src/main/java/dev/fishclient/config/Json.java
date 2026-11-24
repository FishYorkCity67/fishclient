package dev.fishclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/** JSON storage below {@code config/fishclient}. Writes go through a temp file so a crash cannot truncate data. */
public final class Json {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("fishclient");
	}

	public static Path file(String name) {
		return dir().resolve(name);
	}

	@Nullable
	public static <T> T read(Path path, Type type) {
		if (!Files.isRegularFile(path)) {
			return null;
		}
		try {
			return GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), type);
		} catch (Exception e) {
			LOGGER.warn("FishClient could not read {}", path, e);
			return null;
		}
	}

	public static void write(Path path, Object value) {
		try {
			Files.createDirectories(path.getParent());
			Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(value), StandardCharsets.UTF_8);
			try {
				Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			LOGGER.warn("FishClient could not write {}", path, e);
		}
	}

	private Json() {
	}
}
