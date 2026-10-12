package vanta.bridge;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * VANTA Bridge: VANTA clients say "hello" on the vanta:hello channel when they join; this
 * plugin remembers them and sends every VANTA player the list of VANTA players online
 * (vanta:users), so they can show each other's badge. Nothing else is sent or stored.
 *
 * vanta:users layout: int count, then count x (long most significant, long least significant).
 */
public final class VantaBridge extends JavaPlugin implements Listener, PluginMessageListener {
	private static final String HELLO = "vanta:hello";
	private static final String USERS = "vanta:users";

	private final Set<UUID> users = ConcurrentHashMap.newKeySet();

	@Override
	public void onEnable() {
		getServer().getMessenger().registerIncomingPluginChannel(this, HELLO, this);
		getServer().getMessenger().registerOutgoingPluginChannel(this, USERS);
		getServer().getPluginManager().registerEvents(this, this);
		getLogger().info("VANTA Bridge ready: VANTA players will see each other's badge.");
	}

	@Override
	public void onDisable() {
		users.clear();
	}

	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {
		if (!HELLO.equals(channel)) return;
		if (users.add(player.getUniqueId())) getLogger().info(player.getName() + " is using VANTA Client.");
		// Give the client a moment to register the vanta:users channel, then tell everyone.
		getServer().getScheduler().runTaskLater(this, this::broadcast, 20L);
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		if (users.remove(event.getPlayer().getUniqueId())) broadcast();
	}

	private void broadcast() {
		List<UUID> online = new ArrayList<>();
		for (UUID id : users) {
			Player p = getServer().getPlayer(id);
			if (p != null && p.isOnline()) online.add(id);
			else users.remove(id);
		}
		byte[] data;
		try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes)) {
			out.writeInt(online.size());
			for (UUID id : online) {
				out.writeLong(id.getMostSignificantBits());
				out.writeLong(id.getLeastSignificantBits());
			}
			out.flush();
			data = bytes.toByteArray();
		} catch (IOException e) {
			return;
		}
		for (UUID id : online) {
			Player p = getServer().getPlayer(id);
			if (p != null) p.sendPluginMessage(this, USERS, data);
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		List<String> names = new ArrayList<>();
		for (UUID id : users) {
			Player p = getServer().getPlayer(id);
			if (p != null) names.add(p.getName());
		}
		sender.sendMessage(names.isEmpty() ? "No one online is using VANTA Client." : "Using VANTA Client: " + String.join(", ", names));
		return true;
	}
}
