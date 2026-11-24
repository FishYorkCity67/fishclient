package dev.fishclient.feature;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.fishclient.FishClient;
import dev.fishclient.config.FishConfig;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Util;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * Procedural sky presets, drawn as a coloured dome instead of the overworld sky. They are purely client-side:
 * the world's time, light and weather stay as they are.
 */
public final class CustomSky {
	/** Colours are RGB. {@code aurora}, {@code nebula} and {@code stars} scale their layers from 0 to 1. */
	public record Preset(String id, String name, int zenith, int middle, int horizon, float stars, float aurora, int aurora1, int aurora2, float nebula, int nebula1, int nebula2) {
	}

	public static final List<Preset> PRESETS = List.of(
		new Preset("northern_lights", "Northern Lights", 0x050B1E, 0x0A1A33, 0x123044, 0.9f, 1f, 0x38F2A6, 0x8A5CFF, 0.1f, 0x12305A, 0x0E3A4A),
		new Preset("galaxy", "Galaxy", 0x04030F, 0x0B0822, 0x1A1238, 1f, 0f, 0, 0, 1f, 0x6A3DE8, 0x2E8BD8),
		new Preset("blood_moon", "Blood Moon", 0x120207, 0x2A0610, 0x54101A, 0.5f, 0f, 0, 0, 0.5f, 0x7A1020, 0x401018),
		new Preset("nebula", "Nebula", 0x0A0420, 0x1A0B3A, 0x31134F, 0.8f, 0f, 0, 0, 1f, 0xD04CC8, 0x5C4CF0),
		new Preset("deep_ocean", "Deep Ocean", 0x021024, 0x05284A, 0x0B5A78, 0.35f, 0.45f, 0x2FD6C3, 0x1E9FD8, 0.45f, 0x0F6A8A, 0x0A3E70),
		new Preset("vaporwave", "Vaporwave", 0x170A3A, 0x6A1E8A, 0xF0589A, 0.6f, 0f, 0, 0, 0.3f, 0x38C8F0, 0xB43CD8),
		new Preset("sunset_dream", "Sunset Dream", 0x1E1A4E, 0xB4486A, 0xFFA14F, 0.15f, 0f, 0, 0, 0.35f, 0xFF7A5C, 0x8A3C9C),
		new Preset("emerald_veil", "Emerald Veil", 0x03140F, 0x07271F, 0x0E4A38, 0.8f, 0.8f, 0x34F0A0, 0x7CF2D8, 0.3f, 0x0C5A42, 0x0A3C3A)
	);

	private static final int LON = 72;
	private static final int LAT = 30;
	private static final int STAR_COUNT = 900;
	private static final float RADIUS = 90f;
	// Reused every frame so the sky produces no garbage.
	private static final float[][] DOME_X = new float[LON + 1][LAT + 1];
	private static final float[][] DOME_Y = new float[LON + 1][LAT + 1];
	private static final float[][] DOME_Z = new float[LON + 1][LAT + 1];
	private static final int[][] DOME_COLOR = new int[LON + 1][LAT + 1];
	private static final float[][] STARS = new float[STAR_COUNT][5];
	private static final int MAX_METEORS = 2;
	private static final Meteor[] METEORS = new Meteor[MAX_METEORS];
	private static final Random RANDOM = new Random(4711L);
	private static long nextMeteor = 8000L;
	@Nullable
	private static RenderPipeline pipeline;
	@Nullable
	private static ByteBufferBuilder bytes;

	private static final class Meteor {
		final Vector3f from = new Vector3f();
		final Vector3f to = new Vector3f();
		long start;
		int duration;
	}

	static {
		Random r = new Random(10842L);
		for (float[] s : STARS) {
			// Uniform points on the upper part of a sphere, slightly below the horizon too.
			float y = r.nextFloat() * 1.1f - 0.1f;
			float a = r.nextFloat() * (float) Math.PI * 2f;
			float h = (float) Math.sqrt(Math.max(0f, 1f - y * y));
			s[0] = h * (float) Math.cos(a);
			s[1] = y;
			s[2] = h * (float) Math.sin(a);
			s[3] = 0.05f + r.nextFloat() * r.nextFloat() * 0.13f;
			s[4] = r.nextFloat() * 6.28f;
		}
	}

