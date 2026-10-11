package vanta.gui.items;

import java.util.function.BooleanSupplier;

import net.minecraft.client.gui.GuiGraphics;

import vanta.branding.Theme;
import vanta.gui.MenuSettings;
import vanta.ui.Colors;
import vanta.ui.Ui;

/** A selectable card with a title and two-line description. */
public final class PresetCard extends Item {
	private final String title;
	private final String description;
	private final BooleanSupplier selected;
	private final BooleanSupplier active;
	private final Runnable action;

	public PresetCard(String title, String description, BooleanSupplier selected, BooleanSupplier active, Runnable action) {
		this.title = title;
		this.description = description;
		this.selected = selected;
		this.active = active;
		this.action = action;
	}

	@Override
	public void render(GuiGraphics g, int ax, int ay, int mx, int my, boolean hovered) {
		boolean sel = selected.getAsBoolean();
		Ui.card(g, ax, ay, w, h, hovered, sel ? Theme.accent() : 0);
		if (sel) Ui.accentFill(g, ax, ay, w, 2, 0, 0f);
		Ui.text(g, Ui.ellipsize(title, w - 16), ax + 8, ay + 7, Theme.text());
		if (active.getAsBoolean()) Ui.textRight(g, "Active", ax + w - 8, ay + 7, Colors.fade(Theme.accent(), 0.9f));
		var lines = BasicItems.Paragraph.wrap(description, w - 16);
		for (int i = 0; i < Math.min(2, lines.size()); i++) Ui.text(g, lines.get(i), ax + 8, ay + 20 + i * 10, Theme.textFaint());
	}

	@Override
	public boolean click(int ax, int ay, double mx, double my, int button) {
		MenuSettings.click();
		action.run();
		return true;
	}

	@Override public String hint() { return description + "  (Click to preview the changes)"; }
}
