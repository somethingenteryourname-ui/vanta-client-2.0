package vanta.gui.items;

import net.minecraft.client.gui.GuiGraphics;

import vanta.branding.Theme;
import vanta.branding.UiFormat;
import vanta.gui.MenuSettings;
import vanta.modules.Module;
import vanta.ui.Colors;
import vanta.ui.Icons;
import vanta.ui.Ui;

/**
 * A module as a card. Looks: Standard card, Icon row (icon tile, bold name, description,
 * switch) or Tile (big icon with an Enabled/Disabled bar), plus the compact list row.
 * Click: toggle. Right-click or gear: settings. Star: favorite.
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

	private static UiFormat.CardLook look() { return Theme.CARD_LOOK.get(); }

	public static int gridHeight() {
		return switch (look()) {
			case ICON_ROWS -> Math.max(28, Math.round((MenuSettings.SHOW_DESCRIPTIONS.on() ? 38 : 30) * Theme.density()));
			case TILES -> Math.max(66, Math.round(84 * Theme.density()));
			case STANDARD -> Math.round((MenuSettings.SHOW_DESCRIPTIONS.on() ? 56 : 42) * Theme.density());
		};
	}

	/** Minimum card width for the grid at the current look (Icon rows: two columns on most screens). */
	public static int gridMinWidth(int bodyWidth, int gap) {
		return switch (look()) {
			case ICON_ROWS -> bodyWidth >= 360 ? (bodyWidth - gap) / 2 : bodyWidth;
			case TILES -> Math.round(96 * Theme.density());
			case STANDARD -> Math.round(Theme.CARD_WIDTH.value() * Theme.density());
		};
	}

	public static int listHeight() { return Math.max(20, Math.round(24 * Theme.density())); }

	private boolean hasSettings() { return !module.visibleSettings().isEmpty(); }

	private UiFormat.CardLook mode() { return list ? UiFormat.CardLook.STANDARD : look(); }

	// Hit boxes relative to the card.
	private int toggleX() {
		return switch (mode()) {
			case ICON_ROWS -> w - 10 - Ui.toggleWidth();
			case TILES -> 0;
			case STANDARD -> w - 8 - Ui.toggleWidth();
		};
	}

	private int toggleY() {
		return switch (mode()) {
			case ICON_ROWS -> (h - 10) / 2;
			case TILES -> h - tileBar();
			case STANDARD -> list ? (h - 10) / 2 : 8;
		};
	}

	private int gearX() {
		return switch (mode()) {
			case ICON_ROWS -> toggleX() - 15;
			case TILES -> w - 14;
			case STANDARD -> list ? w - 46 : w - 17;
		};
	}

	private int gearY() {
		return switch (mode()) {
			case ICON_ROWS -> (h - 9) / 2;
			case TILES -> 5;
			case STANDARD -> list ? (h - 9) / 2 : h - 15;
		};
	}

	private int starX() {
		return switch (mode()) {
			case ICON_ROWS -> gearX() - (hasSettings() ? 14 : 0);
			case TILES -> 5;
			case STANDARD -> list ? w - (hasSettings() ? 62 : 46) : w - 44;
		};
	}

	private int starY() {
		return switch (mode()) {
			case ICON_ROWS -> (h - 9) / 2;
			case TILES -> 5;
			case STANDARD -> list ? (h - 9) / 2 : 8;
		};
	}

	private int tileBar() { return Math.max(12, Math.min(16, h / 6)); }

	@Override
	public void render(GuiGraphics g, int ax, int ay, int mx, int my, boolean hovered) {
		switch (mode()) {
			case ICON_ROWS -> renderIconRow(g, ax, ay, mx, my, hovered);
			case TILES -> renderTile(g, ax, ay, mx, my, hovered);
			case STANDARD -> renderStandard(g, ax, ay, mx, my, hovered);
		}
	}

	private void starAndGear(GuiGraphics g, int ax, int ay, int mx, int my, boolean hovered) {
		boolean starHover = Ui.inside(mx, my, ax + starX() - 2, ay + starY() - 2, 13, 13);
		if (module.isFavorite()) Ui.icon(g, Icons.STAR, ax + starX(), ay + starY(), Theme.secondary());
		else if (hovered) Ui.icon(g, Icons.STAR, ax + starX(), ay + starY(), starHover ? Theme.textDim() : Theme.textFaint());
		if (hasSettings() && (hovered || mode() != UiFormat.CardLook.ICON_ROWS)) {
			boolean gearHover = Ui.inside(mx, my, ax + gearX() - 2, ay + gearY() - 2, 13, 13);
			Ui.icon(g, Icons.GEAR, ax + gearX(), ay + gearY(), gearHover ? Theme.accent() : Theme.textFaint());
		}
	}

	private static void bold(GuiGraphics g, String s, int x, int y, int color) {
		Ui.bold(g, s, x, y, color);
	}

	private static String ellipsizeBold(String s, int maxW) {
		return Ui.ellipsizeBold(s, maxW);
	}

	private void iconTile(GuiGraphics g, int x, int y, int size, boolean on) {
		int r = Math.min(Theme.radius() + 2, size / 3);
		Ui.roundRect(g, x, y, size, size, r, Colors.withAlpha(Colors.mix(Theme.PANEL.argb(), Theme.TEXT.argb(), 0.08f), 0.85f));
		Ui.roundOutline(g, x, y, size, size, r, Colors.withAlpha(Theme.TEXT.argb(), 0.08f));
		float scale = size >= 24 ? 1.5f : 1f;
		int icon = on ? Theme.text() : Theme.textDim();
		g.pose().pushMatrix();
		g.pose().translate(x + size / 2f - 4.5f * scale, y + size / 2f - 4.5f * scale);
		g.pose().scale(scale);
		Ui.icon(g, Icons.forModule(module), 0, 0, icon);
		g.pose().popMatrix();
	}

	private void renderIconRow(GuiGraphics g, int ax, int ay, int mx, int my, boolean hovered) {
		boolean on = module.isEnabled();
		int cat = Theme.categoryColor(module.category());
		int outline = host.isHighlighted(module.id()) ? Theme.secondary() : on ? Colors.withAlpha(cat, 0.85f) : 0;
		Ui.card(g, ax, ay, w, h, hovered, outline);
		int t = Math.max(16, Math.min(26, h - 12));
		iconTile(g, ax + 7, ay + (h - t) / 2, t, on);
		int tx = ax + 7 + t + 8;
		int maxW = ax + starX() - 6 - tx;
		boolean desc = MenuSettings.SHOW_DESCRIPTIONS.on() && h >= 32;
		int ty = desc ? ay + h / 2 - 9 : ay + (h - 8) / 2;
		String name = module.name() + (module.isExperimental() ? " (Exp.)" : "");
		bold(g, ellipsizeBold(name, maxW), tx, ty, Theme.text());
		if (desc) {
			String status = module.status();
			Ui.text(g, Ui.ellipsize(status != null && hovered ? status : module.description(), maxW), tx, ay + h / 2 + 2, Theme.textDim());
		}
		starAndGear(g, ax, ay, mx, my, hovered);
		Ui.toggle(g, ax + toggleX(), ay + toggleY(), on, hovered);
	}

	private void renderTile(GuiGraphics g, int ax, int ay, int mx, int my, boolean hovered) {
		boolean on = module.isEnabled();
		int cat = Theme.categoryColor(module.category());
		int outline = host.isHighlighted(module.id()) ? Theme.secondary() : 0;
		Ui.card(g, ax, ay, w, h, hovered, outline);
		int bar = tileBar();
		int t = Math.min(30, h - bar - 26);
		iconTile(g, ax + (w - t) / 2, ay + 8, t, on);
		String name = module.name();
		int ny = ay + 8 + t + 5;
		Ui.textCentered(g, Ui.ellipsize(name, w - 8), ax + w / 2, ny, Theme.text());
		String status = module.status();
		if (ny + 11 < ay + h - bar - 7) {
			Ui.textCentered(g, Ui.ellipsize(status != null ? status : module.category().label, w - 8), ax + w / 2, ny + 11, Theme.textFaint());
		}
		// Enabled / Disabled bar along the bottom
		int r = Math.min(Theme.radius(), bar / 2);
		int by = ay + h - bar;
		boolean barHover = Ui.inside(mx, my, ax, by, w, bar);
		int fill = on ? Colors.withAlpha(cat, barHover ? 1f : 0.9f) : Colors.withAlpha(Colors.mix(Theme.PANEL.argb(), Theme.TEXT.argb(), barHover ? 0.16f : 0.08f), 0.95f);
		Ui.roundRect(g, ax, by, w, bar, r, fill);
		if (r > 0) Ui.rect(g, ax, by, w, r, fill); // square top edge
		String label = on ? "ENABLED" : "DISABLED";
		int tc = on ? Colors.contrastText(cat) : Theme.textDim();
		Ui.textCentered(g, label, ax + w / 2, by + (bar - 8) / 2 + 1, tc);
		starAndGear(g, ax, ay, mx, my, hovered);
	}

	private void renderStandard(GuiGraphics g, int ax, int ay, int mx, int my, boolean hovered) {
		boolean on = module.isEnabled();
		int cat = Theme.categoryColor(module.category());
		int outline = host.isHighlighted(module.id()) ? Theme.secondary() : on ? Colors.fade(cat, 0.55f) : 0;
		Ui.card(g, ax, ay, w, h, hovered, outline);
		if (on) Ui.rect(g, ax, ay + Math.min(4, h / 4), 2, h - Math.min(8, h / 2), cat);
		starAndGear(g, ax, ay, mx, my, hovered);
		Ui.toggle(g, ax + toggleX(), ay + toggleY(), on, hovered);

		String status = module.status();
		if (list) {
			int textX = ax + 8;
			if (look() != UiFormat.CardLook.STANDARD) {
				Ui.icon(g, Icons.forModule(module), ax + 8, ay + (h - 9) / 2, on ? Theme.accent() : Theme.textDim());
				textX += 14;
			}
			int nameW = Ui.width(module.name());
			Ui.text(g, module.name(), textX, ay + (h - 8) / 2, Theme.text());
			int textRight = ax + starX() - 6;
			int descX = textX + nameW + 8;
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
