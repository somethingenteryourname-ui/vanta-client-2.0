package vanta.menu;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;

import vanta.branding.Theme;
import vanta.client.VantaClient;
import vanta.client.VantaInfo;
import vanta.compat.CompatDetector;
import vanta.config.ConfigManager;
import vanta.gui.MenuPage;
import vanta.gui.MenuSettings;
import vanta.gui.VantaMenuScreen;
import vanta.gui.VantaScreen;
import vanta.hud.HudEditorScreen;
import vanta.metrics.FrameMetrics;
import vanta.modules.ModuleRegistry;
import vanta.modules.impl.TitleScreenModule;
import vanta.ui.Colors;
import vanta.ui.Icons;
import vanta.ui.Ui;

/**
 * The VANTA home screen. Every button leads to a real screen: worlds and servers use
 * Minecraft's own screens and join flows (so world creation, saved servers, direct connect,
 * resource packs and authentication all work exactly as in vanilla), and "Vanilla menu" is
 * always available. Favorite servers show live ping and player count and join in one click
 * (or with keys 1-9).
 */
public final class VantaTitleScreen extends VantaScreen {
	private enum Page { HOME, PLAY, ABOUT }

	private enum ServerTab { FAVORITES("Favorites"), RECENT("Recent"), SAVED("Saved");
		final String label;
		ServerTab(String label) { this.label = label; }
	}

	private record Nav(String label, String[] icon, Runnable action, String hint, boolean enabled) {
	}

	private record Btn(int x, int y, int w, int h, String label, String[] icon, boolean primary, Runnable action, String hint, boolean enabled) {
	}

	/** A server / world line. {@code hotkey} > 0 shows a number badge; {@code star} shows the favorite toggle. */
	private record Row(int x, int y, int w, int h, QuickPlay.Entry server, LevelSummary world, int hotkey, boolean star, boolean big) {
	}

	private static final int STAR_W = 16;

	private Page page = Page.HOME;
	private ServerTab serverTab;
	private int serverScroll;
	private int serverListX, serverListY, serverListW, serverListH, serverRowsFit, serverTotal;
	private List<QuickPlay.Entry> savedServers;
	private boolean worldsRequested;
	private final List<Btn> buttons = new ArrayList<>();
	private final List<Row> rows = new ArrayList<>();
	private final List<String[]> labels = new ArrayList<>(); // {x, y, text} section labels for lists
	private int sidebarX, sidebarW, mainX, mainW;
	private int quickX = -1, quickW;
	private String hoverHint;
	private String cachedWelcome;
	private String cachedWelcomeSource;

	public VantaTitleScreen() {
		super(Component.literal("VANTA"), null);
	}

	private TitleScreenModule cfg() { return ModuleRegistry.TITLE_SCREEN; }

	@Override protected int minVirtualWidth() { return 380; }
	@Override protected int minVirtualHeight() { return 240; }

	// ------------------------------------------------------------ actions

	private void openSingleplayer() { minecraft.setScreen(new SelectWorldScreen(this)); }

