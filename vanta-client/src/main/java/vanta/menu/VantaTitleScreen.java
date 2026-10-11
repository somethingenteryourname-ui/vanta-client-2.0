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
 * Minecraft's own screens (so world creation, saved servers, direct connect and
 * authentication all work exactly as in vanilla), and "Vanilla menu" is always available.
 */
public final class VantaTitleScreen extends VantaScreen {
	private enum Page { HOME, PLAY, ABOUT }

	private record Nav(String label, String[] icon, Runnable action, String hint, boolean enabled) {
	}

	private record Btn(int x, int y, int w, int h, String label, String[] icon, boolean primary, Runnable action, String hint, boolean enabled) {
	}

	private Page page = Page.HOME;
	private final List<Btn> buttons = new ArrayList<>();
	private int sidebarX, sidebarW, mainX, mainW;
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
		openedAt = System.currentTimeMillis();
		layout();
	}

	// ------------------------------------------------------------ layout

	@Override
	protected void layout() {
		buttons.clear();
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
			nav.add(new Nav("Play", Icons.PLAY, () -> setPage(Page.PLAY), "Choose singleplayer or multiplayer", true));
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
			case PLAY -> layoutPlay(cx, cw, multiplayer);
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
		buttons.add(new Btn(cx, y, bw, bh + 6, "Play", Icons.PLAY, true, () -> setPage(Page.PLAY), "Singleplayer and multiplayer", true));
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
	}

	private void layoutPlay(int cx, int cw, boolean multiplayer) {
		int tileW = Math.min(180, (cw - 10) / 2);
		int tileH = 96;
		int y = vh / 2 - tileH / 2 + 6;
		buttons.add(new Btn(cx, y, tileW, tileH, "Singleplayer", Icons.CUBE, false, this::openSingleplayer,
				"Play, create, edit and back up worlds", true));
		buttons.add(new Btn(cx + tileW + 10, y, tileW, tileH, "Multiplayer", Icons.GLOBE, false, this::openMultiplayer,
				multiplayer ? "Saved servers, direct connect and adding servers" : "Multiplayer is disabled for this account", multiplayer));
		buttons.add(new Btn(cx, y + tileH + 10, 80, 18, "Back", Icons.BACK, false, () -> setPage(Page.HOME), null, true));
	}

	private void layoutAbout(int cx, int cw, int bh) {
		buttons.add(new Btn(cx, vh - 40, 80, 18, "Back", Icons.BACK, false, () -> setPage(Page.HOME), null, true));
		buttons.add(new Btn(cx + 86, vh - 40, 110, 18, "Home screen options", Icons.GEAR, false,
				() -> minecraft.setScreen(VantaMenuScreen.forModule(this, cfg())), "Background, sidebar and buttons", true));
	}

	// ------------------------------------------------------------ rendering

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		TitleScreenModule c = cfg();
		switch (c.background.get()) {
			case PANORAMA -> super.renderBackground(g, mouseX, mouseY, delta);
			case DARK -> g.fill(0, 0, width, height, Theme.background());
			case GRADIENT -> {
				g.fill(0, 0, width, height, Theme.background());
				g.fillGradient(0, 0, width, height, Colors.withAlpha(Theme.accent(), 0.22f), Colors.withAlpha(Theme.secondary(), 0.06f));
			}
			case ANIMATED -> renderAnimatedBackground(g);
		}
		int dim = c.backgroundDim.value();
		if (dim > 0) g.fill(0, 0, width, height, Colors.withAlpha(0xFF000000, dim / 100f));
	}

	/** A few slow, translucent diagonal bands: cheap (a handful of rectangles) and static with Reduced motion. */
	private void renderAnimatedBackground(GuiGraphics g) {
		g.fill(0, 0, width, height, Theme.background());
		double t = Theme.reducedMotion() ? 0 : (System.currentTimeMillis() % 60000L) / 60000.0;
		for (int i = 0; i < 4; i++) {
			double phase = (t + i * 0.25) % 1.0;
			int y = (int) (phase * (height + 120)) - 60;
			int color = Colors.withAlpha(i % 2 == 0 ? Theme.accent() : Theme.secondary(), 0.05f);
			g.fillGradient(0, y - 40, width, y, 0x00000000, color);
			g.fillGradient(0, y, width, y + 40, color, 0x00000000);
		}
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
		g.pose().popMatrix();

		for (Btn b : buttons) {
			boolean inMain = !(b.x() >= sidebarX && b.x() < sidebarX + sidebarW && sidebarW > 0);
			int bx = inMain ? b.x() + slide : b.x();
			boolean hov = b.enabled() && Ui.inside(mx, my, bx, b.y(), b.w(), b.h());
			if (Ui.inside(mx, my, bx, b.y(), b.w(), b.h())) hoverHint = b.hint();
			drawButton(g, b, bx, hov);
		}

		// Footer
		Ui.text(g, "Minecraft " + VantaInfo.minecraftVersion() + "  -  " + Theme.clientName() + " " + VantaInfo.vantaVersion(),
				mainX + 8, vh - 12, Colors.fade(Theme.textFaint(), 0.9f));
		Ui.textRight(g, "Copyright Mojang AB. Do not distribute!", mainX + mainW - 8, vh - 12, Colors.fade(Theme.textFaint(), 0.9f));
		if (hoverHint != null) {
			int hw = Ui.width(hoverHint) + 12;
			int hx = Math.min(mx + 8, vw - hw - 4);
			int hy = Math.min(my + 12, vh - 16);
			Ui.roundRect(g, hx, hy, hw, 13, Theme.radius(), Colors.withAlpha(Theme.background(), 0.92f));
			Ui.roundOutline(g, hx, hy, hw, 13, Theme.radius(), Theme.border());
			Ui.text(g, hoverHint, hx + 6, hy + 3, Theme.textDim());
		}
		drawMenuFps(g, vw - 8, 6);
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
		return cachedWelcome;
	}

	private void renderHome(GuiGraphics g) {
		int cx = mainX + 22;
		int y = homeTop();
		if (showHeaderLogo()) {
			drawLogo(g, cx, y, 3.2f * Theme.headingScale());
			y += 42;
		}
		Ui.text(g, Ui.ellipsize(welcome(), mainW - 44), cx, y, Theme.text());
		Ui.text(g, "Ready when you are.", cx, y + 11, Theme.textFaint());

		if (cfg().showInfo.on()) renderInfoCards(g);
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
		String problem = ConfigManager.get().lastProblem();
		String conflict = CompatDetector.titleScreenMods();
		String warn = problem != null ? "Settings were reset - see VANTA menu" : conflict != null ? conflict + " detected" : null;
		if (warn != null) Ui.textRight(g, Ui.ellipsize(warn, 240), mainX + mainW - 18, y + 4, 0xFFFBBF24);
	}

	private void renderPlay(GuiGraphics g) {
		int cx = mainX + 22;
		int y = vh / 2 - 48 - 30;
		Ui.textScaled(g, "Play", cx, y, 1.6f * Theme.headingScale(), Theme.text(), false);
		Ui.text(g, "Pick how you want to play. Both open Minecraft's own world and server screens.", cx, y + 18, Theme.textFaint());
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
				"Works offline. No account, no telemetry; settings live in config/vanta.",
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
		if (button != 0) return false;
		float open = openProgress();
		int slide = Math.round((1f - open) * 16f * Theme.animationDistance());
		for (Btn b : buttons) {
			boolean inMain = !(b.x() >= sidebarX && b.x() < sidebarX + sidebarW && sidebarW > 0);
			int bx = inMain ? b.x() + slide : b.x();
			if (b.enabled() && Ui.inside(mx, my, bx, b.y(), b.w(), b.h())) {
				MenuSettings.click();
				b.action().run();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && page != Page.HOME) {
			setPage(Page.HOME);
			return true;
		}
		if (vanta.input.VantaKeys.isMenuKey(event)) {
			openMenu(MenuPage.HOME);
			return true;
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

	/** Re-run layout when settings that affect it change (e.g. after returning from the menu). */
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
		return h;
	}
}
