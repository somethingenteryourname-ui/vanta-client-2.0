package vanta.net;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;

import vanta.client.VantaClient;
import vanta.config.BoolSetting;
import vanta.config.ConfigManager;
import vanta.config.SettingRegistry;
import vanta.gui.MenuPage;

/**
 * Works out which region the server you're on is in (NA East, NA Central, EU West...).
 * <ol>
 * <li>If the server address names a specific region (nae.example.net, dal.example.gg), that wins.</li>
 * <li>Otherwise the IP you're actually connected to is looked up (only the server's IP,
 * nothing about you). Several free services are tried in turn, failures are retried
 * with a growing delay, and answers are saved in config/vanta/regions.json so the next
 * visit shows the region instantly.</li>
 * <li>A broad hint from the address (na., eu.) is shown until the lookup refines it.</li>
 * </ol>
 * Servers behind DDoS protection (TCPShield, Cloudflare...) report the protection
 * network's location; those are marked as a proxy in the F3 panel.
 */
public final class RegionLookup {
	public static final BoolSetting ONLINE = SettingRegistry.register(new BoolSetting("region.online_lookup", "Online region lookup",
			"Look up the server's region from its IP (only the server IP is sent; answers are saved so each server is looked up "
			+ "about once a month). Off = use the server address only.", true), MenuPage.SETTINGS);

	private static final long CACHE_DAYS = 30;
	private static final long[] RETRY_MS = {5_000, 20_000, 60_000, 300_000};
	/** Owner-name fragment -> display name of DDoS-protection networks (their IPs show the edge location). */
	private static final String[][] PROXY_ORGS = {{"tcpshield", "TCPShield"}, {"cloudflare", "Cloudflare"}, {"neoprotect", "NeoProtect"},
			{"path network", "Path.net"}, {"cosmicguard", "CosmicGuard"}, {"infinity-filter", "Infinity Filter"}, {"infinityfilter", "Infinity Filter"},
			{"papyrus", "Papyrus"}, {"ddos-guard", "DDoS-Guard"}, {"x4b", "X4B"}, {"corero", "Corero"}};

