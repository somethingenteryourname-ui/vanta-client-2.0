package vanta.gui;

import vanta.ui.Icons;

/** The pages in the RIGHT SHIFT menu sidebar. Only pages with real content are listed. */
public enum MenuPage {
	HOME("Home", "Dashboard: live performance, quick controls and client status", Icons.HOME),
	MODULES("Modules", "Turn features on and off and configure them", Icons.GRID),
	PERFORMANCE("Performance", "FPS presets, boosters and the settings that matter most", Icons.BOLT),
	VISUALS("Visuals", "Camera, overlays, held items, sky, totems and resource packs", Icons.EYE),
	COSMETICS("Cosmetics", "Capes and particle trails that other VANTA players can see", Icons.CAPE),
	HUD("HUD", "Widgets, HUD style and the drag-and-drop editor", Icons.HUD),
	OPTIMIZATIONS("Optimizations", "Client-side crystals and anchors, PvP clarity and smoothness tweaks", Icons.BOLT),
	CHAT("Chat", "Themed chat with your logo, timestamps, mentions, filters and history", Icons.CHAT),
	CROSSHAIR("Crosshair", "Design your own crosshair with a live preview", Icons.TARGET),
	FRIENDS("Friends", "Your friends list, join alerts and friend markers", Icons.FRIENDS),
	THEME("Theme", "Colors, layout, styles and motion for every VANTA screen", Icons.PALETTE),
	PROFILES("Profiles", "Save, switch and share complete setups", Icons.PROFILE),
	MODS("Mods", "Your installed Fabric mods and their settings", Icons.CUBE),
	SETTINGS("Settings", "Menu behaviour, keys, title screen and data", Icons.GEAR),
	ABOUT("About", "Versions, compatibility and known limitations", Icons.INFO);

	public final String title;
	public final String subtitle;
	public final String[] icon;

	MenuPage(String title, String subtitle, String[] icon) {
		this.title = title;
		this.subtitle = subtitle;
		this.icon = icon;
	}
}
