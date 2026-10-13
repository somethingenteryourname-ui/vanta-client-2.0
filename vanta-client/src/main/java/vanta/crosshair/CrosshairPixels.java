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

	/** Copies another grid into this one. */
	public void setAll(CrosshairPixels other) {
		System.arraycopy(other.cells, 0, cells, 0, cells.length);
	}

	public int count() {
		int n = 0;
		for (boolean b : cells) if (b) n++;
		return n;
	}

	// ---- drawing helpers used by the editor tools

	/** Cells on a straight line between two cells (Bresenham). */
	public static java.util.List<int[]> line(int x0, int y0, int x1, int y1) {
		java.util.List<int[]> out = new java.util.ArrayList<>();
		int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
		int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
		while (true) {
			out.add(new int[] {x0, y0});
			if (x0 == x1 && y0 == y1) break;
			int e2 = 2 * err;
			if (e2 >= dy) { err += dy; x0 += sx; }
			if (e2 <= dx) { err += dx; y0 += sy; }
		}
		return out;
	}

	/** Outline of the box between two corner cells. */
	public static java.util.List<int[]> box(int x0, int y0, int x1, int y1) {
		java.util.List<int[]> out = new java.util.ArrayList<>();
		int ax = Math.min(x0, x1), bx = Math.max(x0, x1), ay = Math.min(y0, y1), by = Math.max(y0, y1);
		for (int x = ax; x <= bx; x++) {
			out.add(new int[] {x, ay});
			out.add(new int[] {x, by});
		}
		for (int y = ay; y <= by; y++) {
			out.add(new int[] {ax, y});
			out.add(new int[] {bx, y});
		}
		return out;
	}

	/** Ring centered on (cx, cy) passing through (px, py). */
	public static java.util.List<int[]> ring(int cx, int cy, int px, int py) {
		java.util.List<int[]> out = new java.util.ArrayList<>();
		double r = Math.hypot(px - cx, py - cy);
		if (r < 0.5) {
			out.add(new int[] {cx, cy});
			return out;
		}
		for (int y = cy - (int) r - 1; y <= cy + r + 1; y++) {
			for (int x = cx - (int) r - 1; x <= cx + r + 1; x++) {
				double d = Math.hypot(x - cx, y - cy);
				if (Math.abs(d - r) < 0.5) out.add(new int[] {x, y});
			}
		}
		return out;
	}

	/** Flood fill from a cell: every connected cell with the same state is set to {@code on}. */
	public void fill(int x, int y, boolean on) {
		if (x < 0 || y < 0 || x >= N || y >= N || get(x, y) == on) return;
		boolean from = get(x, y);
		java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
		q.add(new int[] {x, y});
		while (!q.isEmpty()) {
			int[] c = q.poll();
			if (c[0] < 0 || c[1] < 0 || c[0] >= N || c[1] >= N || get(c[0], c[1]) != from) continue;
			set(c[0], c[1], on);
			q.add(new int[] {c[0] + 1, c[1]});
			q.add(new int[] {c[0] - 1, c[1]});
			q.add(new int[] {c[0], c[1] + 1});
			q.add(new int[] {c[0], c[1] - 1});
		}
	}

	/** Moves the whole drawing; pixels pushed off the edge are lost. */
	public void shift(int dx, int dy) {
		boolean[] old = cells.clone();
		clear();
		for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) if (old[y * N + x]) set(x + dx, y + dy, true);
	}

	/**
	 * The quick builder: a crosshair from a few numbers instead of painting.
	 *
	 * @param style 0 = plus, 1 = X, 2 = both
	 */
	public static CrosshairPixels build(int arm, int gap, int thick, boolean dot, int style) {
		CrosshairPixels p = new CrosshairPixels();
		int t0 = -(thick - 1) / 2, t1 = thick / 2;
		for (int i = gap + 1; i <= gap + arm; i++) {
			for (int t = t0; t <= t1; t++) {
				if (style != 1) {
					p.set(C - i, C + t, true);
					p.set(C + i, C + t, true);
					p.set(C + t, C - i, true);
					p.set(C + t, C + i, true);
				}
				if (style != 0) {
					p.set(C - i + t, C - i, true);
					p.set(C + i + t, C - i, true);
					p.set(C - i + t, C + i, true);
					p.set(C + i + t, C + i, true);
				}
			}
		}
		if (dot) for (int y = t0; y <= t1; y++) for (int x = t0; x <= t1; x++) p.set(C + x, C + y, true);
		return p;
	}

	// ---- starting points for the editor

	/** Template ids and names shown in the editor. */
	public static final String[][] TEMPLATES = {
			{"plus", "Plus"}, {"dot", "Dot"}, {"circle", "Circle"}, {"x", "X"}, {"t", "T"},
			{"box", "Box"}, {"chevron", "Arrow"}, {"ring_dot", "Ring + dot"}, {"plus_dot", "Plus + dot"}};

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
			}
			case "x" -> {
				for (int i = 2; i <= 5; i++) {
					p.set(C - i, C - i, true);
					p.set(C + i, C - i, true);
					p.set(C - i, C + i, true);
					p.set(C + i, C + i, true);
				}
			}
			case "t" -> {
				for (int i = 2; i <= 5; i++) {
					p.set(C - i, C, true);
					p.set(C + i, C, true);
					p.set(C, C + i, true);
				}
			}
			case "box" -> {
				for (int[] c : box(C - 4, C - 4, C + 4, C + 4)) p.set(c[0], c[1], true);
				for (int i = -1; i <= 1; i++) {
					p.set(C + i, C - 4, false);
					p.set(C + i, C + 4, false);
					p.set(C - 4, C + i, false);
					p.set(C + 4, C + i, false);
				}
			}
			case "chevron" -> {
				for (int i = 0; i <= 4; i++) {
					p.set(C - i, C + i, true);
					p.set(C + i, C + i, true);
				}
			}
			case "ring_dot" -> {
				p.setAll(preset("circle"));
				p.set(C, C, true);
			}
			case "plus_dot" -> {
				p.setAll(preset("plus"));
				p.set(C, C, true);
			}
			default -> {
			}
		}
		return p;
	}
}
