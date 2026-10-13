package vanta.profiles;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import vanta.config.ConfigManager;

/** Starting points created on first run: Max FPS, Crystal PvP, Mace PvP, Recording, Balanced. */
final class BuiltInProfiles {
	enum Builtin {
		MAX_FPS("Max FPS"), CRYSTAL("Crystal PvP"), MACE("Mace PvP"), RECORDING("Recording"), BALANCED("Balanced");
		final String label;
		Builtin(String label) { this.label = label; }
	}

	private BuiltInProfiles() {
	}

	/** Current settings with the profile's tweaks on top. */
	static JsonObject build(Builtin b) {
		JsonObject s = ConfigManager.get().snapshotAll();
		switch (b) {
			case MAX_FPS -> {
				put(s, "module.particles.enabled", true);
				put(s, "module.particles.density", 50);
				put(s, "module.particles.hide_explosions", true);
				put(s, "module.entity_culling.enabled", true);
				put(s, "module.entity_culling.items", 32);
				put(s, "module.entity_culling.block_entities", 48);
				put(s, "module.background_fps.enabled", true);
				put(s, "theme.reduced_motion", true);
				put(s, "theme.shadows", false);
				put(s, "theme.accent_mode", "SOLID");
			}
			case CRYSTAL -> {
				put(s, "module.particles.enabled", true);
				put(s, "module.particles.hide_explosions", true);
				put(s, "module.particles.density", 75);
				put(s, "module.screen_effects.enabled", true);
				put(s, "module.totem.size", 40);
				put(s, "module.screen_effects.fire", "LOW");
				put(s, "module.crosshair.enabled", true);
				crosshair(s, vanta.crosshair.CrosshairModule.Preset.CRYSTAL);
				put(s, "module.combat_counter.enabled", true);
				put(s, "module.armor_status.enabled", true);
				put(s, "module.cps.enabled", true);
				put(s, "module.keystrokes.enabled", true);
			}
			case MACE -> {
				put(s, "module.particles.enabled", true);
				put(s, "module.particles.hide_crits", true);
				put(s, "module.screen_effects.enabled", true);
				put(s, "module.crosshair.enabled", true);
				crosshair(s, vanta.crosshair.CrosshairModule.Preset.MACE);
				put(s, "module.armor_status.enabled", true);
				put(s, "module.combo.enabled", true);
				put(s, "module.reach.enabled", true);
			}
			case RECORDING -> {
				put(s, "module.fps.enabled", false);
				put(s, "module.keystrokes.enabled", false);
				put(s, "module.cps.enabled", false);
				put(s, "theme.show_menu_fps", false);
				put(s, "theme.open_animation", "FADE");
			}
			case BALANCED -> {
				put(s, "module.particles.density", 100);
				put(s, "module.particles.hide_explosions", false);
			}
		}
		return s;
	}

	private static void crosshair(JsonObject s, vanta.crosshair.CrosshairModule.Preset preset) {
		vanta.crosshair.CrosshairModule c = (vanta.crosshair.CrosshairModule) vanta.modules.ModuleRegistry.byId("crosshair");
		if (c == null) return;
		for (java.util.Map.Entry<String, Object> e : c.presetValues(preset).entrySet()) {
			Object v = e.getValue();
			if (vanta.config.SettingRegistry.byId(e.getKey()) instanceof vanta.config.ColorSetting && v instanceof Integer i) {
				v = vanta.config.ColorSetting.toHex(i);
			} else if (v instanceof Enum<?> en) {
				v = en.name();
			}
			put(s, e.getKey(), v);
		}
	}

	private static void put(JsonObject o, String id, Object v) {
		if (v instanceof Boolean bo) o.add(id, new JsonPrimitive(bo));
		else if (v instanceof Number n) o.add(id, new JsonPrimitive(n));
		else o.add(id, new JsonPrimitive(String.valueOf(v)));
	}
}
