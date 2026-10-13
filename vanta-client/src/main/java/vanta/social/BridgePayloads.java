package vanta.social;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The two tiny messages VANTA uses with the optional VANTA Bridge server plugin.
 * Simple fixed layout so any server plugin can read/write it with a DataInputStream:
 * <ul>
 * <li>{@code vanta:hello} (client to server): one byte, the protocol version (1).</li>
 * <li>{@code vanta:users} (server to client): int count, then count x (long most, long least) UUID halves.</li>
 * <li>{@code vanta:cosmetic} / {@code vanta:cosmetics}: cape and trail ids (see below).</li>
 * </ul>
 */
public final class BridgePayloads {
	public static final int PROTOCOL = 1;
	private static final int MAX_USERS = 1000;

	private BridgePayloads() {
	}

	public record Hello(int protocol) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<Hello> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("vanta", "hello"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Hello> CODEC = CustomPacketPayload.codec(Hello::write, Hello::new);

		public Hello(RegistryFriendlyByteBuf buf) {
			this(buf.readByte());
		}

		public void write(RegistryFriendlyByteBuf buf) {
			buf.writeByte(protocol);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record Users(List<UUID> users) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<Users> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("vanta", "users"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Users> CODEC = CustomPacketPayload.codec(Users::write, Users::read);

		static Users read(RegistryFriendlyByteBuf buf) {
			int n = Math.max(0, Math.min(MAX_USERS, buf.readInt()));
			List<UUID> list = new ArrayList<>(n);
			for (int i = 0; i < n && buf.readableBytes() >= 16; i++) list.add(new UUID(buf.readLong(), buf.readLong()));
			return new Users(list);
		}

		public void write(RegistryFriendlyByteBuf buf) {
			buf.writeInt(users.size());
			for (UUID u : users) {
				buf.writeLong(u.getMostSignificantBits());
				buf.writeLong(u.getLeastSignificantBits());
			}
		}

		@Override
		public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/** {@code vanta:cosmetic} (client to server): your cape id and trail id, each as unsigned short length + UTF-8. */
	public record Cosmetic(String cape, String trail) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<Cosmetic> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("vanta", "cosmetic"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Cosmetic> CODEC = CustomPacketPayload.codec(Cosmetic::write, Cosmetic::read);

		static Cosmetic read(RegistryFriendlyByteBuf buf) {
			return new Cosmetic(readString(buf), readString(buf));
		}

		public void write(RegistryFriendlyByteBuf buf) {
			writeString(buf, cape);
			writeString(buf, trail);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/** {@code vanta:cosmetics} (server to client): int count, then count x (long, long, cape string, trail string). */
	public record Cosmetics(List<Entry> entries) implements CustomPacketPayload {
		public record Entry(UUID id, String cape, String trail) {
		}

		public static final CustomPacketPayload.Type<Cosmetics> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("vanta", "cosmetics"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Cosmetics> CODEC = CustomPacketPayload.codec(Cosmetics::write, Cosmetics::read);

		static Cosmetics read(RegistryFriendlyByteBuf buf) {
			int n = Math.max(0, Math.min(MAX_USERS, buf.readInt()));
			List<Entry> list = new ArrayList<>(n);
			for (int i = 0; i < n && buf.readableBytes() >= 20; i++) {
				UUID id = new UUID(buf.readLong(), buf.readLong());
				list.add(new Entry(id, readString(buf), readString(buf)));
			}
			return new Cosmetics(list);
		}

		public void write(RegistryFriendlyByteBuf buf) {
			buf.writeInt(entries.size());
			for (Entry e : entries) {
				buf.writeLong(e.id().getMostSignificantBits());
				buf.writeLong(e.id().getLeastSignificantBits());
				writeString(buf, e.cape());
				writeString(buf, e.trail());
			}
		}

		@Override
		public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/** Same as Java's DataOutputStream.writeUTF for plain ids (letters, digits, underscore). Max 32 characters. */
	static void writeString(RegistryFriendlyByteBuf buf, String s) {
		byte[] b = (s == null ? "" : s.length() > 32 ? s.substring(0, 32) : s).getBytes(java.nio.charset.StandardCharsets.UTF_8);
		buf.writeShort(b.length);
		buf.writeBytes(b);
	}

	static String readString(RegistryFriendlyByteBuf buf) {
		if (buf.readableBytes() < 2) return "";
		int len = buf.readUnsignedShort();
		if (len > 64 || buf.readableBytes() < len) {
			buf.skipBytes(Math.min(len, buf.readableBytes()));
			return "";
		}
		byte[] b = new byte[len];
		buf.readBytes(b);
		return new String(b, java.nio.charset.StandardCharsets.UTF_8);
	}
}
