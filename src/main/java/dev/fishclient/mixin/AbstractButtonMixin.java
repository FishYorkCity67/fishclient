package dev.fishclient.mixin;

import dev.fishclient.feature.TitleBackground;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives the title screen's buttons the mod's rounded look. Every other screen keeps vanilla buttons. */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin {
	@Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
	private void fishclient$titleButton(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		if (Minecraft.getInstance().screen instanceof TitleScreen) {
			TitleBackground.drawButton(graphics, (AbstractButton) (Object) this);
			ci.cancel();
		}
	}

	@ModifyArg(
		method = "extractDefaultLabel",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/AbstractButton;extractScrollingStringOverContents(Lnet/minecraft/client/gui/ActiveTextCollector;Lnet/minecraft/network/chat/Component;I)V"),
		index = 1
	)
	private Component fishclient$titleLabel(Component message) {
		return Minecraft.getInstance().screen instanceof TitleScreen ? TitleBackground.styledLabel(message) : message;
	}
}
