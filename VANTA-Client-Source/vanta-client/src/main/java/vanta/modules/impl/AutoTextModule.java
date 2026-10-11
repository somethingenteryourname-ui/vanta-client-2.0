package vanta.modules.impl;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import vanta.config.TextSetting;
import vanta.input.VantaKeys;
import vanta.modules.Category;
import vanta.modules.Module;

/**
 * Hotkeys that send a saved chat message or command (e.g. "gg" or "/spawn").
 * One message per key press, with a 1-second cooldown so it can't spam.
 */
public final class AutoTextModule extends Module {
	private final TextSetting[] messages = new TextSetting[3];
	private long lastSent;

	public AutoTextModule() {
		super("auto_text", "Auto Text", "Send a saved message or command with one key. Set the keys in Controls (VANTA Client).",
				Category.QOL, false);
		String[] defaults = {"gg", "/spawn", ""};
		for (int i = 0; i < messages.length; i++) {
			messages[i] = setting(new TextSetting(key("message_" + (i + 1)), "Message " + (i + 1),
					"Sent when you press Auto Text " + (i + 1) + ". Start with / for a command.", defaults[i], 200));
		}
	}

	@Override
	public void tick(Minecraft mc) {
		KeyMapping[] keys = VantaKeys.AUTO_TEXT;
		for (int i = 0; i < keys.length; i++) {
			while (keys[i].consumeClick()) {
				if (mc.screen == null) send(mc, messages[i].get());
			}
		}
	}

	private void send(Minecraft mc, String text) {
		ClientPacketListener connection = mc.getConnection();
		long now = System.currentTimeMillis();
		if (connection == null || text.isBlank() || now - lastSent < 1000) return;
		lastSent = now;
		if (text.startsWith("/")) connection.sendCommand(text.substring(1));
		else connection.sendChat(text);
	}

	@Override
	public String status() {
		return "Keys: " + VantaKeys.name(VantaKeys.AUTO_TEXT[0]) + ", " + VantaKeys.name(VantaKeys.AUTO_TEXT[1]) + ", " + VantaKeys.name(VantaKeys.AUTO_TEXT[2]);
	}
}
