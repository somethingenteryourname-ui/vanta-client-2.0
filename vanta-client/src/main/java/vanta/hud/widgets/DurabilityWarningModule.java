package vanta.hud.widgets;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import vanta.branding.Theme;
import vanta.config.BoolSetting;
import vanta.config.IntSetting;
import vanta.modules.Category;
import vanta.modules.Module;
import vanta.notify.NotificationsModule;
import vanta.ui.Colors;
import vanta.ui.Ui;

/**
 * Durability Warnings: a heads-up (on screen, a sound and a notification) when your armor
 * or held item is about to break. Warns once per item at your threshold, and again when
 * it's nearly gone.
 */
public final class DurabilityWarningModule extends Module {
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
			EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static DurabilityWarningModule instance;

	public final IntSetting threshold = setting(new IntSetting(key("threshold"), "Warn at",
			"Warn when an item has this much durability left.", 10, 3, 50, 1, "%"));
	public final BoolSetting armor = setting(new BoolSetting(key("armor"), "Armor", "Warn about armor you're wearing.", true));
	public final BoolSetting held = setting(new BoolSetting(key("held"), "Held items", "Warn about tools and weapons in your hands.", true));
	public final BoolSetting overlay = setting(new BoolSetting(key("overlay"), "On-screen warning", "Show the warning in the middle of the screen.", true));
	public final BoolSetting sound = setting(new BoolSetting(key("sound"), "Sound", "Play a warning sound.", true));

	private final Map<ItemStack, Integer> warned = new WeakHashMap<>();
	private ItemStack shownStack = ItemStack.EMPTY;
	private String shownText = "";
	private long shownAt;

	public DurabilityWarningModule() {
		super("durability_warnings", "Durability Warnings", "A heads-up when your armor or held item is about to break: "
				+ "on screen, with a sound and a notification.", Category.QOL, true);
		instance = this;
	}

	/** 0 = fine, 1 = low, 2 = almost broken. */
	public static int level(int left, int max, int thresholdPercent) {
		if (max <= 0) return 0;
		float frac = left / (float) max;
		if (left <= 5 || frac <= Math.min(0.03f, thresholdPercent / 300f)) return 2;
		return frac <= thresholdPercent / 100f ? 1 : 0;
	}

	@Override
	public void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null) return;
		for (EquipmentSlot slot : SLOTS) {
			boolean isArmor = slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND;
			if (isArmor ? !armor.on() : !held.on()) continue;
			ItemStack s = p.getItemBySlot(slot);
			if (s == null || s.isEmpty() || !s.isDamageableItem()) continue;
			int max = s.getMaxDamage(), left = max - s.getDamageValue();
			int lvl = level(left, max, threshold.value());
			int before = warned.getOrDefault(s, 0);
			if (lvl > before) warn(mc, s, left, lvl);
			if (lvl != before) warned.put(s, lvl);
		}
	}

	private void warn(Minecraft mc, ItemStack s, int left, int lvl) {
		String name = s.getHoverName().getString();
		shownStack = s;
		shownText = name + (lvl == 2 ? " is about to break! " : " is getting low: ") + left + (left == 1 ? " use left" : " uses left");
		shownAt = System.currentTimeMillis();
		if (sound.on()) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS, lvl == 2 ? 0.6f : 0.9f));
		NotificationsModule.show(NotificationsModule.Kind.DURABILITY, lvl == 2 ? "About to break!" : "Low durability", name + ": " + left + " uses left");
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		DurabilityWarningModule m = instance;
		if (m == null || !m.isEnabled() || !m.overlay.on() || m.shownStack.isEmpty()) return;
		long age = System.currentTimeMillis() - m.shownAt;
		if (age > 2800) return;
		float alpha = age < 150 ? age / 150f : age > 2300 ? (2800 - age) / 500f : 1f;
		float pulse = Theme.reducedMotion() ? 1f : 0.75f + 0.25f * (float) Math.sin(age / 90.0);
		int w = Ui.width(m.shownText) + 30, x = (g.guiWidth() - w) / 2, y = g.guiHeight() / 2 - 58;
		Ui.roundRect(g, x, y, w, 20, 4, Colors.withAlpha(0xFF1A0E0E, 0.75f * alpha));
		Ui.rect(g, x, y + 3, 2, 14, Colors.withAlpha(0xFFFF5040, alpha));
		g.renderItem(m.shownStack, x + 5, y + 2);
		g.drawString(Ui.font(), m.shownText, x + 24, y + 6, Colors.withAlpha(Colors.mix(0xFFFFB020, 0xFFFF5040, pulse - 0.5f), alpha), true);
	}
}
