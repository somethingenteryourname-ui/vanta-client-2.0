package vanta.hud;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import vanta.branding.Theme;
import vanta.gui.MenuSettings;
import vanta.gui.VantaMenuScreen;
import vanta.modules.ModuleRegistry;
import vanta.ui.Colors;
import vanta.ui.Icons;
import vanta.ui.Ui;

/**
 * Drag-and-drop HUD editor. Works directly in real GUI pixels (not the menu's interface
 * scale) so what you place is exactly what you get in game.
 * Drag: move. Scroll: resize. Right-click: show/hide. Arrows: nudge. R: reset selected.
 */
public final class HudEditorScreen extends Screen {
	private static boolean snap = true;
	private static boolean showHidden = true;
	private static final int GRID = 4;
	private static final int SNAP_DISTANCE = 4;

	private final Screen parent;
	private HudModule dragging;
	private HudModule selected;
	private double grabX, grabY;
	private boolean guideV, guideH;
	private final int[] btnX = new int[4];
	private int btnY, btnW = 72, btnH = 16;

	public HudEditorScreen(Screen parent) {
		super(Component.literal("VANTA HUD Editor"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int total = btnW * 4 + 6 * 3;
		int x0 = (width - total) / 2;
		for (int i = 0; i < 4; i++) btnX[i] = x0 + i * (btnW + 6);
		btnY = height - btnH - 8;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		if (minecraft.level == null) {
			g.fill(0, 0, width, height, Theme.background());
		} else {
			g.fill(0, 0, width, height, 0x38000000);
		}
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float delta) {
		if (snap) {
			int line = Colors.withAlpha(Theme.text(), 0.04f);
			for (int x = 0; x < width; x += GRID * 4) g.fill(x, 0, x + 1, height, line);
			for (int y = 0; y < height; y += GRID * 4) g.fill(0, y, width, y + 1, line);
		}
		if (dragging != null) {
			if (guideV) g.fill(width / 2, 0, width / 2 + 1, height, Theme.secondary());
			if (guideH) g.fill(0, height / 2, width, height / 2 + 1, Theme.secondary());
		}

		HudModule hovered = widgetAt(mx, my);
		for (HudModule m : ModuleRegistry.hud()) {
			if (!m.isEnabled() && !showHidden) continue;
			m.render(g, width, height, true);
			int outline;
			if (!m.isEnabled()) {
				g.fill(m.drawnX, m.drawnY, m.drawnX + m.drawnW, m.drawnY + m.drawnH, 0x99000000);
				outline = Colors.fade(Theme.textFaint(), 0.8f);
			} else if (m == dragging || m == selected) {
				outline = Theme.accent();
			} else if (m == hovered) {
				outline = Theme.secondary();
			} else {
				outline = Colors.fade(Theme.accent(), 0.35f);
			}
			Ui.roundOutline(g, m.drawnX - 1, m.drawnY - 1, m.drawnW + 2, m.drawnH + 2, 0, outline);
			if (m.locked.on()) Ui.text(g, "locked", m.drawnX, m.drawnY - 9, Theme.textFaint());
			if (m == hovered || m == selected) {
				String label = m.name() + (m.isEnabled() ? "" : " (hidden)") + "  " + m.scale.value() + "%";
				int ly = m.drawnY - 10 < 0 ? m.drawnY + m.drawnH + 2 : m.drawnY - 10;
				int lw = Ui.width(label) + 6;
				int lx = Math.min(m.drawnX, width - lw - 2);
				Ui.roundRect(g, lx, ly, lw, 10, 2, Colors.withAlpha(Theme.background(), 0.85f));
				Ui.text(g, label, lx + 3, ly + 1, Theme.text());
			}
		}

		// Help bar
		String help = "Drag to move  -  Scroll to resize  -  Right-click to show/hide  -  Arrows nudge  -  R resets";
		int hw = Ui.width(help) + 16;
		Ui.roundRect(g, (width - hw) / 2, 6, hw, 14, Theme.radius(), Colors.withAlpha(Theme.background(), 0.85f));
		Ui.icon(g, Icons.MOVE, (width - hw) / 2 + 3, 8, Theme.accent());
		Ui.textCentered(g, help, width / 2 + 4, 9, Theme.textDim());

		String[] labels = {"Snap: " + (snap ? "On" : "Off"), showHidden ? "Hide hidden" : "Show hidden", "Reset all", "Done"};
		for (int i = 0; i < 4; i++) {
			boolean hov = Ui.inside(mx, my, btnX[i], btnY, btnW, btnH);
			Ui.button(g, btnX[i], btnY, btnW, btnH, labels[i], hov, i == 3);
		}
		super.render(g, mx, my, delta);
	}

	private HudModule widgetAt(double mx, double my) {
		List<HudModule> hud = ModuleRegistry.hud();
		for (int i = hud.size() - 1; i >= 0; i--) {
			HudModule m = hud.get(i);
			if (!m.isEnabled() && !showHidden) continue;
			if (m.drawnW > 0 && Ui.inside(mx, my, m.drawnX, m.drawnY, m.drawnW, m.drawnH)) return m;
		}
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x(), my = event.y();
		for (int i = 0; i < 4; i++) {
			if (Ui.inside(mx, my, btnX[i], btnY, btnW, btnH)) {
				MenuSettings.click();
				switch (i) {
					case 0 -> snap = !snap;
					case 1 -> showHidden = !showHidden;
					case 2 -> minecraft.setScreen(new vanta.gui.ConfirmDialog(this, "Reset HUD layout?",
							"Moves every widget back to its default position and size.", "Reset", () -> {
								for (HudModule m : ModuleRegistry.hud()) {
									m.posX.reset();
									m.posY.reset();
									m.scale.reset();
								}
							}));
					default -> onClose();
				}
				return true;
			}
		}
		HudModule m = widgetAt(mx, my);
		if (m == null) {
			selected = null;
			return super.mouseClicked(event, doubleClick);
		}
		selected = m;
		if (event.button() == 1) {
			MenuSettings.click();
			m.toggle();
			return true;
		}
		if (doubleClick) {
			minecraft.setScreen(VantaMenuScreen.forModule(this, m));
			return true;
		}
		if (event.button() == 0 && !m.locked.on()) {
			dragging = m;
			grabX = mx - m.drawnX;
			grabY = my - m.drawnY;
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging == null) return super.mouseDragged(event, dragX, dragY);
		int x = (int) Math.round(event.x() - grabX);
		int y = (int) Math.round(event.y() - grabY);
		guideV = guideH = false;
		int cx = x + dragging.drawnW / 2;
		int cy = y + dragging.drawnH / 2;
		if (Math.abs(cx - width / 2) <= SNAP_DISTANCE) {
			x = width / 2 - dragging.drawnW / 2;
			guideV = true;
		} else if (snap) {
			x = Math.round(x / (float) GRID) * GRID;
		}
		if (Math.abs(cy - height / 2) <= SNAP_DISTANCE) {
			y = height / 2 - dragging.drawnH / 2;
			guideH = true;
		} else if (snap) {
			y = Math.round(y / (float) GRID) * GRID;
		}
		// Snap to screen edges.
		if (x < HudModule.MARGIN + SNAP_DISTANCE) x = HudModule.MARGIN;
		if (y < HudModule.MARGIN + SNAP_DISTANCE) y = HudModule.MARGIN;
		if (x + dragging.drawnW > width - HudModule.MARGIN - SNAP_DISTANCE) x = width - HudModule.MARGIN - dragging.drawnW;
		if (y + dragging.drawnH > height - HudModule.MARGIN - SNAP_DISTANCE) y = height - HudModule.MARGIN - dragging.drawnH;
		dragging.moveTo(x, y, width, height);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = null;
		guideV = guideH = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		HudModule m = widgetAt(mx, my);
		if (m == null) return false;
		m.scale.set(m.scale.value() + (scrollY > 0 ? 10 : -10));
		selected = m;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			onClose();
			return true;
		}
		if (selected != null) {
			int dx = 0, dy = 0;
			if (key == GLFW.GLFW_KEY_LEFT) dx = -1;
			else if (key == GLFW.GLFW_KEY_RIGHT) dx = 1;
			else if (key == GLFW.GLFW_KEY_UP) dy = -1;
			else if (key == GLFW.GLFW_KEY_DOWN) dy = 1;
			if ((dx != 0 || dy != 0) && !selected.locked.on()) {
				selected.moveTo(selected.drawnX + dx, selected.drawnY + dy, width, height);
				return true;
			}
			if (key == GLFW.GLFW_KEY_R) {
				selected.posX.reset();
				selected.posY.reset();
				selected.scale.reset();
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		vanta.config.ConfigManager.get().saveIfDirty();
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() { return false; }
}
