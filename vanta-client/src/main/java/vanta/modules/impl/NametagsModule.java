package vanta.modules.impl;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

import vanta.combat.TotemTracker;
import vanta.config.BoolSetting;
import vanta.config.ChoiceSetting;
import vanta.config.IntSetting;
import vanta.config.Labeled;
import vanta.modules.Category;
import vanta.modules.Module;

/**
 * Nametag tweaks: background opacity and size, your own nametag in third person, and
 * health / totem pops after player names. Nametags appear exactly where and when vanilla
 * shows them (team visibility, sneaking and invisibility rules are untouched).
 */
public final class NametagsModule extends Module {
	public enum HealthStyle implements Labeled {
		NUMBER("Number (18.5 ❤)"), HALF_HEARTS("Hearts (9.2 ❤)"), PERCENT("Percent");
		private final String label;
		HealthStyle(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	private static NametagsModule instance;

	public final BoolSetting own = setting(new BoolSetting(key("own"), "Show your own nametag",
			"See your name above your head in third person.", true));
	public final BoolSetting health = setting(new BoolSetting(key("health"), "Show health",
			"Health after player names (including absorption). Some servers send fake health values.", true));
	public final ChoiceSetting<HealthStyle> healthStyle = setting(new ChoiceSetting<>(key("health_style"), "Health format",
			"How health is written.", HealthStyle.NUMBER));
	public final BoolSetting customBackground = setting(new BoolSetting(key("custom_background"), "Custom background",
			"Use the opacity below instead of Minecraft's text background setting.", true));
	public final IntSetting opacity = setting(new IntSetting(key("opacity"), "Background opacity",
			"How dark the box behind nametags is. 0 removes it.", 25, 0, 100, 5, "%"));
	public final IntSetting scale = setting(new IntSetting(key("scale"), "Size", "Nametag text size.", 100, 50, 200, 5, "%"));

	public NametagsModule() {
		super("nametags", "Nametags", "Nametag background and size, your own nametag, and health and totem pops after names.",
				Category.VISUALS, false);
		instance = this;
	}

	public static boolean active() { return instance != null && instance.isEnabled(); }

	public static boolean showOwn() {
		return active() && instance.own.on() && !Minecraft.getInstance().options.getCameraType().isFirstPerson();
	}

	/** Background opacity override (0..1), or a negative value to keep vanilla's. */
	public static float backgroundOpacity() {
		return active() && instance.customBackground.on() ? instance.opacity.value() / 100f : -1f;
	}

	public static float scaleFactor() {
		return active() ? instance.scale.value() / 100f : 1f;
	}

	/** Adds health and pops to a player's nametag. */
	public static Component decorate(Player p, Component name) {
		if (!active()) return name;
		boolean hp = instance.health.on();
		int pops = TotemPopsModule.onNametags() ? TotemTracker.pops(p.getUUID()) : 0;
		if (!hp && pops == 0) return name;
		MutableComponent out = name.copy();
		if (hp) {
			float health = p.getHealth() + p.getAbsorptionAmount();
			float max = Math.max(1f, p.getMaxHealth());
			String text = switch (instance.healthStyle.get()) {
				case NUMBER -> format(health) + " ❤";
				case HALF_HEARTS -> format(health / 2f) + " ❤";
				case PERCENT -> Math.round(Math.min(health, max) / max * 100) + "%";
			};
			out.append(Component.literal(" " + text).withColor(healthColor(p.getHealth() / max, p.getAbsorptionAmount() > 0)));
		}
		if (pops > 0) out.append(Component.literal(" -" + pops).withColor(0xFFAA00));
		return out;
	}

	private static String format(float v) {
		return v == Math.floor(v) ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.1f", v);
	}

	private static int healthColor(float fraction, boolean absorption) {
		if (absorption) return 0xFFD54F;
		if (fraction > 0.66f) return 0x55FF55;
		if (fraction > 0.33f) return 0xFFFF55;
		return 0xFF5555;
	}
}
