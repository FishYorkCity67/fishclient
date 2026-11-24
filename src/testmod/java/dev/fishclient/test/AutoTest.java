package dev.fishclient.test;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import dev.fishclient.FishKeys;
import dev.fishclient.config.FishConfig;
import dev.fishclient.config.Profiles;
import dev.fishclient.feature.AuctionWatcher;
import dev.fishclient.feature.Auctions;
import dev.fishclient.feature.ChatWatcher;
import dev.fishclient.feature.Cosmetics;
import dev.fishclient.feature.CustomSky;
import dev.fishclient.feature.FishingTracker;
import dev.fishclient.feature.LiveAuction;
import dev.fishclient.feature.Lyrics;
import dev.fishclient.feature.Freelook;
import dev.fishclient.feature.Minimap;
import dev.fishclient.feature.Money;
import dev.fishclient.feature.MusicService;
import dev.fishclient.feature.PortalCapture;
import dev.fishclient.feature.PortalRenderer;
import dev.fishclient.feature.PortalTransition;
import dev.fishclient.feature.PortalViews;
import dev.fishclient.feature.Screenshots;
import dev.fishclient.feature.ServerRules;
import dev.fishclient.feature.SkinLibrary;
import dev.fishclient.feature.Transactions;
import dev.fishclient.feature.Waypoints;
import dev.fishclient.feature.Zoom;
import dev.fishclient.hud.FishHud;
import dev.fishclient.hud.HudElement;
import dev.fishclient.menu.FishMenuScreen;
import dev.fishclient.menu.HudEditScreen;
import dev.fishclient.menu.Ui;
import dev.fishclient.mixin.FishingHookAccessor;
import dev.fishclient.test.mixin.KeyboardHandlerTestAccess;
import dev.fishclient.ui.Theme;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * Walks through FishClient in the real game: it opens every page, clicks real widgets through the pointer path, and
 * checks that each feature changes what it should. It writes run/autotest-report.txt and pictures to
 * run/screenshots/autotest. It never touches the account service, the clipboard or the network.
 */
public final class AutoTest implements ClientModInitializer {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String WORLD = "fishtest";

	private interface Action {
		void run() throws Exception;
	}

	private final Deque<Action> steps = new ArrayDeque<>();
	private final List<String> report = new ArrayList<>();
	private final Map<String, float[]> widgets = new HashMap<>();
	private final Map<String, Double> measured = new HashMap<>();
	private final Map<String, Double> saved = new HashMap<>();
	private int waitTicks;
	private BooleanSupplier waitingFor;
	private String waitingName = "";
	private int waitingTimeout;
	private int passed;
	private int failed;
	private boolean finished;
	private int shotIndex;
	private final List<String> commands = new ArrayList<>();
	private final List<Long> commandTimes = new ArrayList<>();

	private static Minecraft mc() {
		return Minecraft.getInstance();
	}

	private static MouseAccess mouse() {
		return (MouseAccess) mc().mouseHandler;
	}

	@Override
	public void onInitializeClient() {
		Ui.widgetListener = (id, x, y) -> this.widgets.put(id, new float[]{x, y});
		ClientSendMessageEvents.COMMAND.register(command -> {
			this.commands.add(command);
			this.commandTimes.add(System.currentTimeMillis());
		});
		plan();
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
	}

	private void tick() {
		if (this.finished) {
			return;
		}
		if (this.waitingFor != null) {
			boolean done;
			try {
				done = this.waitingFor.getAsBoolean();
			} catch (RuntimeException e) {
				done = false;
			}
			if (done) {
				pass("reached: " + this.waitingName);
				this.waitingFor = null;
			} else if (--this.waitingTimeout <= 0) {
				fail("timed out waiting for: " + this.waitingName);
				this.waitingFor = null;
			}
			return;
		}
		if (this.waitTicks > 0) {
			this.waitTicks--;
			return;
		}
		Action next = this.steps.pollFirst();
		if (next == null) {
			finish();
			return;
		}
		try {
			next.run();
		} catch (Throwable t) {
			fail("exception in step: " + t);
			LOGGER.error("AutoTest step failed", t);
		}
	}

	private void pass(String text) {
		this.passed++;
		this.report.add("PASS  " + text);
		LOGGER.info("[AutoTest] PASS {}", text);
	}

	private void fail(String text) {
		this.failed++;
		this.report.add("FAIL  " + text);
		LOGGER.warn("[AutoTest] FAIL {}", text);
	}

	private void note(String text) {
		this.report.add("NOTE  " + text);
		LOGGER.info("[AutoTest] {}", text);
	}

