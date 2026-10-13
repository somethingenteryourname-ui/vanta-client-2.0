package vanta.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.util.FormattedCharSequence;

import vanta.branding.Theme;
import vanta.mixin.feature.ChatComponentAccessor;
import vanta.notify.NotificationsModule;
import vanta.ui.Colors;
import vanta.ui.Ui;

/**
 * Extras on the open chat screen: the tab bar, the emoji picker, right-click to copy and
 * Ctrl+F search. Coordinates are Minecraft GUI pixels (the chat screen isn't scaled).
 */
public final class ChatScreenExtras {
	private static final int TAB_H = 11;
	private static final int EMOJI_COLS = 10, EMOJI_CELL = 14;

	private static boolean pickerOpen;
	private static boolean searching;
	private static String draft = "";
	private static long copiedAt;

	private ChatScreenExtras() {
	}

	public static boolean searching() { return searching; }

	/** Chat closed: stop searching and close the picker. */
	public static void onClosed() {
		pickerOpen = false;
		if (searching) {
			searching = false;
			ChatTabs.setQuery("");
		}
	}

	// ------------------------------------------------------------------ layout

	private record Box(int x, int y, int w, int h) {
		boolean has(double mx, double my) { return mx >= x && my >= y && mx < x + w && my < y + h; }
	}

	private static List<Map.Entry<ChatTabs.Tab, Box>> tabBoxes(int height) {
		List<Map.Entry<ChatTabs.Tab, Box>> out = new ArrayList<>();
		int x = 2, y = height - 14 - TAB_H - 2;
		for (ChatTabs.Tab t : ChatTabs.Tab.values()) {
			int w = Ui.width(label(t)) + 10;
			out.add(Map.entry(t, new Box(x, y, w, TAB_H)));
			x += w + 2;
		}
		return out;
	}

	private static String label(ChatTabs.Tab t) {
		int n = ChatTabs.unread(t);
		return n > 0 && t != ChatTabs.tab() ? t.label + " " + Math.min(99, n) : t.label;
	}

	private static Box emojiButton(int width, int height, ChatModule m) {
		int right = width - 2 - (m.inputLogo.on() && m.inputBox.on() ? 14 : 2);
		return new Box(right - 12, height - 14, 12, 12);
	}

	private static Box pickerBox(int width, int height) {
		int rows = (Emoji.ALL.size() + EMOJI_COLS - 1) / EMOJI_COLS;
		int w = EMOJI_COLS * EMOJI_CELL + 6, h = rows * EMOJI_CELL + 18;
		return new Box(width - 2 - w, height - 14 - 4 - h, w, h);
	}

	// ------------------------------------------------------------------ drawing

	public static void render(GuiGraphics g, EditBox input, int width, int height, int mx, int my) {
		ChatModule m = ChatModule.active();
		if (m == null) return;
		if (searching && input != null) ChatTabs.setQuery(input.getValue());

		if (m.tabs.on() || searching) {
			if (searching) {
				String s = "Searching chat: type to filter  -  Enter or Esc to stop";
				int w = Ui.width(s) + 10, y = height - 14 - TAB_H - 2;
				Ui.roundRect(g, 2, y, w, TAB_H, 3, Colors.withAlpha(Theme.accent(), 0.85f));
				g.drawString(Ui.font(), s, 7, y + 2, Colors.contrastText(Theme.accent()), false);
			} else {
				for (Map.Entry<ChatTabs.Tab, Box> e : tabBoxes(height)) {
					Box b = e.getValue();
					boolean sel = e.getKey() == ChatTabs.tab();
					boolean hov = b.has(mx, my);
					int fill = sel ? Colors.withAlpha(Theme.accent(), 0.9f) : Colors.withAlpha(Theme.panel(), hov ? 0.95f : 0.75f);
					Ui.roundRect(g, b.x(), b.y(), b.w(), b.h(), 3, fill);
					int text = sel ? Colors.contrastText(Theme.accent()) : ChatTabs.unread(e.getKey()) > 0 ? Theme.accent() | 0xFF000000 : Theme.text() | 0xFF000000;
					g.drawString(Ui.font(), label(e.getKey()), b.x() + 5, b.y() + 2, text, false);
				}
			}
		}

		if (m.emojiButton.on()) {
			Box b = emojiButton(width, height, m);
			boolean hov = b.has(mx, my);
			g.drawString(Ui.font(), "☺", b.x() + 2, b.y() + 2, hov || pickerOpen ? Theme.accent() | 0xFF000000 : 0xFFB0B4BC, false);
			if (pickerOpen) drawPicker(g, width, height, mx, my);
		}

		if (System.currentTimeMillis() - copiedAt < 1200) {
			String s = "Copied!";
			int w = Ui.width(s) + 8;
			Ui.roundRect(g, mx + 6, my - 12, w, 11, 3, Colors.withAlpha(Theme.accent(), 0.9f));
			g.drawString(Ui.font(), s, mx + 10, my - 10, Colors.contrastText(Theme.accent()), false);
		}
	}

