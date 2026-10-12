package vanta.menu;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;

import vanta.client.VantaClient;
import vanta.config.ConfigManager;

/**
 * Favorite servers (with live ping and player count), recently played servers and recent
 * worlds for the home screen. Joining uses Minecraft's own connect / open-world flow, so
 * authentication, resource packs and world loading work exactly like the vanilla screens.
 * Stored in config/vanta/servers.json.
 */
public final class QuickPlay {
	public record Entry(String name, String ip, long time) {
	}

	/** Live status for a server shown on the home screen. */
	public static final class Status {
		private static final long TIMEOUT_MS = 10_000L;
		public final ServerData data;
		private final long started = System.currentTimeMillis();
		public volatile boolean done;
		public volatile boolean failed;

		Status(ServerData data) {
			this.data = data;
		}

		/** True when the ping can't succeed any more (unknown host, refused, or no answer in time). */
		public boolean offline() {
			if (done) return false;
			if (failed) return true;
			if (data.motd != null && data.motd.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
					&& t.getKey().equals("multiplayer.status.cannot_connect")) {
				return data.players == null; // legacy servers still report players
			}
			return System.currentTimeMillis() - started > TIMEOUT_MS && data.players == null;
		}

		public String pingText() {
			if (offline()) return "offline";
			if (!done) return data.players != null ? "?" : "...";
			return data.ping + " ms";
		}

		public String playersText() {
			if (data.players == null) return "";
			return data.players.online() + "/" + data.players.max();
		}

		public int pingColor() {
			if (offline()) return 0xFFF87171;
			if (!done) return 0xFF9CA3AF;
			long p = data.ping;
			return p < 80 ? 0xFF4ADE80 : p < 150 ? 0xFFFACC15 : p < 250 ? 0xFFFB923C : 0xFFF87171;
		}
	}

