package vanta.ui;

/** Small ARGB helpers. All colors passed to Minecraft must include alpha. */
public final class Colors {
	private Colors() {
	}

	public static int alpha(int argb) { return (argb >>> 24) & 0xFF; }

	public static int withAlpha(int argb, float alpha) {
		int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
		return (a << 24) | (argb & 0xFFFFFF);
	}

	/** Multiplies the existing alpha by factor. */
	public static int fade(int argb, float factor) {
		int a = Math.round(alpha(argb) * Math.max(0f, Math.min(1f, factor)));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	/** Linear blend from a (t=0) to b (t=1), including alpha. */
	public static int mix(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		int oa = Math.round(aa + (ba - aa) * t);
		int or = Math.round(ar + (br - ar) * t);
		int og = Math.round(ag + (bg - ag) * t);
		int ob = Math.round(ab + (bb - ab) * t);
		return (oa << 24) | (or << 16) | (og << 8) | ob;
	}

	/** Black or white, whichever reads better on the given color. */
	public static int contrastText(int argb) {
		int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
		double lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
		return lum > 150 ? 0xFF0B0D11 : 0xFFFFFFFF;
	}

	/** HSB (all 0..1) to opaque ARGB. */
	public static int hsb(float hue, float saturation, float brightness) {
		float h = (hue - (float) Math.floor(hue)) * 6f;
		int sector = (int) h;
		float f = h - sector;
		float p = brightness * (1f - saturation);
		float q = brightness * (1f - saturation * f);
		float t = brightness * (1f - saturation * (1f - f));
		float r, g, b;
		switch (sector) {
			case 0 -> { r = brightness; g = t; b = p; }
			case 1 -> { r = q; g = brightness; b = p; }
			case 2 -> { r = p; g = brightness; b = t; }
			case 3 -> { r = p; g = q; b = brightness; }
			case 4 -> { r = t; g = p; b = brightness; }
			default -> { r = brightness; g = p; b = q; }
		}
		return 0xFF000000 | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
	}
}
