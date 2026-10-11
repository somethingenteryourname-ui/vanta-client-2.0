package vanta.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import vanta.config.BoolSetting;
import vanta.config.Setting;
import vanta.config.SettingRegistry;

/** Settings for how the VANTA menus behave (shown on the Settings page). */
public final class MenuSettings {
	private static final List<Setting<?>> SETTINGS = new ArrayList<>();

	public static final BoolSetting PAUSE_SINGLEPLAYER = add(new BoolSetting("menu.pause_singleplayer", "Pause in singleplayer",
			"Pause the game while the VANTA menu is open in singleplayer. Multiplayer never pauses.", false));
	public static final BoolSetting CLOSE_WITH_KEY = add(new BoolSetting("menu.close_with_key", "Close with menu key",
			"Pressing the menu key again closes the menu.", true));
	public static final BoolSetting REMEMBER_PAGE = add(new BoolSetting("menu.remember_page", "Remember last page",
			"Reopen the menu on the page you last used.", true));
	public static final BoolSetting SOUNDS = add(new BoolSetting("menu.sounds", "Menu sounds",
			"Play a quiet click when using VANTA buttons and toggles.", false));
	public static final BoolSetting SHOW_DESCRIPTIONS = add(new BoolSetting("menu.show_descriptions", "Card descriptions",
			"Show short descriptions on module cards.", true));

	private MenuSettings() {
	}

	private static <S extends Setting<?>> S add(S s) {
		SETTINGS.add(s);
		return SettingRegistry.register(s, MenuPage.SETTINGS);
	}

	public static void init() {
	}

	public static List<Setting<?>> settings() { return SETTINGS; }

	public static void click() {
		if (!SOUNDS.on()) return;
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
	}
}