	private void finish() {
		this.finished = true;
		this.report.add("");
		this.report.add("RESULT passed=" + this.passed + " failed=" + this.failed);
		try {
			Files.write(mc().gameDirectory.toPath().resolve("autotest-report.txt"), this.report, StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.error("Could not write the report", e);
		}
		LOGGER.info("[AutoTest] RESULT passed={} failed={}", this.passed, this.failed);
		mc().stop();
	}

	// ---- Plan building blocks ----

	private void step(Action action) {
		this.steps.addLast(action);
	}

	private void pause(int ticks) {
		step(() -> this.waitTicks = ticks);
	}

	private void until(String name, BooleanSupplier condition, int timeoutTicks) {
		step(() -> {
			this.waitingFor = condition;
			this.waitingName = name;
			this.waitingTimeout = timeoutTicks;
		});
	}

	private void check(String name, BooleanSupplier condition) {
		step(() -> {
			if (condition.getAsBoolean()) {
				pass(name);
			} else {
				fail(name);
			}
		});
	}

	private void shot(String name) {
		step(() -> {
			File dir = new File(mc().gameDirectory, "screenshots/autotest");
			dir.mkdirs();
			String file = String.format(Locale.ROOT, "autotest/%02d_%s.png", this.shotIndex++, name);
			Screenshot.grab(mc().gameDirectory, file, mc().getMainRenderTarget(), 1, message -> {
			});
		});
		pause(2);
	}

	/** Stores the screen's average brightness (0 to 255) under {@code key}. */
	private void measure(String key) {
		step(() -> {
			this.measured.remove(key);
			Screenshot.takeScreenshot(mc().getMainRenderTarget(), image -> {
				this.measured.put(key, luminance(image));
				image.close();
			});
		});
		until("measured " + key, () -> this.measured.containsKey(key), 100);
	}

	private static double luminance(NativeImage image) {
		double sum = 0d;
		int count = 0;
		for (int y = 0; y < image.getHeight(); y += 8) {
			for (int x = 0; x < image.getWidth(); x += 8) {
				int c = image.getPixel(x, y);
				sum += 0.3d * (c >> 16 & 0xFF) + 0.59d * (c >> 8 & 0xFF) + 0.11d * (c & 0xFF);
				count++;
			}
		}
		return count > 0 ? sum / count : 0d;
	}

	/** Average brightness of a horizontal band, given as fractions of the picture's height. */
	private static double bandLuminance(NativeImage image, float from, float to) {
		double sum = 0d;
		int count = 0;
		for (int y = (int) (image.getHeight() * from); y < image.getHeight() * to; y += 4) {
			// The middle third: the HUD sits at the sides.
			for (int x = image.getWidth() / 3; x < image.getWidth() * 2 / 3; x += 8) {
				int c = image.getPixel(x, y);
				sum += 0.3d * (c >> 16 & 0xFF) + 0.59d * (c >> 8 & 0xFF) + 0.11d * (c & 0xFF);
				count++;
			}
		}
		return count > 0 ? sum / count : 0d;
	}

	private void command(String command) {
		step(() -> mc().player.connection.sendCommand(command));
		pause(3);
	}

	private void openMenu(String tab) {
		step(() -> {
			this.widgets.clear();
			FishMenuScreen.openTab(tab);
			mc().setScreen(new FishMenuScreen());
		});
		pause(14);
	}

	private void closeMenu() {
		step(() -> {
			if (mc().screen != null) {
				mc().screen.onClose();
			}
		});
		until("menu closed", () -> mc().screen == null, 60);
	}

	/** A real click: the pointer moves to the widget, then the button goes down and up through the screen's events. */
	private void click(String widget) {
		click(() -> widget);
	}

	private void click(Supplier<String> id) {
		step(() -> {
			String widget = id.get();
			float[] p = this.widgets.get(widget);
			if (p == null) {
				fail("no such widget on screen: " + widget);
				return;
			}
			var window = mc().getWindow();
			mouse().test$pointer(p[0] * window.getScreenWidth() / window.getGuiScaledWidth(), p[1] * window.getScreenHeight() / window.getGuiScaledHeight());
		});
		pause(2);
		step(() -> {
			float[] p = this.widgets.get(id.get());
			if (p == null || mc().screen == null) {
				return;
			}
			MouseButtonEvent event = new MouseButtonEvent(p[0], p[1], new MouseButtonInfo(0, 0));
			mc().screen.mouseClicked(event, false);
			mc().screen.mouseReleased(event);
		});
		pause(3);
	}

	private void type(String text) {
		step(() -> text.codePoints().forEach(cp -> mc().screen.charTyped(new CharacterEvent(cp))));
		pause(2);
	}

	private void key(int glfwKey) {
		step(() -> mc().screen.keyPressed(new KeyEvent(glfwKey, 0, 0)));
		pause(2);
	}

	private static FishConfig cfg() {
		return FishConfig.get();
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 0.01d;
	}

	// ---- The plan ----

	private void plan() {
		until("title screen", () -> mc().screen instanceof TitleScreen && mc().getOverlay() == null, 1800);
		step(() -> {
			mc().options.pauseOnLostFocus = false;
			cfg().screenshotPreview = false;
		});
		pause(50);
		shot("title_screen");
		check("default theme is Deep Ocean", () -> Theme.current().id().equals("deep_ocean"));

		// The menu must open from the title screen with the real key path.
		step(() -> ((KeyboardHandlerTestAccess) mc().keyboardHandler).test$keyPress(mc().getWindow().handle(), 1, new KeyEvent(GLFW.GLFW_KEY_RIGHT_SHIFT, 0, 0)));
		pause(14);
		check("Right Shift opens the menu on the title screen", () -> mc().screen instanceof FishMenuScreen);
		shot("menu_on_title_screen");
		closeMenuTo(TitleScreen.class);

		planWorld();
		planLogic();
		planChat();
		planMenuPages();
		planMenuClicks();
		planHud();
		planCamera();
		planFullbright();
		planSky();
		planCosmetics();
		planScoreboard();
		planMaps();
		planFishing();
		planScreenshots();
		planProfiles();
		planMusic();
		planAuctions();
		planPortals();
		planGuiScale();
	}

	private void closeMenuTo(Class<? extends Screen> type) {
		step(() -> mc().screen.onClose());
		until("menu closed back to " + type.getSimpleName(), () -> type.isInstance(mc().screen), 60);
	}

	private void planWorld() {
		step(() -> {
			if (new File(mc().gameDirectory, "saves/" + WORLD).isDirectory()) {
				mc().createWorldOpenFlows().openWorld(WORLD, () -> fail("the test world did not open"));
			} else {
				LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
				mc().createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(424242L, true, false), WorldPresets::createNormalWorldDimensions, mc().screen);
			}
		});
		until("in a world", () -> mc().player != null && mc().level != null && (mc().screen == null || mc().screen instanceof net.minecraft.client.gui.screens.DeathScreen), 3600);
		// A world saved after a failed run can start on the death screen.
		step(() -> {
			if (mc().screen instanceof net.minecraft.client.gui.screens.DeathScreen) {
				note("the test world started on the death screen, respawning");
				mc().player.respawn();
				mc().setScreen(null);
			}
		});
		until("alive in the world", () -> mc().player != null && mc().player.isAlive() && mc().screen == null, 400);
		pause(60);
		command("gamerule advance_time false");
		command("gamerule advance_weather false");
		command("weather clear");
		command("time set 6000");
		pause(20);
		shot("world_with_default_hud");
	}

	/** Pure logic that needs no rendering. */
	private void planLogic() {
		check("Money parses 1.5M", () -> near(Money.parse("1.5M"), 1.5e6));
		check("Money parses 250,000", () -> near(Money.parse("250,000"), 250000d));
		check("Money parses 1,5k as a decimal comma", () -> near(Money.parse("1,5k"), 1500d));
		check("Money parses $2.25b", () -> near(Money.parse("$2.25b"), 2.25e9));
		check("Money parses 1.500.000", () -> near(Money.parse("1.500.000"), 1.5e6));
		check("Money formats 1.5M", () -> Money.format(1.5e6).equals("$1.5M"));
		check("Money formats a negative value", () -> Money.signed(-2500d).equals("-$2.5K"));
		check("all three servers have rules", () -> cfg().servers.size() == 3 && cfg().servers.get(2).name.equals("FishSMP"));
		check("synced lyrics are read in time order", () -> {
			List<Lyrics.Line> lrc = Lyrics.parseLrc("[00:03.00][00:09.00]Zweite Probezeile" + "\n" + "[00:01.50]Erste Probezeile" + "\n" + "[00:05.25]");
			return lrc.size() == 4 && lrc.get(0).timeMs() == 1500L && lrc.get(3).timeMs() == 9000L && lrc.get(2).text().isEmpty()
				&& Lyrics.indexAt(lrc, 3100L) == 1 && Lyrics.indexAt(lrc, 100L) == -1 && Lyrics.indexAt(lrc, 60000L) == 3;
		});
		step(() -> {
			var rules = ServerRules.current();
			expectMatch(rules, "Fishy_Bob paid you $1.5M.", "received", "Fishy_Bob", 1.5e6, null);
			expectMatch(rules, "You received 200k from Alice", "received", "Alice", 200000d, null);
			expectMatch(rules, "You paid Alice $200K.", "sent", "Alice", 200000d, null);
			expectMatch(rules, "Bob bought your Diamond Sword for $3M", "sold", "Bob", 3e6, "Diamond Sword");
			expectMatch(rules, "You bought Diamond Sword from Bob for $1M", "bought", "Bob", 1e6, "Diamond Sword");
			if (ChatWatcher.parse("<Bob> Alice paid you $1M", rules) == null) {
				pass("a chat message quoting a payment is not a payment");
			} else {
				fail("a chat message quoting a payment is not a payment");
			}
			if (ChatWatcher.parse("§aBob §fpaid you §e$5K", rules) != null) {
				pass("colour codes are ignored when matching");
			} else {
				fail("colour codes are ignored when matching");
			}
		});
	}

	private void expectMatch(FishConfig.ServerPatterns rules, String line, String kind, String player, double amount, String item) {
		ChatWatcher.Match m = ChatWatcher.parse(line, rules);
		boolean ok = m != null && m.kind().equals(kind) && near(m.amount(), amount) && (player.equals(m.player())) && (item == null || item.equals(m.item()));
		if (ok) {
			pass("recognized: " + line);
		} else {
			fail("recognized: " + line + " -> " + m);
		}
	}

	/** The real path: system messages from the server end up in the totals, normal player chat does not. */
	private void planChat() {
		step(() -> {
			Transactions.clearHistory();
			Auctions.clearSales();
		});
		command("tellraw @s {\"text\":\"Fishy_Bob paid you $1.5M.\",\"color\":\"green\"}");
		command("tellraw @s \"Alice_01 paid you $200K.\"");
		command("tellraw @s \"Fishy_Bob paid you $500K.\"");
		command("tellraw @s \"You paid Carla $300K.\"");
		command("tellraw @s \"You bought Diamond Sword from Dan for $1M\"");
		command("tellraw @s \"Eve bought your Diamond Sword for $3M\"");
		pause(10);
		check("received total from the chat", () -> near(Transactions.sessionReceived(), 2.2e6));
		check("sent total from the chat", () -> near(Transactions.sessionSent(), 3e5));
		check("session profit", () -> near(Transactions.sessionProfit(), 1.9e6));
		check("top payer is the one who paid most", () -> !Transactions.topPayers(true).isEmpty() && Transactions.topPayers(true).getFirst().name().equals("Fishy_Bob") && near(Transactions.topPayers(true).getFirst().total(), 2e6));
		check("flip recognized with its profit", () -> !Auctions.flips().isEmpty() && near(Auctions.flips().getFirst().profit(), 2e6));
		step(() -> this.saved.put("rx", Transactions.sessionReceived()));
		step(() -> mc().player.connection.sendChat("Mallory paid you $9M."));
		pause(10);
		check("player chat cannot fake a payment", () -> near(Transactions.sessionReceived(), this.saved.get("rx")));
		step(() -> Transactions.saveIfDirty());
		check("history is saved to disk", () -> Files.exists(dev.fishclient.config.Json.file("transactions.json")));
		pause(30);
		shot("hud_counters_after_payments");
	}

