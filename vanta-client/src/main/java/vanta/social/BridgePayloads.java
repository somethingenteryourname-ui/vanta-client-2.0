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
}
