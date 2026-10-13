package vanta.mixin.feature;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;

import vanta.cursor.CursorModule;

/** Custom Cursor: swaps Minecraft's arrow and pointing hand for the VANTA ones. Text and resize cursors stay. */
@Mixin(CursorType.class)
abstract class CursorTypeMixin {
	@Inject(method = "select", at = @At("HEAD"), cancellable = true)
	private void vanta$cursor(Window window, CallbackInfo ci) {
		Object self = this;
		long custom = 0L;
		if (self == CursorType.DEFAULT || self == CursorTypes.ARROW) custom = CursorModule.arrowHandle();
		else if (self == CursorTypes.POINTING_HAND) custom = CursorModule.handHandle();
		if (custom != 0L) {
			GLFW.glfwSetCursor(window.handle(), custom);
			ci.cancel();
		}
	}
}
