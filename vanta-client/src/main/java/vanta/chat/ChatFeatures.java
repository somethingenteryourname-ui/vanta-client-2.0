package vanta.chat;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundEvents;

import vanta.branding.Theme;
import vanta.mixin.feature.ChatComponentAccessor;

/**
 * Everything VANTA does to a chat message on its way in: filters, stacking repeats,
 * timestamps and mention highlights. The message is then added the normal way, so
 * signatures, deletion and the chat log keep working.
 */
public final class ChatFeatures {
	/** Log tag of the marker VANTA puts on lines that mention you. */
	public static final String MENTION_TAG = "VANTA Mention";

	private static final DateTimeFormatter H24 = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
	private static final DateTimeFormatter H12 = DateTimeFormatter.ofPattern("h:mm a", Locale.ROOT);
	private static final DateTimeFormatter SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

	private static boolean reentry;
	private static String lastKey;
	private static GuiMessage lastMessage;
	private static int lastCount;
	private static String pendingKey;
	private static boolean lastVisible = true;
	private static int pendingCount;
	private static String lastSent = "";
	private static long lastSentAt;
	private static long lastAddedAt;
	private static int lastAddedLines;

	private ChatFeatures() {
	}

	/** When the newest message arrived (System.currentTimeMillis) and how many lines it took. */
	public static long lastAddedAt() { return lastAddedAt; }
	public static int lastAddedLines() { return lastAddedLines; }

	/** Called when you send something, so your own message doesn't count as a mention. */
	public static void onSent(String message) {
		if (message == null || message.startsWith("/")) return;
		lastSent = message.strip();
		lastSentAt = System.currentTimeMillis();
	}

