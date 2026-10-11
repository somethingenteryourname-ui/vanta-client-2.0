package vanta.hud.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

import vanta.hud.TextHudModule;
import vanta.modules.Category;

public final class PingWidget extends TextHudModule {
	private long nextRefresh;

	public PingWidget() {
		super("ping", "Ping", "Your latency to the server as reported in the player list (0 in singleplayer).", Category.HUD, false, 0, 240);
	}

	@Override
	public void update() {
		long now = System.currentTimeMillis();
		if (now < nextRefresh) return;
		nextRefresh = now + 1000;
		Minecraft mc = Minecraft.getInstance();
		ClientPacketListener connection = mc.getConnection();
		if (connection == null || mc.player == null) {
			setLine("Ping", "-");
			return;
		}
		PlayerInfo info = connection.getPlayerInfo(mc.player.getUUID());
		setLine("Ping", info == null ? "-" : info.getLatency() + " ms");
	}
}
