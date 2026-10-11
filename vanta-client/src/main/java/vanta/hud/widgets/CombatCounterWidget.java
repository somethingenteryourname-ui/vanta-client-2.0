package vanta.hud.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import vanta.config.BoolSetting;
import vanta.hud.HudModule;
import vanta.modules.Category;
import vanta.ui.Ui;

/**
 * Counts the crystal/anchor PvP supplies in your inventory: totems, crystals, obsidian,
 * anchors, glowstone, pearls, golden apples and XP bottles. Display only.
 */
public final class CombatCounterWidget extends HudModule {
	private final BoolSetting horizontal = setting(new BoolSetting(key("horizontal"), "Horizontal", "Show as a row instead of a column.", false));
	private final BoolSetting hideEmpty = setting(new BoolSetting(key("hide_empty"), "Hide zero counts", "Skip items you have none of.", true));
	private final BoolSetting[] show;
	private final Item[] items = {Items.TOTEM_OF_UNDYING, Items.END_CRYSTAL, Items.OBSIDIAN, Items.RESPAWN_ANCHOR,
			Items.GLOWSTONE, Items.ENDER_PEARL, Items.GOLDEN_APPLE, Items.EXPERIENCE_BOTTLE};
	private final String[] names = {"Totems", "Crystals", "Obsidian", "Anchors", "Glowstone", "Pearls", "Golden apples", "XP bottles"};
	private final int[] counts = new int[items.length];
	private final ItemStack[] icons = new ItemStack[items.length];
	private final String[] texts = new String[items.length];
	private final int[] visible = new int[items.length];
	private int visibleCount;
	private int textWidth;
	private long nextRefresh;

	public CombatCounterWidget() {
		super("combat_counter", "Combat Counter", "Totems, crystals, obsidian, anchors, glowstone, pearls, gapples and XP in your inventory.",
				Category.PVP, false, 0, 640);
		show = new BoolSetting[items.length];
		for (int i = 0; i < items.length; i++) {
			show[i] = setting(new BoolSetting(key("show_" + i), "Show " + names[i].toLowerCase(java.util.Locale.ROOT), "Count " + names[i].toLowerCase(java.util.Locale.ROOT) + ".", i < 5));
		}
	}

	@Override
	public void update() {
		long now = System.currentTimeMillis();
		if (now < nextRefresh) return;
		nextRefresh = now + 200;
		java.util.Arrays.fill(counts, 0);
		LocalPlayer p = Minecraft.getInstance().player;
		if (p != null) {
			Inventory inv = p.getInventory();
			for (int s = 0; s < inv.getContainerSize(); s++) {
				ItemStack st = inv.getItem(s);
				if (st.isEmpty()) continue;
				for (int i = 0; i < items.length; i++) {
					if (st.is(items[i])) {
						counts[i] += st.getCount();
						break;
					}
				}
			}
		}
		visibleCount = 0;
		textWidth = 0;
		for (int i = 0; i < items.length; i++) {
			if (!show[i].on() || (hideEmpty.on() && counts[i] == 0)) continue;
			if (icons[i] == null) icons[i] = new ItemStack(items[i]);
			texts[i] = String.valueOf(counts[i]);
			textWidth = Math.max(textWidth, Ui.width(texts[i]));
			visible[visibleCount++] = i;
		}
	}

	@Override
	public boolean hasContent() { return visibleCount > 0; }

	private int cellW() { return horizontal.on() ? Math.max(18, textWidth + 2) : 18 + textWidth + 3; }

	@Override
	public int width() {
		int n = Math.max(1, visibleCount);
		return horizontal.on() ? n * cellW() + 4 : cellW() + 4;
	}

	@Override
	public int height() {
		int n = Math.max(1, visibleCount);
		return horizontal.on() ? 30 : n * 17 + 4;
	}

	@Override
	protected void renderContent(GuiGraphics g, boolean editor) {
		if (visibleCount == 0) {
			if (editor) drawText(g, "Combat items", 4, 6, textColor.argb());
			return;
		}
		for (int k = 0; k < visibleCount; k++) {
			int i = visible[k];
			int color = counts[i] == 0 ? 0xFFFF5555 : valueColor();
			if (horizontal.on()) {
				int x = 2 + k * cellW();
				g.renderItem(icons[i], x + (cellW() - 16) / 2, 2);
				int tw = Ui.width(texts[i]);
				drawText(g, texts[i], x + (cellW() - tw) / 2, 20, color);
			} else {
				int y = 2 + k * 17;
				g.renderItem(icons[i], 2, y);
				drawText(g, texts[i], 21, y + 4, color);
			}
		}
	}
}
