package vanta.menu;

import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;

import vanta.config.BoolSetting;
import vanta.config.IntSetting;
import vanta.gui.VantaScreen;
import vanta.modules.Category;
import vanta.modules.Module;

/**
 * Applies the VANTA look to Minecraft's own menus (pause menu, world and server lists,
 * and optionally every other menu). It restyles widgets where they are drawn instead of
 * replacing the screens, so buttons added by Mod Menu or other mods keep working and
 * pick up the same style automatically.
 */
public final class MenuStyleModule extends Module {
	private static MenuStyleModule instance;

	public final BoolSetting pause = setting(new BoolSetting(key("pause"), "Pause menu",
			"Style the Esc menu.", true));
	public final BoolSetting worlds = setting(new BoolSetting(key("worlds"), "World and server lists",
			"Style Singleplayer, Multiplayer, Create World, Edit Server and Direct Connect.", true));
	public final BoolSetting others = setting(new BoolSetting(key("others"), "Other Minecraft menus",
			"Style Options, Controls, Resource Packs and every other vanilla menu.", true));
	public final BoolSetting modScreens = setting(new BoolSetting(key("mod_screens"), "Other mods' menus",
			"Also style menus added by other mods (e.g. Mod Menu's list). Turn off if one looks wrong.", false));
	public final BoolSetting pausePanel = setting(new BoolSetting(key("pause_panel"), "Pause info panel",
			"Side panel on the pause menu with server, ping, FPS, session time and VANTA shortcuts.", true));
	public final BoolSetting listLogo = setting(new BoolSetting(key("list_logo"), "Logo on menus",
			"Show the client name in the corner of styled menus.", true));
	public final BoolSetting keepBlur = setting(new BoolSetting(key("keep_blur"), "Keep vanilla blur",
			"Keep Minecraft's background blur in styled menus. Off is faster and sharper.", false));
	public final IntSetting dim = setting(new IntSetting(key("dim"), "In-game dimming",
			"How much the world is darkened behind styled menus.", 60, 0, 95, 5, "%"));

	public MenuStyleModule() {
		super("menu_style", "Menu Style", "Gives the pause menu, world/server lists and other menus the VANTA look. Works with Mod Menu.",
				Category.INTERFACE, true);
		instance = this;
	}

	public static MenuStyleModule get() { return instance; }

	/** Which styling, if any, applies to this screen. */
	public static boolean styles(Screen screen) {
		MenuStyleModule m = instance;
		if (m == null || screen == null || !m.isEnabled() || screen instanceof VantaScreen) return false;
		if (screen instanceof PauseScreen) return m.pause.on();
		String pkg = screen.getClass().getPackageName();
		if (pkg.startsWith("net.minecraft.client.gui.screens.worldselection") || pkg.startsWith("net.minecraft.client.gui.screens.multiplayer")) {
			return m.worlds.on();
		}
		if (pkg.startsWith("net.minecraft.")) {
			if (!m.others.on() || pkg.contains(".inventory") || pkg.contains(".recipebook")) return false;
			String n = screen.getClass().getSimpleName();
			return !(n.equals("ChatScreen") || n.equals("InBedChatScreen") || n.equals("DeathScreen") || n.equals("TitleScreen"));
		}
		return m.modScreens.on();
	}
}
