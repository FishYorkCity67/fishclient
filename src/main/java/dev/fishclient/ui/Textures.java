package dev.fishclient.ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import dev.fishclient.FishClient;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/** Texture lookup for the GUI, plus the generated corner masks behind rounded rectangles and glows. */
public final class Textures {
	/** A corner mask, drawn as the top-left corner of a nine-slice. The texel at u=v=1 is fully opaque. */
	public record Corner(TextureSetup setup, float size) {
	}

	private static final int OVERSAMPLE = 6;
	private static final Map<String, Corner> CORNERS = new HashMap<>();

	public static GpuSampler linear() {
		return RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, false);
	}

	public static GpuSampler nearest() {
		return RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST, FilterMode.NEAREST, false);
	}

	/**
	 * Looked up on every use and never kept: a resource reload, such as changing the language or a resource pack,
	 * replaces the GPU textures behind these ids, and a stored view would then point at a closed texture.
	 */
	public static TextureSetup of(Identifier id) {
		AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(id);
		return TextureSetup.singleTexture(texture.getTextureView(), linear());
	}

	public static Identifier icon(String name) {
		return FishClient.id("textures/gui/icons/" + name + ".png");
	}

	public static Corner filled(float radius) {
		return CORNERS.computeIfAbsent("f" + radius, k -> create(radius, 0f, 0f));
	}

	public static Corner stroke(float radius, float thickness) {
		return CORNERS.computeIfAbsent("s" + radius + "/" + thickness, k -> create(radius, thickness, 0f));
	}

	public static Corner glow(float radius, float spread) {
		return CORNERS.computeIfAbsent("g" + radius + "/" + spread, k -> create(radius, 0f, spread));
	}

	private static Corner create(float radius, float stroke, float spread) {
		float logical = Math.max(radius + spread, 1f);
		int size = Math.max(4, Math.round(logical * OVERSAMPLE));
		NativeImage image = new NativeImage(size, size, false);
		float scale = size / logical;
		float r = radius * scale;
		float s = spread * scale;
		float th = stroke * scale;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				// Distance from the arc centre, which sits at the inner edge of the mask.
				float dx = size - x - 0.5f;
				float dy = size - y - 0.5f;
				float d = (float) Math.sqrt(dx * dx + dy * dy);
				float a;
				if (spread > 0f) {
					float out = Math.clamp((d - r) / Math.max(s, 1f), 0f, 1f);
					a = (1f - out) * (1f - out);
				} else {
					a = Math.clamp(r - d + 0.5f, 0f, 1f);
					if (stroke > 0f) {
						a *= Math.clamp(d - (r - th) + 0.5f, 0f, 1f);
					}
				}
				image.setPixel(x, y, ARGB.color(Math.round(a * 255f), 255, 255, 255));
			}
		}
		DynamicTexture texture = new DynamicTexture(() -> "fishclient corner", image);
		return new Corner(TextureSetup.singleTexture(texture.getTextureView(), linear()), logical);
	}

	private Textures() {
	}
}
