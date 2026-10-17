package vanta.profiles;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonObject;

import vanta.client.VantaClient;
import vanta.config.ConfigManager;
import vanta.config.SettingRegistry;
import vanta.config.TextSetting;
import vanta.gui.MenuPage;

/**
 * Profiles are complete VANTA setups (modules, HUD layout, crosshair, theme, menu
 * settings) saved as JSON files in config/vanta/profiles. Switching saves the current
 * profile first, so nothing is lost. Files can be shared: drop one into the folder and
 * press Refresh. Imported files are validated; bad values fall back to defaults.
 */
public final class ProfileManager {
	public static final TextSetting ACTIVE = (TextSetting) SettingRegistry.register(
			new TextSetting("profiles.active", "Active profile", "The profile in use.", "Default", 32), MenuPage.PROFILES)
			.hidden().excludeFromSnapshots();

	private static final String EXT = ".json";
	private static List<String> cached = List.of();

	private ProfileManager() {
	}

	public static void init() {
	}

	public static Path folder() {
		return ConfigManager.get().directory().resolve("profiles");
	}

	/** Creates the built-in profiles the first time VANTA runs. */
	public static void ensureBuiltIns() {
		try {
			Files.createDirectories(folder());
		} catch (IOException e) {
			VantaClient.LOG.warn("[VANTA] Could not create profiles folder", e);
			return;
		}
		if (!Files.exists(path("Default"))) save("Default");
		for (BuiltInProfiles.Builtin b : BuiltInProfiles.Builtin.values()) {
			if (!Files.exists(path(b.label))) {
				JsonObject snap = BuiltInProfiles.build(b);
				ConfigManager.writeObject(path(b.label), wrap(snap));
			}
		}
		refresh();
	}

	public static String active() { return ACTIVE.get().isEmpty() ? "Default" : ACTIVE.get(); }

	public static List<String> list() { return cached; }

	public static void refresh() {
		List<String> names = new ArrayList<>();
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(folder(), "*" + EXT)) {
			for (Path p : ds) {
				String n = p.getFileName().toString();
				names.add(n.substring(0, n.length() - EXT.length()));
			}
		} catch (IOException e) {
			VantaClient.LOG.warn("[VANTA] Could not list profiles", e);
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
		cached = List.copyOf(names);
	}

	/** Returns an error message, or null if the name is usable. */
	public static String validateName(String name) {
		String n = name.strip();
		if (n.isEmpty()) return "Enter a name.";
		if (n.length() > 32) return "Use 32 characters or fewer.";
		if (!n.matches("[A-Za-z0-9 _\\-]+")) return "Use letters, numbers, spaces, - and _ only.";
		if (n.toLowerCase(Locale.ROOT).matches("con|prn|aux|nul|com\\d|lpt\\d")) return "That name isn't allowed on Windows.";
		return null;
	}

	public static boolean exists(String name) {
		return Files.exists(path(name));
	}

	private static Path path(String name) {
		return folder().resolve(name.strip() + EXT);
	}

	private static JsonObject wrap(JsonObject settings) {
		JsonObject root = new JsonObject();
		root.addProperty("vantaProfile", 1);
		root.add("settings", settings);
		return root;
	}

	/** Saves the current settings as the named profile. */
	public static boolean save(String name) {
		boolean ok = ConfigManager.writeObject(path(name), wrap(ConfigManager.get().snapshotAll()));
		refresh();
		return ok;
	}

	/** Saves the current profile, then loads the named one. Returns an error message or null. */
	public static String switchTo(String name) {
		JsonObject root = ConfigManager.readObject(path(name));
		if (root == null) return "That profile file is missing or damaged.";
		JsonObject settings = root.has("settings") && root.get("settings").isJsonObject() ? root.getAsJsonObject("settings") : root;
		save(active());
		int invalid = ConfigManager.get().applySnapshot(settings, true);
		ACTIVE.set(name.strip());
		ConfigManager.get().saveNow();
		return invalid > 0 ? invalid + " value(s) in that profile were invalid and were reset." : null;
	}

	public static String duplicate(String from, String to) {
		String err = validateName(to);
		if (err != null) return err;
		if (exists(to)) return "A profile with that name already exists.";
		try {
			Files.copy(path(from), path(to));
		} catch (IOException e) {
			return "Could not copy: " + e.getMessage();
		}
		refresh();
		return null;
	}

	public static String rename(String from, String to) {
		String err = validateName(to);
		if (err != null) return err;
		if (exists(to) && !from.equalsIgnoreCase(to.strip())) return "A profile with that name already exists.";
		try {
			Files.move(path(from), path(to));
		} catch (IOException e) {
			return "Could not rename: " + e.getMessage();
		}
		if (active().equals(from)) ACTIVE.set(to.strip());
		refresh();
		return null;
	}

	public static String delete(String name) {
		if (name.equals(active())) return "You can't delete the profile you're using.";
		try {
			Files.deleteIfExists(path(name));
		} catch (IOException e) {
			return "Could not delete: " + e.getMessage();
		}
		refresh();
		return null;
	}

	public static String create(String name) {
		String err = validateName(name);
		if (err != null) return err;
		if (exists(name)) return "A profile with that name already exists.";
		save(name);
		return null;
	}
}
