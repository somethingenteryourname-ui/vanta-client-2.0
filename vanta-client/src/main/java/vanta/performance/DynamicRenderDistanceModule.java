package vanta.performance;

import net.minecraft.client.Minecraft;

import vanta.config.IntSetting;
import vanta.metrics.FrameMetrics;
import vanta.modules.Category;
import vanta.modules.Module;

/**
 * Dynamic render distance: when FPS falls below your target, render distance steps down
 * one chunk at a time; when there's headroom it climbs back up to your normal distance.
 * Your normal distance is restored when you turn this off.
 */
public final class DynamicRenderDistanceModule extends Module {
	public final IntSetting target = setting(new IntSetting(key("target"), "Target FPS",
			"Keep FPS at or above this. Render distance drops when you go under it.", 60, 30, 240, 5, " FPS"));
	public final IntSetting minimum = setting(new IntSetting(key("minimum"), "Lowest distance",
			"Never go below this many chunks.", 6, 2, 32, 1, " ch"));
	public final IntSetting normal = setting(new IntSetting(key("normal"), "Normal distance",
			"The distance to use when FPS is fine (your usual render distance).", 12, 2, 32, 1, " ch"));
	/** The real render distance from before the module took over, restored on disable. */
	private final IntSetting saved = (IntSetting) setting(new IntSetting(key("saved"), "Saved distance", "", 0, 0, 32, 1, "")).hidden();

	private long lastCheck, lastChange;
	private int current = -1;

	public DynamicRenderDistanceModule() {
		super("dynamic_render_distance", "Dynamic Render Distance", "Lowers render distance a chunk at a time when FPS drops "
				+ "below your target, and raises it again when there's room. Your normal distance comes back when you turn it off.",
				Category.PERFORMANCE, false);
	}

	@Override
	protected void onEnable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.options == null) return;
		int now = mc.options.renderDistance().get();
		if (saved.value() == 0) {
			// first time on: remember the real distance and use it as the normal one
			saved.set(now);
			normal.set(Math.max(minimum.value(), now));
		}
		current = -1;
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc != null && mc.options != null && saved.value() > 0) {
			mc.options.renderDistance().set(saved.value());
			mc.options.save();
		}
		saved.set(0);
		current = -1;
	}

	/** Pure decision: the next distance given current FPS. Returns the same value for no change. */
	public static int decide(int distance, float fps, int target, int min, int max, boolean canRaise) {
		int top = Math.max(min, max);
		if (distance > top) return top;
		if (fps < target * 0.9f && distance > min) return distance - 1;
		if (canRaise && fps > target * 1.3f && distance < top) return distance + 1;
		return distance;
	}

	@Override
	public void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null || !FrameMetrics.hasData()) return;
		long now = System.currentTimeMillis();
		if (now - lastCheck < 2000) return;
		lastCheck = now;
		if (mc.screen != null) return; // menus have their own FPS
		int dist = mc.options.renderDistance().get();
		if (current < 0) current = dist;
		int next = decide(dist, FrameMetrics.averageFps(), target.value(), minimum.value(), normal.value(), now - lastChange > 8000);
		if (next != dist) {
			mc.options.renderDistance().set(next);
			current = next;
			lastChange = now;
		}
	}

	@Override
	public String status() {
		return current > 0 ? "Now " + current + " chunks (target " + target.value() + " FPS)" : "Target " + target.value() + " FPS";
	}
}
