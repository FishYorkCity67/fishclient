package dev.fishclient.feature;

import com.google.gson.reflect.TypeToken;
import dev.fishclient.config.Json;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/** Saved locations, kept per server and dimension. */
public final class Waypoints {
	public static final class Point {
		public String name = "";
		public int x;
		public int y;
		public int z;
		public String dimension = "";
		public String server = "";
		public int color = 0xFF2FD6C3;
		public boolean visible = true;
	}

	public static final int[] COLORS = {0xFF2FD6C3, 0xFF1E9FD8, 0xFF3ADB8F, 0xFFF2C14E, 0xFFFF8C40, 0xFFF0565B, 0xFFD04CC8, 0xFFEEF2FB};
	private static List<Point> points = new ArrayList<>();

	private static Path path() {
		return Json.file("waypoints.json");
	}

	public static void load() {
		List<Point> saved = Json.read(path(), new TypeToken<List<Point>>() { }.getType());
		points = saved != null ? saved : new ArrayList<>();
	}

	public static void save() {
		Json.write(path(), points);
	}

	public static List<Point> all() {
		return points;
	}

	public static String serverKey() {
		ServerData data = Minecraft.getInstance().getCurrentServer();
		return data != null && data.ip != null ? data.ip : "local";
	}

	public static String dimensionKey() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null ? mc.level.dimension().identifier().toString() : "";
	}

	/** The waypoints of the world the player is in. */
	public static List<Point> here() {
		String server = serverKey();
		String dimension = dimensionKey();
		List<Point> out = new ArrayList<>();
		for (Point p : points) {
			if (p.server.equals(server) && p.dimension.equals(dimension)) {
				out.add(p);
			}
		}
		return out;
	}

	public static Point addHere(String name) {
		Minecraft mc = Minecraft.getInstance();
		Point p = new Point();
		p.name = name;
		if (mc.player != null) {
			p.x = mc.player.getBlockX();
			p.y = mc.player.getBlockY();
			p.z = mc.player.getBlockZ();
		}
		p.dimension = dimensionKey();
		p.server = serverKey();
		p.color = COLORS[points.size() % COLORS.length];
		points.add(p);
		save();
		return p;
	}

	public static void remove(Point p) {
		points.remove(p);
		save();
	}

	private Waypoints() {
	}
}
