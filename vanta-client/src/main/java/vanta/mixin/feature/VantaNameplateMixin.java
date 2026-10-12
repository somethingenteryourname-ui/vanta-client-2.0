package vanta.mixin.feature;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import net.minecraft.network.chat.Component;

import vanta.modules.impl.ClientBadgeModule;

/**
 * Client Badge: nametags of VANTA players (the ones carrying the VANTA logo) get a plate
 * tinted with your theme instead of plain black. Only the background color changes.
 */
@Mixin(NameTagFeatureRenderer.Storage.class)
abstract class VantaNameplateMixin {
	@WrapOperation(method = "add", at = @At(value = "NEW",
			target = "(Lorg/joml/Matrix4f;FFLnet/minecraft/network/chat/Component;IIID)Lnet/minecraft/client/renderer/SubmitNodeStorage$NameTagSubmit;"))
	private SubmitNodeStorage.NameTagSubmit vanta$plate(Matrix4f pose, float x, float y, Component text, int light, int color, int background,
			double distance, Operation<SubmitNodeStorage.NameTagSubmit> original) {
		return original.call(pose, x, y, text, light, color, ClientBadgeModule.plateColor(text, background), distance);
	}
}
