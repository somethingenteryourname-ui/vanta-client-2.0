package vanta.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A short line of text, edited in a small input dialog. */
public final class TextSetting extends Setting<String> {
	private final int maxLength;

	public TextSetting(String id, String name, String description, String defaultValue, int maxLength) {
		super(id, name, description, defaultValue);
		this.maxLength = maxLength;
	}

	public int maxLength() { return maxLength; }

	@Override public Kind kind() { return Kind.TEXT; }

	@Override
	protected String validate(String candidate) {
		// Strip control characters and section signs so stored text can't inject formatting codes.
		String clean = candidate.replaceAll("[\\p{Cntrl}§]", "").strip();
		if (clean.length() > maxLength) clean = clean.substring(0, maxLength);
		return clean;
	}

	@Override public JsonElement toJson() { return new JsonPrimitive(get()); }

	@Override
	protected String parse(JsonElement element) {
		if (!element.isJsonPrimitive()) return null;
		return element.getAsString();
	}

	@Override public String displayValue() { return get().isEmpty() ? "(empty)" : get(); }
}
