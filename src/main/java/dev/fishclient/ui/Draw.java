package dev.fishclient.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.fishclient.FishClient;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/** Float-precision 2D drawing on top of Minecraft's GUI render state. */
public final class Draw {
	private static final Style REGULAR = Style.EMPTY.withFont(new FontDescription.Resource(FishClient.id("inter")));
	private static final Style BOLD = Style.EMPTY.withFont(new FontDescription.Resource(FishClient.id("inter_bold")));
	private static final Map<String, FormattedCharSequence> REGULAR_TEXT = new HashMap<>();
	private static final Map<String, FormattedCharSequence> BOLD_TEXT = new HashMap<>();

	public final GuiGraphicsExtractor g;
	public final Font font;
	/** Multiplied into every colour, which is how whole screens fade. */
	public float alpha = 1f;

	public Draw(GuiGraphicsExtractor g) {
		this.g = g;
		this.font = Minecraft.getInstance().font;
	}

	/**
	 * Scale for drawing in "menu units", so HUD and menu look the same at every GUI scale. A unit is about two
	 * physical pixels at 1080p, and grows with the window.
	 */
	public static float unit() {
		Window window = Minecraft.getInstance().getWindow();
		float physical = Math.max(1f, Math.min(window.getWidth() / 960f, window.getHeight() / 540f));
		return physical / window.getGuiScale();
	}

	public static int withAlpha(int color, int alpha) {
		return (alpha & 0xFF) << 24 | color & 0xFFFFFF;
	}

	public static int mulAlpha(int color, float factor) {
		int a = Math.round((color >>> 24) * Math.clamp(factor, 0f, 1f));
		return a << 24 | color & 0xFFFFFF;
	}

	public static int lerpColor(int a, int b, float t) {
		t = Math.clamp(t, 0f, 1f);
		int aa = a >>> 24;
		int ar = a >> 16 & 0xFF;
		int ag = a >> 8 & 0xFF;
		int ab = a & 0xFF;
		int ba = b >>> 24;
		int br = b >> 16 & 0xFF;
		int bg = b >> 8 & 0xFF;
		int bb = b & 0xFF;
		return Math.round(aa + (ba - aa) * t) << 24
			| Math.round(ar + (br - ar) * t) << 16
			| Math.round(ag + (bg - ag) * t) << 8
			| Math.round(ab + (bb - ab) * t);
	}

	private int c(int color) {
		return this.alpha >= 1f ? color : mulAlpha(color, this.alpha);
	}

	// ---- Rectangles ----

	public void rect(float x, float y, float w, float h, int color) {
		gradient(x, y, w, h, color, color, color, color);
	}

	public void gradientV(float x, float y, float w, float h, int top, int bottom) {
		gradient(x, y, w, h, top, top, bottom, bottom);
	}

	public void gradientH(float x, float y, float w, float h, int left, int right) {
		gradient(x, y, w, h, left, right, left, right);
	}

	private void gradient(float x, float y, float w, float h, int tl, int tr, int bl, int br) {
		if (w <= 0f || h <= 0f) {
			return;
		}
		add(new Quads(RenderPipelines.GUI, TextureSetup.noTexture(), new float[]{x, y, x + w, y + h}, null, new int[]{c(tl), c(tr), c(bl), c(br)}, pose(), scissor()));
	}

	public void round(float x, float y, float w, float h, float r, int color) {
		roundGradient(x, y, w, h, r, color, color, color, color);
	}

	public void roundV(float x, float y, float w, float h, float r, int top, int bottom) {
		roundGradient(x, y, w, h, r, top, top, bottom, bottom);
	}

	public void roundH(float x, float y, float w, float h, float r, int left, int right) {
		roundGradient(x, y, w, h, r, left, right, left, right);
	}

	public void roundGradient(float x, float y, float w, float h, float r, int tl, int tr, int bl, int br) {
		if (w <= 0f || h <= 0f) {
			return;
		}
		r = Math.min(r, Math.min(w, h) / 2f);
		if (r < 0.5f) {
			gradient(x, y, w, h, tl, tr, bl, br);
			return;
		}
		nineSlice(Textures.filled(snap(r)), x, y, w, h, r, true, tl, tr, bl, br);
	}

	public void roundOutline(float x, float y, float w, float h, float r, float thickness, int color) {
		if (w <= 0f || h <= 0f) {
			return;
		}
		r = Math.max(Math.min(r, Math.min(w, h) / 2f), thickness);
		nineSlice(Textures.stroke(snap(r), thickness), x, y, w, h, r, false, color, color, color, color);
	}

