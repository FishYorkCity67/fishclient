package dev.fishclient.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.fishclient.feature.Screenshots;
import java.io.File;
import java.util.function.Consumer;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Screenshot.class)
public abstract class ScreenshotMixin {
	/** The game's callback runs after the file was written, which is the moment the preview can show it. */
	@ModifyVariable(
		method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V",
		at = @At("HEAD"),
		argsOnly = true
	)
	private static Consumer<Component> fishclient$notify(Consumer<Component> callback) {
		return message -> {
			callback.accept(message);
			Screenshots.onSaved();
		};
	}
}
