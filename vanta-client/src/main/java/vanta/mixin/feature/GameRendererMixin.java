package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

import vanta.menu.BackgroundImages;
import vanta.modules.impl.ZoomModule;

/** Target: GameRenderer in 1.21.11. */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
	/** Zoom: scales the final field of view. */
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void vanta$zoom(Camera camera, float partialTicks, boolean applyEffects, CallbackInfoReturnable<Float> cir) {
		float m = ZoomModule.fovMultiplier();
		if (m != 1f) cir.setReturnValue(cir.getReturnValue() * m);
	}

	/** Last-world background: the world is drawn but the HUD and menus are not yet. */
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;tryTakeScreenshotIfNeeded()V", shift = At.Shift.AFTER))
	private void vanta$afterWorld(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
		try {
			BackgroundImages.afterWorldRendered(Minecraft.getInstance());
		} catch (RuntimeException ignored) {
			// never break rendering for a background picture
		}
	}
}
