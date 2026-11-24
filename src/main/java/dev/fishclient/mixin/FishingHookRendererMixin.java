package dev.fishclient.mixin;

import dev.fishclient.config.FishConfig;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(FishingHookRenderer.class)
public abstract class FishingHookRendererMixin {
	/** Recolours the line. The render state does not tell whose hook it is, so all lines on this client change. */
	@ModifyArg(
		method = "stringVertex",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(I)Lcom/mojang/blaze3d/vertex/VertexConsumer;")
	)
	private static int fishclient$lineColor(int color) {
		return FishConfig.get().rodSkin ? FishConfig.get().rodColor : color;
	}
}
