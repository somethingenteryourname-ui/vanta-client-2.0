package vanta.ui;

import vanta.branding.Theme;

/** Time-based animation helpers that respect the theme's speed and reduced-motion settings. */
public final class Anim {
	private Anim() {
	}

	/** Progress 0..1 of an animation that started at startMs and lasts baseMs (scaled by theme speed). */
	public static float progress(long startMs, float baseMs) {
		float scale = Theme.animationTimeScale();
		if (scale <= 0f) return 1f;
		float t = (System.currentTimeMillis() - startMs) / (baseMs * scale);
		return Math.max(0f, Math.min(1f, t));
	}

	public static float easeOutCubic(float t) {
		float u = 1f - t;
		return 1f - u * u * u;
	}

	/** Frame-rate independent smoothing of current toward target. */
	public static float approach(float current, float target, float deltaSeconds, float speed) {
		if (Theme.animationTimeScale() <= 0f) return target;
		float k = 1f - (float) Math.exp(-speed / Theme.animationTimeScale() * deltaSeconds);
		float next = current + (target - current) * k;
		return Math.abs(target - next) < 0.05f ? target : next;
	}
}
