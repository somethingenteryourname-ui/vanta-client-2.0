package vanta.net;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.server.network.EventLoopGroupHolder;

import vanta.config.BoolSetting;
import vanta.config.IntSetting;
import vanta.config.SettingRegistry;
import vanta.gui.MenuPage;

/**
 * Measured ping. The number in the tab list is reported by the server: it's averaged
 * slowly (it can take a minute to drop after a lag spike) and includes the server's own
 * processing time, so it often reads higher than your real connection. This sends the
 * same tiny status ping the server list uses every few seconds and shows the real
 * round trip (median of the last 3). Falls back to the server's number when it can't measure.
 */
public final class LivePing {
	public static final BoolSetting LIVE = SettingRegistry.register(new BoolSetting("ping.live", "Measured ping",
			"Show your real round-trip time (a small status ping every few seconds, like the server list) instead of the "
			+ "server-reported number, which updates slowly and includes server lag.", true), MenuPage.SETTINGS);
	public static final IntSetting INTERVAL = SettingRegistry.register(new IntSetting("ping.interval", "Measure every",
			"How often ping is measured.", 10, 5, 60, 1, " s"), MenuPage.SETTINGS);

	private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "VANTA ping");
		t.setDaemon(true);
		return t;
	});
	private static final long[] samples = new long[3];
	private static int sampleCount;
	private static ServerStatusPinger pinger;
	private static String server;
	private static long nextAt;
	private static long lastSampleAt;
	private static volatile long sentAt;
	private static volatile boolean inFlight;
	private static int failures;

	private LivePing() {
	}

	public static void init() {
	}

	/** Client tick: schedule measurements and keep the ping connection moving. */
	public static void tick(Minecraft mc) {
		ServerData data = mc.getCurrentServer();
		if (!LIVE.on() || data == null || data.isRealm() || mc.level == null || mc.hasSingleplayerServer()) {
			if (server != null) reset();
			return;
		}
		if (!data.ip.equals(server)) {
			reset();
			server = data.ip;
			nextAt = System.currentTimeMillis() + 2_000;
		}
		if (pinger != null) pinger.tick();
		long now = System.currentTimeMillis();
		if (inFlight && now - sentAt > 8_000) { // no answer: count as a failure and try again later
			inFlight = false;
			failures++;
		}
		if (inFlight || now < nextAt) return;
		long interval = INTERVAL.value() * 1000L;
		nextAt = now + (failures >= 3 ? Math.max(60_000L, interval) : interval);
		if (pinger == null) pinger = new ServerStatusPinger();
		ServerStatusPinger p = pinger;
		ServerData probe = new ServerData("VANTA ping", data.ip, ServerData.Type.OTHER);
		boolean nativeTransport = mc.options.useNativeTransport();
		inFlight = true;
		sentAt = now;
		EXEC.execute(() -> {
			try {
				p.pingServer(probe, () -> { }, () -> mc.execute(() -> record(probe.ping)), EventLoopGroupHolder.remote(nativeTransport));
			} catch (Exception e) {
				inFlight = false;
				failures++;
			}
		});
	}

	private static void record(long ms) {
		inFlight = false;
		if (ms < 0 || ms > 10_000) return;
		failures = 0;
		samples[sampleCount % samples.length] = ms;
		sampleCount++;
		lastSampleAt = System.currentTimeMillis();
	}

	private static void reset() {
		if (pinger != null) pinger.removeAll();
		server = null;
		sampleCount = 0;
		inFlight = false;
		failures = 0;
		lastSampleAt = 0;
	}

	/** Measured ping in ms, or -1 if there's no recent measurement. */
	public static long measured() {
		if (sampleCount == 0 || System.currentTimeMillis() - lastSampleAt > 60_000L) return -1;
		int n = Math.min(sampleCount, samples.length);
		long[] copy = Arrays.copyOf(samples, n);
		Arrays.sort(copy);
		return copy[n / 2];
	}

	/** The server-reported ping from the player list, or -1. */
	public static int reported(Minecraft mc) {
		ClientPacketListener conn = mc.getConnection();
		if (conn == null || mc.player == null) return -1;
		PlayerInfo info = conn.getPlayerInfo(mc.player.getUUID());
		return info == null ? -1 : info.getLatency();
	}

	/** Best ping to show: measured when available, otherwise the server's number. -1 if none. */
	public static long current(Minecraft mc) {
		if (mc.hasSingleplayerServer()) return 0;
		long m = LIVE.on() ? measured() : -1;
		return m >= 0 ? m : reported(mc);
	}

	public static String text(Minecraft mc) {
		long p = current(mc);
		return p < 0 ? "-" : p + " ms";
	}
}
