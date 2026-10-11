package vanta.mixin.feature;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import vanta.modules.impl.ScreenEffectsModule;

/** Low/hidden fire overlay and totem animation size. Targets verified in 1.21.11 ScreenEffectRenderer. */
@Mixin(ScreenEffectRenderer.class)
abstract class ScreenEffectRendererMixin {
	@Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
	private static void vanta$fireStart(PoseStack poseStack, MultiBufferSource bufferSource, TextureAtlasSprite sprite, CallbackInfo ci) {
		if (ScreenEffectsModule.hideFire()) {
			ci.cancel();
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0f, -ScreenEffectsModule.fireOffset(), 0f);
	}

	@Inject(method = "renderFire", at = @At("RETURN"))
	private static void vanta$fireEnd(PoseStack poseStack, MultiBufferSource bufferSource, TextureAtlasSprite sprite, CallbackInfo ci) {
		poseStack.popPose();
	}

	@Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true)
	private void vanta$hideTotem(PoseStack poseStack, float partialTicks, SubmitNodeCollector collector, CallbackInfo ci) {
		if (ScreenEffectsModule.totemScale() <= 0f) ci.cancel();
	}

	@WrapOperation(method = "renderItemActivationAnimation",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
	private void vanta$totemScale(PoseStack stack, float x, float y, float z, Operation<Void> original) {
		float s = ScreenEffectsModule.totemScale();
		original.call(stack, x * s, y * s, z * s);
	}
}
