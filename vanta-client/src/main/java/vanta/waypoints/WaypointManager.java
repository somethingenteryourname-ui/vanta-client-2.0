package vanta.waypoints;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;

import vanta.config.ConfigManager;

/**
 * Waypoints per world (singleplayer save or server address), stored in
 * config/vanta/waypoints.json. Each world keeps its own list across all dimensions.
 */
public final class WaypointManager {
	public static final int[] COLORS = {0xFF8B5CF6, 0xFF22D3EE, 0xFF4ADE80, 0xFFFACC15, 0xFFFB923C, 0xFFF87171, 0xFFF472B6, 0xFFFFFFFF};
	private static final int MAX_PER_WORLD = 200;

	private static final Map<String, List<Waypoint>> worlds = new HashMap<>();
	private static boolean loaded;
	private static String cachedKey;
	private static long cachedKeyAt;

	private WaypointManager() {
	}

	private static Path file() {
		return ConfigManager.get().directory().resolve("waypoints.json");
	}

	private static void ensureLoaded() {
		if (loaded) return;
		loaded = true;
		JsonObject root = ConfigManager.readObject(file());
		if (root == null) return;
		for (Map.Entry<String, JsonElement> world : root.entrySet()) {
			if (!world.getValue().isJsonArray()) continue;
			List<Waypoint> list = new ArrayList<>();
			for (JsonElement el : world.getValue().getAsJsonArray()) {
				try {
					JsonObject o = el.getAsJsonObject();
					Waypoint w = new Waypoint(o.get("name").getAsString(), o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt(),
							o.has("dimension") ? o.get("dimension").getAsString() : "minecraft:overworld",
							o.has("color") ? (int) Long.parseLong(o.get("color").getAsString(), 16) : COLORS[0]);
					w.visible = !o.has("visible") || o.get("visible").getAsBoolean();
					w.death = o.has("death") && o.get("death").getAsBoolean();
					w.created = o.has("created") ? o.get("created").getAsLong() : 0;
					if (w.name.length() > 32) w.name = w.name.substring(0, 32);
					list.add(w);
				} catch (RuntimeException ignored) {
					// skip broken entries, keep the rest
				}
			}
			worlds.put(world.getKey(), list);
		}
	}

	public static void save() {
		JsonObject root = new JsonObject();
		for (Map.Entry<String, List<Waypoint>> e : worlds.entrySet()) {
			if (e.getValue().isEmpty()) continue;
			JsonArray arr = new JsonArray();
			for (Waypoint w : e.getValue()) {
				JsonObject o = new JsonObject();
				o.addProperty("name", w.name);
				o.addProperty("x", w.x);
				o.addProperty("y", w.y);
				o.addProperty("z", w.z);
				o.addProperty("dimension", w.dimension);
				o.addProperty("color", String.format(Locale.ROOT, "%08X", w.color));
				o.addProperty("visible", w.visible);
				if (w.death) o.addProperty("death", true);
				o.addProperty("created", w.created);
				arr.add(o);
			}
			root.add(e.getKey(), arr);
		}
		ConfigManager.writeObject(file(), root);
	}

	/** Key for the world you're in: "world:&lt;save folder&gt;", "server:&lt;address&gt;", or null outside a world. */
	public static String worldKey(Minecraft mc) {
		if (mc.level == null) return null;
		long now = System.currentTimeMillis();
		if (cachedKey != null && now - cachedKeyAt < 2000) return cachedKey;
		String key = null;
		IntegratedServer server = mc.getSingleplayerServer();
		if (server != null) {
			try {
				Path p = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
				key = "world:" + (p != null ? p.toString() : server.getWorldData().getLevelName());
			} catch (RuntimeException e) {
				key = "world:" + server.getWorldData().getLevelName();
			}
		} else {
			ServerData data = mc.getCurrentServer();
			if (data != null) key = (data.isRealm() ? "realms:" + data.name : "server:" + data.ip.toLowerCase(Locale.ROOT));
		}
		cachedKey = key;
		cachedKeyAt = now;
		return key;
	}

	/** Forget the cached key (call when leaving a world). */
	public static void resetKey() {
		cachedKey = null;
	}

	/** All waypoints of the current world (every dimension), or an empty list. */
	public static List<Waypoint> current(Minecraft mc) {
		ensureLoaded();
		String key = worldKey(mc);
		if (key == null) return Collections.emptyList();
		return worlds.computeIfAbsent(key, k -> new ArrayList<>());
	}

	public static String dimension(Minecraft mc) {
		return mc.level == null ? "" : mc.level.dimension().identifier().toString();
	}

	public static Waypoint add(Minecraft mc, String name, int x, int y, int z) {
		List<Waypoint> list = current(mc);
		if (mc.level == null || list.size() >= MAX_PER_WORLD) return null;
		int color = COLORS[list.size() % COLORS.length];
		Waypoint w = new Waypoint(name, x, y, z, dimension(mc), color);
		list.add(w);
		save();
		return w;
	}

	/** Replaces the previous death point (if any) with a new one. */
	public static Waypoint addDeath(Minecraft mc, int x, int y, int z, boolean keepOld) {
		List<Waypoint> list = current(mc);
		if (mc.level == null) return null;
		if (!keepOld) list.removeIf(w -> w.death);
		Waypoint w = new Waypoint("Death", x, y, z, dimension(mc), 0xFFF87171);
		w.death = true;
		list.add(w);
		while (list.size() > MAX_PER_WORLD) list.remove(0);
		save();
		return w;
	}

	public static void remove(Minecraft mc, Waypoint w) {
		current(mc).remove(w);
		save();
	}

	public static String nextName(Minecraft mc) {
		int n = 1;
		List<Waypoint> list = current(mc);
		outer:
		while (true) {
			String name = "Waypoint " + n;
			for (Waypoint w : list) {
				if (w.name.equalsIgnoreCase(name)) {
					n++;
					continue outer;
				}
			}
			return name;
		}
	}
}
