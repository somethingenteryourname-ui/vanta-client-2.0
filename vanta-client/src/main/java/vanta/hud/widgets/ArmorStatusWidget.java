package vanta.hud.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import vanta.config.BoolSetting;
import vanta.config.ChoiceSetting;
import vanta.config.Labeled;
import vanta.hud.HudModule;
import vanta.modules.Category;
import vanta.ui.Ui;

/** Your armor (and held items) with durability. */
public final class ArmorStatusWidget extends HudModule {
	public enum Durability implements Labeled {
		PERCENT("Percent"), REMAINING("Uses left"), BOTH("Uses / max"), NONE("Hidden");
		private final String label;
		Durability(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private final ChoiceSetting<Durability> durability = setting(new ChoiceSetting<>(key("durability"), "Durability",
			"How durability is shown.", Durability.PERCENT));
	private final BoolSetting horizontal = setting(new BoolSetting(key("horizontal"), "Horizontal",
			"Lay items out in a row instead of a column.", false));
	private final BoolSetting heldItems = setting(new BoolSetting(key("held_items"), "Show held items",
			"Also show main hand and offhand.", true));
	private final BoolSetting colorByDamage = setting(new BoolSetting(key("color_by_damage"), "Color by damage",
			"Green when healthy, red when almost broken.", true));

	private final ItemStack[] items = new ItemStack[6];
	private final String[] texts = new String[6];
	private final int[] colors = new int[6];
	private int count;
	private int textWidth;

	public ArmorStatusWidget() {
		super("armor_status", "Armor Status", "Your armor and held items with durability.", Category.PVP, false, 1000, 760);
	}

	@Override
	public void update() {
		LocalPlayer p = Minecraft.getInstance().player;
		count = 0;
		textWidth = 0;
		if (p == null) return;
		if (heldItems.on()) {
			add(p.getItemBySlot(EquipmentSlot.MAINHAND));
			add(p.getItemBySlot(EquipmentSlot.OFFHAND));
		}
		for (EquipmentSlot slot : ARMOR) add(p.getItemBySlot(slot));
	}

	private void add(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return;
		String text = "";
		int color = textColor.argb();
		if (stack.isDamageableItem() && durability.get() != Durability.NONE) {
			int max = stack.getMaxDamage();
			int left = max - stack.getDamageValue();
			float frac = max == 0 ? 1f : left / (float) max;
			text = switch (durability.get()) {
				case PERCENT -> Math.round(frac * 100) + "%";
				case REMAINING -> String.valueOf(left);
				case BOTH -> left + "/" + max;
				case NONE -> "";
			};
			if (colorByDamage.on()) color = frac > 0.5f ? 0xFF55FF55 : frac > 0.2f ? 0xFFFFFF55 : 0xFFFF5555;
		} else if (stack.getCount() > 1) {
			text = String.valueOf(stack.getCount());
		}
		items[count] = stack;
		texts[count] = text;
		colors[count] = color;
		textWidth = Math.max(textWidth, text.isEmpty() ? 0 : Ui.width(text));
		count++;
	}

	private int cellW() { return horizontal.on() ? Math.max(18, textWidth + 2) : 18 + (textWidth > 0 ? textWidth + 3 : 0); }

	@Override
	public int width() {
		int n = Math.max(1, count);
		return horizontal.on() ? n * cellW() + 4 : cellW() + 4;
	}

	@Override
	public int height() {
		int n = Math.max(1, count);
		return horizontal.on() ? 18 + (textWidth > 0 ? 10 : 0) + 4 : n * 17 + 4;
	}

	@Override
	public boolean hasContent() { return count > 0; }

	@Override
	protected void renderContent(GuiGraphics g, boolean editor) {
		if (count == 0 && editor) {
			drawText(g, "Armor", 4, 6, textColor.argb());
			return;
		}
		for (int i = 0; i < count; i++) {
			int x, y;
			if (horizontal.on()) {
				x = 2 + i * cellW() + (cellW() - 16) / 2;
				y = 2;
			} else {
				x = 2;
				y = 2 + i * 17;
			}
			g.renderItem(items[i], x, y);
			if (!texts[i].isEmpty()) {
				if (horizontal.on()) {
					int tw = Ui.width(texts[i]);
					drawText(g, texts[i], 2 + i * cellW() + (cellW() - tw) / 2, 19, colors[i]);
				} else {
					drawText(g, texts[i], x + 19, y + 4, colors[i]);
				}
			}
		}
	}
}
