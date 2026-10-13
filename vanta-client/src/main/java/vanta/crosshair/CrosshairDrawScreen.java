package vanta.crosshair;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import vanta.branding.Theme;
import vanta.gui.MenuSettings;
import vanta.gui.VantaScreen;
import vanta.ui.Colors;
import vanta.ui.Ui;

/**
 * The crosshair editor, built to be easy:
 *   1. pick a template or move three sliders (no painting needed),
 *   2. touch it up with Pen, Eraser, Line, Box, Ring or Fill, with automatic mirroring,
 *   3. pick a color and size, check the live preview, and Save.
 * Every control explains itself at the bottom of the screen when you hover it.
 */
public final class CrosshairDrawScreen extends VantaScreen {
	private enum Tool {
		PEN("Pen", "Click or drag to draw. Right click erases."),
		ERASE("Erase", "Click or drag to erase."),
		LINE("Line", "Drag from start to end to draw a straight line."),
		BOX("Box", "Drag from one corner to the other to draw a box."),
		RING("Ring", "Drag out from the center to draw a circle."),
		FILL("Fill", "Click inside an area to fill it. Right click empties it.");
		final String label, hint;
		Tool(String label, String hint) { this.label = label; this.hint = hint; }
	}

	private enum Mirror {
		OFF("Off", "No mirroring."), LR("L-R", "Mirror left and right."), UD("U-D", "Mirror top and bottom."),
		FOUR("4-way", "Mirror left-right and top-bottom (best for most crosshairs)."),
		EIGHT("8-way", "Mirror in all 8 directions, including diagonals.");
		final String label, hint;
		Mirror(String label, String hint) { this.label = label; this.hint = hint; }
	}

	private record Btn(int x, int y, int w, int h, String label, Runnable action, BooleanSupplier selected, boolean primary, String hint) {
	}

	private record Slider(int x, int y, int w, String label, int min, int max, IntSupplier get, IntConsumer set, String hint) {
	}

	private record Tile(int x, int y, int s, String id, String name) {
	}

	private static final int[] SWATCHES = {0xFFFFFFFF, 0xFFFF5555, 0xFF55FF55, 0xFF55FFFF, 0xFFFFFF55, 0xFFFF55FF, 0xFFFFAA00, 0xFF000000};
	private static final int LW = 124, RW = 168;

	private final CrosshairModule module;
	private final CrosshairPixels work;
	private final Deque<String> undo = new ArrayDeque<>();
	private final Deque<String> redo = new ArrayDeque<>();
	private final List<Btn> buttons = new ArrayList<>();
	private final List<Slider> sliders = new ArrayList<>();
	private final List<Tile> tiles = new ArrayList<>();
	private Tool tool = Tool.PEN;
	private Mirror mirror = Mirror.FOUR;
	private int brush = 1;
	private int buildArm = 4, buildGap = 2, buildThick = 1, buildStyle = 0;
	private boolean buildDot = false;
	private int gridX, gridY, cell, rx, previewY, swatchY;
	private Boolean painting; // true = drawing, false = erasing, null = idle
	private int dragX = -1, dragY = -1, curX, curY;
	private Slider activeSlider;
	private String hoverHint;

	public CrosshairDrawScreen(Screen parent, CrosshairModule module) {
		super(Component.literal("Crosshair editor"), parent);
		this.module = module;
		this.work = module.drawn().copy();
		if (work.isEmpty()) work.setAll(CrosshairPixels.build(buildArm, buildGap, buildThick, buildDot, buildStyle));
	}

	@Override protected int minVirtualWidth() { return 24 + LW + 16 + 21 * 8 + 16 + RW; }
	@Override protected int minVirtualHeight() { return 300; }

