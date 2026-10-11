package vanta.hud.widgets;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import vanta.config.BoolSetting;
import vanta.hud.TextHudModule;
import vanta.modules.Category;

public final class ClockWidget extends TextHudModule {
	private final BoolSetting twelveHour = setting(new BoolSetting(key("twelve_hour"), "12-hour clock",
			"Show AM/PM time instead of 24-hour.", true));
	private final BoolSetting seconds = setting(new BoolSetting(key("seconds"), "Show seconds", "Include seconds.", false));
	private String last = "";

	public ClockWidget() {
		super("clock", "Clock", "Your computer's current time.", Category.HUD, false, 1000, 420);
	}

	@Override
	public void update() {
		String pattern = (twelveHour.on() ? "h:mm" : "HH:mm") + (seconds.on() ? ":ss" : "") + (twelveHour.on() ? " a" : "");
		String now = LocalTime.now().format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
		if (now.equals(last)) return;
		last = now;
		setLine("Time", now);
	}
}
