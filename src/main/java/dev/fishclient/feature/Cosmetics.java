package dev.fishclient.feature;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import dev.fishclient.FishClient;
import dev.fishclient.config.FishConfig;
import dev.fishclient.config.Json;
import dev.fishclient.config.Profiles;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/** Local player cosmetics. They are only rendered on this client until a sharing server exists. */
public final class Cosmetics {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final Identifier DEFAULT_CAPE = FishClient.id("textures/cosmetics/default_cape.png");
	private static final Identifier CUSTOM_CAPE = FishClient.id("dynamic/cape");
	private static String loadedCape = "\0";
	private static boolean hasCustomCape;
	private static ClientAsset.@Nullable ResourceTexture capeAsset;

	// Spring state of the cape, advanced by real time so it does not depend on the frame rate.
	private static float lean;
	private static float leanVelocity;
	private static float side;
	private static float sideVelocity;
	private static float flap;
	private static float flapVelocity;
	private static long lastPhysics;

	private static Path capeDir() {
		return Json.file("capes");
	}

	public static List<String> capes() {
		List<String> names = new ArrayList<>();
		if (Files.isDirectory(capeDir())) {
			try (Stream<Path> files = Files.list(capeDir())) {
				files.filter(p -> p.getFileName().toString().endsWith(".png")).forEach(p -> names.add(p.getFileName().toString()));
			} catch (IOException e) {
				LOGGER.warn("FishClient could not list capes", e);
			}
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	/** Copies an image into the cape folder, converted to the 64x32 cape layout, and selects it. */
	@Nullable
	public static String importCape(Path source) {
		try (InputStream in = Files.newInputStream(source); NativeImage image = NativeImage.read(in); NativeImage cape = toCapeLayout(image)) {
			String base = source.getFileName().toString().replaceAll("(?i)\\.png$", "").replaceAll("[^\\p{L}\\p{N} _-]", "_");
			Files.createDirectories(capeDir());
			Path target = capeDir().resolve(base + ".png");
			Path tmp = capeDir().resolve(base + ".tmp");
			cape.writeToFile(tmp);
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
			FishConfig.get().capeFile = target.getFileName().toString();
			FishConfig.get().cape = true;
			loadedCape = "\0";
			Profiles.markDirty();
			return null;
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("FishClient could not import the cape {}", source, e);
			return e.getMessage() != null ? e.getMessage() : e.toString();
		}
	}

	/** Real cape sheets (2:1) are scaled as they are; any other picture is fitted onto the front and back panels. */
	private static NativeImage toCapeLayout(NativeImage image) {
		NativeImage out = new NativeImage(64, 32, true);
		int w = image.getWidth();
		int h = image.getHeight();
		if (w == h * 2) {
			for (int y = 0; y < 32; y++) {
				for (int x = 0; x < 64; x++) {
					out.setPixel(x, y, image.getPixel(x * w / 64, y * h / 32));
				}
			}
			return out;
		}
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 10; x++) {
				int color = image.getPixel(x * w / 10, y * h / 16) | 0xFF000000;
				out.setPixel(1 + x, 1 + y, color);
				out.setPixel(12 + x, 1 + y, color);
			}
		}
		// Top, bottom and sides take the panel's edge colours so the cape has no see-through rim.
		for (int x = 0; x < 10; x++) {
			out.setPixel(1 + x, 0, out.getPixel(1 + x, 1));
			out.setPixel(11 + x, 0, out.getPixel(1 + x, 16));
		}
		for (int y = 0; y < 16; y++) {
			out.setPixel(0, 1 + y, out.getPixel(1, 1 + y));
			out.setPixel(11, 1 + y, out.getPixel(10, 1 + y));
		}
		return out;
	}

	public static void deleteCape(String name) {
		try {
			Files.deleteIfExists(capeDir().resolve(name));
		} catch (IOException e) {
			LOGGER.warn("FishClient could not delete the cape {}", name, e);
		}
		if (name.equals(FishConfig.get().capeFile)) {
			FishConfig.get().capeFile = "";
			loadedCape = "\0";
			Profiles.markDirty();
		}
	}

