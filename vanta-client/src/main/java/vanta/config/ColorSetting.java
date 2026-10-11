package vanta.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** An ARGB color, stored as "#AARRGGBB" (or "#RRGGBB" when fully opaque). */
public final class ColorSetting extends Setting<Integer> {
	private final boolean allowAlpha;

	public ColorSetting(String id, String name, String description, int defaultArgb, boolean allowAlpha) {
		super(id, name, description, defaultArgb);
		this.allowAlpha = allowAlpha;
	}

	public int argb() { return get(); }
	public boolean allowsAlpha() { return allowAlpha; }

	@Override public Kind kind() { return Kind.COLOR; }

	@Override
	protected Integer validate(Integer candidate) {
		return allowAlpha ? candidate : (candidate | 0xFF000000);
	}

	@Override public JsonElement toJson() { return new JsonPrimitive(toHex(get())); }

	@Override
	protected Integer parse(JsonElement element) {
		if (!element.isJsonPrimitive()) return null;
		return parseHex(element.getAsString());
	}

	@Override public String displayValue() { return toHex(get()); }

	public static String toHex(int argb) {
		int a = (argb >>> 24) & 0xFF;
		if (a == 0xFF) return String.format("#%06X", argb & 0xFFFFFF);
		return String.format("#%08X", argb);
	}

	/** Accepts "#RGB", "#RRGGBB", "#AARRGGBB", with or without '#'. Returns null if invalid. */
	public static Integer parseHex(String raw) {
		if (raw == null) return null;
		String s = raw.trim();
		if (s.startsWith("#")) s = s.substring(1);
		if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
		if (!s.matches("[0-9a-fA-F]+")) return null;
		try {
			if (s.length() == 3) {
				StringBuilder b = new StringBuilder(6);
				for (char c : s.toCharArray()) b.append(c).append(c);
				return 0xFF000000 | Integer.parseInt(b.toString(), 16);
			}
			if (s.length() == 6) return 0xFF000000 | Integer.parseInt(s, 16);
			if (s.length() == 8) return (int) Long.parseLong(s, 16);
		} catch (NumberFormatException ignored) {
		}
		return null;
	}
}
