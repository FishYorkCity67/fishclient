package dev.fishclient.feature;

import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import dev.fishclient.FishClient;
import dev.fishclient.config.FishConfig;
import dev.fishclient.config.Json;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Remembers what lies on the other side of each nether portal. The server only ever sends the dimension the player is
 * in, so the other side cannot be shown live: instead a panorama is taken at both ends of a trip, and each portal then
 * shows the panorama of the place it leads to, as it looked on the last visit.
 */
public final class PortalViews {
	/** The blocks of one portal, identified by dimension and lowest corner. */
	public static final class Portal {
		public String dim = "";
		public int minX;
		public int minY;
		public int minZ;
		public int maxX;
		public int maxY;
		public int maxZ;
		public String axis = "x";

		public String id() {
			return this.dim + "|" + this.minX + "," + this.minY + "," + this.minZ;
		}

		boolean contains(BlockPos pos) {
			return pos.getX() >= this.minX && pos.getX() <= this.maxX && pos.getY() >= this.minY && pos.getY() <= this.maxY
				&& pos.getZ() >= this.minZ && pos.getZ() <= this.maxZ;
		}

		public Vec3 center() {
			return new Vec3((this.minX + this.maxX + 1) / 2d, (this.minY + this.maxY + 1) / 2d, (this.minZ + this.maxZ + 1) / 2d);
		}

		public boolean axisX() {
			return this.axis.equals("x");
		}
	}

	/** A portal and the panorama it shows. */
	public static final class Entry {
		public Portal portal = new Portal();
		public String view = "";
		/** The direction (yaw) the panorama's first face looks: straight out of the portal at the other end. */
		public int viewYaw;
		public String target = "";
		public long time;
	}

	/** A finished panorama: its file and the direction it faces. */
	public record View(String file, int yaw) {
	}

	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int MAX_PORTAL_BLOCKS = 23 * 23;
	private static String loadedServer = "\0";
	private static List<Entry> entries = new ArrayList<>();
	private static final Map<String, Identifier> TEXTURES = new HashMap<>();
	private static final Set<String> LOADING = new HashSet<>();
	private static int nextTexture;

	// Trip tracking.
	@Nullable
	private static String lastDim;
	private static @Nullable Portal departure;
	@Nullable
	private static View departureView;
	private static boolean departureCaptured;
	private static long lastInPortal;
	private static int insideTicks;
	private static float departureYaw;
	@Nullable
	private static Portal arrivalFrom;
	@Nullable
	private static View arrivalFromView;
	private static int arrivalTicks = -1;

	public static String serverKey() {
		return Waypoints.serverKey().replaceAll("[^A-Za-z0-9._-]", "_");
	}

	public static Path dir() {
		return Json.file("portal_views").resolve(serverKey());
	}

	private static void ensureLoaded() {
		String server = serverKey();
		if (server.equals(loadedServer)) {
			return;
		}
		loadedServer = server;
		List<Entry> saved = Json.read(dir().resolve("index.json"), new TypeToken<List<Entry>>() { }.getType());
		entries = saved != null ? saved : new ArrayList<>();
	}

	private static void save() {
		Json.write(dir().resolve("index.json"), entries);
	}

	public static List<Entry> entries() {
		ensureLoaded();
		return entries;
	}

	public static void remove(Entry entry) {
		ensureLoaded();
		entries.remove(entry);
		if (entries.stream().noneMatch(e -> e.view.equals(entry.view))) {
			try {
				Files.deleteIfExists(dir().resolve(entry.view));
			} catch (IOException e) {
				LOGGER.warn("FishClient could not delete the portal view {}", entry.view, e);
			}
		}
		save();
	}

	public static void clear() {
		for (Entry entry : new ArrayList<>(entries())) {
			remove(entry);
		}
	}

	@Nullable
	public static Entry find(String portalId) {
		for (Entry e : entries()) {
			if (e.portal.id().equals(portalId)) {
				return e;
			}
		}
		return null;
	}

