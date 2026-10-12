package vanta.branding;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import vanta.ui.Colors;
import vanta.ui.Ui;

/**
 * The VANTA logo: a hexagon emblem with a folded "V" and a diamond spark, next to the
 * VΛNTΛ wordmark. Drawn from textures (smooth at any size), tinted with your theme.
 *
 * Animation: when a screen opens the emblem pops in with a small turn while the letters
 * slide up one by one and an accent line draws under them. While idle, a soft glow
 * breathes behind the emblem and a shine sweeps across it every few seconds.
 * Reduced motion shows the finished logo with no movement.
 */
public final class Logo {
	/** One resolution of the artwork. The small set is used for little logos so they stay crisp. */
	private record Art(Identifier emblem, Identifier sheen, Identifier wordmark, int e, int wmW, int wmH, float letterScale) {
	}

	private static final Art LARGE = new Art(id("emblem"), id("emblem_sheen"), id("wordmark"), 192, 513, 103, 1f);
	private static final Art SMALL = new Art(id("emblem_small"), id("emblem_sheen_small"), id("wordmark_small"), 48, 129, 25, 0.25f);
	private static final Identifier GLOW = id("glow");
	private static final int GLOW_SIZE = 128;
	private static final int WM_W = 513, WM_H = 103;
	/** Left/right edge of each wordmark letter in texture pixels. */
	private static final float[][] LETTERS = {{3.8f, 90.3f}, {113.3f, 199.7f}, {222.7f, 299.6f}, {322.6f, 399.4f}, {422.4f, 508.8f}};
	private static final float WORDMARK_RATIO = 0.42f; // wordmark height relative to emblem size
	private static final long SHEEN_PERIOD = 5200, SHEEN_TIME = 850;

	private Logo() {
	}

	private static Identifier id(String name) {
		return Identifier.fromNamespaceAndPath("vanta", "textures/gui/logo/" + name + ".png");
	}

	/** True when the client name is the default, so the designed wordmark can be used. */
	public static boolean usesWordmark() {
		return Theme.clientName().strip().equalsIgnoreCase("VANTA");
	}

	/** Total width of the logo at this emblem size. */
	public static int width(int size, boolean withName) {
		if (!withName) return size;
		int gap = Math.max(3, Math.round(size * 0.2f));
		if (usesWordmark()) return size + gap + Math.round(size * WORDMARK_RATIO * WM_W / WM_H);
		return size + gap + Math.round(Ui.width(Theme.clientName()) * nameScale(size));
	}

	/** Biggest emblem size whose full logo fits in {@code maxWidth}. */
	public static int fit(int maxWidth, int preferred, boolean withName) {
		int s = preferred;
		while (s > 8 && width(s, withName) > maxWidth) s--;
		return s;
	}

	private static Art art(int size) {
		double gui;
		try {
			gui = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
		} catch (RuntimeException e) {
			gui = 2;
		}
		return size * gui <= 72 ? SMALL : LARGE;
	}

	private static int accent() {
		return Theme.ACCENT_MODE.get() == Theme.AccentMode.CHROMA ? Theme.chroma(0f) | 0xFF000000 : Theme.accent() | 0xFF000000;
	}

	/**
	 * Draws the logo with its top-left corner at (x, y).
	 *
	 * @param size       emblem size in GUI pixels (the name is scaled to match)
	 * @param withName   draw the wordmark (or the custom client name) next to the emblem
	 * @param introStart time the intro started (System.currentTimeMillis), or 0 for none
	 * @return the width drawn
	 */
	public static int draw(GuiGraphics g, int x, int y, int size, boolean withName, long introStart) {
		long now = System.currentTimeMillis();
		Art art = art(size);
		int E = art.e();
		boolean still = Theme.reducedMotion();
		float t = introStart <= 0 || still ? 10f : (now - introStart) / 1000f;
		int accent = accent();
		int secondary = Theme.secondary() | 0xFF000000;

		// --- emblem
		float pop = ease(clamp01(t / 0.65f));
		float emblemAlpha = clamp01(t / 0.3f);
		float scale = 0.72f + 0.28f * backOut(clamp01(t / 0.65f));
		float turn = (1f - pop) * -0.35f;
		float cx = x + size / 2f, cy = y + size / 2f;

		float breathe = still ? 0.5f : 0.5f + 0.5f * (float) Math.sin(now / 900.0);
		float glowAlpha = (0.16f + 0.12f * breathe) * emblemAlpha;
		float glowSize = size * 1.75f;
		g.pose().pushMatrix();
		g.pose().translate(cx - glowSize / 2f, cy - glowSize / 2f);
		g.pose().scale(glowSize / GLOW_SIZE);
		g.blit(RenderPipelines.GUI_TEXTURED, GLOW, 0, 0, 0f, 0f, GLOW_SIZE, GLOW_SIZE, GLOW_SIZE, GLOW_SIZE, Colors.withAlpha(secondary, glowAlpha));
		g.pose().popMatrix();

		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		if (turn != 0f) g.pose().rotate(turn);
		g.pose().scale(size * scale / E);
		g.pose().translate(-E / 2f, -E / 2f);
		g.blit(RenderPipelines.GUI_TEXTURED, art.emblem(), 0, 0, 0f, 0f, E, E, E, E, Colors.withAlpha(accent, emblemAlpha));
		if (!still) {
			long since = introStart > 0 ? now - introStart - 700 : now;
			long phase = Math.floorMod(since, SHEEN_PERIOD);
			if (since > 0 && phase < SHEEN_TIME) {
				int frame = (int) (phase * 16 / SHEEN_TIME);
				float u = (frame % 4) * E, v = (frame / 4) * E;
				g.blit(RenderPipelines.GUI_TEXTURED, art.sheen(), 0, 0, u, v, E, E, E * 4, E * 4, Colors.withAlpha(0xFFFFFFFF, 0.8f * emblemAlpha));
			}
		}
		g.pose().popMatrix();

		if (!withName) return size;

		// --- name
		int gap = Math.max(3, Math.round(size * 0.2f));
		int nx = x + size + gap;
		int textColor = Theme.text() | 0xFF000000;
		if (!usesWordmark()) {
			drawCustomName(g, Theme.clientName(), nx, y, size, t, now, still, accent, secondary);
			return width(size, true);
		}
		float h = size * WORDMARK_RATIO;
		float s = h / WM_H; // screen px per large-wordmark px
		float ts = s / art.letterScale(); // screen px per texture px of the chosen art
		float ny = y + (size - h) / 2f - size * 0.04f;
		for (int i = 0; i < LETTERS.length; i++) {
			float lt = clamp01((t - 0.22f - i * 0.075f) / 0.32f);
			if (lt <= 0f) continue;
			float l = LETTERS[i][0] * art.letterScale(), r = LETTERS[i][1] * art.letterScale();
			float rise = (1f - ease(lt)) * 0.35f * h;
			g.pose().pushMatrix();
			g.pose().translate(nx, ny + rise);
			g.pose().scale(ts);
			int u0 = (int) Math.floor(l), u1 = Math.min(art.wmW(), (int) Math.ceil(r) + 1);
			g.blit(RenderPipelines.GUI_TEXTURED, art.wordmark(), u0, 0, u0, 0f, u1 - u0, art.wmH(), art.wmW(), art.wmH(), Colors.withAlpha(textColor, lt));
			g.pose().popMatrix();
		}
		// accent line that draws in under the wordmark
		float line = ease(clamp01((t - 0.55f) / 0.45f));
		if (line > 0f) {
			int lw = Math.max(1, Math.round(WM_W * s * 0.22f * line));
			int ly = Math.round(ny + h + Math.max(2f, size * 0.07f));
			Ui.hGradient(g, nx + Math.round(LETTERS[0][0] * s), ly, lw, Math.max(1, Math.round(size / 24f)), accent, secondary);
		}
		return width(size, true);
	}

