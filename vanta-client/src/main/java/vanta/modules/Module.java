package vanta.modules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import vanta.config.BoolSetting;
import vanta.config.Setting;
import vanta.config.SettingRegistry;

/**
 * A feature that can be switched on and off and has its own settings.
 * The id is permanent: it prefixes every setting id ("module.&lt;id&gt;.…"),
 * so saved settings survive updates even if the display name changes.
 */
public abstract class Module {
	private final String id;
	private final String name;
	private final String description;
	private final Category category;
	private final List<Setting<?>> settings = new ArrayList<>();
	private final BoolSetting enabled;
	private final BoolSetting favorite;
	private boolean experimental;
	private long lastChanged;

	protected Module(String id, String name, String description, Category category, boolean enabledByDefault) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.category = category;
		this.enabled = new BoolSetting("module." + id + ".enabled", "Enabled", "Turns " + name + " on or off.", enabledByDefault);
		this.enabled.hidden();
		SettingRegistry.register(enabled, this);
		this.favorite = new BoolSetting("module." + id + ".favorite", "Favorite", "Pinned to the top of the module list.", false);
		this.favorite.hidden();
		SettingRegistry.register(favorite, this);
		this.enabled.onChange(on -> {
			lastChanged = System.currentTimeMillis();
			if (on) onEnable(); else onDisable();
		});
	}

	/** Registers a module setting. The id is automatically prefixed with "module.&lt;id&gt;.". */
	protected final <S extends Setting<?>> S setting(S setting) {
		if (!setting.id().startsWith("module." + id + ".")) {
			throw new IllegalArgumentException("Setting " + setting.id() + " must start with module." + id + ".");
		}
		settings.add(setting);
		SettingRegistry.register(setting, this);
		setting.onChange(v -> lastChanged = System.currentTimeMillis());
		return setting;
	}

	/** Builds a properly prefixed setting id for this module. */
	protected final String key(String name) {
		return "module." + id + "." + name;
	}

	protected final void markExperimental() { this.experimental = true; }

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	/** Called every client tick while the module is enabled. */
	public void tick(net.minecraft.client.Minecraft mc) {
	}

	/**
	 * Re-applies any state the module keeps outside VANTA (vanilla options, debug toggles)
	 * after settings are loaded or a profile is switched.
	 */
	public void syncExternal() {
	}

	/** Optional status line shown on the module card (e.g. a live value). Return null for none. */
	public String status() { return null; }

	public final String id() { return id; }
	public final String name() { return name; }
	public final String description() { return description; }
	public final Category category() { return category; }
	public final boolean isExperimental() { return experimental; }
	public final boolean isEnabled() { return enabled.on(); }
	public final void setEnabled(boolean on) { enabled.set(on); }
	public final void toggle() { enabled.toggle(); }
	public final boolean isFavorite() { return favorite.on(); }
	public final void toggleFavorite() { favorite.toggle(); }
	public final long lastChanged() { return lastChanged; }
	public final BoolSetting enabledSetting() { return enabled; }

	/** Visible, user-editable settings (hidden ones such as HUD positions are excluded). */
	public final List<Setting<?>> visibleSettings() {
		List<Setting<?>> out = new ArrayList<>(settings.size());
		for (Setting<?> s : settings) if (!s.isHidden()) out.add(s);
		return out;
	}

	public final List<Setting<?>> allSettings() { return Collections.unmodifiableList(settings); }

	/** Resets every setting of this module, including position and enabled state. */
	public void resetToDefaults() {
		SettingRegistry.resetOwnedBy(this);
	}
}
