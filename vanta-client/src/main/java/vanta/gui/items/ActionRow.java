package vanta.gui.items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphics;

import vanta.branding.Theme;
import vanta.gui.MenuSettings;
import vanta.ui.Colors;
import vanta.ui.Ui;

/** A titled row with a few small buttons on the right (profiles, saved themes). */
public final class ActionRow extends Item {
	private record Action(String label, Runnable run, boolean danger) {
	}

	private final Supplier<String> title;
	private final Supplier<String> subtitle;
	private final Supplier<Boolean> highlighted;
	private final List<Action> actions = new ArrayList<>();
	private final String hint;

	public ActionRow(Supplier<String> title, Supplier<String> subtitle, Supplier<Boolean> highlighted, String hint) {
		this.title = title;
		this.subtitle = subtitle;
		this.highlighted = highlighted;
		this.hint = hint;
	}

	public ActionRow action(String label, Runnable run) {
		actions.add(new Action(label, run, false));
		return this;
	}

	public ActionRow danger(String label, Runnable run) {
		actions.add(new Action(label, run, true));
		return this;
	}

	private int buttonWidth(Action a) { return Ui.width(a.label()) + 12; }

	private int buttonsLeft() {
		int x = w - 6;
		for (int i = actions.size() - 1; i >= 0; i--) x -= buttonWidth(actions.get(i)) + 4;
		return x + 4;
	}

	@Override
	public void render(GuiGraphics g, int ax, int ay, int mx, int my, boolean hovered) {
		boolean hi = highlighted != null && highlighted.get();
		Ui.card(g, ax, ay, w, h, hovered, hi ? Theme.accent() : 0);
		if (hi) Ui.rect(g, ax, ay + 3, 2, h - 6, Theme.accent());
		int textW = buttonsLeft() - 14;
		String t = title.get();
		Ui.text(g, Ui.ellipsize(t, textW), ax + 8, ay + (subtitle == null ? (h - 8) / 2 : 5), Theme.text());
		if (subtitle != null) Ui.text(g, Ui.ellipsize(subtitle.get(), textW), ax + 8, ay + 16, Theme.textFaint());
		int bx = ax + buttonsLeft();
		int by = ay + (h - 14) / 2;
		for (Action a : actions) {
			int bw = buttonWidth(a);
			boolean hov = Ui.inside(mx, my, bx, by, bw, 14);
			int fill = a.danger() ? (hov ? 0xFFF05252 : Colors.withAlpha(0xFFDC3B3B, 0.75f)) : (hov ? Theme.panelHover() : Colors.withAlpha(Theme.background(), 0.5f));
			Ui.roundRect(g, bx, by, bw, 14, Theme.radius(), fill);
			Ui.textCentered(g, a.label(), bx + bw / 2, by + 3, a.danger() ? 0xFFFFFFFF : hov ? Theme.accent() : Theme.text());
			bx += bw + 4;
		}
	}

	@Override
	public boolean click(int ax, int ay, double mx, double my, int button) {
		int bx = ax + buttonsLeft();
		int by = ay + (h - 14) / 2;
		for (Action a : actions) {
			int bw = buttonWidth(a);
			if (Ui.inside(mx, my, bx, by, bw, 14)) {
				MenuSettings.click();
				a.run().run();
				return true;
			}
			bx += bw + 4;
		}
		return false;
	}

	@Override public String hint() { return hint; }
}
