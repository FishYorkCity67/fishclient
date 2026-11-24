package dev.fishclient;

import dev.fishclient.config.Profiles;
import dev.fishclient.feature.AuctionWatcher;
import dev.fishclient.feature.Auctions;
import dev.fishclient.feature.ChatWatcher;
import dev.fishclient.feature.CosmeticLayer;
import dev.fishclient.feature.Cosmetics;
import dev.fishclient.feature.FishingTracker;
import dev.fishclient.feature.Freelook;
import dev.fishclient.feature.LiveAuction;
import dev.fishclient.feature.Lyrics;
import dev.fishclient.feature.MusicService;
import dev.fishclient.feature.PortalCapture;
import dev.fishclient.feature.PortalRenderer;
import dev.fishclient.feature.PortalTransition;
import dev.fishclient.feature.PortalViews;
import dev.fishclient.feature.SkinLibrary;
import dev.fishclient.feature.Transactions;
import dev.fishclient.feature.Waypoints;
import dev.fishclient.feature.Zoom;
import dev.fishclient.hud.FishHud;
import dev.fishclient.menu.FishMenuScreen;
import dev.fishclient.ui.L;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class FishClient implements ClientModInitializer {
	public static final String MOD_ID = "fishclient";
	private int saveCountdown = 200;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitializeClient() {
		FishKeys.register();
		Profiles.init();
		Transactions.load();
		Auctions.load();
		LiveAuction.load();
		FishingTracker.load();
		Waypoints.load();
		SkinLibrary.load();
		FishHud.register();

		// System messages only: normal player chat could otherwise fake a payment by typing the same words.
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				ChatWatcher.onMessage(message.getString());
			}
		});

		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatarRenderer) {
				helper.register(new CosmeticLayer(avatarRenderer));
			}
		});

		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(PortalRenderer::render);
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			AuctionWatcher.attach(screen);
			// Keybinds do not fire while a screen is open, but the menu should open from the title screen too.
			if (screen instanceof TitleScreen) {
				ScreenKeyboardEvents.afterKeyPress(screen).register((current, event) -> {
					if (FishKeys.MENU.matches(event)) {
						client.setScreen(new FishMenuScreen(current));
					}
				});
			}
		});
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> Profiles.applyKeybinds());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			MusicService.stop();
			saveAll();
			Profiles.save();
		});
	}

	private void tick(Minecraft mc) {
		Freelook.tick(mc);
		Zoom.tick(mc);
		Cosmetics.tick(mc);
		FishingTracker.tick(mc);
		MusicService.tick(mc);
		Lyrics.tick();
		AuctionWatcher.tick(mc);
		LiveAuction.tick(mc);
		PortalViews.tick(mc);
		PortalTransition.tick(mc);
		// Last: the panorama renders whole frames, so everything else of this tick is settled by then.
		PortalCapture.tick(mc);
		Profiles.tick();
		while (FishKeys.MENU.consumeClick()) {
			if (mc.screen == null) {
				mc.setScreen(new FishMenuScreen());
			}
		}
		while (FishKeys.WAYPOINT.consumeClick()) {
			if (mc.player != null) {
				Waypoints.Point p = Waypoints.addHere(L.f("waypoint.default_name", Waypoints.all().size() + 1));
				mc.gui.setOverlayMessage(Component.literal(L.f("waypoint.added", p.name)), false);
			}
		}
		if (--this.saveCountdown <= 0) {
			this.saveCountdown = 200;
			saveAll();
		}
	}

	private static void saveAll() {
		Transactions.saveIfDirty();
		Auctions.saveIfDirty();
		FishingTracker.saveIfDirty();
	}
}
