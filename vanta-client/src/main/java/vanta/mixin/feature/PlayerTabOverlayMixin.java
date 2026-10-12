package vanta.mixin.feature;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import vanta.branding.Theme;
import vanta.combat.TotemTracker;
import vanta.modules.impl.ClientBadgeModule;
import vanta.modules.impl.TabListModule;
import vanta.modules.impl.TotemPopsModule;
import vanta.social.VantaUsers;

/**
 * Tab list: ping numbers instead of bars, a themed -N totem pop count next to the ping,
 * the VANTA logo before VANTA players' names, extra width for all that, and theme colors.
 */
@Mixin(PlayerTabOverlay.class)
abstract class PlayerTabOverlayMixin {
	@ModifyConstant(method = "render", constant = @Constant(intValue = 13))
	private int vanta$widerSlots(int original) {
		return original + TabListModule.extraWidth() + TotemPopsModule.tabExtraWidth();
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
	private void vanta$colors(GuiGraphics g, int x0, int y0, int x1, int y1, int color, Operation<Void> original) {
		original.call(g, x0, y0, x1, y1, TabListModule.recolor(color));
	}

	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
	private void vanta$badge(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
		if (vanta.tiers.TierTaggerModule.inTabList() && cir.getReturnValue() != null) {
			cir.setReturnValue(vanta.tiers.TierTaggerModule.decorate(info.getProfile().id(), info.getProfile().name(), cir.getReturnValue()));
		}
		if (ClientBadgeModule.inTabList() && cir.getReturnValue() != null && ClientBadgeModule.badgeFor(info.getProfile().id())) {
			cir.setReturnValue(VantaUsers.withBadge(cir.getReturnValue()));
		}
	}

	@Inject(method = "renderPingIcon", at = @At("HEAD"), cancellable = true)
	private void vanta$pingAndPops(GuiGraphics g, int slotWidth, int xo, int yo, PlayerInfo info, CallbackInfo ci) {
		boolean numbers = TabListModule.pingNumbers();
		int pops = TotemPopsModule.inTab() ? TotemTracker.pops(info.getProfile().id()) : 0;
		if (!numbers && pops == 0) return;
		Minecraft mc = Minecraft.getInstance();
		int right = xo + slotWidth - 1;
		if (numbers) {
			int ms = info.getLatency();
			String text = ms < 0 ? "?" : String.valueOf(ms);
			int w = mc.font.width(text);
			g.drawString(mc.font, text, right - w, yo, TabListModule.pingColor(ms), true);
			right -= w + 3;
		} else {
			right = xo + slotWidth - 13; // left of vanilla's 10 px ping bars
		}
		if (pops > 0) {
			String text = "-" + pops;
			g.drawString(mc.font, text, right - mc.font.width(text), yo, Theme.accent() | 0xFF000000, true);
		}
		if (numbers) ci.cancel();
	}
}
