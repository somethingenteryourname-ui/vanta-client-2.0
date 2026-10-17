package vanta.modules.impl;

import vanta.config.IntSetting;
import vanta.modules.Category;
import vanta.modules.Module;

/** Brightens dark areas like night vision does (client-side lighting only). */
public final class FullbrightModule extends Module {
	private static FullbrightModule instance;

	public final IntSetting strength = setting(new IntSetting(key("strength"), "Strength",
			"How bright dark areas become. 100% matches night vision.", 100, 10, 100, 5, "%"));

	public FullbrightModule() {
		super("fullbright", "Fullbright", "See in the dark without night vision. Lighting only; works in caves and at night.",
				Category.VISUALS, false);
		instance = this;
	}

	/** Applied to the night-vision factor of the lightmap. */
	public static float apply(float nightVision) {
		FullbrightModule f = instance;
		if (f == null || !f.isEnabled()) return nightVision;
		return Math.max(nightVision, f.strength.value() / 100f);
	}

	@Override
	public String status() { return strength.value() + "%"; }
}
