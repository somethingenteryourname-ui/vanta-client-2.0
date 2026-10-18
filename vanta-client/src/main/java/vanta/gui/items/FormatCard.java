package vanta.gui.items;

import net.minecraft.client.gui.GuiGraphics;

import vanta.branding.Theme;
import vanta.branding.UiFormat;
import vanta.gui.MenuSettings;
import vanta.ui.Colors;
import vanta.ui.Ui;

/**
 * One interface format on the Theme page: a little drawing of its layout in its own
 * colors, its name, and a click to apply it.
 */
public final class FormatCard extends Item {
	private final UiFormat format;
	private final MenuHost host;

	public FormatCard(UiFormat format, MenuHost host) {
		this.format = format;
		this.host = host;
	}

	@Override
	public void render(GuiGraphics g, int ax, int ay, int mx, int my, boolean hovered) {
		boolean current = Theme.FORMAT.get() == format;
		Ui.card(g, ax, ay, w, h, hovered, current ? Theme.accent() : 0);
		int px = ax + 5, py = ay + 5, pw = w - 10, ph = h - 21;
		drawPreview(g, format, px, py, pw, ph);
		Ui.text(g, Ui.ellipsize(format.label(), w - 10), ax + 6, ay + h - 13, current ? Theme.accent() : Theme.text());
	}

