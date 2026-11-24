package dev.fishclient.feature;

import com.mojang.blaze3d.platform.NativeImage;
import dev.fishclient.FishClient;
import dev.fishclient.config.FishConfig;
import dev.fishclient.ui.Textures;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

/**
 * Builds the map from loaded chunks only, a limited number of columns per frame so scanning never causes a stutter.
 * The image follows the player: when the centre moves, pixels shift and only the new rows get scanned.
 */
public final class Minimap {
	public static final int SIZE = 128;
	/** Blocks covered by one map pixel, per zoom step. */
	public static final float[] BLOCKS_PER_PIXEL = {0.5f, 1f, 2f, 4f};
	private static final int RESCAN_BUDGET = 1400;
	private static final long UPLOAD_INTERVAL_MS = 90L;
	private static final Identifier ID = FishClient.id("dynamic/minimap");

	@Nullable
	private static DynamicTexture texture;
	@Nullable
	private static TextureSetup setup;
	private static final int[] PIXELS = new int[SIZE * SIZE];
	private static final boolean[] KNOWN = new boolean[SIZE * SIZE];
	private static int cursor;
	private static int centerCellX = Integer.MIN_VALUE;
	private static int centerCellZ;
	private static int zoomIndex = -1;
	@Nullable
	private static ClientLevel lastLevel;
	private static long lastUpload;
	private static boolean changed;
	private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();

	private static float step() {
		return BLOCKS_PER_PIXEL[Math.clamp(zoomIndex, 0, BLOCKS_PER_PIXEL.length - 1)];
	}

	/** World X of the map centre, used by the renderer for smooth sub-pixel scrolling. */
	public static double centerWorldX() {
		return centerCellX * step();
	}

	public static double centerWorldZ() {
		return centerCellZ * step();
	}

	public static float blocksPerPixel() {
		return step();
	}

	public static void update(Minecraft mc) {
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (player == null || level == null) {
			return;
		}
		int zoom = Math.clamp(FishConfig.get().minimapZoom, 0, BLOCKS_PER_PIXEL.length - 1);
		boolean reset = zoom != zoomIndex || level != lastLevel;
		zoomIndex = zoom;
		lastLevel = level;
		float step = step();
		int cellX = (int) Math.floor(player.getX() / step);
		int cellZ = (int) Math.floor(player.getZ() / step);
		if (reset || centerCellX == Integer.MIN_VALUE) {
			Arrays.fill(KNOWN, false);
			Arrays.fill(PIXELS, 0);
			centerCellX = cellX;
			centerCellZ = cellZ;
		} else if (cellX != centerCellX || cellZ != centerCellZ) {
			shift(cellX - centerCellX, cellZ - centerCellZ);
			centerCellX = cellX;
			centerCellZ = cellZ;
		}
		scan(level, player);
		upload();
	}

	private static void shift(int dx, int dz) {
		if (Math.abs(dx) >= SIZE || Math.abs(dz) >= SIZE) {
			Arrays.fill(KNOWN, false);
			Arrays.fill(PIXELS, 0);
			return;
		}
		int[] oldPixels = PIXELS.clone();
		boolean[] oldKnown = KNOWN.clone();
		for (int z = 0; z < SIZE; z++) {
			for (int x = 0; x < SIZE; x++) {
				int sx = x + dx;
				int sz = z + dz;
				int i = x + z * SIZE;
				if (sx >= 0 && sx < SIZE && sz >= 0 && sz < SIZE) {
					int j = sx + sz * SIZE;
					PIXELS[i] = oldPixels[j];
					KNOWN[i] = oldKnown[j];
				} else {
					PIXELS[i] = 0;
					KNOWN[i] = false;
				}
			}
		}
		changed = true;
	}

