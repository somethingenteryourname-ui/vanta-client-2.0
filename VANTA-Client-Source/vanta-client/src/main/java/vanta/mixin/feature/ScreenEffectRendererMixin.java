package vanta.mixin.feature;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import vanta.modules.impl.ScreenEffectsModule;
import vanta.modules.impl.TotemModule;

/** Low/hidden fire overlay and the Totem Customizer pop animation. Targets verified in 1.21.11 ScreenEffectRenderer. */
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
		if (TotemModule.scale3d() <= 0f) ci.cancel(); // hidden, or drawn flat on the HUD instead
	}

	@WrapOperation(method = "renderItemActivationAnimation",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
	private void vanta$totemScale(PoseStack stack, float x, float y, float z, Operation<Void> original) {
		float s = TotemModule.scale3d();
		original.call(stack, x * s, y * s, z * s);
	}

	/** Center position: drop the random sideways drift (first translate). */
	@WrapOperation(method = "renderItemActivationAnimation",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0))
	private void vanta$totemPosition(PoseStack stack, float x, float y, float z, Operation<Void> original) {
		if (TotemModule.centered()) original.call(stack, 0f, 0f, z);
		else original.call(stack, x, y, z);
	}

	@ModifyConstant(method = "renderItemActivationAnimation", constant = @Constant(floatValue = 900.0F))
	private float vanta$totemSpin(float spin) {
		return spin * TotemModule.spinFactor();
	}

	@ModifyConstant(method = "renderItemActivationAnimation", constant = @Constant(intValue = 40))
	private int vanta$totemLengthTick(int ticks) {
		return TotemModule.ticks();
	}

	@ModifyConstant(method = "renderItemActivationAnimation", constant = @Constant(floatValue = 40.0F))
	private float vanta$totemLengthScale(float ticks) {
		return TotemModule.ticks();
	}

	@ModifyConstant(method = "displayItemActivation", constant = @Constant(intValue = 40))
	private int vanta$totemLength(int ticks) {
		return TotemModule.ticks();
	}
}
