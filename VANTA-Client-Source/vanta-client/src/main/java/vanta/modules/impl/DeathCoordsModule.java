package vanta.modules.impl;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import vanta.modules.Category;
import vanta.modules.Module;

/** When you die, shows where (in your own chat only, never sent to the server). */
public final class DeathCoordsModule extends Module {
	private static DeathCoordsModule instance;
	private static String lastDeath;

	public DeathCoordsModule() {
		super("death_coords", "Death Info", "Shows where you died on the death screen and in your chat (only you see it).",
				Category.QOL, true);
		instance = this;
	}

	public static void onDeathScreen(Minecraft mc) {
		if (instance == null || !instance.isEnabled()) return;
		LocalPlayer p = mc.player;
		if (p == null) return;
		String dim = p.level().dimension().identifier().getPath().replace('_', ' ');
		lastDeath = (int) Math.floor(p.getX()) + ", " + (int) Math.floor(p.getY()) + ", " + (int) Math.floor(p.getZ()) + " (" + dim + ")";
		p.displayClientMessage(Component.literal("[VANTA] You died at " + lastDeath).withStyle(ChatFormatting.GRAY), false);
	}

	/** Text for the death screen, or null. */
	public static String deathText() {
		return instance != null && instance.isEnabled() && lastDeath != null ? "Died at " + lastDeath : null;
	}
}
