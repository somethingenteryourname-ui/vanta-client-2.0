package vanta.hud.widgets;

import java.util.Locale;

import vanta.combat.CombatTracker;
import vanta.config.IntSetting;
import vanta.hud.TextHudModule;
import vanta.modules.Category;

public final class ReachWidget extends TextHudModule {
	private final IntSetting hideAfter = setting(new IntSetting(key("hide_after"), "Hide after",
			"Seconds before the last reach disappears. 0 = always show.", 3, 0, 30, 1, "s"));
	private long shownTime = -1;
	private boolean wasHidden;

	public ReachWidget() {
		super("reach", "Reach Display", "Distance to the player or mob you last hit. Display only.", Category.PVP, false, 500, 620);
	}

	@Override
	public void update() {
		long t = CombatTracker.lastReachTime();
		boolean hidden = t == 0 || (hideAfter.value() > 0 && System.currentTimeMillis() - t > hideAfter.value() * 1000L);
		if (t == shownTime && hidden == wasHidden) return;
		shownTime = t;
		wasHidden = hidden;
		setLine("Reach", t == 0 ? "-" : String.format(Locale.ROOT, "%.2f blocks", CombatTracker.lastReach()));
	}

	@Override
	public boolean hasContent() { return !wasHidden; }
}
