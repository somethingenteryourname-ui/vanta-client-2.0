package vanta.metrics;

import java.util.Arrays;

/**
 * Measures real frame times. {@link #onFrame()} is called once per rendered frame
 * (hooked at the start of Minecraft's frame loop). History is a fixed-size ring
 * buffer, so memory use never grows, and statistics are recomputed at most 4 times
 * per second rather than every frame.
 */
public final class FrameMetrics {
	private static final int CAPACITY = 2048;
	private static final long STATS_INTERVAL_NS = 250_000_000L;
	private static final long[] durations = new long[CAPACITY];
	private static final long[] scratch = new long[CAPACITY];
	private static int head;
	private static int count;
	private static long lastFrame;
	private static long lastStats;

	private static int fps;
	private static float lastFrameMs;
	private static float avgFrameMs;
	private static float avgFps;
	private static float onePercentLow = Float.NaN;
	private static float maxFrameMs;
	private static long statsVersion;

	private FrameMetrics() {
	}

	public static void onFrame() {
		long now = System.nanoTime();
		if (lastFrame != 0) {
			long d = now - lastFrame;
			// Ignore pauses longer than 5s (window minimized, debugger, world load).
			if (d > 0 && d < 5_000_000_000L) {
				durations[head] = d;
				head = (head + 1) % CAPACITY;
				if (count < CAPACITY) count++;
				lastFrameMs = d / 1_000_000f;
			}
		}
		lastFrame = now;
		InputStats.onFrame();
		if (now - lastStats >= STATS_INTERVAL_NS) {
			lastStats = now;
			recompute();
		}
	}

	private static void recompute() {
		if (count == 0) return;
		// FPS: frames completed within the last second.
		long sum = 0;
		int frames = 0;
		for (int i = 1; i <= count; i++) {
			long d = durations[(head - i + CAPACITY) % CAPACITY];
			if (sum + d > 1_000_000_000L && frames > 0) break;
			sum += d;
			frames++;
		}
		fps = sum > 0 ? (int) Math.round(frames * 1_000_000_000.0 / sum) : 0;

		// Average and 1% low over the last ~5 seconds (bounded by buffer size).
		long window = 0;
		int n = 0;
		long max = 0;
		for (int i = 1; i <= count; i++) {
			long d = durations[(head - i + CAPACITY) % CAPACITY];
			if (window + d > 5_000_000_000L && n > 0) break;
			window += d;
			scratch[n++] = d;
			if (d > max) max = d;
		}
		avgFrameMs = window / (float) n / 1_000_000f;
		avgFps = avgFrameMs > 0 ? 1000f / avgFrameMs : 0;
		maxFrameMs = max / 1_000_000f;
		if (n >= 100) {
			Arrays.sort(scratch, 0, n);
			// Average of the slowest 1% of frames, reported as FPS.
			int slow = Math.max(1, n / 100);
			long slowSum = 0;
			for (int i = n - slow; i < n; i++) slowSum += scratch[i];
			onePercentLow = (float) (1_000_000_000.0 / (slowSum / (double) slow));
		} else {
			onePercentLow = Float.NaN;
		}
		statsVersion++;
	}

	/** Frames per second over the last second. */
	public static int fps() { return fps; }
	public static float lastFrameMs() { return lastFrameMs; }
	public static float averageFrameMs() { return avgFrameMs; }
	public static float averageFps() { return avgFps; }
	/** Average FPS of the slowest 1% of frames in the last ~5s, or NaN if too few samples. */
	public static float onePercentLow() { return onePercentLow; }
	public static float worstFrameMs() { return maxFrameMs; }
	public static boolean hasData() { return count > 0; }
	/** Changes whenever statistics are recomputed; lets widgets cache their text. */
	public static long version() { return statsVersion; }

	/** Copies up to max recent frame times (ms, oldest first) into out; returns the number copied. */
	public static int recentFrameTimes(float[] out, int max) {
		int n = Math.min(Math.min(max, out.length), count);
		for (int i = 0; i < n; i++) {
			out[i] = durations[(head - n + i + CAPACITY) % CAPACITY] / 1_000_000f;
		}
		return n;
	}
}