	/** A soft halo around a rounded rectangle, drawn underneath it. */
	public void glow(float x, float y, float w, float h, float r, float spread, int color) {
		float total = r + spread;
		Textures.Corner corner = Textures.glow(snap(r), spread);
		nineSlice(corner, x - spread, y - spread, w + spread * 2f, h + spread * 2f, total, true, color, color, color, color);
	}

	private static float snap(float r) {
		return Math.round(r * 2f) / 2f;
	}

	private void nineSlice(Textures.Corner corner, float x, float y, float w, float h, float r, boolean center, int tl, int tr, int bl, int br) {
		r = Math.min(r, Math.min(w, h) / 2f);
		float x1 = x + r;
		float x2 = x + w - r;
		float x3 = x + w;
		float y1 = y + r;
		float y2 = y + h - r;
		float y3 = y + h;
		float[] xs = {x, x1, x2, x3};
		float[] ys = {y, y1, y2, y3};
		// Texture coordinates per grid column: arc, solid, then the mirrored arc.
		float[] us = {0f, 1f, 1f, 0f};
		float[] colorX = {0f, r / w, 1f - r / w, 1f};
		float[] colorY = {0f, r / h, 1f - r / h, 1f};
		int count = center ? 9 : 8;
		float[] pos = new float[count * 4];
		float[] uv = new float[count * 4];
		int[] colors = new int[count * 4];
		int i = 0;
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 3; col++) {
				if (row == 1 && col == 1 && !center) {
					continue;
				}
				pos[i * 4] = xs[col];
				pos[i * 4 + 1] = ys[row];
				pos[i * 4 + 2] = xs[col + 1];
				pos[i * 4 + 3] = ys[row + 1];
				uv[i * 4] = us[col];
				uv[i * 4 + 1] = us[row];
				uv[i * 4 + 2] = us[col + 1];
				uv[i * 4 + 3] = us[row + 1];
				colors[i * 4] = c(bilinear(tl, tr, bl, br, colorX[col], colorY[row]));
				colors[i * 4 + 1] = c(bilinear(tl, tr, bl, br, colorX[col + 1], colorY[row]));
				colors[i * 4 + 2] = c(bilinear(tl, tr, bl, br, colorX[col], colorY[row + 1]));
				colors[i * 4 + 3] = c(bilinear(tl, tr, bl, br, colorX[col + 1], colorY[row + 1]));
				i++;
			}
		}
		add(new Quads(RenderPipelines.GUI_TEXTURED, corner.setup(), pos, uv, colors, pose(), scissor()));
	}

	private static int bilinear(int tl, int tr, int bl, int br, float u, float v) {
		if (tl == tr && bl == br && tl == bl) {
			return tl;
		}
		return lerpColor(lerpColor(tl, tr, u), lerpColor(bl, br, u), v);
	}

	// ---- Textures ----

	public void icon(String name, float x, float y, float size, int color) {
		texture(Textures.icon(name), x, y, size, size, color);
	}

	public void texture(Identifier id, float x, float y, float w, float h, int color) {
		textured(Textures.of(id), x, y, w, h, 0f, 0f, 1f, 1f, color);
	}

	public void textured(TextureSetup setup, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int color) {
		int col = c(color);
		add(new Quads(RenderPipelines.GUI_TEXTURED, setup, new float[]{x, y, x + w, y + h}, new float[]{u0, v0, u1, v1}, new int[]{col, col, col, col}, pose(), scissor()));
	}

	/**
	 * Draws a map image as a disc or square whose texture turns around its centre. {@code uvPerUnit} converts
	 * screen units to texture space, and {@code rotation} (radians) is how far the map is turned on screen.
	 */
	public void map(TextureSetup setup, float cx, float cy, float radius, boolean disc, float u, float v, float uvPerUnit, float rotation, int color) {
		float cos = (float) Math.cos(-rotation);
		float sin = (float) Math.sin(-rotation);
		int segments = disc ? 48 : 1;
		float[] mesh = new float[segments * 16];
		for (int i = 0; i < segments; i++) {
			float[][] corners;
			if (disc) {
				float a0 = i * 6.2831855f / segments;
				float a1 = (i + 1) * 6.2831855f / segments;
				float[] p0 = {(float) Math.cos(a0) * radius, (float) Math.sin(a0) * radius};
				float[] p1 = {(float) Math.cos(a1) * radius, (float) Math.sin(a1) * radius};
				corners = new float[][]{{0f, 0f}, p1, p1, p0};
			} else {
				corners = new float[][]{{-radius, -radius}, {-radius, radius}, {radius, radius}, {radius, -radius}};
			}
			for (int k = 0; k < 4; k++) {
				float ox = corners[k][0];
				float oy = corners[k][1];
				int base = (i * 4 + k) * 4;
				mesh[base] = cx + ox;
				mesh[base + 1] = cy + oy;
				mesh[base + 2] = u + (ox * cos - oy * sin) * uvPerUnit;
				mesh[base + 3] = v + (ox * sin + oy * cos) * uvPerUnit;
			}
		}
		add(new Mesh(setup, mesh, c(color), pose(), scissor()));
	}

	// ---- Text ----

	public static FormattedCharSequence seq(String text, boolean bold) {
		Map<String, FormattedCharSequence> cache = bold ? BOLD_TEXT : REGULAR_TEXT;
		if (cache.size() > 4000) {
			cache.clear();
		}
		return cache.computeIfAbsent(text, s -> Component.literal(s).withStyle(bold ? BOLD : REGULAR).getVisualOrderText());
	}

	public float width(String text) {
		return this.font.width(seq(text, false));
	}

	public float widthBold(String text) {
		return this.font.width(seq(text, true));
	}

	public void text(String text, float x, float y, int color) {
		text(text, x, y, 1f, color, false);
	}

	public void bold(String text, float x, float y, int color) {
		text(text, x, y, 1f, color, true);
	}

	public void text(String text, float x, float y, float scale, int color, boolean bold) {
		if (text == null || text.isEmpty()) {
			return;
		}
		int col = c(color);
		if ((col >>> 24) < 5) {
			return;
		}
		this.g.pose().pushMatrix();
		this.g.pose().translate(x, y);
		if (scale != 1f) {
			this.g.pose().scale(scale, scale);
		}
		this.g.text(this.font, seq(text, bold), 0, 0, col, false);
		this.g.pose().popMatrix();
	}

	public void centered(String text, float cx, float y, float scale, int color, boolean bold) {
		float w = (bold ? widthBold(text) : width(text)) * scale;
		text(text, cx - w / 2f, y, scale, color, bold);
	}

	public void right(String text, float rightX, float y, float scale, int color, boolean bold) {
		float w = (bold ? widthBold(text) : width(text)) * scale;
		text(text, rightX - w, y, scale, color, bold);
	}

	/** Shortens text with an ellipsis so it fits into {@code maxWidth}. */
	public String fit(String text, float maxWidth) {
		if (width(text) <= maxWidth) {
			return text;
		}
		String s = text;
		while (!s.isEmpty() && width(s + "...") > maxWidth) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "...";
	}

	// ---- Clipping and state ----

	public void clip(float x, float y, float w, float h) {
		this.g.enableScissor((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(x + w), (int) Math.ceil(y + h));
	}

	public void unclip() {
		this.g.disableScissor();
	}

	private Matrix3x2f pose() {
		return new Matrix3x2f(this.g.pose());
	}

	@Nullable
	private ScreenRectangle scissor() {
		return this.g.scissorStack.peek();
	}

	private void add(GuiElementRenderState element) {
		this.g.guiRenderState.addGuiElement(element);
	}

	/** Free-form textured quads, stored as x,y,u,v per vertex. */
	private record Mesh(TextureSetup textureSetup, float[] data, int color, Matrix3x2fc pose, @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements GuiElementRenderState {
		private Mesh(TextureSetup setup, float[] data, int color, Matrix3x2fc pose, @Nullable ScreenRectangle scissor) {
			this(setup, data, color, pose, scissor, boundsOf(data, pose, scissor));
		}

		@Nullable
		private static ScreenRectangle boundsOf(float[] data, Matrix3x2fc pose, @Nullable ScreenRectangle scissor) {
			float minX = Float.MAX_VALUE;
			float minY = Float.MAX_VALUE;
			float maxX = -Float.MAX_VALUE;
			float maxY = -Float.MAX_VALUE;
			for (int i = 0; i < data.length; i += 4) {
				minX = Math.min(minX, data[i]);
				minY = Math.min(minY, data[i + 1]);
				maxX = Math.max(maxX, data[i]);
				maxY = Math.max(maxY, data[i + 1]);
			}
			int x0 = (int) Math.floor(minX);
			int y0 = (int) Math.floor(minY);
			ScreenRectangle box = new ScreenRectangle(x0, y0, Math.max(1, (int) Math.ceil(maxX) - x0), Math.max(1, (int) Math.ceil(maxY) - y0)).transformMaxBounds(pose);
			return scissor != null ? scissor.intersection(box) : box;
		}

		@Override
		public void buildVertices(VertexConsumer consumer) {
			for (int i = 0; i < this.data.length; i += 4) {
				consumer.addVertexWith2DPose(this.pose, this.data[i], this.data[i + 1]).setUv(this.data[i + 2], this.data[i + 3]).setColor(this.color);
			}
		}

		@Override
		public RenderPipeline pipeline() {
			return RenderPipelines.GUI_TEXTURED;
		}
	}

	/**
	 * Any number of quads in one element. {@code pos} holds x0,y0,x1,y1 per quad, {@code uv} the same for
	 * texture coordinates (or null for untextured), {@code colors} top-left, top-right, bottom-left, bottom-right.
	 */
	private static final class Quads implements GuiElementRenderState {
		private final RenderPipeline pipeline;
		private final TextureSetup setup;
		private final float[] pos;
		private final float @Nullable [] uv;
		private final int[] colors;
		private final Matrix3x2fc pose;
		@Nullable
		private final ScreenRectangle scissor;
		@Nullable
		private final ScreenRectangle bounds;

		private Quads(RenderPipeline pipeline, TextureSetup setup, float[] pos, float @Nullable [] uv, int[] colors, Matrix3x2fc pose, @Nullable ScreenRectangle scissor) {
			this.pipeline = pipeline;
			this.setup = setup;
			this.pos = pos;
			this.uv = uv;
			this.colors = colors;
			this.pose = pose;
			this.scissor = scissor;
			float minX = Float.MAX_VALUE;
			float minY = Float.MAX_VALUE;
			float maxX = -Float.MAX_VALUE;
			float maxY = -Float.MAX_VALUE;
			for (int i = 0; i < pos.length; i += 4) {
				minX = Math.min(minX, pos[i]);
				minY = Math.min(minY, pos[i + 1]);
				maxX = Math.max(maxX, pos[i + 2]);
				maxY = Math.max(maxY, pos[i + 3]);
			}
			int x0 = (int) Math.floor(minX);
			int y0 = (int) Math.floor(minY);
			ScreenRectangle box = new ScreenRectangle(x0, y0, Math.max(1, (int) Math.ceil(maxX) - x0), Math.max(1, (int) Math.ceil(maxY) - y0)).transformMaxBounds(pose);
			this.bounds = scissor != null ? scissor.intersection(box) : box;
		}

		@Override
		public void buildVertices(VertexConsumer consumer) {
			int quads = this.pos.length / 4;
			for (int i = 0; i < quads; i++) {
				int p = i * 4;
				float x0 = this.pos[p];
				float y0 = this.pos[p + 1];
				float x1 = this.pos[p + 2];
				float y1 = this.pos[p + 3];
				if (this.uv != null) {
					float u0 = this.uv[p];
					float v0 = this.uv[p + 1];
					float u1 = this.uv[p + 2];
					float v1 = this.uv[p + 3];
					consumer.addVertexWith2DPose(this.pose, x0, y0).setUv(u0, v0).setColor(this.colors[p]);
					consumer.addVertexWith2DPose(this.pose, x0, y1).setUv(u0, v1).setColor(this.colors[p + 2]);
					consumer.addVertexWith2DPose(this.pose, x1, y1).setUv(u1, v1).setColor(this.colors[p + 3]);
					consumer.addVertexWith2DPose(this.pose, x1, y0).setUv(u1, v0).setColor(this.colors[p + 1]);
				} else {
					consumer.addVertexWith2DPose(this.pose, x0, y0).setColor(this.colors[p]);
					consumer.addVertexWith2DPose(this.pose, x0, y1).setColor(this.colors[p + 2]);
					consumer.addVertexWith2DPose(this.pose, x1, y1).setColor(this.colors[p + 3]);
					consumer.addVertexWith2DPose(this.pose, x1, y0).setColor(this.colors[p + 1]);
				}
			}
		}

		@Override
		public RenderPipeline pipeline() {
			return this.pipeline;
		}

		@Override
		public TextureSetup textureSetup() {
			return this.setup;
		}

		@Nullable
		@Override
		public ScreenRectangle scissorArea() {
			return this.scissor;
		}

		@Nullable
		@Override
		public ScreenRectangle bounds() {
			return this.bounds;
		}
	}
}
