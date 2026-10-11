package vanta.gui.items;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import vanta.branding.Theme;
import vanta.config.BoolSetting;
import vanta.config.ChoiceSetting;
import vanta.config.ColorSetting;
import vanta.config.IntSetting;
import vanta.config.Setting;
import vanta.config.TextSetting;
import vanta.gui.MenuSettings;
import vanta.gui.TextInputScreen;
import vanta.ui.Colors;
import vanta.ui.Icons;
import vanta.ui.Ui;

/**
 * One setting with the right control for its type: toggle, slider (+ typed number),
 * choice arrows, color swatches (+ hex entry) or text field. A reset icon appears
 * when the value differs from the default.
 */
public final class SettingRow extends Item {
	private static final int SLIDER_W = 80;
	private static final int[] QUICK_COLORS = {0xFF2F8CFF, 0xFF27D3E6, 0xFF8B5CF6, 0xFFEF4444, 0xFFF97316, 0xFF10B981, 0xFFFFFFFF, 0xFF0A0C10};

	private final Setting<?> setting;
	private final MenuHost host;
	private boolean dragging;

	public SettingRow(Setting<?> setting, MenuHost host) {
		this.setting = setting;
		this.host = host;
	}

	public static int height() { return Math.max(24, Math.round(28 * Theme.density())); }

	private int controlWidth() {
		return switch (setting.kind()) {
			case BOOLEAN -> Ui.toggleWidth();
			case INT -> SLIDER_W + 40;
			case CHOICE -> 104;
			case COLOR -> QUICK_COLORS.length * 10 + 72;
			case TEXT -> 120;
		};
	}

	private int resetX() { return w - 14; }
	private int controlRight() { return w - 20; }
	private int controlLeft() { return controlRight() - controlWidth(); }

	@Override
	public void render(GuiGraphics g, int ax, int ay, int mx, int my, boolean hovered) {
		Ui.card(g, ax, ay, w, h, hovered, host.isHighlighted(setting.id()) ? Theme.secondary() : 0);

		int textW = controlLeft() - 16;
		int nameY = ay + (h >= 26 ? 5 : (h - 8) / 2);
		String name = setting.name();
		Ui.text(g, Ui.ellipsize(name, textW), ax + 8, nameY, Theme.text());
		if (!setting.tag().isEmpty()) {
			int tx = ax + 8 + Math.min(textW, Ui.width(name)) + 6;
			if (tx + Ui.width(setting.tag()) < ax + 8 + textW) Ui.text(g, setting.tag(), tx, nameY, Colors.fade(Theme.accent(), 0.8f));
		}
		if (h >= 26) Ui.text(g, Ui.ellipsize(setting.description(), textW), ax + 8, ay + 16, Theme.textFaint());

		int cy = ay + h / 2;
		int cx = ax + controlLeft();
		switch (setting.kind()) {
			case BOOLEAN -> Ui.toggle(g, ax + controlRight() - Ui.toggleWidth(), cy - 5, ((BoolSetting) setting).on(), hovered);
			case INT -> {
				IntSetting s = (IntSetting) setting;
				boolean valueHover = Ui.inside(mx, my, cx, cy - 6, 36, 12);
				Ui.roundRect(g, cx, cy - 6, 36, 12, 2, valueHover ? Theme.panelHover() : Colors.withAlpha(Theme.background(), 0.5f));
				Ui.textCentered(g, Ui.ellipsize(s.displayValue(), 34), cx + 18, cy - 4, Theme.text());
				Ui.slider(g, cx + 40, cy - 5, SLIDER_W, s.fraction(), dragging);
			}
			case CHOICE -> {
				ChoiceSetting<?> s = (ChoiceSetting<?>) setting;
				int bw = controlWidth();
				Ui.roundRect(g, cx, cy - 7, bw, 14, Theme.radius(), Colors.withAlpha(Theme.background(), 0.5f));
				boolean leftHover = Ui.inside(mx, my, cx, cy - 7, 14, 14);
				boolean rightHover = Ui.inside(mx, my, cx + bw - 14, cy - 7, 14, 14);
				Ui.text(g, "<", cx + 4, cy - 4, leftHover ? Theme.accent() : Theme.textDim());
				Ui.text(g, ">", cx + bw - 9, cy - 4, rightHover ? Theme.accent() : Theme.textDim());
				Ui.textCentered(g, Ui.ellipsize(s.displayValue(), bw - 28), cx + bw / 2, cy - 4, Theme.text());
			}
			case COLOR -> {
				ColorSetting s = (ColorSetting) setting;
				for (int i = 0; i < QUICK_COLORS.length; i++) {
					int sx = cx + i * 10;
					boolean sel = s.argb() == QUICK_COLORS[i];
					if (sel) Ui.roundRect(g, sx - 1, cy - 5, 10, 10, 2, Theme.text());
					Ui.roundRect(g, sx, cy - 4, 8, 8, 2, QUICK_COLORS[i]);
				}
				int px = cx + QUICK_COLORS.length * 10 + 4;
				Ui.roundRect(g, px, cy - 6, 12, 12, 2, s.argb());
				Ui.roundOutline(g, px, cy - 6, 12, 12, 2, Theme.border());
				boolean hexHover = Ui.inside(mx, my, px + 15, cy - 6, 52, 12);
				Ui.roundRect(g, px + 15, cy - 6, 52, 12, 2, hexHover ? Theme.panelHover() : Colors.withAlpha(Theme.background(), 0.5f));
				Ui.textCentered(g, s.displayValue(), px + 41, cy - 4, Theme.text());
			}
			case TEXT -> {
				TextSetting s = (TextSetting) setting;
				boolean boxHover = Ui.inside(mx, my, cx, cy - 7, controlWidth(), 14);
				Ui.roundRect(g, cx, cy - 7, controlWidth(), 14, Theme.radius(), boxHover ? Theme.panelHover() : Colors.withAlpha(Theme.background(), 0.5f));
				Ui.text(g, Ui.ellipsize(s.displayValue(), controlWidth() - 8), cx + 4, cy - 4, Theme.text());
			}
		}

		if (!setting.isDefault()) {
			boolean resetHover = Ui.inside(mx, my, ax + resetX() - 2, cy - 6, 13, 13);
			Ui.icon(g, Icons.RESET, ax + resetX(), cy - 4, resetHover ? Theme.accent() : Theme.textFaint());
		}
	}

