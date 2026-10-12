package vanta.mixin.feature;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;

import vanta.modules.impl.TabListModule;

/** Tab List: ping numbers instead of bars, a bit of extra width for them, and theme colors. */
@Mixin(PlayerTabOverlay.class)
abstract class PlayerTabOverlayMixin {
	@ModifyConstant(method = "render", constant = @Constant(intValue = 13))
	private int vanta$widerSlots(int original) {
		return original + TabListModule.extraWidth();
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
	private void vanta$colors(GuiGraphics g, int x0, int y0, int x1, int y1, int color, Operation<Void> original) {
		original.call(g, x0, y0, x1, y1, TabListModule.recolor(color));
	}

	@Inject(method = "renderPingIcon", at = @At("HEAD"), cancellable = true)
	private void vanta$pingNumber(GuiGraphics g, int slotWidth, int xo, int yo, PlayerInfo info, CallbackInfo ci) {
		if (!TabListModule.pingNumbers()) return;
		int ms = info.getLatency();
		String text = ms < 0 ? "?" : String.valueOf(ms);
		Minecraft mc = Minecraft.getInstance();
		int w = mc.font.width(text);
		g.drawString(mc.font, text, xo + slotWidth - w - 1, yo, TabListModule.pingColor(ms), true);
		ci.cancel();
	}
}
