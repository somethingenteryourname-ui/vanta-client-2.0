package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.renderer.LevelRenderer;

import vanta.modules.impl.BlockOutlineModule;

/**
 * Block Outline: changes the color and width passed to LevelRenderer.renderHitOutline.
 * The thick black high-contrast pass (width 7) is left alone.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
	@ModifyVariable(method = "renderHitOutline", at = @At("HEAD"), argsOnly = true)
	private int vanta$outlineColor(int color) {
		return BlockOutlineModule.active() && color != 0xFF000000 ? BlockOutlineModule.outlineColor(color) : color;
	}

	@ModifyVariable(method = "renderHitOutline", at = @At("HEAD"), argsOnly = true)
	private float vanta$outlineWidth(float width) {
		return BlockOutlineModule.active() && width != 7.0f ? BlockOutlineModule.outlineWidth(width) : width;
	}
}
