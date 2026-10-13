package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import vanta.loading.LoadingScreenModule;

/** Loading Screens: "Connecting..." shows the VANTA logo, status and a moving bar. */
@Mixin(ConnectScreen.class)
abstract class ConnectScreenMixin {
	@Unique
	private long vanta$start;

	@WrapOperation(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
	private void vanta$status(GuiGraphics graphics, Font font, Component status, int x, int y, int color, Operation<Void> original) {
		if (LoadingScreenModule.active() == null) {
			original.call(graphics, font, status, x, y, color);
			return;
		}
		if (vanta$start == 0) vanta$start = System.currentTimeMillis();
		Screen self = (Screen) (Object) this;
		LoadingScreenModule.draw(graphics, self.width, self.height, status.getString(), -1f, vanta$start, false);
	}
}
