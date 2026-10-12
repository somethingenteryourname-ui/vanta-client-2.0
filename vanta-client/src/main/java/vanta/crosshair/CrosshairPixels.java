package vanta.crosshair;

import java.util.Locale;

/**
 * A hand-drawn crosshair: a 21 x 21 grid of on/off pixels, centered on the screen center
 * (cell 10,10). Stored as hex, one bit per cell, row by row.
 */
public final class CrosshairPixels {
	public static final int N = 21;
	public static final int C = N / 2;

	private final boolean[] cells = new boolean[N * N];

	public boolean get(int x, int y) {
		return x >= 0 && y >= 0 && x < N && y < N && cells[y * N + x];
	}

	public void set(int x, int y, boolean on) {
		if (x >= 0 && y >= 0 && x < N && y < N) cells[y * N + x] = on;
	}

	public void clear() {
		java.util.Arrays.fill(cells, false);
	}

	public boolean isEmpty() {
		for (boolean b : cells) if (b) return false;
		return true;
	}

	public CrosshairPixels copy() {
		CrosshairPixels p = new CrosshairPixels();
		System.arraycopy(cells, 0, p.cells, 0, cells.length);
		return p;
	}

	public String encode() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < cells.length; i += 4) {
			int v = 0;
			for (int b = 0; b < 4; b++) if (i + b < cells.length && cells[i + b]) v |= 8 >> b;
			sb.append(Integer.toHexString(v));
		}
		return sb.toString();
	}

	public static CrosshairPixels decode(String hex) {
		CrosshairPixels p = new CrosshairPixels();
		if (hex == null) return p;
		String h = hex.strip().toLowerCase(Locale.ROOT);
		for (int d = 0; d < h.length(); d++) {
			int v = Character.digit(h.charAt(d), 16);
			if (v < 0) return new CrosshairPixels(); // broken text: start empty rather than draw garbage
			for (int b = 0; b < 4; b++) {
				int i = d * 4 + b;
				if (i < p.cells.length) p.cells[i] = (v & (8 >> b)) != 0;
			}
		}
		return p;
	}

	// ---- starting points for the editor

	public static CrosshairPixels preset(String name) {
		CrosshairPixels p = new CrosshairPixels();
		switch (name) {
			case "plus" -> {
				for (int i = 2; i <= 5; i++) {
					p.set(C - i, C, true);
					p.set(C + i, C, true);
					p.set(C, C - i, true);
					p.set(C, C + i, true);
				}
			}
			case "dot" -> {
				p.set(C, C, true);
				p.set(C + 1, C, true);
				p.set(C, C + 1, true);
				p.set(C + 1, C + 1, true);
			}
			case "circle" -> {
				for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
					double d = Math.hypot(x - C, y - C);
					if (d >= 4.4 && d < 5.4) p.set(x, y, true);
				}
				p.set(C, C, true);
			}
			case "x" -> {
				for (int i = 2; i <= 5; i++) {
					p.set(C - i, C - i, true);
					p.set(C + i, C - i, true);
					p.set(C - i, C + i, true);
					p.set(C + i, C + i, true);
				}
			}
			default -> {
			}
		}
		return p;
	}
}
