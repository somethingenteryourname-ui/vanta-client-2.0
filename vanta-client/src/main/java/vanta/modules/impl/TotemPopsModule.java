package vanta.modules.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import vanta.combat.TotemTracker;
import vanta.config.BoolSetting;
import vanta.modules.Category;
import vanta.modules.Module;

/** Totem pop counter: how many totems each player has used this life, in chat and on nametags. */
public final class TotemPopsModule extends Module {
	private static TotemPopsModule instance;

	public final BoolSetting chat = setting(new BoolSetting(key("chat"), "Chat messages",
			"A line in your own chat when someone pops a totem (only you see it).", true));
	public final BoolSetting self = setting(new BoolSetting(key("self"), "Count your own",
			"Also announce your own pops.", false));
	public final BoolSetting tabList = setting(new BoolSetting(key("tab_list"), "Show in tab list",
			"A -N pop count next to each player's ping in the tab list, in your theme color (yours too).", true));
	public final BoolSetting nametag = setting(new BoolSetting(key("nametag"), "Show on nametags",
			"Adds the pop count after player names (needs the Nametags module).", true));

	public TotemPopsModule() {
		super("totem_pops", "Totem Pop Counter", "Counts how many totems each player pops, from the totem animation everyone already sees.",
				Category.PVP, false);
		instance = this;
	}

	public static boolean active() { return instance != null && instance.isEnabled(); }
	public static boolean onNametags() { return active() && instance.nametag.on(); }
	public static boolean inTab() { return active() && instance.tabList.on(); }
	/** Extra tab list slot width reserved for the pop count. */
	public static int tabExtraWidth() { return inTab() ? 14 : 0; }

	public static void onPop(Player p, int count) {
		if (!active() || !instance.chat.on()) return;
		Minecraft mc = Minecraft.getInstance();
		if (p == mc.player && !instance.self.on()) return;
		String name = p == mc.player ? "You" : p.getName().getString();
		TotemTracker.say(mc, name + (p == mc.player ? " popped " : " popped ") + count + (count == 1 ? " totem" : " totems"));
	}

	public static void onDeathAfterPops(Player p, int count) {
		if (!active() || !instance.chat.on()) return;
		Minecraft mc = Minecraft.getInstance();
		if (p == mc.player) return;
		TotemTracker.say(mc, p.getName().getString() + " died after popping " + count + (count == 1 ? " totem" : " totems"));
	}
}
