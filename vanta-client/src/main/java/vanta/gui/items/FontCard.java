package vanta.gui.items;

import net.minecraft.client.gui.GuiGraphics;

import vanta.config.ChoiceSetting;
import vanta.branding.Theme;
import vanta.gui.MenuSettings;
import vanta.ui.Colors;
import vanta.ui.Ui;
import vanta.ui.UiFont;

/**
 * One font in the font picker: its name and a sample line, both drawn in that font.
 * Click to use it for the setting this picker edits (menus or HUD).
 */
public final class FontCard extends Item {
	private static final String SAMPLE = "Aa Bb 123 VANTA";

	private final UiFont font;
	private final ChoiceSetting<UiFont> target;
	private final MenuHost host;

	public FontCard(UiFont font, ChoiceSetting<UiFont> target, MenuHost host) {
		this.font = font;
		this.target = target;
		this.host = host;
	}

	public static int height() { return 34; }

	@Override
	public void render(GuiGraphics g, int ax, int ay, int mx, int my, boolean hovered) {
		boolean current = target.get() == font;
		Ui.card(g, ax, ay, w, h, hovered, current ? Theme.accent() : 0);
		int maxW = w - 14;
		String name = font.label();
		if (Ui.widthIn(font, name) > maxW) name = fit(name, maxW);
		Ui.drawIn(g, font, name, ax + 7, ay + 7, current ? Theme.accent() : Theme.text(), Theme.TEXT_SHADOW.on());
		String sample = Ui.widthIn(font, SAMPLE) <= maxW ? SAMPLE : fit(SAMPLE, maxW);
		Ui.drawIn(g, font, sample, ax + 7, ay + 19, Colors.withAlpha(Theme.TEXT.argb(), 0.55f), false);
		if (current) Ui.rect(g, ax + w - 9, ay + 7, 3, 3, Theme.accent());
	}

	private String fit(String s, int maxW) {
		int end = s.length();
		while (end > 1 && Ui.widthIn(font, s.substring(0, end)) > maxW) end--;
		return s.substring(0, end);
	}

	@Override
	public boolean click(int ax, int ay, double mx, double my, int button) {
		MenuSettings.click();
		target.set(font);
		host.rebuild();
		return true;
	}

	@Override public String hint() { return font.label() + ": " + font.description; }

	@Override public String key() { return target.id(); }
}