	private void openMultiplayer() {
		if (!minecraft.allowsMultiplayer()) return; // respect account/parental restrictions exactly like vanilla
		minecraft.setScreen(minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this));
	}

	private void joinServer(QuickPlay.Entry e) {
		if (!minecraft.allowsMultiplayer()) return;
		if (!minecraft.options.skipMultiplayerWarning) {
			// First time on multiplayer: show Minecraft's own third-party server warning first.
			minecraft.setScreen(new SafetyScreen(this));
			return;
		}
		QuickPlay.join(minecraft, this, e);
	}

	private void joinWorld(LevelSummary world) {
		QuickPlay.openWorld(minecraft, world, () -> minecraft.setScreen(this));
	}

	private void openOptions() { minecraft.setScreen(new OptionsScreen(this, minecraft.options)); }
	private void openMenu(MenuPage p) { minecraft.setScreen(new VantaMenuScreen(this, p)); }
	private void openVanilla() { minecraft.setScreen(TitleScreenHook.vanillaTitleScreen()); }

	private void openModMenu() {
		try {
			Class<?> cls = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
			Screen s = (Screen) cls.getConstructor(Screen.class).newInstance(this);
			minecraft.setScreen(s);
		} catch (ReflectiveOperationException | RuntimeException e) {
			VantaClient.LOG.warn("[VANTA] Could not open Mod Menu", e);
		}
	}

	private void setPage(Page p) {
		page = p;
		serverScroll = 0;
		openedAt = System.currentTimeMillis();
		layout();
	}

	private void setTab(ServerTab t) {
		serverTab = t;
		serverScroll = 0;
		layout();
	}

	// ------------------------------------------------------------ data

	/** The most recent server or world, for the Continue card. */
	private Object lastPlayed() {
		List<QuickPlay.Entry> recent = QuickPlay.recent();
		List<LevelSummary> worlds = QuickPlay.recentWorlds();
		QuickPlay.Entry s = recent.isEmpty() || !minecraft.allowsMultiplayer() ? null : recent.get(0);
		LevelSummary w = worlds.isEmpty() ? null : worlds.get(0);
		if (s == null) return w;
		if (w == null) return s;
		return s.time() >= w.getLastPlayed() ? s : w;
	}

	private List<QuickPlay.Entry> serverList() {
		return switch (serverTab) {
			case FAVORITES -> QuickPlay.favorites();
			case RECENT -> QuickPlay.recent();
			case SAVED -> {
				if (savedServers == null) savedServers = QuickPlay.savedServers(minecraft);
				yield savedServers;
			}
		};
	}

	// ------------------------------------------------------------ layout

	@Override
	protected void layout() {
		buttons.clear();
		rows.clear();
		labels.clear();
		quickX = -1;
		if (!worldsRequested) {
			worldsRequested = true;
			QuickPlay.refreshWorlds(minecraft);
		}
		if (serverTab == null) serverTab = QuickPlay.favorites().isEmpty() ? ServerTab.SAVED : ServerTab.FAVORITES;

		TitleScreenModule c = cfg();
		boolean multiplayer = minecraft.allowsMultiplayer();
		int bh = c.buttonHeight.value();
		int gap = c.buttonSpacing.value();

		sidebarW = switch (c.sidebarStyle.get()) {
			case FULL -> Math.max(96, Theme.SIDEBAR_WIDTH.value() + 14);
			case ICONS -> 30;
			case HIDDEN -> 0;
		};
		boolean right = c.sidebarSide.get() == TitleScreenModule.Side.RIGHT;
		sidebarX = right ? vw - sidebarW : 0;
		mainX = right ? 0 : sidebarW;
		mainW = vw - sidebarW;

		// Sidebar navigation
		if (sidebarW > 0) {
			List<Nav> nav = new ArrayList<>();
			nav.add(new Nav("Home", Icons.HOME, () -> setPage(Page.HOME), "Home", true));
			nav.add(new Nav("Play", Icons.PLAY, () -> setPage(Page.PLAY), "Favorite servers, recent servers and worlds", true));
			nav.add(new Nav("Singleplayer", Icons.CUBE, this::openSingleplayer, "Your worlds: play, create, edit", true));
			nav.add(new Nav("Multiplayer", Icons.GLOBE, this::openMultiplayer,
					multiplayer ? "Servers, direct connect, add server" : "Multiplayer is disabled for this account", multiplayer));
			nav.add(new Nav("Modules", Icons.GRID, () -> openMenu(MenuPage.MODULES), "Turn VANTA features on and off", true));
			nav.add(new Nav("Customize", Icons.PALETTE, () -> openMenu(MenuPage.THEME), "Colors, layout and animation", true));
			nav.add(new Nav("HUD editor", Icons.HUD, () -> minecraft.setScreen(new HudEditorScreen(this)), "Place HUD widgets", true));
			nav.add(new Nav("Options", Icons.GEAR, this::openOptions, "Minecraft options: video, controls, language, accessibility", true));
			if (CompatDetector.isLoaded("modmenu")) nav.add(new Nav("Mods", Icons.GRID, this::openModMenu, "Open Mod Menu", true));
			nav.add(new Nav("Performance", Icons.BOLT, () -> openMenu(MenuPage.PERFORMANCE), "FPS presets and boosters", true));
			nav.add(new Nav("Profiles", Icons.PROFILE, () -> openMenu(MenuPage.PROFILES), "Switch your whole setup", true));
			nav.add(new Nav("About", Icons.INFO, () -> setPage(Page.ABOUT), "Versions, privacy and compatibility", true));

			int logoSpace = showSidebarLogo() ? 38 : 10;
			int y = logoSpace;
			int navGap = Math.max(1, gap / 2);
			// Fit every entry plus the two bottom buttons into the window height.
			int fit = (vh - logoSpace - 12) / (nav.size() + 2) - navGap;
			int itemH = Math.max(12, Math.min(Math.max(16, bh - 2), fit));
			boolean icons = c.sidebarStyle.get() == TitleScreenModule.SidebarStyle.ICONS;
			for (Nav n : nav) {
				buttons.add(new Btn(sidebarX + 5, y, sidebarW - 10, itemH, icons ? "" : n.label(), n.icon(), false, n.action(), n.hint(), n.enabled()));
				y += itemH + navGap;
			}
			int by = vh - 2 * (itemH + 2) - 6;
			buttons.add(new Btn(sidebarX + 5, by, sidebarW - 10, itemH, icons ? "" : "Vanilla menu", Icons.BACK, false, this::openVanilla,
					"Open Minecraft's original title screen (Realms, etc.)", true));
			buttons.add(new Btn(sidebarX + 5, by + itemH + 2, sidebarW - 10, itemH, icons ? "" : "Quit", Icons.POWER, false, minecraft::stop,
					"Quit the game", true));
		}

		int pad = 22;
		int cx = mainX + pad;
		int cw = mainW - pad * 2;
		switch (page) {
			case HOME -> layoutHome(cx, cw, bh, gap, multiplayer);
			case PLAY -> layoutPlay(cx, cw, bh, multiplayer);
			case ABOUT -> layoutAbout(cx, cw, bh);
		}
	}

	private boolean showSidebarLogo() {
		return Theme.SHOW_LOGO.on() && cfg().logoPosition.get() != TitleScreenModule.LogoPosition.HEADER
				&& cfg().sidebarStyle.get() == TitleScreenModule.SidebarStyle.FULL;
	}

	private boolean showHeaderLogo() {
		return Theme.SHOW_LOGO.on() && (cfg().logoPosition.get() != TitleScreenModule.LogoPosition.SIDEBAR
				|| cfg().sidebarStyle.get() != TitleScreenModule.SidebarStyle.FULL);
	}

	private int homeTop() { return showHeaderLogo() ? vh / 2 - 70 : vh / 2 - 46; }

	private void layoutHome(int cx, int cw, int bh, int gap, boolean multiplayer) {
		int y = homeTop() + (showHeaderLogo() ? 58 : 24);
		int bw = Math.min(260, cw);
		buttons.add(new Btn(cx, y, bw, bh + 6, "Play", Icons.PLAY, true, () -> setPage(Page.PLAY), "Favorites, recent servers and worlds", true));
		y += bh + 6 + gap;
		int half = (bw - gap) / 2;
		buttons.add(new Btn(cx, y, half, bh, "Singleplayer", Icons.CUBE, false, this::openSingleplayer, "Your worlds", true));
		buttons.add(new Btn(cx + half + gap, y, bw - half - gap, bh, "Multiplayer", Icons.GLOBE, false, this::openMultiplayer,
				multiplayer ? "Your servers" : "Multiplayer is disabled for this account", multiplayer));
		y += bh + gap;
		int q = (bw - gap * 3) / 4;
		String[][] quick = {{"Modules"}, {"Theme"}, {"HUD"}, {"Options"}};
		Runnable[] actions = {() -> openMenu(MenuPage.MODULES), () -> openMenu(MenuPage.THEME),
				() -> minecraft.setScreen(new HudEditorScreen(this)), this::openOptions};
		String[][] icons = {Icons.GRID, Icons.PALETTE, Icons.HUD, Icons.GEAR};
		for (int i = 0; i < 4; i++) {
			int w = i == 3 ? bw - 3 * (q + gap) : q;
			buttons.add(new Btn(cx + i * (q + gap), y, w, bh - 2, quick[i][0], icons[i], false, actions[i], null, true));
		}

		// Quick join panel to the right of the buttons
		int px = cx + bw + 24;
		int pw = Math.min(240, mainX + mainW - 18 - px);
		if (!cfg().quickJoin.on() || pw < 170) return;
		quickX = px;
		quickW = pw;
		int top = Math.max(24, homeTop() - 6);
		int bottom = vh - 22;
		int ry = top;

		Object last = lastPlayed();
		if (last != null) {
			labels.add(new String[] {String.valueOf(px), String.valueOf(ry), "Continue"});
			ry += 11;
			rows.add(last instanceof QuickPlay.Entry e ? new Row(px, ry, pw, 40, e, null, 0, false, true)
					: new Row(px, ry, pw, 40, null, (LevelSummary) last, 0, false, true));
			ry += 46;
		}

		if (multiplayer) {
			labels.add(new String[] {String.valueOf(px), String.valueOf(ry), "Favorites"});
			ry += 11;
			List<QuickPlay.Entry> favs = QuickPlay.favorites();
			if (favs.isEmpty()) {
				labels.add(new String[] {String.valueOf(px), String.valueOf(ry + 2), "~Star servers on the Play page to pin them here."});
				ry += 16;
			}
			for (int i = 0; i < favs.size() && i < 9 && ry + 18 <= bottom - 30; i++) {
				rows.add(new Row(px, ry, pw, 18, favs.get(i), null, i + 1, false, false));
				ry += 20;
			}
			ry += 4;
		}

		// Recent servers and worlds, newest first (skipping what's already shown)
		List<Object> recent = new ArrayList<>();
		if (multiplayer) for (QuickPlay.Entry e : QuickPlay.recent()) if (e != last && !QuickPlay.isFavorite(e.ip())) recent.add(e);
		for (LevelSummary w : QuickPlay.recentWorlds()) if (w != last) recent.add(w);
		recent.sort((a, b) -> Long.compare(time(b), time(a)));
		if (!recent.isEmpty() && ry + 11 + 18 <= bottom) {
			labels.add(new String[] {String.valueOf(px), String.valueOf(ry), "Recent"});
			ry += 11;
			for (Object o : recent) {
				if (ry + 18 > bottom) break;
				rows.add(o instanceof QuickPlay.Entry e ? new Row(px, ry, pw, 18, e, null, 0, false, false)
						: new Row(px, ry, pw, 18, null, (LevelSummary) o, 0, false, false));
				ry += 20;
			}
		}
	}

	private static long time(Object o) {
		return o instanceof QuickPlay.Entry e ? e.time() : ((LevelSummary) o).getLastPlayed();
	}

	private void layoutPlay(int cx, int cw, int bh, boolean multiplayer) {
		int top = 24;
		int y = top + 30;
		int half = (cw - 10) / 2;
		buttons.add(new Btn(cx, y, half, bh + 6, "Singleplayer", Icons.CUBE, false, this::openSingleplayer,
				"All worlds: play, create, edit and back up", true));
		buttons.add(new Btn(cx + half + 10, y, cw - half - 10, bh + 6, "Multiplayer", Icons.GLOBE, false, this::openMultiplayer,
				multiplayer ? "All servers, direct connect and adding servers" : "Multiplayer is disabled for this account", multiplayer));
		y += bh + 6 + 14;

		int bottom = vh - 34;
		int leftW = multiplayer ? (cw - 12) * 3 / 5 : 0;
		int rightX = multiplayer ? cx + leftW + 12 : cx;
		int rightW = cw - (rightX - cx);

		if (multiplayer) {
			// Tabs
			int tx = cx;
			for (ServerTab t : ServerTab.values()) {
				int tw = Ui.width(t.label) + 16;
				buttons.add(new Btn(tx, y, tw, 14, t.label, Icons.STAR, t == serverTab, () -> setTab(t), switch (t) {
					case FAVORITES -> "Servers you starred. Keys 1-9 join them from the home screen.";
					case RECENT -> "Servers you joined lately. Right-click to remove one.";
					case SAVED -> "Your Multiplayer list. Click the star to make a server a favorite.";
				}, true));
				tx += tw + 4;
			}
			int ly = y + 18;
			List<QuickPlay.Entry> list = serverList();
			serverListX = cx;
			serverListY = ly;
			serverListW = leftW;
			serverListH = bottom - ly;
			serverRowsFit = Math.max(1, (serverListH + 2) / 22);
			serverTotal = list.size();
			serverScroll = Math.max(0, Math.min(serverScroll, list.size() - serverRowsFit));
			if (list.isEmpty()) {
				String msg = switch (serverTab) {
					case FAVORITES -> "~No favorites yet. Open Saved or Recent and click a star.";
					case RECENT -> "~Servers you join show up here.";
					case SAVED -> "~Your Multiplayer server list is empty.";
				};
				labels.add(new String[] {String.valueOf(cx), String.valueOf(ly + 4), msg});
			}
			for (int i = serverScroll; i < list.size() && i < serverScroll + serverRowsFit; i++) {
				int hotkey = serverTab == ServerTab.FAVORITES && i < 9 ? i + 1 : 0;
				rows.add(new Row(cx, ly, leftW, 20, list.get(i), null, hotkey, true, false));
				ly += 22;
			}
		}

		labels.add(new String[] {String.valueOf(rightX), String.valueOf(y + 3), "Recent worlds"});
		int wy = y + 18;
		List<LevelSummary> worlds = QuickPlay.recentWorlds();
		if (worlds.isEmpty()) labels.add(new String[] {String.valueOf(rightX), String.valueOf(wy + 4), "~Your newest worlds show up here."});
		for (LevelSummary w : worlds) {
			if (wy + 20 > bottom) break;
			rows.add(new Row(rightX, wy, rightW, 20, null, w, 0, false, false));
			wy += 22;
		}

		buttons.add(new Btn(cx, vh - 28, 80, 18, "Back", Icons.BACK, false, () -> setPage(Page.HOME), null, true));
	}

	private void layoutAbout(int cx, int cw, int bh) {
		buttons.add(new Btn(cx, vh - 40, 80, 18, "Back", Icons.BACK, false, () -> setPage(Page.HOME), null, true));
		buttons.add(new Btn(cx + 86, vh - 40, 110, 18, "Home screen options", Icons.GEAR, false,
				() -> minecraft.setScreen(VantaMenuScreen.forModule(this, cfg())), "Background, sidebar and buttons", true));
	}

	// ------------------------------------------------------------ rendering

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		if (MenuBackground.wantsPanorama()) super.renderBackground(g, mouseX, mouseY, delta);
		MenuBackground.outOfWorld(g, width, height);
	}

	@Override
	public void tick() {
		super.tick();
		QuickPlay.tickPinger();
	}

	@Override
	public void removed() {
		super.removed();
		QuickPlay.clearPings();
		savedServers = null;
		worldsRequested = false;
	}

	@Override
	protected void renderScaled(GuiGraphics g, int mx, int my, float delta) {
		hoverHint = null;
		float open = openProgress();
		int slide = Math.round((1f - open) * 16f * Theme.animationDistance());

		if (sidebarW > 0) {
			Ui.rect(g, sidebarX, 0, sidebarW, vh, Theme.sidebar());
			Ui.rect(g, cfg().sidebarSide.get() == TitleScreenModule.Side.RIGHT ? sidebarX : sidebarX + sidebarW - 1, 0, 1, vh, Theme.border());
			if (showSidebarLogo()) drawLogo(g, sidebarX + 10, 12, Math.min(1.7f * Theme.headingScale(), (sidebarW - 20) / (float) Ui.width(Theme.clientName())));
		}

		g.pose().pushMatrix();
		g.pose().translate(slide, 0);
		switch (page) {
			case HOME -> renderHome(g);
			case PLAY -> renderPlay(g);
			case ABOUT -> renderAbout(g);
		}
		for (String[] l : labels) {
			boolean faint = l[2].startsWith("~");
			Ui.text(g, faint ? l[2].substring(1) : l[2], Integer.parseInt(l[0]), Integer.parseInt(l[1]), faint ? Theme.textFaint() : Theme.textDim());
		}
		g.pose().popMatrix();

		for (Btn b : buttons) {
			int bx = inMain(b.x()) ? b.x() + slide : b.x();
			boolean hov = b.enabled() && Ui.inside(mx, my, bx, b.y(), b.w(), b.h());
			if (Ui.inside(mx, my, bx, b.y(), b.w(), b.h())) hoverHint = b.hint();
			drawButton(g, b, bx, hov);
		}
		for (Row r : rows) {
			int rx = r.x() + slide;
			boolean hov = Ui.inside(mx, my, rx, r.y(), r.w(), r.h());
			boolean starHov = r.star() && Ui.inside(mx, my, rx + r.w() - STAR_W - 2, r.y(), STAR_W, r.h());
			if (hov) hoverHint = rowHint(r, starHov);
			drawRow(g, r, rx, hov, starHov);
		}
		if (page == Page.PLAY && serverTotal > serverRowsFit && serverListW > 0) {
			int trackH = serverListH;
			int thumbH = Math.max(10, trackH * serverRowsFit / serverTotal);
			int thumbY = serverListY + (trackH - thumbH) * serverScroll / Math.max(1, serverTotal - serverRowsFit);
			Ui.rect(g, serverListX + serverListW + 3 + slide, serverListY, 2, trackH, Colors.fade(Theme.border(), 0.6f));
			Ui.rect(g, serverListX + serverListW + 3 + slide, thumbY, 2, thumbH, Theme.accent());
		}

		// Footer
		Ui.text(g, "Minecraft " + VantaInfo.minecraftVersion() + "  -  " + Theme.clientName() + " " + VantaInfo.vantaVersion(),
				mainX + 8, vh - 12, Colors.fade(Theme.textFaint(), 0.9f));
		Ui.textRight(g, "Copyright Mojang AB. Do not distribute!", mainX + mainW - 8, vh - 12, Colors.fade(Theme.textFaint(), 0.9f));
		if (hoverHint != null) {
			int hw = Ui.width(hoverHint) + 12;
			int hx = Math.max(4, Math.min(mx + 8, vw - hw - 4));
			int hy = Math.min(my + 12, vh - 16);
			Ui.roundRect(g, hx, hy, hw, 13, Theme.radius(), Colors.withAlpha(Theme.background(), 0.92f));
			Ui.roundOutline(g, hx, hy, hw, 13, Theme.radius(), Theme.border());
			Ui.text(g, hoverHint, hx + 6, hy + 3, Theme.textDim());
		}
		drawMenuFps(g, vw - 8, 6);
	}

	private boolean inMain(int x) {
		return !(sidebarW > 0 && x >= sidebarX && x < sidebarX + sidebarW);
	}

	private String rowHint(Row r, boolean starHov) {
		if (starHov) return QuickPlay.isFavorite(r.server().ip()) ? "Remove from favorites" : "Add to favorites";
		if (r.server() != null) {
			String hint = "Join " + r.server().ip();
			if (r.hotkey() > 0 && page == Page.HOME) hint += "  (key " + r.hotkey() + ")";
			if (page == Page.PLAY && serverTab == ServerTab.RECENT) hint += "  -  right-click to remove";
			return hint;
		}
		LevelSummary w = r.world();
		return "Open " + w.getLevelName() + (w.isHardcore() ? "  (hardcore)" : "");
	}

	private void drawRow(GuiGraphics g, Row r, int x, boolean hov, boolean starHov) {
		Ui.card(g, x, r.y(), r.w(), r.h(), hov && !starHov, hov && !starHov ? Colors.fade(Theme.accent(), 0.7f) : 0);
		int tx = x + 6;
		int right = x + r.w() - 6 - (r.star() ? STAR_W : 0);

		if (r.big()) {
			int th = r.h() - 8;
			int tw = th * 16 / 9;
			boolean thumb = BackgroundImages.lastWorldThumbnail(g, x + 4, r.y() + 4, tw, th);
			if (thumb) {
				Ui.roundOutline(g, x + 4, r.y() + 4, tw, th, 1, Theme.border());
				tx = x + tw + 10;
			} else {
				Ui.icon(g, r.server() != null ? Icons.GLOBE : Icons.CUBE, tx, r.y() + (r.h() - 9) / 2, Theme.accent());
				tx += 15;
			}
			String title = r.server() != null ? r.server().name() : r.world().getLevelName();
			String sub = r.server() != null ? r.server().ip() + "  " + QuickPlay.ago(r.server().time())
					: "Singleplayer  " + QuickPlay.ago(r.world().getLastPlayed());
			Ui.text(g, Ui.ellipsize(title, right - tx - 40), tx, r.y() + 8, Theme.text());
			Ui.text(g, Ui.ellipsize(sub, right - tx), tx, r.y() + 21, Theme.textFaint());
			int pw = Ui.width("Play") + 14;
			int bx = x + r.w() - pw - 5;
			Ui.accentFill(g, bx, r.y() + 5, pw, 13, Theme.radius(), hov ? 0.12f : 0f);
			Ui.text(g, "Play", bx + 7, r.y() + 8, Colors.contrastText(Theme.accent()));
			if (r.server() != null) drawPing(g, r.server(), x + r.w() - 6, r.y() + 26);
			return;
		}

		int ty = r.y() + (r.h() - 8) / 2;
		if (r.hotkey() > 0) {
			Ui.roundRect(g, tx, r.y() + (r.h() - 11) / 2, 11, 11, 2, Colors.fade(Theme.panelHover(), 0.9f));
			Ui.textCentered(g, String.valueOf(r.hotkey()), tx + 6, r.y() + (r.h() - 8) / 2 + 1, Theme.textDim());
			tx += 15;
		} else {
			Ui.icon(g, r.server() != null ? Icons.GLOBE : Icons.CUBE, tx, r.y() + (r.h() - 9) / 2, hov ? Theme.accent() : Theme.textDim());
			tx += 14;
		}

		if (r.server() != null) {
			int used = drawPing(g, r.server(), right, ty);
			Ui.text(g, Ui.ellipsize(r.server().name(), right - used - tx - 6), tx, ty, hov ? Theme.text() : Theme.textDim());
			if (r.star()) {
				boolean fav = QuickPlay.isFavorite(r.server().ip());
				int color = fav ? 0xFFFACC15 : starHov ? Theme.text() : Colors.fade(Theme.textFaint(), 0.8f);
				Ui.icon(g, Icons.STAR, x + r.w() - STAR_W + 1, r.y() + (r.h() - 9) / 2, color);
			}
		} else {
			LevelSummary w = r.world();
			String when = QuickPlay.ago(w.getLastPlayed());
			Ui.textRight(g, when, right, ty, Theme.textFaint());
			Ui.text(g, Ui.ellipsize(w.getLevelName(), right - Ui.width(when) - tx - 8), tx, ty, hov ? Theme.text() : Theme.textDim());
		}
	}

	/** Draws "players  ping" right-aligned; returns the width used. */
	private int drawPing(GuiGraphics g, QuickPlay.Entry e, int right, int y) {
		QuickPlay.Status s = QuickPlay.status(minecraft, e);
		String ping = s.pingText();
		String players = s.playersText();
		int pingW = Ui.width(ping);
		Ui.textRight(g, ping, right, y, s.pingColor());
		int used = pingW;
		if (!players.isEmpty()) {
			Ui.textRight(g, players, right - pingW - 6, y, Theme.textFaint());
			used += Ui.width(players) + 6;
		}
		return used;
	}

	private void drawLogo(GuiGraphics g, int x, int y, float scale) {
		String name = Theme.clientName();
		if (Theme.ACCENT_MODE.get() == Theme.AccentMode.CHROMA) {
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			g.pose().scale(scale);
			Ui.chromaText(g, name, 0, 0, false);
			g.pose().popMatrix();
		} else {
			Ui.textScaled(g, name, x, y, scale, Theme.accent(), false);
		}
		int lineY = y + Math.round(9 * scale) + 2;
		Ui.hGradient(g, x, lineY, Math.max(10, Math.round(Ui.width(name) * scale * 0.35f)), 2, Theme.accent(), Theme.secondary());
	}

	private void drawButton(GuiGraphics g, Btn b, int x, boolean hov) {
		boolean tile = b.h() >= 60;
		int fill;
		int textColor;
		if (!b.enabled()) {
			fill = Colors.fade(Theme.panelRaised(), 0.5f);
			textColor = Theme.textFaint();
		} else if (b.primary()) {
			fill = hov ? Colors.mix(Theme.accent(), 0xFFFFFFFF, 0.12f) : Theme.accent();
			textColor = Colors.contrastText(Theme.accent());
		} else {
			fill = hov ? Theme.panelHover() : Theme.panelRaised();
			textColor = hov ? Theme.text() : Theme.textDim();
		}
		if (!b.enabled()) {
			Ui.roundRect(g, x, b.y(), b.w(), b.h(), Theme.radius(), fill);
		} else if (b.primary()) {
			if (Theme.SHADOWS.on()) Ui.roundRect(g, x + 1, b.y() + 2, b.w(), b.h(), Theme.radius(), Colors.fade(Theme.shadowColor(), 0.6f));
			Ui.accentFill(g, x, b.y(), b.w(), b.h(), Theme.radius(), hov ? 0.12f : 0f);
		} else {
			Ui.card(g, x, b.y(), b.w(), b.h(), hov, hov ? Colors.fade(Theme.accent(), 0.7f) : 0);
		}
		int iconColor = b.primary() ? textColor : hov ? Theme.accent() : Theme.textDim();
		if (!b.enabled()) iconColor = Theme.textFaint();
		if (tile) {
			Ui.icon(g, b.icon(), x + 12, b.y() + 12, iconColor);
			Ui.textScaled(g, b.label(), x + 12, b.y() + b.h() - 38, 1.25f, textColor, false);
			if (b.hint() != null) {
				List<String> lines = vanta.gui.items.BasicItems.Paragraph.wrap(b.hint(), b.w() - 24);
				for (int i = 0; i < Math.min(2, lines.size()); i++) Ui.text(g, lines.get(i), x + 12, b.y() + b.h() - 24 + i * 10, Theme.textFaint());
			}
			return;
		}
		int ty = b.y() + (b.h() - 8) / 2;
		if (b.label().isEmpty()) {
			Ui.icon(g, b.icon(), x + (b.w() - 9) / 2, b.y() + (b.h() - 9) / 2, iconColor);
		} else if (b.h() < 16) {
			Ui.text(g, Ui.ellipsize(b.label(), b.w() - 6), x + (b.w() - Math.min(Ui.width(b.label()), b.w() - 6)) / 2, ty, textColor);
		} else if (b.primary() || b.w() < 70) {
			int tw = Ui.width(b.label());
			boolean withIcon = b.w() >= tw + 24;
			int total = tw + (withIcon ? 13 : 0);
			int sx = x + (b.w() - total) / 2;
			if (withIcon) Ui.icon(g, b.icon(), sx, b.y() + (b.h() - 9) / 2, iconColor);
			Ui.text(g, Ui.ellipsize(b.label(), b.w() - 6), sx + (withIcon ? 13 : 0), ty, textColor);
		} else {
			Ui.icon(g, b.icon(), x + 7, b.y() + (b.h() - 9) / 2, iconColor);
			Ui.text(g, Ui.ellipsize(b.label(), b.w() - 26), x + 21, ty, textColor);
		}
	}

	private String welcome() {
		String raw = cfg().welcomeText.get();
		if (!raw.equals(cachedWelcomeSource)) {
			cachedWelcomeSource = raw;
			String name;
			try {
				name = minecraft.getUser().getName();
			} catch (RuntimeException e) {
				name = "player";
			}
			cachedWelcome = raw.replace("{name}", name);
		}
		return vanta.modules.impl.NickHiderModule.filter(cachedWelcome);
	}

	private void renderHome(GuiGraphics g) {
		int cx = mainX + 22;
		int y = homeTop();
		if (showHeaderLogo()) {
			drawLogo(g, cx, y, 3.2f * Theme.headingScale());
			y += 42;
		}
		int textW = (quickX > 0 ? quickX - cx - 10 : mainW - 44);
		Ui.text(g, Ui.ellipsize(welcome(), textW), cx, y, Theme.text());
		Ui.text(g, Ui.ellipsize("Ready when you are.", textW), cx, y + 11, Theme.textFaint());

		if (cfg().showInfo.on() && quickX < 0) renderInfoCards(g);
		else if (cfg().showInfo.on()) renderWarning(g, quickX + quickW, vh - 24);
	}

	private void renderInfoCards(GuiGraphics g) {
		String[][] cards = {
				{"Minecraft", VantaInfo.minecraftVersion()},
				{Theme.clientName(), VantaInfo.vantaVersion()},
				{"FPS", String.valueOf(FrameMetrics.fps())},
				{"Fabric Loader", VantaInfo.loaderVersion()},
		};
		int cw = 84;
		int ch = 32;
		int gap = 6;
		int totalH = cards.length * (ch + gap) - gap;
		int x = mainX + mainW - cw - 18;
		if (x < mainX + 22 + 270) return; // not enough room beside the buttons
		int y = vh / 2 - totalH / 2;
		for (String[] card : cards) {
			Ui.card(g, x, y, cw, ch, false, 0);
			Ui.text(g, Ui.ellipsize(card[0], cw - 12), x + 6, y + 5, Theme.textFaint());
			Ui.text(g, Ui.ellipsize(card[1], cw - 12), x + 6, y + 17, Theme.text());
			y += ch + gap;
		}
		renderWarning(g, mainX + mainW - 18, y + 4);
	}

	private void renderWarning(GuiGraphics g, int right, int y) {
		String problem = ConfigManager.get().lastProblem();
		String conflict = CompatDetector.titleScreenMods();
		String warn = problem != null ? "Settings were reset - see VANTA menu" : conflict != null ? conflict + " detected" : null;
		if (warn != null) Ui.textRight(g, Ui.ellipsize(warn, 240), right, y, 0xFFFBBF24);
	}

	private void renderPlay(GuiGraphics g) {
		int cx = mainX + 22;
		Ui.textScaled(g, "Play", cx, 24, 1.6f * Theme.headingScale(), Theme.text(), false);
		String sub = minecraft.allowsMultiplayer() ? "Click a server or world to join. Stars pin servers to your home screen."
				: "Click a world to open it.";
		Ui.textRight(g, Ui.ellipsize(sub, mainW - 120), mainX + mainW - 22, 30, Theme.textFaint());
	}

	private void renderAbout(GuiGraphics g) {
		int cx = mainX + 22;
		int y = 30;
		drawLogo(g, cx, y, 2f * Theme.headingScale());
		y += 30;
		String[] lines = {
				"Version " + VantaInfo.vantaVersion() + " for Minecraft " + VantaInfo.minecraftVersion(),
				"Fabric Loader " + VantaInfo.loaderVersion() + "  -  Fabric API " + VantaInfo.fabricApiVersion(),
				"",
				"Press " + vanta.input.VantaKeys.menuKeyName() + " in game to open the client menu.",
				"No account, no telemetry; settings live in config/vanta. Region lookup can be turned off.",
				"VANTA only changes visuals and menus. It never clicks, aims or changes reach.",
				"",
				CompatDetector.detected().isEmpty() ? "No known optimization mods detected."
						: "Detected: " + String.join(", ", CompatDetector.detected().stream().map(CompatDetector.Detected::name).toList()),
		};
		for (String line : lines) {
			Ui.text(g, Ui.ellipsize(line, mainW - 44), cx, y, line.startsWith("Version") ? Theme.text() : Theme.textDim());
			y += 11;
		}
	}

	// ------------------------------------------------------------ input

	@Override
	protected boolean onClick(double mx, double my, int button) {
		float open = openProgress();
		int slide = Math.round((1f - open) * 16f * Theme.animationDistance());
		for (Row r : rows) {
			int rx = r.x() + slide;
			if (!Ui.inside(mx, my, rx, r.y(), r.w(), r.h())) continue;
			if (button == 1 && r.server() != null && page == Page.PLAY && serverTab == ServerTab.RECENT) {
				QuickPlay.removeRecent(r.server().ip());
				layout();
				return true;
			}
			if (button != 0) return false;
			MenuSettings.click();
			if (r.star() && Ui.inside(mx, my, rx + r.w() - STAR_W - 2, r.y(), STAR_W + 2, r.h())) {
				QuickPlay.toggleFavorite(r.server().name(), r.server().ip());
				layout();
			} else if (r.server() != null) {
				joinServer(r.server());
			} else {
				joinWorld(r.world());
			}
			return true;
		}
		if (button != 0) return false;
		for (Btn b : buttons) {
			int bx = inMain(b.x()) ? b.x() + slide : b.x();
			if (b.enabled() && Ui.inside(mx, my, bx, b.y(), b.w(), b.h())) {
				MenuSettings.click();
				b.action().run();
				return true;
			}
		}
		return false;
	}

	@Override
	protected boolean onScroll(double mx, double my, double amount) {
		if (page != Page.PLAY || serverTotal <= serverRowsFit) return false;
		if (!Ui.inside(mx, my, serverListX, serverListY, serverListW + 8, serverListH)) return false;
		int before = serverScroll;
		serverScroll = Math.max(0, Math.min(serverTotal - serverRowsFit, serverScroll - (int) Math.signum(amount)));
		if (serverScroll != before) layout();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && page != Page.HOME) {
			setPage(Page.HOME);
			return true;
		}
		if (vanta.input.VantaKeys.isMenuKey(event)) {
			openMenu(MenuPage.HOME);
			return true;
		}
		// 1-9 join favorites (home screen and the Favorites tab)
		if (key >= org.lwjgl.glfw.GLFW.GLFW_KEY_1 && key <= org.lwjgl.glfw.GLFW.GLFW_KEY_9 && minecraft.allowsMultiplayer()
				&& (page == Page.HOME || page == Page.PLAY && serverTab == ServerTab.FAVORITES)) {
			int i = key - org.lwjgl.glfw.GLFW.GLFW_KEY_1;
			List<QuickPlay.Entry> favs = QuickPlay.favorites();
			if (i < favs.size()) {
				MenuSettings.click();
				joinServer(favs.get(i));
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean shouldCloseOnEsc() { return false; }

	@Override
	public void onClose() {
		// The home screen has nowhere to go "back" to.
	}

	@Override
	public boolean isPauseScreen() { return false; }

	/** Re-run layout when settings or lists that affect it change (e.g. after returning from the menu). */
	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		int sig = layoutSignature();
		if (sig != lastSignature) {
			lastSignature = sig;
			init();
		}
		super.render(g, mouseX, mouseY, delta);
	}

	private int lastSignature = Integer.MIN_VALUE;

	private int layoutSignature() {
		TitleScreenModule c = cfg();
		int h = Theme.UI_SCALE.value();
		h = h * 31 + Theme.SIDEBAR_WIDTH.value();
		h = h * 31 + c.sidebarSide.get().ordinal();
		h = h * 31 + c.sidebarStyle.get().ordinal();
		h = h * 31 + c.logoPosition.get().ordinal();
		h = h * 31 + c.buttonHeight.value();
		h = h * 31 + c.buttonSpacing.value();
		h = h * 31 + (Theme.SHOW_LOGO.on() ? 1 : 0);
		h = h * 31 + (c.quickJoin.on() ? 1 : 0);
		h = h * 31 + System.identityHashCode(QuickPlay.recentWorlds());
		h = h * 31 + QuickPlay.favorites().size();
		h = h * 31 + QuickPlay.recent().size();
		return h;
	}
}
