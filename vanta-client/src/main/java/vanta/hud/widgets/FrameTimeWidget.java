package vanta.hud.widgets;

import java.util.Locale;

import vanta.config.BoolSetting;
import vanta.hud.TextHudModule;
import vanta.metrics.FrameMetrics;
import vanta.modules.Category;

public final class FrameTimeWidget extends TextHudModule {
	private final BoolSetting showWorst = setting(new BoolSetting(key("show_worst"), "Show worst frame",
			"Also show the slowest frame in the last ~5 seconds.", false));
	private long version = -1;
	private boolean lastShowWorst;

	public FrameTimeWidget() {
		super("frame_time", "Frame Time", "Average time per frame in milliseconds. Lower and steadier is smoother.", Category.HUD, false, 0, 60);
	}

	@Override
	public void update() {
		if (version == FrameMetrics.version() && lastShowWorst == showWorst.on()) return;
		version = FrameMetrics.version();
		lastShowWorst = showWorst.on();
		String avg = String.format(Locale.ROOT, "%.1f ms", FrameMetrics.averageFrameMs());
		if (showWorst.on()) {
			setLines(new String[]{"Frame", "Worst"}, new String[]{avg, String.format(Locale.ROOT, "%.1f ms", FrameMetrics.worstFrameMs())});
		} else {
			setLine("Frame", avg);
		}
	}
}
