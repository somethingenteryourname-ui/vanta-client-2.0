package vanta.modules.impl;

import net.minecraft.client.Minecraft;

import vanta.config.BoolSetting;
import vanta.config.TextSetting;
import vanta.modules.Category;
import vanta.modules.Module;

/**
 * Nick Hider for streaming and recording: your username is replaced everywhere it is
 * drawn on your screen (chat, tab list, nametag, scoreboard), and your skin can be swapped
 * for a default one. Only changes what you see; the server still knows who you are.
 */
public final class NickHiderModule extends Module {
	private static NickHiderModule instance;

	public final TextSetting fakeName = setting(new TextSetting(key("fake_name"), "Shown name",
			"What your name is replaced with on your screen.", "You", 16));
	public final BoolSetting hideSkin = setting(new BoolSetting(key("hide_skin"), "Hide skin",
			"Show a default skin for yourself instead of your real one.", true));

	private static String realName;

	public NickHiderModule() {
		super("nick_hider", "Nick Hider", "Hides your name and skin on your own screen, for streaming and recording.",
				Category.INTERFACE, false);
		instance = this;
	}

	public static boolean active() { return instance != null && instance.isEnabled(); }

	public static boolean hidesSkin() { return active() && instance.hideSkin.on(); }

	/** Replaces your username in a piece of text. Called for every drawn string, so it stays cheap. */
	public static String filter(String text) {
		if (!active() || text == null || text.isEmpty()) return text;
		if (realName == null) {
			Minecraft mc = Minecraft.getInstance();
			if (mc == null || mc.getUser() == null) return text;
			realName = mc.getUser().getName();
		}
		if (realName.length() < 3 || !text.contains(realName)) return text;
		String fake = instance.fakeName.get();
		return text.replace(realName, fake.isEmpty() ? "You" : fake);
	}
}
