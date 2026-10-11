package vanta.hud.widgets;

import vanta.config.BoolSetting;
import vanta.hud.TextHudModule;
import vanta.metrics.FrameMetrics;
import vanta.modules.Category;

public final class FpsWidget extends TextHudModule {
	private final BoolSetting showLow = setting(new BoolSetting(key("show_low"), "Show 1% low",
			"Also show the average FPS of your slowest 1% of frames (a stutter indicator).", false));
	private long version = -1;
	private boolean lastShowLow;

	public FpsWidget() {
		super("fps", "FPS", "Live frames per second, measured from real frame times.", Category.HUD, true, 0, 0);
	}

	@Override
	public void update() {
		if (version == FrameMetrics.version() && lastShowLow == showLow.on()) return;
		version = FrameMetrics.version();
		lastShowLow = showLow.on();
		String fps = String.valueOf(FrameMetrics.fps());
		if (showLow.on()) {
			float low = FrameMetrics.onePercentLow();
			setLines(new String[]{"FPS", "1% low"}, new String[]{fps, Float.isNaN(low) ? "..." : String.valueOf(Math.round(low))});
		} else {
			setLine("FPS", fps);
		}
	}

	@Override
	public String status() { return FrameMetrics.fps() + " FPS"; }
}
