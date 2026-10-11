package vanta.modules.impl;

import vanta.branding.Theme;
import vanta.config.BoolSetting;
import vanta.config.ColorSetting;
import vanta.config.IntSetting;
import vanta.modules.Category;
import vanta.modules.Module;

/** Custom color and thickness for the outline around the block you're looking at. */
public final class BlockOutlineModule extends Module {
	private static BlockOutlineModule instance;

	public final ColorSetting color = setting(new ColorSetting(key("color"), "Color",
			"Outline color. Supports transparency (#AARRGGBB).", 0xCC27D3E6, true));
	public final BoolSetting chroma = setting(new BoolSetting(key("chroma"), "Chroma",
			"Cycle through rainbow colors.", false));
	public final BoolSetting useAccent = setting(new BoolSetting(key("use_accent"), "Use theme accent",
			"Match the VANTA accent color.", false));
	public final IntSetting width = setting(new IntSetting(key("width"), "Thickness",
			"Line thickness. Vanilla is about 3.", 3, 1, 10, 1, "px"));

	public BlockOutlineModule() {
		super("block_outline", "Block Outline", "Recolor and thicken the outline of the block you're looking at.", Category.VISUALS, false);
		instance = this;
	}

	public static boolean active() { return instance != null && instance.isEnabled(); }

	public static int outlineColor(int vanilla) {
		BlockOutlineModule b = instance;
		int alpha = (b.color.argb() >>> 24) & 0xFF;
		int rgb;
		if (b.chroma.on()) rgb = Theme.chroma(0f);
		else if (b.useAccent.on()) rgb = Theme.accent();
		else rgb = b.color.argb();
		return (alpha << 24) | (rgb & 0xFFFFFF);
	}

	public static float outlineWidth(float vanilla) {
		return Math.max(1f, instance.width.value() * Math.max(1f, vanilla / 2.5f));
	}
}
