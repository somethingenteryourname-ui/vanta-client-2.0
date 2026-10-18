package vanta.modules.impl;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import vanta.config.BoolSetting;
import vanta.config.IntSetting;
import vanta.modules.Category;
import vanta.modules.Module;
import vanta.ui.Ui;

/**
 * When a server disconnects you, the disconnect screen gets a Reconnect button and
 * (optionally) a countdown that reconnects automatically. It never retries after a
 * ban-looking message and stops after a few tries in a row.
 */
public final class AutoReconnectModule extends Module {
	private static AutoReconnectModule instance;

	public final BoolSetting auto = setting(new BoolSetting(key("auto"), "Reconnect automatically",
			"Count down and reconnect by itself. Off = just show a Reconnect button.", true));
	public final IntSetting delay = setting(new IntSetting(key("delay"), "Delay", "Seconds before reconnecting.", 5, 2, 60, 1, "s"));
	public final IntSetting attempts = setting(new IntSetting(key("attempts"), "Max attempts", "Stop after this many tries in a row.", 3, 1, 10, 1, ""));

	private ServerData lastServer;
	private int tries;
	private long reconnectAt;
	private boolean blocked;

	public AutoReconnectModule() {
		super("auto_reconnect", "Auto Reconnect", "Reconnect button and countdown when a server disconnects you. Never retries after a ban.",
				Category.QOL, true);
		instance = this;
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (instance == null || !instance.isEnabled() || !(screen instanceof DisconnectedScreen ds)) return;
			instance.onDisconnectScreen(mc, ds, w, h);
		});
	}

	@Override
	public void tick(Minecraft mc) {
		ServerData current = mc.getCurrentServer();
		if (current != null && mc.level != null && mc.player != null && mc.player.tickCount > 100) {
			tries = 0; // stayed connected for 5 seconds: a fresh disconnect starts counting again
			lastServer = current;
		} else if (current != null && mc.level != null) {
			lastServer = current;
		}
		if (reconnectAt > 0 && System.currentTimeMillis() >= reconnectAt) {
			reconnectAt = 0;
			if (mc.screen instanceof DisconnectedScreen) reconnect(mc);
		}
	}

	private void onDisconnectScreen(Minecraft mc, DisconnectedScreen screen, int w, int h) {
		if (lastServer == null) return;
		String reason = "";
		try {
			reason = ((vanta.mixin.feature.DisconnectedScreenAccessor) screen).vanta$details().reason().getString().toLowerCase(Locale.ROOT);
		} catch (RuntimeException | LinkageError ignored) {
		}
		blocked = reason.contains("banned") || reason.contains("ban ") || reason.contains("blacklist") || reason.contains("not whitelisted");
		Screens.getButtons(screen).add(Button.builder(Component.literal("Reconnect to " + lastServer.ip), b -> reconnect(mc))
				.bounds(w / 2 - 100, h - 30, 200, 20).build());
		reconnectAt = auto.on() && !blocked && tries < attempts.value() ? System.currentTimeMillis() + delay.value() * 1000L : 0;
		ScreenEvents.afterRender(screen).register((s, g, mx, my, d) -> {
			String text;
			if (blocked) text = "Auto reconnect is off for this disconnect (looks like a ban).";
			else if (reconnectAt > 0) text = "Reconnecting in " + Math.max(0, (reconnectAt - System.currentTimeMillis() + 999) / 1000)
					+ "s (try " + (tries + 1) + "/" + attempts.value() + ")";
			else if (auto.on() && tries >= attempts.value()) text = "Stopped after " + tries + " tries.";
			else return;
			Ui.draw(g, text, w / 2 - Ui.width(text) / 2, h - 44, 0xFFBFBFBF, true);
		});
	}

	private void reconnect(Minecraft mc) {
		if (lastServer == null) return;
		tries++;
		reconnectAt = 0;
		Screen parent = new JoinMultiplayerScreen(new TitleScreen());
		ConnectScreen.startConnecting(parent, mc, ServerAddress.parseString(lastServer.ip), lastServer, false, null);
	}

	@Override
	protected void onDisable() { reconnectAt = 0; }
}
