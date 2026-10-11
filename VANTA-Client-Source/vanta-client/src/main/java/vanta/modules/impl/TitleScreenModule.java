package vanta.modules.impl;

import vanta.compat.CompatDetector;
import vanta.config.BoolSetting;
import vanta.config.ChoiceSetting;
import vanta.config.IntSetting;
import vanta.config.Labeled;
import vanta.config.TextSetting;
import vanta.modules.Category;
import vanta.modules.Module;

/** Replaces the vanilla title screen with the VANTA home screen. */
public final class TitleScreenModule extends Module {
	public enum Background implements Labeled {
		PANORAMA("Vanilla panorama"), DARK("Minimal dark"), GRADIENT("Accent gradient"), ANIMATED("Subtle motion");
		private final String label;
		Background(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	public enum Side implements Labeled {
		LEFT("Left"), RIGHT("Right");
		private final String label;
		Side(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	public enum SidebarStyle implements Labeled {
		FULL("Icons and labels"), ICONS("Icons only"), HIDDEN("Hidden");
		private final String label;
		SidebarStyle(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	public enum LogoPosition implements Labeled {
		SIDEBAR("Sidebar"), HEADER("Main panel"), BOTH("Both");
		private final String label;
		LogoPosition(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	public final ChoiceSetting<Background> background = setting(new ChoiceSetting<>(key("background"), "Background",
			"What is drawn behind the home screen. Subtle motion is cheap but turns static with Reduced motion.", Background.PANORAMA));
	public final IntSetting backgroundDim = setting(new IntSetting(key("background_dim"), "Background dimming",
			"Darkens the background so text stays readable.", 30, 0, 90, 1, "%"));
	public final ChoiceSetting<Side> sidebarSide = setting(new ChoiceSetting<>(key("sidebar_side"), "Sidebar side",
			"Which side the navigation sidebar is on.", Side.LEFT));
	public final ChoiceSetting<SidebarStyle> sidebarStyle = setting(new ChoiceSetting<>(key("sidebar_style"), "Sidebar style",
			"Show the sidebar with labels, icons only, or hide it (the main panel keeps all buttons).", SidebarStyle.FULL));
	public final ChoiceSetting<LogoPosition> logoPosition = setting(new ChoiceSetting<>(key("logo_position"), "Logo position",
			"Where the client name is shown on the home screen.", LogoPosition.BOTH));
	public final IntSetting buttonHeight = setting(new IntSetting(key("button_height"), "Button size",
			"Height of home screen buttons.", 20, 14, 28, 1, "px"));
	public final IntSetting buttonSpacing = setting(new IntSetting(key("button_spacing"), "Button spacing",
			"Gap between home screen buttons.", 4, 0, 12, 1, "px"));
	public final TextSetting welcomeText = setting(new TextSetting(key("welcome_text"), "Welcome text",
			"Greeting on the home screen. {name} is replaced with your Minecraft username.", "Welcome back, {name}", 48));
	public final BoolSetting showInfo = setting(new BoolSetting(key("show_info"), "Show info cards",
			"Version, FPS and client status cards on the home screen.", true));

	public TitleScreenModule() {
		super("title_screen", "Home Screen", "Replaces Minecraft's title screen with the VANTA home screen. Vanilla menu stays one click away.",
				Category.INTERFACE, true);
	}

	@Override
	public String status() {
		String other = CompatDetector.titleScreenMods();
		if (other != null) return "Possible conflict: " + other;
		return isEnabled() ? "Active" : "Using vanilla title screen";
	}
}
