package dev.fishclient.test.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Sends a key press down the same path a real key takes, including Fabric's screen events. */
@Mixin(KeyboardHandler.class)
public interface KeyboardHandlerTestAccess {
	@Invoker("keyPress")
	void test$keyPress(long handle, int action, KeyEvent event);
}