	/** ip -> region ("" = being looked up). */
	private static final Map<String, Result> CACHE = new ConcurrentHashMap<>();
	private static final Map<String, Integer> FAILURES = new ConcurrentHashMap<>();
	private static final Map<String, Long> RETRY_AT = new ConcurrentHashMap<>();
	private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "VANTA region lookup");
		t.setDaemon(true);
		return t;
	});
	private static HttpClient http;
	private static boolean diskLoaded;
	private static String currentServer;
	private static String currentIp;
	private static String hint;
	private static volatile String status = "";

	/** A lookup answer. {@code proxy} is the protection network's name when the IP belongs to one. */
	public record Result(String region, String proxy, long time) {
	}

	private RegionLookup() {
	}

	public static void init() {
	}

	/** Region label for the server you're connected to ("" while unknown). Cheap; call any time from the render thread. */
	public static String current() {
		Minecraft mc = Minecraft.getInstance();
		ServerData data = mc.getCurrentServer();
		if (data == null) {
			currentServer = null;
			return mc.hasSingleplayerServer() ? "Local" : "";
		}
		String host = data.ip == null ? "" : data.ip.toLowerCase(Locale.ROOT);
		if (!host.equals(currentServer)) {
			currentServer = host;
			currentIp = null;
			hint = fromHostname(host);
		}
		if (hint != null && !isBroad(hint)) return hint;
		if (!ONLINE.on()) return hint == null ? "" : hint;

		if (currentIp == null) currentIp = remoteIp(mc);
		if (currentIp == null) return hint == null ? "" : hint;
		if (currentIp.equals("LAN")) return "LAN";

		ensureDisk();
		Result r = CACHE.get(currentIp);
		if (r != null && !r.region().isEmpty() && !expired(r)) return r.region();
		long retry = RETRY_AT.getOrDefault(currentIp, 0L);
		if ((r == null || expired(r)) && System.currentTimeMillis() >= retry) lookup(currentIp);
		return hint == null ? "" : hint;
	}

	/** Extra detail for the F3 panel, e.g. "via TCPShield (proxy location)" or the lookup state. */
	public static String detail() {
		if (currentIp == null || currentIp.equals("LAN")) return "";
		Result r = CACHE.get(currentIp);
		if (r != null && r.proxy() != null && !r.proxy().isEmpty()) return "via " + r.proxy() + " (proxy location)";
		return status;
	}

	/** True for labels that only name a continent ("NA", "EU"), which a lookup can refine. */
	public static boolean isBroad(String region) {
		return region.equals("NA") || region.equals("EU") || region.equals("Asia") || region.equals("Oceania")
				|| region.equals("South America") || region.equals("Unknown");
	}

	private static boolean expired(Result r) {
		return System.currentTimeMillis() - r.time() > CACHE_DAYS * 86_400_000L;
	}

	private static String remoteIp(Minecraft mc) {
		ClientPacketListener conn = mc.getConnection();
		if (conn == null || conn.getConnection() == null) return null;
		SocketAddress addr = conn.getConnection().getRemoteAddress();
		if (!(addr instanceof InetSocketAddress isa)) return null;
		InetAddress a = isa.getAddress();
		if (a == null) return null; // unresolved (rare); try again next frame
		if (a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress()) return "LAN";
		return normalize(a.getHostAddress());
	}

	/** Strips IPv6 scope ids and unwraps IPv4-mapped IPv6 addresses. */
	static String normalize(String ip) {
		int pct = ip.indexOf('%');
		if (pct >= 0) ip = ip.substring(0, pct);
		String lower = ip.toLowerCase(Locale.ROOT);
		if (lower.startsWith("::ffff:") && lower.indexOf('.') > 0) return lower.substring(7);
		return lower;
	}

	// ------------------------------------------------------------------ lookup (background thread)

	private static void lookup(String ip) {
		Result existing = CACHE.get(ip);
		if (existing != null && existing.region().isEmpty()) return; // already running
		CACHE.put(ip, new Result("", null, System.currentTimeMillis()));
		status = "looking up...";
		EXEC.execute(() -> {
			Result r = null;
			for (Provider p : Provider.values()) {
				try {
					r = p.query(client(), ip);
					if (r != null) break;
				} catch (Exception e) {
					VantaClient.LOG.debug("[VANTA] Region lookup via {} failed: {}", p.name(), e.toString());
				}
			}
			if (r != null) {
				CACHE.put(ip, r);
				FAILURES.remove(ip);
				RETRY_AT.remove(ip);
				status = "";
				saveDisk();
			} else {
				int n = FAILURES.merge(ip, 1, Integer::sum);
				long wait = RETRY_MS[Math.min(n - 1, RETRY_MS.length - 1)];
				RETRY_AT.put(ip, System.currentTimeMillis() + wait);
				CACHE.remove(ip);
				status = "lookup failed, retrying in " + wait / 1000 + "s";
				VantaClient.LOG.info("[VANTA] Region lookup for the server failed {} time(s); retrying in {}s", n, wait / 1000);
			}
		});
	}

	private static HttpClient client() {
		if (http == null) {
			http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NORMAL).build();
		}
		return http;
	}

	private static JsonObject getJson(HttpClient http, String url) throws Exception {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
				.header("User-Agent", "VANTA-Client").header("Accept", "application/json").GET().build();
		HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
		JsonElement el = JsonParser.parseString(res.body());
		if (!el.isJsonObject()) throw new IllegalStateException("not JSON");
		return el.getAsJsonObject();
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}

	private static double num(JsonObject o, String key) {
		JsonElement e = o.get(key);
		try {
			return e != null && e.isJsonPrimitive() ? e.getAsDouble() : Double.NaN;
		} catch (RuntimeException ex) {
			return Double.NaN;
		}
	}

	/** The free services tried in order. Each returns null if it has no usable answer. */
	private enum Provider {
		IPWHO {
			@Override Result query(HttpClient http, String ip) throws Exception {
				JsonObject o = getJson(http, "https://ipwho.is/" + ip);
				if (o.has("success") && !o.get("success").getAsBoolean()) return null;
				String tz = o.has("timezone") && o.get("timezone").isJsonObject() ? str(o.getAsJsonObject("timezone"), "id") : "";
				String org = o.has("connection") && o.get("connection").isJsonObject()
						? str(o.getAsJsonObject("connection"), "org") + " " + str(o.getAsJsonObject("connection"), "isp") : "";
				return result(tz, str(o, "country_code"), str(o, "continent_code"), num(o, "longitude"), org);
			}
		},
		GEOJS {
			@Override Result query(HttpClient http, String ip) throws Exception {
				JsonObject o = getJson(http, "https://get.geojs.io/v1/ip/geo/" + ip + ".json");
				return result(str(o, "timezone"), str(o, "country_code"), str(o, "continent_code"), num(o, "longitude"),
						str(o, "organization_name"));
			}
		},
		IPAPI_CO {
			@Override Result query(HttpClient http, String ip) throws Exception {
				JsonObject o = getJson(http, "https://ipapi.co/" + ip + "/json/");
				if (o.has("error") && o.get("error").getAsBoolean()) return null;
				return result(str(o, "timezone"), str(o, "country_code"), str(o, "continent_code"), num(o, "longitude"), str(o, "org"));
			}
		},
		IP_API {
			@Override Result query(HttpClient http, String ip) throws Exception {
				// Plain HTTP on the free tier; only the server's IP is sent.
				JsonObject o = getJson(http, "http://ip-api.com/json/" + ip + "?fields=status,countryCode,continentCode,lon,timezone,org,isp");
				if (!"success".equals(str(o, "status"))) return null;
				return result(str(o, "timezone"), str(o, "countryCode"), str(o, "continentCode"), num(o, "lon"),
						str(o, "org") + " " + str(o, "isp"));
			}
		};

		abstract Result query(HttpClient http, String ip) throws Exception;
	}

	static Result result(String tz, String country, String continent, double lon, String org) {
		if (tz.isEmpty() && country.isEmpty() && continent.isEmpty()) return null;
		String region = fromTimezone(tz, country, continent, lon);
		if (region == null || region.equals("Unknown")) return null;
		return new Result(region, proxyName(org), System.currentTimeMillis());
	}

	/** Name of a DDoS-protection network if the IP's owner is one, else null. */
	static String proxyName(String org) {
		if (org == null) return null;
		String o = org.toLowerCase(Locale.ROOT);
		for (String[] p : PROXY_ORGS) {
			if (o.contains(p[0])) return p[1];
		}
		return null;
	}

	// ------------------------------------------------------------------ saved answers

	private static Path file() {
		return ConfigManager.get().directory().resolve("regions.json");
	}

	private static synchronized void ensureDisk() {
		if (diskLoaded) return;
		diskLoaded = true;
		try {
			JsonObject root = ConfigManager.readObject(file());
			if (root == null) return;
			for (Map.Entry<String, JsonElement> e : root.entrySet()) {
				if (!e.getValue().isJsonObject()) continue;
				JsonObject o = e.getValue().getAsJsonObject();
				String region = str(o, "region");
				if (region.isEmpty()) continue;
				String proxy = str(o, "proxy");
				long time = o.has("time") ? o.get("time").getAsLong() : 0;
				CACHE.putIfAbsent(e.getKey(), new Result(region, proxy.isEmpty() ? null : proxy, time));
			}
		} catch (RuntimeException e) {
			VantaClient.LOG.debug("[VANTA] Could not read saved regions", e);
		}
	}

	private static synchronized void saveDisk() {
		JsonObject root = new JsonObject();
		CACHE.forEach((ip, r) -> {
			if (r.region().isEmpty() || expired(r)) return;
			JsonObject o = new JsonObject();
			o.addProperty("region", r.region());
			if (r.proxy() != null) o.addProperty("proxy", r.proxy());
			o.addProperty("time", r.time());
			root.add(ip, o);
		});
		ConfigManager.writeObject(file(), root);
	}

	/** Region from a timezone like "America/Chicago", with country/continent/longitude fallbacks. */
	public static String fromTimezone(String tz, String country, String continent, double longitude) {
		String t = tz == null ? "" : tz;
		if (t.startsWith("America/")) {
			String city = t.substring(8);
			if (city.matches("New_York|Detroit|Toronto|Montreal|Nassau|Iqaluit|Indiana/.*|Kentucky/.*|Halifax|Moncton|Glace_Bay|Puerto_Rico")) return "NA East";
			if (city.matches("Chicago|Winnipeg|Menominee|North_Dakota/.*|Regina|Matamoros|Monterrey|Merida")) return "NA Central";
			if (city.matches("Denver|Phoenix|Boise|Edmonton|Yellowknife|Cambridge_Bay|Ojinaga|Chihuahua")) return "NA West";
			if (city.matches("Los_Angeles|Vancouver|Tijuana|Whitehorse|Dawson|Anchorage|Juneau|Sitka|Nome")) return "NA West";
			if (city.matches("Mexico_City|Cancun|Bogota|Lima|Panama|Guatemala|Costa_Rica|El_Salvador|Tegucigalpa|Managua|Havana|Jamaica|Santo_Domingo")) return "Central America";
			if (city.matches("Sao_Paulo|Buenos_Aires|Argentina/.*|Santiago|Montevideo|Caracas|Asuncion|La_Paz|Fortaleza|Recife|Bahia|Manaus|Belem")) return "South America";
		}
		if (t.startsWith("Europe/")) {
			String city = t.substring(7);
			if (city.matches("London|Dublin|Lisbon|Madrid|Paris|Brussels|Amsterdam|Luxembourg|Guernsey|Jersey|Isle_of_Man")) return "EU West";
			if (city.matches("Moscow|Helsinki|Kyiv|Kiev|Riga|Vilnius|Tallinn|Bucharest|Sofia|Athens|Istanbul|Minsk|Chisinau")) return "EU East";
			return "EU Central";
		}
		if (t.startsWith("Asia/")) {
			String city = t.substring(5);
			if (city.matches("Dubai|Riyadh|Qatar|Bahrain|Kuwait|Tehran|Baghdad|Jerusalem|Tel_Aviv|Amman|Beirut")) return "Middle East";
			if (city.matches("Kolkata|Calcutta|Mumbai|Karachi|Dhaka|Colombo")) return "Asia (South)";
			if (city.matches("Tokyo|Seoul")) return "Asia (JP/KR)";
			if (city.matches("Singapore|Kuala_Lumpur|Jakarta|Bangkok|Manila|Ho_Chi_Minh")) return "Asia (SEA)";
			return "Asia";
		}
		if (t.startsWith("Australia/")) return "Oceania (AU)";
		if (t.startsWith("Pacific/Auckland")) return "Oceania (NZ)";
		if (t.startsWith("Africa/")) return "Africa";
		if ("US".equals(country) || "CA".equals(country)) {
			if (Double.isNaN(longitude)) return "NA";
			if (longitude > -87) return "NA East";
			if (longitude > -102) return "NA Central";
			return "NA West";
		}
		return switch (continent == null ? "" : continent) {
			case "NA" -> "NA";
			case "SA" -> "South America";
			case "EU" -> "EU";
			case "AS" -> "Asia";
			case "OC" -> "Oceania";
			case "AF" -> "Africa";
			default -> "Unknown";
		};
	}

	/** Region from words in the server address, or null if it doesn't say. */
	public static String fromHostname(String host) {
		if (host == null || host.isEmpty()) return null;
		String h = host.toLowerCase(Locale.ROOT);
		int colon = h.indexOf(':');
		if (colon >= 0) h = h.substring(0, colon);
		for (String p : h.split("[.\\-_]")) {
			switch (p) {
				case "nae", "naeast", "useast", "use", "east", "nyc", "ny", "virginia", "ash", "atl", "mia", "tor", "mtl" -> {
					return "NA East";
				}
				case "nac", "nacentral", "uscentral", "central", "dal", "dallas", "chi", "chicago", "kc" -> {
					return "NA Central";
				}
				case "naw", "nawest", "uswest", "usw", "west", "lax", "sea", "sjc", "phx", "den" -> {
					return "NA West";
				}
				case "na", "us" -> {
					return "NA";
				}
				case "eu" -> {
					return "EU";
				}
				case "euw", "euwest", "lon", "london", "uk", "ams", "par", "nl" -> {
					return "EU West";
				}
				case "euc", "fra", "frankfurt", "de" -> {
					return "EU Central";
				}
				case "asia", "sg", "sgp", "jp", "tyo", "hk", "kr" -> {
					return "Asia";
				}
				case "au", "oce", "syd", "sydney" -> {
					return "Oceania (AU)";
				}
				case "br", "brazil", "sao" -> {
					return "South America";
				}
				default -> {
				}
			}
		}
		return null;
	}
}
