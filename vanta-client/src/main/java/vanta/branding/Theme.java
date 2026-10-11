package vanta.branding;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import vanta.config.BoolSetting;
import vanta.config.ChoiceSetting;
import vanta.config.ColorSetting;
import vanta.config.IntSetting;
import vanta.config.Labeled;
import vanta.config.Setting;
import vanta.config.SettingRegistry;
import vanta.config.TextSetting;
import vanta.gui.MenuPage;
import vanta.modules.Category;
import vanta.ui.Colors;

/**
 * The single source of truth for how every VANTA screen looks.
 * The title screen, the RIGHT SHIFT menu, the HUD editor, dialogs and the HUD all read
 * from here, so a change in one place shows up everywhere immediately.
 */
public final class Theme {
	// ------------------------------------------------------------------ option enums

	public enum Layout implements Labeled {
		GRID("Grid"), LIST("List");
		private final String label;
		Layout(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	public enum Density implements Labeled {
		COMPACT("Compact", 0.8f), COMFORTABLE("Comfortable", 1f), SPACIOUS("Spacious", 1.25f);
		private final String label;
		public final float factor;
		Density(String label, float factor) { this.label = label; this.factor = factor; }
		@Override public String label() { return label; }
	}

	public enum MenuPosition implements Labeled {
		CENTER("Center"), LEFT("Left"), RIGHT("Right");
		private final String label;
		MenuPosition(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	public enum AccentMode implements Labeled {
		SOLID("Solid"), GRADIENT("Gradient"), CHROMA("Chroma (rainbow)");
		private final String label;
		AccentMode(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	public enum CardStyle implements Labeled {
		FILLED("Filled"), OUTLINED("Outlined"), MINIMAL("Minimal"), GLASS("Glass");
		private final String label;
		CardStyle(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	public enum Indicator implements Labeled {
		BAR("Side bar"), PILL("Pill"), UNDERLINE("Underline"), GLOW("Soft glow"), NONE("None");
		private final String label;
		Indicator(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	public enum ToggleStyle implements Labeled {
		SWITCH("Switch"), CHECKBOX("Checkbox"), PILL("On/Off pill"), DOT("Dot");
		private final String label;
		ToggleStyle(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	public enum OpenAnimation implements Labeled {
		SLIDE_UP("Slide up"), FADE("Fade"), SCALE("Zoom"), SLIDE_SIDE("Slide in"), NONE("None");
		private final String label;
		OpenAnimation(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	public enum Backdrop implements Labeled {
		PLAIN("Plain dim"), GRID("Grid"), DOTS("Dots"), GRADIENT("Accent gradient"), VIGNETTE("Vignette");
		private final String label;
		Backdrop(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	public enum Side implements Labeled {
		LEFT("Left"), RIGHT("Right");
		private final String label;
		Side(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	public enum Palette {
		ELECTRIC("Electric", 0xFF2F8CFF, 0xFF27D3E6),
		VIOLET("Violet", 0xFF8B5CF6, 0xFFC084FC),
		CRIMSON("Crimson", 0xFFEF4444, 0xFFF97373),
		EMBER("Ember", 0xFFF97316, 0xFFFBBF24),
		MINT("Mint", 0xFF10B981, 0xFF5EEAD4),
		SAKURA("Sakura", 0xFFEC4899, 0xFFF9A8D4),
		GOLD("Gold", 0xFFEAB308, 0xFFFDE68A),
		ICE("Ice", 0xFF38BDF8, 0xFFE0F2FE),
		MONO("Mono", 0xFFD4D7DD, 0xFF8A909C);

		public final String label;
		public final int accent;
		public final int secondary;

		Palette(String label, int accent, int secondary) {
			this.label = label;
			this.accent = accent;
			this.secondary = secondary;
		}
	}

	/** Whole-look presets: palette plus surface colors and shape. */
	public enum Style {
		VANTA("VANTA", 0xFF0A0C10, 0xFF14171D, 0xFF252A33, 0xFFE7EAF0, 3, CardStyle.FILLED, Palette.ELECTRIC),
		MIDNIGHT("Midnight", 0xFF05060A, 0xFF0E1018, 0xFF1C2030, 0xFFDDE3F0, 4, CardStyle.GLASS, Palette.VIOLET),
		CARBON("Carbon", 0xFF111111, 0xFF1A1A1A, 0xFF2C2C2C, 0xFFEDEDED, 0, CardStyle.OUTLINED, Palette.CRIMSON),
		SLATE("Slate", 0xFF0F172A, 0xFF1E293B, 0xFF334155, 0xFFF1F5F9, 5, CardStyle.FILLED, Palette.ICE),
		FOREST("Forest", 0xFF07110D, 0xFF0F1D17, 0xFF1F3A2E, 0xFFE3F2EA, 3, CardStyle.MINIMAL, Palette.MINT),
		LIGHT("Light", 0xFFE9ECF1, 0xFFFFFFFF, 0xFFCBD2DC, 0xFF151922, 4, CardStyle.FILLED, Palette.ELECTRIC);

		public final String label;
		final int background, panel, border, text, radius;
		final CardStyle card;
		final Palette palette;

		public int background() { return background; }
		public int panel() { return panel; }
		public Palette palette() { return palette; }

		Style(String label, int background, int panel, int border, int text, int radius, CardStyle card, Palette palette) {
			this.label = label;
			this.background = background;
			this.panel = panel;
			this.border = border;
			this.text = text;
			this.radius = radius;
			this.card = card;
			this.palette = palette;
		}
	}

	// ------------------------------------------------------------------ settings

	private static final List<Setting<?>> SETTINGS = new ArrayList<>();
	private static final MenuPage OWNER = MenuPage.THEME;

	// Identity & colors
	public static final TextSetting CLIENT_NAME = add(new TextSetting("theme.client_name", "Client name",
			"The name shown in the logo and headers.", "VANTA", 16));
	public static final ChoiceSetting<AccentMode> ACCENT_MODE = add(new ChoiceSetting<>("theme.accent_mode", "Accent mode",
			"Solid color, a gradient from accent to secondary, or a cycling rainbow.", AccentMode.SOLID));
	public static final ColorSetting ACCENT = add(new ColorSetting("theme.accent", "Accent color",
			"Main highlight color for buttons, toggles and selection.", Palette.ELECTRIC.accent, false));
	public static final ColorSetting SECONDARY = add(new ColorSetting("theme.secondary", "Secondary color",
			"Second accent used in gradients, highlights and the logo.", Palette.ELECTRIC.secondary, false));
	public static final IntSetting CHROMA_SPEED = add(new IntSetting("theme.chroma_speed", "Chroma speed",
			"How fast the rainbow accent cycles.", 6, 1, 30, 1, ""));
	public static final IntSetting CHROMA_SATURATION = add(new IntSetting("theme.chroma_saturation", "Chroma saturation",
			"How colorful the rainbow accent is.", 70, 10, 100, 5, "%"));
	public static final ColorSetting BACKGROUND = add(new ColorSetting("theme.background", "Background color",
			"Base color behind menus.", 0xFF0A0C10, false));
	public static final ColorSetting PANEL = add(new ColorSetting("theme.panel", "Panel color",
			"Color of cards and panels.", 0xFF14171D, false));
	public static final ColorSetting BORDER = add(new ColorSetting("theme.border", "Border color",
			"Thin outline around panels and cards.", 0xFF252A33, false));
	public static final ColorSetting TEXT = add(new ColorSetting("theme.text", "Text color",
			"Primary text color.", 0xFFE7EAF0, false));
	public static final ColorSetting TEXT_SECONDARY = add(new ColorSetting("theme.text_secondary", "Secondary text color",
			"Color of descriptions and hints.", 0xFF8C93A1, false));
	public static final BoolSetting CATEGORY_COLORS = add(new BoolSetting("theme.category_colors", "Color by category",
			"Give each module category its own accent on cards and chips.", false));

	// Surfaces
	public static final ChoiceSetting<CardStyle> CARD_STYLE = add(new ChoiceSetting<>("theme.card_style", "Card style",
			"How cards, rows and buttons are drawn.", CardStyle.FILLED));
	public static final IntSetting PANEL_OPACITY = add(new IntSetting("theme.panel_opacity", "Panel opacity",
			"How solid panels look. Lower values let the background show through.", 92, 20, 100, 1, "%"));
	public static final IntSetting CORNER_RADIUS = add(new IntSetting("theme.corner_radius", "Corner radius",
			"Roundness of panels, cards and buttons.", 3, 0, 6, 1, "px"));
	public static final IntSetting BORDER_WIDTH = add(new IntSetting("theme.border_width", "Border width",
			"Thickness of outlines. 0 removes them.", 1, 0, 2, 1, "px"));
	public static final BoolSetting SHADOWS = add(new BoolSetting("theme.shadows", "Shadows",
			"Soft drop shadow under the menu panel and cards.", true));
	public static final IntSetting SHADOW_STRENGTH = add(new IntSetting("theme.shadow_strength", "Shadow strength",
			"How dark the shadows are.", 30, 5, 80, 5, "%"));
	public static final IntSetting HOVER_STRENGTH = add(new IntSetting("theme.hover_strength", "Hover highlight",
			"How much cards and buttons light up under the mouse.", 10, 0, 30, 1, "%"));
	public static final BoolSetting TEXT_SHADOW = add(new BoolSetting("theme.text_shadow", "Text shadow",
			"Draw a shadow under menu text.", false));
	public static final BoolSetting UPPERCASE_HEADINGS = add(new BoolSetting("theme.uppercase_headings", "Uppercase headings",
			"Show section headings in capitals.", true));
	public static final ChoiceSetting<ToggleStyle> TOGGLE_STYLE = add(new ChoiceSetting<>("theme.toggle_style", "Toggle style",
			"How on/off switches look.", ToggleStyle.SWITCH));

	// Layout
	public static final IntSetting UI_SCALE = add(new IntSetting("theme.ui_scale", "Interface scale",
			"Size of VANTA menus relative to Minecraft's GUI scale. Shrinks automatically if the window is too small.", 100, 70, 150, 5, "%"));
	public static final IntSetting FONT_SCALE = add(new IntSetting("theme.font_scale", "Heading scale",
			"Size of large headings and the logo.", 100, 75, 150, 5, "%"));
	public static final ChoiceSetting<Layout> LAYOUT = add(new ChoiceSetting<>("theme.layout", "Module layout",
			"Show modules as a grid of cards or as a list.", Layout.GRID));
	public static final ChoiceSetting<Density> DENSITY = add(new ChoiceSetting<>("theme.density", "Density",
			"Spacing between cards and rows.", Density.COMFORTABLE));
	public static final IntSetting CARD_WIDTH = add(new IntSetting("theme.card_width", "Card width",
			"Minimum width of module cards; wider cards mean fewer columns.", 150, 110, 260, 10, "px"));
	public static final ChoiceSetting<MenuPosition> MENU_POSITION = add(new ChoiceSetting<>("theme.menu_position", "Menu position",
			"Where the RIGHT SHIFT menu sits on screen.", MenuPosition.CENTER));
	public static final IntSetting MENU_WIDTH = add(new IntSetting("theme.menu_width", "Menu width",
			"Width of the RIGHT SHIFT menu as a share of the screen.", 84, 55, 100, 1, "%"));
	public static final IntSetting MENU_HEIGHT = add(new IntSetting("theme.menu_height", "Menu height",
			"Height of the RIGHT SHIFT menu as a share of the screen.", 86, 60, 100, 1, "%"));
	public static final ChoiceSetting<Side> SIDEBAR_SIDE = add(new ChoiceSetting<>("theme.sidebar_side", "Sidebar side",
			"Which side of the menu the navigation sits on.", Side.LEFT));
	public static final IntSetting SIDEBAR_WIDTH = add(new IntSetting("theme.sidebar_width", "Sidebar width",
			"Width of the navigation sidebar.", 96, 30, 150, 2, "px"));
	public static final IntSetting NAV_ITEM_HEIGHT = add(new IntSetting("theme.nav_item_height", "Sidebar item height",
			"Height of each sidebar entry. Shrinks automatically if the menu is short.", 18, 14, 28, 1, "px"));
	public static final BoolSetting SIDEBAR_ICONS = add(new BoolSetting("theme.sidebar_icons", "Sidebar icons",
			"Show icons next to sidebar labels.", true));
	public static final BoolSetting SIDEBAR_LABELS = add(new BoolSetting("theme.sidebar_labels", "Sidebar labels",
			"Show page names in the sidebar. Turn off (with a narrow sidebar) for an icon rail.", true));
	public static final ChoiceSetting<Indicator> INDICATOR = add(new ChoiceSetting<>("theme.indicator", "Selection indicator",
			"How the current page is marked in the sidebar.", Indicator.BAR));
	public static final IntSetting BACKGROUND_DIM = add(new IntSetting("theme.background_dim", "Background dimming",
			"How much the game is darkened behind the in-game menu.", 55, 0, 95, 1, "%"));
	public static final ChoiceSetting<Backdrop> BACKDROP = add(new ChoiceSetting<>("theme.backdrop", "Backdrop",
			"Pattern drawn behind the in-game menu.", Backdrop.PLAIN));
	public static final BoolSetting SHOW_SUBTITLES = add(new BoolSetting("theme.show_subtitles", "Page subtitles",
			"Show a one-line description under each page title.", true));
	public static final BoolSetting SHOW_FOOTER = add(new BoolSetting("theme.show_footer", "Footer hints",
			"Explain whatever is under the mouse at the bottom of the menu.", true));

	// Motion
	public static final ChoiceSetting<OpenAnimation> OPEN_ANIMATION = add(new ChoiceSetting<>("theme.open_animation", "Open animation",
			"How menus appear.", OpenAnimation.SLIDE_UP));
	public static final IntSetting ANIMATION_SPEED = add(new IntSetting("theme.animation_speed", "Animation speed",
			"Speed of menu transitions. Higher is faster.", 100, 50, 300, 10, "%"));
	public static final IntSetting ANIMATION_INTENSITY = add(new IntSetting("theme.animation_intensity", "Animation intensity",
			"How far elements move when menus open.", 100, 0, 200, 10, "%"));
	public static final BoolSetting REDUCED_MOTION = add(new BoolSetting("theme.reduced_motion", "Reduced motion",
			"Turns off all menu animations, chroma cycling and moving backgrounds.", false));

	// Extras
	public static final BoolSetting SHOW_LOGO = add(new BoolSetting("theme.show_logo", "Show logo",
			"Show the client name in sidebars.", true));
	public static final BoolSetting SHOW_MENU_FPS = add(new BoolSetting("theme.show_menu_fps", "FPS in menus",
			"Show a small live FPS readout in the corner of VANTA menus.", true));

	/** Per-category accent colors (used when "Color by category" is on). */
	public static final Map<Category, ColorSetting> CATEGORY_COLOR = new EnumMap<>(Category.class);

	static {
		int[] defaults = {0xFF22C55E, 0xFFA855F7, 0xFFEF4444, 0xFF38BDF8, 0xFFF59E0B, 0xFF14B8A6, 0xFFF472B6};
		for (Category c : Category.values()) {
			CATEGORY_COLOR.put(c, add(new ColorSetting("theme.category." + c.name().toLowerCase(java.util.Locale.ROOT),
					c.label + " color", "Accent for " + c.label + " modules when Color by category is on.",
					defaults[c.ordinal() % defaults.length], false)));
		}
	}

	private Theme() {
	}

	private static <S extends Setting<?>> S add(S setting) {
		SETTINGS.add(setting);
		return SettingRegistry.register(setting, OWNER);
	}

	/** Forces class loading so the settings are registered before the config loads. */
	public static void init() {
	}

	public static List<Setting<?>> settings() { return SETTINGS; }

	public static void applyPalette(Palette palette) {
		ACCENT.set(palette.accent);
		SECONDARY.set(palette.secondary);
	}

	public static void applyStyle(Style style) {
		BACKGROUND.set(style.background);
		PANEL.set(style.panel);
		BORDER.set(style.border);
		TEXT.set(style.text);
		TEXT_SECONDARY.set(Colors.mix(style.text, style.panel, 0.42f));
		CORNER_RADIUS.set(style.radius);
		CARD_STYLE.set(style.card);
		applyPalette(style.palette);
	}

	public static void restoreDefaults() {
		for (Setting<?> s : SETTINGS) s.reset();
	}

	// ------------------------------------------------------------------ derived colors

	/** Accent color, animated when Chroma mode is on. */
	public static int accent() {
		if (ACCENT_MODE.get() == AccentMode.CHROMA) return chroma(0f);
		return ACCENT.argb();
	}

	/** Secondary accent; in Chroma mode it is offset around the color wheel. */
	public static int secondary() {
		if (ACCENT_MODE.get() == AccentMode.CHROMA) return chroma(0.18f);
		return SECONDARY.argb();
	}

	/** Rainbow color at a hue offset (0..1). Static when reduced motion is on. */
	public static int chroma(float offset) {
		double t = REDUCED_MOTION.on() ? 0 : (System.currentTimeMillis() % 3_600_000L) / 1000.0;
		float hue = (float) ((t * CHROMA_SPEED.value() / 60.0 + offset) % 1.0);
		return Colors.hsb(hue, CHROMA_SATURATION.value() / 100f, 1f);
	}

	public static boolean gradientAccent() { return ACCENT_MODE.get() != AccentMode.SOLID; }

	public static int categoryColor(Category c) {
		if (CATEGORY_COLORS.on()) return CATEGORY_COLOR.get(c).argb();
		return accent();
	}

	public static int text() { return TEXT.argb(); }
	public static int textDim() { return TEXT_SECONDARY.argb(); }
	public static int textFaint() { return Colors.mix(TEXT_SECONDARY.argb(), PANEL.argb(), 0.38f); }
	public static int border() { return BORDER.argb(); }
	public static int background() { return BACKGROUND.argb(); }

	private static float opacity() { return PANEL_OPACITY.value() / 100f; }

	public static int panel() {
		float o = CARD_STYLE.get() == CardStyle.GLASS ? opacity() * 0.78f : opacity();
		return Colors.withAlpha(PANEL.argb(), o);
	}

	public static int panelRaised() {
		float o = switch (CARD_STYLE.get()) {
			case GLASS -> opacity() * 0.55f;
			case OUTLINED -> opacity() * 0.30f;
			case MINIMAL -> opacity() * 0.45f;
			case FILLED -> opacity();
		};
		return Colors.withAlpha(Colors.mix(PANEL.argb(), TEXT.argb(), 0.05f), o);
	}

	public static int panelHover() {
		float lift = 0.05f + HOVER_STRENGTH.value() / 200f;
		float o = CARD_STYLE.get() == CardStyle.GLASS ? opacity() * 0.7f : opacity();
		return Colors.withAlpha(Colors.mix(PANEL.argb(), TEXT.argb(), lift), Math.max(o, 0.5f));
	}

	public static int sidebar() {
		float o = CARD_STYLE.get() == CardStyle.GLASS ? opacity() * 0.7f : Math.min(1f, opacity() + 0.04f);
		return Colors.withAlpha(Colors.mix(BACKGROUND.argb(), PANEL.argb(), 0.5f), o);
	}

	public static int shadowColor() {
		return SHADOWS.on() ? Colors.withAlpha(0xFF000000, SHADOW_STRENGTH.value() / 100f) : 0;
	}

	public static int radius() { return CORNER_RADIUS.value(); }
	public static int borderWidth() { return BORDER_WIDTH.value(); }
	public static float uiScale() { return UI_SCALE.value() / 100f; }
	public static float headingScale() { return FONT_SCALE.value() / 100f; }
	public static float density() { return DENSITY.get().factor; }
	public static boolean reducedMotion() { return REDUCED_MOTION.on() || ANIMATION_INTENSITY.value() == 0; }

	/** Duration multiplier for animations; 0 when motion is reduced. */
	public static float animationTimeScale() {
		return reducedMotion() || OPEN_ANIMATION.get() == OpenAnimation.NONE ? 0f : 100f / ANIMATION_SPEED.value();
	}

	public static float animationDistance() {
		return reducedMotion() ? 0f : ANIMATION_INTENSITY.value() / 100f;
	}

	public static String clientName() {
		String name = CLIENT_NAME.get();
		return name.isEmpty() ? "VANTA" : name;
	}

	public static String heading(String s) {
		return UPPERCASE_HEADINGS.on() ? s.toUpperCase(java.util.Locale.ROOT) : s;
	}
}
