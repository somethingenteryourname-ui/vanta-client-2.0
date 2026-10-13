package vanta.friends;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import vanta.config.BoolSetting;
import vanta.config.ColorSetting;
import vanta.config.TextSetting;
import vanta.modules.Category;
import vanta.modules.Module;
import vanta.notify.NotificationsModule;

/**
 * Friends list: a star next to friends in nametags and the tab list, a notification when
 * they join or leave the server you're on, and /friend commands. Stored on your computer
 * only; nothing is sent anywhere.
 */
public final class FriendsModule extends Module {
	private static FriendsModule instance;

	/** Friend names, comma separated (kept in your settings file). */
	public final TextSetting list = (TextSetting) setting(new TextSetting(key("list"), "Friends", "Your friends' names.", "", 4000)).hidden();
	public final ColorSetting color = setting(new ColorSetting(key("color"), "Friend color", "Color of the star next to friends.", 0xFF55FF7F, false));
	public final BoolSetting nametags = setting(new BoolSetting(key("nametags"), "Mark nametags", "Show a star in front of friends' nametags.", true));
	public final BoolSetting tabList = setting(new BoolSetting(key("tab_list"), "Mark tab list", "Show a star next to friends in the tab list.", true));
	public final BoolSetting notify = setting(new BoolSetting(key("notify"), "Join and leave alerts",
			"A notification when a friend joins or leaves the server you're on.", true));

	private Set<String> online = new HashSet<>();
	private Object connection;
	private boolean first;

	public FriendsModule() {
		super("friends", "Friends", "A friends list: stars on friends' nametags and in the tab list, join/leave alerts and /friend commands.",
				Category.QOL, true);
		instance = this;
	}

	public static FriendsModule get() { return instance; }

	public static FriendsModule active() { return instance != null && instance.isEnabled() ? instance : null; }

	public List<String> names() {
		List<String> out = new ArrayList<>();
		for (String s : list.get().split(",")) if (!s.isBlank()) out.add(s.strip());
		return out;
	}

	public static boolean isFriend(String name) {
		FriendsModule m = instance;
		if (m == null || name == null) return false;
		for (String n : m.names()) if (n.equalsIgnoreCase(name)) return true;
		return false;
	}

	/** Adds a friend. Returns an error message, or null when it worked. */
	public String add(String name) {
		String n = name == null ? "" : name.strip();
		if (!n.matches("[A-Za-z0-9_]{1,16}")) return "That isn't a valid Minecraft name.";
		if (isFriend(n)) return n + " is already your friend.";
		Set<String> all = new LinkedHashSet<>(names());
		all.add(n);
		String joined = String.join(",", all);
		if (joined.length() > 4000) return "Your friends list is full.";
		list.set(joined);
		return null;
	}

	public boolean remove(String name) {
		List<String> all = names();
		boolean removed = all.removeIf(n -> n.equalsIgnoreCase(name));
		if (removed) list.set(String.join(",", all));
		return removed;
	}

	/** True when this friend is in the current server's tab list. */
	public static boolean isOnline(String name) {
		FriendsModule m = instance;
		return m != null && m.online.contains(name.toLowerCase(Locale.ROOT));
	}

	public static MutableComponent star() {
		int rgb = instance == null ? 0x55FF7F : instance.color.argb() & 0xFFFFFF;
		return Component.literal("★").withColor(rgb);
	}

	/** "star + space + name" under an empty parent, so the name keeps its own style. */
	public static Component decorate(Component name) {
		return Component.empty().append(star()).append(Component.literal(" ")).append(name);
	}

	public static boolean markNametag(String name) {
		FriendsModule m = active();
		return m != null && m.nametags.on() && isFriend(name);
	}

	public static boolean markTab(String name) {
		FriendsModule m = active();
		return m != null && m.tabList.on() && isFriend(name);
	}

	@Override
	public void tick(Minecraft mc) {
		ClientPacketListener conn = mc.getConnection();
		if (conn != connection) {
			connection = conn;
			online = new HashSet<>();
			first = true;
		}
		if (conn == null || mc.player == null || mc.player.tickCount % 20 != 0) return;
		Set<String> friends = new HashSet<>();
		for (String n : names()) friends.add(n.toLowerCase(Locale.ROOT));
		Set<String> now = new HashSet<>();
		List<String> display = new ArrayList<>();
		for (PlayerInfo info : conn.getOnlinePlayers()) {
			String n = info.getProfile().name();
			if (n != null && friends.contains(n.toLowerCase(Locale.ROOT)) && !n.equalsIgnoreCase(mc.player.getName().getString())) {
				now.add(n.toLowerCase(Locale.ROOT));
				display.add(n);
			}
		}
		if (notify.on()) {
			if (first) {
				if (!display.isEmpty()) {
					NotificationsModule.show(NotificationsModule.Kind.FRIENDS, display.size() == 1 ? "1 friend online" : display.size() + " friends online",
							String.join(", ", display));
				}
			} else {
				for (String n : display) if (!online.contains(n.toLowerCase(Locale.ROOT))) NotificationsModule.show(NotificationsModule.Kind.FRIENDS, n + " joined", "Your friend is on this server.");
				for (String n : online) if (!now.contains(n)) NotificationsModule.show(NotificationsModule.Kind.FRIENDS, properName(n) + " left", "Your friend left the server.");
			}
		}
		first = false;
		online = now;
	}

	private String properName(String lower) {
		for (String n : names()) if (n.equalsIgnoreCase(lower)) return n;
		return lower;
	}

	@Override
	public String status() {
		int n = names().size();
		return n == 0 ? "No friends added" : n + (n == 1 ? " friend" : " friends") + ", " + online.size() + " online here";
	}
}
