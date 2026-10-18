package vanta.branding;

import vanta.config.Labeled;
import vanta.ui.Colors;

/**
 * Interface formats: complete looks for the VANTA menu. Each one picks a layout (where the
 * navigation, search and content go) and a full set of colors and shapes. Picking a format
 * applies all of it at once; you can still fine-tune every setting on the Theme page after.
 */
public enum UiFormat implements Labeled {
	VANTA("VANTA", "The classic: sidebar on the left, cards on the right.", Shell.SIDEBAR,
			spec(0xFF0A0C10, 0xFF14171D, 0xFF252A33, 0xFFE7EAF0, 0xFF2F8CFF, 0xFF27D3E6)),
	LIQUID_GLASS("Liquid Glass", "See-through frosted glass, big rounded corners, icon rows and filter chips.", Shell.LIQUID,
			spec(0xFF0B0C14, 0xFF1C1E2C, 0xFF3A3D52, 0xFFF2F3F8, 0xFF8B6CFF, 0xFFB79CFF).card(Theme.CardStyle.GLASS).opacity(74)
					.radius(10).look(CardLook.ICON_ROWS).blur().dim(30).lower().noIcons().font(vanta.ui.UiFont.POPPINS)),
	FROSTED("Frosted", "Bright frosted glass with dark text.", Shell.LIQUID,
			spec(0xFFDDE3EE, 0xFFF7F9FC, 0xFFC4CCDA, 0xFF141821, 0xFF2F6BFF, 0xFF6CA0FF).card(Theme.CardStyle.GLASS).opacity(78)
					.radius(10).look(CardLook.ICON_ROWS).blur().dim(10).lower().noIcons().font(vanta.ui.UiFont.INTER)),
	AQUA("Aqua Glass", "Cool teal glass with soft glowing edges.", Shell.LIQUID,
			spec(0xFF04131A, 0xFF0C2530, 0xFF1F4B5A, 0xFFE6FBFF, 0xFF22D3EE, 0xFF5EEAD4).card(Theme.CardStyle.GLASS).opacity(70)
					.radius(12).look(CardLook.ICON_ROWS).blur().dim(30).lower().noIcons().font(vanta.ui.UiFont.OUTFIT)),
	SHOWCASE("Showcase", "Tabs across the top and big module tiles.", Shell.TABS,
			spec(0xFF0E0F12, 0xFF1A1C21, 0xFF2B2E36, 0xFFF2F2F4, 0xFF4ADE80, 0xFF86EFAC).radius(6).look(CardLook.TILES).lower().font(vanta.ui.UiFont.MONTSERRAT)),
	RAIL("Rail", "A slim icon rail on the left and plenty of room for content.", Shell.RAIL,
			spec(0xFF06070C, 0xFF10121B, 0xFF1F2333, 0xFFDDE3F0, 0xFF8B5CF6, 0xFFC084FC).card(Theme.CardStyle.GLASS).opacity(88)
					.radius(5).indicator(Theme.Indicator.GLOW).font(vanta.ui.UiFont.INTER)),
	DOCK("Dock", "Pages in a dock at the bottom, like a desktop.", Shell.DOCK,
			spec(0xFF120A06, 0xFF20140D, 0xFF3A2618, 0xFFFFF4EA, 0xFFF97316, 0xFFFBBF24).card(Theme.CardStyle.GLASS).opacity(84)
					.radius(8).look(CardLook.TILES).blur().dim(35).lower().font(vanta.ui.UiFont.NUNITO)),
	CLICK_GUI("Click GUI", "Draggable category panels with module lists. Right-click a module for its settings.", Shell.CLICKGUI,
			spec(0xFF0C0C0E, 0xFF161619, 0xFF2A2A30, 0xFFEDEDED, 0xFFEF4444, 0xFFF97373).radius(2).card(Theme.CardStyle.OUTLINED).font(vanta.ui.UiFont.RAJDHANI)),
	NEON("Neon", "Black with glowing pink and cyan outlines.", Shell.SIDEBAR,
			spec(0xFF040208, 0xFF0C0714, 0xFF3B1442, 0xFFFFE9FB, 0xFFFF2BD6, 0xFF00F0FF).card(Theme.CardStyle.OUTLINED).radius(4)
					.indicator(Theme.Indicator.GLOW).toggle(Theme.ToggleStyle.DOT).accentMode(Theme.AccentMode.GRADIENT).shadow(70).font(vanta.ui.UiFont.ORBITRON)),
	TERMINAL("Terminal", "Green on black, square corners, like an old computer.", Shell.SIDEBAR,
			spec(0xFF000000, 0xFF050A06, 0xFF0F3A1A, 0xFF8CFFB0, 0xFF00FF66, 0xFF7CFFB2).card(Theme.CardStyle.OUTLINED).radius(0)
					.toggle(Theme.ToggleStyle.CHECKBOX).indicator(Theme.Indicator.UNDERLINE).backdrop(Theme.Backdrop.GRID).noShadow().layout(Theme.Layout.LIST).font(vanta.ui.UiFont.JETBRAINS_MONO)),
	PIXEL("Retro Pixel", "Chunky square borders and On/Off pills.", Shell.TABS,
			spec(0xFF15120C, 0xFF231E14, 0xFF4A3F28, 0xFFFFF6DC, 0xFFEAB308, 0xFFFDE68A).radius(0).borderWidth(2)
					.toggle(Theme.ToggleStyle.PILL).look(CardLook.TILES)),
	MINIMAL("Minimal", "Quiet and flat: a simple list with no shadows.", Shell.TABS,
			spec(0xFF0D0D0F, 0xFF151518, 0xFF26262B, 0xFFE8E8EA, 0xFFD4D7DD, 0xFF8A909C).card(Theme.CardStyle.MINIMAL).radius(4)
					.noShadow().layout(Theme.Layout.LIST).lower().font(vanta.ui.UiFont.INTER)),
	PAPER("Paper", "A light, clean look with icon rows.", Shell.SIDEBAR,
			spec(0xFFE9ECF1, 0xFFFFFFFF, 0xFFCBD2DC, 0xFF151922, 0xFF2F8CFF, 0xFF27D3E6).radius(6).look(CardLook.ICON_ROWS)
					.indicator(Theme.Indicator.PILL).lower().font(vanta.ui.UiFont.SPACE_GROTESK)),
	SUNSET("Sunset", "Pink-to-orange gradient glass with a dock.", Shell.DOCK,
			spec(0xFF160812, 0xFF261020, 0xFF4A1E3A, 0xFFFFEAF5, 0xFFEC4899, 0xFFFB923C).card(Theme.CardStyle.GLASS).opacity(80)
					.radius(10).accentMode(Theme.AccentMode.GRADIENT).look(CardLook.ICON_ROWS).blur().dim(30).lower().font(vanta.ui.UiFont.POPPINS)),
	CHROMA("Chroma", "A rainbow accent that slowly cycles, on a slim rail.", Shell.RAIL,
			spec(0xFF08080C, 0xFF12121A, 0xFF24243A, 0xFFF0F0FA, 0xFF8B5CF6, 0xFF38BDF8).radius(6)
					.accentMode(Theme.AccentMode.CHROMA).indicator(Theme.Indicator.PILL).look(CardLook.ICON_ROWS).font(vanta.ui.UiFont.OUTFIT));

