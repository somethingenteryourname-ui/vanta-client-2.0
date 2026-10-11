package vanta.performance;

import vanta.config.SettingRegistry;
import vanta.config.TextSetting;
import vanta.gui.MenuPage;

/** Remembers which performance preset was applied last (shown on the dashboard). */
public final class PerformanceState {
	public static final TextSetting ACTIVE_PRESET = SettingRegistry.register(
			(TextSetting) new TextSetting("performance.active_preset", "Active preset", "Last applied performance preset.", "Custom", 32).hidden(),
			MenuPage.PERFORMANCE);

	private PerformanceState() {
	}

	public static void init() {
	}
}
