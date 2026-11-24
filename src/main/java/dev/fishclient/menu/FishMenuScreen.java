package dev.fishclient.menu;

import dev.fishclient.FishClient;
import dev.fishclient.config.FishConfig;
import dev.fishclient.config.Profiles;
import dev.fishclient.feature.TitleBackground;
import dev.fishclient.menu.pages.AuctionPages;
import dev.fishclient.menu.pages.CosmeticPages;
import dev.fishclient.menu.pages.FishingPage;
import dev.fishclient.menu.pages.MapPages;
import dev.fishclient.menu.pages.MusicPage;
import dev.fishclient.menu.pages.PortalPage;
import dev.fishclient.menu.pages.ProfilePage;
import dev.fishclient.menu.pages.ScreenshotPage;
import dev.fishclient.menu.pages.SettingsPage;
import dev.fishclient.menu.pages.ThemePage;
import dev.fishclient.menu.pages.TransactionPage;
import dev.fishclient.menu.pages.UtilityPages;
import dev.fishclient.ui.Anim;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.Easing;
import dev.fishclient.ui.L;
import dev.fishclient.ui.Smooth;
import dev.fishclient.ui.Sounds;
import dev.fishclient.ui.Theme;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

/** The main menu: sidebar, greeting header, and the active module page on the right. */
public final class FishMenuScreen extends Screen {
	private record Tab(String key, String icon, Page page) {
	}

	private static final Identifier LOGO = FishClient.id("textures/gui/logo.png");
	private static final List<Tab> TABS = List.of(
		new Tab("auction", "auction", AuctionPages::auction),
		new Tab("recent", "clock", AuctionPages::recent),
		new Tab("transactions", "swap", TransactionPage::draw),
		new Tab("theme", "palette", ThemePage::draw),
		new Tab("skins", "person", CosmeticPages::skins),
		new Tab("cape", "cape", CosmeticPages::cape),
		new Tab("cosmetics", "hat", CosmeticPages::cosmetics),
		new Tab("music", "music", MusicPage::draw),
		new Tab("crosshair", "crosshair", UtilityPages::crosshair),
		new Tab("utilities", "sliders", UtilityPages::utilities),
		new Tab("scoreboard", "scoreboard", UtilityPages::scoreboard),
		new Tab("sky", "sky", UtilityPages::sky),
		new Tab("portals", "portal", PortalPage::draw),
		new Tab("hud", "keyboard", UtilityPages::hud),
		new Tab("waypoints", "pin", MapPages::waypoints),
		new Tab("minimap", "map", MapPages::minimap),
		new Tab("fishing", "fish", FishingPage::draw),
		new Tab("screenshots", "camera", ScreenshotPage::draw),
		new Tab("profiles", "layers", ProfilePage::draw),
		new Tab("settings", "gear", SettingsPage::draw)
	);
	// Twenty entries have to fit the sidebar without scrolling.
	private static final float ITEM_H = 17f;
	private static final float MENU_W = 690f;
	private static final float MENU_H = 440f;
	private static int lastTab = 3;

	private final Ui ui = new Ui();
	private final Anim open = new Anim(0f, 240, Easing.OUT_CUBIC);
	private final Anim scale = new Anim(0.955f, 300, Easing.OUT_BACK);
	private final Anim marker = new Anim(lastTab * ITEM_H, 260, Easing.OUT_CUBIC);
	private final Anim contentIn = new Anim(0f, 280, Easing.OUT_CUBIC);
	private final Smooth sidebarScroll = new Smooth(0d, 55d);
	private int tab = lastTab;
	private boolean closing;
	@Nullable
	private Consumer<List<Path>> fileDropHandler;
	@Nullable
	private final Screen previous;
	@Nullable
	private Supplier<PlayerSkin> ownSkin;

	public FishMenuScreen() {
		this(null);
	}

	public FishMenuScreen(@Nullable Screen previous) {
		super(Component.literal("FishClient"));
		this.previous = previous;
	}

	/** Blur strength while opening and closing, which the game reads through the options mixin. */
	public int blurNow() {
		return Math.round(FishConfig.get().blur * this.open.get());
	}

