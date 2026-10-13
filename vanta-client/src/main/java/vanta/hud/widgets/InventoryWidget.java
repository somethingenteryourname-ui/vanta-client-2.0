package vanta.hud.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import vanta.config.BoolSetting;
import vanta.hud.HudModule;
import vanta.modules.Category;
import vanta.ui.Colors;
import vanta.ui.Ui;

/** Inventory Viewer: your main inventory (and optionally the hotbar) on screen. */
public final class InventoryWidget extends HudModule {
	private final BoolSetting hotbar = setting(new BoolSetting(key("hotbar"), "Include hotbar", "Show the hotbar row too.", false));
	private final BoolSetting slots = setting(new BoolSetting(key("slots"), "Slot backgrounds", "Draw a box behind every slot.", true));
	private final BoolSetting hideEmpty = setting(new BoolSetting(key("hide_empty"), "Hide when empty",
			"Don't draw the widget when your inventory is empty.", false));
	private final ItemStack[] stacks = new ItemStack[36];
	private boolean any;

	public InventoryWidget() {
		super("inventory_viewer", "Inventory Viewer", "Your inventory contents on screen, so you can see what you have without opening it.",
				Category.HUD, false, 1000, 1000);
	}

	private int rows() { return hotbar.on() ? 4 : 3; }

	@Override public int width() { return 9 * 18 + 4; }
	@Override public int height() { return rows() * 18 + 4; }

	@Override
	public void update() {
		LocalPlayer p = Minecraft.getInstance().player;
		any = false;
		for (int i = 0; i < 36; i++) {
			stacks[i] = p == null ? ItemStack.EMPTY : p.getInventory().getItem(i);
			if (stacks[i] != null && !stacks[i].isEmpty() && (i >= 9 || hotbar.on())) any = true;
		}
	}

	@Override
	public boolean hasContent() { return any || !hideEmpty.on(); }

	@Override
	protected void renderContent(GuiGraphics g, boolean editor) {
		int rows = rows();
		for (int r = 0; r < rows; r++) {
			for (int c = 0; c < 9; c++) {
				int idx = r < 3 ? 9 + r * 9 + c : c; // rows 1-3 are slots 9..35, then the hotbar
				int x = 2 + c * 18, y = 2 + r * 18 + (r == 3 ? 0 : 0);
				if (slots.on()) Ui.rect(g, x, y, 17, 17, Colors.withAlpha(0xFF000000, 0.28f));
				ItemStack s = stacks[idx];
				if (s != null && !s.isEmpty()) {
					g.renderItem(s, x, y);
					g.renderItemDecorations(Ui.font(), s, x, y);
				}
			}
		}
	}
}
