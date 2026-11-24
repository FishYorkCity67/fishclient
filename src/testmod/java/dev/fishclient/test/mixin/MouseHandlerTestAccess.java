package dev.fishclient.test.mixin;

import dev.fishclient.test.MouseAccess;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets the test place the pointer and feed mouse deltas, and keeps the real cursor free while it runs. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerTestAccess implements MouseAccess {
	@Shadow
	private double xpos;
	@Shadow
	private double ypos;
	@Shadow
	private double accumulatedDX;
	@Shadow
	private double accumulatedDY;

	@Shadow
	protected abstract void turnPlayer(double mousea);

	@Shadow
	protected abstract void onScroll(long handle, double xoffset, double yoffset);

	@Override
	public void test$pointer(double x, double y) {
		this.xpos = x;
		this.ypos = y;
	}

	@Override
	public void test$delta(double dx, double dy) {
		this.accumulatedDX = dx;
		this.accumulatedDY = dy;
	}

	@Override
	public void test$turn() {
		turnPlayer(0d);
	}

	@Override
	public void test$scroll(long handle, double amount) {
		onScroll(handle, 0d, amount);
	}

	@Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
	private void test$neverGrab(CallbackInfo ci) {
		ci.cancel();
	}
}