	private static void drawPicker(GuiGraphics g, int width, int height, int mx, int my) {
		Box p = pickerBox(width, height);
		Ui.roundRect(g, p.x(), p.y(), p.w(), p.h(), 4, Colors.withAlpha(Theme.panel(), 0.97f));
		Ui.roundOutline(g, p.x(), p.y(), p.w(), p.h(), 4, Colors.withAlpha(Theme.accent(), 0.5f));
		String hovered = null;
		int i = 0;
		for (Map.Entry<String, String> e : Emoji.ALL.entrySet()) {
			int cx = p.x() + 3 + (i % EMOJI_COLS) * EMOJI_CELL, cy = p.y() + 3 + (i / EMOJI_COLS) * EMOJI_CELL;
			boolean hov = mx >= cx && my >= cy && mx < cx + EMOJI_CELL && my < cy + EMOJI_CELL;
			if (hov) {
				Ui.roundRect(g, cx, cy, EMOJI_CELL, EMOJI_CELL, 2, Colors.withAlpha(Theme.accent(), 0.35f));
				hovered = ":" + e.getKey() + ":  " + e.getValue();
			}
			String sym = e.getValue();
			String shown = Ui.width(sym) > EMOJI_CELL - 2 ? sym.substring(0, 1) + ".." : sym;
			g.drawString(Ui.font(), shown, cx + (EMOJI_CELL - Ui.width(shown)) / 2, cy + 3, 0xFFFFFFFF, false);
			i++;
		}
		String foot = hovered != null ? hovered : "Click to insert. Or type :name:";
		g.drawString(Ui.font(), Ui.ellipsize(foot, p.w() - 8), p.x() + 4, p.y() + p.h() - 11, Theme.textDim() | 0xFF000000, false);
	}

	// ------------------------------------------------------------------ input

	/** Handles a click on the chat screen. Returns true when VANTA used it. */
	public static boolean click(Minecraft mc, EditBox input, int width, int height, double mx, double my, int button) {
		ChatModule m = ChatModule.active();
		if (m == null) return false;
		if (button == 0) {
			if (m.emojiButton.on()) {
				if (emojiButton(width, height, m).has(mx, my)) {
					pickerOpen = !pickerOpen;
					return true;
				}
				if (pickerOpen) {
					Box p = pickerBox(width, height);
					if (p.has(mx, my)) {
						int col = (int) ((mx - p.x() - 3) / EMOJI_CELL), row = (int) ((my - p.y() - 3) / EMOJI_CELL);
						int idx = row * EMOJI_COLS + col;
						if (col >= 0 && col < EMOJI_COLS && idx >= 0 && idx < Emoji.ALL.size() && input != null) {
							input.insertText(new ArrayList<>(Emoji.ALL.values()).get(idx));
						}
						return true;
					}
					pickerOpen = false;
				}
			}
			if (m.tabs.on() && !searching) {
				for (Map.Entry<ChatTabs.Tab, Box> e : tabBoxes(height)) {
					if (e.getValue().has(mx, my)) {
						ChatTabs.setTab(e.getKey());
						return true;
					}
				}
			}
		}
		if (button == 1 && m.copy.on()) {
			String text = messageAt(mc, mx, my);
			if (text != null) {
				mc.keyboardHandler.setClipboard(text);
				copiedAt = System.currentTimeMillis();
				NotificationsModule.show(NotificationsModule.Kind.ACTIONS, "Message copied", text);
				return true;
			}
		}
		return false;
	}

