package vanta.loading;

import net.minecraft.client.gui.GuiGraphics;

import vanta.branding.Logo;
import vanta.branding.Theme;
import vanta.config.BoolSetting;
import vanta.menu.MenuBackground;
import vanta.modules.Category;
import vanta.modules.Module;
import vanta.ui.Colors;
import vanta.ui.Ui;

/**
 * VANTA loading screens: joining a server and loading a world show your logo animation,
 * a themed progress bar and a tip, on your home screen background.
 */
public final class LoadingScreenModule extends Module {
	private static LoadingScreenModule instance;

	public final BoolSetting tips = setting(new BoolSetting(key("tips"), "Tips", "Show a VANTA tip under the progress bar.", true));
	public final BoolSetting chunkMap = setting(new BoolSetting(key("chunk_map"), "Chunk loading map",
			"Show Minecraft's colored map of loading chunks (singleplayer).", false));
	public final BoolSetting portals = setting(new BoolSetting(key("portals"), "Keep portal animation",
			"When you go through a nether or end portal, keep Minecraft's portal swirl instead.", true));

	private static final String[] TIPS = {
			"Press Right Shift to open the VANTA menu.",
			"Ctrl+F in chat searches every message.",
			"Right-click a chat message to copy it.",
			"Type :skull: or :heart: in chat for symbols.",
			"Press B in game to add a waypoint.",
			"Drag widgets around in the HUD editor.",
			"Profiles save your whole setup. Switch any time.",
			"The Crosshair editor has templates and sliders.",
			"Totem Customizer makes pops smaller or see-through.",
			"Friends get a highlight in chat and the tab list.",
			"Menu FPS Cap keeps your PC cool in menus.",
			"Use /resetcounter to clear totem pops."};

	public LoadingScreenModule() {
		super("loading_screen", "Loading Screens", "Joining servers and loading worlds show your animated logo, a themed "
				+ "progress bar and tips.", Category.INTERFACE, true);
		instance = this;
	}

	public static LoadingScreenModule active() { return instance != null && instance.isEnabled() ? instance : null; }

	/**
	 * Draws a full VANTA loading screen.
	 *
	 * @param progress 0..1, or below 0 for "working on it" with no known progress
	 * @return the y just below the progress bar (for extras like the chunk map)
	 */
	public static int draw(GuiGraphics g, int width, int height, String status, float progress, long start, boolean background) {
		LoadingScreenModule m = instance;
		if (background) {
			if (MenuBackground.wantsPanorama()) MenuBackground.animated(g, width, height);
			else MenuBackground.outOfWorld(g, width, height);
		}
		int size = Math.max(16, Math.min(34, height / 9));
		int lw = Logo.width(size, true);
		int cy = height / 2 - size;
		Logo.draw(g, (width - lw) / 2, cy - size / 2, size, true, start);
		int ty = cy + size / 2 + 14;
		Ui.textCentered(g, status, width / 2, ty, Theme.text() | 0xFF000000);
		int bw = Math.min(220, width - 40), bx = (width - bw) / 2, by = ty + 14;
		Ui.roundRect(g, bx, by, bw, 4, 2, Colors.withAlpha(Theme.text(), 0.12f));
		if (progress >= 0f) {
			int fw = Math.max(4, Math.round(bw * Math.min(1f, progress)));
			Ui.roundRect(g, bx, by, fw, 4, 2, Theme.accent() | 0xFF000000);
			Ui.hGradient(g, bx + 2, by + 1, Math.max(0, fw - 4), 2, Theme.accent() | 0xFF000000, Theme.secondary() | 0xFF000000);
			Ui.textCentered(g, Math.round(progress * 100) + "%", width / 2, by + 8, Theme.textDim() | 0xFF000000);
		} else {
			float t = Theme.reducedMotion() ? 0.35f : (System.currentTimeMillis() % 1400L) / 1400f;
			int seg = bw / 3;
			int sx = bx + Math.round((bw + seg) * t) - seg;
			int x0 = Math.max(bx, sx), x1 = Math.min(bx + bw, sx + seg);
			if (x1 > x0) Ui.roundRect(g, x0, by, x1 - x0, 4, 2, Theme.accent() | 0xFF000000);
		}
		if (m != null && m.tips.on()) {
			String tip = "Tip: " + TIPS[(int) ((start / 1000 + System.currentTimeMillis() / 6000) % TIPS.length)];
			Ui.textCentered(g, tip, width / 2, height - 22, Theme.textFaint() | 0xFF000000);
		}
		return by + 22;
	}
}
