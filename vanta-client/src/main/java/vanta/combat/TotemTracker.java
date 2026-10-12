package vanta.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import vanta.modules.impl.TotemPopsModule;

/**
 * Counts totem pops per player from the totem animation event every client already
 * receives (the same one that plays the sound and particles). Counts reset when that
 * player dies or you change worlds.
 */
public final class TotemTracker {
	private static final Map<UUID, Integer> POPS = new HashMap<>();
	private static ClientLevel lastLevel;

	private TotemTracker() {
	}

	/** Entity event 35 = totem used; 3 = death. */
	public static void onEntityEvent(Entity entity, byte event) {
		if (!(entity instanceof Player p)) return;
		if (event == 35) {
			int n = POPS.merge(p.getUUID(), 1, Integer::sum);
			TotemPopsModule.onPop(p, n);
		} else if (event == 3) {
			Integer n = POPS.remove(p.getUUID());
			if (n != null && n > 0) TotemPopsModule.onDeathAfterPops(p, n);
		}
	}

	/** Forget every count (shown as nothing until someone pops again). */
	public static void resetAll() {
		POPS.clear();
	}

	public static boolean reset(UUID id) {
		return POPS.remove(id) != null;
	}

	public static int pops(UUID id) {
		return POPS.getOrDefault(id, 0);
	}

	public static void tick(Minecraft mc) {
		if (mc.level != lastLevel) {
			lastLevel = mc.level;
			POPS.clear();
		}
		if (mc.player != null && !mc.player.isAlive()) POPS.remove(mc.player.getUUID());
	}

	/** Local chat line, only visible to you. */
	public static void say(Minecraft mc, String text) {
		if (mc.player != null) mc.player.displayClientMessage(Component.literal("[VANTA] " + text).withStyle(ChatFormatting.GOLD), false);
	}
}
