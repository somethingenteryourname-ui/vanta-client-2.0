package vanta.hud.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import vanta.hud.TextHudModule;
import vanta.modules.Category;

public final class ServerWidget extends TextHudModule {
	private long nextRefresh;

	public ServerWidget() {
		super("server", "Server Address", "The server you're playing on (or Singleplayer).", Category.HUD, false, 1000, 540);
	}

	@Override
	public void update() {
		long now = System.currentTimeMillis();
		if (now < nextRefresh) return;
		nextRefresh = now + 2000;
		Minecraft mc = Minecraft.getInstance();
		ServerData data = mc.getCurrentServer();
		if (data != null) setLine("Server", data.ip);
		else setLine("Server", mc.hasSingleplayerServer() ? "Singleplayer" : "-");
	}
}
