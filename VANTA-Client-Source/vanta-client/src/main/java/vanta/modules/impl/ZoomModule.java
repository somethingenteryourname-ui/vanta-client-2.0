package vanta.modules.impl;

import net.minecraft.client.Minecraft;

import vanta.config.BoolSetting;
import vanta.config.ChoiceSetting;
import vanta.config.IntSetting;
import vanta.input.VantaKeys;
import vanta.modules.Category;
import vanta.modules.Module;

/**
 * Zoom (default key C). Lowers the field of view while active; optional smooth
 * transition, scroll-to-adjust, reduced mouse sensitivity and cinematic camera.
 * Purely a camera change, like a spyglass.
 */
public final class ZoomModule extends Module {
	private static ZoomModule instance;

	public final IntSetting level = setting(new IntSetting(key("level"), "Zoom level",
			"How far you zoom in (times).", 4, 2, 30, 1, "x"));
	public final ChoiceSetting<ActivationMode> mode = setting(new ChoiceSetting<>(key("mode"), "Activation",
			"Hold the key to zoom, or press once to toggle.", ActivationMode.HOLD));
	public final BoolSetting smooth = setting(new BoolSetting(key("smooth"), "Smooth zoom",
			"Animate in and out instead of snapping.", true));
	public final BoolSetting scroll = setting(new BoolSetting(key("scroll"), "Scroll to adjust",
			"Mouse wheel changes zoom while zooming (hotbar won't scroll).", true));
	public final BoolSetting reduceSensitivity = setting(new BoolSetting(key("reduce_sensitivity"), "Lower sensitivity",
			"Scale mouse sensitivity down while zoomed so aiming stays steady.", true));
	public final BoolSetting cinematic = setting(new BoolSetting(key("cinematic"), "Cinematic camera",
			"Use smooth camera while zoomed.", false));

	private boolean active;
	private float scrollFactor = 1f;
	private float current = 1f;
	private long lastFrame;
	private boolean restoreSmooth;

	public ZoomModule() {
		super("zoom", "Zoom", "Zoom in with a key (C by default). Change the key in Controls.", Category.VISUALS, true);
		instance = this;
	}

	@Override
	public void tick(Minecraft mc) {
		boolean want;
		if (mode.get() == ActivationMode.HOLD) {
			want = mc.screen == null && VantaKeys.ZOOM.isDown();
			while (VantaKeys.ZOOM.consumeClick()) {
				// drain presses so they don't pile up
			}
		} else {
			want = active;
			while (VantaKeys.ZOOM.consumeClick()) want = !want;
		}
		if (want != active) setActive(mc, want);
	}

	private void setActive(Minecraft mc, boolean on) {
		active = on;
		scrollFactor = 1f;
		if (cinematic.on()) {
			if (on) {
				restoreSmooth = mc.options.smoothCamera;
				mc.options.smoothCamera = true;
			} else {
				mc.options.smoothCamera = restoreSmooth;
			}
		}
	}

	@Override
	protected void onDisable() {
		if (active) setActive(Minecraft.getInstance(), false);
	}

	/** Multiplier applied to the final FOV each frame (1 = no zoom). */
	public static float fovMultiplier() {
		ZoomModule z = instance;
		if (z == null) return 1f;
		float target = z.isEnabled() && z.active ? 1f / Math.max(1f, z.level.value() * z.scrollFactor) : 1f;
		long now = System.nanoTime();
		float dt = z.lastFrame == 0 ? 0f : Math.min(0.1f, (now - z.lastFrame) / 1e9f);
		z.lastFrame = now;
		if (!z.smooth.on()) {
			z.current = target;
		} else {
			float k = 1f - (float) Math.exp(-14f * dt);
			z.current += (target - z.current) * k;
			if (Math.abs(z.current - target) < 0.001f) z.current = target;
		}
		return z.current;
	}

	public static boolean isZooming() {
		return instance != null && instance.isEnabled() && instance.active;
	}

	/** Mouse sensitivity multiplier while zoomed. */
	public static double sensitivityScale() {
		ZoomModule z = instance;
		if (z == null || !isZooming() || !z.reduceSensitivity.on()) return 1.0;
		return Math.max(0.05, z.current);
	}

	/** Returns true if the scroll was used to change zoom. */
	public static boolean onScroll(double amount) {
		ZoomModule z = instance;
		if (z == null || !isZooming() || !z.scroll.on() || amount == 0) return false;
		z.scrollFactor *= amount > 0 ? 1.15f : 1f / 1.15f;
		z.scrollFactor = Math.max(0.35f, Math.min(6f, z.scrollFactor));
		return true;
	}

	@Override
	public String status() {
		return "Key: " + VantaKeys.name(VantaKeys.ZOOM) + "  -  " + level.value() + "x";
	}
}
