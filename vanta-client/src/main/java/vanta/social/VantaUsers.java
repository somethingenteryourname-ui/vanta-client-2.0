package vanta.social;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import vanta.branding.Theme;
import vanta.client.VantaClient;
import vanta.modules.impl.ClientBadgeModule;

/**
 * Which players are using VANTA. You always are. Others are known when:
 * <ul>
 * <li>the server runs the VANTA Bridge plugin (it tells VANTA players about each other), or</li>
 * <li>you and your friends set the same optional presence server in Client Badge settings.</li>
 * </ul>
 * Nothing is guessed: without one of those, only your own badge shows.
 */
public final class VantaUsers {
	/** Private-use character drawn with the VANTA badge font (assets/vanta/font/badge.json). */
	public static final String GLYPH = "";
	private static final FontDescription BADGE_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("vanta", "badge"));

	private static final Set<UUID> FROM_SERVER = ConcurrentHashMap.newKeySet();
	private static final Set<UUID> FROM_PRESENCE = ConcurrentHashMap.newKeySet();
	private static Object lastConnection;
	private static boolean helloSent;

	private VantaUsers() {
	}

	public static void init() {
		try {
			PayloadTypeRegistry.playC2S().register(BridgePayloads.Hello.TYPE, BridgePayloads.Hello.CODEC);
			PayloadTypeRegistry.playS2C().register(BridgePayloads.Users.TYPE, BridgePayloads.Users.CODEC);
			ClientPlayNetworking.registerGlobalReceiver(BridgePayloads.Users.TYPE, (payload, context) -> {
				FROM_SERVER.clear();
				FROM_SERVER.addAll(payload.users());
			});
		} catch (RuntimeException | LinkageError e) {
			VantaClient.LOG.warn("[VANTA] Could not set up the VANTA Bridge channel; badges will only use the presence server", e);
		}
	}

	/** Client tick: say hello to bridge servers, forget everything when you leave. */
	public static void tick(Minecraft mc) {
		Object conn = mc.getConnection();
		if (conn != lastConnection) {
			lastConnection = conn;
			helloSent = false;
			FROM_SERVER.clear();
			FROM_PRESENCE.clear();
		}
		if (conn == null || !ClientBadgeModule.active()) return;
		if (!helloSent && ClientBadgeModule.shares()) {
			try {
				if (ClientPlayNetworking.canSend(BridgePayloads.Hello.TYPE)) {
					ClientPlayNetworking.send(new BridgePayloads.Hello(BridgePayloads.PROTOCOL));
					helloSent = true;
				}
			} catch (RuntimeException | LinkageError e) {
				helloSent = true; // never retry in a loop
			}
		}
		PresenceClient.tick(mc);
	}

	static void setPresence(Collection<UUID> users) {
		FROM_PRESENCE.clear();
		FROM_PRESENCE.addAll(users);
	}

	/** True if this player uses VANTA (as far as we know). */
	public static boolean isUser(UUID id) {
		if (id == null) return false;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && id.equals(mc.player.getUUID())) return true;
		return FROM_SERVER.contains(id) || FROM_PRESENCE.contains(id);
	}

	public static int knownOthers() {
		Set<UUID> all = ConcurrentHashMap.newKeySet();
		all.addAll(FROM_SERVER);
		all.addAll(FROM_PRESENCE);
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) all.remove(mc.player.getUUID());
		return all.size();
	}

	/** The badge glyph in your theme's accent color. */
	public static MutableComponent badge() {
		int rgb = Theme.accent() & 0xFFFFFF;
		return Component.literal(GLYPH).withStyle(s -> s.withFont(BADGE_FONT).withColor(rgb));
	}

	/** "badge + space + name". */
	public static MutableComponent withBadge(Component name) {
		return badge().append(Component.literal(" ")).append(name);
	}
}
