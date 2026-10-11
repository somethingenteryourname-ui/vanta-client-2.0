package vanta.performance;

import net.minecraft.client.Minecraft;

import vanta.config.IntSetting;
import vanta.modules.Category;
import vanta.modules.Module;

/**
 * Caps the frame rate while Minecraft isn't the focused window, so tabbing out to
 * Discord or a browser stops your GPU running flat out. Full speed returns the moment
 * you click back in.
 */
public final class BackgroundFpsModule extends Module {
	private static BackgroundFpsModule instance;

	public final IntSetting unfocused = setting(new IntSetting(key("unfocused"), "FPS when tabbed out",
			"Frame rate cap while another window is focused.", 15, 1, 60, 1, " FPS"));

	public BackgroundFpsModule() {
		super("background_fps", "Background FPS", "Lowers FPS while you're tabbed out to cut heat, noise and power use.",
				Category.PERFORMANCE, true);
		instance = this;
	}

	public static int apply(int limit) {
		BackgroundFpsModule m = instance;
		if (m == null || !m.isEnabled()) return limit;
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.isWindowActive()) return limit;
		return Math.min(limit, m.unfocused.value());
	}

	@Override
	public String status() { return unfocused.value() + " FPS when unfocused"; }
}
