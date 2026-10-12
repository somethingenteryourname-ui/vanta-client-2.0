package vanta.menu;

import net.minecraft.client.gui.GuiGraphics;

import vanta.branding.Theme;
import vanta.modules.ModuleRegistry;
import vanta.modules.impl.TitleScreenModule;
import vanta.ui.Colors;

/** Backgrounds shared by the home screen, the VANTA menu and styled vanilla menus. */
public final class MenuBackground {
	private MenuBackground() {
	}

	/**
	 * Out-of-world background, following the Home Screen background setting.
	 * Returns true if the caller should draw the vanilla panorama first.
	 */
	public static boolean wantsPanorama() {
		TitleScreenModule c = ModuleRegistry.TITLE_SCREEN;
		return c == null || c.background.get() == TitleScreenModule.Background.PANORAMA;
	}

	/** Draws everything except the panorama itself (call after the panorama if wantsPanorama()). */
	public static void outOfWorld(GuiGraphics g, int width, int height) {
		TitleScreenModule c = ModuleRegistry.TITLE_SCREEN;
		TitleScreenModule.Background bg = c == null ? TitleScreenModule.Background.DARK : c.background.get();
		switch (bg) {
			case PANORAMA -> {
			}
			case DARK -> g.fill(0, 0, width, height, Theme.background());
			case GRADIENT -> gradient(g, width, height);
			case ANIMATED -> animated(g, width, height);
			case LAST_WORLD -> {
				if (!BackgroundImages.drawLastWorld(g, width, height)) gradient(g, width, height);
			}
			case CUSTOM_IMAGE -> {
				if (!BackgroundImages.drawCustom(g, width, height)) gradient(g, width, height);
			}
		}
		int dim = c == null ? 30 : c.backgroundDim.value();
		if (dim > 0) g.fill(0, 0, width, height, Colors.withAlpha(0xFF000000, dim / 100f));
	}

	private static void gradient(GuiGraphics g, int width, int height) {
		g.fill(0, 0, width, height, Theme.background());
		g.fillGradient(0, 0, width, height, Colors.withAlpha(Theme.accent(), 0.22f), Colors.withAlpha(Theme.secondary(), 0.06f));
	}

	/** A few slow, translucent bands: cheap and static with Reduced motion. */
	public static void animated(GuiGraphics g, int width, int height) {
		g.fill(0, 0, width, height, Theme.background());
		double t = Theme.reducedMotion() ? 0 : (System.currentTimeMillis() % 60000L) / 60000.0;
		for (int i = 0; i < 4; i++) {
			double phase = (t + i * 0.25) % 1.0;
			int y = (int) (phase * (height + 120)) - 60;
			int color = Colors.withAlpha(i % 2 == 0 ? Theme.accent() : Theme.secondary(), 0.05f);
			g.fillGradient(0, y - 40, width, y, 0x00000000, color);
			g.fillGradient(0, y, width, y + 40, color, 0x00000000);
		}
	}

	/** In-world dim plus the chosen backdrop pattern. */
	public static void inWorld(GuiGraphics g, int width, int height, float dim, float strength) {
		if (dim > 0) g.fill(0, 0, width, height, Colors.withAlpha(Theme.background(), dim * strength));
		backdrop(g, width, height, strength);
	}

	public static void backdrop(GuiGraphics g, int width, int height, float strength) {
		int faint = Colors.withAlpha(Theme.text(), 0.035f * strength);
		switch (Theme.BACKDROP.get()) {
			case PLAIN -> {
			}
			case GRID -> {
				for (int x = 0; x < width; x += 16) g.fill(x, 0, x + 1, height, faint);
				for (int y = 0; y < height; y += 16) g.fill(0, y, width, y + 1, faint);
			}
			case DOTS -> {
				int dot = Colors.withAlpha(Theme.text(), 0.08f * strength);
				for (int x = 8; x < width; x += 16) {
					for (int y = 8; y < height; y += 16) g.fill(x, y, x + 1, y + 1, dot);
				}
			}
			case GRADIENT -> g.fillGradient(0, 0, width, height, Colors.withAlpha(Theme.accent(), 0.16f * strength), Colors.withAlpha(Theme.secondary(), 0.03f * strength));
			case VIGNETTE -> {
				int edge = Colors.withAlpha(0xFF000000, 0.55f * strength);
				g.fillGradient(0, 0, width, height / 4, edge, 0);
				g.fillGradient(0, height - height / 4, width, height, 0, edge);
			}
		}
	}
}
