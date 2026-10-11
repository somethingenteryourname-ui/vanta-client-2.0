package vanta.hud.widgets;

import vanta.config.BoolSetting;
import vanta.hud.TextHudModule;
import vanta.modules.Category;

public final class MemoryWidget extends TextHudModule {
	private final BoolSetting percent = setting(new BoolSetting(key("percent"), "Show percent",
			"Show memory use as a percentage of the maximum.", true));
	private long nextRefresh;

	public MemoryWidget() {
		super("memory", "Memory", "Java memory currently used by Minecraft.", Category.HUD, false, 0, 300);
	}

	@Override
	public void update() {
		long now = System.currentTimeMillis();
		if (now < nextRefresh) return;
		nextRefresh = now + 500;
		Runtime rt = Runtime.getRuntime();
		long used = (rt.totalMemory() - rt.freeMemory()) >> 20;
		long max = rt.maxMemory() >> 20;
		String value = used + " / " + max + " MB";
		if (percent.on() && max > 0) value += " (" + (used * 100 / max) + "%)";
		setLine("Mem", value);
	}
}
