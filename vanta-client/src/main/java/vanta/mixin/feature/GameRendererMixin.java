package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;

import vanta.modules.impl.ZoomModule;

/** Zoom: scales the final field of view. Target: GameRenderer.getFov(Camera, float, boolean) in 1.21.11. */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void vanta$zoom(Camera camera, float partialTicks, boolean applyEffects, CallbackInfoReturnable<Float> cir) {
		float m = ZoomModule.fovMultiplier();
		if (m != 1f) cir.setReturnValue(cir.getReturnValue() * m);
	}
}
