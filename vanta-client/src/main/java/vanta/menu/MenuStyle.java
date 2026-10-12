package vanta.menu;

import java.lang.ref.WeakReference;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import vanta.branding.Theme;
import vanta.ui.Colors;
import vanta.ui.Ui;

/**
 * Draws vanilla widgets (buttons, sliders, text boxes, lists) in the VANTA style while a
 * styled menu is open. Vanilla still draws every label, icon and list entry, so
 * scrolling text, tooltips and mod-added widgets all keep working.
 */
public final class MenuStyle {
	private static WeakReference<Screen> lastScreen = new WeakReference<>(null);
	private static boolean lastResult;
	private static long lastCheck;

	private MenuStyle() {
	}

	/** True if the screen currently open should be drawn in the VANTA style. Cached per screen. */
	public static boolean active() {
		Minecraft mc = Minecraft.getInstance();
		Screen s = mc == null ? null : mc.screen;
		if (s == null) return false;
		long now = System.currentTimeMillis();
		if (lastScreen.get() != s || now - lastCheck > 250) {
			lastScreen = new WeakReference<>(s);
			lastResult = MenuStyleModule.styles(s);
			lastCheck = now;
		}
		return lastResult;
	}

	/** Vanilla labels are white, so buttons always use a dark fill even with a light theme. */
	private static int darkFill(int color) {
		return Colors.contrastText(color) == 0xFFFFFFFF ? color : Colors.mix(color, 0xFF15181F, 0.85f);
	}

	public static void button(GuiGraphics g, int x, int y, int w, int h, boolean active, boolean hovered, float alpha) {
		int r = Math.min(Theme.radius(), h / 3);
		if (!active) {
			Ui.roundRect(g, x, y, w, h, r, Colors.fade(darkFill(Theme.PANEL.argb()), 0.55f * alpha));
			Ui.roundOutline(g, x, y, w, h, r, Colors.fade(Theme.border(), 0.6f * alpha));
			return;
		}
		if (Theme.SHADOWS.on()) Ui.roundRect(g, x + 1, y + 2, w, h, r, Colors.fade(Theme.shadowColor(), 0.55f * alpha));
		int base = darkFill(Colors.mix(Theme.PANEL.argb(), Theme.TEXT.argb(), hovered ? 0.05f + Theme.HOVER_STRENGTH.value() / 200f : 0.04f));
		Ui.roundRect(g, x, y, w, h, r, Colors.fade(Colors.withAlpha(base, Math.max(0.75f, Theme.PANEL_OPACITY.value() / 100f)), alpha));
		int outline = hovered ? Theme.accent() : Theme.border();
		Ui.roundOutline(g, x, y, w, h, r, Colors.fade(outline, alpha));
		if (hovered) Ui.accentFill(g, x + r + 1, y + h - 1, w - 2 * r - 2, 1, 0, 0f);
	}

	public static void sliderTrack(GuiGraphics g, int x, int y, int w, int h, boolean hovered, float alpha) {
		button(g, x, y, w, h, true, hovered, alpha);
	}

	public static void sliderHandle(GuiGraphics g, int x, int y, int w, int h, boolean hovered, float alpha) {
		int r = Math.min(Theme.radius(), 3);
		Ui.accentFill(g, x + 1, y + 2, w - 2, h - 4, r, hovered ? 0.15f : 0f);
	}

	public static void textField(GuiGraphics g, int x, int y, int w, int h, boolean focused, boolean active) {
		int r = Math.min(Theme.radius(), h / 3);
		Ui.roundRect(g, x, y, w, h, r, darkFill(Colors.mix(Theme.BACKGROUND.argb(), Theme.PANEL.argb(), 0.4f)));
		Ui.roundOutline(g, x, y, w, h, r, focused ? Theme.accent() : active ? Theme.border() : Colors.fade(Theme.border(), 0.5f));
	}

	public static void listBackground(GuiGraphics g, int x, int y, int w, int h) {
		Ui.rect(g, x, y, w, h, Colors.withAlpha(darkFill(Theme.PANEL.argb()), Math.min(0.85f, Theme.PANEL_OPACITY.value() / 100f)));
	}

	public static void listSeparators(GuiGraphics g, int x, int top, int bottom, int w) {
		Ui.accentFill(g, x, top - 1, w, 1, 0, 0f);
		Ui.rect(g, x, bottom, w, 1, Theme.border());
	}

	public static void listSelection(GuiGraphics g, int x, int y, int w, int h, boolean focused) {
		int r = Math.min(Theme.radius(), 3);
		Ui.roundRect(g, x, y, w, h, r, Colors.withAlpha(Colors.mix(darkFill(Theme.PANEL.argb()), Theme.accent(), 0.18f), 0.95f));
		Ui.roundOutline(g, x, y, w, h, r, focused ? Theme.accent() : Colors.fade(Theme.accent(), 0.55f));
	}

	public static void listHover(GuiGraphics g, int x, int y, int w, int h) {
		Ui.roundRect(g, x, y, w, h, Math.min(Theme.radius(), 3), Colors.withAlpha(Theme.TEXT.argb(), 0.05f));
	}

	/** Small client logo in the top-left corner of styled menus. */
	public static void cornerLogo(GuiGraphics g) {
		MenuStyleModule m = MenuStyleModule.get();
		if (m == null || !m.listLogo.on()) return;
		String name = Theme.clientName();
		if (Theme.ACCENT_MODE.get() == Theme.AccentMode.CHROMA) Ui.chromaText(g, name, 8, 8, true);
		else g.drawString(Ui.font(), name, 8, 8, Theme.accent(), true);
		Ui.accentFill(g, 8, 19, Math.min(18, Ui.width(name)), 1, 0, 0f);
	}
}
