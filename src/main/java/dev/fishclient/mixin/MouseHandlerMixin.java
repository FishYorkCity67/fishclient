package dev.fishclient.mixin;

import dev.fishclient.feature.Cps;
import dev.fishclient.feature.Freelook;
import dev.fishclient.feature.Zoom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void fishclient$turn(LocalPlayer player, double dx, double dy) {
		double speed = Zoom.sensitivity();
		if (Freelook.active()) {
			Freelook.turn(dx * speed, dy * speed);
		} else {
			player.turn(dx * speed, dy * speed);
		}
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void fishclient$scroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
		if (this.minecraft.screen == null && Zoom.scroll(yoffset)) {
			ci.cancel();
		}
	}

	@Inject(method = "onButton", at = @At("HEAD"))
	private void fishclient$countClick(long handle, MouseButtonInfo info, int action, CallbackInfo ci) {
		if (action == 1 && this.minecraft.screen == null) {
			Cps.press(info.button());
		}
	}
}
