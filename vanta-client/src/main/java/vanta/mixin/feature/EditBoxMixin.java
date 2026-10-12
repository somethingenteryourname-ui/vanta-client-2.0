package vanta.mixin.feature;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.resources.Identifier;

import vanta.menu.MenuStyle;

/** Menu Style: VANTA-styled text boxes (search, world name, server address...). */
@Mixin(EditBox.class)
abstract class EditBoxMixin {
	@WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
	private void vanta$field(GuiGraphics g, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h, Operation<Void> original) {
		if (!MenuStyle.active()) {
			original.call(g, pipeline, sprite, x, y, w, h);
			return;
		}
		EditBox self = (EditBox) (Object) this;
		MenuStyle.textField(g, x, y, w, h, self.isFocused(), self.isActive());
	}
}
