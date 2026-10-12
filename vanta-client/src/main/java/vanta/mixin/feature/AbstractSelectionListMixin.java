package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.layouts.LayoutElement;

import vanta.menu.MenuStyle;

/**
 * Menu Style: world list, server list and option lists get a VANTA panel, accent
 * separators, a themed selection and a hover highlight. Entries (icons, names, ping
 * bars) are still drawn by vanilla.
 */
@Mixin(AbstractSelectionList.class)
abstract class AbstractSelectionListMixin {
	@Inject(method = "renderListBackground", at = @At("HEAD"), cancellable = true)
	private void vanta$background(GuiGraphics g, CallbackInfo ci) {
		if (!MenuStyle.active()) return;
		AbstractSelectionList<?> self = (AbstractSelectionList<?>) (Object) this;
		MenuStyle.listBackground(g, self.getX(), self.getY(), self.getWidth(), self.getHeight());
		ci.cancel();
	}

	@Inject(method = "renderListSeparators", at = @At("HEAD"), cancellable = true)
	private void vanta$separators(GuiGraphics g, CallbackInfo ci) {
		if (!MenuStyle.active()) return;
		AbstractSelectionList<?> self = (AbstractSelectionList<?>) (Object) this;
		MenuStyle.listSeparators(g, self.getX(), self.getY(), self.getBottom(), self.getWidth());
		ci.cancel();
	}

	@Inject(method = "renderSelection", at = @At("HEAD"), cancellable = true)
	private void vanta$selection(GuiGraphics g, @Coerce LayoutElement entry, int outlineColor, CallbackInfo ci) {
		if (!MenuStyle.active()) return;
		MenuStyle.listSelection(g, entry.getX(), entry.getY(), entry.getWidth(), entry.getHeight(), outlineColor == -1);
		ci.cancel();
	}

	@Inject(method = "renderItem", at = @At("HEAD"))
	private void vanta$hover(GuiGraphics g, int mouseX, int mouseY, float a, @Coerce LayoutElement entry, CallbackInfo ci) {
		if (!MenuStyle.active()) return;
		AbstractSelectionList<?> self = (AbstractSelectionList<?>) (Object) this;
		if (self.getSelected() == (Object) entry) return;
		int x = entry.getX(), y = entry.getY(), w = entry.getWidth(), h = entry.getHeight();
		if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h && self.isMouseOver(mouseX, mouseY)) {
			MenuStyle.listHover(g, x, y, w, h);
		}
	}
}
