package vanta.modules.impl;

import net.minecraft.client.Minecraft;

import vanta.config.BoolSetting;
import vanta.config.TextSetting;
import vanta.modules.Category;
import vanta.modules.Module;
import vanta.social.PresenceClient;
import vanta.social.VantaUsers;

/**
 * Client Badge: your name and the VANTA logo above your head in F5 / freelook, and the
 * logo next to other VANTA players' names (nametags and tab list) in your theme color.
 */
public final class ClientBadgeModule extends Module {
	private static ClientBadgeModule instance;

	public final BoolSetting ownNametag = setting(new BoolSetting(key("own_nametag"), "Your nametag in F5 / freelook",
			"Show your own name above your head in third person and freelook.", true));
	public final BoolSetting badgeSelf = setting(new BoolSetting(key("badge_self"), "Logo next to your name",
			"Put the VANTA logo in front of your own name.", true));
	public final BoolSetting badgeOthers = setting(new BoolSetting(key("badge_others"), "Logo next to other VANTA players",
			"Show the logo for other players using VANTA (in nametags and the tab list).", true));
	public final BoolSetting inTabList = setting(new BoolSetting(key("tab_list"), "Show in tab list", "Logos in the player list too.", true));
	public final BoolSetting share = setting(new BoolSetting(key("share"), "Let others see I use VANTA",
			"Tell servers running the VANTA Bridge plugin (and your presence server, if set) that you use VANTA. Off = you still see others.", true));
	public final TextSetting presenceUrl = setting(new TextSetting(key("presence_url"), "Presence server",
			"Optional. A presence server URL you and your friends share (see presence-server in the source) to see badges on any server. "
			+ "Empty = off. Sends only UUIDs.", "", 200));

	public ClientBadgeModule() {
		super("client_badge", "Client Badge", "Your name and the VANTA logo in F5/freelook, plus the logo next to other VANTA players' names.",
				Category.INTERFACE, true);
		instance = this;
	}

	public static boolean active() { return instance != null && instance.isEnabled(); }

	public static boolean showOwnNametag() {
		return active() && instance.ownNametag.on() && !Minecraft.getInstance().options.getCameraType().isFirstPerson();
	}

	public static boolean shares() { return active() && instance.share.on(); }

	public static String presenceUrl() {
		if (!active()) return "";
		String u = instance.presenceUrl.get().strip();
		return u.startsWith("https://") || u.startsWith("http://") ? u : "";
	}

	/** Should this player's name get the logo? */
	public static boolean badgeFor(java.util.UUID id) {
		if (!active() || id == null) return false;
		Minecraft mc = Minecraft.getInstance();
		boolean self = mc.player != null && id.equals(mc.player.getUUID());
		if (self) return instance.badgeSelf.on();
		return instance.badgeOthers.on() && VantaUsers.isUser(id);
	}

	public static boolean inTabList() { return active() && instance.inTabList.on(); }

	@Override
	public String status() {
		int n = VantaUsers.knownOthers();
		String presence = presenceUrl().isEmpty() ? "" : "  ·  presence: " + PresenceClient.status();
		return (n == 0 ? "No other VANTA players seen" : n == 1 ? "1 other VANTA player here" : n + " other VANTA players here") + presence;
	}
}
