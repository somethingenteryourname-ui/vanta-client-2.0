package vanta.compat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.fabricmc.loader.api.FabricLoader;

import vanta.client.VantaClient;
import vanta.config.BoolSetting;
import vanta.config.SettingRegistry;
import vanta.config.TextSetting;
import vanta.gui.MenuPage;
import vanta.modules.Module;
import vanta.modules.ModuleRegistry;

/**
 * Detects other installed mods so VANTA can coexist with them. When another mod already
 * does the same job (Zoomify, Xaero's waypoints, BetterF3...), VANTA turns its own copy
 * off once, so you don't get double zoom or two keys fighting. You can turn VANTA's
 * version back on and it stays on. Detection only reads Fabric's mod list.
 */
public final class CompatDetector {
	public record Detected(String id, String name, String note) {
	}

	public static final BoolSetting AVOID_DUPLICATES = SettingRegistry.register(new BoolSetting("compat.avoid_duplicates",
			"Avoid duplicate features", "When another mod already does something VANTA does (zoom, waypoints, F3, title screen...), "
			+ "turn VANTA's copy off the first time that mod is seen. You can turn it back on.", true), MenuPage.SETTINGS);
	public static final BoolSetting YIELD_TITLE = SettingRegistry.register(new BoolSetting("compat.yield_title_screen",
			"Let menu mods own the title screen", "If FancyMenu or another title-screen mod is installed, keep its title screen "
			+ "instead of VANTA's home screen.", true), MenuPage.SETTINGS);
	/** Mod ids already handled, so a module you turn back on isn't switched off again. */
	private static final TextSetting HANDLED = (TextSetting) SettingRegistry.register(new TextSetting("compat.handled", "Handled mods",
			"Mods VANTA already adjusted for.", "", 2000), MenuPage.SETTINGS).hidden().excludeFromSnapshots();

	private static final String[][] KNOWN = {
			{"sodium", "Sodium", "Renderer optimization. Works with VANTA; its F3 lines appear in Clean F3 under Mods."},
			{"iris", "Iris", "Shaders. VANTA menus and HUD draw on top as normal GUI."},
			{"lithium", "Lithium", "Game logic optimization. No overlap with VANTA."},
			{"ferritecore", "FerriteCore", "Memory optimization. No overlap with VANTA."},
			{"entityculling", "EntityCulling", "Skips hidden entities. Works next to VANTA's distance culling."},
			{"immediatelyfast", "ImmediatelyFast", "Speeds up GUI/HUD drawing, which VANTA's menus also benefit from."},
			{"dynamic_fps", "Dynamic FPS", "Lowers FPS when unfocused, so VANTA's Background FPS is turned off."},
			{"badoptimizations", "BadOptimizations", "Micro-optimizations. No overlap with VANTA."},
			{"modmenu", "Mod Menu", "VANTA's home screen shows a Mods button that opens Mod Menu."},
			{"zoomify", "Zoomify", "Zoom mod: VANTA's Zoom is turned off to avoid double zoom."},
			{"ok_zoomer", "Ok Zoomer", "Zoom mod: VANTA's Zoom is turned off to avoid double zoom."},
			{"logical_zoom", "Logical Zoom", "Zoom mod: VANTA's Zoom is turned off to avoid double zoom."},
			{"xaerominimap", "Xaero's Minimap", "VANTA's Minimap and Waypoints are turned off (Xaero also uses B for waypoints)."},
			{"xaerominimapfair", "Xaero's Minimap (Fair-Play)", "VANTA's Minimap and Waypoints are turned off."},
			{"journeymap", "JourneyMap", "VANTA's Minimap and Waypoints are turned off."},
			{"betterf3", "BetterF3", "VANTA's Clean F3 is turned off so BetterF3 shows."},
			{"perspectivemod", "Perspective Mod", "VANTA's Freelook is turned off (same key)."},
			{"freelook", "Freelook", "VANTA's Freelook is turned off (same key)."},
			{"chatpatches", "Chat Patches", "Also adds timestamps and stacking: turn those off on VANTA's Chat page if you see them twice."},
			{"itemphysic", "ItemPhysic", "VANTA's Item Physics is turned off."},
			{"itemphysiclite", "ItemPhysic Lite", "VANTA's Item Physics is turned off."},
			{"autoreconnect", "AutoReconnect", "VANTA's Auto Reconnect is turned off."},
			{"shulkerboxtooltip", "Shulker Box Tooltip", "VANTA's Shulker Preview is turned off."},
			{"fancymenu", "FancyMenu", "Customizes menus; VANTA keeps FancyMenu's title screen (see Settings)."},
	};

	/** Other mod id -> VANTA module ids that do the same thing. */
	private static final String[][] DUPLICATES = {
			{"zoomify", "zoom"}, {"ok_zoomer", "zoom"}, {"logical_zoom", "zoom"},
			{"xaerominimap", "minimap", "waypoints"}, {"xaerominimapfair", "minimap", "waypoints"}, {"journeymap", "minimap", "waypoints"},
			{"betterf3", "info_panel"}, {"dynamic_fps", "background_fps"},
			{"perspectivemod", "freelook"}, {"freelook", "freelook"},
			{"itemphysic", "item_physics"}, {"itemphysiclite", "item_physics"},
			{"autoreconnect", "auto_reconnect"}, {"shulkerboxtooltip", "shulker_preview"},
	};

	/** Mods known to replace or heavily modify the title screen. */
	private static final String[][] TITLE_SCREEN_MODS = {
			{"fancymenu", "FancyMenu"},
			{"custom_main_menu", "Custom Main Menu"},
			{"isxander-main-menu-credits", "Main Menu Credits"},
	};

	private static List<Detected> detected = List.of();
	private static final List<String> adjusted = new ArrayList<>();
	private static String titleScreenConflict;

	private CompatDetector() {
	}

	/** Registers the compatibility settings (call before the config is loaded). */
	public static void init() {
	}

	/** Call after settings are loaded. */
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
			VantaClient.LOG.info("[VANTA] {} also changes the title screen.{}", titleScreenConflict,
					YIELD_TITLE.on() ? " Keeping its title screen (VANTA Settings > Compatibility to change)." : "");
		}

		Set<String> handled = new LinkedHashSet<>();
		for (String s : HANDLED.get().split(",")) if (!s.isBlank()) handled.add(s.strip());
		boolean changed = false;
		for (String[] d : DUPLICATES) {
			if (!loader.isModLoaded(d[0]) || handled.contains(d[0])) continue;
			handled.add(d[0]);
			changed = true;
			if (!AVOID_DUPLICATES.on()) continue;
			for (int i = 1; i < d.length; i++) {
				Module m = ModuleRegistry.byId(d[i]);
				if (m != null && m.isEnabled()) {
					m.setEnabled(false);
					adjusted.add(m.name() + " (because of " + d[0] + ")");
					VantaClient.LOG.info("[VANTA] Turned off {} because {} does the same thing", m.name(), d[0]);
				}
			}
		}
		if (changed) HANDLED.set(String.join(",", handled));
	}

	public static List<Detected> detected() { return detected; }

	/** VANTA modules switched off this session because another mod covers them. */
	public static List<String> adjusted() { return Collections.unmodifiableList(adjusted); }

	/** Names of installed mods that may conflict with VANTA's home screen, or null. */
	public static String titleScreenMods() { return titleScreenConflict; }

	/** True when VANTA should leave the title screen to another mod. */
	public static boolean yieldTitleScreen() { return titleScreenConflict != null && YIELD_TITLE.on(); }

	public static boolean isLoaded(String id) { return FabricLoader.getInstance().isModLoaded(id); }
}
