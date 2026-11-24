package dev.fishclient.mixin;

import dev.fishclient.config.FishConfig;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapExtractorMixin {
	/** The lightmap shader's night-vision path is what lifts dark areas, without needing the potion effect. */
	@Inject(method = "extract", at = @At("TAIL"))
	private void fishclient$fullbright(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
		if (FishConfig.get().fullbright && state.needsUpdate) {
			state.nightVisionEffectIntensity = 1f;
			state.brightness = Math.max(state.brightness, 1f);
			state.darknessEffectScale = 0f;
		}
	}
}
