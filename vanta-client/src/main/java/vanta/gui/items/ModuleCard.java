package vanta.gui.items;

import net.minecraft.client.gui.GuiGraphics;

import vanta.branding.Theme;
import vanta.gui.MenuSettings;
import vanta.modules.Module;
import vanta.ui.Colors;
import vanta.ui.Icons;
import vanta.ui.Ui;

/**
 * A module as a card (grid layout) or a row (list layout).
 * Click: toggle. Gear: settings. Star: favorite.
 */
public final class ModuleCard extends Item {
	private final Module module;
	private final boolean list;
	private final MenuHost host;

	public ModuleCard(Module module, boolean list, MenuHost host) {
		this.module = module;
		this.list = list;
		this.host = host;
	}

	public static int gridHeight() {
		int base = MenuSettings.SHOW_DESCRIPTIONS.on() ? 56 : 42;
		return Math.round(base * Theme.density());
	}

	public static int listHeight() { return Math.max(20, Math.round(24 * Theme.density())); }

	private boolean hasSettings() { return !module.visibleSettings().isEmpty(); }

	// Hit boxes relative to the card.
	private int toggleX() { return w - 8 - Ui.toggleWidth(); }
	private int toggleY() { return list ? (h - 10) / 2 : 8; }
	private int gearX() { return list ? w - 46 : w - 17; }
	private int gearY() { return list ? (h - 9) / 2 : h - 15; }
	private int starX() { return list ? w - (hasSettings() ? 62 : 46) : w - 44; }
	private int starY() { return list ? (h - 9) / 2 : 8; }

	@Override
	public void render(GuiGraphics g, int ax, int ay, int mx, int my, boolean hovered) {
		boolean on = module.isEnabled();
		int cat = Theme.categoryColor(module.category());
		int outline = host.isHighlighted(module.id()) ? Theme.secondary() : on ? Colors.fade(cat, 0.55f) : 0;
		Ui.card(g, ax, ay, w, h, hovered, outline);
		if (on) Ui.rect(g, ax, ay + Math.min(4, h / 4), 2, h - Math.min(8, h / 2), cat);

		boolean starHover = Ui.inside(mx, my, ax + starX() - 2, ay + starY() - 2, 13, 13);
		if (module.isFavorite()) Ui.icon(g, Icons.STAR, ax + starX(), ay + starY(), Theme.secondary());
		else if (hovered) Ui.icon(g, Icons.STAR, ax + starX(), ay + starY(), starHover ? Theme.textDim() : Theme.textFaint());

		if (hasSettings()) {
			boolean gearHover = Ui.inside(mx, my, ax + gearX() - 2, ay + gearY() - 2, 13, 13);
			Ui.icon(g, Icons.GEAR, ax + gearX(), ay + gearY(), gearHover ? Theme.accent() : Theme.textFaint());
		}
		Ui.toggle(g, ax + toggleX(), ay + toggleY(), on, hovered);

		String status = module.status();
		if (list) {
			int nameW = Ui.width(module.name());
			Ui.text(g, module.name(), ax + 8, ay + (h - 8) / 2, Theme.text());
			int textRight = ax + starX() - 6;
			int descX = ax + 8 + nameW + 8;
			String right = status != null ? status : module.category().label;
			int rightW = Math.min(Ui.width(right), Math.max(0, (textRight - descX) / 2));
			if (MenuSettings.SHOW_DESCRIPTIONS.on()) {
				Ui.text(g, Ui.ellipsize(module.description(), textRight - descX - rightW - 8), descX, ay + (h - 8) / 2, Theme.textFaint());
			}
			if (rightW > 10) Ui.textRight(g, Ui.ellipsize(right, rightW), textRight, ay + (h - 8) / 2, Colors.fade(cat, 0.85f));
		} else {
			String name = module.name() + (module.isExperimental() ? " (Experimental)" : "");
			Ui.text(g, Ui.ellipsize(name, starX() - 12), ax + 8, ay + 9, Theme.text());
			if (MenuSettings.SHOW_DESCRIPTIONS.on()) {
				Ui.text(g, Ui.ellipsize(module.description(), w - 16), ax + 8, ay + 22, Theme.textFaint());
			}
			String bottom = status != null ? status : module.category().label;
			Ui.text(g, Ui.ellipsize(bottom, w - 34), ax + 8, ay + h - 14, on ? Colors.fade(cat, 0.9f) : Theme.textFaint());
		}
	}

	@Override
	public boolean click(int ax, int ay, double mx, double my, int button) {
		MenuSettings.click();
		if (Ui.inside(mx, my, ax + starX() - 2, ay + starY() - 2, 13, 13)) {
			module.toggleFavorite();
			host.rebuild();
			return true;
		}
		if (hasSettings() && (button == 1 || Ui.inside(mx, my, ax + gearX() - 2, ay + gearY() - 2, 13, 13))) {
			host.openModule(module);
			return true;
		}
		module.toggle();
		return true;
	}

	@Override
	public String hint() {
		return module.description() + (hasSettings() ? "  (Right-click or gear: settings)" : "");
	}

	@Override public String key() { return module.id(); }
}
