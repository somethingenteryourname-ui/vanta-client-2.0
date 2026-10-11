package vanta.gui.items;

import net.minecraft.client.gui.screens.Screen;

import vanta.gui.MenuPage;
import vanta.modules.Module;

/** What menu items can ask of the menu they live in. */
public interface MenuHost {
	void openPage(MenuPage page);
	void openModule(Module module);
	/** Rebuild page content (e.g. after a favorite changes the sort order). */
	void rebuild();
	void beginDrag(Item item);
	/** The screen to return to after a dialog closes. */
	Screen asScreen();
	boolean isHighlighted(String key);
	void focusSearch();
}
