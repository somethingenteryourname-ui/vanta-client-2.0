package vanta.gui.items;

import net.minecraft.client.gui.GuiGraphics;

import vanta.branding.Theme;
import vanta.crosshair.CrosshairModule;
import vanta.crosshair.CrosshairRenderer;
import vanta.ui.Colors;
import vanta.ui.Ui;

/** Live crosshair preview on light and dark backgrounds, at 1x and 2x. */
public final class CrosshairPreview extends Item {
	private final CrosshairModule module;

	public CrosshairPreview(CrosshairModule module) {
		this.module = module;
	}

	@Override
	public void render(GuiGraphics g, int ax, int ay, int mx, int my, boolean hovered) {
		Ui.card(g, ax, ay, w, h, false, 0);
		int half = w / 2;
		// Left: sky-like light background. Right: dark cave-like background.
		g.fill(ax + 6, ay + 6, ax + half - 3, ay + h - 6, 0xFF87A9D9);
		g.fill(ax + half + 3, ay + 6, ax + w - 6, ay + h - 6, 0xFF202326);
		int cy = ay + h / 2;
		CrosshairRenderer.draw(g, module, ax + half / 2, cy, true);
		g.pose().pushMatrix();
		g.pose().translate(ax + half + half / 2f, cy);
		g.pose().scale(2f);
		CrosshairRenderer.draw(g, module, 0, 0, true);
		g.pose().popMatrix();
		Ui.text(g, "1x", ax + 10, ay + h - 16, Colors.withAlpha(0xFF000000, 0.6f));
		Ui.text(g, "2x", ax + half + 7, ay + h - 16, Colors.withAlpha(0xFFFFFFFF, 0.6f));
		if (!module.isEnabled()) Ui.textCentered(g, "Custom crosshair is off", ax + w / 2, ay + 8, Theme.textDim());
	}

	@Override public String hint() { return "Live preview. Changes apply in game immediately."; }
}
