package vanta.hud.widgets;

import vanta.combat.CombatTracker;
import vanta.config.BoolSetting;
import vanta.hud.TextHudModule;
import vanta.modules.Category;

public final class ComboWidget extends TextHudModule {
	private final BoolSetting hideZero = setting(new BoolSetting(key("hide_zero"), "Hide when 0",
			"Only show while you have a combo going.", true));
	private int shown = -1;

	public ComboWidget() {
		super("combo", "Combo Display", "Hits in a row without getting hit back.", Category.PVP, false, 500, 680);
	}

	@Override
	public void update() {
		int c = CombatTracker.combo();
		if (c == shown) return;
		shown = c;
		setLine("Combo", c + (c == 1 ? " hit" : " hits"));
	}

	@Override
	public boolean hasContent() { return !(hideZero.on() && shown <= 0); }
}
