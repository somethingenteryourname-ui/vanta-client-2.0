package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;

import vanta.menu.MenuStyle;

/**
 * Menu Style: draws the button background (AbstractButton.renderDefaultSprite) in the
 * VANTA style. The label is still drawn by vanilla, so every button, including ones
 * added by other mods, keeps its text, scrolling and tooltip.
 */
@Mixin(AbstractButton.class)
abstract class AbstractButtonMixin {
	@Inject(method = "renderDefaultSprite", at = @At("HEAD"), cancellable = true)
	private void vanta$styledButton(GuiGraphics graphics, CallbackInfo ci) {
		if (!MenuStyle.active()) return;
		AbstractButton self = (AbstractButton) (Object) this;
		MenuStyle.button(graphics, self.getX(), self.getY(), self.getWidth(), self.getHeight(),
				self.isActive(), self.isHoveredOrFocused(), self.getAlpha());
		ci.cancel();
	}
}
