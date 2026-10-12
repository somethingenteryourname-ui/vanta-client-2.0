package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;

import vanta.optimize.OptimizationsModule;

/** Optimizations: the camera jumps straight to the new eye height (sneaking) instead of easing there. */
@Mixin(Camera.class)
abstract class InstantSneakMixin {
	@Shadow private float eyeHeight;
	@Shadow private float eyeHeightOld;
	@Shadow private Entity entity;

	@Inject(method = "tick", at = @At("TAIL"))
	private void vanta$instantSneak(CallbackInfo ci) {
		if (OptimizationsModule.instantSneak() && entity != null) {
			eyeHeight = entity.getEyeHeight();
			eyeHeightOld = eyeHeight;
		}
	}
}
