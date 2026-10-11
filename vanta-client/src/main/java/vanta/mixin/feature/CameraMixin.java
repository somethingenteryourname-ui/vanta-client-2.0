package vanta.mixin.feature;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.Camera;

import vanta.modules.impl.FreelookModule;

/** Freelook: the camera uses the freelook angles instead of the player's. Target: Camera.setup. */
@Mixin(Camera.class)
abstract class CameraMixin {
	@ModifyExpressionValue(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
	private float vanta$yaw(float original) {
		return FreelookModule.isActive() ? FreelookModule.yaw() : original;
	}

	@ModifyExpressionValue(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
	private float vanta$pitch(float original) {
		return FreelookModule.isActive() ? FreelookModule.pitch() : original;
	}
}
