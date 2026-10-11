package vanta.modules.impl;

import vanta.config.BoolSetting;
import vanta.config.ChoiceSetting;
import vanta.config.IntSetting;
import vanta.config.Labeled;
import vanta.modules.Category;
import vanta.modules.Module;

/** Cleaner screen: low fire, smaller totem animation, no pumpkin/powder snow/portal overlays. */
public final class ScreenEffectsModule extends Module {
	public enum Fire implements Labeled {
		NORMAL("Normal"), LOW("Low"), HIDDEN("Hidden");
		private final String label;
		Fire(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	private static ScreenEffectsModule instance;

	public final ChoiceSetting<Fire> fire = setting(new ChoiceSetting<>(key("fire"), "Fire overlay",
			"Lower or hide the flames covering your screen when you're burning.", Fire.LOW));
	public final IntSetting fireHeight = setting(new IntSetting(key("fire_height"), "Low fire height",
			"How far the flames are pushed down in Low mode.", 30, 5, 60, 5, "%"));
	public final IntSetting totemScale = setting(new IntSetting(key("totem_scale"), "Totem animation size",
			"Size of the totem pop animation. 0 hides it.", 60, 0, 100, 5, "%"));
	public final BoolSetting hidePumpkin = setting(new BoolSetting(key("hide_pumpkin"), "Hide pumpkin blur",
			"Remove the overlay when wearing a carved pumpkin.", true));
	public final BoolSetting hidePowderSnow = setting(new BoolSetting(key("hide_powder_snow"), "Hide powder snow frost",
			"Remove the frozen overlay in powder snow.", false));
	public final BoolSetting hidePortal = setting(new BoolSetting(key("hide_portal"), "Hide portal swirl",
			"Remove the purple overlay while standing in a nether portal.", false));

	public ScreenEffectsModule() {
		super("screen_effects", "Screen Effects", "Low fire, smaller totem pops and no blocking overlays. Visual only.",
				Category.VISUALS, true);
		instance = this;
	}

	private static boolean on() { return instance != null && instance.isEnabled(); }

	public static boolean hideFire() { return on() && instance.fire.get() == Fire.HIDDEN; }

	/** How far to push the fire overlay down (0 = vanilla). */
	public static float fireOffset() {
		return on() && instance.fire.get() == Fire.LOW ? instance.fireHeight.value() / 100f : 0f;
	}

	public static float totemScale() { return on() ? instance.totemScale.value() / 100f : 1f; }

	/** Whether a full-screen texture overlay should be skipped. */
	public static boolean hideOverlay(String texturePath) {
		if (!on()) return false;
		if (instance.hidePumpkin.on() && texturePath.contains("pumpkinblur")) return true;
		return instance.hidePowderSnow.on() && texturePath.contains("powder_snow_outline");
	}

	public static boolean hidePortal() { return on() && instance.hidePortal.on(); }
}
