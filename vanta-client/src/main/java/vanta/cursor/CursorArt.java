package vanta.cursor;

/**
 * Draws cursor images in code (anti-aliased by 4x4 supersampling), so they're sharp at
 * any size and always in your colors.
 */
public final class CursorArt {
	public enum Shape { ARROW, POINTER, DOT, RING }

	private CursorArt() {
	}

	/** ARGB pixels, row by row, size x size. */
	public static int[] render(Shape shape, int size, int fillRgb, int edgeRgb) {
		int[] out = new int[size * size];
		int ss = 4;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				int fill = 0, edge = 0;
				for (int sy = 0; sy < ss; sy++) {
					for (int sx = 0; sx < ss; sx++) {
						double u = (x + (sx + 0.5) / ss) / size, v = (y + (sy + 0.5) / ss) / size;
						int r = sample(shape, u, v, 1.4 / size);
						if (r == 1) fill++;
						else if (r == 2) edge++;
					}
				}
				int total = ss * ss;
				if (fill + edge == 0) continue;
				float a = (fill + edge) / (float) total;
				float t = edge / (float) Math.max(1, fill + edge);
				int rgb = mix(fillRgb, edgeRgb, t);
				out[y * size + x] = (Math.round(a * 255) << 24) | rgb;
			}
		}
		return out;
	}

	/** Hotspot (click point) in pixels. */
	public static int[] hotspot(Shape shape, int size) {
		return switch (shape) {
			case ARROW -> new int[] {1, 1};
			case POINTER -> new int[] {Math.round(size * 0.36f), 1};
			default -> new int[] {size / 2, size / 2};
		};
	}

	/** 0 = empty, 1 = fill, 2 = edge, for a point in 0..1 space. */
	static int sample(Shape shape, double u, double v, double edgeW) {
		switch (shape) {
			case ARROW, POINTER -> {
				double[][] poly = shape == Shape.ARROW ? ARROW : POINTER;
				if (!inside(poly, u, v)) return 0;
				return distToEdge(poly, u, v) < edgeW ? 2 : 1;
			}
			case DOT -> {
				double d = Math.hypot(u - 0.5, v - 0.5);
				if (d > 0.3) return 0;
				return d > 0.3 - edgeW ? 2 : 1;
			}
			case RING -> {
				double d = Math.hypot(u - 0.5, v - 0.5);
				if (d < 0.08) return 1;
				if (d > 0.42 || d < 0.28) return d < 0.08 + edgeW && d >= 0.08 ? 2 : 0;
				return d > 0.42 - edgeW || d < 0.28 + edgeW ? 2 : 1;
			}
			default -> {
				return 0;
			}
		}
	}

	private static final double[][] ARROW = {{0.05, 0.03}, {0.05, 0.86}, {0.27, 0.66}, {0.42, 0.97}, {0.56, 0.91}, {0.41, 0.61}, {0.70, 0.61}};
	private static final double[][] POINTER = {{0.30, 0.03}, {0.42, 0.03}, {0.42, 0.42}, {0.86, 0.48}, {0.86, 0.80}, {0.70, 0.97},
			{0.36, 0.97}, {0.14, 0.66}, {0.14, 0.52}, {0.30, 0.56}};

	private static boolean inside(double[][] poly, double x, double y) {
		boolean in = false;
		for (int i = 0, j = poly.length - 1; i < poly.length; j = i++) {
			double xi = poly[i][0], yi = poly[i][1], xj = poly[j][0], yj = poly[j][1];
			if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) in = !in;
		}
		return in;
	}

	private static double distToEdge(double[][] poly, double x, double y) {
		double best = Double.MAX_VALUE;
		for (int i = 0, j = poly.length - 1; i < poly.length; j = i++) {
			double ax = poly[j][0], ay = poly[j][1], bx = poly[i][0], by = poly[i][1];
			double dx = bx - ax, dy = by - ay;
			double t = Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy)));
			best = Math.min(best, Math.hypot(x - (ax + t * dx), y - (ay + t * dy)));
		}
		return best;
	}

	private static int mix(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return (r << 16) | (g << 8) | bl;
	}
}
