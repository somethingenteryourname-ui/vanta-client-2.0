package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;

import vanta.modules.impl.InfoPanelModule;

/** Clean F3: skip the vanilla debug text while F3 is open; VANTA draws its panel instead. */
@Mixin(DebugScreenOverlay.class)
abstract class DebugScreenOverlayMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void vanta$cleanF3(GuiGraphics graphics, CallbackInfo ci) {
		if (InfoPanelModule.replacesF3()) {
			InfoPanelModule.renderIfOpen(graphics);
			ci.cancel();
		}
	}
}
