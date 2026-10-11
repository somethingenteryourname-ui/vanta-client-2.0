package vanta.compat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.fabricmc.loader.api.FabricLoader;

import vanta.client.VantaClient;

/**
 * Detects other installed mods so VANTA can explain how it coexists with them.
 * Detection only reads Fabric's mod list; nothing is sent anywhere.
 */
public final class CompatDetector {
	public record Detected(String id, String name, String note) {
	}

	private static final String[][] KNOWN = {
			{"sodium", "Sodium", "Renderer optimization. VANTA does not touch world rendering, so they don't overlap."},
			{"lithium", "Lithium", "Game logic optimization. No overlap with VANTA."},
			{"ferritecore", "FerriteCore", "Memory optimization. No overlap with VANTA."},
			{"entityculling", "EntityCulling", "Skips hidden entities. No overlap with VANTA."},
			{"immediatelyfast", "ImmediatelyFast", "Speeds up GUI/HUD drawing, which VANTA's menus also benefit from."},
			{"dynamic_fps", "Dynamic FPS", "Lowers FPS when the game is unfocused. VANTA's FPS readouts will reflect that."},
			{"badoptimizations", "BadOptimizations", "Micro-optimizations. No overlap with VANTA."},
			{"iris", "Iris", "Shaders. VANTA menus draw on top as normal GUI."},
			{"modmenu", "Mod Menu", "VANTA's home screen shows a Mods button that opens Mod Menu."},
	};

	/** Mods known to replace or heavily modify the title screen. */
	private static final String[][] TITLE_SCREEN_MODS = {
			{"fancymenu", "FancyMenu"},
			{"custom_main_menu", "Custom Main Menu"},
			{"isxander-main-menu-credits", "Main Menu Credits"},
	};

	private static List<Detected> detected = List.of();
	private static String titleScreenConflict;

	private CompatDetector() {
	}

	public static void scan() {
		FabricLoader loader = FabricLoader.getInstance();
		List<Detected> found = new ArrayList<>();
		for (String[] k : KNOWN) {
			if (loader.isModLoaded(k[0])) found.add(new Detected(k[0], k[1], k[2]));
		}
		detected = Collections.unmodifiableList(found);

		List<String> titleMods = new ArrayList<>();
		for (String[] t : TITLE_SCREEN_MODS) if (loader.isModLoaded(t[0])) titleMods.add(t[1]);
		titleScreenConflict = titleMods.isEmpty() ? null : String.join(", ", titleMods);
		if (titleScreenConflict != null) {
			VantaClient.LOG.warn("[VANTA] {} also changes the title screen. If menus look wrong, turn off "
					+ "Home Screen in the VANTA menu (Modules > Interface).", titleScreenConflict);
		}
	}

	public static List<Detected> detected() { return detected; }

	/** Names of installed mods that may conflict with VANTA's home screen, or null. */
	public static String titleScreenMods() { return titleScreenConflict; }

	public static boolean isLoaded(String id) { return FabricLoader.getInstance().isModLoaded(id); }
}