	@Override
	public boolean click(int ax, int ay, double mx, double my, int button) {
		int cy = ay + h / 2;
		int cx = ax + controlLeft();
		if (!setting.isDefault() && Ui.inside(mx, my, ax + resetX() - 2, cy - 6, 13, 13)) {
			MenuSettings.click();
			setting.reset();
			return true;
		}
		switch (setting.kind()) {
			case BOOLEAN -> {
				MenuSettings.click();
				((BoolSetting) setting).toggle();
				return true;
			}
			case INT -> {
				IntSetting s = (IntSetting) setting;
				if (Ui.inside(mx, my, cx, cy - 6, 36, 12)) {
					openNumberInput(s);
					return true;
				}
				if (Ui.inside(mx, my, cx + 36, cy - 8, SLIDER_W + 8, 16)) {
					dragging = true;
					host.beginDrag(this);
					s.setFraction((mx - (cx + 40)) / SLIDER_W);
					return true;
				}
				return false;
			}
			case CHOICE -> {
				ChoiceSetting<?> s = (ChoiceSetting<?>) setting;
				MenuSettings.click();
				boolean back = button == 1 || Ui.inside(mx, my, cx, cy - 7, 14, 14);
				if (back) s.previous(); else s.next();
				return true;
			}
			case COLOR -> {
				ColorSetting s = (ColorSetting) setting;
				for (int i = 0; i < QUICK_COLORS.length; i++) {
					if (Ui.inside(mx, my, cx + i * 10 - 1, cy - 5, 10, 10)) {
						MenuSettings.click();
						s.set(QUICK_COLORS[i]);
						return true;
					}
				}
				if (mx >= cx + QUICK_COLORS.length * 10) {
					openColorInput(s);
					return true;
				}
				return false;
			}
			case TEXT -> {
				openTextInput((TextSetting) setting);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean drag(int ax, int ay, double mx, double my) {
		if (!dragging || setting.kind() != Setting.Kind.INT) return false;
		((IntSetting) setting).setFraction((mx - (ax + controlLeft() + 40)) / SLIDER_W);
		return true;
	}

	@Override
	public void release() { dragging = false; }

	@Override
	public boolean scroll(int ax, int ay, double mx, double my, double amount) {
		if (setting.kind() != Setting.Kind.INT) return false;
		int cy = ay + h / 2;
		if (!Ui.inside(mx, my, ax + controlLeft(), cy - 8, controlWidth(), 16)) return false;
		IntSetting s = (IntSetting) setting;
		s.set(s.value() + (amount > 0 ? s.step() : -s.step()));
		return true;
	}

	private void openNumberInput(IntSetting s) {
		Minecraft.getInstance().setScreen(new TextInputScreen(host.asScreen(), s.name(),
				"Enter a whole number from " + s.min() + " to " + s.max() + ".", String.valueOf(s.value()), 8, text -> {
					try {
						int v = Integer.parseInt(text.trim());
						if (v < s.min() || v > s.max()) return "Must be between " + s.min() + " and " + s.max() + ".";
						s.set(v);
						return null;
					} catch (NumberFormatException e) {
						return "That isn't a whole number.";
					}
				}));
	}

	private void openColorInput(ColorSetting s) {
		Minecraft.getInstance().setScreen(new TextInputScreen(host.asScreen(), s.name(),
				"Hex color, e.g. #2F8CFF" + (s.allowsAlpha() ? " or #AARRGGBB" : ""), s.displayValue(), 9, text -> {
					Integer parsed = ColorSetting.parseHex(text);
					if (parsed == null) return "Use a hex color like #2F8CFF.";
					s.set(parsed);
					return null;
				}));
	}

	private void openTextInput(TextSetting s) {
		Minecraft.getInstance().setScreen(new TextInputScreen(host.asScreen(), s.name(), s.description(), s.get(), s.maxLength(), text -> {
			s.set(text);
			return null;
		}));
	}

	@Override
	public String hint() {
		String extra = switch (setting.kind()) {
			case INT -> "  Drag, scroll, or click the number to type it.";
			case CHOICE -> "  Click to cycle; right-click goes back.";
			case COLOR -> "  Pick a swatch or click the hex code to type one.";
			default -> "";
		};
		return setting.description() + extra;
	}

	@Override public String key() { return setting.id(); }
}
