package vanta.hud.widgets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import vanta.branding.Theme;
import vanta.config.BoolSetting;
import vanta.hud.HudModule;
import vanta.modules.Category;
import vanta.ui.Colors;
import vanta.ui.Ui;

/**
 * Items on cooldown (ender pearls, chorus fruit, shields after an axe hit, wind charges,
 * goat horns...) with a bar and seconds left. Only reads what Minecraft already tracks.
 */
public final class ItemCooldownWidget extends HudModule {
	private record Shown(ItemStack stack, float percent, String text) {
	}

	private static final Map<Identifier, long[]> TIMES = new HashMap<>(); // group -> {startMillis, durationMillis}

	private final BoolSetting horizontal = setting(new BoolSetting(key("horizontal"), "Horizontal",
			"Lay items out in a row instead of a column.", false));
	private final BoolSetting seconds = setting(new BoolSetting(key("seconds"), "Seconds left",
			"Show remaining time next to each item.", true));
	private final BoolSetting bar = setting(new BoolSetting(key("bar"), "Progress bar",
			"Show a bar that empties as the cooldown ends.", true));

	private final List<Shown> shown = new ArrayList<>();

	public ItemCooldownWidget() {
		super("item_cooldowns", "Item Cooldowns", "Ender pearl, shield, wind charge and other item cooldowns with time left.",
				Category.PVP, false, 520, 820);
	}

	public static void started(Identifier group, int ticks) {
		TIMES.put(group, new long[] {System.currentTimeMillis(), ticks * 50L});
	}

	public static void ended(Identifier group) {
		TIMES.remove(group);
	}

	@Override
	public void update() {
		shown.clear();
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) return;
		ItemCooldowns cooldowns = p.getCooldowns();
		float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
		List<Identifier> seen = new ArrayList<>(4);
		Inventory inv = p.getInventory();
		for (int slot = 0; slot <= Inventory.SLOT_OFFHAND; slot++) {
			if (slot >= Inventory.INVENTORY_SIZE && slot != Inventory.SLOT_OFFHAND) continue;
			ItemStack stack = inv.getItem(slot);
			if (stack.isEmpty()) continue;
			float pct = cooldowns.getCooldownPercent(stack, partial);
			if (pct <= 0f) continue;
			Identifier group = cooldowns.getCooldownGroup(stack);
			if (seen.contains(group)) continue;
			seen.add(group);
			shown.add(new Shown(stack, pct, timeLeft(group, pct)));
			if (shown.size() >= 8) break;
		}
		long now = System.currentTimeMillis();
		TIMES.values().removeIf(t -> now > t[0] + t[1] + 1000);
	}

	private static String timeLeft(Identifier group, float pct) {
		long[] t = TIMES.get(group);
		double secs = t != null ? Math.max(0, (t[0] + t[1] - System.currentTimeMillis()) / 1000.0) : -1;
		if (secs < 0) return Math.round(pct * 100) + "%";
		return secs < 10 ? String.format(Locale.ROOT, "%.1fs", secs) : (int) Math.ceil(secs) + "s";
	}

	@Override
	public boolean hasContent() { return !shown.isEmpty(); }

	private int itemW() { return 18 + (seconds.on() ? 26 : 0); }

	@Override
	public int width() {
		int n = Math.max(1, shown.size());
		return horizontal.on() ? pad() * 2 + n * itemW() + (n - 1) * 2 : pad() * 2 + itemW();
	}

	@Override
	public int height() {
		int n = Math.max(1, shown.size());
		int rowH = 16 + (bar.on() ? 3 : 0);
		return horizontal.on() ? pad() * 2 + rowH : pad() * 2 + n * rowH + (n - 1) * 2;
	}

	@Override
	protected void renderContent(GuiGraphics g, boolean editor) {
		List<Shown> list = shown;
		if (list.isEmpty() && editor) {
			list = List.of(new Shown(new ItemStack(Items.ENDER_PEARL), 0.6f, "0.6s"));
		}
		int rowH = 16 + (bar.on() ? 3 : 0);
		int x = pad(), y = pad();
		for (Shown s : list) {
			g.renderItem(s.stack(), x, y);
			if (seconds.on()) drawText(g, s.text(), x + 19, y + 4, valueColor());
			if (bar.on()) {
				int bw = itemW() - 2;
				Ui.rect(g, x, y + 17, bw, 2, Colors.withAlpha(0xFF000000, 0.5f));
				Ui.rect(g, x, y + 17, Math.max(1, Math.round(bw * s.percent())), 2, Theme.accent());
			}
			if (horizontal.on()) x += itemW() + 2; else y += rowH + 2;
		}
	}
}
