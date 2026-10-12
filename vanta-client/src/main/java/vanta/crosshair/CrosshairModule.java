package vanta.crosshair;

import java.util.LinkedHashMap;
import java.util.Map;

import vanta.config.BoolSetting;
import vanta.config.ChoiceSetting;
import vanta.config.ColorSetting;
import vanta.config.IntSetting;
import vanta.config.Labeled;
import vanta.config.Setting;
import vanta.modules.Category;
import vanta.modules.Module;

/** Fully custom crosshair. Display only: it never changes reach or how attacks work. */
public final class CrosshairModule extends Module {
	public enum Shape implements Labeled {
		CROSS("Cross"), CROSS_DOT("Cross + dot"), DOT("Dot"), CIRCLE("Circle"), CIRCLE_DOT("Circle + dot"),
		T_SHAPE("T"), X("X"), PLUS_GAP("Brackets"), VANILLA("Vanilla look");
		private final String label;
		Shape(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	public enum Dynamic implements Labeled {
		STATIC("Static"), MOVEMENT("Spread when moving");
		private final String label;
		Dynamic(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	public enum Preset implements Labeled {
		CRYSTAL("Crystal PvP"), MACE("Mace PvP"), MINIMAL("Minimal"), CLASSIC("Classic");
		private final String label;
		Preset(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	public final ChoiceSetting<Shape> shape = setting(new ChoiceSetting<>(key("shape"), "Shape", "The crosshair's shape.", Shape.CROSS));
	public final IntSetting length = setting(new IntSetting(key("length"), "Arm length", "Length of each line.", 4, 1, 30, 1, "px"));
	public final IntSetting thickness = setting(new IntSetting(key("thickness"), "Thickness", "Line thickness.", 1, 1, 8, 1, "px"));
	public final IntSetting gap = setting(new IntSetting(key("gap"), "Gap", "Empty space in the middle.", 2, 0, 20, 1, "px"));
	public final IntSetting dotSize = setting(new IntSetting(key("dot_size"), "Dot size", "Size of the center dot.", 1, 1, 10, 1, "px"));
	public final IntSetting circleRadius = setting(new IntSetting(key("circle_radius"), "Circle size", "Radius of circle shapes.", 5, 2, 24, 1, "px"));
	public final IntSetting size = setting(new IntSetting(key("size"), "Overall size",
			"Scales the whole crosshair, including the Vanilla look shape. 100% is normal.", 100, 50, 400, 10, "%"));
	public final ColorSetting color = setting(new ColorSetting(key("color"), "Color", "Crosshair color. Supports transparency (#AARRGGBB).", 0xFFFFFFFF, true));
	public final BoolSetting outline = setting(new BoolSetting(key("outline"), "Outline", "Dark border around the lines for visibility.", true));
	public final ColorSetting outlineColor = setting(new ColorSetting(key("outline_color"), "Outline color", "Color of the border.", 0xB0000000, true));
	public final BoolSetting chroma = setting(new BoolSetting(key("chroma"), "Chroma", "Rainbow color cycling.", false));
	public final BoolSetting hitColor = setting(new BoolSetting(key("hit_color"), "Target color", "Change color when aiming at an entity.", true));
	public final ColorSetting targetColor = setting(new ColorSetting(key("target_color"), "Target color", "Color when aiming at an entity.", 0xFFFF5555, true));
	public final ChoiceSetting<Dynamic> dynamic = setting(new ChoiceSetting<>(key("dynamic"), "Behavior", "Static, or spread a little while moving.", Dynamic.STATIC));
	public final BoolSetting attackIndicator = setting(new BoolSetting(key("attack_indicator"), "Attack cooldown bar", "Thin bar under the crosshair while your attack recharges.", true));
	public final BoolSetting thirdPerson = setting(new BoolSetting(key("third_person"), "Show in third person", "Also draw the crosshair in third person.", false));

	public CrosshairModule() {
		super("crosshair", "Custom Crosshair", "Your own crosshair: shape, size, gap, colors, outline and presets.", Category.VISUALS, false);
	}

	/** Setting values for a preset, keyed by setting id. */
	public Map<String, Object> presetValues(Preset p) {
		Map<String, Object> m = new LinkedHashMap<>();
		switch (p) {
			case CRYSTAL -> {
				m.put(shape.id(), Shape.CROSS_DOT);
				m.put(length.id(), 3);
				m.put(thickness.id(), 1);
				m.put(gap.id(), 2);
				m.put(dotSize.id(), 1);
				m.put(color.id(), 0xFF27D3E6);
				m.put(outline.id(), true);
				m.put(dynamic.id(), Dynamic.STATIC);
			}
			case MACE -> {
				m.put(shape.id(), Shape.CIRCLE_DOT);
				m.put(circleRadius.id(), 5);
				m.put(thickness.id(), 1);
				m.put(dotSize.id(), 2);
				m.put(color.id(), 0xFFFBBF24);
				m.put(outline.id(), true);
				m.put(dynamic.id(), Dynamic.STATIC);
			}
			case MINIMAL -> {
				m.put(shape.id(), Shape.DOT);
				m.put(dotSize.id(), 2);
				m.put(color.id(), 0xFFFFFFFF);
				m.put(outline.id(), true);
				m.put(dynamic.id(), Dynamic.STATIC);
			}
			case CLASSIC -> {
				m.put(shape.id(), Shape.CROSS);
				m.put(length.id(), 5);
				m.put(thickness.id(), 1);
				m.put(gap.id(), 0);
				m.put(color.id(), 0xFFFFFFFF);
				m.put(outline.id(), false);
				m.put(dynamic.id(), Dynamic.STATIC);
			}
		}
		return m;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public void applyPreset(Preset p) {
		for (Map.Entry<String, Object> e : presetValues(p).entrySet()) {
			for (Setting<?> s : allSettings()) {
				if (s.id().equals(e.getKey())) ((Setting) s).set(e.getValue());
			}
		}
	}

	@Override
	public String status() { return shape.get().label(); }
}
