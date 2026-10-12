package vanta.crosshair;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.BooleanSupplier;

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
 * Draw your own crosshair pixel by pixel. Left click / drag draws, right click / drag
 * erases. Mirroring keeps it symmetrical, presets give a starting point, and the preview
 * shows it at real size on a light and a dark background with your color and outline.
 */
public final class CrosshairDrawScreen extends VantaScreen {
	private record Btn(int x, int y, int w, int h, String label, Runnable action, BooleanSupplier selected, boolean primary) {
	}

	private final CrosshairModule module;
	private final CrosshairPixels work;
	private final Deque<String> undo = new ArrayDeque<>();
	private final List<Btn> buttons = new ArrayList<>();
	private boolean mirrorX = true, mirrorY = true;
	private int gridX, gridY, cell;
	private int panelX, panelW;
	private Boolean painting; // true = drawing, false = erasing, null = idle

	public CrosshairDrawScreen(Screen parent, CrosshairModule module) {
		super(Component.literal("Draw your crosshair"), parent);
		this.module = module;
		this.work = module.drawn().copy();
	}

	@Override protected int minVirtualWidth() { return 380; }
	@Override protected int minVirtualHeight() { return 250; }

	@Override
	protected void layout() {
		buttons.clear();
		int n = CrosshairPixels.N;
		cell = Math.max(6, Math.min(14, (vh - 60) / n));
		int gridSize = cell * n;
		gridX = Math.max(16, (vw - gridSize - 200) / 2);
		gridY = Math.max(34, (vh - gridSize) / 2 + 6);
		panelX = gridX + gridSize + 18;
		panelW = Math.min(200, vw - panelX - 12);

		int y = gridY + 86;
		int half = (panelW - 4) / 2;
		buttons.add(new Btn(panelX, y, half, 16, "Mirror L-R", () -> mirrorX = !mirrorX, () -> mirrorX, false));
		buttons.add(new Btn(panelX + half + 4, y, panelW - half - 4, 16, "Mirror U-D", () -> mirrorY = !mirrorY, () -> mirrorY, false));
		y += 20;
		String[][] presets = {{"Plus", "plus"}, {"Dot", "dot"}, {"Circle", "circle"}, {"X", "x"}};
		int q = (panelW - 12) / 4;
		for (int i = 0; i < presets.length; i++) {
			String id = presets[i][1];
			buttons.add(new Btn(panelX + i * (q + 4), y, i == 3 ? panelW - 3 * (q + 4) : q, 16, presets[i][0], () -> {
				snapshot();
				CrosshairPixels p = CrosshairPixels.preset(id);
				work.clear();
				for (int yy = 0; yy < CrosshairPixels.N; yy++) for (int xx = 0; xx < CrosshairPixels.N; xx++) work.set(xx, yy, p.get(xx, yy));
			}, () -> false, false));
		}
		y += 20;
		buttons.add(new Btn(panelX, y, half, 16, "Undo", this::undo, () -> false, false));
		buttons.add(new Btn(panelX + half + 4, y, panelW - half - 4, 16, "Clear", () -> {
			snapshot();
			work.clear();
		}, () -> false, false));
		int by = gridY + gridSize - 18;
		buttons.add(new Btn(panelX, by, half, 18, "Cancel", this::onClose, () -> false, false));
		buttons.add(new Btn(panelX + half + 4, by, panelW - half - 4, 18, "Save & use", this::save, () -> false, true));
	}

	private void snapshot() {
		undo.push(work.encode());
		while (undo.size() > 50) undo.removeLast();
	}

	private void undo() {
		if (undo.isEmpty()) return;
		CrosshairPixels p = CrosshairPixels.decode(undo.pop());
		work.clear();
		for (int y = 0; y < CrosshairPixels.N; y++) for (int x = 0; x < CrosshairPixels.N; x++) work.set(x, y, p.get(x, y));
	}

	private void save() {
		module.pixels.set(work.encode());
		module.shape.set(CrosshairModule.Shape.DRAWN);
		module.setEnabled(true);
		onClose();
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		g.fill(0, 0, width, height, Colors.withAlpha(Theme.background(), 0.9f));
	}

