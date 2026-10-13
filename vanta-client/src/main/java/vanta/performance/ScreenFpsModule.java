package vanta.performance;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import vanta.config.IntSetting;
import vanta.modules.Category;
import vanta.modules.Module;

/**
 * Per-screen FPS caps: lower FPS in menus and inventories to keep your PC cool and quiet,
 * full speed the moment you're back in the game. 260 means no cap.
 */
public final class ScreenFpsModule extends Module {
	private static ScreenFpsModule instance;

	public final IntSetting inventories = setting(new IntSetting(key("inventories"), "Inventories and chests",
			"FPS cap while an inventory, chest or other container is open. 260 = no cap.", 260, 30, 260, 10, " FPS"));
	public final IntSetting menus = setting(new IntSetting(key("menus"), "Pause and settings menus",
			"FPS cap in the pause menu, options and other menus while in a world. 260 = no cap.", 60, 10, 260, 10, " FPS"));
	public final IntSetting chat = setting(new IntSetting(key("chat"), "Chat open",
			"FPS cap while typing in chat. 260 = no cap.", 260, 30, 260, 10, " FPS"));
	public final IntSetting mainMenu = setting(new IntSetting(key("main_menu"), "Main menu",
			"FPS cap on the title screen and server list (Minecraft uses 60).", 60, 10, 260, 10, " FPS"));

	public ScreenFpsModule() {
		super("screen_fps", "Menu FPS Cap", "Set separate FPS caps for menus, inventories and chat, so your PC runs cooler "
				+ "when you're not playing. Back to full speed in game.", Category.PERFORMANCE, false);
		instance = this;
	}

	/** Classifies a screen: 0 = none, 1 = container, 2 = chat, 3 = other menu in a world, 4 = main menu. */
	public static int kind(Screen screen, boolean inWorld) {
		if (screen == null) return 0;
		if (!inWorld) return 4;
		if (screen instanceof AbstractContainerScreen<?>) return 1;
		if (screen instanceof ChatScreen) return 2;
		return 3;
	}

	public static int apply(int limit, Minecraft mc) {
		ScreenFpsModule m = instance;
		if (m == null || !m.isEnabled() || mc == null) return limit;
		int cap = switch (kind(mc.screen, mc.level != null)) {
			case 1 -> m.inventories.value();
			case 2 -> m.chat.value();
			case 3 -> m.menus.value();
			case 4 -> m.mainMenu.value();
			default -> 260;
		};
		return cap >= 260 ? limit : Math.min(limit, cap);
	}

	@Override
	public String status() { return "Menus " + label(menus.value()) + ", inventories " + label(inventories.value()); }

	private static String label(int v) { return v >= 260 ? "no cap" : v + " FPS"; }
}