	public static Preset preset() {
		String id = FishConfig.get().skyPreset;
		for (Preset p : PRESETS) {
			if (p.id().equals(id)) {
				return p;
			}
		}
		return PRESETS.getFirst();
	}

	public static boolean active() {
		if (!FishConfig.get().sky) {
			return false;
		}
		ClientLevel level = Minecraft.getInstance().level;
		return level != null && level.dimensionType().skybox() == DimensionType.Skybox.OVERWORLD;
	}

	/** The terrain fades into the horizon colour, otherwise distant land glows in vanilla's blue. */
	public static void adjustFog(Vector4f dest, FogType fogType) {
		if (fogType != FogType.NONE || !active()) {
			return;
		}
		int c = preset().horizon();
		dest.set((c >> 16 & 0xFF) / 255f, (c >> 8 & 0xFF) / 255f, (c & 0xFF) / 255f, 1f);
	}

	private static RenderPipeline pipeline() {
		if (pipeline == null) {
			pipeline = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
				.withLocation(FishClient.id("pipeline/sky_dome"))
				.withVertexShader("core/position_color")
				.withFragmentShader("core/position_color")
				.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
				.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
				// The dome is seen from the inside, and the stars from whichever side they happen to face.
				.withCull(false)
				.build();
		}
		return pipeline;
	}

	public static void render() {
		Preset p = preset();
		float time = Util.getMillis() / 1000f;
		int quads = LON * LAT + STAR_COUNT + MAX_METEORS * 3;
		if (bytes == null) {
			bytes = ByteBufferBuilder.exactlySized(quads * 4 * DefaultVertexFormat.POSITION_COLOR.getVertexSize());
		}
		BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		buildDome(builder, p, time);
		buildStars(builder, p, time);
		if (FishConfig.get().shootingStars) {
			buildMeteors(builder);
		}
		try (MeshData mesh = builder.buildOrThrow()) {
			int indexCount = mesh.drawState().indexCount();
			try (GpuBuffer vertices = RenderSystem.getDevice().createBuffer(() -> "FishClient sky", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer())) {
				RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
				GpuBuffer indexBuffer = indices.getBuffer(indexCount);
				GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
					.writeTransform(RenderSystem.getModelViewMatrix(), new Vector4f(1f, 1f, 1f, 1f), new Vector3f(), new Matrix4f());
				GpuTextureView color = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
				GpuTextureView depth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
				try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
					.createRenderPass(() -> "FishClient sky", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
					pass.setPipeline(pipeline());
					RenderSystem.bindDefaultUniforms(pass);
					pass.setUniform("DynamicTransforms", transforms);
					pass.setVertexBuffer(0, vertices);
					pass.setIndexBuffer(indexBuffer, indices.type());
					pass.drawIndexed(0, 0, indexCount, 1);
				}
			}
		}
	}

	private static void buildDome(BufferBuilder b, Preset p, float time) {
		float[][] cx = DOME_X;
		float[][] cy = DOME_Y;
		float[][] cz = DOME_Z;
		int[][] col = DOME_COLOR;
		for (int i = 0; i <= LON; i++) {
			float lon = i / (float) LON * (float) Math.PI * 2f;
			float sinLon = (float) Math.sin(lon);
			float cosLon = (float) Math.cos(lon);
			for (int j = 0; j <= LAT; j++) {
				// Latitude runs from just below the horizon up to the zenith.
				float lat = -0.2f + (j / (float) LAT) * (1.5708f + 0.2f);
				float y = (float) Math.sin(lat);
				float h = (float) Math.cos(lat);
				cx[i][j] = cosLon * h * RADIUS;
				cy[i][j] = y * RADIUS;
				cz[i][j] = sinLon * h * RADIUS;
				col[i][j] = domeColor(p, cosLon * h, y, sinLon * h, lon, time);
			}
		}
		for (int i = 0; i < LON; i++) {
			for (int j = 0; j < LAT; j++) {
				b.addVertex(cx[i][j], cy[i][j], cz[i][j]).setColor(col[i][j]);
				b.addVertex(cx[i][j + 1], cy[i][j + 1], cz[i][j + 1]).setColor(col[i][j + 1]);
				b.addVertex(cx[i + 1][j + 1], cy[i + 1][j + 1], cz[i + 1][j + 1]).setColor(col[i + 1][j + 1]);
				b.addVertex(cx[i + 1][j], cy[i + 1][j], cz[i + 1][j]).setColor(col[i + 1][j]);
			}
		}
	}