	/** Where the navigation, search and content go. */
	public enum Shell {
		/** Labeled sidebar on the left or right (VANTA's original layout). */
		SIDEBAR,
		/** Logo, page list and buttons in a sidebar; search, filter chips and close button in a top bar. */
		LIQUID,
		/** No sidebar: a row of tabs under the header. */
		TABS,
		/** A slim column of icons. */
		RAIL,
		/** Icons in a dock along the bottom. */
		DOCK,
		/** Floating, draggable category panels. */
		CLICKGUI
	}

	/** How module cards are drawn. */
	public enum CardLook implements Labeled {
		STANDARD("Standard"), ICON_ROWS("Icon rows"), TILES("Tiles");
		private final String label;
		CardLook(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	/** Every value a format sets. */
	public static final class Spec {
		final int background, panel, border, text, accent, secondary;
		Theme.CardStyle card = Theme.CardStyle.FILLED;
		int opacity = 92, radius = 3, borderWidth = 1, shadow = 30, dim = 55;
		Theme.ToggleStyle toggle = Theme.ToggleStyle.SWITCH;
		Theme.Indicator indicator = Theme.Indicator.BAR;
		Theme.AccentMode accentMode = Theme.AccentMode.SOLID;
		Theme.Backdrop backdrop = Theme.Backdrop.PLAIN;
		Theme.Layout layout = Theme.Layout.GRID;
		CardLook look = CardLook.STANDARD;
		boolean blur, uppercase = true, shadows = true, sidebarIcons = true;
		vanta.ui.UiFont font = vanta.ui.UiFont.MINECRAFT;

		Spec(int background, int panel, int border, int text, int accent, int secondary) {
			this.background = background;
			this.panel = panel;
			this.border = border;
			this.text = text;
			this.accent = accent;
			this.secondary = secondary;
		}

		Spec card(Theme.CardStyle v) { card = v; return this; }
		Spec opacity(int v) { opacity = v; return this; }
		Spec radius(int v) { radius = v; return this; }
		Spec borderWidth(int v) { borderWidth = v; return this; }
		Spec shadow(int v) { shadow = v; return this; }
		Spec noShadow() { shadows = false; return this; }
		Spec dim(int v) { dim = v; return this; }
		Spec toggle(Theme.ToggleStyle v) { toggle = v; return this; }
		Spec indicator(Theme.Indicator v) { indicator = v; return this; }
		Spec accentMode(Theme.AccentMode v) { accentMode = v; return this; }
		Spec backdrop(Theme.Backdrop v) { backdrop = v; return this; }
		Spec layout(Theme.Layout v) { layout = v; return this; }
		Spec look(CardLook v) { look = v; return this; }
		Spec blur() { blur = true; return this; }
		Spec lower() { uppercase = false; return this; }
		Spec noIcons() { sidebarIcons = false; return this; }
		Spec font(vanta.ui.UiFont v) { font = v; return this; }
		public vanta.ui.UiFont font() { return font; }

		public int background() { return background; }
		public int panel() { return panel; }
		public int border() { return border; }
		public int text() { return text; }
		public int accent() { return accent; }
		public int secondary() { return secondary; }
		public int radius() { return radius; }
		public CardLook look() { return look; }
		public Theme.Layout layout() { return layout; }
	}

	private static Spec spec(int background, int panel, int border, int text, int accent, int secondary) {
		return new Spec(background, panel, border, text, accent, secondary);
	}

	private final String label;
	public final String description;
	public final Shell shell;
	public final Spec spec;

	UiFormat(String label, String description, Shell shell, Spec spec) {
		this.label = label;
		this.description = description;
		this.shell = shell;
		this.spec = spec;
	}

	@Override public String label() { return label; }

	/** Applies the whole format: layout, colors and shapes. */
	public void apply() {
		Spec s = spec;
		Theme.FORMAT.set(this);
		Theme.BACKGROUND.set(s.background);
		Theme.PANEL.set(s.panel);
		Theme.BORDER.set(s.border);
		Theme.TEXT.set(s.text);
		Theme.TEXT_SECONDARY.set(Colors.mix(s.text, s.panel, 0.42f));
		Theme.ACCENT.set(s.accent);
		Theme.SECONDARY.set(s.secondary);
		Theme.ACCENT_MODE.set(s.accentMode);
		Theme.CARD_STYLE.set(s.card);
		Theme.PANEL_OPACITY.set(s.opacity);
		Theme.CORNER_RADIUS.set(s.radius);
		Theme.BORDER_WIDTH.set(s.borderWidth);
		Theme.SHADOWS.set(s.shadows);
		Theme.SHADOW_STRENGTH.set(s.shadow);
		Theme.TOGGLE_STYLE.set(s.toggle);
		Theme.INDICATOR.set(s.indicator);
		Theme.BACKDROP.set(s.backdrop);
		Theme.LAYOUT.set(s.layout);
		Theme.CARD_LOOK.set(s.look);
		Theme.BLUR.set(s.blur);
		Theme.BACKGROUND_DIM.set(s.dim);
		Theme.UPPERCASE_HEADINGS.set(s.uppercase);
		Theme.SIDEBAR_ICONS.set(s.sidebarIcons);
		Theme.SIDEBAR_LABELS.set(true);
		Theme.FONT.set(s.font);
	}
}
