package vanta.gui.items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphics;

import vanta.branding.Theme;
import vanta.gui.MenuSettings;
import vanta.metrics.FrameMetrics;
import vanta.ui.Colors;
import vanta.ui.Ui;

/** Small, general-purpose menu items. */
public final class BasicItems {
	private BasicItems() {
	}

	/** Section heading with an optional subtitle. */
	public static final class SectionTitle extends Item {
		private final String title;
		private final String sub;

		public SectionTitle(String title, String sub) {
			this.title = title;
			this.sub = sub;
		}

		public static int height(String sub) { return sub == null ? 14 : 24; }

		@Override
		public void render(GuiGraphics g, int ax, int ay, int mx, int my, boolean hovered) {
			Ui.rect(g, ax, ay + 2, 2, 7, Theme.accent());
			Ui.text(g, Theme.heading(title), ax + 6, ay + 1, Theme.text());
			if (sub != null) Ui.text(g, Ui.ellipsize(sub, w - 6), ax + 6, ay + 12, Theme.textFaint());
		}
	}

	/** Wrapped text. Wrap happens once at build time. */
	public static final class Paragraph extends Item {
		private final List<String> lines;
		private final int color;

		public Paragraph(String text, int width, int color) {
			this.lines = wrap(text, width);
			this.color = color;
		}

		public int lineCount() { return lines.size(); }
		public int preferredHeight() { return lines.size() * 10; }

		@Override
		public void render(GuiGraphics g, int ax, int ay, int mx, int my, boolean hovered) {
			int yy = ay;
			for (String line : lines) {
				Ui.text(g, line, ax, yy, color == 0 ? Theme.textDim() : color);
				yy += 10;
			}
		}

		public static List<String> wrap(String text, int width) {
			List<String> out = new ArrayList<>();
			for (String para : text.split("\n")) {
				StringBuilder line = new StringBuilder();
				for (String word : para.split(" ")) {
					String candidate = line.isEmpty() ? word : line + " " + word;
					if (Ui.width(candidate) > width && !line.isEmpty()) {
						out.add(line.toString());
						line = new StringBuilder(word);
					} else {
						line = new StringBuilder(candidate);
					}
				}
				out.add(line.toString());
			}
			return out;
		}
	}

	/** Themed button. */
	public static final class ButtonItem extends Item {
		private final Supplier<String> label;
		private final boolean primary;
		private final Runnable action;
		private final String hint;

		public ButtonItem(String label, boolean primary, Runnable action, String hint) {
			this(() -> label, primary, action, hint);
		}

		public ButtonItem(Supplier<String> label, boolean primary, Runnable action, String hint) {
			this.label = label;
			this.primary = primary;
			this.action = action;
			this.hint = hint;
		}

		@Override
		public void render(GuiGraphics g, int ax, int ay, int mx, int my, boolean hovered) {
			Ui.button(g, ax, ay, w, h, label.get(), hovered, primary);
		}

		@Override
		public boolean click(int ax, int ay, double mx, double my, int button) {
			if (button != 0) return false;
			MenuSettings.click();
			action.run();
			return true;
		}

		@Override public String hint() { return hint; }
	}

	/** Filter chip (category selector). */
	public static final class ChipItem extends Item {
		private final String label;
		private final BooleanSupplier selected;
		private final Runnable action;

		private int color;

		public ChipItem(String label, BooleanSupplier selected, Runnable action) {
			this.label = label;
			this.selected = selected;
			this.action = action;
		}

		public ChipItem color(int color) {
			this.color = color;
			return this;
		}

		public int preferredWidth() { return Ui.width(label) + (color != 0 ? 20 : 14); }

		@Override
		public void render(GuiGraphics g, int ax, int ay, int mx, int my, boolean hovered) {
			boolean sel = selected.getAsBoolean();
			int c = color != 0 ? color : Theme.accent();
			int fill = sel ? Colors.fade(c, 0.9f) : hovered ? Theme.panelHover() : Theme.panelRaised();
			Ui.roundRect(g, ax, ay, w, h, Math.min(h / 2, Theme.radius() + 2), fill);
			if (!sel && color != 0) Ui.rect(g, ax + 4, ay + h / 2 - 1, 3, 3, color);
			Ui.textCentered(g, label, ax + w / 2 + (!sel && color != 0 ? 2 : 0), ay + (h - 8) / 2, sel ? Colors.contrastText(c) : Theme.textDim());
		}

