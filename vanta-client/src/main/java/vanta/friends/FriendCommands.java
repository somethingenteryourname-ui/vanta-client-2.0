package vanta.friends;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

/** /friend add|remove|list (handled by VANTA, never sent to the server). */
public final class FriendCommands {
	private FriendCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal("friend")
						.then(ClientCommandManager.literal("add").then(ClientCommandManager.argument("name", StringArgumentType.word())
								.suggests((ctx, builder) -> {
									var conn = Minecraft.getInstance().getConnection();
									if (conn != null) {
										for (PlayerInfo info : conn.getOnlinePlayers()) {
											String n = info.getProfile().name();
											if (n.toLowerCase().startsWith(builder.getRemainingLowerCase()) && !FriendsModule.isFriend(n)) builder.suggest(n);
										}
									}
									return builder.buildFuture();
								})
								.executes(ctx -> {
									String name = StringArgumentType.getString(ctx, "name");
									String err = FriendsModule.get().add(name);
									if (err != null) {
										ctx.getSource().sendError(Component.literal("[VANTA] " + err));
										return 0;
									}
									ctx.getSource().sendFeedback(Component.literal("[VANTA] Added " + name + " to your friends.").withStyle(ChatFormatting.GREEN));
									return 1;
								})))
						.then(ClientCommandManager.literal("remove").then(ClientCommandManager.argument("name", StringArgumentType.word())
								.suggests((ctx, builder) -> {
									for (String n : FriendsModule.get().names()) {
										if (n.toLowerCase().startsWith(builder.getRemainingLowerCase())) builder.suggest(n);
									}
									return builder.buildFuture();
								})
								.executes(ctx -> {
									String name = StringArgumentType.getString(ctx, "name");
									if (!FriendsModule.get().remove(name)) {
										ctx.getSource().sendError(Component.literal("[VANTA] " + name + " isn't on your friends list."));
										return 0;
									}
									ctx.getSource().sendFeedback(Component.literal("[VANTA] Removed " + name + " from your friends.").withStyle(ChatFormatting.GOLD));
									return 1;
								})))
						.then(ClientCommandManager.literal("list").executes(ctx -> {
							var names = FriendsModule.get().names();
							if (names.isEmpty()) {
								ctx.getSource().sendFeedback(Component.literal("[VANTA] No friends yet. Use /friend add <name>.").withStyle(ChatFormatting.GRAY));
								return 1;
							}
							StringBuilder sb = new StringBuilder();
							for (String n : names) {
								if (sb.length() > 0) sb.append(", ");
								sb.append(n).append(FriendsModule.isOnline(n) ? " (online)" : "");
							}
							ctx.getSource().sendFeedback(Component.literal("[VANTA] Friends: " + sb).withStyle(ChatFormatting.GREEN));
							return 1;
						}))));
	}
}
