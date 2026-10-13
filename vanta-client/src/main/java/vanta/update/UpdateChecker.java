package vanta.update;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import vanta.client.VantaClient;
import vanta.client.VantaInfo;
import vanta.config.BoolSetting;
import vanta.config.TextSetting;
import vanta.modules.Category;
import vanta.modules.Module;
import vanta.notify.NotificationsModule;

/**
 * Checks GitHub Releases for a newer VANTA once per game start (and when you press
 * "Check now"). Only the public release info of the repository you set is requested.
 */
public final class UpdateChecker extends Module {
	private static UpdateChecker instance;

	public final TextSetting repo = setting(new TextSetting(key("repo"), "GitHub repository",
			"Where VANTA releases are published, as owner/name.", "somethingenteryourname-ui/vanta-client-2.0", 120));
	public final BoolSetting notifyOnStart = setting(new BoolSetting(key("notify"), "Tell me about updates",
			"Show a notification when a newer version is out.", true));

	private volatile String status = "Not checked yet";
	private volatile String latest = "";
	private volatile String pageUrl = "";
	private volatile boolean checking;
	private boolean checkedThisRun;

	public UpdateChecker() {
		super("update_checker", "Update Checker", "Checks GitHub for a newer VANTA when the game starts and tells you about it.",
				Category.INTERFACE, true);
		instance = this;
	}

	public static UpdateChecker get() { return instance; }

	public String status() { return status; }
	public String pageUrl() { return pageUrl.isEmpty() ? "https://github.com/" + repo.get() + "/releases" : pageUrl; }

	@Override
	public void tick(net.minecraft.client.Minecraft mc) {
		if (!checkedThisRun && mc.screen != null) {
			checkedThisRun = true;
			check();
		}
	}

	/** Compares "1.2.10" style versions; true when {@code remote} is newer. Leading "v" and suffixes after "-" or "+" are ignored. */
	public static boolean newer(String remote, String local) {
		int[] a = parts(remote), b = parts(local);
		for (int i = 0; i < Math.max(a.length, b.length); i++) {
			int x = i < a.length ? a[i] : 0, y = i < b.length ? b[i] : 0;
			if (x != y) return x > y;
		}
		return false;
	}

	private static int[] parts(String v) {
		String s = v == null ? "" : v.strip();
		if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
		int cut = s.length();
		for (char c : new char[] {'-', '+', ' '}) {
			int i = s.indexOf(c);
			if (i >= 0) cut = Math.min(cut, i);
		}
		String[] p = s.substring(0, cut).split("\\.");
		int[] out = new int[p.length];
		for (int i = 0; i < p.length; i++) {
			try {
				out[i] = Integer.parseInt(p[i].replaceAll("\\D", ""));
			} catch (NumberFormatException e) {
				out[i] = 0;
			}
		}
		return out;
	}

	public void check() {
		if (checking) return;
		String r = repo.get().strip();
		if (!r.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
			status = "Set the repository as owner/name";
			return;
		}
		checking = true;
		status = "Checking...";
		Thread t = new Thread(() -> {
			try {
				HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).followRedirects(HttpClient.Redirect.NORMAL).build();
				HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + r + "/releases/latest"))
						.timeout(Duration.ofSeconds(8)).header("Accept", "application/vnd.github+json").header("User-Agent", "VANTA-Client").GET().build();
				HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
				if (res.statusCode() == 404) {
					status = "No releases published yet";
					return;
				}
				if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
				JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
				String tag = o.has("tag_name") ? o.get("tag_name").getAsString() : "";
				latest = tag;
				pageUrl = o.has("html_url") ? o.get("html_url").getAsString() : "";
				String mine = VantaInfo.vantaVersion();
				if (newer(tag, mine)) {
					status = "Version " + tag + " is available (you have " + mine + ")";
					if (notifyOnStart.on()) NotificationsModule.show(NotificationsModule.Kind.UPDATES, "VANTA " + tag + " is out", "Open the About page to download it.");
				} else {
					status = "Up to date (" + mine + ")";
				}
			} catch (Exception e) {
				status = "Couldn't check (" + e.getClass().getSimpleName() + ")";
				VantaClient.LOG.debug("[VANTA] Update check failed", e);
			} finally {
				checking = false;
			}
		}, "VANTA update check");
		t.setDaemon(true);
		t.start();
	}
}
