package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import vanta.menu.MenuBackground;
import vanta.menu.MenuStyleModule;

/**
 * Menu Style: replaces the background of styled vanilla menus (Screen.renderBackground)
 * with the VANTA background. Widgets are untouched here.
 */
@Mixin(Screen.class)
abstract class ScreenMixin {
	@Shadow
	public int width;
	@Shadow
	public int height;
	@Shadow
	protected Minecraft minecraft;

	@Shadow
	protected abstract void renderPanorama(GuiGraphics graphics, float a);

	@Shadow
	protected abstract void renderBlurredBackground(GuiGraphics graphics);

	@Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
	private void vanta$background(GuiGraphics graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		Screen self = (Screen) (Object) this;
		if (!MenuStyleModule.styles(self)) return;
		MenuStyleModule m = MenuStyleModule.get();
		if (minecraft.level == null) {
			if (MenuBackground.wantsPanorama()) renderPanorama(graphics, a);
			if (m.keepBlur.on()) renderBlurredBackground(graphics);
			MenuBackground.outOfWorld(graphics, width, height);
		} else {
			if (m.keepBlur.on()) renderBlurredBackground(graphics);
			MenuBackground.inWorld(graphics, width, height, m.dim.value() / 100f, 1f);
		}
		minecraft.gui.renderDeferredSubtitles();
		ci.cancel();
	}
}