	private static void scan(ClientLevel level, LocalPlayer player) {
		int originX = centerCellX - SIZE / 2;
		int originZ = centerCellZ - SIZE / 2;
		float step = step();
		boolean ceiling = level.dimensionType().hasCeiling();
		int playerY = player.getBlockY();
		int total = SIZE * SIZE;
		int unknownFound = 0;
		// Unknown pixels first: walk the whole image once, then refresh known ones with what budget is left.
		for (int n = 0; n < total && unknownFound < RESCAN_BUDGET; n++) {
			int i = (cursor + n) % total;
			boolean known = KNOWN[i];
			if (known && n > RESCAN_BUDGET / 2) {
				continue;
			}
			int px = i % SIZE;
			int pz = i / SIZE;
			int wx = (int) Math.floor((originX + px) * step);
			int wz = (int) Math.floor((originZ + pz) * step);
			if (!level.hasChunk(wx >> 4, wz >> 4)) {
				continue;
			}
			int color = columnColor(level, wx, wz, ceiling, playerY, step);
			if (PIXELS[i] != color || !known) {
				PIXELS[i] = color;
				changed = true;
			}
			KNOWN[i] = true;
			unknownFound++;
		}
		cursor = (cursor + RESCAN_BUDGET) % total;
	}

	private static int surfaceY(ClientLevel level, int wx, int wz, boolean ceiling, int playerY) {
		if (!ceiling) {
			return level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
		}
		int y = Math.min(playerY + 2, level.getMaxY());
		POS.set(wx, y, wz);
		while (y > level.getMinY() && level.getBlockState(POS).isAir()) {
			y--;
			POS.setY(y);
		}
		return y;
	}

	private static int columnColor(ClientLevel level, int wx, int wz, boolean ceiling, int playerY, float step) {
		int y = surfaceY(level, wx, wz, ceiling, playerY);
		POS.set(wx, y, wz);
		BlockState state = level.getBlockState(POS);
		MapColor color = state.getMapColor(level, POS);
		if (color == MapColor.NONE) {
			return 0xFF0B1220;
		}
		MapColor.Brightness brightness = MapColor.Brightness.NORMAL;
		if (color == MapColor.WATER) {
			// Deeper water is darker, like on vanilla maps.
			int depth = 0;
			int bottom = y;
			while (depth < 10 && bottom > level.getMinY() && !level.getFluidState(POS.setY(--bottom)).isEmpty()) {
				depth++;
			}
			brightness = depth > 7 ? MapColor.Brightness.LOW : depth > 3 ? MapColor.Brightness.NORMAL : MapColor.Brightness.HIGH;
		} else {
			// The neighbour to the north shades slopes, which gives the map its relief.
			int northY = surfaceY(level, wx, wz - Math.max(1, (int) step), ceiling, playerY);
			if (northY > y) {
				brightness = MapColor.Brightness.LOW;
			} else if (northY < y) {
				brightness = MapColor.Brightness.HIGH;
			}
		}
		return 0xFF000000 | color.calculateARGBColor(brightness);
	}

	private static void upload() {
		long now = System.currentTimeMillis();
		if (!changed || now - lastUpload < UPLOAD_INTERVAL_MS) {
			return;
		}
		lastUpload = now;
		changed = false;
		if (texture == null) {
			texture = new DynamicTexture(() -> "fishclient minimap", new NativeImage(SIZE, SIZE, true));
			Minecraft.getInstance().getTextureManager().register(ID, texture);
			setup = TextureSetup.singleTexture(texture.getTextureView(), Textures.nearest());
		}
		NativeImage image = texture.getPixels();
		if (image == null) {
			return;
		}
		for (int z = 0; z < SIZE; z++) {
			for (int x = 0; x < SIZE; x++) {
				int c = PIXELS[x + z * SIZE];
				image.setPixel(x, z, ARGB.color(c >>> 24, c >> 16 & 0xFF, c >> 8 & 0xFF, c & 0xFF));
			}
		}
		texture.upload();
	}

	@Nullable
	public static TextureSetup setup() {
		return setup;
	}

	private Minimap() {
	}
}
