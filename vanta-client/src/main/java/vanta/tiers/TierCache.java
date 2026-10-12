package vanta.tiers;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import vanta.client.VantaClient;

/**
 * Looks players up on a tier list, one request at a time (a few per second at most), and
 * remembers the answers for 30 minutes. Players that aren't ranked are remembered too, so
 * they aren't asked for again and again.
 */
public final class TierCache {
	public enum State { LOADING, RANKED, UNRANKED, ERROR }

	public record Entry(State state, Map<String, TierParser.Tier> tiers, long time) {
	}

	private static final long KEEP_MS = 30 * 60_000L, RETRY_MS = 2 * 60_000L;
	private static final int MAX_ENTRIES = 3000;
	private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
	private static final Set<String> QUEUED = ConcurrentHashMap.newKeySet();
	private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "VANTA tier lookup");
		t.setDaemon(true);
		return t;
	});
	private static HttpClient http;
	private static volatile String lastProblem = "";
	private static volatile int lookups;

	private TierCache() {
	}

	public static String lastProblem() { return lastProblem; }
	public static int lookups() { return lookups; }

	public static void clear() {
		CACHE.clear();
		lastProblem = "";
	}

	public static int rankedCount() {
		int n = 0;
		for (Entry e : CACHE.values()) if (e.state() == State.RANKED) n++;
		return n;
	}

	/** The cached answer (or null) for this player on this list; starts a lookup when needed. */
	public static Entry get(String listId, String apiTemplate, UUID uuid, String name) {
		String key = listId + ":" + uuid;
		Entry e = CACHE.get(key);
		long now = System.currentTimeMillis();
		boolean stale = e == null
				|| (e.state() == State.ERROR && now - e.time() > RETRY_MS)
				|| ((e.state() == State.RANKED || e.state() == State.UNRANKED) && now - e.time() > KEEP_MS);
		if (stale && QUEUED.size() < 200 && QUEUED.add(key)) {
			if (e == null) CACHE.put(key, new Entry(State.LOADING, Collections.emptyMap(), now));
			String url = url(apiTemplate, uuid, name);
			EXEC.execute(() -> fetch(key, url));
		}
		if (CACHE.size() > MAX_ENTRIES) CACHE.clear();
		return CACHE.get(key);
	}

	/** Builds the request URL: placeholders {uuid}, {uuid_nodash} and {name}, or /v2/profile/{uuid} appended. */
	static String url(String template, UUID uuid, String name) {
		String t = template.strip();
		if (!t.contains("{")) t = (t.endsWith("/") ? t.substring(0, t.length() - 1) : t) + "/v2/profile/{uuid}";
		return t.replace("{uuid}", uuid.toString())
				.replace("{uuid_nodash}", uuid.toString().replace("-", ""))
				.replace("{name}", URLEncoder.encode(name == null ? "" : name, StandardCharsets.UTF_8));
	}

	private static void fetch(String key, String url) {
		try {
			if (http == null) {
				http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();
			}
			HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
					.header("User-Agent", "VANTA-Client").header("Accept", "application/json").GET().build();
			HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
			lookups++;
			long now = System.currentTimeMillis();
			if (res.statusCode() == 404) {
				CACHE.put(key, new Entry(State.UNRANKED, Collections.emptyMap(), now));
			} else if (res.statusCode() == 200) {
				Map<String, TierParser.Tier> tiers = TierParser.parse(res.body());
				CACHE.put(key, new Entry(tiers.isEmpty() ? State.UNRANKED : State.RANKED, tiers, now));
				lastProblem = "";
			} else {
				CACHE.put(key, new Entry(State.ERROR, Collections.emptyMap(), now));
				lastProblem = "The tier list answered HTTP " + res.statusCode() + " - check its API address";
			}
			Thread.sleep(250); // stay polite: at most ~4 lookups a second
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			CACHE.put(key, new Entry(State.ERROR, Collections.emptyMap(), System.currentTimeMillis()));
			lastProblem = "Can't reach the tier list (" + e.getClass().getSimpleName() + ") - check its API address";
			VantaClient.LOG.debug("[VANTA] Tier lookup failed for {}", url, e);
		} finally {
			QUEUED.remove(key);
		}
	}
}
