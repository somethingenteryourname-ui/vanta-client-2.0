package vanta.social;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

import vanta.client.VantaClient;
import vanta.modules.impl.ClientBadgeModule;

/**
 * Optional presence server, for seeing VANTA badges on servers that don't run the bridge
 * plugin. Off unless you enter a URL in Client Badge settings (a free Cloudflare Worker
 * you host yourself; the code is in the presence-server folder). Every minute it sends
 * your UUID and the UUIDs in the tab list, and gets back which of them use VANTA.
 * Nothing else (no names, no server address, no chat) is sent.
 */
public final class PresenceClient {
	private static final long INTERVAL_MS = 60_000L;
	private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "VANTA presence");
		t.setDaemon(true);
		return t;
	});
	private static HttpClient http;
	private static long nextAt;
	private static int lastPlayerCount = -1;
	private static volatile boolean busy;
	private static volatile String lastError = "";

	private PresenceClient() {
	}

	public static String status() {
		String url = ClientBadgeModule.presenceUrl();
		if (url.isEmpty()) return "Off (no presence server set)";
		return lastError.isEmpty() ? "Connected" : lastError;
	}

	static void tick(Minecraft mc) {
		String url = ClientBadgeModule.presenceUrl();
		ClientPacketListener conn = mc.getConnection();
		if (url.isEmpty() || conn == null || mc.player == null || busy) return;
		long now = System.currentTimeMillis();
		int players = conn.getOnlinePlayers().size();
		boolean tabChanged = lastPlayerCount >= 0 && players != lastPlayerCount && now > nextAt - INTERVAL_MS + 15_000L;
		if (now < nextAt && !tabChanged) return;
		nextAt = now + INTERVAL_MS;
		lastPlayerCount = players;

		JsonObject body = new JsonObject();
		if (ClientBadgeModule.shares()) {
			body.addProperty("self", mc.player.getUUID().toString());
			String[] look = vanta.cosmetics.CosmeticsModule.shared();
			JsonObject cos = new JsonObject();
			cos.addProperty("cape", look[0]);
			cos.addProperty("trail", look[1]);
			body.add("cosmetics", cos);
		}
		JsonArray check = new JsonArray();
		int n = 0;
		for (PlayerInfo info : conn.getOnlinePlayers()) {
			if (n++ >= 500) break;
			UUID id = info.getProfile().id();
			if (!id.equals(mc.player.getUUID())) check.add(id.toString());
		}
		body.add("check", check);
		String json = body.toString();
		busy = true;
		EXEC.execute(() -> {
			try {
				if (http == null) http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
				HttpRequest req = HttpRequest.newBuilder(URI.create(url.endsWith("/") ? url + "v1/presence" : url + "/v1/presence"))
						.timeout(Duration.ofSeconds(8)).header("Content-Type", "application/json").header("User-Agent", "VANTA-Client")
						.POST(HttpRequest.BodyPublishers.ofString(json)).build();
				HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
				if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
				JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
				List<UUID> users = new ArrayList<>();
				if (o.has("users") && o.get("users").isJsonArray()) {
					for (JsonElement e : o.getAsJsonArray("users")) {
						try {
							users.add(UUID.fromString(e.getAsString()));
						} catch (RuntimeException ignored) {
						}
					}
				}
				mc.execute(() -> VantaUsers.setPresence(users));
				if (o.has("cosmetics") && o.get("cosmetics").isJsonObject()) {
					for (var e : o.getAsJsonObject("cosmetics").entrySet()) {
						try {
							UUID id = UUID.fromString(e.getKey());
							JsonObject c = e.getValue().getAsJsonObject();
							String cape = c.has("cape") ? c.get("cape").getAsString() : "none";
							String trail = c.has("trail") ? c.get("trail").getAsString() : "none";
							mc.execute(() -> vanta.cosmetics.Cosmetics.setOther(id, cape, trail));
						} catch (RuntimeException ignored) {
							// bad entry
						}
					}
				}
				lastError = "";
			} catch (Exception e) {
				lastError = "Can't reach the presence server (" + e.getClass().getSimpleName() + ")";
				VantaClient.LOG.debug("[VANTA] Presence server request failed", e);
			} finally {
				busy = false;
			}
		});
	}
}
