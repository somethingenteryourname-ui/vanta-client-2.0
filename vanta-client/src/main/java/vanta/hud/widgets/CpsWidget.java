package vanta.hud.widgets;

import vanta.config.BoolSetting;
import vanta.hud.TextHudModule;
import vanta.metrics.InputStats;
import vanta.modules.Category;

public final class CpsWidget extends TextHudModule {
	private final BoolSetting showRight = setting(new BoolSetting(key("show_right"), "Show right clicks",
			"Show right-click CPS next to left-click CPS.", true));
	private int lastKey = -1;

	public CpsWidget() {
		super("cps", "CPS", "Clicks per second over the last second. Display only; it never clicks for you.", Category.PVP, false, 1000, 0);
	}

	@Override
	public boolean needsInputStats() { return true; }

	@Override
	public void update() {
		int l = InputStats.leftCps();
		int r = InputStats.rightCps();
		int key = l * 1000 + r * 2 + (showRight.on() ? 1 : 0);
		if (key == lastKey) return;
		lastKey = key;
		setLine("CPS", showRight.on() ? l + " | " + r : String.valueOf(l));
	}
}
