package vanta.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.google.gson.JsonElement;

/**
 * One persistent, validated, user-facing option.
 * Every setting has a stable unique id (used as its key in the config file),
 * a default value, a display name, a description, and a UI control type.
 */
public abstract class Setting<T> {
	public enum Kind { BOOLEAN, INT, CHOICE, COLOR, TEXT }

	private final String id;
	private final String name;
	private final String description;
	private final T defaultValue;
	private T value;
	private boolean hidden;
	private String tag = "";
	private Object owner;
	private final List<Consumer<T>> listeners = new ArrayList<>(1);

	protected Setting(String id, String name, String description, T defaultValue) {
		this.id = Objects.requireNonNull(id);
		this.name = name;
		this.description = description;
		this.defaultValue = defaultValue;
		this.value = defaultValue;
	}

	public final String id() { return id; }
	public final String name() { return name; }
	public final String description() { return description; }
	public final T defaultValue() { return defaultValue; }
	public final T get() { return value; }

	public final void set(T newValue) {
		T valid = newValue == null ? defaultValue : validate(newValue);
		if (Objects.equals(valid, value)) return;
		value = valid;
		ConfigManager.get().markDirty();
		for (Consumer<T> l : listeners) l.accept(valid);
	}

	/** Sets a value while loading, without marking the config dirty or firing listeners. */
	final void setSilently(T newValue) {
		value = newValue == null ? defaultValue : validate(newValue);
	}

	public final void reset() { set(defaultValue); }

	final void setDefaultSilently() { value = defaultValue; }
	public final boolean isDefault() { return Objects.equals(value, defaultValue); }

	public final Setting<T> onChange(Consumer<T> listener) { listeners.add(listener); return this; }

	/** Hidden settings are saved but not shown as rows (e.g. HUD positions). */
	public final Setting<T> hidden() { this.hidden = true; return this; }
	public final boolean isHidden() { return hidden; }

	/** Short label shown beside the description, e.g. "Visual" or "Performance". */
	public final Setting<T> tag(String tag) { this.tag = tag; return this; }
	public final String tag() { return tag; }

	public final void setOwner(Object owner) { this.owner = owner; }
	public final Object owner() { return owner; }

	public abstract Kind kind();
	protected abstract T validate(T candidate);
	public abstract JsonElement toJson();
	/** Parses a stored value. Returns null when the stored value is unusable. */
	protected abstract T parse(JsonElement element);
	public abstract String displayValue();

	/** Parses and applies a stored value through set(), so listeners fire. Returns false if invalid. */
	final boolean applyJson(JsonElement element) {
		try {
			T parsed = parse(element);
			if (parsed == null) return false;
			set(parsed);
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** Excluded settings are never copied into profiles or saved themes. */
	private boolean excludeFromSnapshots;

	public final Setting<T> excludeFromSnapshots() { excludeFromSnapshots = true; return this; }
	public final boolean isExcludedFromSnapshots() { return excludeFromSnapshots; }

	final boolean load(JsonElement element) {
		try {
			T parsed = parse(element);
			if (parsed == null) return false;
			setSilently(parsed);
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}
}
