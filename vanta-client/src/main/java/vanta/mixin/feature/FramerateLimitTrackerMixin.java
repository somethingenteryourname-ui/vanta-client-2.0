package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.platform.FramerateLimitTracker;

import vanta.performance.BackgroundFpsModule;

/** Background FPS: caps the frame rate while the window isn't focused. */
@Mixin(FramerateLimitTracker.class)
abstract class FramerateLimitTrackerMixin {
	@Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
	private void vanta$unfocused(CallbackInfoReturnable<Integer> cir) {
		int limit = BackgroundFpsModule.apply(cir.getReturnValue());
		if (limit != cir.getReturnValue()) cir.setReturnValue(limit);
	}
}
