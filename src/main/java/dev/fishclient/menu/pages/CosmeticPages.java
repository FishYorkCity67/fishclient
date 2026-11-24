package dev.fishclient.menu.pages;

import dev.fishclient.config.FishConfig;
import dev.fishclient.feature.Cosmetics;
import dev.fishclient.feature.FileDialogs;
import dev.fishclient.feature.SkinLibrary;
import dev.fishclient.menu.FishMenuScreen;
import dev.fishclient.menu.Ui;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.L;
import dev.fishclient.ui.Theme;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

public final class CosmeticPages {
	private static final String[] HATS = {"none", "top_hat", "fisher_hat", "crown", "fish_hat"};
	private static final int[] ACCENTS = {0xFF2FD6C3, 0xFF30C6FF, 0xFF307CFF, 0xFFA06CFF, 0xFFFF30B1, 0xFFF0565B, 0xFFFF8C40, 0xFFF2C14E, 0xFF3ADB8F, 0xFFEEF2FB, 0xFF3A3F4C};
	private static boolean importSlim;
	private static String confirmDelete = "";

	/** The real player model, which also shows the current skin, cape and hat live. */
	private static void preview(Ui ui, float x, float y, float w, float h) {
		ui.card(x, y, w, h);
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (ui.d.alpha < 0.5f) {
			// A 3D model cannot fade with the menu, so it leaves as soon as the fade is under way.
			return;
		}
		if (player != null) {
			// Entity previews ignore the pose, so their box is converted to plain GUI coordinates first.
			Matrix3x2f pose = new Matrix3x2f(ui.d.g.pose());
			Vector2f from = pose.transformPosition(x + 2f, y + 2f, new Vector2f());
			Vector2f to = pose.transformPosition(x + w - 2f, y + h - 2f, new Vector2f());
			int size = Math.round(h * 0.38f * pose.m00());
			float mouseX = (float) mc.mouseHandler.getScaledXPos(mc.getWindow());
			float mouseY = (float) mc.mouseHandler.getScaledYPos(mc.getWindow());
			ui.d.g.pose().pushMatrix();
			ui.d.g.pose().identity();
			InventoryScreen.extractEntityInInventoryFollowsMouse(
				ui.d.g, Math.round(from.x), Math.round(from.y), Math.round(to.x), Math.round(to.y), size, 0.0625f, mouseX, mouseY, player
			);
			ui.d.g.pose().popMatrix();
		} else {
			ui.d.centered(L.t("cosmetics.no_world"), x + w / 2f, y + h / 2f - 4f, 0.85f, Theme.faint(), false);
		}
	}