	/** Text scale for a custom client name next to an emblem of this size. */
	private static float nameScale(int size) {
		return Math.max(1f, size / 13f);
	}

	/**
	 * A custom client name (anything other than VANTA) gets the same treatment as the
	 * wordmark: letters rise in one by one, colored along your accent gradient (or chroma),
	 * an accent line draws in underneath, and a shine runs across the letters now and then.
	 */
	private static void drawCustomName(GuiGraphics g, String name, int nx, int y, int size, float t, long now, boolean still,
			int accent, int secondary) {
		float ts = nameScale(size);
		float textH = 8 * ts;
		float baseY = y + (size - textH) / 2f - size * 0.04f;
		int n = name.length();
		float totalW = Ui.width(name) * ts;
		boolean chroma = Theme.ACCENT_MODE.get() == Theme.AccentMode.CHROMA;
		boolean gradient = Theme.ACCENT_MODE.get() != Theme.AccentMode.SOLID;
		// shine: a bright spot sweeps left to right every few seconds after the intro
		long since = now - 900;
		long phase = Math.floorMod(since, SHEEN_PERIOD);
		float shine = still || phase > 1100 ? -10f : phase / 1100f * 1.4f - 0.2f;
		float cx = 0f;
		for (int i = 0; i < n; i++) {
			String ch = String.valueOf(name.charAt(i));
			float cw = Ui.width(ch) * ts;
			float lt = clamp01((t - 0.22f - i * 0.05f) / 0.3f);
			if (lt > 0f && !ch.isBlank()) {
				float pos = n <= 1 ? 0f : i / (float) (n - 1);
				int color;
				if (chroma) color = Theme.chroma(pos * 0.35f) | 0xFF000000;
				else if (gradient) color = Colors.mix(accent, secondary, pos);
				else color = accent;
				float centre = (cx + cw / 2f) / Math.max(1f, totalW);
				float hl = clamp01(1f - Math.abs(centre - shine) / 0.12f);
				if (hl > 0f) color = Colors.mix(color, 0xFFFFFFFF, hl * 0.65f);
				float rise = (1f - ease(lt)) * 0.45f * textH;
				g.pose().pushMatrix();
				g.pose().translate(nx + cx, baseY + rise);
				g.pose().scale(ts);
				g.drawString(Ui.font(), ch, 0, 0, Colors.withAlpha(color, lt), false);
				g.pose().popMatrix();
			}
			cx += cw;
		}
		float line = ease(clamp01((t - 0.2f - n * 0.05f) / 0.45f));
		if (line > 0f) {
			int lw = Math.max(1, Math.round(totalW * 0.3f * line));
			int ly = Math.round(baseY + textH + Math.max(2f, size * 0.08f));
			Ui.hGradient(g, nx, ly, lw, Math.max(1, Math.round(size / 18f)), accent, secondary);
		}
	}

	private static float clamp01(float v) { return v < 0f ? 0f : Math.min(1f, v); }

	private static float ease(float t) {
		float u = 1f - t;
		return 1f - u * u * u;
	}

	private static float backOut(float t) {
		float c = 1.6f;
		float u = t - 1f;
		return 1f + (c + 1f) * u * u * u + c * u * u;
	}
}
