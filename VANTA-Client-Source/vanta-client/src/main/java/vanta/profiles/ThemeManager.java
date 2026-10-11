package vanta.profiles;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

import vanta.branding.Theme;
import vanta.client.VantaClient;
import vanta.config.ConfigManager;

/** Saved themes: just the Theme page's settings, stored in config/vanta/themes and shareable. */
public final class ThemeManager {
	private static List<String> cached = List.of();

	private ThemeManager() {
	}

	public static Path folder() {
		return ConfigManager.get().directory().resolve("themes");
	}

	public static List<String> list() { return cached; }

	public static void refresh() {
		List<String> names = new ArrayList<>();
		try {
			Files.createDirectories(folder());
			try (DirectoryStream<Path> ds = Files.newDirectoryStream(folder(), "*.json")) {
				for (Path p : ds) {
					String n = p.getFileName().toString();
					names.add(n.substring(0, n.length() - 5));
				}
			}
		} catch (IOException e) {
			VantaClient.LOG.warn("[VANTA] Could not list themes", e);
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
		cached = List.copyOf(names);
	}

	public static String save(String name) {
		String err = ProfileManager.validateName(name);
		if (err != null) return err;
		JsonObject root = new JsonObject();
		root.addProperty("vantaTheme", 1);
		root.add("settings", ConfigManager.get().snapshot(Theme.settings()));
		if (!ConfigManager.writeObject(folder().resolve(name.strip() + ".json"), root)) return "Could not save the theme file.";
		refresh();
		return null;
	}

	public static String apply(String name) {
		JsonObject root = ConfigManager.readObject(folder().resolve(name + ".json"));
		if (root == null) return "That theme file is missing or damaged.";
		JsonObject settings = root.has("settings") && root.get("settings").isJsonObject() ? root.getAsJsonObject("settings") : root;
		int invalid = ConfigManager.get().applySnapshot(settings, Theme.settings(), false);
		return invalid > 0 ? invalid + " value(s) were invalid and were reset." : null;
	}

	public static String delete(String name) {
		try {
			Files.deleteIfExists(folder().resolve(name + ".json"));
		} catch (IOException e) {
			return "Could not delete: " + e.getMessage();
		}
		refresh();
		return null;
	}
}
