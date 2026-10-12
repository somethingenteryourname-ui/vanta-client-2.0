package vanta.crosshair;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import vanta.branding.Theme;
import vanta.ui.Colors;

/** Draws the custom crosshair with plain rectangles (no textures). */
public final class CrosshairRenderer {
	private static float spread;

	private CrosshairRenderer() {
	}

	private static final Identifier VANILLA_SPRITE = Identifier.withDefaultNamespace("hud/crosshair");

	/** Like {@link #draw} but applies the Overall size setting around the center. */
	public static void drawScaled(GuiGraphics g, CrosshairModule c, int cx, int cy, boolean preview) {
		float s = c.size.value() / 100f;
		if (s == 1f) {
			draw(g, c, cx, cy, preview);
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(s);
		draw(g, c, 0, 0, preview);
		g.pose().popMatrix();
	}

	/** Draw centered on (cx, cy). preview = true when drawn in the editor. */
	public static void draw(GuiGraphics g, CrosshairModule c, int cx, int cy, boolean preview) {
		Minecraft mc = Minecraft.getInstance();
		int color = c.color.argb();
		if (c.chroma.on()) color = (color & 0xFF000000) | (Theme.chroma(0f) & 0xFFFFFF);
		Entity target = preview ? null : mc.crosshairPickEntity;
		if (c.hitColor.on() && target != null && target.isAlive()) color = c.targetColor.argb();

		int extra = 0;
		if (!preview && c.dynamic.get() == CrosshairModule.Dynamic.MOVEMENT && mc.player != null) {
			LocalPlayer p = mc.player;
			double speed = Math.hypot(p.getX() - p.xo, p.getZ() - p.zo);
			float targetSpread = (float) Math.min(4.0, speed * 12.0) + (p.onGround() ? 0f : 2f);
			spread += (targetSpread - spread) * 0.25f;
			extra = Math.round(spread);
		}

		int len = c.length.value();
		int th = c.thickness.value();
		int gap = c.gap.value() + extra;
		int half = th / 2;
		int outline = c.outline.on() ? c.outlineColor.argb() : 0;

		switch (c.shape.get()) {
			case CROSS -> cross(g, cx, cy, len, th, gap, half, color, outline, true);
			case CROSS_DOT -> {
				cross(g, cx, cy, len, th, gap, half, color, outline, true);
				dot(g, cx, cy, c.dotSize.value(), color, outline);
			}
			case DOT -> dot(g, cx, cy, c.dotSize.value(), color, outline);
			case CIRCLE -> circle(g, cx, cy, c.circleRadius.value() + extra, th, color, outline);
			case CIRCLE_DOT -> {
				circle(g, cx, cy, c.circleRadius.value() + extra, th, color, outline);
				dot(g, cx, cy, c.dotSize.value(), color, outline);
			}
			case T_SHAPE -> cross(g, cx, cy, len, th, gap, half, color, outline, false);
			case X -> diagonal(g, cx, cy, len, th, gap, color, outline);
			case PLUS_GAP -> brackets(g, cx, cy, len, th, gap + 2, color, outline);
			case VANILLA -> g.blitSprite(RenderPipelines.GUI_TEXTURED, VANILLA_SPRITE, cx - 7, cy - 7, 15, 15);
		}

		if (!preview && c.attackIndicator.on() && mc.player != null) {
			float s = mc.player.getAttackStrengthScale(0f);
			if (s < 1f) {
				int w = 16;
				int y = cy + Math.max(len + gap, c.circleRadius.value()) + 5;
				g.fill(cx - w / 2 - 1, y - 1, cx + w / 2 + 1, y + 2, 0x80000000);
				g.fill(cx - w / 2, y, cx - w / 2 + Math.round(w * s), y + 1, Colors.withAlpha(color, 1f));
			}
		}
	}

	private static void box(GuiGraphics g, int x0, int y0, int x1, int y1, int color, int outline) {
		if (outline != 0) g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, outline);
		g.fill(x0, y0, x1, y1, color);
	}

	private static void cross(GuiGraphics g, int cx, int cy, int len, int th, int gap, int half, int color, int outline, boolean top) {
		int t0 = -half, t1 = th - half;
		// outlines first so lines never get covered by a neighbour's border
		if (outline != 0) {
			if (top) g.fill(cx + t0 - 1, cy - gap - len - 1, cx + t1 + 1, cy - gap + 1, outline);
			g.fill(cx + t0 - 1, cy + gap + 1 - 1, cx + t1 + 1, cy + gap + 1 + len + 1, outline);
			g.fill(cx - gap - len - 1, cy + t0 - 1, cx - gap + 1, cy + t1 + 1, outline);
			g.fill(cx + gap + 1 - 1, cy + t0 - 1, cx + gap + 1 + len + 1, cy + t1 + 1, outline);
		}
		if (top) g.fill(cx + t0, cy - gap - len, cx + t1, cy - gap, color);
		g.fill(cx + t0, cy + gap + 1, cx + t1, cy + gap + 1 + len, color);
		g.fill(cx - gap - len, cy + t0, cx - gap, cy + t1, color);
		g.fill(cx + gap + 1, cy + t0, cx + gap + 1 + len, cy + t1, color);
	}

	private static void dot(GuiGraphics g, int cx, int cy, int size, int color, int outline) {
		int h = size / 2;
		box(g, cx - h, cy - h, cx - h + size, cy - h + size, color, outline);
	}

	private static void circle(GuiGraphics g, int cx, int cy, int r, int th, int color, int outline) {
		// Rasterize a ring: for each pixel row, fill the pixels whose distance is within [r - th, r].
		for (int pass = outline != 0 ? 0 : 1; pass < 2; pass++) {
			int c = pass == 0 ? outline : color;
			float inner = pass == 0 ? r - th - 0.9f : r - th + 0.1f;
			float outer = pass == 0 ? r + 0.9f : r - 0.1f + 0.5f;
			for (int dy = -r - 1; dy <= r + 1; dy++) {
				for (int dx = -r - 1; dx <= r + 1; dx++) {
					double d = Math.sqrt(dx * dx + dy * dy);
					if (d >= inner && d <= outer) g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, c);
				}
			}
		}
	}

	private static void diagonal(GuiGraphics g, int cx, int cy, int len, int th, int gap, int color, int outline) {
		for (int pass = outline != 0 ? 0 : 1; pass < 2; pass++) {
			int c = pass == 0 ? outline : color;
			int grow = pass == 0 ? 1 : 0;
			for (int i = gap; i < gap + len; i++) {
				for (int sx = -1; sx <= 1; sx += 2) {
					for (int sy = -1; sy <= 1; sy += 2) {
						int x = cx + sx * (i + 1);
						int y = cy + sy * (i + 1);
						g.fill(x - grow, y - grow, x + th + grow, y + th + grow, c);
					}
				}
			}
		}
	}

	private static void brackets(GuiGraphics g, int cx, int cy, int len, int th, int gap, int color, int outline) {
		int s = gap + len;
		int[][] corners = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
		for (int[] k : corners) {
			int x = cx + k[0] * s;
			int y = cy + k[1] * s;
			int hx0 = k[0] < 0 ? x : x - len + 1;
			int vy0 = k[1] < 0 ? y : y - len + 1;
			box(g, hx0, y, hx0 + len, y + th, color, outline);
			box(g, x, vy0, x + th, vy0 + len, color, outline);
		}
	}
}
