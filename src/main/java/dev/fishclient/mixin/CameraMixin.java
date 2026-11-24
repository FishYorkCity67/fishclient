package dev.fishclient.mixin;

import dev.fishclient.feature.Freelook;
import dev.fishclient.feature.PortalCapture;
import dev.fishclient.feature.Zoom;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Shadow
	public abstract boolean isPanoramicMode();

	@Redirect(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
	private float fishclient$freelookYaw(Entity entity, float partialTicks) {
		return Freelook.active() ? Freelook.yaw() : entity.getViewYRot(partialTicks);
	}

	@Redirect(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
	private float fishclient$freelookPitch(Entity entity, float partialTicks) {
		return Freelook.active() ? Freelook.pitch() : entity.getViewXRot(partialTicks);
	}

	/** While a portal panorama is rendered, the camera stands in front of the portal instead of at the player's eyes. */
	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void fishclient$portalCamera(float partialTicks, CallbackInfo ci) {
		Vec3 override = PortalCapture.cameraOverride();
		if (override != null) {
			setPosition(override);
		}
	}

	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void fishclient$zoom(float partialTicks, CallbackInfoReturnable<Float> cir) {
		float divisor = Zoom.fovDivisor();
		// Panoramas need exactly 90 degrees, whatever the zoom key is doing.
		if (divisor != 1f && !isPanoramicMode()) {
			cir.setReturnValue(cir.getReturnValueF() / divisor);
		}
	}
}
