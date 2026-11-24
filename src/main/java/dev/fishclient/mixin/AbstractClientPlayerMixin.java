package dev.fishclient.mixin;

import dev.fishclient.feature.Cosmetics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	/** Only the local player's skin is patched, for the body, the first-person hand and the cape alike. */
	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void fishclient$skin(CallbackInfoReturnable<PlayerSkin> cir) {
		if ((Object) this == Minecraft.getInstance().player) {
			cir.setReturnValue(Cosmetics.patchSkin(cir.getReturnValue()));
		}
	}
}
