package vanta.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Session stats for the Session Stats widget: play time, kills, deaths, streaks, hits and
 * totem pops given and taken. Kills and pops count when the player died or popped within
 * a few seconds of your last hit on them. Everything is read from what your client
 * already sees; nothing is sent.
 */
public final class SessionStats {
	static final long CREDIT_MS = 8000;

	private static final Map<UUID, Long> LAST_HIT = new HashMap<>();
	private static long start = System.currentTimeMillis();
	private static int kills, deaths, streak, bestStreak, hits, popsGiven, popsTaken;
	private static boolean wasDead;
	private static Object lastServer;

	private SessionStats() {
	}

	public static void reset() {
		start = System.currentTimeMillis();
		kills = deaths = streak = bestStreak = hits = popsGiven = popsTaken = 0;
		LAST_HIT.clear();
	}

	public static void onAttack(Entity target) {
		if (target instanceof Player p) {
			LAST_HIT.put(p.getUUID(), System.currentTimeMillis());
			hits++;
		}
	}

	/** Entity event 35 = totem pop, 3 = death. */
	public static void onEntityEvent(Entity entity, byte event, long now) {
		if (!(entity instanceof Player p)) return;
		Minecraft mc = Minecraft.getInstance();
		boolean self = mc != null && mc.player != null && p.getUUID().equals(mc.player.getUUID());
		if (event == 35) {
			if (self) popsTaken++;
			else if (recentlyHit(p.getUUID(), now)) popsGiven++;
		} else if (event == 3 && !self && recentlyHit(p.getUUID(), now)) {
			kills++;
			streak++;
			bestStreak = Math.max(bestStreak, streak);
			LAST_HIT.remove(p.getUUID());
		}
	}

	static boolean recentlyHit(UUID id, long now) {
		Long t = LAST_HIT.get(id);
		return t != null && now - t <= CREDIT_MS;
	}

	/** Every client tick: counts your deaths and resets when you join another server (if asked). */
	public static void tick(Minecraft mc, boolean resetOnJoin) {
		Object server = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : mc.hasSingleplayerServer() ? "singleplayer" : null;
		if (server != null && !server.equals(lastServer)) {
			if (resetOnJoin && lastServer != null) reset();
			lastServer = server;
		}
		LocalPlayer p = mc.player;
		boolean dead = p != null && p.isDeadOrDying();
		if (dead && !wasDead) {
			deaths++;
			streak = 0;
		}
		wasDead = dead;
	}

	public static long sessionMs() { return System.currentTimeMillis() - start; }
	public static int kills() { return kills; }
	public static int deaths() { return deaths; }
	public static int streak() { return streak; }
	public static int bestStreak() { return bestStreak; }
	public static int hits() { return hits; }
	public static int popsGiven() { return popsGiven; }
	public static int popsTaken() { return popsTaken; }

	public static String kd() {
		if (deaths == 0) return kills == 0 ? "-" : kills + ".00";
		return String.format(java.util.Locale.ROOT, "%.2f", kills / (double) deaths);
	}

	public static String time(long ms) {
		long s = ms / 1000;
		long h = s / 3600, m = (s / 60) % 60;
		return h > 0 ? h + "h " + m + "m" : m + "m " + (s % 60) + "s";
	}
}
