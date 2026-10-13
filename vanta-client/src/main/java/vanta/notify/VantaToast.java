package vanta.notify;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;

import vanta.branding.Theme;
import vanta.ui.Colors;
import vanta.ui.Ui;

/** A notification in the VANTA look: rounded panel, accent bar, icon, title, message and a timer line. */
public final class VantaToast implements Toast {
	private final String title;
	private final String message;
	private final String[] icon;
	private final int color;
	private final long duration;
	private Visibility visibility = Visibility.SHOW;
	private final int width;

	public VantaToast(String title, String message, String[] icon, int color, long durationMs) {
		this.title = title;
		this.message = message == null ? "" : message;
		this.icon = icon;
		this.color = color;
		this.duration = durationMs;
		int textW = Math.max(Ui.width(title), Ui.width(this.message));
		this.width = Math.max(140, Math.min(260, textW + 34));
	}

	@Override public Visibility getWantedVisibility() { return visibility; }

	@Override
	public void update(ToastManager manager, long fullyVisibleForMs) {
		visibility = fullyVisibleForMs < duration ? Visibility.SHOW : Visibility.HIDE;
	}

	@Override public int width() { return width; }
	@Override public int height() { return 30; }

	@Override
	public void render(GuiGraphics g, Font font, long fullyVisibleForMs) {
		int w = width, h = 28;
		int accent = color != 0 ? color : Theme.accent();
		Ui.roundRect(g, 0, 0, w, h, Math.min(5, Theme.radius()), Colors.withAlpha(Theme.panel(), 0.95f));
		Ui.roundOutline(g, 0, 0, w, h, Math.min(5, Theme.radius()), Colors.withAlpha(accent, 0.45f));
		Ui.rect(g, 0, 4, 2, h - 8, accent | 0xFF000000);
		if (icon != null) Ui.icon(g, icon, 8, (h - 9) / 2, accent | 0xFF000000);
		int tx = icon != null ? 22 : 9;
		int maxW = w - tx - 6;
		g.drawString(font, Ui.ellipsize(title, maxW), tx, 5, Theme.text() | 0xFF000000, false);
		g.drawString(font, Ui.ellipsize(message, maxW), tx, 16, Theme.textDim() | 0xFF000000, false);
		float left = 1f - Math.min(1f, fullyVisibleForMs / (float) duration);
		Ui.rect(g, 4, h - 2, Math.round((w - 8) * left), 1, Colors.withAlpha(accent, 0.8f));
	}
}
