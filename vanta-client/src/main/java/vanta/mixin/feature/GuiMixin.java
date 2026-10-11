package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

import vanta.modules.impl.ScreenEffectsModule;

/** Hides the pumpkin blur, powder snow frost and portal swirl overlays when chosen. */
@Mixin(Gui.class)
abstract class GuiMixin {
	@Inject(method = "renderTextureOverlay", at = @At("HEAD"), cancellable = true)
	private void vanta$hideOverlay(GuiGraphics graphics, Identifier texture, float alpha, CallbackInfo ci) {
		if (ScreenEffectsModule.hideOverlay(texture.getPath())) ci.cancel();
	}

	@Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
	private void vanta$hidePortal(GuiGraphics graphics, float alpha, CallbackInfo ci) {
		if (ScreenEffectsModule.hidePortal()) ci.cancel();
	}
}
