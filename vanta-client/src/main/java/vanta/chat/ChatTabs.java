package vanta.chat;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Chat tabs and chat search. Every message stays in chat history; the tab (and search
 * text) only decides which ones are shown, so switching back to All shows everything.
 */
public final class ChatTabs {
	public enum Tab {
		ALL("All"), CHAT("Chat"), PRIVATE("Private"), PARTY("Party"), SYSTEM("Server");
		public final String label;
		Tab(String label) { this.label = label; }
	}

	/** "[Rank] Name: hi", "Name » hi", "<Name> hi" style chat lines. */
	private static final Pattern CHAT_LINE = Pattern.compile(
			"^(?:\\[[^\\]]{1,32}\\]\\s*|\\([^)]{1,32}\\)\\s*|[^\\sA-Za-z0-9_<]{1,3}\\s*)*<?([A-Za-z0-9_]{3,16})>?\\s*(?::|»|>>|->|\\|)\\s");
	private static final Pattern ANGLE = Pattern.compile("^(?:\\[[^\\]]{1,32}\\]\\s*)*<([A-Za-z0-9_]{3,16})>\\s");

	private static Tab tab = Tab.ALL;
	private static String query = "";
	private static final Map<Tab, Integer> UNREAD = new EnumMap<>(Tab.class);

	private ChatTabs() {
	}

	public static Tab tab() { return tab; }
	public static String query() { return query; }
	public static int unread(Tab t) { return UNREAD.getOrDefault(t, 0); }

	public static void setTab(Tab t) {
		if (t == tab) return;
		tab = t;
		UNREAD.put(t, 0);
		refresh();
	}

	public static void setQuery(String q) {
		String clean = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
		if (clean.equals(query)) return;
		query = clean;
		refresh();
	}

	/** Back to showing everything (used when the feature is turned off). */
	public static void reset() {
		boolean changed = tab != Tab.ALL || !query.isEmpty();
		tab = Tab.ALL;
		query = "";
		if (changed) refresh();
	}

	private static void refresh() {
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc != null && mc.gui != null) mc.gui.getChat().rescaleChat();
		} catch (RuntimeException ignored) {
			// not in game yet
		}
	}

	/** Whether a message should be shown with the current tab and search. */
	public static boolean visible(GuiMessage message) {
		if (tab == Tab.ALL && query.isEmpty()) return true;
		ChatModule m = ChatModule.active();
		if (m == null || !m.tabs.on()) return true;
		String text = message.content().getString();
		if (!query.isEmpty() && !text.toLowerCase(Locale.ROOT).contains(query)) return false;
		return tab == Tab.ALL || classify(message.content(), message.tag(), m.privateWords.get(), m.partyWords.get()) == tab;
	}

	/** Called for each new message: counts unread messages for the other tabs. */
	public static void onNewMessage(Component content, GuiMessageTag tag) {
		ChatModule m = ChatModule.active();
		if (m == null || !m.tabs.on()) return;
		Tab t = classify(content, tag, m.privateWords.get(), m.partyWords.get());
		if (t != tab && tab != Tab.ALL) UNREAD.merge(t, 1, Integer::sum);
		else if (t != tab && (t == Tab.PRIVATE || t == Tab.PARTY)) UNREAD.merge(t, 1, Integer::sum);
	}

	public static Tab classify(Component content, GuiMessageTag tag, String privateWords, String partyWords) {
		String key = content.getContents() instanceof TranslatableContents tc ? tc.getKey() : "";
		if (key.startsWith("commands.message.display")) return Tab.PRIVATE;
		return classify(content.getString(), tag != null && "System".equals(tag.logTag()), key.startsWith("chat.type"), privateWords, partyWords);
	}

	/** Pure classification from the message text. */
	public static Tab classify(String text, boolean systemTag, boolean playerChat, String privateWords, String partyWords) {
		String lower = text.toLowerCase(Locale.ROOT);
		if (matches(lower, privateWords)) return Tab.PRIVATE;
		if (matches(lower, partyWords)) return Tab.PARTY;
		if (playerChat || !systemTag || CHAT_LINE.matcher(text).find()) return Tab.CHAT;
		return Tab.SYSTEM;
	}

	/** Comma list match; an entry starting with ^ must be at the start of the message. */
	public static boolean matches(String lower, String list) {
		if (list == null || list.isBlank()) return false;
		for (String raw : list.split(",")) {
			String w = raw.toLowerCase(Locale.ROOT);
			if (w.isBlank()) continue;
			if (w.startsWith("^")) {
				if (lower.startsWith(w.substring(1).stripLeading())) return true;
			} else if (lower.contains(w.strip())) {
				return true;
			}
		}
		return false;
	}

	/** The player name a chat line starts with, or null. */
	public static String senderName(String text) {
		if (text == null) return null;
		Matcher a = ANGLE.matcher(text);
		if (a.find()) return a.group(1);
		Matcher m = CHAT_LINE.matcher(text);
		return m.find() ? m.group(1) : null;
	}
}