	private static int domeColor(Preset p, float x, float y, float z, float lon, float time) {
		float up = Math.max(y, 0f);
		float r;
		float g;
		float bl;
		if (up < 0.35f) {
			float t = up / 0.35f;
			r = mix(red(p.horizon()), red(p.middle()), t);
			g = mix(green(p.horizon()), green(p.middle()), t);
			bl = mix(blue(p.horizon()), blue(p.middle()), t);
		} else {
			float t = (up - 0.35f) / 0.65f;
			r = mix(red(p.middle()), red(p.zenith()), t);
			g = mix(green(p.middle()), green(p.zenith()), t);
			bl = mix(blue(p.middle()), blue(p.zenith()), t);
		}
		if (p.nebula() > 0f) {
			float n = fbm(x * 2.4f + time * 0.006f, y * 2.4f, z * 2.4f - time * 0.004f);
			float a = smooth(0.48f, 0.82f, n) * p.nebula() * 0.8f;
			float m = fbm(x * 3.7f + 5.1f, y * 3.7f, z * 3.7f + 2.3f);
			r += a * mix(red(p.nebula1()), red(p.nebula2()), m);
			g += a * mix(green(p.nebula1()), green(p.nebula2()), m);
			bl += a * mix(blue(p.nebula1()), blue(p.nebula2()), m);
		}
		if (p.aurora() > 0f && y > 0.02f) {
			// Curtains: bright ribbons that wander around the sky and fade towards the top.
			float band = smooth(0.08f, 0.22f, y) * (1f - smooth(0.45f, 0.82f, y));
			float ribbon1 = (float) Math.sin(lon * 5f + time * 0.22f + 2.2f * (float) Math.sin(lon * 2f - time * 0.13f));
			float ribbon2 = (float) Math.sin(lon * 11f - time * 0.31f + (float) Math.sin(lon * 7f + time * 0.17f));
			float intensity = band * (0.25f + 0.75f * smooth(0.1f, 1f, ribbon1 * 0.7f + ribbon2 * 0.3f)) * p.aurora();
			float blend = smooth(0.2f, 0.6f, y);
			r += intensity * mix(red(p.aurora1()), red(p.aurora2()), blend);
			g += intensity * mix(green(p.aurora1()), green(p.aurora2()), blend);
			bl += intensity * mix(blue(p.aurora1()), blue(p.aurora2()), blend);
		}
		return 0xFF000000 | Math.min(255, (int) (r * 255f)) << 16 | Math.min(255, (int) (g * 255f)) << 8 | Math.min(255, (int) (bl * 255f));
	}

	private static void buildStars(BufferBuilder b, Preset p, float time) {
		if (p.stars() <= 0f) {
			return;
		}
		int visible = (int) (STAR_COUNT * p.stars());
		Vector3f right = new Vector3f();
		Vector3f up = new Vector3f();
		Vector3f normal = new Vector3f();
		for (int i = 0; i < visible; i++) {
			float[] s = STARS[i];
			normal.set(s[0], s[1], s[2]);
			right.set(0f, 1f, 0f).cross(normal);
			if (right.lengthSquared() < 1e-4f) {
				right.set(1f, 0f, 0f);
			}
			right.normalize(s[3]);
			normal.cross(right, up).normalize(s[3]);
			float twinkle = 0.6f + 0.4f * (float) Math.sin(time * 1.4f + s[4]);
			int color = (int) (twinkle * 235f) << 24 | 0xE8F0FF;
			float cx = s[0] * (RADIUS - 2f);
			float cy = s[1] * (RADIUS - 2f);
			float cz = s[2] * (RADIUS - 2f);
			b.addVertex(cx - right.x - up.x, cy - right.y - up.y, cz - right.z - up.z).setColor(color);
			b.addVertex(cx - right.x + up.x, cy - right.y + up.y, cz - right.z + up.z).setColor(color);
			b.addVertex(cx + right.x + up.x, cy + right.y + up.y, cz + right.z + up.z).setColor(color);
			b.addVertex(cx + right.x - up.x, cy + right.y - up.y, cz + right.z - up.z).setColor(color);
		}
	}