	private static void link(Portal portal, View view, String target) {
		ensureLoaded();
		entries.removeIf(e -> e.portal.id().equals(portal.id()));
		Entry entry = new Entry();
		entry.portal = portal;
		entry.view = view.file();
		entry.viewYaw = view.yaw();
		entry.target = target;
		entry.time = System.currentTimeMillis();
		entries.add(entry);
		save();
	}

	/** The view of the portal the player is about to travel through, if a previous trip recorded one. */
	@Nullable
	public static Entry departureEntry() {
		return departure != null ? find(departure.id()) : null;
	}

	public static float departureYaw() {
		return departureYaw;
	}

	// ---- Portal blocks ----

	private static boolean isPortal(ClientLevel level, BlockPos pos) {
		return level.getBlockState(pos).is(Blocks.NETHER_PORTAL);
	}

	/** Collects the connected portal blocks around {@code start}. */
	@Nullable
	public static Portal scan(ClientLevel level, BlockPos start, String dim) {
		BlockState first = level.getBlockState(start);
		if (!first.is(Blocks.NETHER_PORTAL)) {
			return null;
		}
		Direction.Axis axis = first.getValue(NetherPortalBlock.AXIS);
		Portal p = new Portal();
		p.dim = dim;
		p.axis = axis == Direction.Axis.X ? "x" : "z";
		p.minX = p.maxX = start.getX();
		p.minY = p.maxY = start.getY();
		p.minZ = p.maxZ = start.getZ();
		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start.immutable());
		seen.add(start.immutable());
		Direction[] sides = axis == Direction.Axis.X
			? new Direction[]{Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST}
			: new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH};
		while (!queue.isEmpty() && seen.size() < MAX_PORTAL_BLOCKS) {
			BlockPos pos = queue.poll();
			p.minX = Math.min(p.minX, pos.getX());
			p.minY = Math.min(p.minY, pos.getY());
			p.minZ = Math.min(p.minZ, pos.getZ());
			p.maxX = Math.max(p.maxX, pos.getX());
			p.maxY = Math.max(p.maxY, pos.getY());
			p.maxZ = Math.max(p.maxZ, pos.getZ());
			for (Direction side : sides) {
				BlockPos next = pos.relative(side);
				if (!seen.contains(next) && isPortal(level, next)) {
					seen.add(next);
					queue.add(next);
				}
			}
		}
		return p;
	}

	@Nullable
	private static BlockPos portalNear(ClientLevel level, BlockPos center, int radius) {
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
			if (isPortal(level, pos)) {
				return pos.immutable();
			}
		}
		return null;
	}

	/** Whether the portal still stands: its lowest corner block has to be a portal block. */
	public static boolean stillThere(ClientLevel level, Portal p) {
		return isPortal(level, new BlockPos(p.minX, p.minY, p.minZ));
	}

	/** The side of the portal with more free space, where the camera stands for its panorama, as a yaw. */
	static int openSideYaw(ClientLevel level, Portal p) {
		int plus = 0;
		int minus = 0;
		for (int step = 1; step <= 3; step++) {
			for (int a = p.axisX() ? p.minX : p.minZ; a <= (p.axisX() ? p.maxX : p.maxZ); a++) {
				for (int y = p.minY; y <= p.maxY; y++) {
					BlockPos front = p.axisX() ? new BlockPos(a, y, p.minZ + step) : new BlockPos(p.minX + step, y, a);
					BlockPos back = p.axisX() ? new BlockPos(a, y, p.minZ - step) : new BlockPos(p.minX - step, y, a);
					plus += level.getBlockState(front).isAir() ? 1 : 0;
					minus += level.getBlockState(back).isAir() ? 1 : 0;
				}
			}
		}
		// Yaw 0 looks towards +Z, 180 towards -Z, -90 towards +X, 90 towards -X.
		if (p.axisX()) {
			return plus >= minus ? 0 : 180;
		}
		return plus >= minus ? -90 : 90;
	}

	// ---- Trip tracking ----

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		LocalPlayer player = mc.player;
		if (level == null || player == null) {
			lastDim = null;
			return;
		}
		FishConfig config = FishConfig.get();
		String dim = level.dimension().identifier().toString();
		long now = Util.getMillis();
		if (!dim.equals(lastDim)) {
			if (lastDim != null && departure != null && now - lastInPortal < 20000L) {
				arrivalFrom = departure;
				arrivalFromView = departureView;
				arrivalTicks = 0;
			}
			lastDim = dim;
			departure = null;
			departureView = null;
			insideTicks = 0;
		}

		BlockPos feet = player.blockPosition();
		BlockPos inside = isPortal(level, feet) ? feet : isPortal(level, feet.above()) ? feet.above() : null;
		if (inside != null && mc.screen == null) {
			lastInPortal = now;
			if (departure == null || !departure.contains(inside)) {
				departure = scan(level, inside, dim);
				departureView = null;
				departureCaptured = false;
			}
			departureYaw = player.getYRot();
			insideTicks++;
			// Standing in the portal for a moment is the warm-up of the trip: the panorama of this side is taken now.
			// Right after an arrival the arrival capture below takes this picture, so it is not taken twice.
			if (insideTicks == 12 && arrivalTicks < 0 && !departureCaptured && config.portalViews && departure != null && !PortalCapture.busy()) {
				departureCaptured = true;
				Portal here = departure;
				PortalCapture.start(level, here, view -> {
					if (here == departure) {
						departureView = view;
					}
				});
			}
		} else {
			insideTicks = 0;
		}

		if (arrivalTicks >= 0 && mc.screen == null) {
			arrivalTicks++;
			if (arrivalTicks >= 30 && !PortalCapture.busy()) {
				BlockPos found = portalNear(level, feet, 3);
				Portal from = arrivalFrom;
				View fromView = arrivalFromView;
				arrivalTicks = -1;
				arrivalFrom = null;
				arrivalFromView = null;
				Portal arrival = found != null ? scan(level, found, dim) : null;
				if (arrival != null && from != null && config.portalViews) {
					// The portal here shows where the player came from, the portal there shows this place.
					if (fromView != null) {
						link(arrival, fromView, from.dim);
					}
					PortalCapture.start(level, arrival, view -> {
						link(from, view, arrival.dim);
						// The same picture serves the trip back, when the player steps into this portal again.
						if (departure != null && departure.id().equals(arrival.id())) {
							departureView = view;
							departureCaptured = true;
						}
					});
				}
			}
		}
	}

	// ---- Files and textures ----

	/** Saves a finished panorama atlas and makes its texture available at once. Returns its file name. */
	static String store(NativeImage atlas, Portal portal) {
		Path folder = dir();
		String file = "view_" + portal.dim.replaceAll("[^A-Za-z0-9]", "_") + "_" + portal.minX + "_" + portal.minY + "_" + portal.minZ + "_" + System.currentTimeMillis() + ".png";
		NativeImage copy = new NativeImage(atlas.getWidth(), atlas.getHeight(), false);
		copy.copyFrom(atlas);
		Util.ioPool().execute(() -> {
			try {
				Files.createDirectories(folder);
				copy.writeToFile(folder.resolve(file));
			} catch (IOException e) {
				LOGGER.warn("FishClient could not save the portal view", e);
			} finally {
				copy.close();
			}
		});
		register(file, atlas);
		return file;
	}

	private static void register(String file, NativeImage image) {
		Identifier id = FishClient.id("dynamic/portal_view/" + nextTexture++);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "fishclient portal view", image));
		TEXTURES.put(file, id);
	}

	/** The texture of a saved view, loaded from disk the first time it is needed. Null while it is still loading. */
	@Nullable
	public static Identifier texture(String file) {
		Identifier known = TEXTURES.get(file);
		if (known != null || file.isEmpty()) {
			return known;
		}
		if (LOADING.add(file)) {
			Path path = dir().resolve(file);
			Minecraft mc = Minecraft.getInstance();
			Util.ioPool().execute(() -> {
				try (InputStream in = Files.newInputStream(path)) {
					NativeImage image = NativeImage.read(in);
					mc.execute(() -> register(file, image));
				} catch (IOException e) {
					LOGGER.warn("FishClient could not load the portal view {}", file, e);
				}
			});
		}
		return null;
	}

	private PortalViews() {
	}
}
