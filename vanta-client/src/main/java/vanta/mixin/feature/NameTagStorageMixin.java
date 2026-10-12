package vanta.mixin.feature;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;

import vanta.modules.impl.NametagsModule;

/** Nametags: background opacity and size of every nametag. */
@Mixin(NameTagFeatureRenderer.Storage.class)
abstract class NameTagStorageMixin {
	@ModifyExpressionValue(method = "add", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getBackgroundOpacity(F)F"))
	private float vanta$opacity(float original) {
		float o = NametagsModule.backgroundOpacity();
		return o < 0f ? original : o;
	}

	@WrapOperation(method = "add", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
	private void vanta$scale(PoseStack stack, float x, float y, float z, Operation<Void> original) {
		float s = NametagsModule.scaleFactor();
		original.call(stack, x * s, y * s, z * s);
	}
}
