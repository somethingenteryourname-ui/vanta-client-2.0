package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;

import vanta.performance.EntityCullingModule;

/** Entity Culling: far-away clutter is skipped before any rendering work is done. */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderDispatcherMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void vanta$cull(Entity entity, Frustum culler, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
		if (EntityCullingModule.cull(entity, camX, camY, camZ)) cir.setReturnValue(false);
	}
}