		@Override
		public boolean click(int ax, int ay, double mx, double my, int button) {
			MenuSettings.click();
			action.run();
			return true;
		}
	}

	/** Dashboard stat card with cached text. */
	public static final class StatCard extends Item {
		private final String label;
		private final Supplier<String> value;
		private final Supplier<String> sub;
		private final DoubleSupplier bar;
		private String cachedValue = "";
		private String cachedSub = "";
		private long version = -1;
		private long lastRefresh;

		public StatCard(String label, Supplier<String> value, Supplier<String> sub, DoubleSupplier bar) {
			this.label = label;
			this.value = value;
			this.sub = sub;
			this.bar = bar;
		}

		@Override
		public void render(GuiGraphics g, int ax, int ay, int mx, int my, boolean hovered) {
			long now = System.currentTimeMillis();
			if (version != FrameMetrics.version() || now - lastRefresh > 500) {
				version = FrameMetrics.version();
				lastRefresh = now;
				cachedValue = value.get();
				cachedSub = sub == null ? "" : sub.get();
			}
			Ui.card(g, ax, ay, w, h, false, 0);
			Ui.text(g, Ui.ellipsize(label, w - 16), ax + 8, ay + 7, Theme.textFaint());
			Ui.textScaled(g, Ui.ellipsize(cachedValue, (int) ((w - 16) / 1.5f)), ax + 8, ay + 18, 1.5f, Theme.text(), false);
			if (bar != null) {
				Ui.bar(g, ax + 8, ay + h - 9, w - 16, 3, bar.getAsDouble(), Theme.accent());
			} else if (!cachedSub.isEmpty()) {
				Ui.text(g, Ui.ellipsize(cachedSub, w - 16), ax + 8, ay + h - 13, Theme.textDim());
			}
		}
	}

	/** Frame-time graph of recent frames. */
	public static final class GraphCard extends Item {
		private final float[] samples = new float[512];

		@Override
		public void render(GuiGraphics g, int ax, int ay, int mx, int my, boolean hovered) {
			Ui.card(g, ax, ay, w, h, false, 0);
			Ui.text(g, "Frame times", ax + 8, ay + 6, Theme.textFaint());
			int gx = ax + 8, gy = ay + 18, gw = w - 16, gh = h - 24;
			int n = FrameMetrics.recentFrameTimes(samples, Math.min(samples.length, gw));
			if (n == 0) {
				Ui.text(g, "Collecting...", gx, gy + gh / 2 - 4, Theme.textDim());
				return;
			}
			float avg = FrameMetrics.averageFrameMs();
			float top = Math.max(33.3f, avg * 3f);
			int target = gy + gh - Math.round(16.7f / top * gh);
			Ui.rect(g, gx, target, gw, 1, Colors.fade(Theme.border(), 0.9f));
			Ui.textRight(g, "60 FPS", ax + w - 8, ay + 6, Theme.textFaint());
			int barW = Math.max(1, gw / Math.max(1, n));
			int start = gx + gw - n * barW;
			for (int i = 0; i < n; i++) {
				float ms = Math.min(top, samples[i]);
				int bh = Math.max(1, Math.round(ms / top * gh));
				int color = samples[i] > avg * 2.2f && samples[i] > 20f ? 0xFFF87171 : Theme.accent();
				Ui.rect(g, start + i * barW, gy + gh - bh, barW, bh, Colors.fade(color, 0.85f));
			}
		}

		@Override public String hint() { return "Each bar is one frame. Red bars are stutters (much slower than average)."; }
	}

	/** Label on the left, live value on the right. */
	public static final class KeyValueRow extends Item {
		private final String label;
		private final Supplier<String> value;
		private final int valueColor;

		public KeyValueRow(String label, Supplier<String> value, int valueColor) {
			this.label = label;
			this.value = value;
			this.valueColor = valueColor;
		}

		@Override
		public void render(GuiGraphics g, int ax, int ay, int mx, int my, boolean hovered) {
			Ui.text(g, label, ax, ay + 2, Theme.textDim());
			String v = value.get();
			int lw = Ui.width(label) + 10;
			Ui.textRight(g, Ui.ellipsize(v, w - lw), ax + w, ay + 2, valueColor == 0 ? Theme.text() : valueColor);
			Ui.rect(g, ax, ay + h - 1, w, 1, Colors.fade(Theme.border(), 0.6f));
		}
	}

