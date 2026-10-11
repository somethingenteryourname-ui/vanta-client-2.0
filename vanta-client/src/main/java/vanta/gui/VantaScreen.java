package vanta.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import vanta.branding.Theme;
import vanta.metrics.FrameMetrics;
import vanta.ui.Colors;
import vanta.ui.Ui;

/**
 * Base for every VANTA screen. Handles the interface-scale setting: content is laid out
 * in "virtual" pixels and scaled with the GUI pose, and mouse positions are converted
 * back, so all VANTA screens scale the same way and stay usable at any window size.
 */
public abstract class VantaScreen extends Screen {
	protected final Screen parent;
	protected float scale = 1f;
	/** Virtual (scaled) width and height. */
	protected int vw;
	protected int vh;
	protected long openedAt;
	private int lastMouseX;
	private int lastMouseY;

	protected VantaScreen(Component title, Screen parent) {
		super(title);
		this.parent = parent;
	}

	/** Smallest virtual size the screen's layout needs. */
	protected int minVirtualWidth() { return 340; }
	protected int minVirtualHeight() { return 220; }

	@Override
	protected void init() {
		clearWidgets();
		if (openedAt == 0) openedAt = System.currentTimeMillis();
		float wanted = Theme.uiScale();
		float fit = Math.min(width / (float) minVirtualWidth(), height / (float) minVirtualHeight());
		scale = Math.max(0.5f, Math.min(wanted, fit));
		vw = Math.round(width / scale);
		vh = Math.round(height / scale);
		layout();
	}

	/** Compute positions in virtual pixels and (re)create any vanilla widgets. */
	protected abstract void layout();

	/** Draw in virtual pixels; mouse coordinates are already converted. */
	protected abstract void renderScaled(GuiGraphics g, int mouseX, int mouseY, float delta);

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		lastMouseX = toVirtual(mouseX);
		lastMouseY = toVirtual(mouseY);
		g.pose().pushMatrix();
		g.pose().scale(scale);
		renderScaled(g, lastMouseX, lastMouseY, delta);
		g.pose().popMatrix();
		super.render(g, mouseX, mouseY, delta); // vanilla widgets (search box) in real pixels
		renderAfterWidgets(g, lastMouseX, lastMouseY);
	}

	/** Optional extra layer drawn above vanilla widgets (still scaled). */
	protected void renderAfterWidgets(GuiGraphics g, int mouseX, int mouseY) {
	}

	protected final int toVirtual(double real) { return (int) Math.floor(real / scale); }
	protected final int toReal(int virtual) { return Math.round(virtual * scale); }
	protected final int mouseX() { return lastMouseX; }
	protected final int mouseY() { return lastMouseY; }

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		setFocused(null);
		return onClick(event.x() / scale, event.y() / scale, event.button());
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		onRelease(event.x() / scale, event.y() / scale, event.button());
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (onDrag(event.x() / scale, event.y() / scale, event.button())) return true;
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (onScroll(mouseX / scale, mouseY / scale, scrollY)) return true;
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	protected boolean onClick(double mx, double my, int button) { return false; }
	protected void onRelease(double mx, double my, int button) { }
	protected boolean onDrag(double mx, double my, int button) { return false; }
	protected boolean onScroll(double mx, double my, double amount) { return false; }

	@Override
	public void onClose() {
		vanta.config.ConfigManager.get().saveIfDirty();
		minecraft.setScreen(parent);
	}

	/** Opening slide/fade progress 0..1. */
	protected final float openProgress() {
		return vanta.ui.Anim.easeOutCubic(vanta.ui.Anim.progress(openedAt, 180f));
	}

	/** Small live FPS readout used by VANTA menus (optional in Theme settings). */
	protected final void drawMenuFps(GuiGraphics g, int right, int y) {
		if (!Theme.SHOW_MENU_FPS.on()) return;
		String s = FrameMetrics.fps() + " FPS";
		Ui.textRight(g, s, right, y, Colors.fade(Theme.textDim(), 0.9f));
	}
}
