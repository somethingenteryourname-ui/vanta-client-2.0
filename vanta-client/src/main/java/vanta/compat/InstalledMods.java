package vanta.compat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;

import net.minecraft.client.gui.screens.Screen;

/** The installed Fabric mods for the Mods page, plus opening their settings through Mod Menu when it's installed. */
public final class InstalledMods {
	public record Entry(String id, String name, String version, String description, String authors, String homepage, boolean library) {
	}

	private static final Set<String> BUILT_IN = Set.of("java", "minecraft", "fabricloader", "mixinextras");
	private static List<Entry> cache;

	private InstalledMods() {
	}

	public static List<Entry> list() {
		if (cache != null) return cache;
		List<Entry> out = new ArrayList<>();
		try {
			for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
				ModMetadata meta = mod.getMetadata();
				String id = meta.getId();
				if (BUILT_IN.contains(id)) continue;
				StringBuilder authors = new StringBuilder();
				for (Person p : meta.getAuthors()) {
					if (authors.length() > 0) authors.append(", ");
					authors.append(p.getName());
				}
				String home = meta.getContact().get("homepage").orElse(meta.getContact().get("sources").orElse(""));
				boolean library = isLibrary(id, mod.getContainingMod().isPresent());
				out.add(new Entry(id, meta.getName(), meta.getVersion().getFriendlyString(), meta.getDescription(), authors.toString(), home, library));
			}
		} catch (RuntimeException | LinkageError e) {
			// very old loader: no mod list
		}
		out.sort(Comparator.comparing((Entry e) -> e.name().toLowerCase(Locale.ROOT)));
		cache = out;
		return out;
	}

	/** Fabric API's inner modules and mods bundled inside other mods count as libraries. */
	public static boolean isLibrary(String id, boolean nested) {
		if (nested) return true;
		return id.startsWith("fabric-") && !id.equals("fabric-api") || id.endsWith("-api-base") || id.equals("fabric-language-kotlin");
	}

	public static boolean modMenuInstalled() {
		try {
			return FabricLoader.getInstance().isModLoaded("modmenu");
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** The mod's own settings screen (through Mod Menu), or null if it has none. */
	public static Screen configScreen(String modId, Screen parent) {
		if (!modMenuInstalled()) return null;
		try {
			Class<?> mm = Class.forName("com.terraformersmc.modmenu.ModMenu");
			Method m = mm.getMethod("getConfigScreen", String.class, Screen.class);
			Object s = m.invoke(null, modId, parent);
			return s instanceof Screen screen ? screen : null;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return null;
		}
	}
}