	@Override
	protected void renderScaled(GuiGraphics g, int mx, int my, float delta) {
		int n = CrosshairPixels.N, c = CrosshairPixels.C;
		int gridSize = cell * n;
		Ui.textScaled(g, "Draw your crosshair", gridX, 12, 1.4f, Theme.text(), false);
		Ui.text(g, "Left click draws, right click erases, drag to paint. Ctrl+Z undoes.", gridX, gridY - 12, Theme.textFaint());

		// grid
		Ui.rect(g, gridX - 2, gridY - 2, gridSize + 4, gridSize + 4, Theme.border());
		int color = module.color.argb() | 0xFF000000;
		for (int y = 0; y < n; y++) {
			for (int x = 0; x < n; x++) {
				int px = gridX + x * cell, py = gridY + y * cell;
				boolean checker = ((x + y) & 1) == 0;
				int bg = checker ? 0xFF202228 : 0xFF2A2D35;
				if (x == c || y == c) bg = Colors.mix(bg, Theme.accent(), 0.18f);
				Ui.rect(g, px, py, cell, cell, bg);
				if (work.get(x, y)) Ui.rect(g, px + 1, py + 1, cell - 2, cell - 2, color);
			}
		}
		int hx = Math.floorDiv(mx - gridX, cell), hy = Math.floorDiv(my - gridY, cell);
		if (hx >= 0 && hy >= 0 && hx < n && hy < n) {
			for (int[] p : targets(hx, hy)) Ui.roundOutline(g, gridX + p[0] * cell, gridY + p[1] * cell, cell, cell, 0, Colors.withAlpha(Theme.accent(), 0.9f));
		}

		// preview
		int pv = 76;
		Ui.card(g, panelX, gridY, panelW, pv, false, 0);
		int halfW = (panelW - 12) / 2;
		g.fill(panelX + 4, gridY + 4, panelX + 4 + halfW, gridY + pv - 4, 0xFF87A9D9);
		g.fill(panelX + 8 + halfW, gridY + 4, panelX + panelW - 4, gridY + pv - 4, 0xFF202326);
		int outline = module.outline.on() ? module.outlineColor.argb() : 0;
		int pc = module.color.argb();
		float s = module.size.value() / 100f;
		previewAt(g, panelX + 4 + halfW / 2, gridY + pv / 2, s, pc, outline);
		previewAt(g, panelX + 8 + halfW + halfW / 2, gridY + pv / 2, s * 2f, pc, outline);
		Ui.text(g, "1x", panelX + 7, gridY + pv - 13, Colors.withAlpha(0xFF000000, 0.6f));
		Ui.text(g, "2x", panelX + 11 + halfW, gridY + pv - 13, Colors.withAlpha(0xFFFFFFFF, 0.6f));
		Ui.text(g, "Color, outline and size come from the crosshair page.", panelX, gridY + pv + 2, Theme.textFaint());

		for (Btn b : buttons) {
			boolean hov = Ui.inside(mx, my, b.x(), b.y(), b.w(), b.h());
			boolean sel = b.selected().getAsBoolean();
			Ui.button(g, b.x(), b.y(), b.w(), b.h(), b.label(), hov, b.primary() || sel);
		}
	}

	private void previewAt(GuiGraphics g, int cx, int cy, float scale, int color, int outline) {
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(scale);
		CrosshairRenderer.drawn(g, work, 0, 0, color, outline);
		g.pose().popMatrix();
	}

	/** The cell plus its mirrored partners. */
	private List<int[]> targets(int x, int y) {
		int n = CrosshairPixels.N - 1;
		List<int[]> out = new ArrayList<>(4);
		out.add(new int[] {x, y});
		if (mirrorX) out.add(new int[] {n - x, y});
		if (mirrorY) out.add(new int[] {x, n - y});
		if (mirrorX && mirrorY) out.add(new int[] {n - x, n - y});
		return out;
	}

	private boolean paintAt(double mx, double my) {
		int x = (int) Math.floor((mx - gridX) / cell), y = (int) Math.floor((my - gridY) / cell);
		if (x < 0 || y < 0 || x >= CrosshairPixels.N || y >= CrosshairPixels.N || painting == null) return false;
		for (int[] p : targets(x, y)) work.set(p[0], p[1], painting);
		return true;
	}

	@Override
	protected boolean onClick(double mx, double my, int button) {
		for (Btn b : buttons) {
			if (button == 0 && Ui.inside(mx, my, b.x(), b.y(), b.w(), b.h())) {
				MenuSettings.click();
				b.action().run();
				return true;
			}
		}
		if (Ui.inside(mx, my, gridX, gridY, cell * CrosshairPixels.N, cell * CrosshairPixels.N) && (button == 0 || button == 1)) {
			snapshot();
			painting = button == 0;
			return paintAt(mx, my);
		}
		return false;
	}

	@Override
	protected boolean onDrag(double mx, double my, int button) {
		return painting != null && paintAt(mx, my);
	}

	@Override
	protected void onRelease(double mx, double my, int button) {
		painting = null;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_Z && (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
			undo();
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}
}
