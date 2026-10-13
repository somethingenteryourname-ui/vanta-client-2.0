package vanta.chat;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Emoji for chat. Minecraft's font has no color emoji, so these are the symbols its font
 * really draws (everyone on the server sees them). Type :name: or use the picker.
 */
public final class Emoji {
	/** Shortcode (without colons) to symbol, in picker order. */
	public static final Map<String, String> ALL = new LinkedHashMap<>();

	static {
		String[][] table = {
				{"smile", "☺"}, {"frown", "☹"}, {"heart", "❤"}, {"star", "★"}, {"skull", "☠"},
				{"fire", "🔥"}, {"swords", "⚔"}, {"shield", "⛨"}, {"crown", "♛"}, {"check", "✔"},
				{"x", "✖"}, {"zap", "⚡"}, {"sun", "☀"}, {"cloud", "☁"}, {"umbrella", "☂"},
				{"snowman", "☃"}, {"music", "♫"}, {"note", "♪"}, {"flower", "✿"}, {"sparkle", "✨"},
				{"diamond", "♦"}, {"spade", "♠"}, {"club", "♣"}, {"hearts", "♥"}, {"coffee", "☕"},
				{"phone", "☎"}, {"mail", "✉"}, {"pencil", "✎"}, {"scissors", "✂"}, {"plane", "✈"},
				{"peace", "☮"}, {"yinyang", "☯"}, {"warning", "⚠"}, {"radioactive", "☢"}, {"anchor", "⚓"},
				{"flag", "⚑"}, {"arrow", "➜"}, {"up", "↑"}, {"down", "↓"}, {"left", "←"},
				{"right", "→"}, {"infinity", "∞"}, {"deg", "°"}, {"tm", "™"}, {"copyright", "©"},
				{"shrug", "¯\\_(ツ)_/¯"}, {"tableflip", "(╯°□°)╯︵ ┻━┻"},
				{"lenny", "( ͡° ͜ʖ ͡°)"}, {"gg", "ɢɢ"}, {"wave", "(＾▽＾)ﾉ"}};
		for (String[] e : table) ALL.put(e[0], e[1]);
		// symbols outside the basic plane don't draw in Minecraft's font: drop them
		ALL.entrySet().removeIf(e -> e.getValue().codePoints().anyMatch(cp -> cp > 0xFFFF));
	}

	private Emoji() {
	}

	/** Replaces every complete :shortcode: in the text. Unknown codes are left alone. */
	public static String replace(String text) {
		if (text == null || text.indexOf(':') < 0) return text;
		StringBuilder out = new StringBuilder(text.length());
		int i = 0;
		while (i < text.length()) {
			char ch = text.charAt(i);
			if (ch == ':') {
				int end = text.indexOf(':', i + 1);
				if (end > i + 1 && end - i <= 14) {
					String code = text.substring(i + 1, end).toLowerCase(java.util.Locale.ROOT);
					String sym = ALL.get(code);
					if (sym != null) {
						out.append(sym);
						i = end + 1;
						continue;
					}
				}
			}
			out.append(ch);
			i++;
		}
		return out.toString();
	}
}
