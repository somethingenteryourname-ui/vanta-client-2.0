package vanta.hud.widgets;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;

import vanta.branding.Theme;
import vanta.config.BoolSetting;
import vanta.hud.HudModule;
import vanta.metrics.InputStats;
import vanta.modules.Category;
import vanta.ui.Colors;
import vanta.ui.Ui;

/** Shows movement keys and mouse buttons lighting up as you press them. Display only. */
public final class KeystrokesWidget extends HudModule {
	private static final int KEY = 18;
	private static final int GAP = 2;

	private final BoolSetting showMouse = setting(new BoolSetting(key("show_mouse"), "Show mouse buttons",
			"Show left and right mouse buttons.", true));
	private final BoolSetting showSpace = setting(new BoolSetting(key("show_space"), "Show jump bar",
			"Show a bar for the jump key.", true));
	private final BoolSetting showCps = setting(new BoolSetting(key("show_cps"), "CPS on mouse buttons",
			"Show clicks per second inside the mouse buttons.", true));

	private final String[] keyLabels = new String[4];
	private long nextLabelRefresh;

	public KeystrokesWidget() {
		super("keystrokes", "Keystrokes", "Shows WASD, jump and mouse buttons as you press them. Display only.", Category.PVP, false, 1000, 120);
	}

	@Override
	public boolean needsInputStats() { return showMouse.on() && showCps.on(); }

	@Override
	public int width() { return KEY * 3 + GAP * 2; }

	@Override
	public int height() {
		int rows = 2;
		if (showMouse.on()) rows++;
		int h = rows * KEY + (rows - 1) * GAP;
		if (showSpace.on()) h += GAP + 8;
		return h;
	}

	@Override
	public void update() {
		long now = System.currentTimeMillis();
		if (now < nextLabelRefresh) return;
		nextLabelRefresh = now + 2000; // key names only change when rebinding
		Options o = Minecraft.getInstance().options;
		keyLabels[0] = shortName(o.keyUp, "W");
		keyLabels[1] = shortName(o.keyLeft, "A");
		keyLabels[2] = shortName(o.keyDown, "S");
		keyLabels[3] = shortName(o.keyRight, "D");
	}

	private static String shortName(KeyMapping key, String fallback) {
		String s = key.getTranslatedKeyMessage().getString();
		if (s.isEmpty()) return fallback;
		return s.length() > 3 ? s.substring(0, 3) : s;
	}

	@Override
	protected void renderContent(GuiGraphics g, boolean editor) {
		Options o = Minecraft.getInstance().options;
		int y = 0;
		drawKey(g, KEY + GAP, y, KEY, KEY, keyLabels[0], o.keyUp.isDown(), null);
		y += KEY + GAP;
		drawKey(g, 0, y, KEY, KEY, keyLabels[1], o.keyLeft.isDown(), null);
		drawKey(g, KEY + GAP, y, KEY, KEY, keyLabels[2], o.keyDown.isDown(), null);
		drawKey(g, (KEY + GAP) * 2, y, KEY, KEY, keyLabels[3], o.keyRight.isDown(), null);
		y += KEY + GAP;
		if (showMouse.on()) {
			int half = (width() - GAP) / 2;
			String lc = showCps.on() ? String.valueOf(InputStats.leftCps()) : null;
			String rc = showCps.on() ? String.valueOf(InputStats.rightCps()) : null;
			drawKey(g, 0, y, half, KEY, "LMB", o.keyAttack.isDown(), lc);
			drawKey(g, half + GAP, y, width() - half - GAP, KEY, "RMB", o.keyUse.isDown(), rc);
			y += KEY + GAP;
		}
		if (showSpace.on()) {
			boolean down = o.keyJump.isDown();
			Ui.roundRect(g, 0, y, width(), 8, Theme.radius(), down ? Theme.accent() : Colors.withAlpha(Theme.background(), 0.55f));
			Ui.rect(g, width() / 2 - 8, y + 3, 16, 1, down ? Colors.contrastText(Theme.accent()) : textColor.argb());
		}
	}

	private void drawKey(GuiGraphics g, int x, int y, int w, int h, String label, boolean down, String sub) {
		if (label == null) label = "?";
		int fill = down ? Theme.accent() : Colors.withAlpha(Theme.background(), 0.55f);
		Ui.roundRect(g, x, y, w, h, Theme.radius(), fill);
		int color = down ? Colors.contrastText(Theme.accent()) : textColor.argb();
		if (sub == null) {
			int tw = Ui.width(label);
			drawText(g, label, x + (w - tw) / 2, y + (h - 8) / 2, color);
		} else {
			// Small CPS number under the label.
			int tw = Ui.width(label);
			drawText(g, label, x + (w - tw) / 2, y + 2, color);
			g.pose().pushMatrix();
			g.pose().translate(x + w / 2f, y + 11);
			g.pose().scale(0.5f);
			int sw = Ui.width(sub);
			g.drawString(Ui.font(), sub, -sw / 2, 0, color, false);
			g.pose().popMatrix();
		}
	}
}
