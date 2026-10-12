package vanta.modules.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import vanta.config.BoolSetting;
import vanta.config.IntSetting;
import vanta.modules.Category;
import vanta.modules.Module;
import vanta.ui.Colors;
import vanta.ui.Ui;
import vanta.waypoints.WorldProjection;

/**
 * Floating damage numbers: when a player you can see loses (or regains) health, a small
 * "-3.5" rises from them. Uses the health every client already receives, and only for
 * players in your line of sight at that moment.
 */
public final class DamageIndicatorsModule extends Module {
	private record Popup(double x, double y, double z, String text, int color, long born) {
	}

	private static final long LIFE_MS = 1100;
	private static DamageIndicatorsModule instance;
	private static final float[] OUT = new float[2];

	public final BoolSetting mobs = setting(new BoolSetting(key("mobs"), "Mobs too",
			"Also show numbers for mobs, not just players.", false));
	public final BoolSetting healing = setting(new BoolSetting(key("healing"), "Show healing",
			"Green numbers when health goes up.", false));
	public final BoolSetting self = setting(new BoolSetting(key("self"), "Your own damage",
			"Show numbers on yourself in third person.", false));
	public final IntSetting size = setting(new IntSetting(key("size"), "Size", "Text size.", 100, 50, 200, 10, "%"));

	private final Map<Integer, Float> lastHealth = new HashMap<>();
	private final List<Popup> popups = new ArrayList<>();
	private Object lastLevel;

	public DamageIndicatorsModule() {
		super("damage_indicators", "Damage Indicators", "Numbers float up from players you can see when they take damage.",
				Category.VISUALS, false);
		instance = this;
	}

	@Override
	protected void onDisable() {
		lastHealth.clear();
		popups.clear();
	}

	@Override
	public void tick(Minecraft mc) {
		LocalPlayer me = mc.player;
		if (mc.level == null || me == null) {
			lastHealth.clear();
			return;
		}
		if (mc.level != lastLevel) {
			lastLevel = mc.level;
			lastHealth.clear();
			popups.clear();
		}
		Map<Integer, Float> seen = new HashMap<>();
		Iterable<? extends Entity> list = mobs.on() ? mc.level.entitiesForRendering() : mc.level.players();
		for (Entity e : list) {
			if (!(e instanceof LivingEntity living) || e.isInvisible()) continue;
			if (e.distanceToSqr(me) > 32 * 32) continue;
			float hp = living.getHealth() + living.getAbsorptionAmount();
			Float before = lastHealth.get(e.getId());
			seen.put(e.getId(), hp);
			if (before == null) continue;
			float diff = hp - before;
			if (Math.abs(diff) < 0.05f) continue;
			if (diff > 0 && !healing.on()) continue;
			if (e == me && !self.on()) continue;
			if (e != me && !me.hasLineOfSight(e)) continue;
			String text = (diff < 0 ? "-" : "+") + format(Math.abs(diff));
			int color = diff < 0 ? (e instanceof Player ? 0xFFFF5555 : 0xFFFFAA55) : 0xFF55FF55;
			double jitter = ((e.getId() * 31 + popups.size() * 17) % 10) / 20.0 - 0.25;
			popups.add(new Popup(e.getX() + jitter, e.getY() + e.getBbHeight() + 0.3, e.getZ() - jitter, text, color, System.currentTimeMillis()));
		}
		lastHealth.clear();
		lastHealth.putAll(seen);
		if (popups.size() > 40) popups.subList(0, popups.size() - 40).clear();
	}

	private static String format(float v) {
		return v >= 10 || v == Math.floor(v) ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
	}

	public static void render(GuiGraphics g, DeltaTracker tracker) {
		if (instance == null || !instance.isEnabled() || instance.popups.isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui || !WorldProjection.valid()) return;
		long now = System.currentTimeMillis();
		float scale = instance.size.value() / 100f;
		Iterator<Popup> it = instance.popups.iterator();
		while (it.hasNext()) {
			Popup p = it.next();
			float age = (now - p.born()) / (float) LIFE_MS;
			if (age >= 1f) {
				it.remove();
				continue;
			}
			if (!WorldProjection.project(p.x(), p.y() + age * 0.7, p.z(), g.guiWidth(), g.guiHeight(), OUT)) continue;
			float alpha = age < 0.7f ? 1f : 1f - (age - 0.7f) / 0.3f;
			float s = scale * (age < 0.12f ? 0.6f + age / 0.12f * 0.5f : 1.1f - Math.min(0.1f, (age - 0.12f)));
			g.pose().pushMatrix();
			g.pose().translate(OUT[0], OUT[1]);
			g.pose().scale(s);
			int w = Ui.width(p.text());
			g.drawString(Ui.font(), p.text(), -w / 2, -4, Colors.withAlpha(p.color(), alpha), true);
			g.pose().popMatrix();
		}
	}
}