	/** Lets the current page accept files dragged onto the window. */
	public void onFileDrop(@Nullable Consumer<List<Path>> handler) {
		this.fileDropHandler = handler;
	}

	public static void openTab(String key) {
		for (int i = 0; i < TABS.size(); i++) {
			if (TABS.get(i).key().equals(key)) {
				lastTab = i;
			}
		}
	}

	@Override
	protected void init() {
		if (this.open.target() == 0f && !this.closing) {
			this.open.to(1f);
			this.scale.to(1f);
			this.contentIn.to(1f);
			Sounds.ui(Sounds.MENU_OPEN);
			Sounds.water(Sounds.WATER_OPEN);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		if (!this.closing) {
			this.closing = true;
			this.open.to(0f);
			Sounds.ui(Sounds.MENU_CLOSE);
			Profiles.save();
		}
	}

	@Override
	public void tick() {
		// Changing the screen in the middle of drawing is not safe, so the end of the fade-out is handled here.
		if (this.closing && this.open.done()) {
			this.minecraft.setScreen(this.previous);
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (this.minecraft.level == null) {
			TitleBackground.draw(graphics, this.width, this.height);
			// A new layer, so the bubbles count as "before" the blur.
			graphics.nextStratum();
		}
		if (FishConfig.get().frostedBlur && blurNow() >= 1) {
			graphics.blurBeforeThisStratum();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Draw d = new Draw(graphics);
		float alpha = this.open.get();
		d.alpha = alpha;
		float s = this.closing ? 0.97f + 0.03f * alpha : this.scale.get();

		FishConfig config = FishConfig.get();
		// Darkening keeps the panels readable over bright worlds.
		d.rect(0f, 0f, this.width, this.height, Draw.withAlpha(0xFF040812, config.seeThrough ? 0x60 : 0xB0));
		if (config.ambient) {
			drawAmbient(d);
		}

		// The menu is laid out in its own units, so it keeps its size at every GUI scale.
		float unit = Draw.unit();
		float viewW = this.width / unit;
		float viewH = this.height / unit;
		float mw = Math.min(viewW - 16f, MENU_W);
		float mh = Math.min(viewH - 16f, MENU_H);
		float mx = (viewW - mw) / 2f;
		float my = (viewH - mh) / 2f;
		float cx = this.width / 2f;
		float cy = this.height / 2f;

		// The whole panel scales from its centre; the pointer is mapped back so hits stay exact.
		float rawX = (float) this.minecraft.mouseHandler.getScaledXPos(this.minecraft.getWindow());
		float rawY = (float) this.minecraft.mouseHandler.getScaledYPos(this.minecraft.getWindow());
		float total = s * unit;
		float px = viewW / 2f + (rawX - cx) / total;
		float py = viewH / 2f + (rawY - cy) / total;
		this.ui.begin(d, px, py, !this.closing);

		graphics.pose().pushMatrix();
		graphics.pose().translate(cx, cy);
		graphics.pose().scale(total, total);
		graphics.pose().translate(-viewW / 2f, -viewH / 2f);

		d.glow(mx, my, mw, mh, Ui.radius() + 2f, 14f, Draw.withAlpha(Theme.accent3(), 0x30));
		d.round(mx, my, mw, mh, Ui.radius() + 2f, Theme.panel());
		d.roundOutline(mx, my, mw, mh, Ui.radius() + 2f, 1f, Theme.panelEdge());

		float gap = 6f;
		float sideW = Math.clamp(mw * 0.24f, 112f, 150f);
		drawSidebar(d, mx + gap, my + gap, sideW, mh - gap * 2f);

		float contentX = mx + gap * 2f + sideW;
		float contentW = mw - sideW - gap * 3f;
		float headerH = 28f;
		drawHeader(d, contentX, my + gap, contentW, headerH);

		float pageY = my + gap * 2f + headerH;
		float pageH = mh - gap * 3f - headerH;
		drawContent(d, contentX, pageY, contentW, pageH);

		this.ui.drawToast(contentX + contentW / 2f, my + mh - 26f);
		this.ui.drawTooltip();
		graphics.pose().popMatrix();
		this.ui.end();
	}

	private void drawAmbient(Draw d) {
		float t = Util.getMillis() / 1000f;
		float w = this.width;
		float h = this.height;
		float x1 = w * (0.22f + 0.05f * (float) Math.sin(t * 0.21f));
		float y1 = h * (0.25f + 0.06f * (float) Math.cos(t * 0.17f));
		float x2 = w * (0.78f + 0.05f * (float) Math.cos(t * 0.19f));
		float y2 = h * (0.78f + 0.05f * (float) Math.sin(t * 0.23f));
		float r = Math.min(w, h) * 0.5f;
		d.glow(x1, y1, 1f, 1f, 0.5f, r, Draw.withAlpha(Theme.accent(), 0x26));
		d.glow(x2, y2, 1f, 1f, 0.5f, r, Draw.withAlpha(Theme.accent3(), 0x26));
	}

	private void drawSidebar(Draw d, float x, float y, float w, float h) {
		d.round(x, y, w, h, Ui.radius(), Theme.card());
		d.roundOutline(x, y, w, h, Ui.radius(), 1f, Theme.line());

		d.texture(LOGO, x + 9f, y + 8f, 16f, 16f, 0xFFFFFFFF);
		d.bold("FISHCLIENT", x + 30f, y + 12f, Theme.text());

		d.text("MODULES", x + 10f, y + 32f, 0.75f, Theme.faint(), true);

		float profileH = 34f;
		float listY = y + 42f;
		float listH = h - 42f - profileH - 10f;
		float total = TABS.size() * ITEM_H;
		float wheel = this.ui.takeWheel(x, listY, w, listH);
		if (wheel != 0f) {
			this.sidebarScroll.target(this.sidebarScroll.target() - wheel * 24f);
		}
		float maxScroll = Math.max(0f, total - listH);
		this.sidebarScroll.target(Math.clamp(this.sidebarScroll.target(), 0d, maxScroll));
		float scroll = (float) this.sidebarScroll.get();

		this.ui.pushClip(x + 2f, listY, w - 4f, listH);
		float itemX = x + 6f;
		float itemW = w - 12f;
		float markY = listY - scroll + this.marker.get();
		d.round(itemX, markY, itemW, ITEM_H - 1f, Math.min(Ui.radius(), 7f), Draw.withAlpha(Theme.accent(), 0x24));
		d.round(itemX, markY + 4f, 2f, ITEM_H - 9f, 1f, Theme.accent());
		for (int i = 0; i < TABS.size(); i++) {
			Tab tabInfo = TABS.get(i);
			float iy = listY - scroll + i * ITEM_H;
			boolean over = this.ui.hovered(itemX, iy, itemW, ITEM_H - 1f);
			Anim hoverAnim = this.ui.anim("side:" + i, 0f, 130);
			hoverAnim.to(over ? 1f : 0f);
			float hv = hoverAnim.get();
			if (hv > 0.01f && i != this.tab) {
				d.round(itemX, iy, itemW, ITEM_H - 1f, Math.min(Ui.radius(), 7f), Draw.mulAlpha(Theme.cardHover(), hv));
			}
			Anim activeAnim = this.ui.anim("sideOn:" + i, i == this.tab ? 1f : 0f, 180);
			activeAnim.to(i == this.tab ? 1f : 0f);
			int color = Draw.lerpColor(Draw.lerpColor(Theme.muted(), Theme.text(), hv), Theme.text(), activeAnim.get());
			d.icon(tabInfo.icon(), itemX + 7f, iy + 4f, 9f, Draw.lerpColor(color, Theme.accent(), activeAnim.get()));
			String label = L.t("tab." + tabInfo.key());
			if (i == this.tab) {
				d.bold(d.fit(label, itemW - 26f), itemX + 21f, iy + 4.5f, color);
			} else {
				d.text(d.fit(label, itemW - 26f), itemX + 21f, iy + 4.5f, color);
			}
			this.ui.note("side:" + tabInfo.key(), itemX, iy, itemW, ITEM_H - 1f);
			if (this.ui.click(itemX, iy, itemW, ITEM_H - 1f) && i != this.tab) {
				selectTab(i);
			}
		}
		this.ui.popClip();

		drawProfileCard(d, x + 5f, y + h - profileH - 5f, w - 10f, profileH);
	}

	private void selectTab(int index) {
		this.tab = index;
		lastTab = index;
		this.marker.to(index * ITEM_H);
		this.contentIn.snap(0f);
		this.contentIn.to(1f);
		this.fileDropHandler = null;
		this.ui.resetScroll("page");
		Sounds.ui(Sounds.CLICK);
	}

	private void drawProfileCard(Draw d, float x, float y, float w, float h) {
		d.round(x, y, w, h, Math.min(Ui.radius(), 8f), Theme.cardHover());
		Minecraft mc = this.minecraft;
		float face = 22f;
		float fy = y + (h - face) / 2f;
		if (this.ownSkin == null) {
			this.ownSkin = mc.getSkinManager().createLookup(mc.getGameProfile(), false);
		}
		// On the title screen there is no player yet, so the account's skin is looked up directly.
		PlayerSkin skin = mc.player != null ? mc.player.getSkin() : this.ownSkin.get();
		PlayerFaceExtractor.extractRenderState(d.g, skin, Math.round(x + 6f), Math.round(fy), Math.round(face), Draw.mulAlpha(0xFFFFFFFF, d.alpha));
		String name = mc.getUser().getName();
		float textX = x + 6f + face + 6f;
		float room = w - (textX - x) - 12f;
		d.bold(d.fit(name, room), textX, y + 7f, Theme.text());
		d.text(d.fit(FishConfig.get().rank, room / 0.85f), textX, y + 19f, 0.85f, Theme.muted(), false);
		d.round(x + w - 10f, y + h / 2f - 2.5f, 5f, 5f, 2.5f, Theme.GOOD);
	}

	private void drawHeader(Draw d, float x, float y, float w, float h) {
		d.round(x, y, w, h, Ui.radius(), Theme.card());
		d.roundOutline(x, y, w, h, Ui.radius(), 1f, Theme.line());
		String hello = L.t("menu.hello");
		float tx = x + 11f;
		float ty = y + (h - 8f) / 2f;
		d.text(hello, tx, ty, Theme.muted());
		d.bold(this.minecraft.getUser().getName(), tx + d.width(hello) + 3f, ty, Theme.text());
		if (this.ui.iconButton("menu_close", x + w - 23f, y + 5f, 18f, "close", L.t("menu.close"))) {
			onClose();
		}
		float layoutW = this.ui.buttonWidth(L.t("menu.edit_hud"));
		if (this.ui.button("menu_edit_hud", x + w - 28f - layoutW, y + 5f, layoutW, 18f, L.t("menu.edit_hud"), false)) {
			this.minecraft.setScreen(new HudEditScreen(this));
		}
	}

	private void drawContent(Draw d, float x, float y, float w, float h) {
		d.round(x, y, w, h, Ui.radius(), Theme.card());
		d.roundOutline(x, y, w, h, Ui.radius(), 1f, Theme.line());
		float inner = 10f;
		float previous = d.alpha;
		float appear = this.contentIn.get();
		d.alpha = previous * appear;
		float slide = (1f - appear) * 7f;
		float top = this.ui.beginScroll("page", x + 1f, y + 1f, w - 2f, h - 2f);
		float height = TABS.get(this.tab).page().draw(this.ui, x + inner, top + inner + slide, w - inner * 2f - 3f);
		this.ui.endScroll("page", x + 1f, y + 1f, w - 2f, h - 2f, height + inner * 2f);
		d.alpha = previous;
	}

	// ---- Events ----

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		this.ui.onClick(event.button());
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		this.ui.onRelease(event.button());
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		this.ui.onWheel((float) scrollY);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.ui.onKey(event)) {
			return true;
		}
		if (dev.fishclient.FishKeys.MENU.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		this.ui.onChar(event.codepoint());
		return true;
	}

	@Override
	public void onFilesDrop(List<Path> files) {
		if (this.fileDropHandler != null) {
			this.fileDropHandler.accept(files);
		}
	}
}
