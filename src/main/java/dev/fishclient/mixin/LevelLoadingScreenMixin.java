package dev.fishclient.mixin;

import dev.fishclient.feature.PortalTransition;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the portal transition instead of the loading screen; the screen itself still decides when it closes. */
@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void fishclient$begin(LevelLoadTracker tracker, LevelLoadingScreen.Reason reason, CallbackInfo ci) {
		PortalTransition.begin(reason);
	}

	@Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
	private void fishclient$background(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (PortalTransition.active()) {
			PortalTransition.drawLoading(graphics);
			ci.cancel();
		}
	}

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void fishclient$foreground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (PortalTransition.active()) {
			ci.cancel();
		}
	}
}
