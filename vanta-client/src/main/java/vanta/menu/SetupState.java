package vanta.menu;

import vanta.config.BoolSetting;
import vanta.config.SettingRegistry;
import vanta.gui.MenuPage;

/** Remembers whether the first-launch setup has been done (registered before settings load). */
public final class SetupState {
	public static final BoolSetting DONE = (BoolSetting) SettingRegistry.register(new BoolSetting("setup.done", "Setup finished",
			"Whether the first-launch setup has been completed or skipped.", false), MenuPage.SETTINGS).hidden();

	private SetupState() {
	}

	public static void init() {
		// loading the class registers the setting
	}
}
