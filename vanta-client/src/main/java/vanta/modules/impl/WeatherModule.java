package vanta.modules.impl;

import vanta.config.BoolSetting;
import vanta.modules.Category;
import vanta.modules.Module;

/** Weather: hide rain, snow and thunder storms on your screen. The server's weather is unchanged. */
public final class WeatherModule extends Module {
	private static WeatherModule instance;

	public final BoolSetting rain = setting(new BoolSetting(key("rain"), "Hide rain and snow",
			"No falling rain or snow, splashes, rain sounds or darker sky.", true));
	public final BoolSetting thunder = setting(new BoolSetting(key("thunder"), "Hide thunderstorms",
			"No storm darkening. Lightning bolts still appear (they can start fires).", true));

	public WeatherModule() {
		super("weather", "Weather", "Turn off rain, snow and storm darkness on your screen.", Category.VISUALS, false);
		instance = this;
	}

	public static boolean hideRain() { return instance != null && instance.isEnabled() && instance.rain.on(); }
	public static boolean hideThunder() { return instance != null && instance.isEnabled() && (instance.thunder.on() || instance.rain.on()); }
}
