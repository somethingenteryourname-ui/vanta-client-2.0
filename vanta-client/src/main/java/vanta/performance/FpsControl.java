package vanta.performance;

import com.mojang.blaze3d.platform.FramerateLimitTracker;

import net.minecraft.client.Minecraft;

import vanta.optimize.OptimizationsModule;

/**
 * Decides the final frame-rate cap each frame.
 *
 * The random "stuck at 30 FPS" drops come from Minecraft's own idle limiter: with
 * "Reduce FPS when: AFK" it drops to 30 FPS after a minute without input, and on some
 * setups it misses input (raw mouse, controllers, overlays) so it kicks in while you
 * play. VANTA's "Stop random 30 FPS drops" ignores that limiter whenever the window is
 * focused. After that come the per-screen caps and the tabbed-out cap.
 */
public final class FpsControl {
	/** Why the frame rate is capped right now, for the Performance page. */
	private static volatile String reason = "Nothing";

	private FpsControl() {
	}

	public static String reason() { return reason; }

	public static int limit(int vanilla, int userLimit, FramerateLimitTracker.FramerateThrottleReason throttle) {
		Minecraft mc = Minecraft.getInstance();
		int v = vanilla;
		String why = v < userLimit ? describe(throttle) : (userLimit < 260 ? "Max framerate setting (" + userLimit + ")" : "Nothing");
		int fixed = afterIdleFix(v, userLimit, throttle, mc != null && mc.isWindowActive(), OptimizationsModule.noIdleThrottle());
		if (fixed != v) {
			v = fixed;
			why = userLimit < 260 ? "Max framerate setting (" + userLimit + ")" : "Nothing";
		}
		int screen = ScreenFpsModule.apply(v, mc);
		if (screen < v) {
			v = screen;
			why = "Menu FPS cap (" + v + ")";
		}
		int bg = BackgroundFpsModule.apply(v);
		if (bg < v) {
			v = bg;
			why = "Tabbed out (" + v + ")";
		}
		reason = why;
		return v;
	}

	/** The idle-limit fix on its own: AFK caps are dropped while the window is focused. */
	public static int afterIdleFix(int vanilla, int userLimit, FramerateLimitTracker.FramerateThrottleReason throttle, boolean focused, boolean fixOn) {
		boolean idle = throttle == FramerateLimitTracker.FramerateThrottleReason.SHORT_AFK
				|| throttle == FramerateLimitTracker.FramerateThrottleReason.LONG_AFK;
		return idle && fixOn && focused ? userLimit : vanilla;
	}

	private static String describe(FramerateLimitTracker.FramerateThrottleReason r) {
		return switch (r) {
			case WINDOW_ICONIFIED -> "Window minimized";
			case SHORT_AFK -> "Minecraft's AFK limit (30)";
			case LONG_AFK -> "Minecraft's long AFK limit (10)";
			case OUT_OF_LEVEL_MENU -> "Main menu limit (60)";
			default -> "Nothing";
		};
	}
}
