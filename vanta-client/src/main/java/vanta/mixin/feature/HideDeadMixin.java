package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;

import vanta.optimize.OptimizationsModule;

/** Optimizations: hide dead entities instead of playing their death animation. */
@Mixin(EntityRenderDispatcher.class)
abstract class HideDeadMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void vanta$hideDead(Entity entity, Frustum culler, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
		if (OptimizationsModule.hideIfDead(Minecraft.getInstance(), entity)) cir.setReturnValue(false);
	}
}
