package vanta.gui.items;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphics;

import vanta.branding.Theme;
import vanta.gui.MenuSettings;
import vanta.ui.Colors;
import vanta.ui.Ui;

/**
 * A row bound directly to a value stored elsewhere (usually a Minecraft video option),
 * so VANTA shows and changes the real setting instead of keeping a copy.
 */
public final class OptionRow extends Item {
	private enum Kind { TOGGLE, SLIDER, CYCLE }

	private static final int SLIDER_W = 80;

	private final Kind kind;
	private final String name;
	private final String description;
	private BooleanSupplier boolGet;
	private Runnable toggle;
	private IntSupplier intGet;
	private IntConsumer intSet;
	private IntFunction<String> intLabel;
	private int min, max, step;
	private Supplier<String> cycleLabel;
	private Runnable next, previous;
	private boolean dragging;
	private final MenuHost host;

	private OptionRow(Kind kind, String name, String description, MenuHost host) {
		this.kind = kind;
		this.name = name;
		this.description = description;
		this.host = host;
	}

	public static OptionRow toggle(MenuHost host, String name, String description, BooleanSupplier get, Runnable toggle) {
		OptionRow r = new OptionRow(Kind.TOGGLE, name, description, host);
		r.boolGet = get;
		r.toggle = toggle;
		return r;
	}

	public static OptionRow slider(MenuHost host, String name, String description, int min, int max, int step,
			IntSupplier get, IntConsumer set, IntFunction<String> label) {
		OptionRow r = new OptionRow(Kind.SLIDER, name, description, host);
		r.min = min;
		r.max = max;
		r.step = Math.max(1, step);
		r.intGet = get;
		r.intSet = set;
		r.intLabel = label;
		return r;
	}

	public static OptionRow cycle(MenuHost host, String name, String description, Supplier<String> label, Runnable next, Runnable previous) {
		OptionRow r = new OptionRow(Kind.CYCLE, name, description, host);
		r.cycleLabel = label;
		r.next = next;
		r.previous = previous;
		return r;
	}

	private int controlWidth() {
		return switch (kind) {
			case TOGGLE -> Ui.toggleWidth();
			case SLIDER -> SLIDER_W + 52;
			case CYCLE -> 104;
		};
	}

	private int controlLeft() { return w - 8 - controlWidth(); }

	private void setFraction(double f) {
		f = Math.max(0, Math.min(1, f));
		int v = (int) Math.round(min + f * (max - min));
		v = min + Math.round((v - min) / (float) step) * step;
		intSet.accept(Math.max(min, Math.min(max, v)));
	}

	@Override
	public void render(GuiGraphics g, int ax, int ay, int mx, int my, boolean hovered) {
		Ui.card(g, ax, ay, w, h, hovered, 0);
		int textW = controlLeft() - 16;
		boolean twoLines = h >= 26;
		Ui.text(g, Ui.ellipsize(name, textW), ax + 8, ay + (twoLines ? 5 : (h - 8) / 2), Theme.text());
		if (twoLines) Ui.text(g, Ui.ellipsize(description, textW), ax + 8, ay + 16, Theme.textFaint());
		int cy = ay + h / 2;
		int cx = ax + controlLeft();
		switch (kind) {
			case TOGGLE -> Ui.toggle(g, ax + w - 8 - Ui.toggleWidth(), cy - 5, boolGet.getAsBoolean(), hovered);
			case SLIDER -> {
				int v = intGet.getAsInt();
				Ui.roundRect(g, cx, cy - 6, 48, 12, 2, Colors.withAlpha(Theme.background(), 0.5f));
				Ui.textCentered(g, Ui.ellipsize(intLabel.apply(v), 46), cx + 24, cy - 4, Theme.text());
				Ui.slider(g, cx + 52, cy - 5, SLIDER_W, max == min ? 0 : (v - min) / (double) (max - min), dragging);
			}
			case CYCLE -> {
				int bw = controlWidth();
				Ui.roundRect(g, cx, cy - 7, bw, 14, Theme.radius(), Colors.withAlpha(Theme.background(), 0.5f));
				boolean lh = Ui.inside(mx, my, cx, cy - 7, 14, 14);
				boolean rh = Ui.inside(mx, my, cx + bw - 14, cy - 7, 14, 14);
				Ui.text(g, "<", cx + 4, cy - 4, lh ? Theme.accent() : Theme.textDim());
				Ui.text(g, ">", cx + bw - 9, cy - 4, rh ? Theme.accent() : Theme.textDim());
				Ui.textCentered(g, Ui.ellipsize(cycleLabel.get(), bw - 28), cx + bw / 2, cy - 4, Theme.text());
			}
		}
	}

	@Override
	public boolean click(int ax, int ay, double mx, double my, int button) {
		int cy = ay + h / 2;
		int cx = ax + controlLeft();
		switch (kind) {
			case TOGGLE -> {
				MenuSettings.click();
				toggle.run();
				return true;
			}
			case SLIDER -> {
				if (Ui.inside(mx, my, cx + 48, cy - 8, SLIDER_W + 8, 16)) {
					dragging = true;
					host.beginDrag(this);
					setFraction((mx - (cx + 52)) / SLIDER_W);
					return true;
				}
				return false;
			}
			case CYCLE -> {
				MenuSettings.click();
				if (button == 1 || Ui.inside(mx, my, cx, cy - 7, 14, 14)) previous.run();
				else next.run();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean drag(int ax, int ay, double mx, double my) {
		if (!dragging) return false;
		setFraction((mx - (ax + controlLeft() + 52)) / SLIDER_W);
		return true;
	}

	@Override
	public void release() { dragging = false; }

	@Override
	public boolean scroll(int ax, int ay, double mx, double my, double amount) {
		if (kind != Kind.SLIDER) return false;
		int cy = ay + h / 2;
		if (!Ui.inside(mx, my, ax + controlLeft(), cy - 8, controlWidth(), 16)) return false;
		int v = intGet.getAsInt() + (amount > 0 ? step : -step);
		intSet.accept(Math.max(min, Math.min(max, v)));
		return true;
	}

	@Override
	public String hint() { return description + "  (Minecraft setting)"; }
}
