package vanta.hud.widgets;

import net.minecraft.client.Minecraft;

import vanta.config.BoolSetting;
import vanta.hud.TextHudModule;
import vanta.modules.Category;
import vanta.net.RegionLookup;

/** Ping to the server plus the server's region (NA East, NA Central, EU West...). */
public final class PingWidget extends TextHudModule {
	private final BoolSetting showRegion = setting(new BoolSetting(key("show_region"), "Show region",
			"Show the server's region after your ping, e.g. 53 ms (NA Central).", true));
	private final BoolSetting separateLine = setting(new BoolSetting(key("separate_line"), "Region on its own line",
			"Put the region on a second line instead of after the ping.", false));
	private long nextRefresh;

	public PingWidget() {
		super("ping", "Ping", "Your latency to the server and the server's region (0 ms / Local in singleplayer).", Category.HUD, false, 0, 240);
	}

	@Override
	public void update() {
		long now = System.currentTimeMillis();
		if (now < nextRefresh) return;
		nextRefresh = now + 1000;
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null || mc.player == null) {
			setLine("Ping", "-");
			return;
		}
		String ping = vanta.net.LivePing.text(mc);
		String region = showRegion.on() ? RegionLookup.current() : "";
		if (region.isEmpty()) {
			setLine("Ping", ping);
		} else if (separateLine.on()) {
			setLines(new String[]{"Ping", "Region"}, new String[]{ping, region});
		} else {
			setLine("Ping", ping + " (" + region + ")");
		}
	}
}
