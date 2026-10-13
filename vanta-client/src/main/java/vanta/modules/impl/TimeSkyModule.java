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

	public enum SkyStyle implements Labeled {
		SOLID("One color"), GRADIENT("Gradient (top + horizon)"), THEME("Theme colors"), RAINBOW("Rainbow");
		private final String label;
		SkyStyle(String label) { this.label = label; }
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
	public final ChoiceSetting<SkyStyle> skyStyle = setting(new ChoiceSetting<>(key("sky_style"), "Sky style",
			"One color, a gradient from the top of the sky to the horizon, your theme colors, or a slow rainbow.", SkyStyle.SOLID));
	public final ColorSetting horizonColor = setting(new ColorSetting(key("horizon_color"), "Horizon color",
			"Color near the horizon for the Gradient style.", 0xFFFFB37A, false));
	public final BoolSetting keepBrightness = setting(new BoolSetting(key("keep_brightness"), "Keep day and night",
			"Your sky colors still get darker at night and in storms.", true));
	public final BoolSetting customClouds = setting(new BoolSetting(key("custom_clouds"), "Custom cloud color",
			"Give clouds your own color.", false));
	public final ColorSetting cloudColor = setting(new ColorSetting(key("cloud_color"), "Cloud color",
			"Color of clouds when Custom cloud color is on.", 0xFFFFE4F2, false));
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

	private static boolean skyOn() { return instance != null && instance.isEnabled() && instance.customSky.on(); }

	/** Sky (top) color override as RGB for the given vanilla color, or -1 to keep vanilla. */
	public static int skyColor(int original) {
		if (!skyOn()) return -1;
		TimeSkyModule m = instance;
		int c = switch (m.skyStyle.get()) {
			case THEME -> vanta.branding.Theme.accent();
			case RAINBOW -> vanta.branding.Theme.chroma(0f);
			default -> m.skyColor.argb();
		};
		return m.shade(c & 0xFFFFFF, original);
	}

	/** Fog/horizon color override as RGB, or -1. */
	public static int fogColor(int original) {
		if (!skyOn() || !instance.fogToo.on()) return -1;
		TimeSkyModule m = instance;
		int c = switch (m.skyStyle.get()) {
			case GRADIENT -> m.horizonColor.argb();
			case THEME -> vanta.branding.Theme.secondary();
			case RAINBOW -> vanta.branding.Theme.chroma(0.35f);
			default -> m.skyColor.argb();
		};
		return m.shade(c & 0xFFFFFF, original);
	}

	/** Cloud color override as ARGB (keeps vanilla's alpha), or 0 to keep vanilla. */
	public static int cloudColor(int originalArgb) {
		if (instance == null || !instance.isEnabled() || !instance.customClouds.on()) return 0;
		return (originalArgb & 0xFF000000) | instance.shade(instance.cloudColor.argb() & 0xFFFFFF, originalArgb & 0xFFFFFF);
	}

	/** Scales a chosen color by how bright vanilla's color is (night and storms stay dark). */
	private int shade(int rgb, int original) {
		if (!keepBrightness.on()) return rgb;
		return scaleBrightness(rgb, original);
	}

	public static int scaleBrightness(int rgb, int reference) {
		float lum = luminance(reference & 0xFFFFFF) / luminance(0x78A7FF);
		float k = Math.max(0.04f, Math.min(1f, lum));
		int r = Math.round(((rgb >> 16) & 0xFF) * k), g = Math.round(((rgb >> 8) & 0xFF) * k), b = Math.round((rgb & 0xFF) * k);
		return (r << 16) | (g << 8) | b;
	}

	private static float luminance(int rgb) {
		return 0.2126f * ((rgb >> 16) & 0xFF) + 0.7152f * ((rgb >> 8) & 0xFF) + 0.0722f * (rgb & 0xFF);
	}
}