	@Override
	protected void layout() {
		buttons.clear();
		sliders.clear();
		tiles.clear();
		int n = CrosshairPixels.N;
		int midW = vw - 12 - LW - RW - 12 - 32;
		cell = Math.max(6, Math.min(16, Math.min((vh - 70) / n, midW / n)));
		int gridSize = cell * n;
		gridX = 12 + LW + 16 + Math.max(0, (midW - gridSize) / 2);
		gridY = Math.max(48, (vh - gridSize) / 2 + 6);

		// ---- left: templates and the quick builder
		int lx = 12, y = 48;
		int ts = (LW - 8) / 3;
		for (int i = 0; i < CrosshairPixels.TEMPLATES.length; i++) {
			tiles.add(new Tile(lx + (i % 3) * (ts + 4), y + (i / 3) * (ts + 4), ts, CrosshairPixels.TEMPLATES[i][0], CrosshairPixels.TEMPLATES[i][1]));
		}
		y += 3 * (ts + 4) + 16;
		sliders.add(new Slider(lx, y, LW, "Arm length", 1, 9, () -> buildArm, v -> { buildArm = v; rebuildFromBuilder(); },
				"How long each line of the built crosshair is."));
		y += 22;
		sliders.add(new Slider(lx, y, LW, "Gap", 0, 6, () -> buildGap, v -> { buildGap = v; rebuildFromBuilder(); },
				"Empty space between the center and the lines."));
		y += 22;
		sliders.add(new Slider(lx, y, LW, "Thickness", 1, 3, () -> buildThick, v -> { buildThick = v; rebuildFromBuilder(); },
				"How thick the lines are."));
		y += 22;
		int half = (LW - 4) / 2;
		buttons.add(new Btn(lx, y, half, 16, "Center dot", () -> {
			buildDot = !buildDot;
			snapshot();
			rebuildFromBuilder();
		}, () -> buildDot, false, "Add a dot in the middle of the built crosshair."));
		buttons.add(new Btn(lx + half + 4, y, LW - half - 4, 16, "", () -> {
			buildStyle = (buildStyle + 1) % 3;
			snapshot();
			rebuildFromBuilder();
		}, () -> false, false, "Switch the built crosshair between plus, X and both."));

		// ---- right: tools, mirror, color, size, preview
		rx = vw - 12 - RW;
		y = 48;
		Tool[] tools = Tool.values();
		int tw = (RW - 4 * 2) / 3;
		for (int i = 0; i < tools.length; i++) {
			Tool t = tools[i];
			buttons.add(new Btn(rx + (i % 3) * (tw + 4), y + (i / 3) * 20, i % 3 == 2 ? RW - 2 * (tw + 4) : tw, 16, t.label,
					() -> tool = t, () -> tool == t, false, t.hint + "  (key: " + t.label.charAt(0) + ")"));
		}
		y += 40 + 12;
		Mirror[] mirrors = Mirror.values();
		int mw = (RW - 4 * 4) / 5;
		for (int i = 0; i < mirrors.length; i++) {
			Mirror m = mirrors[i];
			buttons.add(new Btn(rx + i * (mw + 4), y, i == 4 ? RW - 4 * (mw + 4) : mw, 16, m.label, () -> mirror = m, () -> mirror == m, false, m.hint));
		}
		y += 20 + 12;
		int bw = (RW - 4 * 4) / 5;
		for (int b = 1; b <= 3; b++) {
			int size = b;
			buttons.add(new Btn(rx + (b - 1) * (bw + 4), y, bw, 16, b + "px", () -> brush = size, () -> brush == size, false,
					"Brush size for Pen and Erase."));
		}
		buttons.add(new Btn(rx + 3 * (bw + 4), y, bw, 16, "Undo", this::undo, () -> false, false, "Undo the last change (Ctrl+Z)."));
		buttons.add(new Btn(rx + 4 * (bw + 4), y, RW - 4 * (bw + 4), 16, "Redo", this::redo, () -> false, false, "Redo (Ctrl+Y)."));
		y += 20;
		String[][] moves = {{"<", "-1", "0"}, {"^", "0", "-1"}, {"v", "0", "1"}, {">", "1", "0"}};
		for (int i = 0; i < moves.length; i++) {
			int dx = Integer.parseInt(moves[i][1]), dy = Integer.parseInt(moves[i][2]);
			buttons.add(new Btn(rx + i * (bw + 4), y, bw, 16, moves[i][0], () -> {
				snapshot();
				work.shift(dx, dy);
			}, () -> false, false, "Move the whole drawing one pixel (arrow keys)."));
		}
		buttons.add(new Btn(rx + 4 * (bw + 4), y, RW - 4 * (bw + 4), 16, "Clear", () -> {
			snapshot();
			work.clear();
		}, () -> false, false, "Erase everything and start from a blank grid."));
		y += 20 + 12;
		swatchY = y;
		y += 18;
		buttons.add(new Btn(rx, y, 60, 16, "Outline", () -> module.outline.toggle(), () -> module.outline.on(), false,
				"Dark border around the crosshair so it shows on bright and dark blocks."));
		sliders.add(new Slider(rx + 66, y - 6, RW - 66, "Size", 50, 400, () -> module.size.value(), v -> module.size.set(v),
				"How big the crosshair is on screen."));
		y += 22;
		previewY = y;
		int by = vh - 12 - 18;
		int half2 = (RW - 4) / 2;
		buttons.add(new Btn(rx, by, half2, 18, "Cancel", this::onClose, () -> false, false, "Leave without saving."));
		buttons.add(new Btn(rx + half2 + 4, by, RW - half2 - 4, 18, "Save & use", this::save, () -> false, true,
				"Use this crosshair in game."));
	}

