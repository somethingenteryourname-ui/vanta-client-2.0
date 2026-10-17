package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.client.renderer.LightTexture;

import vanta.modules.impl.FullbrightModule;

/**
 * Fullbright: raises the night-vision factor written to the lightmap uniform buffer.
 * In 1.21.11 updateLightTexture writes ambient, sky, block, nightVision, ... in that
 * order, so the 4th putFloat (ordinal 3) is night vision.
 */
@Mixin(LightTexture.class)
abstract class LightTextureMixin {
	@ModifyArg(method = "updateLightTexture",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;", ordinal = 3))
	private float vanta$fullbright(float nightVision) {
		return FullbrightModule.apply(nightVision);
	}
}
