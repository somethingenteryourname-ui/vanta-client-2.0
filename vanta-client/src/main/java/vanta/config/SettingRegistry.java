package vanta.config;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Every setting VANTA knows about, keyed by its unique id. */
public final class SettingRegistry {
	private static final Map<String, Setting<?>> SETTINGS = new LinkedHashMap<>();

	private SettingRegistry() {
	}

	public static <S extends Setting<?>> S register(S setting, Object owner) {
		if (SETTINGS.containsKey(setting.id())) {
			throw new IllegalStateException("Duplicate VANTA setting id: " + setting.id());
		}
		setting.setOwner(owner);
		SETTINGS.put(setting.id(), setting);
		return setting;
	}

	public static Setting<?> byId(String id) { return SETTINGS.get(id); }

	public static Collection<Setting<?>> all() { return Collections.unmodifiableCollection(SETTINGS.values()); }

	public static void resetAll() {
		for (Setting<?> s : SETTINGS.values()) s.reset();
	}

	public static void resetOwnedBy(Object owner) {
		for (Setting<?> s : SETTINGS.values()) {
			if (s.owner() == owner) s.reset();
		}
	}
}
