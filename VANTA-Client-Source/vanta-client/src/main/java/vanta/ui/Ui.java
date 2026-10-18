package vanta.ui;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;

import vanta.branding.Theme;

/**
 * Shared drawing primitives used by every VANTA screen and the HUD.
 * Everything is built from plain rectangles and text, which is the cheapest
 * thing the 1.21.11 GUI renderer can draw — no blur, no shaders, no textures.
 */
public final class Ui {
	/** Pixel insets for rounded corners, per radius. */
	private static final int[][] CORNER = {
			{},
			{1},
			{2, 1},
			{3, 1, 1},
			{4, 2, 1, 1},
			{5, 3, 2, 1, 1},
			{6, 4, 3, 2, 1, 1},
			corner(7), corner(8), corner(9), corner(10), corner(11), corner(12), corner(13), corner(14)
	};

	/** Corner insets for bigger radii, from a circle. */
	private static int[] corner(int r) {
		int[] out = new int[r];
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			out[i] = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
		}
		out[0] = Math.max(out[0], Math.min(r, out.length > 1 ? out[1] + 1 : r));
		return out;
	}

	private static final Map<String[], int[][]> ICON_RUNS = new IdentityHashMap<>();

	private Ui() {
	}

	public static Font font() { return Minecraft.getInstance().font; }

	// ---------- Shapes ----------

	public static void rect(GuiGraphics g, int x, int y, int w, int h, int color) {
		if (w <= 0 || h <= 0 || Colors.alpha(color) == 0) return;
		g.fill(x, y, x + w, y + h, color);
	}

	public static void roundRect(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
		if (w <= 0 || h <= 0 || Colors.alpha(color) == 0) return;
		int r = clampRadius(radius, w, h);
		if (r == 0) {
			g.fill(x, y, x + w, y + h, color);
			return;
		}
		int[] inset = CORNER[r];
		g.fill(x, y + r, x + w, y + h - r, color);
		for (int i = 0; i < r; i++) {
			g.fill(x + inset[i], y + i, x + w - inset[i], y + i + 1, color);
			g.fill(x + inset[i], y + h - 1 - i, x + w - inset[i], y + h - i, color);
		}
	}

	public static void roundOutline(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
		if (w <= 1 || h <= 1 || Colors.alpha(color) == 0) return;
		int r = clampRadius(radius, w, h);
		if (r == 0) {
			g.fill(x, y, x + w, y + 1, color);
			g.fill(x, y + h - 1, x + w, y + h, color);
			g.fill(x, y + 1, x + 1, y + h - 1, color);
			g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
			return;
		}
		int[] inset = CORNER[r];
		g.fill(x + inset[0], y, x + w - inset[0], y + 1, color);
		g.fill(x + inset[0], y + h - 1, x + w - inset[0], y + h, color);
		g.fill(x, y + r, x + 1, y + h - r, color);
		g.fill(x + w - 1, y + r, x + w, y + h - r, color);
		for (int i = 1; i < r; i++) {
			int len = Math.max(1, inset[i - 1] - inset[i]);
			g.fill(x + inset[i], y + i, x + inset[i] + len, y + i + 1, color);
			g.fill(x + w - inset[i] - len, y + i, x + w - inset[i], y + i + 1, color);
			g.fill(x + inset[i], y + h - 1 - i, x + inset[i] + len, y + h - i, color);
			g.fill(x + w - inset[i] - len, y + h - 1 - i, x + w - inset[i], y + h - i, color);
		}
	}

	/** Outline using the theme's border width (0 = none). */
	public static void border(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
		int bw = Theme.borderWidth();
		for (int i = 0; i < bw; i++) roundOutline(g, x + i, y + i, w - i * 2, h - i * 2, Math.max(0, radius - i), color);
	}

	/** Soft drop shadow (two offset layers). */
	public static void shadow(GuiGraphics g, int x, int y, int w, int h, int radius) {
		int c = Theme.shadowColor();
		if (Colors.alpha(c) == 0) return;
		roundRect(g, x + 1, y + 2, w, h, radius + 1, Colors.fade(c, 0.6f));
		roundRect(g, x + 2, y + 4, w, h, radius + 2, Colors.fade(c, 0.35f));
	}

	/** Standard VANTA panel: themed fill with a subtle border. */
	public static void panel(GuiGraphics g, int x, int y, int w, int h, int fill) {
		roundRect(g, x, y, w, h, Theme.radius(), fill);
		border(g, x, y, w, h, Theme.radius(), Theme.border());
	}

	/**
	 * A card in the current card style. highlight is used for the outline of
	 * "active" cards (pass 0 for none).
	 */
	public static void card(GuiGraphics g, int x, int y, int w, int h, boolean hovered, int highlight) {
		int r = Theme.radius();
		Theme.CardStyle style = Theme.CARD_STYLE.get();
		if (style != Theme.CardStyle.MINIMAL && Theme.SHADOWS.on()) {
			roundRect(g, x + 1, y + 2, w, h, r, Colors.fade(Theme.shadowColor(), 0.55f));
		}
		int fill = hovered ? Theme.panelHover() : Theme.panelRaised();
		switch (style) {
			case FILLED -> {
				roundRect(g, x, y, w, h, r, fill);
				border(g, x, y, w, h, r, highlight != 0 ? highlight : Theme.border());
			}
			case OUTLINED -> {
				roundRect(g, x, y, w, h, r, fill);
				int line = highlight != 0 ? highlight : hovered ? Colors.fade(Theme.accent(), 0.7f) : Colors.mix(Theme.border(), Theme.text(), 0.15f);
				border(g, x, y, w, h, r, line);
				if (Theme.borderWidth() == 0) roundOutline(g, x, y, w, h, r, line);
			}
			case MINIMAL -> {
				if (hovered) roundRect(g, x, y, w, h, r, fill);
				else roundRect(g, x, y, w, h, r, Colors.fade(fill, 0.55f));
				if (highlight != 0) rect(g, x, y + h - 1, w, 1, highlight);
			}
			case GLASS -> {
				roundRect(g, x, y, w, h, r, fill);
				rect(g, x + r, y, w - r * 2, 1, Colors.withAlpha(0xFFFFFFFF, hovered ? 0.18f : 0.10f));
				border(g, x, y, w, h, r, highlight != 0 ? highlight : Colors.withAlpha(0xFFFFFFFF, 0.08f));
			}
		}
	}

	/** Fills with the accent (solid, or accent-to-secondary gradient in Gradient/Chroma mode). */
	public static void accentFill(GuiGraphics g, int x, int y, int w, int h, int radius, float lighten) {
		int a = lighten > 0 ? Colors.mix(Theme.accent(), 0xFFFFFFFF, lighten) : Theme.accent();
		if (!Theme.gradientAccent() || w < 8) {
			roundRect(g, x, y, w, h, radius, a);
			return;
		}
		int b = lighten > 0 ? Colors.mix(Theme.secondary(), 0xFFFFFFFF, lighten) : Theme.secondary();
		int r = clampRadius(radius, w, h);
		roundRect(g, x, y, Math.min(w, r * 2 + 1), h, r, a);
		roundRect(g, x + w - Math.min(w, r * 2 + 1), y, Math.min(w, r * 2 + 1), h, r, b);
		hGradient(g, x + r, y, w - r * 2, h, a, b);
	}

	public static void hGradient(GuiGraphics g, int x, int y, int w, int h, int left, int right) {
		if (w <= 0 || h <= 0) return;
		// GuiGraphics only has vertical gradients; approximate with vertical strips.
		int steps = Math.max(1, Math.min(32, w / 3));
		for (int i = 0; i < steps; i++) {
			int x0 = x + w * i / steps;
			int x1 = x + w * (i + 1) / steps;
			g.fill(x0, y, x1, y + h, Colors.mix(left, right, steps == 1 ? 0 : i / (float) (steps - 1)));
		}
	}

	private static int clampRadius(int radius, int w, int h) {
		int r = Math.max(0, Math.min(radius, CORNER.length - 1));
		return Math.min(r, Math.min(w, h) / 2);
	}

	// ---------- Text ----------

	private static boolean hud;

	/** Switches text to the HUD font (true) or the menu font (false). Returns the previous state. */
	public static boolean hud(boolean on) {
		boolean prev = hud;
		hud = on;
		return prev;
	}

	/** The font VANTA text is drawn in right now. */
	public static UiFont currentFont() {
		return hud ? Theme.hudFont() : Theme.FONT.get();
	}

	public static FormattedCharSequence seq(String s) {
		return FormattedCharSequence.forward(s, currentFont().style());
	}

	private static boolean shadowText() { return Theme.TEXT_SHADOW.on(); }

	/** Draws text in the current VANTA font. All VANTA text goes through here. */
	public static void draw(GuiGraphics g, String s, int x, int y, int color, boolean shadow) {
		if (s == null || s.isEmpty()) return;
		UiFont f = currentFont();
		if (f.isDefault()) g.drawString(font(), s, x, y, color, shadow);
		else g.drawString(font(), FormattedCharSequence.forward(s, f.style()), x, y, color, shadow);
	}

	/** Bold text in the current VANTA font. */
	public static void bold(GuiGraphics g, String s, int x, int y, int color, boolean shadow) {
		if (s == null || s.isEmpty()) return;
		g.drawString(font(), FormattedCharSequence.forward(s, currentFont().bold()), x, y, color, shadow);
	}

	public static void bold(GuiGraphics g, String s, int x, int y, int color) {
		bold(g, s, x, y, color, shadowText());
	}

	public static int boldWidth(String s) {
		return s == null ? 0 : font().width(FormattedCharSequence.forward(s, currentFont().bold()));
	}

	public static String ellipsizeBold(String s, int maxWidth) {
		if (maxWidth <= 0) return "";
		if (boldWidth(s) <= maxWidth) return s;
		int end = s.length();
		int dots = boldWidth("...");
		while (end > 0 && boldWidth(s.substring(0, end)) + dots > maxWidth) end--;
		return s.substring(0, end).stripTrailing() + "...";
	}

	public static void text(GuiGraphics g, String s, int x, int y, int color) {
		draw(g, s, x, y, color, shadowText());
	}

	public static void textShadow(GuiGraphics g, String s, int x, int y, int color) {
		draw(g, s, x, y, color, true);
	}

	public static void textCentered(GuiGraphics g, String s, int cx, int y, int color) {
		draw(g, s, cx - width(s) / 2, y, color, shadowText());
	}

	public static void textRight(GuiGraphics g, String s, int right, int y, int color) {
		draw(g, s, right - width(s), y, color, shadowText());
	}

	public static void textScaled(GuiGraphics g, String s, int x, int y, float scale, int color, boolean shadow) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale);
		draw(g, s, 0, 0, color, shadow || shadowText());
		g.pose().popMatrix();
	}

	/** Text where each character takes the next color around the chroma wheel. */
	public static void chromaText(GuiGraphics g, String s, int x, int y, boolean shadow) {
		int cx = x;
		for (int i = 0; i < s.length(); i++) {
			String ch = String.valueOf(s.charAt(i));
			draw(g, ch, cx, y, Theme.chroma(i * 0.035f), shadow);
			cx += width(ch);
		}
	}

	/** Draws text in a specific font, whatever the current one is (font pickers). */
	public static void drawIn(GuiGraphics g, UiFont f, String s, int x, int y, int color, boolean shadow) {
		if (s == null || s.isEmpty()) return;
		if (f.isDefault()) g.drawString(font(), s, x, y, color, shadow);
		else g.drawString(font(), FormattedCharSequence.forward(s, f.style()), x, y, color, shadow);
	}

	public static int widthIn(UiFont f, String s) {
		if (s == null) return 0;
		return f.isDefault() ? font().width(s) : font().width(FormattedCharSequence.forward(s, f.style()));
	}

	public static int width(String s) {
		if (s == null) return 0;
		UiFont f = currentFont();
		return f.isDefault() ? font().width(s) : font().width(FormattedCharSequence.forward(s, f.style()));
	}

	/** Shortens text with "..." so it fits in maxWidth pixels. */
	public static String ellipsize(String s, int maxWidth) {
		if (maxWidth <= 0) return "";
		if (width(s) <= maxWidth) return s;
		String dots = "...";
		int dotsW = width(dots);
		int end = s.length();
		while (end > 0 && width(s.substring(0, end)) + dotsW > maxWidth) end--;
		return s.substring(0, end).stripTrailing() + dots;
	}

	// ---------- Icons ----------

	public static void icon(GuiGraphics g, String[] icon, int x, int y, int color) {
		int[][] runs = ICON_RUNS.computeIfAbsent(icon, Ui::buildRuns);
		for (int[] run : runs) {
			g.fill(x + run[1], y + run[0], x + run[2], y + run[0] + 1, color);
		}
	}

	private static int[][] buildRuns(String[] icon) {
		java.util.List<int[]> runs = new java.util.ArrayList<>();
		for (int row = 0; row < icon.length; row++) {
			String line = icon[row];
			int start = -1;
			for (int col = 0; col <= line.length(); col++) {
				boolean on = col < line.length() && line.charAt(col) == '#';
				if (on && start < 0) start = col;
				if (!on && start >= 0) {
					runs.add(new int[]{row, start, col});
					start = -1;
				}
			}
		}
		return runs.toArray(new int[0][]);
	}

	// ---------- Controls ----------

	/** Width of the toggle control in the current style. Height is always 10. */
	public static int toggleWidth() {
		return switch (Theme.TOGGLE_STYLE.get()) {
			case SWITCH -> 20;
			case CHECKBOX, DOT -> 10;
			case PILL -> 24;
		};
	}

	public static void toggle(GuiGraphics g, int x, int y, boolean on, boolean hovered) {
		int offTrack = Colors.mix(Theme.border(), Theme.text(), hovered ? 0.18f : 0.08f);
		switch (Theme.TOGGLE_STYLE.get()) {
			case SWITCH -> {
				int w = 20, h = 10;
				if (on) accentFill(g, x, y, w, h, 5, 0f);
				else roundRect(g, x, y, w, h, 5, offTrack);
				int knob = on ? Colors.contrastText(Theme.accent()) : Theme.textDim();
				roundRect(g, on ? x + w - 9 : x + 1, y + 1, 8, 8, 4, knob);
			}
			case CHECKBOX -> {
				roundRect(g, x, y, 10, 10, 2, on ? Theme.accent() : offTrack);
				if (on) {
					int c = Colors.contrastText(Theme.accent());
					rect(g, x + 2, y + 5, 2, 2, c);
					rect(g, x + 3, y + 6, 2, 2, c);
					rect(g, x + 5, y + 4, 1, 2, c);
					rect(g, x + 6, y + 3, 1, 2, c);
					rect(g, x + 7, y + 2, 1, 2, c);
				}
			}
			case PILL -> {
				if (on) accentFill(g, x, y, 24, 10, 5, 0f);
				else roundRect(g, x, y, 24, 10, 5, offTrack);
				String s = on ? "ON" : "OFF";
				int c = on ? Colors.contrastText(Theme.accent()) : Theme.textDim();
				textScaledCentered(g, s, x + 12, y + 3, 0.5f, c);
			}
			case DOT -> {
				roundRect(g, x, y, 10, 10, 5, offTrack);
				if (on) roundRect(g, x + 2, y + 2, 6, 6, 3, Theme.accent());
			}
		}
	}

	private static void textScaledCentered(GuiGraphics g, String s, int cx, int y, float scale, int color) {
		g.pose().pushMatrix();
		g.pose().translate(cx, y);
		g.pose().scale(scale);
		g.drawString(font(), s, -font().width(s) / 2, 0, color, false);
		g.pose().popMatrix();
	}

	public static void slider(GuiGraphics g, int x, int y, int w, double fraction, boolean active) {
		int trackY = y + 3;
		roundRect(g, x, trackY, w, 4, 2, Colors.mix(Theme.border(), Theme.text(), 0.06f));
		int filled = (int) Math.round(w * Math.max(0, Math.min(1, fraction)));
		if (filled > 0) accentFill(g, x, trackY, filled, 4, 2, 0f);
		int kx = x + filled - 4;
		roundRect(g, kx, y, 8, 10, 3, active ? Theme.secondary() : Theme.text());
	}

	public static void button(GuiGraphics g, int x, int y, int w, int h, String label, boolean hovered, boolean primary) {
		int textColor;
		if (primary) {
			if (Theme.SHADOWS.on()) roundRect(g, x + 1, y + 2, w, h, Theme.radius(), Colors.fade(Theme.shadowColor(), 0.6f));
			accentFill(g, x, y, w, h, Theme.radius(), hovered ? 0.12f : 0f);
			textColor = Colors.contrastText(Theme.accent());
		} else {
			card(g, x, y, w, h, hovered, hovered ? Colors.fade(Theme.accent(), 0.7f) : 0);
			textColor = Theme.text();
		}
		String s = ellipsize(label, w - 6);
		textCentered(g, s, x + w / 2, y + (h - 8) / 2, textColor);
	}

	/** Simple progress/usage bar. */
	public static void bar(GuiGraphics g, int x, int y, int w, int h, double fraction, int color) {
		roundRect(g, x, y, w, h, h / 2, Colors.mix(Theme.border(), Theme.text(), 0.05f));
		roundRect(g, x, y, (int) Math.round(w * Math.max(0, Math.min(1, fraction))), h, h / 2, color);
	}

	public static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}
}
