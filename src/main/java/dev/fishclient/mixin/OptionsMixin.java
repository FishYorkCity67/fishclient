package dev.fishclient.mixin;

import dev.fishclient.config.FishConfig;
import dev.fishclient.feature.CustomSky;
import dev.fishclient.menu.FishMenuScreen;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
public abstract class OptionsMixin {
	/** The game has one blur pass, so the strength slider applies while FishClient's menu is on screen. */
	@Inject(method = "getMenuBackgroundBlurriness", at = @At("HEAD"), cancellable = true)
	private void fishclient$menuBlur(CallbackInfoReturnable<Integer> cir) {
		if (Minecraft.getInstance().screen instanceof FishMenuScreen menu) {
			cir.setReturnValue(FishConfig.get().frostedBlur ? menu.blurNow() : 0);
		}
	}

	/** Bright day clouds look out of place in front of a night-coloured preset. */
	@Inject(method = "getCloudStatus", at = @At("HEAD"), cancellable = true)
	private void fishclient$skyClouds(CallbackInfoReturnable<CloudStatus> cir) {
		if (FishConfig.get().skyHideClouds && CustomSky.active()) {
			cir.setReturnValue(CloudStatus.OFF);
		}
	}
}
