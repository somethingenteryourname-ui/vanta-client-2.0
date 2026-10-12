package vanta.modules.impl;

import vanta.branding.Theme;
import vanta.config.BoolSetting;
import vanta.config.IntSetting;
import vanta.modules.Category;
import vanta.modules.Module;
import vanta.ui.Colors;

/** Tab list: exact ping numbers instead of bars, and theme colored backgrounds. */
public final class TabListModule extends Module {
	private static TabListModule instance;

	public final BoolSetting pingNumbers = setting(new BoolSetting(key("ping_numbers"), "Ping numbers",
			"Show each player's ping in ms (colored by quality) instead of signal bars.", true));
	public final BoolSetting themed = setting(new BoolSetting(key("themed"), "VANTA colors",
			"Tint the tab list panel and rows with your theme.", true));
	public final IntSetting opacity = setting(new IntSetting(key("opacity"), "Background opacity",
			"How solid the tab list panel is.", 55, 10, 100, 5, "%"));

	public TabListModule() {
		super("tab_list", "Tab List", "Exact ping numbers in the player list and a themed look.", Category.INTERFACE, false);
		instance = this;
	}

	public static boolean active() { return instance != null && instance.isEnabled(); }
	public static boolean pingNumbers() { return active() && instance.pingNumbers.on(); }

	/** Extra slot width so ping numbers fit (vanilla reserves room for a 10 px icon). */
	public static int extraWidth() { return pingNumbers() ? 14 : 0; }

	public static int pingColor(int ms) {
		if (ms < 0) return 0xFF9CA3AF;
		return ms < 80 ? 0xFF4ADE80 : ms < 150 ? 0xFFA3E635 : ms < 250 ? 0xFFFACC15 : ms < 400 ? 0xFFFB923C : 0xFFF87171;
	}

	/** Recolors the tab list backgrounds. Vanilla uses 0x80000000 for panels and a faint white for rows. */
	public static int recolor(int color) {
		if (!active() || !instance.themed.on()) return color;
		float a = instance.opacity.value() / 100f;
		if (color == Integer.MIN_VALUE) return Colors.withAlpha(Theme.background(), a);
		return Colors.withAlpha(Theme.panelRaised(), Math.min(1f, a * 0.7f));
	}
}