	private static void ensureCape() {
		String wanted = FishConfig.get().capeFile;
		if (wanted.equals(loadedCape)) {
			return;
		}
		loadedCape = wanted;
		hasCustomCape = false;
		if (!wanted.isEmpty()) {
			Path path = capeDir().resolve(wanted);
			if (Files.isRegularFile(path)) {
				try (InputStream in = Files.newInputStream(path)) {
					NativeImage image = NativeImage.read(in);
					Minecraft.getInstance().getTextureManager().register(CUSTOM_CAPE, new DynamicTexture(() -> "fishclient cape", image));
					hasCustomCape = true;
				} catch (IOException e) {
					LOGGER.warn("FishClient could not load the cape {}", wanted, e);
				}
			}
		}
		Identifier texture = hasCustomCape ? CUSTOM_CAPE : DEFAULT_CAPE;
		capeAsset = new ClientAsset.ResourceTexture(texture, texture);
	}

	public static PlayerSkin patchSkin(PlayerSkin skin) {
		FishConfig config = FishConfig.get();
		PlayerSkin result = SkinLibrary.patch(skin);
		if (config.cape) {
			ensureCape();
			result = new PlayerSkin(result.body(), capeAsset, result.elytra(), result.model(), result.secure());
		}
		return result;
	}

	/** Replaces vanilla's stiff cape angles by a damped spring chasing them, with some idle sway. */
	public static void applyCapePhysics(AvatarRenderState state) {
		FishConfig config = FishConfig.get();
		if (config.cape) {
			state.showCape = true;
		}
		float amount = config.capePhysics;
		if (amount <= 0f || !config.cape) {
			return;
		}
		long now = Util.getMillis();
		float dt = Math.min((now - lastPhysics) / 1000f, 0.05f);
		lastPhysics = now;
		float time = now / 1000f;
		float speed = Mth.clamp(state.capeLean / 40f, 0f, 1f);
		float targetLean = state.capeLean * amount + (float) Math.sin(time * 5.5f) * (2f + speed * 7f) * amount;
		float targetSide = state.capeLean2 * amount + (float) Math.sin(time * 3.1f) * 2.5f * amount;
		float targetFlap = state.capeFlap;
		// Semi-implicit Euler: stiffness pulls towards the target, damping stops the swinging.
		leanVelocity += ((targetLean - lean) * 48f - leanVelocity * 7f) * dt;
		lean += leanVelocity * dt;
		sideVelocity += ((targetSide - side) * 40f - sideVelocity * 8f) * dt;
		side += sideVelocity * dt;
		flapVelocity += ((targetFlap - flap) * 60f - flapVelocity * 9f) * dt;
		flap += flapVelocity * dt;
		state.capeLean = Mth.clamp(lean, 0f, 165f);
		state.capeLean2 = Mth.clamp(side, -35f, 35f);
		state.capeFlap = Mth.clamp(flap, -12f, 40f);
	}

	public static boolean headphonesVisible() {
		FishConfig config = FishConfig.get();
		return config.headphones && (!config.headphonesOnlyWithMusic || MusicService.playing());
	}

	public static void tick(Minecraft mc) {
		FishConfig config = FishConfig.get();
		if (config.fishBubbles && mc.player != null && mc.level != null && mc.player.fishing != null && mc.level.getGameTime() % 4L == 0L) {
			RandomSource r = mc.player.getRandom();
			// A ring around the player, because a particle right in front of the camera fills half the screen.
			double angle = r.nextDouble() * Math.PI * 2d;
			double distance = 1.3d + r.nextDouble() * 0.9d;
			mc.level.addParticle(
				ParticleTypes.BUBBLE_POP,
				mc.player.getX() + Math.cos(angle) * distance,
				mc.player.getY() + r.nextDouble() * 1.9d,
				mc.player.getZ() + Math.sin(angle) * distance,
				0d, 0.03d, 0d
			);
		}
	}

	private Cosmetics() {
	}
}
