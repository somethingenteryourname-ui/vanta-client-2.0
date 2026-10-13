package vanta.hud.widgets;

import java.util.ArrayList;
import java.util.List;

import vanta.combat.SessionStats;
import vanta.config.BoolSetting;
import vanta.hud.TextHudModule;
import vanta.modules.Category;

/** Session Stats: play time, kills, deaths, K/D, streaks, hits and totem pops this session. */
public final class SessionStatsWidget extends TextHudModule {
	private final BoolSetting showTime = setting(new BoolSetting(key("time"), "Play time", "How long this session has lasted.", true));
	private final BoolSetting showKills = setting(new BoolSetting(key("kills"), "Kills", "Players who died soon after your hit.", true));
	private final BoolSetting showDeaths = setting(new BoolSetting(key("deaths"), "Deaths", "Times you died.", true));
	private final BoolSetting showKd = setting(new BoolSetting(key("kd"), "K/D", "Kills divided by deaths.", true));
	private final BoolSetting showStreak = setting(new BoolSetting(key("streak"), "Kill streak", "Current and best streak.", true));
	private final BoolSetting showPops = setting(new BoolSetting(key("pops"), "Totem pops", "Pops you caused and pops you took.", true));
	private final BoolSetting showHits = setting(new BoolSetting(key("hits"), "Hits", "Hits you landed on players.", false));
	public final BoolSetting resetOnJoin = setting(new BoolSetting(key("reset_on_join"), "Reset on new server",
			"Start fresh stats when you join a different server.", true));
	private String last = "";

	public SessionStatsWidget() {
		super("session_stats", "Session Stats", "Play time, kills, deaths, K/D, streaks, hits and totem pops this session.",
				Category.PVP, false, 0, 300);
		instance = this;
	}

	private static SessionStatsWidget instance;

	/** Whether stats start fresh on a new server (default yes, also while the widget is hidden). */
	public static boolean resetOnJoin() {
		return instance == null || instance.resetOnJoin.on();
	}

	@Override
	public void update() {
		List<String> l = new ArrayList<>(), v = new ArrayList<>();
		if (showTime.on()) { l.add("Session"); v.add(SessionStats.time(SessionStats.sessionMs())); }
		if (showKills.on()) { l.add("Kills"); v.add(String.valueOf(SessionStats.kills())); }
		if (showDeaths.on()) { l.add("Deaths"); v.add(String.valueOf(SessionStats.deaths())); }
		if (showKd.on()) { l.add("K/D"); v.add(SessionStats.kd()); }
		if (showStreak.on()) { l.add("Streak"); v.add(SessionStats.streak() + " (best " + SessionStats.bestStreak() + ")"); }
		if (showPops.on()) { l.add("Pops"); v.add(SessionStats.popsGiven() + " given / " + SessionStats.popsTaken() + " taken"); }
		if (showHits.on()) { l.add("Hits"); v.add(String.valueOf(SessionStats.hits())); }
		String key = String.join("|", l) + "#" + String.join("|", v);
		if (key.equals(last)) return;
		last = key;
		setLines(l.toArray(new String[0]), v.toArray(new String[0]));
	}
}
