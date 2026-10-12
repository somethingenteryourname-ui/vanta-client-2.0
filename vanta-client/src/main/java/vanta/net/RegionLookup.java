package vanta.net;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;

import vanta.client.VantaClient;
import vanta.config.BoolSetting;
import vanta.config.SettingRegistry;
import vanta.gui.MenuPage;

/**
 * Works out which region the server you're on is in (NA East, NA Central, EU West...).
 * <ol>
 * <li>If the server address says it (na.example.net, eu-play.example.com, ...), that wins.</li>
 * <li>Otherwise, if online lookup is on, the server's IP (only the server's, nothing about
 * you) is looked up once at ipwho.is over HTTPS and the result is cached.</li>
 * </ol>
 * Servers behind DDoS protection (TCPShield, Cloudflare...) report the protection network's
 * nearest location, so the result is approximate.
 */
public final class RegionLookup {
	public static final BoolSetting ONLINE = SettingRegistry.register(new BoolSetting("region.online_lookup", "Online region lookup",
			"Look up the server's region from its IP at ipwho.is (only the server IP is sent, once per server). Off = use the server address only.",
			true), MenuPage.SETTINGS);

	private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
	private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "VANTA region lookup");
		t.setDaemon(true);
		return t;
	});
	private static HttpClient http;
	private static String currentKey;
	private static String current = "";

	private RegionLookup() {
	}

	public static void init() {
	}

	/** Region label for the server you're connected to ("" while unknown). Cheap; call any time. */
	public static String current() {
		Minecraft mc = Minecraft.getInstance();
		ServerData data = mc.getCurrentServer();
		if (data == null) return mc.hasSingleplayerServer() ? "Local" : "";
		String host = data.ip == null ? "" : data.ip.toLowerCase(Locale.ROOT);
		if (!host.equals(currentKey)) {
			currentKey = host;
			current = "";
			String fromName = fromHostname(host);
			if (fromName != null) {
				current = fromName;
			} else if (ONLINE.on()) {
				String ip = remoteIp(mc);
				if (ip != null) lookup(ip);
			}
		}
		if (current.isEmpty() && ONLINE.on()) {
			String ip = remoteIp(mc);
			if (ip != null) {
				String cached = CACHE.get(ip);
				if (cached != null) current = cached;
				else lookup(ip);
			}
		}
		return current;
	}

	private static String remoteIp(Minecraft mc) {
		ClientPacketListener conn = mc.getConnection();
		if (conn == null) return null;
		SocketAddress addr = conn.getConnection().getRemoteAddress();
		if (addr instanceof InetSocketAddress isa && isa.getAddress() != null) {
			InetAddress a = isa.getAddress();
			if (a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress()) {
				CACHE.put(a.getHostAddress(), "LAN");
			}
			return a.getHostAddress();
		}
		return null;
	}

	private static void lookup(String ip) {
		if (CACHE.containsKey(ip)) return;
		CACHE.put(ip, "");
		EXEC.execute(() -> {
			try {
				if (http == null) http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
				HttpRequest req = HttpRequest.newBuilder(URI.create("https://ipwho.is/" + ip))
						.timeout(Duration.ofSeconds(6)).header("User-Agent", "VANTA-Client").GET().build();
				HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
				JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
				if (!o.has("success") || !o.get("success").getAsBoolean()) return;
				String tz = o.has("timezone") && o.get("timezone").isJsonObject() && o.getAsJsonObject("timezone").has("id")
						? o.getAsJsonObject("timezone").get("id").getAsString() : "";
				String cc = o.has("country_code") ? o.get("country_code").getAsString() : "";
				String cont = o.has("continent_code") ? o.get("continent_code").getAsString() : "";
				double lon = o.has("longitude") ? o.get("longitude").getAsDouble() : Double.NaN;
				CACHE.put(ip, fromTimezone(tz, cc, cont, lon));
			} catch (Exception e) {
				VantaClient.LOG.info("[VANTA] Region lookup failed: {}", e.getMessage());
			}
		});
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
