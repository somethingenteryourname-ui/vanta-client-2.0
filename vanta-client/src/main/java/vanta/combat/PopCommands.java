package vanta.combat;

import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

/**
 * Client commands (handled by VANTA, never sent to the server):
 * <ul>
 * <li>/resetcounter: reset every totem pop count</li>
 * <li>/resetcounter &lt;player&gt;: reset one player's count</li>
 * </ul>
 * After a reset nothing is shown until that player pops again.
 */
public final class PopCommands {
	private PopCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal("resetcounter")
						.executes(ctx -> {
							TotemTracker.resetAll();
							ctx.getSource().sendFeedback(Component.literal("[VANTA] Totem pop counter reset for everyone.").withStyle(ChatFormatting.GOLD));
							return 1;
						})
						.then(ClientCommandManager.argument("player", StringArgumentType.word())
								.suggests((ctx, builder) -> {
									var conn = Minecraft.getInstance().getConnection();
									if (conn != null) {
										for (PlayerInfo info : conn.getOnlinePlayers()) {
											String n = info.getProfile().name();
											if (n.toLowerCase().startsWith(builder.getRemainingLowerCase())) builder.suggest(n);
										}
									}
									return builder.buildFuture();
								})
								.executes(ctx -> {
									String name = StringArgumentType.getString(ctx, "player");
									UUID id = find(name);
									if (id == null) {
										ctx.getSource().sendError(Component.literal("[VANTA] No player called " + name + " here."));
										return 0;
									}
									TotemTracker.reset(id);
									ctx.getSource().sendFeedback(Component.literal("[VANTA] Totem pop counter reset for " + name + ".").withStyle(ChatFormatting.GOLD));
									return 1;
								}))));
	}

	private static UUID find(String name) {
		var conn = Minecraft.getInstance().getConnection();
		if (conn == null) return null;
		for (PlayerInfo info : conn.getOnlinePlayers()) {
			if (info.getProfile().name().equalsIgnoreCase(name)) return info.getProfile().id();
		}
		return null;
	}
}
