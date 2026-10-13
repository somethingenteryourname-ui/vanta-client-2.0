package vanta.chat;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Player heads in chat. When a message starts with an online player's name, a small gap is
 * put in front of it and that player's face is drawn there. Each chat line remembers whose
 * face it shows (weakly, so old lines are forgotten when chat drops them).
 */
public final class ChatHeads {
	/** Width the face needs, in spaces of Minecraft's font (4 px each). */
	public static final String SPACER = "   ";

	private record Head(UUID id, PlayerSkin skin) {
	}

	private static final Map<GuiMessage, Head> BY_MESSAGE = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<GuiMessage.Line, Head> BY_LINE = Collections.synchronizedMap(new WeakHashMap<>());
	private static Head pending;
	private static GuiMessage lastOwner;

	private ChatHeads() {
	}

	/** Finds the online player a message is from (by the name it starts with), for the next message added. */
	public static boolean prepare(String text) {
		pending = null;
		String name = ChatTabs.senderName(text);
		if (name == null) return false;
		Minecraft mc = Minecraft.getInstance();
		ClientPacketListener conn = mc.getConnection();
		if (conn == null) return false;
		PlayerInfo info = conn.getPlayerInfo(name);
		if (info == null) return false;
		pending = new Head(info.getProfile().id(), info.getSkin());
		return true;
	}

	/** Called after the message is in chat history: ties the head to it. */
	public static void attach(GuiMessage message) {
		if (pending != null && message != null) BY_MESSAGE.put(message, pending);
		pending = null;
	}

	public static void clearPending() { pending = null; }

	/** Called for every chat line as it's created; the first line of a message with a head gets the face. */
	public static void onLine(GuiMessage message, GuiMessage.Line line) {
		boolean first = message != lastOwner;
		lastOwner = message;
		if (!first) return;
		Head h = BY_MESSAGE.get(message);
		if (h == null && pending != null) {
			// the message being added right now: its lines are made before it joins chat history
			h = pending;
			BY_MESSAGE.put(message, h);
		}
		if (h != null) BY_LINE.put(line, h);
	}

	/** The skin to draw at the start of this line, or null. */
	public static PlayerSkin skinFor(GuiMessage.Line line) {
		Head h = BY_LINE.get(line);
		if (h == null) return null;
		Minecraft mc = Minecraft.getInstance();
		ClientPacketListener conn = mc.getConnection();
		if (conn != null) {
			PlayerInfo info = conn.getPlayerInfo(h.id());
			if (info != null) return info.getSkin(); // skins finish loading after the message arrives
		}
		return h.skin();
	}
}
