package vanta.modules.impl;

import vanta.config.BoolSetting;
import vanta.config.IntSetting;
import vanta.modules.Category;
import vanta.modules.Module;

/** Resize or hide the boss bar, scoreboard, titles and vanilla potion icons. */
public final class HudTweaksModule extends Module {
	private static HudTweaksModule instance;

	public final IntSetting bossBar = setting(new IntSetting(key("boss_bar"), "Boss bar size",
			"0 hides it.", 100, 0, 150, 5, "%"));
	public final IntSetting scoreboard = setting(new IntSetting(key("scoreboard"), "Scoreboard size",
			"Sidebar scoreboard size. 0 hides it.", 100, 0, 150, 5, "%"));
	public final IntSetting title = setting(new IntSetting(key("title"), "Title size",
			"Big on-screen titles. 0 hides them.", 100, 0, 150, 5, "%"));
	public final BoolSetting hideEffects = setting(new BoolSetting(key("hide_effects"), "Hide vanilla effect icons",
			"Hide the top-right potion icons (use the Potion Effects widget instead).", false));

	public HudTweaksModule() {
		super("hud_tweaks", "HUD Tweaks", "Resize or hide the boss bar, scoreboard, titles and vanilla effect icons.",
				Category.INTERFACE, false);
		instance = this;
	}

	private static boolean on() { return instance != null && instance.isEnabled(); }

	public static float bossBarScale() { return on() ? instance.bossBar.value() / 100f : 1f; }
	public static float scoreboardScale() { return on() ? instance.scoreboard.value() / 100f : 1f; }
	public static float titleScale() { return on() ? instance.title.value() / 100f : 1f; }
	public static boolean hideEffects() { return on() && instance.hideEffects.on(); }
}