	/** Sends a shooting star across the sky right away instead of waiting for the next random one. */
	public static void shootingStarNow() {
		nextMeteor = 0L;
	}

	private static void buildMeteors(BufferBuilder b) {
		long now = Util.getMillis();
		for (int i = 0; i < MAX_METEORS; i++) {
			Meteor m = METEORS[i];
			if (m == null || now - m.start > m.duration) {
				if (now >= nextMeteor) {
					if (m == null) {
						m = new Meteor();
						METEORS[i] = m;
					}
					spawn(m, now);
					nextMeteor = now + 5000L + RANDOM.nextInt(20000);
				}
				if (METEORS[i] == null || now - METEORS[i].start > METEORS[i].duration) {
					continue;
				}
				m = METEORS[i];
			}
			float t = (now - m.start) / (float) m.duration;
			float fade = t < 0.15f ? t / 0.15f : 1f - (t - 0.15f) / 0.85f;
			Vector3f head = new Vector3f(m.from).lerp(m.to, t);
			Vector3f tail = new Vector3f(m.from).lerp(m.to, Math.max(0f, t - 0.22f));
			Vector3f dir = new Vector3f(head).sub(tail);
			if (dir.lengthSquared() < 1e-4f) {
				continue;
			}
			Vector3f side = new Vector3f(dir).cross(head).normalize(0.16f);
			int bright = (int) (fade * 255f) << 24 | 0xFFFFFF;
			int dim = 0x00FFFFFF;
			b.addVertex(tail.x - side.x, tail.y - side.y, tail.z - side.z).setColor(dim);
			b.addVertex(tail.x + side.x, tail.y + side.y, tail.z + side.z).setColor(dim);
			b.addVertex(head.x + side.x, head.y + side.y, head.z + side.z).setColor(bright);
			b.addVertex(head.x - side.x, head.y - side.y, head.z - side.z).setColor(bright);
		}
	}

	private static void spawn(Meteor m, long now) {
		float a = RANDOM.nextFloat() * 6.283f;
		float y0 = 0.45f + RANDOM.nextFloat() * 0.4f;
		float travel = 0.5f + RANDOM.nextFloat() * 0.5f;
		float a1 = a + (RANDOM.nextBoolean() ? travel : -travel);
		m.from.set((float) Math.cos(a), y0, (float) Math.sin(a)).normalize(RADIUS - 3f);
		m.to.set((float) Math.cos(a1), y0 - 0.25f, (float) Math.sin(a1)).normalize(RADIUS - 3f);
		m.start = now;
		m.duration = 700 + RANDOM.nextInt(600);
	}

	private static float red(int c) {
		return (c >> 16 & 0xFF) / 255f;
	}

	private static float green(int c) {
		return (c >> 8 & 0xFF) / 255f;
	}

	private static float blue(int c) {
		return (c & 0xFF) / 255f;
	}

	private static float mix(float a, float b, float t) {
		return a + (b - a) * t;
	}

	private static float smooth(float e0, float e1, float v) {
		float t = Math.clamp((v - e0) / (e1 - e0), 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	private static float fbm(float x, float y, float z) {
		float sum = 0f;
		float amp = 0.5f;
		for (int i = 0; i < 3; i++) {
			sum += amp * (0.5f + 0.5f * noise(x, y, z));
			x *= 2.03f;
			y *= 2.03f;
			z *= 2.03f;
			amp *= 0.5f;
		}
		return sum / 0.875f;
	}

	/** Cheap smooth value noise; the dome is coarse enough that a sine product looks soft and cloudy. */
	private static float noise(float x, float y, float z) {
		return (float) (Math.sin(x * 1.7 + Math.sin(z * 1.3 + y * 0.9)) * Math.cos(z * 1.9 - Math.sin(x * 1.1 + y * 1.4)) * Math.sin(y * 2.3 + x * 0.7 + 1.3));
	}

	private CustomSky() {
	}
}
