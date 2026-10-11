package vanta.gui;

import java.util.function.Function;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import vanta.branding.Theme;
import vanta.ui.Colors;
import vanta.ui.Ui;

/**
 * Small themed dialog with one text field. The validator returns an error message,
 * or null if the input is accepted (it applies the value itself in that case).
 */
public final class TextInputScreen extends VantaScreen {
	private final String heading;
	private final String help;
	private final String initial;
	private final int maxLength;
	private final Function<String, String> apply;
	private EditBox box;
	private String error;
	private int px, py, pw, ph;

	public TextInputScreen(Screen parent, String heading, String help, String initial, int maxLength, Function<String, String> apply) {
		super(Component.literal(heading), parent);
		this.heading = heading;
		this.help = help;
		this.initial = initial;
		this.maxLength = maxLength;
		this.apply = apply;
	}

	@Override protected int minVirtualWidth() { return 260; }
	@Override protected int minVirtualHeight() { return 130; }

	@Override
	protected void layout() {
		pw = Math.min(280, vw - 20);
		ph = 104;
		px = (vw - pw) / 2;
		py = (vh - ph) / 2;
		String value = box != null ? box.getValue() : initial;
		box = new EditBox(font, toReal(px + 12), toReal(py + 42), toReal(pw - 24) , 12, Component.literal(heading));
		box.setBordered(false);
		box.setMaxLength(maxLength);
		box.setValue(value);
		box.setTextColor(Theme.text());
		addRenderableWidget(box);
		setFocused(box);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		g.fill(0, 0, width, height, Colors.withAlpha(Theme.background(), 0.82f));
	}

	@Override
	protected void renderScaled(GuiGraphics g, int mx, int my, float delta) {
		Ui.panel(g, px, py, pw, ph, Theme.panel());
		Ui.rect(g, px, py, 3, ph, Theme.accent());
		Ui.text(g, heading, px + 12, py + 10, Theme.text());
		Ui.text(g, Ui.ellipsize(help, pw - 24), px + 12, py + 22, Theme.textDim());
		Ui.roundRect(g, px + 8, py + 37, pw - 16, 16, Theme.radius(), Colors.mix(Theme.background(), Theme.panel(), 0.4f));
		Ui.roundOutline(g, px + 8, py + 37, pw - 16, 16, Theme.radius(), Colors.fade(Theme.accent(), 0.8f));
		if (error != null) Ui.text(g, Ui.ellipsize(error, pw - 24), px + 12, py + 58, 0xFFF87171);
		int bw = 70;
		Ui.button(g, px + pw - bw * 2 - 16, py + ph - 26, bw, 18, "Cancel", Ui.inside(mx, my, px + pw - bw * 2 - 16, py + ph - 26, bw, 18), false);
		Ui.button(g, px + pw - bw - 8, py + ph - 26, bw, 18, "Save", Ui.inside(mx, my, px + pw - bw - 8, py + ph - 26, bw, 18), true);
		Ui.text(g, "Enter to save, Esc to cancel", px + 12, py + ph - 21, Theme.textFaint());
	}

	@Override
	protected boolean onClick(double mx, double my, int button) {
		int bw = 70;
		if (Ui.inside(mx, my, px + pw - bw - 8, py + ph - 26, bw, 18)) {
			submit();
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
		if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
			submit();
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	private void submit() {
		String result = apply.apply(box.getValue());
		if (result == null) {
			MenuSettings.click();
			onClose();
		} else {
			error = result;
		}
	}

	@Override
	public boolean isPauseScreen() { return false; }
}
