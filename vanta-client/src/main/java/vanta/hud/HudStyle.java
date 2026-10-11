package vanta.hud;

import java.util.ArrayList;
import java.util.List;

import vanta.branding.Theme;
import vanta.config.BoolSetting;
import vanta.config.ChoiceSetting;
import vanta.config.ColorSetting;
import vanta.config.IntSetting;
import vanta.config.Labeled;
import vanta.config.Setting;
import vanta.config.SettingRegistry;
import vanta.gui.MenuPage;

/** Look shared by every HUD widget. Each widget can still override colors and background. */
public final class HudStyle {
	public enum Format implements Labeled {
		LABEL_VALUE("FPS 120"), COLON("FPS: 120"), BRACKETS("[FPS: 120]"), VALUE_LABEL("120 FPS"), VALUE_ONLY("120");
		private final String label;
		Format(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	public enum LabelColor implements Labeled {
		ACCENT("Theme accent"), CHROMA("Chroma"), CUSTOM("Custom color"), SAME("Same as value");
		private final String label;
		LabelColor(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	private static final List<Setting<?>> SETTINGS = new ArrayList<>();

	public static final ChoiceSetting<Format> FORMAT = add(new ChoiceSetting<>("hud.format", "Text format",
			"How labels and values are written in text widgets.", Format.LABEL_VALUE));
	public static final ChoiceSetting<LabelColor> LABEL_COLOR = add(new ChoiceSetting<>("hud.label_color", "Label color",
			"Color of labels like \"FPS\" (when a widget's Accent labels option is on).", LabelColor.ACCENT));
	public static final ColorSetting CUSTOM_LABEL = add(new ColorSetting("hud.custom_label", "Custom label color",
			"Used when Label color is Custom.", 0xFF27D3E6, false));
	public static final BoolSetting CHROMA_VALUES = add(new BoolSetting("hud.chroma_values", "Chroma values",
			"Rainbow-colored values in every text widget.", false));
	public static final ColorSetting BACKGROUND = add(new ColorSetting("hud.background", "Widget background",
			"Background color behind widgets (opacity is set per widget).", 0xFF0A0C10, false));
	public static final IntSetting PADDING = add(new IntSetting("hud.padding", "Padding",
			"Space between a widget's edge and its text.", 4, 1, 8, 1, "px"));
	public static final BoolSetting OUTLINE = add(new BoolSetting("hud.outline", "Accent outline",
			"Thin accent-colored border around widget backgrounds.", false));
	public static final BoolSetting SIDE_BAR = add(new BoolSetting("hud.side_bar", "Accent side bar",
			"A small accent bar on the left of each widget.", false));

	private HudStyle() {
	}

	private static <S extends Setting<?>> S add(S s) {
		SETTINGS.add(s);
		return SettingRegistry.register(s, MenuPage.HUD);
	}

	public static void init() {
	}

	public static List<Setting<?>> settings() { return SETTINGS; }

	public static int labelColor(int valueColor) {
		return switch (LABEL_COLOR.get()) {
			case ACCENT -> Theme.accent();
			case CHROMA -> Theme.chroma(0.1f);
			case CUSTOM -> CUSTOM_LABEL.argb();
			case SAME -> valueColor;
		};
	}
}
