package vanta.hud.widgets;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import vanta.config.BoolSetting;
import vanta.hud.TextHudModule;
import vanta.modules.Category;

public final class SpeedWidget extends TextHudModule {
	private final BoolSetting vertical = setting(new BoolSetting(key("vertical"), "Include vertical",
			"Count up/down movement too (useful for elytra).", false));
	private long nextRefresh;

	public SpeedWidget() {
		super("speed", "Speed", "How fast you're moving, in blocks per second.", Category.HUD, false, 1000, 480);
	}

	@Override
	public void update() {
		long now = System.currentTimeMillis();
		if (now < nextRefresh) return;
		nextRefresh = now + 100;
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) {
			setLine("Speed", "-");
			return;
		}
		double dx = p.getX() - p.xo, dy = vertical.on() ? p.getY() - p.yo : 0, dz = p.getZ() - p.zo;
		double bps = Math.sqrt(dx * dx + dy * dy + dz * dz) * 20.0;
		setLine("Speed", String.format(Locale.ROOT, "%.1f b/s", bps));
	}
}
