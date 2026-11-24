package dev.fishclient.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All settings that belong to a profile. Gson fills in missing fields from these defaults. */
public final class FishConfig {
	private static FishConfig active = new FishConfig();

	public static FishConfig get() {
		return active;
	}

	static void set(FishConfig config) {
		active = config;
	}

	/** Position of a movable HUD element. {@code x} and {@code y} are fractions of the free screen space. */
	public static final class HudPos {
		public boolean enabled;
		public float x;
		public float y;
		public float scale = 1f;

		public HudPos(boolean enabled, float x, float y) {
			this.enabled = enabled;
			this.x = x;
			this.y = y;
		}
	}

	/** Chat patterns of one server. Each regex names the groups {@code player}, {@code amount} or {@code item}. */
	public static final class ServerPatterns {
		public String name = "";
		public String addressContains = "";
		public List<String> received = new ArrayList<>();
		public List<String> sent = new ArrayList<>();
		public List<String> sold = new ArrayList<>();
		public List<String> bought = new ArrayList<>();
		/** Used for refunds after a live auction. */
		public String payCommand = "/pay {player} {amount}";
	}

	// Menu look
	public String theme = "deep_ocean";
	public boolean seeThrough = true;
	public boolean frostedBlur = true;
	public boolean ambient = true;
	public float radius = 10f;
	public int blur = 5;
	public boolean uiSounds = true;
	public boolean waterSounds = true;
	public String rank = "Fisher";

	// Utilities
	public boolean freelook = true;
	public boolean freelookToggle = false;
	public boolean freelookThirdPerson = true;
	public boolean zoom = true;
	public float zoomLevel = 4f;
	public boolean zoomScroll = true;
	public float zoomSensitivity = 0.45f;
	public boolean zoomClick = false;
	public boolean fullbright = false;

	// FPS and ping colour thresholds
	public int fpsGood = 60;
	public int fpsOkay = 30;
	public int pingGood = 80;
	public int pingOkay = 160;

	// Scoreboard
	public boolean scoreboard = true;
	public boolean scoreboardHideNumbers = true;

	// Crosshair
	public boolean crosshair = false;
	public int crosshairShape = 0;
	public float crosshairSize = 5f;
	public float crosshairGap = 2f;
	public float crosshairThickness = 1f;
	public int crosshairColor = 0xFFFFFFFF;
	public boolean crosshairDot = false;

	// Sky
	public boolean sky = false;
	public String skyPreset = "northern_lights";
	public boolean shootingStars = true;
	public boolean skyHideSun = true;
	public boolean skyHideClouds = true;

	// Portals
	public boolean portalViews = true;
	public boolean portalTransition = true;
	public int portalViewSize = 512;
	public int portalViewDistance = 48;

	// Live auction
	public boolean auctionSumPayments = true;
	public boolean auctionExtend = false;
	public int auctionExtendSeconds = 15;
	public int auctionWinnerSeconds = 15;
	public int auctionSeconds = 60;
	public boolean auctionSounds = true;

	// Profit and transactions
	public int minAmountFilter = 0;
	public String playerFilter = "";
	public boolean profitShowTopPayer = true;

	// Fishing
	public boolean fishBubbles = false;
	public boolean rodSkin = false;
	public int rodColor = 0xFF2FD6C3;

	// Cosmetics
	public boolean cape = false;
	public String capeFile = "";
	public float capePhysics = 1f;
	public String hat = "none";
	public int accentColor = 0xFF2FD6C3;
	public boolean headphones = true;
	public boolean headphonesOnlyWithMusic = true;

	// Skins
	public String localSkin = "";

	// Music
	public boolean music = false;
	public boolean musicLyrics = true;
	public int lyricsOffsetMs = 0;
	public float lyricsSize = 1f;
	public boolean lyricsAccent = false;

	// Minimap
	public int minimapZoom = 1;
	public boolean minimapRotate = false;
	public boolean minimapRound = true;
	public int minimapSize = 96;
	public boolean minimapCoords = true;

	// Waypoints
	public boolean waypointsWorld = true;
	public boolean waypointsDistance = true;
	public int waypointMaxDistance = 5000;

	// Screenshots
	public boolean screenshotPreview = true;

	public Map<String, HudPos> hud = new LinkedHashMap<>();
	public List<ServerPatterns> servers = new ArrayList<>();
	public Map<String, String> keybinds = new LinkedHashMap<>();
}
