package vanta.gui;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import vanta.branding.Theme;
import vanta.ui.Colors;
import vanta.ui.Ui;

/** Themed yes/no confirmation used before anything destructive (resets, restores). */
public final class ConfirmDialog extends VantaScreen {
	private final String heading;
	private final String[] lines;
	private final String confirmLabel;
	private final Runnable onConfirm;
	private int px, py, pw, ph;

	public ConfirmDialog(Screen parent, String heading, String message, String confirmLabel, Runnable onConfirm) {
		super(Component.literal(heading), parent);
		this.heading = heading;
		this.lines = message.split("\n");
		this.confirmLabel = confirmLabel;
		this.onConfirm = onConfirm;
	}

	@Override protected int minVirtualWidth() { return 260; }
	@Override protected int minVirtualHeight() { return 130; }

	@Override
	protected void layout() {
		pw = Math.min(300, vw - 20);
		ph = 64 + lines.length * 11;
		px = (vw - pw) / 2;
		py = (vh - ph) / 2;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		g.fill(0, 0, width, height, Colors.withAlpha(Theme.background(), 0.82f));
	}

	@Override
	protected void renderScaled(GuiGraphics g, int mx, int my, float delta) {
		Ui.panel(g, px, py, pw, ph, Theme.panel());
		Ui.rect(g, px, py, 3, ph, 0xFFF87171);
		Ui.text(g, heading, px + 12, py + 10, Theme.text());
		int y = py + 26;
		for (String line : lines) {
			Ui.text(g, Ui.ellipsize(line, pw - 24), px + 12, y, Theme.textDim());
			y += 11;
		}
		int bw = 80;
		Ui.button(g, px + pw - bw * 2 - 16, py + ph - 26, bw, 18, "Cancel", Ui.inside(mx, my, px + pw - bw * 2 - 16, py + ph - 26, bw, 18), false);
		boolean hov = Ui.inside(mx, my, px + pw - bw - 8, py + ph - 26, bw, 18);
		Ui.roundRect(g, px + pw - bw - 8, py + ph - 26, bw, 18, Theme.radius(), hov ? 0xFFF05252 : 0xFFDC3B3B);
		Ui.textCentered(g, confirmLabel, px + pw - bw / 2 - 8, py + ph - 21, 0xFFFFFFFF);
	}

	@Override
	protected boolean onClick(double mx, double my, int button) {
		int bw = 80;
		if (Ui.inside(mx, my, px + pw - bw - 8, py + ph - 26, bw, 18)) {
			MenuSettings.click();
			onConfirm.run();
			onClose();
			return true;
		}
		if (Ui.inside(mx, my, px + pw - bw * 2 - 16, py + ph - 26, bw, 18)) {
			onClose();
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() { return false; }
}