	/** A tiny schematic of the format, in the format's own colors. */
	public static void drawPreview(GuiGraphics g, UiFormat f, int x, int y, int w, int h) {
		UiFormat.Spec s = f.spec;
		int r = Math.min(4, s.radius() / 2 + 1);
		Ui.roundRect(g, x, y, w, h, r, s.background() | 0xFF000000);
		int panel = s.panel() | 0xFF000000;
		int raised = Colors.mix(panel, s.text() | 0xFF000000, 0.08f);
		int line = s.border() | 0xFF000000;
		int accent = s.accent() | 0xFF000000;
		int faint = Colors.mix(s.text() | 0xFF000000, panel, 0.55f);
		int ix = x + 3, iy = y + 3, iw = w - 6, ih = h - 6;
		switch (f.shell) {
			case CLICKGUI -> {
				int cw = Math.max(10, (iw - 6) / 3);
				for (int c = 0; c < 3; c++) {
					int cx = ix + c * (cw + 3);
					int ch = ih - 4 - c * 5;
					Ui.rect(g, cx, iy + c * 2, cw, ch, panel);
					Ui.rect(g, cx, iy + c * 2, cw, 4, accent);
					for (int k = 0; k < 4 && 6 + k * 4 < ch; k++) Ui.rect(g, cx + 2, iy + c * 2 + 6 + k * 4, cw - 4 - (k % 2) * 3, 2, k == 1 ? accent : faint);
				}
				return;
			}
			default -> Ui.roundRect(g, ix, iy, iw, ih, r, panel);
		}
		int cx = ix, cy = iy, cw = iw, ch = ih;
		switch (f.shell) {
			case SIDEBAR, RAIL, LIQUID -> {
				int sw = f.shell == UiFormat.Shell.RAIL ? 7 : Math.max(12, iw / 4);
				Ui.rect(g, ix + sw, iy, 1, ih, line);
				for (int k = 0; k < 5; k++) {
					int ny = iy + 7 + k * 5;
					if (ny + 3 > iy + ih) break;
					if (k == 1) Ui.rect(g, ix + 2, ny - 1, sw - 4, 4, f.shell == UiFormat.Shell.LIQUID ? raised : Colors.withAlpha(accent, 0.6f));
					Ui.rect(g, ix + 3, ny, f.shell == UiFormat.Shell.RAIL ? 2 : sw - 8, 2, k == 1 ? s.text() | 0xFF000000 : faint);
				}
				cx = ix + sw + 3;
				cw = iw - sw - 5;
				if (f.shell == UiFormat.Shell.LIQUID) {
					Ui.rect(g, ix, iy + 6, iw, 1, line);
					Ui.roundRect(g, cx, iy + 1, cw / 2, 4, 2, raised);
					for (int k = 0; k < 3; k++) Ui.roundRect(g, cx + cw / 2 + 3 + k * (cw / 7 + 1), iy + 1, cw / 7, 4, 2, k == 2 ? raised : panel);
					cy = iy + 9;
				} else {
					Ui.rect(g, cx, iy + 2, cw / 3, 2, s.text() | 0xFF000000);
					cy = iy + 7;
				}
				ch = iy + ih - cy - 2;
			}
			case TABS -> {
				Ui.rect(g, ix + 2, iy + 2, 8, 2, accent);
				for (int k = 0; k < 5; k++) Ui.rect(g, ix + 2 + k * (iw / 6), iy + 6, iw / 7, 3, k == 0 ? accent : faint);
				Ui.rect(g, ix, iy + 10, iw, 1, line);
				cy = iy + 12;
				ch = ih - 14;
				cx = ix + 2;
				cw = iw - 4;
			}
			case DOCK -> {
				Ui.rect(g, ix + 2, iy + 2, 8, 2, accent);
				int dw = Math.min(iw - 8, 30);
				Ui.roundRect(g, ix + (iw - dw) / 2, iy + ih - 6, dw, 5, 2, raised);
				for (int k = 0; k < 5; k++) Ui.rect(g, ix + (iw - dw) / 2 + 3 + k * 5, iy + ih - 5, 3, 3, k == 2 ? accent : faint);
				cy = iy + 7;
				ch = ih - 15;
				cx = ix + 2;
				cw = iw - 4;
			}
			default -> {
			}
		}
		// cards in the content area
		switch (s.look()) {
			case ICON_ROWS -> {
				int rh = 6, cols = 2, gw = (cw - 2) / cols;
				for (int k = 0; k < 6; k++) {
					int rx = cx + (k % cols) * (gw + 2), ry = cy + (k / cols) * (rh + 2);
					if (ry + rh > cy + ch) break;
					boolean on = k % 3 == 1;
					Ui.rect(g, rx, ry, gw, rh, raised);
					if (on) {
						Ui.rect(g, rx, ry, gw, 1, accent);
						Ui.rect(g, rx, ry + rh - 1, gw, 1, accent);
					}
					Ui.rect(g, rx + 1, ry + 1, 4, 4, Colors.mix(raised, s.text() | 0xFF000000, 0.15f));
					Ui.rect(g, rx + gw - 6, ry + 2, 5, 2, on ? accent : faint);
				}
			}
			case TILES -> {
				int tw = Math.max(8, (cw - 6) / 4), th = Math.max(8, Math.min(14, ch / 2 - 1));
				for (int k = 0; k < 8; k++) {
					int rx = cx + (k % 4) * (tw + 2), ry = cy + (k / 4) * (th + 2);
					if (ry + th > cy + ch || rx + tw > cx + cw + 1) continue;
					Ui.rect(g, rx, ry, tw, th, raised);
					Ui.rect(g, rx + tw / 2 - 1, ry + 2, 3, 3, faint);
					Ui.rect(g, rx, ry + th - 2, tw, 2, k % 3 == 0 ? accent : line);
				}
			}
			case STANDARD -> {
				boolean listLayout = s.layout() == Theme.Layout.LIST;
				if (listLayout) {
					for (int k = 0; k < 6; k++) {
						int ry = cy + k * 4;
						if (ry + 3 > cy + ch) break;
						Ui.rect(g, cx, ry, cw, 3, raised);
						Ui.rect(g, cx + cw - 5, ry + 1, 4, 1, k % 2 == 0 ? accent : faint);
					}
				} else {
					int tw = Math.max(8, (cw - 4) / 3), th = Math.max(6, ch / 2 - 1);
					for (int k = 0; k < 6; k++) {
						int rx = cx + (k % 3) * (tw + 2), ry = cy + (k / 3) * (th + 2);
						if (ry + th > cy + ch) continue;
						Ui.rect(g, rx, ry, tw, th, raised);
						if (k % 2 == 0) Ui.rect(g, rx, ry + 1, 1, th - 2, accent);
						Ui.rect(g, rx + 2, ry + 2, tw / 2, 1, s.text() | 0xFF000000);
					}
				}
			}
		}
	}

	@Override
	public boolean click(int ax, int ay, double mx, double my, int button) {
		MenuSettings.click();
		format.apply();
		host.rebuild();
		return true;
	}

	@Override public String hint() { return format.label() + ": " + format.description; }
}
