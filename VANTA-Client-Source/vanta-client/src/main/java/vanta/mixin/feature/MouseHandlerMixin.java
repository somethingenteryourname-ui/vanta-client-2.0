package vanta.mixin.feature;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;

import vanta.modules.impl.FreelookModule;
import vanta.modules.impl.ZoomModule;

/**
 * Freelook redirects mouse movement to the camera; Zoom lowers sensitivity and uses the
 * scroll wheel. Targets: MouseHandler.turnPlayer(double) and onScroll(long, double, double).
 */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
	@WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void vanta$turn(LocalPlayer player, double xo, double yo, Operation<Void> original) {
		if (FreelookModule.isActive()) {
			FreelookModule.turn(xo, yo);
			return;
		}
		double s = ZoomModule.sensitivityScale();
		original.call(player, xo * s, yo * s);
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void vanta$zoomScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen == null && mc.player != null && ZoomModule.onScroll(yoffset)) ci.cancel();
	}
}
