package dev.fishclient.feature;

import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import dev.fishclient.FishClient;
import dev.fishclient.config.FishConfig;
import dev.fishclient.config.Json;
import dev.fishclient.config.Profiles;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Saved skins. Picking one only changes what this client shows; "Apply" is the single step that talks to the
 * account service, and it runs only on that button press.
 */
public final class SkinLibrary {
	public static final class Skin {
		public String name = "";
		public String file = "";
		public boolean slim;
	}

	private static final Logger LOGGER = LogUtils.getLogger();
	private static final URI SKIN_API = URI.create("https://api.minecraftservices.com/minecraft/profile/skins");
	private static final Map<String, Identifier> TEXTURES = new HashMap<>();
	private static List<Skin> skins = new ArrayList<>();

	private static Path dir() {
		return Json.file("skins");
	}

	private static Path index() {
		return dir().resolve("skins.json");
	}

	public static void load() {
		List<Skin> saved = Json.read(index(), new TypeToken<List<Skin>>() { }.getType());
		skins = saved != null ? saved : new ArrayList<>();
		skins.removeIf(s -> !Files.isRegularFile(dir().resolve(s.file)));
	}

	private static void save() {
		Json.write(index(), skins);
	}

	public static List<Skin> all() {
		return skins;
	}

	@Nullable
	public static Skin find(String name) {
		for (Skin s : skins) {
			if (s.name.equals(name)) {
				return s;
			}
		}
		return null;
	}

	/** Adds a PNG to the library. Returns an error text, or null when it worked. */
	@Nullable
	public static String importSkin(Path source, boolean slim) {
		try (InputStream in = Files.newInputStream(source); NativeImage image = NativeImage.read(in)) {
			if (image.getWidth() != 64 || image.getHeight() != 64 && image.getHeight() != 32) {
				return "64x64 / 64x32";
			}
			String base = source.getFileName().toString().replaceAll("(?i)\\.png$", "").replaceAll("[^\\p{L}\\p{N} _-]", "_");
			if (base.isEmpty()) {
				base = "skin";
			}
			String name = base;
			for (int i = 2; find(name) != null; i++) {
				name = base + " " + i;
			}
			Files.createDirectories(dir());
			String file = UUID.randomUUID() + ".png";
			Files.copy(source, dir().resolve(file), StandardCopyOption.REPLACE_EXISTING);
			Skin skin = new Skin();
			skin.name = name;
			skin.file = file;
			skin.slim = slim;
			skins.add(skin);
			save();
			return null;
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("FishClient could not import the skin {}", source, e);
			return e.getMessage() != null ? e.getMessage() : e.toString();
		}
	}

	public static void delete(Skin skin) {
		skins.remove(skin);
		try {
			Files.deleteIfExists(dir().resolve(skin.file));
		} catch (IOException e) {
			LOGGER.warn("FishClient could not delete the skin {}", skin.name, e);
		}
		if (skin.name.equals(FishConfig.get().localSkin)) {
			FishConfig.get().localSkin = "";
			Profiles.markDirty();
		}
		save();
	}

	public static void setSlim(Skin skin, boolean slim) {
		skin.slim = slim;
		save();
	}

	public static void select(@Nullable Skin skin) {
		FishConfig.get().localSkin = skin != null ? skin.name : "";
		Profiles.markDirty();
	}

	@Nullable
	public static Identifier texture(Skin skin) {
		Identifier known = TEXTURES.get(skin.file);
		if (known != null) {
			return known;
		}
		Path path = dir().resolve(skin.file);
		try (InputStream in = Files.newInputStream(path)) {
			NativeImage image = NativeImage.read(in);
			NativeImage upgraded = image.getHeight() == 32 ? upgradeLegacy(image) : image;
			Identifier id = FishClient.id("dynamic/skin/" + skin.file.replace(".png", ""));
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "fishclient skin", upgraded));
			TEXTURES.put(skin.file, id);
			return id;
		} catch (IOException e) {
			LOGGER.warn("FishClient could not load the skin {}", skin.name, e);
			return null;
		}
	}

	/** Old 64x32 skins have no separate left limbs: those are mirrored from the right ones. */
	private static NativeImage upgradeLegacy(NativeImage old) {
		NativeImage out = new NativeImage(64, 64, true);
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 64; x++) {
				out.setPixel(x, y, old.getPixel(x, y));
			}
		}
		for (int y = 20; y < 32; y++) {
			for (int x = 0; x < 16; x++) {
				out.setPixel(16 + (15 - x), 32 + y, old.getPixel(x, y));
				out.setPixel(32 + (15 - x), 32 + y, old.getPixel(40 + x, y));
			}
		}
		old.close();
		return out;
	}

	public static PlayerSkin patch(PlayerSkin skin) {
		String active = FishConfig.get().localSkin;
		if (active.isEmpty()) {
			return skin;
		}
		Skin selected = find(active);
		if (selected == null) {
			return skin;
		}
		Identifier texture = texture(selected);
		if (texture == null) {
			return skin;
		}
		return new PlayerSkin(new ClientAsset.ResourceTexture(texture, texture), skin.cape(), skin.elytra(), selected.slim ? PlayerModelType.SLIM : PlayerModelType.WIDE, skin.secure());
	}

	/** Uploads the skin to the signed-in account. The result is reported on the main thread. */
	public static void applyToAccount(Skin skin, Consumer<@Nullable String> onDone) {
		Minecraft mc = Minecraft.getInstance();
		String token = mc.getUser().getAccessToken();
		Path path = dir().resolve(skin.file);
		String boundary = "fishclient" + UUID.randomUUID().toString().replace("-", "");
		Util.ioPool().execute(() -> {
			String error = null;
			try {
				byte[] png = Files.readAllBytes(path);
				String head = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"variant\"\r\n\r\n" + (skin.slim ? "slim" : "classic") + "\r\n"
					+ "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\nContent-Type: image/png\r\n\r\n";
				String tail = "\r\n--" + boundary + "--\r\n";
				HttpRequest request = HttpRequest.newBuilder(SKIN_API)
					.timeout(Duration.ofSeconds(30))
					.header("Authorization", "Bearer " + token)
					.header("Content-Type", "multipart/form-data; boundary=" + boundary)
					.POST(HttpRequest.BodyPublishers.ofByteArrays(List.of(head.getBytes(StandardCharsets.UTF_8), png, tail.getBytes(StandardCharsets.UTF_8))))
					.build();
				try (HttpClient client = HttpClient.newHttpClient()) {
					HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
					if (response.statusCode() / 100 != 2) {
						error = "HTTP " + response.statusCode();
					}
				}
			} catch (IOException e) {
				error = e.getMessage();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				error = "interrupted";
			}
			String result = error;
			mc.execute(() -> onDone.accept(result));
		});
	}

	private SkinLibrary() {
	}
}
