package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import vanta.modules.impl.ViewModelModule;

/** Held Item View and Totem Customizer: position and size of first-person held items. */
@Mixin(ItemInHandRenderer.class)
abstract class ItemInHandRendererMixin {
	@WrapOperation(method = "renderArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
	private void vanta$viewModel(ItemInHandRenderer self, LivingEntity mob, ItemStack stack, ItemDisplayContext context, PoseStack pose,
			SubmitNodeCollector collector, int light, Operation<Void> original) {
		pose.pushPose();
		ViewModelModule.apply(pose, stack, context);
		original.call(self, mob, stack, context, pose, collector, light);
		pose.popPose();
	}
}