	private void rebuildFromBuilder() {
		work.setAll(CrosshairPixels.build(buildArm, buildGap, buildThick, buildDot, buildStyle));
	}

	private void snapshot() {
		undo.push(work.encode());
		redo.clear();
		while (undo.size() > 80) undo.removeLast();
	}

	private void undo() {
		if (undo.isEmpty()) return;
		redo.push(work.encode());
		work.setAll(CrosshairPixels.decode(undo.pop()));
	}

	private void redo() {
		if (redo.isEmpty()) return;
		undo.push(work.encode());
		work.setAll(CrosshairPixels.decode(redo.pop()));
	}

	private void save() {
		module.pixels.set(work.encode());
		module.shape.set(CrosshairModule.Shape.DRAWN);
		module.setEnabled(true);
		onClose();
	}

	// ------------------------------------------------------------------ geometry

	/** The cell plus its mirrored partners for the current mirror mode. */
	private List<int[]> mirrored(int x, int y) {
		int n = CrosshairPixels.N - 1;
		List<int[]> out = new ArrayList<>(8);
		out.add(new int[] {x, y});
		boolean lr = mirror == Mirror.LR || mirror == Mirror.FOUR || mirror == Mirror.EIGHT;
		boolean ud = mirror == Mirror.UD || mirror == Mirror.FOUR || mirror == Mirror.EIGHT;
		if (lr) out.add(new int[] {n - x, y});
		if (ud) out.add(new int[] {x, n - y});
		if (lr && ud) out.add(new int[] {n - x, n - y});
		if (mirror == Mirror.EIGHT) {
			out.add(new int[] {y, x});
			out.add(new int[] {n - y, x});
			out.add(new int[] {y, n - x});
			out.add(new int[] {n - y, n - x});
		}
		return out;
	}

	private void stamp(int x, int y, boolean on, int size) {
		int a = -(size - 1) / 2, b = size / 2;
		for (int dy = a; dy <= b; dy++) for (int dx = a; dx <= b; dx++) {
			for (int[] p : mirrored(x + dx, y + dy)) work.set(p[0], p[1], on);
		}
	}

	private List<int[]> shapeCells() {
		if (dragX < 0) return List.of();
		return switch (tool) {
			case LINE -> CrosshairPixels.line(dragX, dragY, curX, curY);
			case BOX -> CrosshairPixels.box(dragX, dragY, curX, curY);
			case RING -> CrosshairPixels.ring(dragX, dragY, curX, curY);
			default -> List.of();
		};
	}

	private int cellX(double mx) { return (int) Math.floor((mx - gridX) / cell); }
	private int cellY(double my) { return (int) Math.floor((my - gridY) / cell); }

	private boolean onGrid(int x, int y) { return x >= 0 && y >= 0 && x < CrosshairPixels.N && y < CrosshairPixels.N; }

