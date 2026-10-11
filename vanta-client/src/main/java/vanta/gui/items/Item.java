package vanta.gui.items;

import net.minecraft.client.gui.GuiGraphics;

/**
 * One block of content in a VANTA menu page. Positions are relative to the page body;
 * the menu adds the body origin and scroll offset (ax, ay) when drawing and hit-testing.
 */
public abstract class Item {
	public int x;
	public int y;
	public int w;
	public int h;

	public abstract void render(GuiGraphics g, int ax, int ay, int mx, int my, boolean hovered);

	public boolean click(int ax, int ay, double mx, double my, int button) { return false; }

	public boolean drag(int ax, int ay, double mx, double my) { return false; }

	public void release() {
	}

	public boolean scroll(int ax, int ay, double mx, double my, double amount) { return false; }

	/** Text shown in the menu's footer while hovered. */
	public String hint() { return null; }

	/** Setting/module id this item represents, used to jump to search results. */
	public String key() { return null; }
}
