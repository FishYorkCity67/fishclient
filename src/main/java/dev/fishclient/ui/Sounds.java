package dev.fishclient.ui;

import dev.fishclient.FishClient;
import dev.fishclient.config.FishConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

/** The mod's own UI and water sounds. They are played by id, so nothing is added to the game registry. */
public final class Sounds {
	public static final SoundEvent CLICK = event("click");
	public static final SoundEvent TOGGLE_ON = event("toggle_on");
	public static final SoundEvent TOGGLE_OFF = event("toggle_off");
	public static final SoundEvent MENU_OPEN = event("menu_open");
	public static final SoundEvent MENU_CLOSE = event("menu_close");
	public static final SoundEvent WATER_OPEN = event("water_open");
	public static final SoundEvent BUBBLES = event("bubbles");
	public static final SoundEvent BID = event("bid");
	public static final SoundEvent TICK = event("tick");
	public static final SoundEvent AUCTION_START = event("auction_start");
	public static final SoundEvent AUCTION_END = event("auction_end");

	private static SoundEvent event(String name) {
		return SoundEvent.createVariableRangeEvent(FishClient.id(name));
	}

	public static void ui(SoundEvent sound) {
		if (FishConfig.get().uiSounds) {
			play(sound, 1f, 0.45f);
		}
	}

	public static void water(SoundEvent sound) {
		if (FishConfig.get().waterSounds) {
			play(sound, 1f, 0.5f);
		}
	}

	public static void play(SoundEvent sound, float pitch, float volume) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
	}

	private Sounds() {
	}
}
