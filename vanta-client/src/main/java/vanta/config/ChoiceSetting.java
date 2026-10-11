package vanta.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** One value out of an enum. Enums implement {@link Labeled} for display names. */
public final class ChoiceSetting<E extends Enum<E> & Labeled> extends Setting<E> {
	private final E[] values;

	public ChoiceSetting(String id, String name, String description, E defaultValue) {
		super(id, name, description, defaultValue);
		this.values = defaultValue.getDeclaringClass().getEnumConstants();
	}

	public void next() { set(values[(get().ordinal() + 1) % values.length]); }
	public void previous() { set(values[(get().ordinal() - 1 + values.length) % values.length]); }
	public E[] values() { return values; }

	@Override public Kind kind() { return Kind.CHOICE; }
	@Override protected E validate(E candidate) { return candidate; }
	@Override public JsonElement toJson() { return new JsonPrimitive(get().name()); }

	@Override
	protected E parse(JsonElement element) {
		if (!element.isJsonPrimitive()) return null;
		String s = element.getAsString();
		for (E e : values) if (e.name().equalsIgnoreCase(s)) return e;
		return null;
	}

	@Override public String displayValue() { return get().label(); }
}
