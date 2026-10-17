package vanta.modules.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import vanta.config.IntSetting;
import vanta.modules.Category;
import vanta.modules.Module;

/** Adds a summed contents list (e.g. "64x Totem of Undying") to shulker box tooltips. */
public final class ShulkerPreviewModule extends Module {
	private static ShulkerPreviewModule instance;

	public final IntSetting maxLines = setting(new IntSetting(key("max_lines"), "Max lines",
			"How many different items to list.", 8, 3, 27, 1, ""));

	public ShulkerPreviewModule() {
		super("shulker_preview", "Shulker Preview", "Shows a full, totaled list of what's inside shulker boxes in their tooltip.",
				Category.QOL, true);
		instance = this;
	}

	public static void appendTooltip(ItemStack stack, List<Component> lines) {
		ShulkerPreviewModule m = instance;
		if (m == null || !m.isEnabled()) return;
		ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
		if (contents == null) return;
		Map<String, Integer> totals = new LinkedHashMap<>();
		int total = 0;
		for (ItemStack item : contents.nonEmptyItems()) {
			totals.merge(item.getHoverName().getString(), item.getCount(), Integer::sum);
			total += item.getCount();
		}
		if (totals.isEmpty()) return;
		lines.add(Component.literal("Contents (" + total + " items):").withStyle(ChatFormatting.AQUA));
		int shown = 0;
		for (Map.Entry<String, Integer> e : totals.entrySet()) {
			if (shown++ >= m.maxLines.value()) {
				lines.add(Component.literal("  ...and " + (totals.size() - m.maxLines.value()) + " more").withStyle(ChatFormatting.DARK_GRAY));
				break;
			}
			lines.add(Component.literal("  " + e.getValue() + "x " + e.getKey()).withStyle(ChatFormatting.GRAY));
		}
	}
}