	/**
	 * Handles an incoming message. Returns true when VANTA took care of it (the original
	 * call should then be cancelled): either it was hidden, or it was re-added with changes.
	 */
	public static boolean onAdd(ChatComponent chat, Component contents, MessageSignature signature, GuiMessageTag tag) {
		if (reentry || contents == null) return false;
		ChatModule m = ChatModule.active();
		if (m == null || !((Object) chat instanceof ChatComponentAccessor acc)) return false;
		String text = contents.getString();

		// filters
		if (m.hideBlank.on() && text.isBlank()) return true;
		if (m.hideJoinLeave.on() && isJoinLeave(contents)) return true;
		if (containsAny(text, m.mutedWords.get())) return true;

		List<GuiMessage> all = acc.vanta$allMessages();
		List<GuiMessage.Line> lines = acc.vanta$trimmedMessages();

		// stack repeats
		int count = 1;
		if (m.stack.on() && lastMessage != null && text.equals(lastKey) && !all.isEmpty() && all.get(0) == lastMessage) {
			count = lastCount + 1;
			all.remove(0);
			if (lastVisible && !lines.isEmpty()) {
				lines.remove(0);
				while (!lines.isEmpty() && !lines.get(0).endOfEntry()) lines.remove(0);
			}
		}

		boolean mention = m.mentions.on() && isMention(text, playerName(), m.mentionWords.get(), lastSent,
				System.currentTimeMillis() - lastSentAt);

		Component shown = contents;
		if (count > 1) {
			shown = Component.empty().append(shown).append(Component.literal(" (x" + count + ")").withColor(0x9AA0AA));
		}
		if (m.timestamps.on()) {
			int color = m.stampColor.get() == ChatModule.StampColor.ACCENT ? Theme.accent() & 0xFFFFFF : 0x8A8F98;
			shown = Component.empty().append(Component.literal(stamp(LocalTime.now(), m.timeFormat.get()) + " ").withColor(color)).append(shown);
		}
		if (m.heads.on() && ChatHeads.prepare(text)) {
			shown = Component.empty().append(ChatHeads.SPACER).append(shown);
		}
		GuiMessageTag shownTag = tag;
		if (mention) {
			shownTag = new GuiMessageTag(Theme.accent() & 0xFFFFFF, null, Component.literal("Mentions you"), MENTION_TAG);
			if (count == 1) {
				playMentionSound(m.mentionSound.get());
				Minecraft mc = Minecraft.getInstance();
				if (!(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) {
					vanta.notify.NotificationsModule.show(vanta.notify.NotificationsModule.Kind.MENTIONS, "You were mentioned", text);
				}
			}
		}
		if (count == 1) ChatTabs.onNewMessage(contents, tag);

		pendingKey = text;
		pendingCount = count;
		if (shown == contents && shownTag == tag) return false; // nothing to change: let Minecraft add it as is
		reentry = true;
		try {
			chat.addMessage(shown, signature, shownTag);
		} finally {
			reentry = false;
		}
		return true;
	}

	/** Called after a message was added to chat: remembers it for stacking and the slide-in. */
	public static void afterAdd(ChatComponent chat) {
		if (pendingKey == null || !((Object) chat instanceof ChatComponentAccessor acc)) return;
		List<GuiMessage> all = acc.vanta$allMessages();
		List<GuiMessage.Line> lines = acc.vanta$trimmedMessages();
		lastKey = pendingKey;
		lastCount = pendingCount;
		pendingKey = null;
		lastMessage = all.isEmpty() ? null : all.get(0);
		ChatHeads.attach(lastMessage);
		lastVisible = lastMessage != null && ChatTabs.visible(lastMessage);
		if (!lastVisible) return; // hidden by the current tab or search: nothing slides in
		lastAddedAt = System.currentTimeMillis();
		int n = lines.isEmpty() ? 0 : 1;
		while (n < lines.size() && !lines.get(n).endOfEntry()) n++;
		lastAddedLines = n;
	}

	/** True when a line carries VANTA's mention marker. */
	public static boolean isMentionTag(GuiMessageTag tag) {
		return tag != null && MENTION_TAG.equals(tag.logTag());
	}

	private static String playerName() {
		try {
			return Minecraft.getInstance().getUser().getName();
		} catch (RuntimeException e) {
			return "";
		}
	}

	private static void playMentionSound(ChatModule.MentionSound sound) {
		try {
			Minecraft mc = Minecraft.getInstance();
			switch (sound) {
				case DING -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.3f));
				case PLING -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1.6f));
				case BELL -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL, 1.2f));
				case OFF -> {
				}
			}
		} catch (RuntimeException ignored) {
			// sound system not ready
		}
	}

	private static boolean isJoinLeave(Component c) {
		if (c.getContents() instanceof TranslatableContents t) {
			String key = t.getKey();
			return key.startsWith("multiplayer.player.joined") || key.equals("multiplayer.player.left");
		}
		return false;
	}

	// ------------------------------------------------------------------ pure logic (tested)

	/** "[12:34]" style stamp for a time. */
	public static String stamp(LocalTime time, ChatModule.TimeFormat format) {
		DateTimeFormatter f = switch (format) {
			case H12 -> H12;
			case SECONDS -> SECONDS;
			default -> H24;
		};
		return "[" + time.format(f) + "]";
	}

	/** True when text contains any of the comma-separated words (case-insensitive). */
	public static boolean containsAny(String text, String commaList) {
		if (text == null || commaList == null || commaList.isBlank()) return false;
		String lower = text.toLowerCase(Locale.ROOT);
		for (String w : commaList.split(",")) {
			String word = w.strip().toLowerCase(Locale.ROOT);
			if (!word.isEmpty() && lower.contains(word)) return true;
		}
		return false;
	}

	/**
	 * True when the message mentions your name or one of the extra words as a whole word.
	 * Your own chat line (which naturally has your name in it) doesn't count.
	 */
	public static boolean isMention(String text, String name, String extraWords, String lastSent, long msSinceSent) {
		if (text == null || text.isEmpty()) return false;
		if (name != null && !name.isEmpty()) {
			String lower = text.toLowerCase(Locale.ROOT);
			String n = name.toLowerCase(Locale.ROOT);
			if (lower.startsWith("<" + n + ">")) return false;
			if (lastSent != null && !lastSent.isEmpty() && msSinceSent >= 0 && msSinceSent < 5000 && text.contains(lastSent)) {
				// most likely our own message coming back from the server
				int at = lower.indexOf(n);
				int sentAt = text.lastIndexOf(lastSent);
				if (at >= 0 && at < sentAt) return false;
			}
			if (hasWord(text, name)) return true;
		}
		if (extraWords != null) {
			for (String w : extraWords.split(",")) {
				if (hasWord(text, w.strip())) return true;
			}
		}
		return false;
	}

	/** Whole-word, case-insensitive search. Words shorter than 2 characters never match. */
	public static boolean hasWord(String text, String word) {
		if (word == null || word.length() < 2) return false;
		String lower = text.toLowerCase(Locale.ROOT), w = word.toLowerCase(Locale.ROOT);
		int from = 0;
		while (true) {
			int i = lower.indexOf(w, from);
			if (i < 0) return false;
			int end = i + w.length();
			boolean before = i == 0 || !isWordChar(lower.charAt(i - 1));
			boolean after = end >= lower.length() || !isWordChar(lower.charAt(end));
			if (before && after) return true;
			from = i + 1;
		}
	}

	private static boolean isWordChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_';
	}
}