	public static float skins(Ui ui, float x, float y, float w) {
		Draw d = ui.d;
		float top = y;
		y = ui.heading(L.t("tab.skins"), x, y);
		y = ui.paragraph(L.t("skins.desc"), x, y, w) + 4f;

		float previewW = 90f;
		float previewH = 130f;
		float listW = w - previewW - 8f;
		preview(ui, x + listW + 8f, y, previewW, previewH);

		float[] next = new float[1];
		int slim = ui.pills("skin_arms", x, y, listW - 96f, new String[]{L.t("skins.classic"), L.t("skins.slim")}, importSlim ? 1 : 0, next);
		importSlim = slim == 1;
		if (ui.button("skin_add", x + listW - 92f, y - 1f, 92f, 17f, L.t("skins.add"), true)) {
			FileDialogs.open(L.t("skins.add"), "*.png", "PNG", path -> addSkin(ui, path));
		}
		enableDrop(path -> addSkin(ui, path));
		float ly = y + 22f;

		List<SkinLibrary.Skin> skins = SkinLibrary.all();
		FishConfig c = FishConfig.get();
		boolean anyActive = !c.localSkin.isEmpty();
		ui.card(x, ly, listW, 22f);
		d.text(L.t("skins.account"), x + 9f, ly + 7f, anyActive ? Theme.muted() : Theme.text());
		if (ui.toggleSwitch("skin_account", x + listW - 32f, ly + 4.5f, !anyActive) && anyActive) {
			SkinLibrary.select(null);
		}
		ly += 26f;
		if (skins.isEmpty()) {
			d.text(L.t("skins.empty"), x, ly + 2f, Theme.faint());
			ly += 16f;
		}
		SkinLibrary.Skin toDelete = null;
		int i = 0;
		for (SkinLibrary.Skin skin : skins) {
			String id = "skin" + i++;
			boolean on = skin.name.equals(c.localSkin);
			float h = 44f;
			ui.card(x, ly, listW, h);
			if (on) {
				d.roundOutline(x, ly, listW, h, Ui.radius(), 1f, Theme.accent());
			}
			d.text(d.fit(skin.name, listW - 96f), x + 9f, ly + 6f, Theme.text());
			d.text(skin.slim ? L.t("skins.slim") : L.t("skins.classic"), x + 9f, ly + 17f, 0.85f, Theme.muted(), false);
			if (ui.toggleSwitch(id + "_on", x + listW - 32f, ly + 5f, on)) {
				SkinLibrary.select(on ? null : skin);
			}
			float bx = x + 8f;
			float by = ly + 27f;
			if (ui.button(id + "_apply", bx, by, 70f, 14f, L.t("skins.apply"), false)) {
				ui.toast(L.t("skins.applying"));
				SkinLibrary.applyToAccount(skin, error -> ui.toast(error == null ? L.t("skins.applied") : L.f("skins.apply_failed", error)));
			}
			if (ui.button(id + "_arms", bx + 74f, by, 56f, 14f, skin.slim ? L.t("skins.to_classic") : L.t("skins.to_slim"), false)) {
				SkinLibrary.setSlim(skin, !skin.slim);
			}
			boolean confirming = confirmDelete.equals(skin.file);
			if (ui.button(id + "_del", x + listW - 62f, by, 54f, 14f, confirming ? L.t("common.confirm") : L.t("common.delete"), false)) {
				if (confirming) {
					toDelete = skin;
					confirmDelete = "";
				} else {
					confirmDelete = skin.file;
				}
			}
			ly += h + 4f;
		}
		if (toDelete != null) {
			SkinLibrary.delete(toDelete);
		}
		return Math.max(ly, y + previewH + 6f) - top;
	}

	private static void addSkin(Ui ui, Path path) {
		String error = SkinLibrary.importSkin(path, importSlim);
		ui.toast(error == null ? L.t("skins.added") : L.f("skins.add_failed", error));
	}

	private static void enableDrop(java.util.function.Consumer<Path> handler) {
		if (Minecraft.getInstance().screen instanceof FishMenuScreen menu) {
			menu.onFileDrop(files -> {
				if (!files.isEmpty()) {
					handler.accept(files.getFirst());
				}
			});
		}
	}

	public static float cape(Ui ui, float x, float y, float w) {
		FishConfig c = FishConfig.get();
		Draw d = ui.d;
		float top = y;
		y = ui.heading(L.t("tab.cape"), x, y);
		y = ui.paragraph(L.t("cape.desc"), x, y, w) + 4f;
		float previewW = 90f;
		float previewH = 130f;
		float listW = w - previewW - 8f;
		preview(ui, x + listW + 8f, y, previewW, previewH);

		float ly = ui.toggleRow("cape_on", x, y, listW, L.t("cape.show"), L.t("cape.show.desc"), c.cape, false, v -> c.cape = v);
		c.capePhysics = ui.slider("cape_phys", x, ly, listW, L.t("cape.physics"), Math.round(c.capePhysics * 100f) + "%", c.capePhysics, 0f, 2f, 0.05f);
		ly += 27f;
		if (ui.button("cape_add", x, ly, 110f, 18f, L.t("cape.upload"), true)) {
			FileDialogs.open(L.t("cape.upload"), "*.png", "PNG", path -> addCape(ui, path));
		}
		enableDrop(path -> addCape(ui, path));
		ly += 24f;

		boolean defaultOn = c.capeFile.isEmpty();
		if (capeRow(ui, "cape_def", x, ly, listW, L.t("cape.default"), defaultOn, false)) {
			c.capeFile = "";
			Ui.changed();
		}
		ly += 26f;
		String delete = null;
		int i = 0;
		for (String name : Cosmetics.capes()) {
			String id = "capef" + i++;
			if (capeRow(ui, id, x, ly, listW, name.replaceAll("\\.png$", ""), name.equals(c.capeFile), true)) {
				c.capeFile = name;
				Ui.changed();
			}
			if (ui.iconButton(id + "_del", x + listW - 21f, ly + 3f, 16f, "trash", L.t("common.delete"))) {
				delete = name;
			}
			ly += 26f;
		}
		if (delete != null) {
			Cosmetics.deleteCape(delete);
		}
		return Math.max(ly, y + previewH + 6f) - top;
	}

