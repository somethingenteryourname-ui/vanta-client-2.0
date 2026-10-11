package vanta.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class BoolSetting extends Setting<Boolean> {
	public BoolSetting(String id, String name, String description, boolean defaultValue) {
		super(id, name, description, defaultValue);
	}

	public boolean on() { return get(); }
	public void toggle() { set(!get()); }

	@Override public Kind kind() { return Kind.BOOLEAN; }
	@Override protected Boolean validate(Boolean candidate) { return candidate; }
	@Override public JsonElement toJson() { return new JsonPrimitive(get()); }

	@Override
	protected Boolean parse(JsonElement element) {
		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) return null;
		return element.getAsBoolean();
	}

	@Override public String displayValue() { return get() ? "On" : "Off"; }
}