	private static final int MAX_RECENT = 8;
	private static final List<Entry> favorites = new ArrayList<>();
	private static final List<Entry> recent = new ArrayList<>();
	private static final Map<String, Status> statuses = new HashMap<>();
	private static List<LevelSummary> recentWorlds = List.of();
	private static boolean worldsLoading;
	private static boolean loaded;
	private static ServerStatusPinger pinger;
	private static String lastJoined;
	private static final ExecutorService PING_POOL = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "VANTA server ping");
		t.setDaemon(true);
		return t;
	});

	private QuickPlay() {
	}

	private static Path file() {
		return ConfigManager.get().directory().resolve("servers.json");
	}

	private static void ensureLoaded() {
		if (loaded) return;
		loaded = true;
		JsonObject root = ConfigManager.readObject(file());
		if (root == null) return;
		read(root, "favorites", favorites);
		read(root, "recent", recent);
	}

	private static void read(JsonObject root, String key, List<Entry> into) {
		if (!root.has(key) || !root.get(key).isJsonArray()) return;
		for (JsonElement el : root.getAsJsonArray(key)) {
			if (!el.isJsonObject()) continue;
			JsonObject o = el.getAsJsonObject();
			if (!o.has("ip")) continue;
			String ip = o.get("ip").getAsString().strip();
			if (ip.isEmpty() || ip.length() > 255) continue;
			String name = o.has("name") ? o.get("name").getAsString() : ip;
			long time = o.has("time") ? o.get("time").getAsLong() : 0;
			into.add(new Entry(name.length() > 64 ? name.substring(0, 64) : name, ip, time));
		}
	}

	private static void save() {
		JsonObject root = new JsonObject();
		root.add("favorites", write(favorites));
		root.add("recent", write(recent));
		ConfigManager.writeObject(file(), root);
	}

	private static JsonArray write(List<Entry> list) {
		JsonArray a = new JsonArray();
		for (Entry e : list) {
			JsonObject o = new JsonObject();
			o.addProperty("name", e.name());
			o.addProperty("ip", e.ip());
			o.addProperty("time", e.time());
			a.add(o);
		}
		return a;
	}

	// ------------------------------------------------------------------ servers

	public static List<Entry> favorites() {
		ensureLoaded();
		return favorites;
	}

	public static List<Entry> recent() {
		ensureLoaded();
		return recent;
	}

	public static boolean isFavorite(String ip) {
		ensureLoaded();
		for (Entry e : favorites) if (e.ip().equalsIgnoreCase(ip)) return true;
		return false;
	}

	public static void toggleFavorite(String name, String ip) {
		ensureLoaded();
		if (!favorites.removeIf(e -> e.ip().equalsIgnoreCase(ip))) favorites.add(new Entry(name, ip, System.currentTimeMillis()));
		save();
	}

	public static void moveFavorite(int index, int delta) {
		int to = index + delta;
		if (index < 0 || to < 0 || index >= favorites.size() || to >= favorites.size()) return;
		Entry e = favorites.remove(index);
		favorites.add(to, e);
		save();
	}

	public static void removeRecent(String ip) {
		ensureLoaded();
		recent.removeIf(e -> e.ip().equalsIgnoreCase(ip));
		save();
	}

	/** Called every client tick; remembers the server you joined. */
	public static void tick(Minecraft mc) {
		ServerData current = mc.getCurrentServer();
		if (current == null || mc.level == null || current.isRealm()) {
			if (mc.level == null) lastJoined = null;
			return;
		}
		String ip = current.ip;
		if (ip == null || ip.equals(lastJoined)) return;
		lastJoined = ip;
		ensureLoaded();
		recent.removeIf(e -> e.ip().equalsIgnoreCase(ip));
		recent.add(0, new Entry(current.name == null || current.name.isBlank() ? ip : current.name, ip, System.currentTimeMillis()));
		while (recent.size() > MAX_RECENT) recent.remove(recent.size() - 1);
		save();
	}

	/** Servers saved in Minecraft's own Multiplayer list (for starring as favorites). */
	public static List<Entry> savedServers(Minecraft mc) {
		List<Entry> out = new ArrayList<>();
		try {
			ServerList list = new ServerList(mc);
			list.load();
			for (int i = 0; i < list.size(); i++) {
				ServerData d = list.get(i);
				out.add(new Entry(d.name, d.ip, 0));
			}
		} catch (RuntimeException e) {
			VantaClient.LOG.warn("[VANTA] Could not read the server list", e);
		}
		return out;
	}

	/** Live status for a server, pinging it the first time it's asked for. */
	public static Status status(Minecraft mc, Entry e) {
		String key = e.ip().toLowerCase(Locale.ROOT);
		Status s = statuses.get(key);
		if (s != null) return s;
		ServerData data = new ServerData(e.name(), e.ip(), ServerData.Type.OTHER);
		Status st = new Status(data);
		statuses.put(key, st);
		if (pinger == null) pinger = new ServerStatusPinger();
		ServerStatusPinger p = pinger;
		PING_POOL.submit(() -> {
			try {
				p.pingServer(data, () -> { }, () -> st.done = true, EventLoopGroupHolder.remote(mc.options.useNativeTransport()));
			} catch (Exception ex) {
				st.failed = true; // unknown host and similar
			}
		});
		return st;
	}

	/** Keeps pings flowing while the home screen is open. */
	public static void tickPinger() {
		if (pinger != null) pinger.tick();
	}

	/** Forget ping results (on leaving the home screen) so the next visit refreshes them. */
	public static void clearPings() {
		if (pinger != null) pinger.removeAll();
		statuses.clear();
	}

	public static void join(Minecraft mc, Screen parent, Entry e) {
		// Reuse the saved entry when there is one, so its resource pack choice is kept.
		ServerData data = null;
		try {
			ServerList list = new ServerList(mc);
			list.load();
			for (int i = 0; i < list.size(); i++) {
				if (list.get(i).ip.equalsIgnoreCase(e.ip())) {
					data = list.get(i);
					break;
				}
			}
		} catch (RuntimeException ignored) {
		}
		if (data == null) data = new ServerData(e.name(), e.ip(), ServerData.Type.OTHER);
		clearPings();
		ConnectScreen.startConnecting(parent, mc, ServerAddress.parseString(e.ip()), data, false, null);
	}

	// ------------------------------------------------------------------ worlds

	public static List<LevelSummary> recentWorlds() { return recentWorlds; }

	/** Loads the newest singleplayer worlds in the background. */
	public static void refreshWorlds(Minecraft mc) {
		if (worldsLoading) return;
		worldsLoading = true;
		try {
			LevelStorageSource source = mc.getLevelSource();
			LevelStorageSource.LevelCandidates candidates = source.findLevelCandidates();
			if (candidates.isEmpty()) {
				recentWorlds = List.of();
				worldsLoading = false;
				return;
			}
			source.loadLevelSummaries(candidates).whenComplete((list, err) -> {
				worldsLoading = false;
				if (err != null || list == null) return;
				List<LevelSummary> sorted = new ArrayList<>(list);
				sorted.removeIf(s -> !s.primaryActionActive() || s.isLocked());
				sorted.sort(Comparator.comparingLong(LevelSummary::getLastPlayed).reversed());
				recentWorlds = sorted.size() > 5 ? List.copyOf(sorted.subList(0, 5)) : List.copyOf(sorted);
			});
		} catch (Exception e) {
			worldsLoading = false;
			VantaClient.LOG.warn("[VANTA] Could not list worlds", e);
		}
	}

	public static void openWorld(Minecraft mc, LevelSummary world, Runnable onCancel) {
		clearPings();
		mc.createWorldOpenFlows().openWorld(world.getLevelId(), onCancel);
	}

	public static String ago(long time) {
		if (time <= 0) return "";
		long s = (System.currentTimeMillis() - time) / 1000;
		if (s < 60) return "just now";
		if (s < 3600) return (s / 60) + "m ago";
		if (s < 86400) return (s / 3600) + "h ago";
		return (s / 86400) + "d ago";
	}
}
