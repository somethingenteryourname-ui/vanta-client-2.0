package vanta.mixin.feature;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.resources.Identifier;

import vanta.menu.MenuStyle;

/** Menu Style: VANTA-styled slider track (first sprite) and handle (second sprite). */
@Mixin(AbstractSliderButton.class)
abstract class AbstractSliderButtonMixin {
	private static final String BLIT = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V";

	@WrapOperation(method = "renderWidget", at = @At(value = "INVOKE", target = BLIT, ordinal = 0))
	private void vanta$track(GuiGraphics g, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h, int color, Operation<Void> original) {
		if (!MenuStyle.active()) {
			original.call(g, pipeline, sprite, x, y, w, h, color);
			return;
		}
		AbstractSliderButton self = (AbstractSliderButton) (Object) this;
		MenuStyle.sliderTrack(g, x, y, w, h, self.isHoveredOrFocused(), self.getAlpha());
	}

	@WrapOperation(method = "renderWidget", at = @At(value = "INVOKE", target = BLIT, ordinal = 1))
	private void vanta$handle(GuiGraphics g, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h, int color, Operation<Void> original) {
		if (!MenuStyle.active()) {
			original.call(g, pipeline, sprite, x, y, w, h, color);
			return;
		}
		AbstractSliderButton self = (AbstractSliderButton) (Object) this;
		MenuStyle.sliderHandle(g, x, y, w, h, self.isHoveredOrFocused(), self.getAlpha());
	}
}
