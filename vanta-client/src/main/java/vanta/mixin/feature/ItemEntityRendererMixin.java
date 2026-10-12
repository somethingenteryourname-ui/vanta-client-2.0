package vanta.mixin.feature;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.phys.AABB;

import vanta.modules.impl.ItemPhysicsModule;

/**
 * Item Physics: replaces the bob (first translate) and the spin (first rotation) in
 * ItemEntityRenderer.submit with a resting pose. Everything else (stack count, lighting,
 * outline) is vanilla.
 */
@Mixin(ItemEntityRenderer.class)
abstract class ItemEntityRendererMixin {
	private static boolean vanta$flat(AABB box) {
		return box.getZsize() <= 0.0625 + 1.0E-4 || !ItemPhysicsModule.blocksUpright();
	}

	@WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0))
	private void vanta$rest(PoseStack stack, float x, float y, float z, Operation<Void> original, @Local(argsOnly = true) ItemEntityRenderState state,
			@Local AABB box) {
		if (!ItemPhysicsModule.active()) {
			original.call(stack, x, y, z);
			return;
		}
		if (vanta$flat(box)) {
			// After the 90 degree tilt below, the model's depth becomes its height.
			float pile = state.count > 1 ? (float) box.getZsize() * 1.5f * (state.count - 1) / 2f : 0f;
			original.call(stack, 0f, (float) box.maxZ + 0.01f + pile, 0f);
		} else {
			original.call(stack, 0f, -(float) box.minY, 0f);
		}
	}

	@WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V", ordinal = 0))
	private void vanta$noSpin(PoseStack stack, Quaternionfc rotation, Operation<Void> original, @Local(argsOnly = true) ItemEntityRenderState state,
			@Local AABB box) {
		if (!ItemPhysicsModule.active()) {
			original.call(stack, rotation);
			return;
		}
		original.call(stack, Axis.YP.rotation(state.bobOffset * 2f)); // fixed, random-looking direction per item
		if (vanta$flat(box)) {
			original.call(stack, Axis.XP.rotationDegrees(90f));
			stack.translate(0f, (float) -(box.minY + box.maxY) / 2f, 0f); // center the item where it lies
		}
	}
}
