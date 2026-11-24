package dev.fishclient.mixin;

import dev.fishclient.feature.TitleBackground;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
	@Redirect(
		method = "extractRenderState",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/TitleScreen;extractPanorama(Lnet/minecraft/client/gui/GuiGraphicsExtractor;F)V")
	)
	private void fishclient$background(TitleScreen screen, GuiGraphicsExtractor graphics, float a) {
		TitleBackground.draw(graphics, screen.width, screen.height);
	}

	@Redirect(
		method = "extractRenderState",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/LogoRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IF)V")
	)
	private void fishclient$logo(LogoRenderer renderer, GuiGraphicsExtractor graphics, int width, float alpha) {
		TitleBackground.drawLogo(graphics, width, alpha);
	}

	@Redirect(
		method = "extractRenderState",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/SplashRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/client/gui/Font;F)V")
	)
	private void fishclient$noSplash(SplashRenderer splash, GuiGraphicsExtractor graphics, int width, Font font, float alpha) {
	}
}