	/** Palette preset swatch. */
	public static final class SwatchItem extends Item {
		private final Theme.Palette palette;

		public SwatchItem(Theme.Palette palette) {
			this.palette = palette;
		}

		@Override
		public void render(GuiGraphics g, int ax, int ay, int mx, int my, boolean hovered) {
			boolean active = Theme.ACCENT.argb() == palette.accent && Theme.SECONDARY.argb() == palette.secondary;
			Ui.card(g, ax, ay, w, h, hovered, active ? palette.accent : 0);
			Ui.roundRect(g, ax + 6, ay + (h - 10) / 2, 10, 10, 2, palette.accent);
			Ui.roundRect(g, ax + 13, ay + (h - 10) / 2, 10, 10, 2, palette.secondary);
			Ui.text(g, Ui.ellipsize(palette.label, w - 32), ax + 28, ay + (h - 8) / 2, active ? Theme.text() : Theme.textDim());
		}

		@Override
		public boolean click(int ax, int ay, double mx, double my, int button) {
			MenuSettings.click();
			Theme.applyPalette(palette);
			return true;
		}

		@Override public String hint() { return "Apply the " + palette.label + " accent palette."; }
	}

	/** Whole-look style preset swatch (surfaces + palette + shape). */
	public static final class StyleItem extends Item {
		private final Theme.Style style;
		private final Runnable onApply;

		public StyleItem(Theme.Style style, Runnable onApply) {
			this.style = style;
			this.onApply = onApply;
		}

		@Override
		public void render(GuiGraphics g, int ax, int ay, int mx, int my, boolean hovered) {
			Ui.card(g, ax, ay, w, h, hovered, 0);
			int sx = ax + 6, sy = ay + (h - 12) / 2;
			Ui.roundRect(g, sx, sy, 22, 12, 2, styleBackground());
			Ui.rect(g, sx + 3, sy + 3, 8, 6, stylePanel());
			Ui.rect(g, sx + 13, sy + 4, 6, 4, style.palette().accent);
			Ui.text(g, Ui.ellipsize(style.label, w - 40), ax + 34, ay + (h - 8) / 2, Theme.text());
		}

		private int styleBackground() { return style.background(); }
		private int stylePanel() { return style.panel(); }

		@Override
		public boolean click(int ax, int ay, double mx, double my, int button) {
			MenuSettings.click();
			onApply.run();
			return true;
		}

		@Override public String hint() { return "Apply the " + style.label + " look: background, panels, text, corners, card style and palette."; }
	}

	/** One search result. */
	public static final class ResultRow extends Item {
		private final String[] icon;
		private final String title;
		private final String subtitle;
		private final Runnable action;
		private final BooleanSupplier selected;

		public ResultRow(String[] icon, String title, String subtitle, Runnable action, BooleanSupplier selected) {
			this.icon = icon;
			this.title = title;
			this.subtitle = subtitle;
			this.action = action;
			this.selected = selected;
		}

		@Override
		public void render(GuiGraphics g, int ax, int ay, int mx, int my, boolean hovered) {
			boolean sel = selected.getAsBoolean();
			Ui.roundRect(g, ax, ay, w, h, Theme.radius(), sel || hovered ? Theme.panelHover() : Theme.panelRaised());
			if (sel) Ui.rect(g, ax, ay + 3, 2, h - 6, Theme.accent());
			Ui.icon(g, icon, ax + 8, ay + (h - 9) / 2, Theme.accent());
			Ui.text(g, Ui.ellipsize(title, w / 2 - 24), ax + 24, ay + (h - 8) / 2, Theme.text());
			Ui.textRight(g, Ui.ellipsize(subtitle, w / 2 - 12), ax + w - 8, ay + (h - 8) / 2, Theme.textFaint());
		}

		@Override
		public boolean click(int ax, int ay, double mx, double my, int button) {
			MenuSettings.click();
			action.run();
			return true;
		}

		public void activate() { action.run(); }

		@Override public String hint() { return "Enter or click to open"; }
	}
}
