package vanta.menu;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import vanta.branding.Theme;
import vanta.gui.MenuPage;
import vanta.gui.VantaMenuScreen;
import vanta.hud.HudEditorScreen;
import vanta.metrics.FrameMetrics;
import vanta.profiles.ProfileManager;
import vanta.ui.Colors;
import vanta.ui.Ui;

/**
 * Adds the VANTA info panel and shortcut buttons to the pause menu. It never moves
 * vanilla or mod buttons: it measures where they ended up (after Mod Menu and others
 * have added theirs) and only uses free space to the left. If there isn't room, it adds
 * a single small "VANTA" button in the corner instead.
 */
public final class PauseMenuExtras {
	private static long sessionStart;
	private static boolean wasInWorld;

	private PauseMenuExtras() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof PauseScreen) {
				BackgroundImages.requestCapture();
				setup(client, screen, w, h);
			}
			else if (MenuStyleModule.styles(screen) && screen.getClass().getPackageName().startsWith("net.minecraft.")) {
				ScreenEvents.afterBackground(screen).register((s, g, mx, my, d) -> MenuStyle.cornerLogo(g));
			}
		});
	}

	/** Called every client tick to time the current session. */
	public static void tick(Minecraft mc) {
		boolean inWorld = mc.level != null;
		if (inWorld && !wasInWorld) sessionStart = System.currentTimeMillis();
		wasInWorld = inWorld;
	}

	private static void setup(Minecraft mc, Screen screen, int width, int height) {
		MenuStyleModule m = MenuStyleModule.get();
		if (m == null || !m.isEnabled() || !m.pause.on()) return;
		List<AbstractWidget> buttons = Screens.getButtons(screen);
		if (buttons.isEmpty()) return; // F3+Esc pause without menu

		int minX = width;
		for (AbstractWidget b : buttons) minX = Math.min(minX, b.getX());
		int panelX = 10;
		int panelW = Math.min(150, minX - 24);
		int panelY = 28;
		int panelH = Math.min(height - 56, 196);

		if (!m.pausePanel.on() || panelW < 110 || panelH < 150) {
			buttons.add(Button.builder(Component.literal(Theme.clientName()), b -> mc.setScreen(new VantaMenuScreen(screen)))
					.bounds(6, height - 26, 64, 20).build());
			return;
		}

		int bx = panelX + 8, bw = panelW - 16, bh = 18;
		int by = panelY + panelH - 4 * (bh + 4) - 4;
		buttons.add(Button.builder(Component.literal(Theme.clientName() + " Menu"), b -> mc.setScreen(new VantaMenuScreen(screen)))
				.bounds(bx, by, bw, bh).build());
		buttons.add(Button.builder(Component.literal("HUD Editor"), b -> mc.setScreen(new HudEditorScreen(screen)))
				.bounds(bx, by + (bh + 4), bw, bh).build());
		buttons.add(Button.builder(Component.literal("Performance"), b -> mc.setScreen(new VantaMenuScreen(screen, MenuPage.PERFORMANCE)))
				.bounds(bx, by + 2 * (bh + 4), bw, bh).build());
		buttons.add(Button.builder(Component.literal("Profiles"), b -> mc.setScreen(new VantaMenuScreen(screen, MenuPage.PROFILES)))
				.bounds(bx, by + 3 * (bh + 4), bw, bh).build());

		final int px = panelX, py = panelY, pw = panelW, ph = panelH;
		ScreenEvents.afterBackground(screen).register((s, g, mx, my, d) -> drawPanel(mc, g, px, py, pw, ph));
	}

	private static void drawPanel(Minecraft mc, GuiGraphics g, int x, int y, int w, int h) {
		Ui.shadow(g, x, y, w, h, Theme.radius());
		Ui.roundRect(g, x, y, w, h, Theme.radius(), Colors.withAlpha(Theme.PANEL.argb(), Math.max(0.85f, Theme.PANEL_OPACITY.value() / 100f)));
		Ui.roundOutline(g, x, y, w, h, Theme.radius(), Theme.border());
		Ui.accentFill(g, x, y, w, 2, 0, 0f);

		String name = Theme.clientName();
		if (Theme.ACCENT_MODE.get() == Theme.AccentMode.CHROMA) {
			g.pose().pushMatrix();
			g.pose().translate(x + 8, y + 9);
			g.pose().scale(1.5f);
			Ui.chromaText(g, name, 0, 0, false);
			g.pose().popMatrix();
		} else {
			Ui.textScaled(g, name, x + 8, y + 9, 1.5f, Theme.accent(), false);
		}

		ServerData server = mc.getCurrentServer();
		String where = server != null ? server.ip : mc.hasSingleplayerServer() ? "Singleplayer" : "-";
		String ping = "-";
		ClientPacketListener conn = mc.getConnection();
		if (conn != null && mc.player != null) {
			PlayerInfo info = conn.getPlayerInfo(mc.player.getUUID());
			if (info != null) ping = info.getLatency() + " ms";
		}
		String region = vanta.net.RegionLookup.current();
		if (!region.isEmpty() && !region.equals("Local")) ping = ping + " (" + region + ")";
		long secs = sessionStart == 0 ? 0 : (System.currentTimeMillis() - sessionStart) / 1000;
		String session = secs >= 3600 ? (secs / 3600) + "h " + (secs % 3600 / 60) + "m" : (secs / 60) + "m " + (secs % 60) + "s";

		int ry = y + 30;
		row(g, x, w, ry, server != null ? "Server" : "World", where);
		row(g, x, w, ry + 12, "Ping", ping);
		row(g, x, w, ry + 24, "FPS", String.valueOf(FrameMetrics.fps()));
		row(g, x, w, ry + 36, "Session", session);
		row(g, x, w, ry + 48, "Profile", ProfileManager.active());
	}

	private static void row(GuiGraphics g, int x, int w, int y, String label, String value) {
		Ui.text(g, label, x + 8, y, Theme.textDim());
		int lw = Ui.width(label) + 8;
		Ui.textRight(g, Ui.ellipsize(value, w - 16 - lw), x + w - 8, y, Theme.text());
	}
}
