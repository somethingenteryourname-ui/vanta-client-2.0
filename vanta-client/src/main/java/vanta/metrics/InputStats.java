package vanta.metrics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * Counts clicks per second for the CPS and Keystrokes widgets by sampling the
 * attack/use key bindings once per frame (so it follows the player's rebinds).
 * Read-only: it never sends input.
 */
public final class InputStats {
	private static final int SLOTS = 64;
	private static final long[] left = new long[SLOTS];
	private static final long[] right = new long[SLOTS];
	private static int leftHead;
	private static int rightHead;
	private static boolean leftWasDown;
	private static boolean rightWasDown;
	private static boolean active;

	private InputStats() {
	}

	/** Widgets that need click data turn sampling on; nothing is sampled otherwise. */
	public static void setActive(boolean on) { active = on; }

	static void onFrame() {
		if (!active) return;
		Minecraft mc = Minecraft.getInstance();
		Options options = mc.options;
		if (options == null || mc.screen != null) {
			leftWasDown = rightWasDown = false;
			return;
		}
		long now = System.currentTimeMillis();
		boolean l = options.keyAttack.isDown();
		boolean r = options.keyUse.isDown();
		if (l && !leftWasDown) {
			left[leftHead] = now;
			leftHead = (leftHead + 1) % SLOTS;
		}
		if (r && !rightWasDown) {
			right[rightHead] = now;
			rightHead = (rightHead + 1) % SLOTS;
		}
		leftWasDown = l;
		rightWasDown = r;
	}

	public static int leftCps() { return countRecent(left); }
	public static int rightCps() { return countRecent(right); }

	private static int countRecent(long[] times) {
		long cutoff = System.currentTimeMillis() - 1000;
		int n = 0;
		for (long t : times) if (t > cutoff) n++;
		return n;
	}
}
