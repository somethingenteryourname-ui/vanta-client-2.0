package vanta.modules.impl;

import vanta.config.BoolSetting;
import vanta.config.ChoiceSetting;
import vanta.config.ColorSetting;
import vanta.config.IntSetting;
import vanta.config.Labeled;
import vanta.modules.Category;
import vanta.modules.Module;

/**
 * Time and sky: pick the time of day you see and tint the sky. Only changes your screen;
 * the real time on the server (mobs, crops, beds) is untouched.
 */
public final class TimeSkyModule extends Module {
	public enum Time implements Labeled {
		SERVER("Server time", -1), SUNRISE("Sunrise", 23200), DAY("Day", 6000), SUNSET("Sunset", 12400),
		NIGHT("Night", 18000), CUSTOM("Custom", -2);
		private final String label;
		final int ticks;
		Time(String label, int ticks) { this.label = label; this.ticks = ticks; }
		@Override public String label() { return label; }
	}

	private static TimeSkyModule instance;

	public final ChoiceSetting<Time> time = setting(new ChoiceSetting<>(key("time"), "Time of day",
			"The time you see. Server time keeps the real cycle.", Time.DAY));
	public final IntSetting customTime = setting(new IntSetting(key("custom_time"), "Custom time",
			"Used when Time of day is Custom. 0 = sunrise, 6000 = noon, 12000 = sunset, 18000 = midnight.", 6000, 0, 23999, 250, ""));
	public final BoolSetting customSky = setting(new BoolSetting(key("custom_sky"), "Custom sky color",
			"Replace the sky color with your own.", false));
	public final ColorSetting skyColor = setting(new ColorSetting(key("sky_color"), "Sky color",
			"Color of the sky when Custom sky color is on.", 0xFF7DA7FF, false));
	public final BoolSetting fogToo = setting(new BoolSetting(key("fog_too"), "Match fog",
			"Also tint the distance fog so the horizon blends into your sky.", true));

	public TimeSkyModule() {
		super("time_sky", "Time & Sky", "See the time of day you like and give the sky your own color. Only changes your screen.",
				Category.VISUALS, false);
		instance = this;
	}

	/** Day time to show, or -1 for the real one. */
	public static long dayTime(long real) {
		if (instance == null || !instance.isEnabled()) return -1;
		Time t = instance.time.get();
		if (t == Time.SERVER) return -1;
		int ticks = t == Time.CUSTOM ? instance.customTime.value() : t.ticks;
		return (real / 24000L) * 24000L + ticks; // keep the day count (moon phase)
	}

	/** Sky color override (RGB), or -1. */
	public static int skyColor() {
		return instance != null && instance.isEnabled() && instance.customSky.on() ? instance.skyColor.argb() & 0xFFFFFF : -1;
	}

	public static int fogColor() {
		return instance != null && instance.isEnabled() && instance.customSky.on() && instance.fogToo.on() ? instance.skyColor.argb() & 0xFFFFFF : -1;
	}
}