	private static boolean capeRow(Ui ui, String id, float x, float y, float w, String name, boolean on, boolean deletable) {
		ui.card(x, y, w, 22f);
		if (on) {
			ui.d.roundOutline(x, y, w, 22f, Ui.radius(), 1f, Theme.accent());
		}
		ui.d.text(ui.d.fit(name, w - 50f), x + 9f, y + 7f, on ? Theme.text() : Theme.muted());
		float room = deletable ? w - 24f : w;
		ui.note(id, x, y, room, 22f);
		return !on && ui.click(x, y, room, 22f);
	}

	private static void addCape(Ui ui, Path path) {
		String error = Cosmetics.importCape(path);
		ui.toast(error == null ? L.t("cape.added") : L.f("cape.add_failed", error));
	}

	public static float cosmetics(Ui ui, float x, float y, float w) {
		FishConfig c = FishConfig.get();
		float top = y;
		y = ui.heading(L.t("tab.cosmetics"), x, y);
		y = ui.paragraph(L.t("cosmetics.desc"), x, y, w) + 4f;
		float previewW = 90f;
		float previewH = 130f;
		float listW = w - previewW - 8f;
		preview(ui, x + listW + 8f, y, previewW, previewH);

		ui.d.text(L.t("cosmetics.hat"), x, y, Theme.text());
		float ly = y + 13f;
		String[] labels = new String[HATS.length];
		int selected = 0;
		for (int i = 0; i < HATS.length; i++) {
			labels[i] = L.t("cosmetics.hat." + HATS[i]);
			if (HATS[i].equals(c.hat)) {
				selected = i;
			}
		}
		float[] next = new float[1];
		int picked = ui.pills("hat", x, ly, listW, labels, selected, next);
		if (picked != selected) {
			c.hat = HATS[picked];
			Ui.changed();
		}
		ly = next[0];
		ly = ui.toggleRow("phones", x, ly, listW, L.t("cosmetics.headphones"), L.t("cosmetics.headphones.desc"), c.headphones, true, v -> c.headphones = v);
		ly = ui.toggleRow("phones_music", x, ly, listW, L.t("cosmetics.headphones_music"), L.t("cosmetics.headphones_music.desc"), c.headphonesOnlyWithMusic, true, v -> c.headphonesOnlyWithMusic = v);
		ui.d.text(L.t("cosmetics.accent"), x, ly, Theme.text());
		ly += 13f;
		float size = 19f;
		int columns = Math.max(1, (int) (listW / size));
		for (int row = 0; row * columns < ACCENTS.length; row++) {
			int from = row * columns;
			int to = Math.min(ACCENTS.length, from + columns);
			int[] slice = java.util.Arrays.copyOfRange(ACCENTS, from, to);
			int color = ui.swatches("accent" + row, x, ly, slice, c.accentColor);
			if (color != c.accentColor) {
				c.accentColor = color;
				Ui.changed();
			}
			ly += 20f;
		}
		return Math.max(ly + 4f, y + previewH + 6f) - top;
	}

	private CosmeticPages() {
	}
}
