package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import vanta.performance.EntityCullingModule;

/** Entity Culling: skips far-away chests, signs, banners and heads. */
@Mixin(BlockEntityRenderDispatcher.class)
abstract class BlockEntityRenderDispatcherMixin {
	@Shadow
	private Vec3 cameraPos;

	@Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
	private void vanta$cull(BlockEntity blockEntity, float partialTicks, ModelFeatureRenderer.CrumblingOverlay breakProgress,
			CallbackInfoReturnable<BlockEntityRenderState> cir) {
		Vec3 cam = cameraPos;
		if (cam != null && EntityCullingModule.cullBlockEntity(blockEntity, cam.x, cam.y, cam.z)) cir.setReturnValue(null);
	}
}
