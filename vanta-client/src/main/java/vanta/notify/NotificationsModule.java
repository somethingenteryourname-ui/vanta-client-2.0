package vanta.notify;

import net.minecraft.client.Minecraft;

import vanta.config.BoolSetting;
import vanta.config.IntSetting;
import vanta.modules.Category;
import vanta.modules.Module;
import vanta.ui.Icons;

/**
 * VANTA notifications: small themed pop-ups in the top-right corner for things worth
 * knowing (a friend joined, someone mentioned you, an item is about to break, an update
 * is out). Each kind can be switched off.
 */
public final class NotificationsModule extends Module {
	private static NotificationsModule instance;

	public final IntSetting duration = setting(new IntSetting(key("duration"), "Show for", "How long a notification stays up.", 4, 2, 10, 1, "s"));
	public final BoolSetting friends = setting(new BoolSetting(key("friends"), "Friends joining and leaving",
			"When someone on your friends list joins or leaves the server you're on.", true));
	public final BoolSetting mentions = setting(new BoolSetting(key("mentions"), "Mentions",
			"When someone says your name in chat while chat is closed.", true));
	public final BoolSetting durability = setting(new BoolSetting(key("durability"), "Low durability",
			"When armor or the item in your hand is about to break.", true));
	public final BoolSetting updates = setting(new BoolSetting(key("updates"), "Updates",
			"When a new VANTA version is out.", true));
	public final BoolSetting actions = setting(new BoolSetting(key("actions"), "Confirmations",
			"Small confirmations like \"Message copied\".", true));

	public NotificationsModule() {
		super("notifications", "Notifications", "Themed pop-ups for friends joining, mentions, low durability, updates and more.",
				Category.INTERFACE, true);
		instance = this;
	}

	public enum Kind { FRIENDS, MENTIONS, DURABILITY, UPDATES, ACTIONS }

	private static boolean wants(Kind k) {
		NotificationsModule m = instance;
		if (m == null || !m.isEnabled()) return false;
		return switch (k) {
			case FRIENDS -> m.friends.on();
			case MENTIONS -> m.mentions.on();
			case DURABILITY -> m.durability.on();
			case UPDATES -> m.updates.on();
			case ACTIONS -> m.actions.on();
		};
	}

	/** Shows a notification if that kind is turned on. Safe to call from any thread. */
	public static void show(Kind kind, String title, String message) {
		if (!wants(kind)) return;
		String[] icon = switch (kind) {
			case FRIENDS -> Icons.PROFILE;
			case MENTIONS -> Icons.CHAT;
			case DURABILITY -> Icons.INFO;
			case UPDATES -> Icons.STAR;
			case ACTIONS -> Icons.INFO;
		};
		int color = kind == Kind.DURABILITY ? 0xFFFFB020 : 0;
		long ms = instance.duration.value() * 1000L;
		Minecraft mc = Minecraft.getInstance();
		if (mc == null) return;
		mc.execute(() -> {
			try {
				mc.getToastManager().addToast(new VantaToast(title, message, icon, color, ms));
			} catch (RuntimeException ignored) {
				// toasts not ready (very early startup)
			}
		});
	}
}