	private void planMenuPages() {
		step(() -> {
			Waypoints.addHere("Basis");
			Auctions.addWatch("Elytra", 2.5e6);
		});
		for (String tab : List.of("auction", "recent", "transactions", "theme", "skins", "cape", "cosmetics", "music", "crosshair", "utilities", "scoreboard", "sky", "portals", "hud", "waypoints", "minimap", "fishing", "screenshots", "profiles", "settings")) {
			openMenu(tab);
			check("page opens: " + tab, () -> mc().screen instanceof FishMenuScreen);
			pause(6);
			shot("menu_" + tab);
			closeMenu();
		}
	}

	private void planMenuClicks() {
		openMenu("theme");
		// Early frames of the opening animation and of the theme blend, for checking by eye.
		click("theme:plasma");
		check("clicking a theme card selects it", () -> cfg().theme.equals("plasma") && Theme.current().id().equals("plasma"));
		shot("theme_blend_midway");
		pause(12);
		shot("theme_plasma_done");
		click("theme:deep_ocean");
		check("switching back works", () -> cfg().theme.equals("deep_ocean"));
		pause(10);

		click("frosted");
		check("clicking a switch turns it off", () -> !cfg().frostedBlur);
		pause(6);
		shot("frosted_blur_off");
		click("reset:frosted");
		check("the reset arrow restores the default", () -> cfg().frostedBlur);
		click("see_through");
		check("See-Through switch", () -> !cfg().seeThrough);
		pause(6);
		shot("see_through_off");
		click("see_through");
		click("ambient");
		check("Ambient switch", () -> !cfg().ambient);
		click("ambient");

		step(() -> this.saved.put("radius", (double) cfg().radius));
		click("radius");
		check("clicking a slider moves it", () -> !near(cfg().radius, this.saved.get("radius")));
		// A press and release in one frame must not leave the slider following the pointer.
		step(() -> {
			this.saved.put("radius", (double) cfg().radius);
			float[] p = this.widgets.get("side:theme");
			var window = mc().getWindow();
			mouse().test$pointer(p[0] * window.getScreenWidth() / window.getGuiScaledWidth(), p[1] * window.getScreenHeight() / window.getGuiScaledHeight());
		});
		pause(3);
		check("the slider lets go after the click", () -> near(cfg().radius, this.saved.get("radius")));
		step(() -> cfg().radius = 10f);

		// Tab change with a real click on the sidebar.
		click("side:transactions");
		pause(1);
		shot("tab_change_midway");
		pause(10);
		click("min_filter:2");
		check("pill filter selects 1m+", () -> cfg().minAmountFilter == 2);
		check("the 1m+ filter hides small payments", () -> Transactions.filtered().stream().allMatch(e -> e.amount >= 1e6));
		click("min_filter:0");
		click("player_filter");
		type("Alice");
		check("typing into a field", () -> cfg().playerFilter.equals("Alice"));
		check("the player filter narrows the list", () -> !Transactions.filtered().isEmpty() && Transactions.filtered().stream().allMatch(e -> e.player.contains("Alice")));
		shot("transactions_filtered");
		key(GLFW.GLFW_KEY_BACKSPACE);
		check("backspace in a field", () -> cfg().playerFilter.equals("Alic"));
		key(GLFW.GLFW_KEY_ESCAPE);
		check("Esc in a field only leaves the field", () -> mc().screen instanceof FishMenuScreen);
		step(() -> cfg().playerFilter = "");

		click("side:settings");
		pause(8);
		click("menu_key");
		step(() -> mc().screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_F9, 0, 0)));
		pause(2);
		check("the menu key can be rebound", () -> FishKeys.MENU.saveString().contains("f9"));
		check("rebinding did not close the menu", () -> mc().screen instanceof FishMenuScreen);
		click("menu_key");
		step(() -> mc().screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_RIGHT_SHIFT, 0, 0)));
		pause(2);
		check("the menu key is back on Right Shift", () -> FishKeys.MENU.saveString().contains("right.shift"));
		click("pattern_test");
		type("Fishy_Bob paid you $1.5M.");
		pause(2);
		shot("pattern_tester");

		// Closing by key plays the fade-out, then the screen is gone.
		step(() -> mc().screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0)));
		pause(1);
		shot("menu_fading_out");
		until("Esc fades the menu out", () -> mc().screen == null, 60);

		// Scrolling with easing.
		openMenu("utilities");
		step(() -> mc().screen.mouseScrolled(mc().getWindow().getGuiScaledWidth() * 0.6d, mc().getWindow().getGuiScaledHeight() * 0.5d, 0d, -8d));
		pause(10);
		shot("utilities_scrolled");
		closeMenu();
	}

	private void planHud() {
		step(() -> {
			for (HudElement element : FishHud.ELEMENTS) {
				element.pos().enabled = true;
			}
		});
		pause(10);
		shot("all_hud_elements");
		step(() -> mc().setScreen(new HudEditScreen(new FishMenuScreen())));
		pause(4);
		shot("hud_editor");
		step(() -> {
			HudElement element = FishHud.PROFIT;
			var d = new dev.fishclient.ui.Draw(new net.minecraft.client.gui.GuiGraphicsExtractor(mc(), mc().gameRenderer.getGameRenderState().guiRenderState, 0, 0));
			float x = element.screenX(d) + 8f;
			float y = element.screenY(d) + 8f;
			this.saved.put("profitX", (double) element.pos().x);
			Screen screen = mc().screen;
			MouseButtonEvent press = new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0));
			screen.mouseClicked(press, false);
			MouseButtonEvent drag = new MouseButtonEvent(x + 150f, y + 40f, new MouseButtonInfo(0, 0));
			screen.mouseDragged(drag, 150d, 40d);
			screen.mouseReleased(drag);
			this.saved.put("dropX", (double) x + 150d);
			this.saved.put("dropY", (double) y + 40d);
		});
		pause(3);
		// The editor measures its boxes while drawing, so the wheel comes after a new frame.
		step(() -> mc().screen.mouseScrolled(this.saved.get("dropX"), this.saved.get("dropY"), 0d, 2d));
		pause(2);
		check("dragging moves a HUD element", () -> !near(FishHud.PROFIT.pos().x, this.saved.get("profitX")));
		check("scrolling scales a HUD element", () -> FishHud.PROFIT.pos().scale > 1.05f);
		shot("hud_element_moved");
		step(() -> {
			FishHud.PROFIT.resetPosition();
			mc().setScreen(null);
			for (HudElement element : FishHud.ELEMENTS) {
				element.pos().enabled = !List.of("keystrokes", "cps", "armor", "minimap").contains(element.id);
			}
		});
		pause(3);
	}

	private void planCamera() {
		step(() -> {
			mc().player.setYRot(30f);
			mc().player.setXRot(5f);
			this.saved.put("yaw", (double) mc().player.getYRot());
		});
		pause(2);
		step(() -> FishKeys.FREELOOK.setDown(true));
		pause(3);
		check("the key starts freelook", Freelook::active);
		check("freelook moves to third person", () -> mc().options.getCameraType() == CameraType.THIRD_PERSON_BACK);
		step(() -> {
			mouse().test$delta(1200d, 200d);
			mouse().test$turn();
		});
		pause(3);
		check("the player does not turn during freelook", () -> near(mc().player.getYRot(), this.saved.get("yaw")));
		check("the camera turns during freelook", () -> Math.abs(mc().gameRenderer.getMainCamera().yRot() - mc().player.getYRot()) > 5f);
		shot("freelook");
		step(() -> FishKeys.FREELOOK.setDown(false));
		pause(3);
		check("releasing the key ends freelook", () -> !Freelook.active());
		check("the camera type is restored", () -> mc().options.getCameraType() == CameraType.FIRST_PERSON);
		check("the camera is back on the player", () -> Math.abs(mc().gameRenderer.getMainCamera().yRot() - mc().player.getYRot()) < 1f);
		step(() -> {
			cfg().freelookToggle = true;
			FishKeys.FREELOOK.setDown(true);
			net.minecraft.client.KeyMapping.click(FishKeys.FREELOOK.getDefaultKey());
			FishKeys.FREELOOK.setDown(false);
		});
		pause(3);
		check("toggle mode stays on after releasing the key", Freelook::active);
		step(() -> net.minecraft.client.KeyMapping.click(FishKeys.FREELOOK.getDefaultKey()));
		pause(3);
		check("a second press toggles freelook off", () -> !Freelook.active());
		step(() -> cfg().freelookToggle = false);

		// Normal turning with and without zoom.
		step(() -> {
			double before = mc().player.getYRot();
			mouse().test$delta(200d, 0d);
			mouse().test$turn();
			this.saved.put("normalTurn", Math.abs(mc().player.getYRot() - before));
		});
		check("the mouse turns the player normally", () -> this.saved.get("normalTurn") > 0.5d);
		step(() -> this.saved.put("fov", (double) mc().gameRenderer.getMainCamera().getFov()));
		step(() -> FishKeys.ZOOM.setDown(true));
		pause(1);
		shot("zoom_midway");
		pause(12);
		check("zoom narrows the field of view", () -> mc().gameRenderer.getMainCamera().getFov() < this.saved.get("fov") / 2d);
		shot("zoomed_in");
		step(() -> {
			double before = mc().player.getYRot();
			mouse().test$delta(200d, 0d);
			mouse().test$turn();
			this.saved.put("zoomTurn", Math.abs(mc().player.getYRot() - before));
		});
		check("the mouse is slower while zoomed", () -> this.saved.get("zoomTurn") < this.saved.get("normalTurn") * 0.8d);
		step(() -> {
			this.saved.put("zoomLevel", (double) cfg().zoomLevel);
			this.saved.put("slot", (double) mc().player.getInventory().getSelectedSlot());
			mouse().test$scroll(mc().getWindow().handle(), 1d);
		});
		pause(2);
		check("the wheel changes the zoom", () -> cfg().zoomLevel > this.saved.get("zoomLevel"));
		check("the wheel leaves the hotbar alone while zoomed", () -> near(mc().player.getInventory().getSelectedSlot(), this.saved.get("slot")));
		step(() -> FishKeys.ZOOM.setDown(false));
		pause(12);
		check("releasing zoom restores the field of view", () -> Math.abs(mc().gameRenderer.getMainCamera().getFov() - this.saved.get("fov")) < 1d);
		check("no zoom slowdown afterwards", () -> Zoom.sensitivity() == 1d);
		step(() -> cfg().zoomLevel = 4f);
	}

	/** Seals the player in a dark stone box at night: the only thing that can brighten the picture is Fullbright. */
	private void planFullbright() {
		command("time set 18000");
		command("tp @s 40 120 40");
		command("fill 36 117 36 44 124 44 stone hollow");
		pause(40);
		step(() -> {
			mc().player.setYRot(0f);
			mc().player.setXRot(0f);
		});
		pause(20);
		measure("dark");
		shot("fullbright_off");
		step(() -> cfg().fullbright = true);
		pause(25);
		measure("bright");
		shot("fullbright_on");
		check("Fullbright lifts a dark room", () -> {
			note(String.format(Locale.ROOT, "brightness off=%.1f on=%.1f", this.measured.get("dark"), this.measured.get("bright")));
			return this.measured.get("bright") > this.measured.get("dark") * 1.5d + 10d;
		});
		step(() -> cfg().fullbright = false);
		pause(20);
		command("fill 36 117 36 44 124 44 air");
		command("time set 6000");
	}

	private void planSky() {
		command("tp @s 0 200 0");
		step(() -> {
			mc().player.setXRot(-65f);
			mc().player.setYRot(0f);
		});
		pause(40);
		measure("skyVanilla");
		shot("sky_vanilla");
		step(() -> cfg().sky = true);
		for (CustomSky.Preset preset : CustomSky.PRESETS) {
			step(() -> cfg().skyPreset = preset.id());
			pause(10);
			measure("sky_" + preset.id());
			shot("sky_" + preset.id());
		}
		check("Galaxy really replaces the sky", () -> this.measured.get("sky_galaxy") < this.measured.get("skyVanilla") * 0.6d);
		// Looking straight out shows the aurora's curtains and whether distant land blends into the preset.
		step(() -> mc().player.setXRot(-8f));
		for (String preset : List.of("northern_lights", "deep_ocean", "sunset_dream", "vaporwave")) {
			step(() -> cfg().skyPreset = preset);
			pause(8);
			shot("sky_horizon_" + preset);
		}
		// A flat colour would mean the dome is missing and only the fog colour shows through.
		step(() -> {
			cfg().skyPreset = "sunset_dream";
			mc().player.setXRot(-30f);
			this.measured.remove("skyTop");
			Screenshot.takeScreenshot(mc().getMainRenderTarget(), image -> image.close());
		});
		pause(6);
		step(() -> Screenshot.takeScreenshot(mc().getMainRenderTarget(), image -> {
			this.measured.put("skyTop", bandLuminance(image, 0.02f, 0.12f));
			this.measured.put("skyLow", bandLuminance(image, 0.5f, 0.6f));
			image.close();
		}));
		until("sky colours measured", () -> this.measured.containsKey("skyTop"), 100);
		check("the sky has a real gradient", () -> {
			note(String.format(Locale.ROOT, "sky brightness top=%.1f lower=%.1f", this.measured.get("skyTop"), this.measured.get("skyLow")));
			return Math.abs(this.measured.get("skyTop") - this.measured.get("skyLow")) > 15d;
		});
		step(() -> mc().player.setXRot(-8f));
		step(() -> {
			cfg().skyPreset = "galaxy";
			CustomSky.shootingStarNow();
		});
		pause(7);
		shot("sky_shooting_star");
		step(() -> cfg().skyHideClouds = false);
		pause(6);
		shot("sky_with_clouds_allowed");
		step(() -> {
			cfg().skyHideClouds = true;
			cfg().skyHideSun = false;
		});
		pause(6);
		shot("sky_with_sun_allowed");
		step(() -> {
			cfg().skyHideSun = true;
			cfg().sky = false;
		});
		pause(10);
	}

	private void planCosmetics() {
		step(() -> {
			mc().player.setXRot(10f);
			cfg().cape = true;
			cfg().capeFile = "";
			cfg().headphones = true;
			cfg().headphonesOnlyWithMusic = false;
			mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT);
		});
		pause(5);
		check("the cape is on the local player", () -> mc().player.getSkin().cape() != null && mc().player.getSkin().cape().texturePath().equals(Cosmetics.DEFAULT_CAPE));
		for (String hat : List.of("top_hat", "fisher_hat", "crown", "fish_hat", "none")) {
			step(() -> cfg().hat = hat);
			pause(4);
			shot("cosmetic_" + hat);
		}
		step(() -> mc().options.setCameraType(CameraType.THIRD_PERSON_BACK));
		pause(5);
		shot("cape_back_view");
		step(() -> {
			cfg().accentColor = 0xFFF0565B;
			cfg().hat = "top_hat";
		});
		pause(4);
		shot("accent_colour_red");

		// Uploads, with pictures made on the spot.
		step(() -> {
			Path dir = mc().gameDirectory.toPath().resolve("autotest-files");
			Files.createDirectories(dir);
			Path capePicture = dir.resolve("Test Cape.png");
			try (NativeImage image = new NativeImage(120, 180, false)) {
				for (int y = 0; y < 180; y++) {
					for (int x = 0; x < 120; x++) {
						image.setPixel(x, y, 0xFF000000 | (x * 2) << 16 | (y) << 8 | 0x60);
					}
				}
				image.writeToFile(capePicture);
			}
			String error = Cosmetics.importCape(capePicture);
			if (error == null) {
				pass("a normal picture imports as a cape");
			} else {
				fail("a normal picture imports as a cape: " + error);
			}
			Path skinPicture = dir.resolve("Test Skin.png");
			try (NativeImage image = new NativeImage(64, 64, true)) {
				for (int y = 0; y < 64; y++) {
					for (int x = 0; x < 64; x++) {
						image.setPixel(x, y, 0xFF2040F0 | (x * 4) << 8);
					}
				}
				image.writeToFile(skinPicture);
			}
			String skinError = SkinLibrary.importSkin(skinPicture, true);
			if (skinError == null) {
				pass("a PNG imports into the skin library");
			} else {
				fail("a PNG imports into the skin library: " + skinError);
			}
			Path badSkin = dir.resolve("Too Small.png");
			try (NativeImage image = new NativeImage(16, 16, true)) {
				image.writeToFile(badSkin);
			}
			if (SkinLibrary.importSkin(badSkin, false) != null) {
				pass("a picture of the wrong size is refused as a skin");
			} else {
				fail("a picture of the wrong size is refused as a skin");
			}
			SkinLibrary.select(SkinLibrary.all().getLast());
		});
		pause(5);
		check("the cape file is used", () -> mc().player.getSkin().cape().texturePath().getPath().equals("dynamic/cape"));
		check("the local skin is used", () -> mc().player.getSkin().body().texturePath().getPath().startsWith("dynamic/skin/"));
		check("slim arms come through", () -> mc().player.getSkin().model() == PlayerModelType.SLIM);
		shot("local_skin_and_uploaded_cape");
		openMenu("skins");
		pause(6);
		shot("menu_skins_with_entry");
		closeMenu();
		openMenu("cape");
		pause(6);
		shot("menu_cape_with_entry");
		closeMenu();
		step(() -> {
			SkinLibrary.select(null);
			SkinLibrary.all().removeIf(s -> {
				SkinLibrary.select(null);
				return false;
			});
			new ArrayList<>(SkinLibrary.all()).forEach(SkinLibrary::delete);
			Cosmetics.deleteCape(cfg().capeFile);
			cfg().cape = false;
			cfg().hat = "none";
			cfg().headphones = true;
			cfg().headphonesOnlyWithMusic = true;
			cfg().accentColor = 0xFF2FD6C3;
			mc().options.setCameraType(CameraType.FIRST_PERSON);
		});
		pause(3);
		check("the normal skin is back", () -> !mc().player.getSkin().body().texturePath().getPath().startsWith("dynamic/skin/"));

		// Crosshair: every shape in the real game, zoomed by the camera staying where it is.
		step(() -> {
			cfg().crosshair = true;
			cfg().crosshairSize = 6f;
			cfg().crosshairColor = 0xFF2FD6C3;
		});
		for (int shape = 0; shape < 4; shape++) {
			int index = shape;
			step(() -> cfg().crosshairShape = index);
			pause(2);
			shot("crosshair_" + shape);
		}
		step(() -> {
			cfg().crosshair = false;
			cfg().crosshairShape = 0;
		});
	}

	private void planScoreboard() {
		command("scoreboard objectives add fish dummy {\"text\":\"FishSMP\",\"color\":\"aqua\",\"bold\":true}");
		command("scoreboard objectives setdisplay sidebar fish");
		command("scoreboard players set Money fish 12");
		command("scoreboard players set Rank fish 3");
		command("scoreboard players set Fish fish 41");
		pause(10);
		check("the scoreboard element has something to show", () -> FishHud.ELEMENTS.stream().anyMatch(e -> e.id.equals("scoreboard") && e.visible()));
		shot("scoreboard_hidden_numbers");
		step(() -> cfg().scoreboardHideNumbers = false);
		pause(4);
		shot("scoreboard_with_numbers");
		step(() -> cfg().scoreboard = false);
		pause(4);
		shot("scoreboard_vanilla_when_off");
		step(() -> {
			cfg().scoreboard = true;
			cfg().scoreboardHideNumbers = true;
		});
		command("scoreboard objectives remove fish");
	}

	private void planMaps() {
		command("tp @s 0 90 0");
		pause(60);
		step(() -> {
			Waypoints.all().clear();
			mc().player.setXRot(0f);
			// Facing north, where the two waypoints are.
			mc().player.setYRot(180f);
			Waypoints.Point a = Waypoints.addHere("Hafen");
			a.x += 30;
			a.z -= 20;
			a.y += 10;
			Waypoints.Point b = Waypoints.addHere("Sehr weit weg");
			b.z -= 400;
			FishHud.ELEMENTS.stream().filter(e -> e.id.equals("minimap")).findFirst().get().pos().enabled = true;
			cfg().minimapZoom = 1;
		});
		pause(60);
		check("the minimap texture exists", () -> Minimap.setup() != null);
		shot("minimap_round_with_waypoints");
		step(() -> cfg().minimapRound = false);
		pause(5);
		shot("minimap_square");
		step(() -> {
			cfg().minimapRound = true;
			cfg().minimapRotate = true;
			mc().player.setYRot(120f);
		});
		pause(5);
		shot("minimap_rotating");
		step(() -> {
			cfg().minimapRotate = false;
			cfg().minimapZoom = 3;
		});
		pause(60);
		shot("minimap_far_zoom");
		// Walking: the map scrolls without being rescanned from nothing.
		command("tp @s 60 90 60");
		pause(30);
		shot("minimap_after_moving");
		step(() -> {
			cfg().minimapZoom = 1;
			FishHud.ELEMENTS.stream().filter(e -> e.id.equals("minimap")).findFirst().get().pos().enabled = false;
			mc().player.setYRot(0f);
		});
		check("waypoints are saved", () -> {
			Waypoints.save();
			return Files.exists(dev.fishclient.config.Json.file("waypoints.json"));
		});
	}

	/** Really fishes: casts into water, waits for a bite with Lure, reels in, and expects the statistics to count it. */
	private void planFishing() {
		command("tp @s 200 120 200");
		pause(30);
		command("fill 192 112 192 208 117 208 water");
		command("setblock 200 118 200 stone");
		command("tp @s 200 119 200");
		command("item replace entity @s weapon.mainhand with fishing_rod[enchantments={\"minecraft:lure\":3,\"minecraft:unbreaking\":3}]");
		step(() -> {
			mc().player.getInventory().setSelectedSlot(0);
			mc().player.setYRot(0f);
			mc().player.setXRot(25f);
			FishingTracker.resetSession();
			cfg().fishBubbles = true;
			cfg().rodSkin = true;
		});
		pause(10);
		check("the fishing HUD appears with a rod in hand", () -> FishHud.ELEMENTS.stream().anyMatch(e -> e.id.equals("fishing") && e.visible()));
		step(() -> mc().gameMode.useItem(mc().player, InteractionHand.MAIN_HAND));
		until("the hook is out", () -> mc().player.fishing != null, 60);
		pause(40);
		shot("fishing_line_out");
		until("a fish bites", () -> mc().player.fishing != null && ((FishingHookAccessor) mc().player.fishing).fishclient$isBiting(), 1500);
		step(() -> mc().gameMode.useItem(mc().player, InteractionHand.MAIN_HAND));
		until("the catch is counted", () -> FishingTracker.sessionCatches() >= 1, 200);
		check("the catch reached the statistics", () -> {
			note("caught: " + FishingTracker.stats().items);
			return FishingTracker.stats().totalCatches >= 1 && !FishingTracker.stats().items.isEmpty();
		});
		pause(5);
		shot("fishing_after_catch");
		openMenu("fishing");
		pause(10);
		shot("menu_fishing_with_stats");
		closeMenu();
		step(() -> {
			cfg().fishBubbles = false;
			cfg().rodSkin = false;
			FishingTracker.saveIfDirty();
		});
		check("fishing statistics are saved", () -> Files.exists(dev.fishclient.config.Json.file("fishing.json")));
		command("clear @s fishing_rod");
		command("tp @s 0 90 0");
	}

	private void planScreenshots() {
		step(() -> {
			Screenshots.clearPreview();
			cfg().screenshotPreview = true;
			Screenshot.grab(mc().gameDirectory, mc().getMainRenderTarget(), message -> {
			});
		});
		until("the preview appears after a screenshot", () -> Screenshots.preview() != null, 100);
		until("the thumbnail is ready", () -> Screenshots.preview() != null && Screenshots.thumb(Screenshots.preview()) != null, 100);
		step(() -> cfg().screenshotPreview = false);
		pause(10);
		shot("screenshot_preview_toast");
		openMenu("screenshots");
		pause(30);
		shot("menu_screenshots_gallery");
		closeMenu();
	}

	private void planProfiles() {
		step(() -> {
			this.saved.put("profiles", (double) Profiles.names().size());
			Profiles.create("AutoTest");
			cfg().fullbright = true;
			cfg().theme = "goldrush";
			Theme.apply("goldrush", true);
			FishKeys.ZOOM.setKey(com.mojang.blaze3d.platform.InputConstants.getKey(new KeyEvent(GLFW.GLFW_KEY_V, 0, 0)));
			Profiles.save();
		});
		check("a new profile is active", () -> Profiles.active().equals("AutoTest"));
		step(() -> Profiles.load("Default"));
		pause(2);
		check("loading a profile restores its settings", () -> !cfg().fullbright && cfg().theme.equals("deep_ocean"));
		check("loading a profile restores its keybinds", () -> FishKeys.ZOOM.saveString().endsWith(".c"));
		step(() -> Profiles.load("AutoTest"));
		pause(2);
		check("settings come back with the other profile", () -> cfg().fullbright && cfg().theme.equals("goldrush"));
		check("keybinds come back with the other profile", () -> FishKeys.ZOOM.saveString().endsWith(".v"));
		openMenu("profiles");
		pause(10);
		shot("menu_profiles_two");
		closeMenu();
		step(() -> {
			Path exported = mc().gameDirectory.toPath().resolve("autotest-files/Exportiert.json");
			Files.createDirectories(exported.getParent());
			Profiles.exportTo(exported);
			Profiles.load("Default");
			Profiles.delete("AutoTest");
			if (Profiles.names().contains("AutoTest")) {
				fail("deleting a profile");
			} else {
				pass("deleting a profile");
			}
			if (Profiles.importFrom(exported) && Profiles.active().equals("Exportiert") && cfg().fullbright) {
				pass("a profile survives export and import");
			} else {
				fail("a profile survives export and import");
			}
			Path broken = mc().gameDirectory.toPath().resolve("autotest-files/kaputt.json");
			Files.writeString(broken, "{ this is not json");
			if (!Profiles.importFrom(broken)) {
				pass("a broken profile file is refused");
			} else {
				fail("a broken profile file is refused");
			}
			Profiles.load("Default");
			Profiles.delete("Exportiert");
			cfg().screenshotPreview = false;
		});
		pause(3);
		check("back on the default profile", () -> Profiles.active().equals("Default") && !cfg().fullbright);
	}

	private void planMusic() {
		step(() -> cfg().music = true);
		until("the Windows media helper reports", () -> MusicService.lastUpdate() > 0L, 400);
		step(() -> note("Windows reports: title='" + MusicService.track().title + "' artist='" + MusicService.track().artist + "' playing=" + MusicService.track().playing));
		openMenu("music");
		pause(10);
		shot("menu_music_live");
		closeMenu();
		pause(10);
		shot("music_hud_live");
		// The real lookup: only the number of lines is checked, the text itself is never shown or logged.
		step(() -> new Thread(() -> {
			try {
				this.measured.put("lyricLines", (double) Lyrics.fetch("Get Lucky", "Daft Punk", 248000L).size());
			} catch (Exception e) {
				this.measured.put("lyricLines", -1d);
			}
		}).start());
		until("the lyrics lookup answers", () -> this.measured.containsKey("lyricLines"), 400);
		check("lrclib.net delivers synced lines", () -> {
			note("lyric lines found for a known song: " + this.measured.get("lyricLines").intValue());
			return this.measured.get("lyricLines") > 20d;
		});
		until("an unknown song is reported as not found", () -> Lyrics.status() == Lyrics.Status.NOT_FOUND || Lyrics.status() == Lyrics.Status.FOUND, 400);
		step(() -> note("lookup status for the test song: " + Lyrics.status()));
		// Own test lines, every 2.5 seconds over the 30 seconds the silent test track loops.
		step(() -> {
			String[] words = {"Tiefsee-Probezeile eins", "Blasen steigen langsam auf", "Der Fisch summt leise mit", "Wellen rollen um dich herum",
				"Ein Wal winkt aus der Ferne", "Korallen leuchten blau", "Das Netz bleibt heute leer", "Der Mond spiegelt sich im Meer",
				"Noch eine Runde schwimmen", "Die Flut kommt bald zurueck", "Sand rieselt durch die Finger", "Ende der Probezeilen"};
			List<Lyrics.Line> test = new ArrayList<>();
			for (int i = 0; i < words.length; i++) {
				test.add(new Lyrics.Line(i * 2500L, words[i]));
			}
			Lyrics.useLines(test);
			mc().player.setXRot(-10f);
		});
		pause(20);
		check("the floating lyrics have lines", () -> Lyrics.status() == Lyrics.Status.FOUND && Lyrics.lines().size() == 12);
		shot("lyrics_first_person");
		pause(50);
		shot("lyrics_first_person_later");
		step(() -> mc().options.setCameraType(CameraType.THIRD_PERSON_BACK));
		pause(40);
		shot("lyrics_third_person");
		step(() -> {
			cfg().lyricsAccent = true;
			cfg().lyricsSize = 1.5f;
		});
		pause(40);
		shot("lyrics_third_person_accent");
		step(() -> {
			cfg().lyricsAccent = false;
			cfg().lyricsSize = 1f;
			mc().options.setCameraType(CameraType.FIRST_PERSON);
			cfg().music = false;
		});
	}

	private void planAuctions() {
		command("tp @s 300 150 300");
		pause(20);
		command("setblock 300 149 300 stone");
		command("setblock 301 150 300 chest{CustomName:\"Auction House\",Items:[{Slot:0b,id:\"minecraft:diamond\",count:1,components:{\"minecraft:lore\":[\"Price: $100K\"]}},{Slot:1b,id:\"minecraft:diamond\",count:1,components:{\"minecraft:lore\":[\"Price: $9M\"]}},{Slot:2b,id:\"minecraft:elytra\",count:1,components:{\"minecraft:lore\":[\"Price: $100K\"]}}]}");
		command("tp @s 300 150 300");
		pause(10);
		step(() -> {
			Auctions.watches().clear();
			// Item names follow the game's language, so the watch uses the name the player sees.
			Auctions.addWatch(new ItemStack(Items.DIAMOND).getHoverName().getString(), 500000d);
			mc().gameMode.useItemOn(mc().player, InteractionHand.MAIN_HAND, new BlockHitResult(new Vec3(301.5d, 150.5d, 300.5d), Direction.WEST, new BlockPos(301, 150, 300), false));
		});
		until("the auction window opens", () -> mc().screen instanceof AbstractContainerScreen<?>, 60);
		pause(10);
		check("only the cheap diamond is marked", () -> AuctionWatcher.markedCount() == 1);
		shot("auction_window_watch_frame");
		step(() -> mc().player.closeContainer());
		pause(4);
		step(() -> Auctions.watches().clear());
		planLiveAuction();
	}

	/** A stream auction from the first click to the last refund, driven through the real menu. */
	private void planLiveAuction() {
		command("item replace entity @s hotbar.0 with diamond 64");
		command("item replace entity @s hotbar.1 with elytra");
		step(() -> {
			mc().player.getInventory().setSelectedSlot(0);
			cfg().auctionSumPayments = true;
			cfg().auctionExtend = false;
			cfg().auctionWinnerSeconds = 30;
		});
		openMenu("auction");
		pause(4);
		click("la_slot:0");
		click("la_slot:1");
		click("la_min");
		type("100k");
		click("la_seconds");
		step(() -> mc().screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_BACKSPACE, 0, GLFW.GLFW_MOD_CONTROL)));
		pause(2);
		type("40");
		click("la_note");
		type("Nur heute im Stream");
		check("nothing starts before the button is pressed", () -> !LiveAuction.running());
		shot("live_form_filled");
		click("la_start");
		check("the auction starts", LiveAuction::running);
		check("both picked stacks are in the lot", () -> LiveAuction.current() != null && LiveAuction.current().lots.size() == 2);
		check("the duration comes from the form", () -> LiveAuction.remainingMs() > 34000L && LiveAuction.remainingMs() <= 40000L);
		check("the minimum bid comes from the form", () -> near(LiveAuction.current().minBid, 1e5));
		closeMenu();
		pause(10);
		shot("live_card_waiting");
		command("tellraw @s \"Fishy_Bob paid you $1.5M.\"");
		command("tellraw @s \"Alice_01 paid you $200K.\"");
		command("tellraw @s \"Carla_X paid you $50K.\"");
		pause(6);
		check("the highest payment leads", () -> LiveAuction.leader() != null && LiveAuction.leader().player.equals("Fishy_Bob"));
		check("a bid under the minimum does not count", () -> LiveAuction.ranking().stream().filter(b -> b.player.equals("Carla_X")).noneMatch(LiveAuction::eligible));
		shot("live_card_three_bids");
		command("tellraw @s \"Alice_01 paid you $1.6M.\"");
		pause(1);
		shot("live_card_overtake_midway");
		pause(10);
		check("payments of one bidder add up", () -> LiveAuction.leader() != null && LiveAuction.leader().player.equals("Alice_01") && near(LiveAuction.leader().total, 1.8e6));
		shot("live_card_new_leader");
		step(() -> {
			cfg().auctionExtend = true;
			cfg().auctionExtendSeconds = 15;
		});
		until("the last ten seconds", () -> LiveAuction.remainingMs() < 10000L, 900);
		shot("live_card_last_seconds");
		command("tellraw @s \"Dan_99 paid you $300K.\"");
		pause(3);
		check("a late bid extends the timer", () -> LiveAuction.remainingMs() > 12000L);
		step(() -> cfg().auctionExtend = false);
		openMenu("auction");
		pause(6);
		shot("live_menu_running");
		click("la_end");
		check("the auction ends from the menu", () -> !LiveAuction.running());
		check("the biggest payer wins", () -> LiveAuction.lastResult() != null && "Alice_01".equals(LiveAuction.lastResult().winner) && near(LiveAuction.lastResult().price, 1.8e6));
		check("everyone else is due a refund", () -> {
			var refunds = LiveAuction.lastResult().refunds;
			return refunds.size() == 3 && refunds.stream().noneMatch(r -> r.player.equals("Alice_01"))
				&& refunds.stream().anyMatch(r -> r.player.equals("Fishy_Bob") && near(r.amount, 1.5e6))
				&& refunds.stream().anyMatch(r -> r.player.equals("Carla_X") && near(r.amount, 5e4));
		});
		check("the auction is saved", () -> Files.exists(dev.fishclient.config.Json.file("live_auctions.json")));
		closeMenu();
		pause(1);
		shot("live_winner_card_popping");
		pause(10);
		shot("live_winner_card");
		command("tellraw @s \"Eve_7 paid you $10K.\"");
		pause(3);
		check("a payment after the end is listed for refunding", () -> LiveAuction.lastResult().refunds.stream().anyMatch(r -> r.player.equals("Eve_7") && r.late));
		step(() -> {
			this.commands.clear();
			this.commandTimes.clear();
		});
		openMenu("auction");
		pause(6);
		shot("live_menu_result_refunds");
		click(() -> "la_refund:" + LiveAuction.lastResult().time + ":0");
		check("one click alone sends nothing", () -> this.commands.isEmpty());
		click(() -> "la_refund:" + LiveAuction.lastResult().time + ":0");
		check("the confirmed refund goes out as a pay command", () -> this.commands.size() == 1 && this.commands.getFirst().equals("pay Fishy_Bob 1500000"));
		click(() -> "la_refund_all:" + LiveAuction.lastResult().time);
		click(() -> "la_refund_all:" + LiveAuction.lastResult().time);
		pause(110);
		check("refund all sends the rest one after another", () -> {
			note("refund commands: " + this.commands);
			return this.commands.size() == 4 && LiveAuction.openRefunds(LiveAuction.lastResult()) < 0.5d;
		});
		check("refunds keep a gap between them", () -> {
			for (int i = 2; i < this.commandTimes.size(); i++) {
				if (this.commandTimes.get(i) - this.commandTimes.get(i - 1) < 1300L) {
					return false;
				}
			}
			return this.commandTimes.size() == 4;
		});
		shot("live_menu_refunds_sent");
		closeMenu();
		openMenu("recent");
		pause(6);
		shot("live_history");
		click(() -> "redo:" + LiveAuction.history().getFirst().time);
		check("redo starts the same auction again", () -> LiveAuction.running() && LiveAuction.current().lots.size() == 2 && near(LiveAuction.current().minBid, 1e5));
		closeMenu();
		openMenu("auction");
		pause(4);
		click("la_cancel");
		check("cancel needs a second click", LiveAuction::running);
		click("la_cancel");
		check("cancelling ends without a winner", () -> !LiveAuction.running() && LiveAuction.lastResult().cancelled && LiveAuction.lastResult().winner == null);
		closeMenu();
		pause(4);
		shot("live_cancelled_card");
		step(() -> {
			LiveAuction.hideWinner();
			cfg().auctionWinnerSeconds = 15;
		});
	}

	/** Changing the language reloads all textures, which must not leave the menu with dead references. */
	private void planResourceReload() {
		step(() -> {
			mc().options.languageCode = "en_us";
			mc().getLanguageManager().setSelected("en_us");
			mc().reloadResourcePacks();
		});
		until("the reload starts", () -> mc().getOverlay() != null, 100);
		until("the reload finishes", () -> mc().getOverlay() == null, 1200);
		pause(10);
		openMenu("theme");
		pause(8);
		check("the menu opens after a resource reload", () -> mc().screen instanceof FishMenuScreen);
		shot("menu_english_after_reload");
		closeMenu();
		openMenu("transactions");
		pause(6);
		shot("menu_english_transactions");
		closeMenu();
		pause(5);
		shot("hud_english");
		step(() -> {
			mc().options.languageCode = "de_de";
			mc().getLanguageManager().setSelected("de_de");
			mc().reloadResourcePacks();
		});
		until("the second reload starts", () -> mc().getOverlay() != null, 100);
		until("the second reload finishes", () -> mc().getOverlay() == null, 1200);
		pause(5);
	}

	private static final String OVERWORLD = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";

	private static String dimension() {
		return mc().level == null ? "" : mc().level.dimension().identifier().toString();
	}

	private static PortalViews.Entry portalEntry(String dim) {
		for (PortalViews.Entry e : PortalViews.entries()) {
			if (e.portal.dim.equals(dim)) {
				return e;
			}
		}
		return null;
	}

	private static boolean viewSaved(String dim, String target) {
		PortalViews.Entry e = portalEntry(dim);
		return e != null && e.target.equals(target) && Files.isRegularFile(PortalViews.dir().resolve(e.view));
	}

	/** Puts the player in front of (or into) the portal of {@code dim}, looking at it. */
	private void standAtPortal(String dim, double out) {
		step(() -> {
			PortalViews.Entry e = portalEntry(dim);
			if (e == null) {
				fail("no saved portal in " + dim);
				return;
			}
			PortalViews.Portal p = e.portal;
			Vec3 c = p.center();
			double x = p.axisX() ? c.x : c.x + out;
			double z = p.axisX() ? c.z + out : c.z;
			// Looking back at the portal from the +Z side means yaw 180, from the +X side yaw 90.
			float yaw = p.axisX() ? 180f : 90f;
			if (out > 0d) {
				// The generated Nether portal only promises one block of floor, so the test lays a small platform.
				int a0 = (p.axisX() ? p.minX : p.minZ) - 1;
				int a1 = (p.axisX() ? p.maxX : p.maxZ) + 1;
				int n0 = (p.axisX() ? p.maxZ : p.maxX) + 1;
				int n1 = n0 + 4;
				String floor = p.axisX()
					? String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", a0, p.minY - 1, n0, a1, p.minY - 1, n1)
					: String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", n0, p.minY - 1, a0, n1, p.minY - 1, a1);
				String air = p.axisX()
					? String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air", a0, p.minY, n0, a1, p.maxY + 1, n1)
					: String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air", n0, p.minY, a0, n1, p.maxY + 1, a1);
				mc().player.connection.sendCommand(floor);
				mc().player.connection.sendCommand(air);
			}
			mc().player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.2f %d %.2f %.0f 0", x, p.minY, z, yaw));
		});
		pause(3);
	}

	/** A real nether portal trip there and back: both ends get a panorama and show the other side. */
	private void planPortals() {
		step(() -> {
			PortalViews.clear();
			cfg().portalViews = true;
			cfg().portalTransition = true;
			mc().options.setCameraType(CameraType.FIRST_PERSON);
		});
		// Protection first and hovering in creative, so nothing can hurt the player while the area loads.
		command("gamemode creative");
		command("effect give @s minecraft:resistance 1000 4 true");
		command("effect give @s minecraft:fire_resistance 1000 0 true");
		step(() -> {
			mc().player.getAbilities().flying = true;
			mc().player.onUpdateAbilities();
		});
		command("tp @s 502 160 506");
		// A fresh area has to be generated first; on the slower production client that takes a while.
		until("the portal area is loaded", () -> mc().level.hasChunkAt(new BlockPos(502, 150, 500)) && mc().player.position().distanceTo(new Vec3(502, 160, 506)) < 2d, 600);
		pause(20);
		command("fill 494 149 494 509 158 506 minecraft:air");
		command("fill 494 149 494 509 149 506 minecraft:stone");
		command("fill 500 150 500 503 154 500 minecraft:obsidian");
		command("fill 501 151 500 502 153 500 minecraft:air");
		command("fill 501 151 500 502 153 500 minecraft:nether_portal[axis=x]");
		command("tp @s 502.0 150 504.5 180 0");
		until("the built portal is a real nether portal", () -> mc().level.getBlockState(new BlockPos(501, 151, 500)).is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)
			&& mc().player.position().distanceTo(new Vec3(502, 150, 504.5)) < 1d, 600);
		// Survival only once the player stands on the platform: creative skips the wait in the portal.
		command("gamemode survival");
		until("the player stands on the platform", () -> mc().player.onGround() && mc().player.isAlive(), 200);
		pause(20);
		shot("portal_before_first_trip");
		step(() -> this.saved.put("trips", (double) PortalTransition.trips()));

		// Into the portal: the panorama of this side is taken while the trip warms up.
		command("tp @s 502.0 151 500.5 180 0");
		until("the departure panorama is taken", PortalCapture::busy, 300);
		until("the trip to the Nether starts", () -> mc().screen instanceof net.minecraft.client.gui.screens.LevelLoadingScreen || dimension().equals(NETHER), 300);
		check("the portal trip uses the FishClient transition", () -> PortalTransition.trips() > this.saved.get("trips"));
		pause(8);
		shot("portal_transition_fly_in");
		pause(14);
		shot("portal_transition_late");
		until("arrived in the Nether", () -> dimension().equals(NETHER) && mc().screen == null, 900);
		until("the transition has faded out", () -> !PortalTransition.active(), 200);
		shot("portal_transition_done");
		until("the Nether portal shows the Overworld", () -> viewSaved(NETHER, OVERWORLD), 300);
		until("the Overworld portal shows the Nether", () -> viewSaved(OVERWORLD, NETHER), 400);
		until("the arrival panorama is finished", () -> !PortalCapture.busy(), 300);

		// Out of the portal, looking back at it: it must show the Overworld.
		standAtPortal(NETHER, 3.2);
		pause(40);
		check("the Nether portal draws the view through it", () -> PortalRenderer.drawnLastFrame() > 0);
		shot("portal_view_in_nether");
		step(() -> cfg().portalViews = false);
		pause(4);
		check("switching the view off hides it", () -> PortalRenderer.drawnLastFrame() == 0);
		shot("portal_view_off_in_nether");
		step(() -> cfg().portalViews = true);
		pause(4);

		// And back: the remembered Overworld view shows during the trip.
		step(() -> this.saved.put("trips", (double) PortalTransition.trips()));
		standAtPortal(NETHER, 0d);
		until("the trip back starts", () -> mc().screen instanceof net.minecraft.client.gui.screens.LevelLoadingScreen || dimension().equals(OVERWORLD), 400);
		check("the trip back shows the remembered destination", () -> PortalTransition.trips() > this.saved.get("trips") && PortalTransition.hasDestination());
		pause(8);
		shot("portal_transition_back_fly_in");
		pause(14);
		shot("portal_transition_back_destination");
		until("back in the Overworld", () -> dimension().equals(OVERWORLD) && mc().screen == null, 900);
		until("the return panorama is finished", () -> !PortalCapture.busy(), 400);
		pause(20);
		standAtPortal(OVERWORLD, 3.2);
		pause(40);
		check("the Overworld portal draws the view through it", () -> PortalRenderer.drawnLastFrame() > 0);
		shot("portal_view_in_overworld");
		standAtPortal(OVERWORLD, 1.2);
		pause(10);
		shot("portal_view_close_up");
		check("two linked views are saved", () -> PortalViews.entries().size() == 2);

		// The menu page lists them and deletes one after a confirming second click.
		openMenu("portals");
		pause(6);
		shot("menu_portals_with_views");
		click(() -> "portal_del:" + portalEntry(NETHER).portal.id());
		click(() -> "portal_del:" + portalEntry(NETHER).portal.id());
		check("deleting a view takes a confirming second click", () -> PortalViews.entries().size() == 1 && portalEntry(NETHER) == null);
		closeMenu();
		command("gamemode creative");
		command("effect clear @s");
		command("tp @s 0 90 0");
		pause(20);
	}

	/** The menu must sit in the same physical place at any GUI scale. */
	private void planGuiScale() {
		for (int scale : new int[]{2, 3}) {
			step(() -> {
				mc().options.guiScale().set(scale);
				mc().resizeGui();
			});
			openMenu("theme");
			pause(8);
			step(() -> {
				float[] p = this.widgets.get("side:theme");
				this.saved.put("sideX" + scale, (double) p[0] * mc().getWindow().getGuiScale());
				this.saved.put("sideY" + scale, (double) p[1] * mc().getWindow().getGuiScale());
			});
			shot("gui_scale_" + scale);
			closeMenu();
			step(() -> mc().setScreen(null));
			pause(4);
			shot("hud_gui_scale_" + scale);
		}
		step(() -> {
			mc().options.guiScale().set(0);
			mc().resizeGui();
		});
		openMenu("theme");
		pause(8);
		step(() -> {
			float[] p = this.widgets.get("side:theme");
			double x = p[0] * mc().getWindow().getGuiScale();
			double y = p[1] * mc().getWindow().getGuiScale();
			note(String.format(Locale.ROOT, "sidebar item in pixels: auto (%.0f, %.0f) scale2 (%.0f, %.0f) scale3 (%.0f, %.0f)", x, y, this.saved.get("sideX2"), this.saved.get("sideY2"), this.saved.get("sideX3"), this.saved.get("sideY3")));
			if (Math.abs(x - this.saved.get("sideX2")) < 6d && Math.abs(y - this.saved.get("sideY3")) < 6d) {
				pass("the menu stays in place at every GUI scale");
			} else {
				fail("the menu stays in place at every GUI scale");
			}
		});
		closeMenu();
		planResourceReload();
		// Saved settings can be read back after everything above.
		step(Profiles::save);
		check("the profile file is valid JSON", () -> dev.fishclient.config.Json.read(dev.fishclient.config.Json.file("profiles/Default.json"), FishConfig.class) != null);
	}
}
