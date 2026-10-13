package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.platform.FramerateLimitTracker;

import vanta.performance.FpsControl;

/**
 * Frame-rate cap: stops Minecraft's idle limiter from randomly dropping you to 30 FPS
 * while you play, then applies VANTA's menu and tabbed-out caps.
 */
@Mixin(FramerateLimitTracker.class)
abstract class FramerateLimitTrackerMixin {
	@Shadow
	private int framerateLimit;

	@Shadow
	public abstract FramerateLimitTracker.FramerateThrottleReason getThrottleReason();

	@Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
	private void vanta$limit(CallbackInfoReturnable<Integer> cir) {
		int vanilla = cir.getReturnValue();
		int limit = FpsControl.limit(vanilla, framerateLimit, getThrottleReason());
		if (limit != vanilla) cir.setReturnValue(limit);
	}
}