	/** Handles a key on the chat screen. Returns true when VANTA used it. */
	public static boolean key(EditBox input, int key, int modifiers) {
		ChatModule m = ChatModule.active();
		if (m == null || input == null) return false;
		boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
		if (m.search.on() && ctrl && key == GLFW.GLFW_KEY_F) {
			if (searching) stopSearch(input);
			else {
				searching = true;
				draft = input.getValue();
				input.setValue("");
			}
			return true;
		}
		if (searching && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_ESCAPE)) {
			stopSearch(input);
			return true;
		}
		if (searching && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) return true; // no history while searching
		if (pickerOpen && key == GLFW.GLFW_KEY_ESCAPE) {
			pickerOpen = false;
			return true;
		}
		return false;
	}

	private static void stopSearch(EditBox input) {
		searching = false;
		ChatTabs.setQuery("");
		input.setValue(draft);
		draft = "";
	}

	/** Turns finished :shortcodes: into symbols while typing. */
	public static void liveEmoji(EditBox input) {
		ChatModule m = ChatModule.active();
		if (m == null || !m.emojiCodes.on() || input == null || searching) return;
		String v = input.getValue();
		if (v.indexOf(':') < 0) return;
		String r = Emoji.replace(v);
		if (r.equals(v)) return;
		int cursor = input.getCursorPosition();
		input.setValue(r);
		input.setCursorPosition(Math.max(0, Math.min(r.length(), cursor - (v.length() - r.length()))));
	}

	public static String onSend(String message) {
		ChatModule m = ChatModule.active();
		return m != null && m.emojiCodes.on() ? Emoji.replace(message) : message;
	}

	// ------------------------------------------------------------------ copy

	/** Full text of the chat message under the mouse, or null. */
	public static String messageAt(Minecraft mc, double mx, double my) {
		ChatComponent chat = mc.gui.getChat();
		if (!((Object) chat instanceof ChatComponentAccessor acc)) return null;
		double scale = mc.options.chatScale().get();
		if (scale <= 0) return null;
		double lx = mx / scale - 4, ly = my / scale;
		int maxWidth = (int) Math.ceil(ChatComponent.getWidth(mc.options.chatWidth().get()) / scale);
		if (lx < -4 || lx > maxWidth + 8) return null;
		int chatBottom = (int) Math.floor((mc.getWindow().getGuiScaledHeight() - 40) / scale);
		int entryH = (int) (9 * (mc.options.chatLineSpacing().get() + 1.0));
		if (ly > chatBottom || entryH <= 0) return null;
		int i = (int) Math.floor((chatBottom - ly) / entryH);
		List<GuiMessage.Line> lines = acc.vanta$trimmedMessages();
		if (i < 0 || i >= chat.getLinesPerPage()) return null;
		int idx = i + acc.vanta$scrollPos();
		if (idx >= lines.size()) return null;
		return messageText(lines, idx);
	}

	/** Joins the lines of the message that line {@code idx} belongs to (lines are newest-first). */
	public static String messageText(List<GuiMessage.Line> lines, int idx) {
		int end = idx;
		while (end > 0 && !lines.get(end).endOfEntry()) end--;
		int start = end + 1;
		while (start < lines.size() && !lines.get(start).endOfEntry()) start++;
		StringBuilder sb = new StringBuilder();
		for (int k = start - 1; k >= end; k--) {
			String part = plain(lines.get(k).content());
			if (sb.length() > 0 && !part.startsWith(" ") && sb.charAt(sb.length() - 1) != ' ') sb.append(' '); // wrapping drops the space
			sb.append(part);
		}
		String s = sb.toString();
		if (s.startsWith(ChatHeads.SPACER)) s = s.substring(ChatHeads.SPACER.length());
		return s.strip();
	}

	public static String plain(FormattedCharSequence seq) {
		StringBuilder sb = new StringBuilder();
		seq.accept((index, style, codePoint) -> {
			sb.appendCodePoint(codePoint);
			return true;
		});
		return sb.toString();
	}
}
