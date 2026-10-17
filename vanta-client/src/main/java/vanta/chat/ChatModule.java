package vanta.chat;

import net.minecraft.client.Minecraft;

import vanta.config.BoolSetting;
import vanta.config.ChoiceSetting;
import vanta.config.IntSetting;
import vanta.config.Labeled;
import vanta.config.TextSetting;
import vanta.modules.Category;
import vanta.modules.Module;

/**
 * VANTA chat: a themed chat panel with your logo faded into the background, plus
 * timestamps, stacked repeats, mention highlights, filters, longer history and more.
 * Works the same in singleplayer and on servers; nothing extra is sent anywhere.
 */
public final class ChatModule extends Module {
	public enum WatermarkPosition implements Labeled {
		CENTER("Center"), RIGHT("Right"), LEFT("Left");
		private final String label;
		WatermarkPosition(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	public enum PanelWidth implements Labeled {
		FULL("Full width"), FIT("Fit to text");
		private final String label;
		PanelWidth(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	public enum TimeFormat implements Labeled {
		H24("24-hour"), H12("12-hour"), SECONDS("24-hour + seconds");
		private final String label;
		TimeFormat(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	public enum StampColor implements Labeled {
		GRAY("Gray"), ACCENT("Accent");
		private final String label;
		StampColor(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	public enum MentionSound implements Labeled {
		OFF("Off"), DING("Ding"), PLING("Pling"), BELL("Bell");
		private final String label;
		MentionSound(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	private static ChatModule instance;

	// ---- look
	public final BoolSetting style = setting(new BoolSetting(key("style"), "VANTA chat style",
			"One rounded panel in your theme colors behind the chat instead of Minecraft's black bars.", true));
	public final IntSetting panelOpacity = setting(new IntSetting(key("panel_opacity"), "Panel opacity",
			"How see-through the chat panel is.", 55, 0, 100, 5, "%"));
	public final BoolSetting rounded = setting(new BoolSetting(key("rounded"), "Rounded corners",
			"Round the corners of the chat panel.", true));
	public final BoolSetting accentBar = setting(new BoolSetting(key("accent_bar"), "Accent bar",
			"A thin bar in your accent colors down the left side of the panel.", true));
	public final ChoiceSetting<PanelWidth> width = setting(new ChoiceSetting<>(key("panel_width"), "Panel width",
			"Full width, or only as wide as the longest visible message.", PanelWidth.FULL));
	public final BoolSetting fullWhileTyping = setting(new BoolSetting(key("full_while_typing"), "Full panel while typing",
			"When chat is open, the panel fills the whole chat area so the logo has room.", true));
	public final BoolSetting watermark = setting(new BoolSetting(key("watermark"), "Logo watermark",
			"Your logo and client name, faded into the chat background in your colors.", true));
	public final IntSetting watermarkOpacity = setting(new IntSetting(key("watermark_opacity"), "Watermark opacity",
			"How strong the logo in the chat background is.", 18, 5, 60, 1, "%"));
	public final IntSetting watermarkSize = setting(new IntSetting(key("watermark_size"), "Watermark size",
			"Size of the logo compared to the panel.", 100, 50, 150, 10, "%"));
	public final ChoiceSetting<WatermarkPosition> watermarkPosition = setting(new ChoiceSetting<>(key("watermark_position"),
			"Watermark position", "Where the logo sits in the chat panel.", WatermarkPosition.CENTER));
	public final BoolSetting watermarkAnimation = setting(new BoolSetting(key("watermark_animation"), "Watermark animation",
			"Play the logo intro when you open chat, with the glow and shine while it's open.", true));
	public final BoolSetting watermarkWhileTyping = setting(new BoolSetting(key("watermark_typing_only"), "Watermark only while typing",
			"Only show the logo when chat is open.", false));
	public final BoolSetting inputBox = setting(new BoolSetting(key("input_box"), "Themed input box",
			"The box you type in gets your panel color and an accent underline.", true));
	public final BoolSetting inputLogo = setting(new BoolSetting(key("input_logo"), "Logo in input box",
			"A small emblem at the right end of the chat input.", true));
	public final BoolSetting smooth = setting(new BoolSetting(key("smooth"), "Smooth new messages",
			"New messages slide up into place instead of popping in.", true));

	// ---- messages
	public final BoolSetting timestamps = setting(new BoolSetting(key("timestamps"), "Timestamps",
			"Show the time in front of every new message.", false));
	public final ChoiceSetting<TimeFormat> timeFormat = setting(new ChoiceSetting<>(key("time_format"), "Time format",
			"How timestamps are written.", TimeFormat.H24));
	public final ChoiceSetting<StampColor> stampColor = setting(new ChoiceSetting<>(key("stamp_color"), "Timestamp color",
			"Gray, or your accent color.", StampColor.GRAY));
	public final BoolSetting stack = setting(new BoolSetting(key("stack"), "Stack repeated messages",
			"The same message twice in a row becomes one line with a counter, like \"(x3)\".", true));
	public final IntSetting history = setting(new IntSetting(key("history"), "Chat history",
			"How many messages chat keeps to scroll back through (vanilla keeps 100).", 500, 100, 2000, 100, ""));
	public final BoolSetting keepOnLeave = setting(new BoolSetting(key("keep_on_leave"), "Keep chat when leaving",
			"Don't wipe chat when you leave a world or switch servers.", false));
	public final IntSetting fadeSeconds = setting(new IntSetting(key("fade_seconds"), "Fade out after",
			"How long messages stay on screen when chat is closed (vanilla is 10s).", 10, 3, 60, 1, "s"));
	public final BoolSetting neverFade = setting(new BoolSetting(key("never_fade"), "Never fade",
			"Recent messages stay on screen until new ones push them out.", false));

	// ---- mentions
	public final BoolSetting mentions = setting(new BoolSetting(key("mentions"), "Highlight mentions",
			"Messages with your name (or your words below) get an accent background and marker.", true));
	public final ChoiceSetting<MentionSound> mentionSound = setting(new ChoiceSetting<>(key("mention_sound"), "Mention sound",
			"Play a sound when someone mentions you.", MentionSound.DING));
	public final TextSetting mentionWords = (TextSetting) setting(new TextSetting(key("mention_words"), "Extra highlight words",
			"More words that count as a mention, separated by commas (for example a nickname or your clan tag).", "", 200)).sensitive();

	// ---- filters
	public final TextSetting mutedWords = setting(new TextSetting(key("muted_words"), "Muted words",
			"Messages containing any of these words are hidden. Separate words with commas.", "", 300));
	public final BoolSetting hideJoinLeave = setting(new BoolSetting(key("hide_join_leave"), "Hide join and leave messages",
			"Hide \"joined the game\" and \"left the game\" lines.", false));
	public final BoolSetting hideBlank = setting(new BoolSetting(key("hide_blank"), "Hide empty messages",
			"Skip blank lines some servers send as spacers.", false));

	// ---- tools
	public final BoolSetting tabs = setting(new BoolSetting(key("tabs"), "Chat tabs",
			"Tabs above the chat box: All, Chat, Private, Party and Server. Click one to only see those messages.", true));
	public final TextSetting privateWords = setting(new TextSetting(key("private_words"), "Private message words",
			"How private messages look on your server, separated by commas. Start a word with ^ to match only the start of a message.",
			"whispers to you,you whisper,-> me],[me ->,-> you],^from ,^to ,^[msg]", 200));
	public final TextSetting partyWords = setting(new TextSetting(key("party_words"), "Party message words",
			"How party or team chat looks on your server, separated by commas. Start a word with ^ to match only the start.",
			"^party >,^[party],^party:,^(party),^p >,^[team],^team >", 200));
	public final BoolSetting heads = setting(new BoolSetting(key("heads"), "Player heads",
			"Show the sender's face in front of their messages.", true));
	public final BoolSetting copy = setting(new BoolSetting(key("copy"), "Right-click to copy",
			"Right-click a message while chat is open to copy it.", true));
	public final BoolSetting search = setting(new BoolSetting(key("search"), "Search with Ctrl+F",
			"Press Ctrl+F in chat to filter messages as you type. Enter or Esc stops searching.", true));
	public final BoolSetting emojiButton = setting(new BoolSetting(key("emoji_button"), "Emoji picker",
			"A button at the end of the chat box that opens a grid of symbols to insert.", true));
	public final BoolSetting emojiCodes = setting(new BoolSetting(key("emoji_codes"), "Emoji shortcodes",
			"Type :skull:, :heart:, :star: and more; they turn into symbols as you type.", true));

	public ChatModule() {
		super("chat", "Chat", "Themed chat with your logo in the background, timestamps, stacked repeats, mention highlights, "
				+ "muted words, longer history and more. Open the Chat tab.", Category.INTERFACE, true);
		instance = this;
		Runnable refresh = ChatModule::refreshChat;
		fadeSeconds.onChange(v -> refresh.run());
		neverFade.onChange(v -> refresh.run());
		enabledSetting().onChange(v -> {
			if (!v) ChatTabs.reset();
			refresh.run();
		});
		tabs.onChange(v -> {
			if (!v) ChatTabs.reset();
		});
	}

	/** The module when it is switched on, otherwise null. */
	public static ChatModule active() {
		return instance != null && instance.isEnabled() ? instance : null;
	}

	public static ChatModule instance() { return instance; }

	/** True when the themed panel replaces vanilla's line backgrounds. */
	public static boolean styled() {
		ChatModule m = active();
		return m != null && m.style.on();
	}

	public static int historyLimit() {
		ChatModule m = active();
		return m == null ? 100 : m.history.value();
	}

	/**
	 * Shifts a line's added time so it fades after the chosen time instead of 10 seconds.
	 * Vanilla fully shows a line for 180 ticks then fades it over 20.
	 */
	public static int shiftAddedTime(int addedTime) {
		ChatModule m = active();
		if (m == null) return addedTime;
		if (m.neverFade.on()) return addedTime + 1_000_000_000;
		return addedTime + (m.fadeSeconds.value() * 20 - 200);
	}

	private static void refreshChat() {
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc != null && mc.gui != null) mc.gui.getChat().rescaleChat();
		} catch (RuntimeException ignored) {
			// not ready yet (settings load before the game window exists)
		}
	}
}