	// ------------------------------------------------------------------ drawing

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		g.fill(0, 0, width, height, Colors.withAlpha(Theme.background(), 0.92f));
	}

	@Override
	protected void renderScaled(GuiGraphics g, int mx, int my, float delta) {
		hoverHint = null;
		int n = CrosshairPixels.N, c = CrosshairPixels.C;
		int gridSize = cell * n;
		Ui.textScaled(g, "Crosshair editor", 12, 10, 1.4f, Theme.text(), false);
		Ui.text(g, "Pick a template or use the sliders, touch it up on the grid, then Save.", 12, 25, Theme.textFaint());

		// ---- left
		step(g, 12, 37, "1", "Start from");
		for (Tile t : tiles) {
			boolean hov = Ui.inside(mx, my, t.x(), t.y(), t.s(), t.s());
			Ui.card(g, t.x(), t.y(), t.s(), t.s(), hov, 0);
			CrosshairPixels p = CrosshairPixels.preset(t.id());
			CrosshairRenderer.drawn(g, p, t.x() + t.s() / 2, t.y() + t.s() / 2 - 3, 0xFFFFFFFF, 0xC0000000);
			Ui.textCentered(g, t.name(), t.x() + t.s() / 2, t.y() + t.s() - 9, Theme.textFaint());
			if (hov) hoverHint = "Start from the " + t.name() + " template. You can change it after.";
		}
		Slider first = sliders.get(0);
		Ui.text(g, "Or build one:", first.x(), first.y() - 12, Theme.textDim());
		for (Slider s : sliders) drawSlider(g, s, mx, my);

		// ---- grid
		Ui.rect(g, gridX - 2, gridY - 2, gridSize + 4, gridSize + 4, Theme.border());
		int color = module.color.argb() | 0xFF000000;
		int hx = cellX(mx), hy = cellY(my);
		boolean hoverGrid = onGrid(hx, hy);
		List<int[]> pending = painting != null ? shapeCells() : List.of();
		boolean[] preview = new boolean[n * n];
		for (int[] p : pending) for (int[] q : mirrored(p[0], p[1])) if (onGrid(q[0], q[1])) preview[q[1] * n + q[0]] = true;
		for (int y = 0; y < n; y++) {
			for (int x = 0; x < n; x++) {
				int px = gridX + x * cell, py = gridY + y * cell;
				int bg = ((x + y) & 1) == 0 ? 0xFF202228 : 0xFF2A2D35;
				if (x == c || y == c) bg = Colors.mix(bg, Theme.accent(), 0.16f);
				if (x == c && y == c) bg = Colors.mix(bg, Theme.accent(), 0.4f);
				Ui.rect(g, px, py, cell, cell, bg);
				if (work.get(x, y)) Ui.rect(g, px + 1, py + 1, cell - 2, cell - 2, color);
				if (preview[y * n + x]) Ui.rect(g, px + 1, py + 1, cell - 2, cell - 2, Colors.withAlpha(painting ? Theme.accent() : 0xFFFF5555, 0.75f));
			}
		}
		if (hoverGrid && painting == null) {
			int size = tool == Tool.PEN || tool == Tool.ERASE ? brush : 1;
			int a = -(size - 1) / 2, b = size / 2;
			for (int dy = a; dy <= b; dy++) for (int dx = a; dx <= b; dx++) {
				for (int[] p : mirrored(hx + dx, hy + dy)) {
					if (onGrid(p[0], p[1])) Ui.roundOutline(g, gridX + p[0] * cell, gridY + p[1] * cell, cell, cell, 0, Colors.withAlpha(Theme.accent(), 0.9f));
				}
			}
			hoverHint = tool.hint + "  Mirror: " + mirror.label + ".";
		}
		Ui.textCentered(g, work.count() + " pixels  -  the brighter cell is the exact center", gridX + gridSize / 2, gridY + gridSize + 6, Theme.textFaint());

		// ---- right
		step(g, rx, 37, "2", "Draw");
		Ui.text(g, "Mirror", rx, 48 + 40 + 2, Theme.textDim());
		Ui.text(g, "Brush, undo, move", rx, 48 + 40 + 12 + 20 + 2, Theme.textDim());
		step(g, rx, swatchY - 10, "3", "Look");
		int sw = (RW - 7 * 3) / 8;
		for (int i = 0; i < SWATCHES.length; i++) {
			int sx = rx + i * (sw + 3);
			boolean hov = Ui.inside(mx, my, sx, swatchY, sw, 12);
			boolean sel = (module.color.argb() | 0xFF000000) == SWATCHES[i];
			Ui.roundRect(g, sx - 1, swatchY - 1, sw + 2, 14, 2, sel ? Theme.accent() : hov ? Theme.text() : Theme.border());
			Ui.rect(g, sx, swatchY, sw, 12, SWATCHES[i]);
			if (hov) hoverHint = "Crosshair color. More colors (and transparency) are on the Crosshair page.";
		}
		for (Btn b : buttons) {
			boolean hov = Ui.inside(mx, my, b.x(), b.y(), b.w(), b.h());
			String label = b.label().isEmpty() ? new String[] {"Style: +", "Style: X", "Style: both"}[buildStyle] : b.label();
			Ui.button(g, b.x(), b.y(), b.w(), b.h(), label, hov, b.primary() || b.selected().getAsBoolean());
			if (hov && b.hint() != null) hoverHint = b.hint();
		}

		// preview
		int pvH = Math.max(40, vh - 12 - 18 - 6 - previewY);
		Ui.card(g, rx, previewY, RW, pvH, false, 0);
		int halfW = (RW - 12) / 2;
		g.fillGradient(rx + 4, previewY + 4, rx + 4 + halfW, previewY + pvH - 4, 0xFF87A9D9, 0xFFB9CFE8);
		g.fillGradient(rx + 8 + halfW, previewY + 4, rx + RW - 4, previewY + pvH - 4, 0xFF2A2F36, 0xFF15181C);
		int outline = module.outline.on() ? module.outlineColor.argb() : 0;
		float s = module.size.value() / 100f;
		g.enableScissor(rx + 4, previewY + 4, rx + RW - 4, previewY + pvH - 4);
		previewAt(g, rx + 4 + halfW / 2, previewY + pvH / 2, s, module.color.argb(), outline);
		previewAt(g, rx + 8 + halfW + halfW / 2, previewY + pvH / 2, s, module.color.argb(), outline);
		g.disableScissor();
		Ui.text(g, "Preview", rx + 7, previewY + 6, Colors.withAlpha(0xFF000000, 0.6f));

		// help line
		String help = hoverHint != null ? hoverHint : "Hover anything to see what it does.";
		Ui.text(g, Ui.ellipsize(help, vw - 24 - RW - 12), 12, vh - 14, hoverHint != null ? Theme.text() : Theme.textFaint());
	}

	private void step(GuiGraphics g, int x, int y, String number, String title) {
		Ui.roundRect(g, x, y - 1, 9, 9, 4, Theme.accent());
		Ui.textCentered(g, number, x + 5, y, Colors.contrastText(Theme.accent()));
		Ui.text(g, title, x + 13, y, Theme.text());
	}

	private void drawSlider(GuiGraphics g, Slider s, int mx, int my) {
		int v = s.get().getAsInt();
		Ui.text(g, s.label(), s.x(), s.y(), Theme.textDim());
		String val = s.label().equals("Size") ? v + "%" : String.valueOf(v);
		Ui.textRight(g, val, s.x() + s.w(), s.y(), Theme.text());
		double f = (v - s.min()) / (double) Math.max(1, s.max() - s.min());
		boolean hov = Ui.inside(mx, my, s.x(), s.y() + 8, s.w(), 12);
		Ui.slider(g, s.x(), s.y() + 10, s.w(), f, activeSlider == s || hov);
		if (hov || activeSlider == s) hoverHint = s.hint();
	}

	private void previewAt(GuiGraphics g, int cx, int cy, float scale, int color, int outline) {
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(scale);
		CrosshairRenderer.drawn(g, work, 0, 0, color, outline);
		g.pose().popMatrix();
	}

	// ------------------------------------------------------------------ input

	private void setSlider(Slider s, double mx) {
		double f = Math.max(0, Math.min(1, (mx - s.x()) / s.w()));
		int v = s.min() + (int) Math.round(f * (s.max() - s.min()));
		if (s.label().equals("Size")) v = Math.round(v / 10f) * 10;
		if (v != s.get().getAsInt()) s.set().accept(v);
	}

	@Override
	protected boolean onClick(double mx, double my, int button) {
		if (button == 0) {
			for (Btn b : buttons) {
				if (Ui.inside(mx, my, b.x(), b.y(), b.w(), b.h())) {
					MenuSettings.click();
					b.action().run();
					return true;
				}
			}
			for (Tile t : tiles) {
				if (Ui.inside(mx, my, t.x(), t.y(), t.s(), t.s())) {
					MenuSettings.click();
					snapshot();
					work.setAll(CrosshairPixels.preset(t.id()));
					return true;
				}
			}
			for (int i = 0; i < SWATCHES.length; i++) {
				int sw = (RW - 7 * 3) / 8;
				if (Ui.inside(mx, my, rx + i * (sw + 3), swatchY, sw, 12)) {
					MenuSettings.click();
					module.color.set(SWATCHES[i]);
					return true;
				}
			}
			for (Slider s : sliders) {
				if (Ui.inside(mx, my, s.x(), s.y() + 6, s.w(), 14)) {
					if (sliders.indexOf(s) < 3) snapshot();
					activeSlider = s;
					setSlider(s, mx);
					return true;
				}
			}
		}
		int x = cellX(mx), y = cellY(my);
		if (!onGrid(x, y) || (button != 0 && button != 1)) return false;
		snapshot();
		boolean on = button == 0 && tool != Tool.ERASE;
		painting = on;
		switch (tool) {
			case PEN, ERASE -> stamp(x, y, on, brush);
			case FILL -> {
				for (int[] p : mirrored(x, y)) work.fill(p[0], p[1], on);
				painting = null;
			}
			default -> {
				dragX = curX = x;
				dragY = curY = y;
			}
		}
		return true;
	}

	@Override
	protected boolean onDrag(double mx, double my, int button) {
		if (activeSlider != null) {
			setSlider(activeSlider, mx);
			return true;
		}
		if (painting == null) return false;
		int x = Math.max(0, Math.min(CrosshairPixels.N - 1, cellX(mx))), y = Math.max(0, Math.min(CrosshairPixels.N - 1, cellY(my)));
		if (tool == Tool.PEN || tool == Tool.ERASE) {
			if (onGrid(cellX(mx), cellY(my))) stamp(x, y, painting, brush);
		} else {
			curX = x;
			curY = y;
		}
		return true;
	}

	@Override
	protected void onRelease(double mx, double my, int button) {
		activeSlider = null;
		if (painting != null && dragX >= 0) {
			for (int[] p : shapeCells()) for (int[] q : mirrored(p[0], p[1])) work.set(q[0], q[1], painting);
		}
		painting = null;
		dragX = dragY = -1;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		boolean ctrl = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
		int key = event.key();
		if (ctrl && key == GLFW.GLFW_KEY_Z) {
			undo();
			return true;
		}
		if (ctrl && key == GLFW.GLFW_KEY_Y) {
			redo();
			return true;
		}
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			onClose();
			return true;
		}
		if (!ctrl) {
			for (Tool t : Tool.values()) {
				if (key == GLFW.GLFW_KEY_A + (Character.toUpperCase(t.label.charAt(0)) - 'A')) {
					tool = t;
					return true;
				}
			}
			int dx = key == GLFW.GLFW_KEY_LEFT ? -1 : key == GLFW.GLFW_KEY_RIGHT ? 1 : 0;
			int dy = key == GLFW.GLFW_KEY_UP ? -1 : key == GLFW.GLFW_KEY_DOWN ? 1 : 0;
			if (dx != 0 || dy != 0) {
				snapshot();
				work.shift(dx, dy);
				return true;
			}
		}
		return super.keyPressed(event);
	}
}
