package dev.fishclient.feature;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
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
import dev.fishclient.ui.Textures;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * Draws each remembered view as a window over its portal: a quad on the camera's side of the portal plane whose
 * shader turns the viewing direction into a lookup in the panorama.
 */
public final class PortalRenderer {
	/** Half the thickness of a portal block (4 of 16 pixels), plus a hair so the window covers the purple face. */
	private static final float FACE_OFFSET = 0.135f;
	@Nullable
	private static RenderPipeline pipeline;
	private static int drawnLastFrame;

	private static RenderPipeline pipeline() {
		if (pipeline == null) {
			pipeline = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
				.withLocation(FishClient.id("pipeline/portal_view"))
				.withVertexShader(FishClient.id("core/portal_view"))
				.withFragmentShader(FishClient.id("core/portal_view"))
				.withSampler("Sampler0")
				.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
				.withDepthStencilState(DepthStencilState.DEFAULT)
				.withCull(false)
				.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
				.build();
		}
		return pipeline;
	}

	/** How many portal windows the last frame drew; the automated test reads it. */
	public static int drawnLastFrame() {
		return drawnLastFrame;
	}

	public static void render(LevelRenderContext context) {
		drawnLastFrame = 0;
		FishConfig config = FishConfig.get();
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (!config.portalViews || level == null || PortalCapture.cameraOverride() != null) {
			return;
		}
		CameraRenderState camera = context.levelState().cameraRenderState;
		Vec3 eye = camera.pos;
		String dim = level.dimension().identifier().toString();
		float maxDistance = config.portalViewDistance;
		for (PortalViews.Entry entry : PortalViews.entries()) {
			PortalViews.Portal p = entry.portal;
			if (!p.dim.equals(dim)) {
				continue;
			}
			Vec3 center = p.center();
			double distance = center.distanceTo(eye);
			if (distance > maxDistance || !PortalViews.stillThere(level, p)) {
				continue;
			}
			Identifier texture = PortalViews.texture(entry.view);
			if (texture == null) {
				continue;
			}
			float fade = (float) Math.clamp((maxDistance - distance) / 12d, 0d, 1d);
			draw(camera, p, texture, fade);
			drawnLastFrame++;
		}
	}

	private static void draw(CameraRenderState camera, PortalViews.Portal p, Identifier textureId, float alpha) {
		Vec3 eye = camera.pos;
		Vec3 center = p.center();
		// Which side the camera is on decides the face to draw on and the direction that counts as "into the portal".
		boolean axisX = p.axisX();
		double side = axisX ? Math.signum(eye.z - center.z) : Math.signum(eye.x - center.x);
		if (side == 0d) {
			side = 1d;
		}
		int intoYaw = axisX ? (side > 0d ? 180 : 0) : (side > 0d ? 90 : -90);
		// The panorama's faces are stored relative to the outward direction at the other end, so turning the view by the
		// direction into this portal makes "looking in here" the same as "looking out there".
		int turn = Math.floorMod(Math.round(intoYaw / 90f), 4);
		int color = ARGB.color(Math.round(alpha * 255f), Math.round(turn / 3f * 255f), 255, 255);

		float x0;
		float x1;
		float z0;
		float z1;
		if (axisX) {
			x0 = p.minX;
			x1 = p.maxX + 1;
			z0 = z1 = (float) (center.z + side * FACE_OFFSET);
		} else {
			z0 = p.minZ;
			z1 = p.maxZ + 1;
			x0 = x1 = (float) (center.x + side * FACE_OFFSET);
		}
		float y0 = p.minY;
		float y1 = p.maxY + 1;

		try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(4 * DefaultVertexFormat.POSITION_COLOR.getVertexSize())) {
			BufferBuilder b = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
			vertex(b, x0, y0, z0, eye, color);
			vertex(b, x0, y1, z0, eye, color);
			vertex(b, x1, y1, z1, eye, color);
			vertex(b, x1, y0, z1, eye, color);
			try (MeshData mesh = b.buildOrThrow()) {
				int indexCount = mesh.drawState().indexCount();
				try (GpuBuffer vertices = RenderSystem.getDevice().createBuffer(() -> "FishClient portal", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer())) {
					RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
					GpuBuffer indexBuffer = indices.getBuffer(indexCount);
					GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
						.writeTransform(new Matrix4f(camera.viewRotationMatrix), new Vector4f(1f, 1f, 1f, 1f), new Vector3f(), new Matrix4f());
					GpuTextureView colorTarget = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
					GpuTextureView depthTarget = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
					AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(textureId);
					try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
						.createRenderPass(() -> "FishClient portal", colorTarget, OptionalInt.empty(), depthTarget, OptionalDouble.empty())) {
						pass.setPipeline(pipeline());
						RenderSystem.bindDefaultUniforms(pass);
						pass.setUniform("DynamicTransforms", transforms);
						pass.bindTexture("Sampler0", texture.getTextureView(), Textures.linear());
						pass.setVertexBuffer(0, vertices);
						pass.setIndexBuffer(indexBuffer, indices.type());
						pass.drawIndexed(0, 0, indexCount, 1);
					}
				}
			}
		}
	}

	private static void vertex(BufferBuilder b, float x, float y, float z, Vec3 eye, int color) {
		b.addVertex((float) (x - eye.x), (float) (y - eye.y), (float) (z - eye.z)).setColor(color);
	}

	private PortalRenderer() {
	}
}
