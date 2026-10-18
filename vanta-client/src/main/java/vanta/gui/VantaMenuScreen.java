package vanta.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import vanta.branding.Theme;
import vanta.client.VantaInfo;
import vanta.compat.CompatDetector;
import vanta.config.ConfigManager;
import vanta.config.Setting;
import vanta.config.SettingRegistry;
import vanta.crosshair.CrosshairModule;
import vanta.gui.items.ActionRow;
import vanta.gui.items.BasicItems;
import vanta.gui.items.CrosshairPreview;
import vanta.gui.items.Item;
import vanta.gui.items.LayoutBuilder;
import vanta.gui.items.MenuHost;
import vanta.gui.items.ModuleCard;
import vanta.gui.items.OptionRow;
import vanta.gui.items.PresetCard;
import vanta.gui.items.SettingRow;
import vanta.hud.HudEditorScreen;
import vanta.hud.HudModule;
import vanta.hud.HudStyle;
import vanta.input.VantaKeys;
import vanta.metrics.FrameMetrics;
import vanta.modules.Category;
import vanta.modules.Module;
import vanta.modules.ModuleRegistry;
import vanta.modules.impl.TitleScreenModule;
import vanta.performance.PerformancePresets;
import vanta.performance.PerformanceState;
import vanta.performance.RecommendedMods;
import vanta.profiles.ProfileManager;
import vanta.profiles.ThemeManager;
import vanta.ui.Anim;
import vanta.ui.Colors;
import vanta.ui.Icons;
import vanta.ui.Ui;

/** The VANTA client menu (RIGHT SHIFT). Also opened from the home screen. */
public final class VantaMenuScreen extends VantaScreen implements MenuHost {
	private static MenuPage lastPage = MenuPage.HOME;

	private MenuPage page;
	private Module moduleView;
	private Category categoryFilter;
	private PerformancePresets.Preset selectedPreset;
	private String notice;
	private long noticeUntil;
	private String query = "";
	private int selectedResult;
	private final List<BasicItems.ResultRow> results = new ArrayList<>();

	private List<Item> items = List.of();
	private int contentHeight;
	private float scroll;
	private float scrollTarget;
	private Item dragItem;
	private boolean needsRebuild = true;
	private long pageSwitchedAt;
	private long lastFrameNanos;
	private int layoutSignature;

	private String highlightKey;
	private long highlightUntil;

	private EditBox search;

	// Layout (virtual pixels)
	private int panelX, panelY, panelW, panelH, sidebarW, sidebarX, contentX, contentW;
	private int navTop, navItemH;
	private int bodyX, bodyY, bodyW, bodyH;
	private int searchX, searchY, searchW, searchH = 16;
	private vanta.branding.UiFormat.Shell shell = vanta.branding.UiFormat.Shell.SIDEBAR;
	/** Where each page's button is, for every layout (sidebar, tabs, rail, dock). */
	private record NavSlot(MenuPage page, int x, int y, int w, int h, boolean iconOnly) {
	}
	private record Hit(int x, int y, int w, int h, Runnable action) {
		boolean has(double mx, double my) { return Ui.inside(mx, my, x, y, w, h); }
	}
	private final List<NavSlot> nav = new ArrayList<>();
	private final List<Hit> chromeHits = new ArrayList<>();
	private int headerH;
	private int closeX = -1, closeY;
	private float indX = -1, indY = -1, indW = -1;
	/** Vertical navigation that doesn't fit scrolls (sidebar, rail, Liquid). */
	private int navClipY0, navClipY1, navMaxScroll;
	private float navScroll;
	private boolean headerChipsFit, chipCounts = true;

	private int offsetX, offsetY;
	private int bodyOffsetX;

	public VantaMenuScreen(Screen parent) {
		this(parent, MenuSettings.REMEMBER_PAGE.on() ? lastPage : MenuPage.HOME);
	}

	public VantaMenuScreen(Screen parent, MenuPage page) {
		super(Component.literal("VANTA"), parent);
		this.page = page;
	}

	public static VantaMenuScreen forModule(Screen parent, Module module) {
		VantaMenuScreen s = new VantaMenuScreen(parent, MenuPage.MODULES);
		s.moduleView = module;
		return s;
	}

	@Override protected int minVirtualWidth() { return 400; }
	@Override protected int minVirtualHeight() { return 240; }

	private static int computeSignature() {
		int h = Theme.UI_SCALE.value();
		h = h * 31 + Theme.MENU_WIDTH.value();
		h = h * 31 + Theme.MENU_HEIGHT.value();
		h = h * 31 + Theme.SIDEBAR_WIDTH.value();
		h = h * 31 + Theme.SIDEBAR_SIDE.get().ordinal();
		h = h * 31 + (Theme.SIDEBAR_LABELS.on() ? 1 : 0);
		h = h * 31 + Theme.NAV_ITEM_HEIGHT.value();
		h = h * 31 + Theme.DENSITY.get().ordinal();
		h = h * 31 + Theme.LAYOUT.get().ordinal();
		h = h * 31 + Theme.CARD_WIDTH.value();
		h = h * 31 + Theme.MENU_POSITION.get().ordinal();
		h = h * 31 + (Theme.SHOW_FOOTER.on() ? 1 : 0);
		h = h * 31 + (Theme.SHOW_LOGO.on() ? 1 : 0);
		h = h * 31 + (Theme.CATEGORY_COLORS.on() ? 1 : 0);
		h = h * 31 + (MenuSettings.SHOW_DESCRIPTIONS.on() ? 1 : 0);
		h = h * 31 + Theme.TOGGLE_STYLE.get().ordinal();
		h = h * 31 + Theme.FONT.get().ordinal();
		h = h * 31 + (Theme.HUD_SAME_FONT.on() ? 1 : 0);
		h = h * 31 + HudStyle.FORMAT.get().ordinal();
		h = h * 31 + Theme.FORMAT.get().ordinal();
		h = h * 31 + Theme.CARD_LOOK.get().ordinal();
		h = h * 31 + (Theme.SIDEBAR_ICONS.on() ? 1 : 0);
		return h;
	}

	/** The menu to open from the menu key: the Click GUI when that format is chosen, otherwise this menu. */
	public static Screen open(Screen parent) {
		if (Theme.shell() == vanta.branding.UiFormat.Shell.CLICKGUI) return new ClickGuiScreen(parent);
		return new VantaMenuScreen(parent);
	}

	@Override
	protected void layout() {
		layoutSignature = computeSignature();
		shell = Theme.shell();
		if (shell == vanta.branding.UiFormat.Shell.CLICKGUI) shell = vanta.branding.UiFormat.Shell.SIDEBAR; // full menu from the Click GUI
		nav.clear();
		chromeHits.clear();
		closeX = -1;
		navMaxScroll = 0;
		navClipY0 = 0;
		navClipY1 = vh;
		headerChipsFit = false;
		boolean labels = Theme.SIDEBAR_LABELS.on();
		panelW = Math.max(Math.min(380, vw - 8), Math.min(vw - 16, Math.round(vw * Theme.MENU_WIDTH.value() / 100f)));
		panelH = Math.min(vh - 16, Math.max(230, Math.round(vh * Theme.MENU_HEIGHT.value() / 100f)));
		panelX = switch (Theme.MENU_POSITION.get()) {
			case LEFT -> 8;
			case RIGHT -> vw - panelW - 8;
			case CENTER -> (vw - panelW) / 2;
		};
		panelY = (vh - panelH) / 2;
		int count = MenuPage.values().length;
		boolean footer = Theme.SHOW_FOOTER.on();
		searchH = 16;

		switch (shell) {
			case SIDEBAR, RAIL -> {
				boolean rail = shell == vanta.branding.UiFormat.Shell.RAIL;
				if (rail) labels = false;
				sidebarW = labels ? Math.max(72, Math.min(Theme.SIDEBAR_WIDTH.value(), vw / 3)) : rail ? 34 : Math.max(30, Math.min(Theme.SIDEBAR_WIDTH.value(), 40));
				boolean right = Theme.SIDEBAR_SIDE.get() == Theme.Side.RIGHT;
				sidebarX = right ? panelX + panelW - sidebarW : panelX;
				contentX = right ? panelX : panelX + sidebarW;
				contentW = panelW - sidebarW;
				navTop = (Theme.SHOW_LOGO.on() && labels) || rail ? 36 : 10;
				int available = panelH - navTop - (labels && !rail ? 22 : 8);
				navItemH = Math.max(14, Math.min(rail ? 20 : Theme.NAV_ITEM_HEIGHT.value(), available / count - 2));
				for (MenuPage p : MenuPage.values()) {
					nav.add(new NavSlot(p, sidebarX + 6, panelY + navTop + p.ordinal() * (navItemH + 2), sidebarW - 12, navItemH, !labels));
				}
				setNavClip(panelY + navTop, available, count);
				headerH = Theme.SHOW_SUBTITLES.on() ? 40 : 32;
				bodyY = panelY + headerH;
				searchW = Math.max(90, Math.min(170, contentW * 2 / 5));
				searchX = contentX + contentW - 10 - searchW;
				searchY = panelY + 11;
			}
			case LIQUID -> {
				footer = false;
				sidebarW = Math.max(84, Math.min(Theme.SIDEBAR_WIDTH.value() + 8, Math.max(84, vw / 4)));
				boolean right = Theme.SIDEBAR_SIDE.get() == Theme.Side.RIGHT;
				sidebarX = right ? panelX + panelW - sidebarW : panelX;
				contentX = right ? panelX : panelX + sidebarW;
				contentW = panelW - sidebarW;
				headerH = 34;
				navTop = headerH + 8;
				int bottomButtons = 2 * 20 + 14;
				int available = panelH - navTop - bottomButtons;
				navItemH = Math.max(14, Math.min(20, available / count - 2));
				for (MenuPage p : MenuPage.values()) {
					nav.add(new NavSlot(p, sidebarX + 8, panelY + navTop + p.ordinal() * (navItemH + 2), sidebarW - 16, navItemH, false));
				}
				setNavClip(panelY + navTop, available, count);
				int by = panelY + panelH - 8 - 18;
				chromeHits.add(new Hit(sidebarX + 8, by - 22, sidebarW - 16, 18, this::openHudEditor));
				chromeHits.add(new Hit(sidebarX + 8, by, sidebarW - 16, 18, () -> openPage(MenuPage.THEME)));
				searchH = 18;
				searchX = contentX + 10;
				searchY = panelY + 8;
				searchW = Math.max(100, Math.min(220, contentW * 2 / 5));
				closeX = contentX + contentW - 8 - 18;
				closeY = panelY + 8;
				bodyY = panelY + headerH + 8;
				int room = closeX - 12 - (searchX + 10);
				chipCounts = true;
				int chipsW = chipsWidth(true);
				if (room - chipsW < 90) {
					chipCounts = false;
					chipsW = chipsWidth(false);
				}
				if (room - chipsW < searchW) searchW = Math.max(90, room - chipsW);
				headerChipsFit = room - searchW >= chipsW;
			}
			case TABS, DOCK -> {
				boolean dock = shell == vanta.branding.UiFormat.Shell.DOCK;
				if (dock) footer = false;
				sidebarW = 0;
				sidebarX = panelX;
				contentX = panelX;
				contentW = panelW;
				closeX = panelX + panelW - 8 - 16;
				closeY = panelY + 8;
				searchW = Math.max(90, Math.min(170, panelW / 4));
				searchX = closeX - 6 - searchW;
				searchY = panelY + 8;
				if (!dock) {
					// tabs: labels when they fit, otherwise icons (the current page keeps its label)
					int ty = panelY + 30;
					int total = 0;
					for (MenuPage p : MenuPage.values()) total += Ui.width(p.title) + (Theme.SIDEBAR_ICONS.on() ? 26 : 14) + 2;
					boolean iconsOnly = total > panelW - 20;
					int x = panelX + 10;
					for (MenuPage p : MenuPage.values()) {
						boolean io = iconsOnly && p != page;
						int w = io ? 18 : Ui.width(p.title) + (Theme.SIDEBAR_ICONS.on() ? 26 : 14);
						nav.add(new NavSlot(p, x, ty, w, 18, io));
						x += w + 2;
					}
					headerH = 54;
				} else {
					int n = MenuPage.values().length;
					int cell = Math.max(14, Math.min(20, (panelW - 40) / n - 3));
					int dockW = n * (cell + 3) + 9;
					int dx = panelX + (panelW - dockW) / 2 + 6;
					int dy = panelY + panelH - 8 - cell - 6;
					for (MenuPage p : MenuPage.values()) nav.add(new NavSlot(p, dx + p.ordinal() * (cell + 3), dy + 3, cell, cell, true));
					headerH = 32;
				}
				bodyY = panelY + headerH;
			}
			default -> {
			}
		}
		bodyX = contentX + 10;
		bodyW = contentW - 20;
		int bottom = switch (shell) {
			case DOCK -> 8 + Math.max(14, Math.min(20, (panelW - 40) / count - 3)) + 14;
			case LIQUID -> 8;
			default -> footer ? 18 : 8;
		};
		bodyH = panelY + panelH - bottom - bodyY;

		String value = search != null ? search.getValue() : query;
		search = new EditBox(font, toReal(searchX + 16), toReal(searchY + (searchH - 8) / 2), toReal(searchW - 20), toReal(10), Component.literal("Search"));
		search.setBordered(false);
		search.setMaxLength(48);
		search.setTextColor(Theme.text());
		search.setHint(Component.literal(shell == vanta.branding.UiFormat.Shell.LIQUID ? "Search anything..." : "Search everything"));
		search.setValue(value);
		search.setResponder(this::onSearchChanged);
		addRenderableWidget(search);
		needsRebuild = true;
	}

	private boolean verticalNav() {
		return shell == vanta.branding.UiFormat.Shell.SIDEBAR || shell == vanta.branding.UiFormat.Shell.RAIL || shell == vanta.branding.UiFormat.Shell.LIQUID;
	}

	private void setNavClip(int top, int available, int count) {
		navClipY0 = top - 1;
		navClipY1 = top + available;
		navMaxScroll = Math.max(0, count * (navItemH + 2) - 2 - available);
		// keep the current page in view
		int at = activePage().ordinal() * (navItemH + 2);
		if (at < navScroll) navScroll = at;
		else if (at + navItemH > navScroll + available) navScroll = at + navItemH - available;
		navScroll = Math.max(0, Math.min(navMaxScroll, navScroll));
	}

	/** A nav slot's y on screen (vertical navigation scrolls). */
	private int slotY(NavSlot n) {
		return verticalNav() ? n.y() - Math.round(navScroll) : n.y();
	}

	// ------------------------------------------------------------------ content

	private void onSearchChanged(String text) {
		String q = text.strip();
		if (q.equals(query)) return;
		query = q;
		selectedResult = 0;
		scroll = scrollTarget = 0;
		needsRebuild = true;
	}

	@Override
	public void rebuild() { needsRebuild = true; }

	private void notice(String message) {
		if (message == null) return;
		notice = message;
		noticeUntil = System.currentTimeMillis() + 5000;
		needsRebuild = true;
	}

	private void buildContent() {
		needsRebuild = false;
		int gap = Math.max(3, Math.round(6 * Theme.density()));
		LayoutBuilder lb = new LayoutBuilder(bodyW, gap);
		if (notice != null && System.currentTimeMillis() < noticeUntil) paragraph(lb, "> " + notice, 0xFFFBBF24);
		if (!query.isEmpty()) buildSearch(lb);
		else if (moduleView != null) buildModule(lb, moduleView);
		else {
			switch (page) {
				case HOME -> buildHome(lb);
				case MODULES -> buildModules(lb);
				case PERFORMANCE -> buildPerformance(lb);
				case VISUALS -> buildVisuals(lb);
				case HUD -> buildHud(lb);
				case OPTIMIZATIONS -> buildOptimizations(lb);
				case CHAT -> buildChat(lb);
				case COSMETICS -> buildCosmetics(lb);
				case FRIENDS -> buildFriends(lb);
				case MODS -> buildMods(lb);
				case CROSSHAIR -> buildCrosshair(lb);
				case THEME -> buildTheme(lb);
				case PROFILES -> buildProfiles(lb);
				case SETTINGS -> buildSettings(lb);
				case ABOUT -> buildAbout(lb);
			}
		}
		items = lb.items;
		contentHeight = lb.contentHeight();
		clampScroll();

		if (highlightKey != null) {
			for (Item it : items) {
				if (highlightKey.equals(it.key())) {
					scrollTarget = Math.max(0, it.y - 12);
					clampScroll();
					scroll = scrollTarget;
					break;
				}
			}
		}
	}

	private void section(LayoutBuilder lb, String title, String sub) {
		lb.full(new BasicItems.SectionTitle(title, sub), BasicItems.SectionTitle.height(sub));
	}

	private void paragraph(LayoutBuilder lb, String text, int color) {
		BasicItems.Paragraph p = new BasicItems.Paragraph(text, bodyW, color);
		lb.full(p, p.preferredHeight());
	}

	private void settingRows(LayoutBuilder lb, List<? extends Setting<?>> settings) {
		for (Setting<?> s : settings) {
			if (!s.isHidden()) lb.full(new SettingRow(s, this), SettingRow.height());
		}
	}

	private void optionRows(LayoutBuilder lb, List<OptionRow> rows) {
		for (OptionRow r : rows) lb.full(r, SettingRow.height());
	}

	private void moduleCards(LayoutBuilder lb, List<? extends Module> modules) {
		boolean list = Theme.LAYOUT.get() == Theme.Layout.LIST;
		List<Item> cards = new ArrayList<>();
		for (Module m : modules) cards.add(new ModuleCard(m, list, this));
		if (list) {
			for (Item c : cards) lb.full(c, ModuleCard.listHeight());
		} else {
			lb.grid(cards, ModuleCard.gridMinWidth(bodyW, lb.gap), ModuleCard.gridHeight());
		}
	}

	private List<Module> modulesIn(Category c) {
		List<Module> out = new ArrayList<>();
		for (Module m : ModuleRegistry.sorted(c)) out.add(m);
		return out;
	}

	private void stats(LayoutBuilder lb) {
		List<Item> stats = List.of(
				new BasicItems.StatCard("FPS", () -> String.valueOf(FrameMetrics.fps()),
						() -> "avg " + Math.round(FrameMetrics.averageFps()), null),
				new BasicItems.StatCard("Frame time", () -> String.format(Locale.ROOT, "%.1f ms", FrameMetrics.averageFrameMs()),
						() -> String.format(Locale.ROOT, "worst %.1f ms", FrameMetrics.worstFrameMs()), null),
				new BasicItems.StatCard("1% low", () -> Float.isNaN(FrameMetrics.onePercentLow()) ? "..." : String.valueOf(Math.round(FrameMetrics.onePercentLow())),
						() -> "slowest 1% of frames", null),
				new BasicItems.StatCard("Memory", () -> {
					Runtime rt = Runtime.getRuntime();
					return ((rt.totalMemory() - rt.freeMemory()) >> 20) + " MB";
				}, null, () -> {
					Runtime rt = Runtime.getRuntime();
					return (rt.totalMemory() - rt.freeMemory()) / (double) rt.maxMemory();
				}));
		lb.grid(stats, 84, 44);
		lb.full(new BasicItems.GraphCard(), 58);
	}

	// ---- Home

	private void buildHome(LayoutBuilder lb) {
		String problem = ConfigManager.get().lastProblem();
		if (problem != null) paragraph(lb, "! " + problem, 0xFFFBBF24);
		String conflict = CompatDetector.titleScreenMods();
		if (conflict != null) paragraph(lb, "! " + conflict + " also changes the title screen. If it looks wrong, turn off Home Screen in Modules.", 0xFFFBBF24);

		section(lb, "Performance", "Measured from real frame times on this machine");
		stats(lb);

		section(lb, "Quick controls", null);
		Module fps = ModuleRegistry.byId("fps");
		List<Item> quick = new ArrayList<>();
		quick.add(new BasicItems.ButtonItem("HUD editor", true, this::openHudEditor, "Drag HUD widgets into place."));
		quick.add(new BasicItems.ButtonItem(() -> "FPS overlay: " + (fps.isEnabled() ? "On" : "Off"), false, fps::toggle, "Show or hide the FPS widget."));
		quick.add(new BasicItems.ButtonItem("FPS presets", false, () -> openPage(MenuPage.PERFORMANCE), "Max FPS, Competitive, Balanced and more."));
		quick.add(new BasicItems.ButtonItem("Crosshair", false, () -> openPage(MenuPage.CROSSHAIR), "Design your crosshair."));
		quick.add(new BasicItems.ButtonItem("Chat", false, () -> openPage(MenuPage.CHAT), "Chat look, timestamps, mentions and filters."));
		quick.add(new BasicItems.ButtonItem("Profiles", false, () -> openPage(MenuPage.PROFILES), "Switch your whole setup."));
		quick.add(new BasicItems.ButtonItem("Formats", false, () -> openPage(MenuPage.THEME), "Change the whole look: Liquid Glass, Dock, Click GUI and more."));
		lb.row(quick, 92, 18);

		List<Module> recent = ModuleRegistry.recentlyChanged(3);
		if (!recent.isEmpty()) {
			section(lb, "Recently changed", null);
			for (Module m : recent) lb.full(new ModuleCard(m, true, this), ModuleCard.listHeight());
		}

		section(lb, "Client status", "Everything here is local. VANTA sends no telemetry.");
		lb.full(new BasicItems.KeyValueRow("Profile", ProfileManager::active, 0), 13);
		lb.full(new BasicItems.KeyValueRow("Performance preset", PerformanceState.ACTIVE_PRESET::get, 0), 13);
		lb.full(new BasicItems.KeyValueRow("Modules on", () -> {
			int n = 0;
			for (Module m : ModuleRegistry.all()) if (m.isEnabled()) n++;
			return n + " of " + ModuleRegistry.all().size();
		}, 0), 13);
		statusRows(lb);
	}

	private void statusRows(LayoutBuilder lb) {
		lb.full(new BasicItems.KeyValueRow("Minecraft", VantaInfo::minecraftVersion, 0), 13);
		lb.full(new BasicItems.KeyValueRow("VANTA", VantaInfo::vantaVersion, 0), 13);
		lb.full(new BasicItems.KeyValueRow("Fabric Loader", VantaInfo::loaderVersion, 0), 13);
		lb.full(new BasicItems.KeyValueRow("Fabric API", VantaInfo::fabricApiVersion, 0), 13);
		String mods = CompatDetector.detected().isEmpty() ? "None detected"
				: String.join(", ", CompatDetector.detected().stream().map(CompatDetector.Detected::name).toList());
		lb.full(new BasicItems.KeyValueRow("Detected mods", () -> mods, 0), 13);
		lb.full(new BasicItems.KeyValueRow("Settings file", () -> ConfigManager.get().lastProblem() == null ? "OK" : "See warning above",
				ConfigManager.get().lastProblem() == null ? 0xFF34D399 : 0xFFFBBF24), 13);
	}

	// ---- Modules

	private void buildModules(LayoutBuilder lb) {
		if (shell == vanta.branding.UiFormat.Shell.LIQUID && headerChipsFit) {
			// the filter chips live in the top bar
			moduleCards(lb, ModuleRegistry.sorted(categoryFilter));
			return;
		}
		List<Item> chips = new ArrayList<>();
		chips.add(chip("All", null));
		for (Category c : ModuleRegistry.usedCategories()) chips.add(chip(c.label, c));
		int cx = 0;
		int rowY = lb.y;
		for (Item c : chips) {
			int cw = ((BasicItems.ChipItem) c).preferredWidth();
			if (cx + cw > bodyW && cx > 0) {
				cx = 0;
				rowY += 16;
			}
			c.x = cx;
			c.y = rowY;
			c.w = cw;
			c.h = 13;
			lb.items.add(c);
			cx += cw + 4;
		}
		lb.y = rowY + 13 + lb.gap + 2;
		moduleCards(lb, ModuleRegistry.sorted(categoryFilter));
	}

	private Item chip(String label, Category category) {
		BasicItems.ChipItem chip = new BasicItems.ChipItem(label, () -> categoryFilter == category, () -> {
			categoryFilter = category;
			scroll = scrollTarget = 0;
			rebuild();
		});
		if (category != null && Theme.CATEGORY_COLORS.on()) chip.color(Theme.categoryColor(category));
		return chip;
	}

	// ---- Performance

	private void buildPerformance(LayoutBuilder lb) {
		section(lb, "Live", "Watch these while you change settings");
		stats(lb);
		lb.full(new BasicItems.KeyValueRow("FPS limited by", vanta.performance.FpsControl::reason, 0), 13);

		section(lb, "Presets", "Pick one to see exactly what it changes, then apply. Undo restores your old settings.");
		List<Item> cards = new ArrayList<>();
		for (PerformancePresets.Preset p : PerformancePresets.Preset.values()) {
			cards.add(new PresetCard(p.label, p.description, () -> selectedPreset == p,
					() -> PerformanceState.ACTIVE_PRESET.get().equals(p.label), () -> {
						selectedPreset = selectedPreset == p ? null : p;
						rebuild();
					}));
		}
		lb.grid(cards, 130, 42);
		if (selectedPreset != null) {
			PerformancePresets.Preset p = selectedPreset;
			section(lb, p.label + " will change", "Current -> new");
			for (PerformancePresets.Change c : PerformancePresets.changes(p)) {
				String current = PerformancePresets.currentDisplay(c.name());
				boolean same = current.equals(c.display());
				lb.full(new BasicItems.KeyValueRow(c.name(), () -> same ? c.display() + " (no change)" : current + "  ->  " + c.display(),
						same ? 0 : Theme.accent()), 13);
			}
			for (String v : PerformancePresets.vantaChanges(p)) lb.full(new BasicItems.KeyValueRow("VANTA", () -> v, 0), 13);
			lb.space(2);
			List<Item> buttons = new ArrayList<>();
			buttons.add(new BasicItems.ButtonItem("Apply " + p.label, true, () -> {
				PerformancePresets.apply(p);
				selectedPreset = null;
				notice(p.label + " applied. Use Undo to go back.");
			}, "Apply these settings now."));
			buttons.add(new BasicItems.ButtonItem("Cancel", false, () -> {
				selectedPreset = null;
				rebuild();
			}, null));
			lb.row(buttons, 100, 18);
		}
		if (PerformancePresets.canUndo()) {
			lb.row(List.of(new BasicItems.ButtonItem("Undo last preset", false, () -> notice(PerformancePresets.undo()
					? "Restored your settings from before the preset." : "Couldn't undo - the saved data was missing."),
					"Put back every setting the last preset changed.")), 120, 18);
		}

		section(lb, "VANTA boosters", "Rendering-only optimizations that work alongside Sodium");
		moduleCards(lb, modulesIn(Category.PERFORMANCE));

		section(lb, "Video settings that matter", "These are Minecraft's own settings, gathered in one place");
		optionRows(lb, VanillaOptions.performance(this));

		section(lb, "Recommended mods", RecommendedMods.installedCount() + " of " + RecommendedMods.MODS.size()
				+ " installed. These give the biggest FPS gains and work with VANTA.");
		for (RecommendedMods.Rec r : RecommendedMods.MODS) {
			lb.full(new ActionRow(() -> r.name() + (r.installed() ? "  -  installed" : "  -  not installed"), r::why,
					r::installed, r.installed() ? "Installed and working alongside VANTA." : "Get it free on Modrinth (Fabric, 1.21.11)."), 28);
		}
	}

	// ---- Visuals

	private void buildVisuals(LayoutBuilder lb) {
		section(lb, "Visual modules", null);
		moduleCards(lb, modulesIn(Category.VISUALS));
		section(lb, "Camera and comfort", "Minecraft's own settings");
		optionRows(lb, VanillaOptions.visuals(this));
		buildPacks(lb);
	}

	private void buildPacks(LayoutBuilder lb) {
		section(lb, "Resource packs", "Switch packs here, then Apply once (Minecraft reloads a single time). Top of the list wins.");
		List<vanta.packs.PackToggle.Row> rows = vanta.packs.PackToggle.rows();
		if (rows.isEmpty()) paragraph(lb, "No resource packs found. Put .zip packs in the resource packs folder, then press Refresh.", Theme.textFaint());
		for (vanta.packs.PackToggle.Row r : rows) {
			ActionRow row = new ActionRow(() -> r.title() + (r.pending() ? "  (changed)" : ""), r::description, r::on,
					"Turn this pack on or off. Changes apply when you press Apply.");
			row.action(r.on() ? "Turn off" : "Turn on", () -> {
				vanta.packs.PackToggle.toggle(r.id());
				rebuild();
			});
			if (r.on()) row.action("Up", () -> {
				vanta.packs.PackToggle.raise(r.id());
				rebuild();
			});
			lb.full(row, 26);
		}
		List<Item> b = new ArrayList<>();
		b.add(new BasicItems.ButtonItem(() -> vanta.packs.PackToggle.hasChanges() ? "Apply changes" : "No changes", true, () -> {
			if (!vanta.packs.PackToggle.hasChanges()) return;
			vanta.packs.PackToggle.apply();
			notice("Reloading resource packs...");
		}, "Reload Minecraft's resources with your pack changes."));
		b.add(new BasicItems.ButtonItem("Undo changes", false, () -> {
			vanta.packs.PackToggle.discard();
			rebuild();
		}, "Forget the changes you haven't applied."));
		b.add(new BasicItems.ButtonItem("Open folder", false, () -> openFolder(vanta.packs.PackToggle.folder()), "Add packs by copying them here."));
		b.add(new BasicItems.ButtonItem("Refresh", false, () -> {
			vanta.packs.PackToggle.rescan();
			rebuild();
		}, "Look for new packs in the folder."));
		lb.row(b, 92, 18);
	}

	// ---- Cosmetics

	private void buildCosmetics(LayoutBuilder lb) {
		vanta.cosmetics.CosmeticsModule c = (vanta.cosmetics.CosmeticsModule) ModuleRegistry.byId("cosmetics");
		paragraph(lb, "Capes show in third person (F5) and on your inventory player. If your cape doesn't show, turn on Cape in "
				+ "Options > Skin Customization. Other VANTA players see your cape and trail on servers with the VANTA Bridge plugin, "
				+ "or when you share a presence server (Client Badge settings).", Theme.textFaint());
		lb.full(new SettingRow(c.enabledSetting(), this), SettingRow.height());
		section(lb, "Cape", null);
		List<Item> capes = new ArrayList<>();
		for (vanta.cosmetics.Cosmetics.Cape cape : vanta.cosmetics.Cosmetics.Cape.values()) {
			capes.add(new BasicItems.ButtonItem(() -> (c.cape.get() == cape ? "> " : "") + cape.label(), false, () -> c.cape.set(cape),
					cape == vanta.cosmetics.Cosmetics.Cape.NONE ? "No cape." : "Wear the " + cape.label() + " cape."));
		}
		lb.grid(capes, 80, 18);
		section(lb, "Trail", "Particles behind you while you move");
		List<Item> trails = new ArrayList<>();
		for (vanta.cosmetics.Cosmetics.Trail t : vanta.cosmetics.Cosmetics.Trail.values()) {
			trails.add(new BasicItems.ButtonItem(() -> (c.trail.get() == t ? "> " : "") + t.label(), false, () -> c.trail.set(t),
					t == vanta.cosmetics.Cosmetics.Trail.NONE ? "No trail." : t.label() + " follow you."));
		}
		lb.grid(trails, 90, 18);
		section(lb, "Options", null);
		settingRows(lb, List.of(c.density, c.trailFirstPerson, c.showOthers, c.share));
		lb.full(new BasicItems.KeyValueRow("VANTA players with cosmetics here", () -> String.valueOf(vanta.cosmetics.Cosmetics.others().size()), 0), 13);
	}

	// ---- Friends

	private void buildFriends(LayoutBuilder lb) {
		vanta.friends.FriendsModule f = vanta.friends.FriendsModule.get();
		lb.full(new SettingRow(f.enabledSetting(), this), SettingRow.height());
		lb.row(List.of(new BasicItems.ButtonItem("Add a friend", true, () -> minecraft.setScreen(new TextInputScreen(this, "Add a friend",
				"Their Minecraft name. In game you can also type /friend add <name>.", "", 16, f::add)), "Add someone to your friends list.")), 120, 18);
		List<String> names = f.names();
		section(lb, "Friends", names.isEmpty() ? "No friends yet" : names.size() + " friends");
		for (String n : names) {
			lb.full(new ActionRow(() -> n, () -> vanta.friends.FriendsModule.isOnline(n) ? "Online on this server" : "Not on this server",
					() -> vanta.friends.FriendsModule.isOnline(n), "Remove this friend.")
					.danger("Remove", () -> {
						f.remove(n);
						rebuild();
					}), 26);
		}
		section(lb, "Options", null);
		settingRows(lb, List.of(f.color, f.nametags, f.tabList, f.notify));
		paragraph(lb, "Commands: /friend add <name>, /friend remove <name>, /friend list. Your list stays on your computer.", Theme.textFaint());
	}

	// ---- Mods

	private boolean showLibraries;

	private void buildMods(LayoutBuilder lb) {
		List<vanta.compat.InstalledMods.Entry> mods = vanta.compat.InstalledMods.list();
		long libs = mods.stream().filter(vanta.compat.InstalledMods.Entry::library).count();
		lb.row(List.of(
				new BasicItems.ButtonItem("Open mods folder", false, () -> openFolder(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("mods")),
						"Add or remove mods here, then restart Minecraft."),
				new BasicItems.ButtonItem(() -> showLibraries ? "Hide libraries" : "Show libraries (" + libs + ")", false, () -> {
					showLibraries = !showLibraries;
					rebuild();
				}, "Libraries are parts of other mods (like Fabric API's modules).")), 120, 18);
		if (!vanta.compat.InstalledMods.modMenuInstalled()) {
			paragraph(lb, "Tip: install Mod Menu to open other mods' settings from here.", Theme.textFaint());
		}
		section(lb, "Installed", (mods.size() - libs) + " mods" + (libs > 0 ? " + " + libs + " libraries" : ""));
		for (vanta.compat.InstalledMods.Entry e : mods) {
			if (e.library() && !showLibraries) continue;
			String sub = (e.description() == null || e.description().isBlank() ? "" : e.description() + "  ")
					+ (e.authors().isEmpty() ? "" : "by " + e.authors());
			ActionRow row = new ActionRow(() -> e.name() + "  " + e.version(), () -> sub, null, e.id());
			if (vanta.compat.InstalledMods.modMenuInstalled()) {
				row.action("Settings", () -> {
					net.minecraft.client.gui.screens.Screen sc = vanta.compat.InstalledMods.configScreen(e.id(), this);
					if (sc != null) minecraft.setScreen(sc);
					else notice(e.name() + " has no settings screen.");
				});
			}
			if (!e.homepage().isEmpty()) row.action("Website", () -> Util.getPlatform().openUri(e.homepage()));
			lb.full(row, 26);
		}
	}

	// ---- HUD

	private void buildHud(LayoutBuilder lb) {
		lb.row(List.of(
				new BasicItems.ButtonItem("Open HUD editor", true, this::openHudEditor, "Drag, scale and hide widgets on the real screen."),
				new BasicItems.ButtonItem("Reset all positions", false, () -> minecraft.setScreen(new ConfirmDialog(this, "Reset HUD layout?",
						"Moves every widget back to its default position and size.", "Reset", () -> {
							for (HudModule m : ModuleRegistry.hud()) {
								m.posX.reset();
								m.posY.reset();
								m.scale.reset();
							}
						})), "Put every widget back where it started.")), 120, 18);
		section(lb, "HUD style", "Applies to every widget");
		settingRows(lb, HudStyle.settings());
		section(lb, "Widgets", "Hidden widgets are not drawn or updated, so they cost nothing");
		moduleCards(lb, new ArrayList<>(ModuleRegistry.hud()));
		section(lb, "Vanilla HUD and warnings", null);
		moduleCards(lb, List.of(ModuleRegistry.byId("hud_tweaks"), ModuleRegistry.byId("durability_warnings")));
	}

	// ---- Optimizations

	private void buildOptimizations(LayoutBuilder lb) {
		vanta.optimize.OptimizationsModule o = (vanta.optimize.OptimizationsModule) ModuleRegistry.byId("optimizations");
		lb.full(new SettingRow(o.enabledSetting(), this), SettingRow.height());
		section(lb, "PvP", "Faster-feeling crystal and anchor fights and a clearer view");
		paragraph(lb, "Client-side crystals and anchors remove them on your screen as soon as your hit or click is certain to destroy them, "
				+ "instead of waiting for your ping. VANTA sends exactly the same packets as vanilla. Some servers don't allow these, so check the rules.",
				Theme.textFaint());
		settingRows(lb, List.of(o.crystals, o.anchors, o.staticCrystals, o.noDeathAnimation, o.instantSneak));
		vanta.modules.impl.TotemModule totem = (vanta.modules.impl.TotemModule) ModuleRegistry.byId("totem");
		if (totem != null) {
			section(lb, "Totem pops", "Size, see-through style, length, particles and sound of totem pops");
			lb.full(new SettingRow(totem.enabledSetting(), this), SettingRow.height());
			settingRows(lb, totem.visibleSettings());
		}
		section(lb, "Smoothness", "Less motion and fewer costly effects");
		settingRows(lb, List.of(o.noIdleThrottle, o.noMenuBlur, o.noViewBobbing, o.noHurtTilt, o.freeMemory));
		lb.full(new BasicItems.KeyValueRow("FPS limited by", vanta.performance.FpsControl::reason, 0), 13);
		section(lb, "Performance modules", "More ways to raise FPS; presets live on the Performance page");
		List<Module> mods = new ArrayList<>();
		for (String id : new String[] {"particles", "entity_culling", "background_fps", "screen_fps", "dynamic_render_distance", "screen_effects"}) {
			Module m = ModuleRegistry.byId(id);
			if (m != null) mods.add(m);
		}
		moduleCards(lb, mods);
	}

	// ---- Chat

	private void buildChat(LayoutBuilder lb) {
		vanta.chat.ChatModule c = vanta.chat.ChatModule.instance();
		lb.full(new vanta.gui.items.ChatPreview(c), 92);
		lb.full(new SettingRow(c.enabledSetting(), this), SettingRow.height());
		section(lb, "Look", "Your theme colors, client name and logo, in singleplayer and on servers");
		settingRows(lb, List.of(c.style, c.panelOpacity, c.rounded, c.accentBar, c.width, c.fullWhileTyping, c.smooth));
		section(lb, "Logo watermark", "Your logo and client name faded into the chat background");
		settingRows(lb, List.of(c.watermark, c.watermarkOpacity, c.watermarkSize, c.watermarkPosition, c.watermarkAnimation,
				c.watermarkWhileTyping));
		section(lb, "Input box", null);
		settingRows(lb, List.of(c.inputBox, c.inputLogo));
		section(lb, "Tools", "Tabs, search (Ctrl+F), right-click to copy, player heads and emoji");
		settingRows(lb, List.of(c.tabs, c.privateWords, c.partyWords, c.search, c.copy, c.heads, c.emojiButton, c.emojiCodes));
		section(lb, "Messages", null);
		settingRows(lb, List.of(c.timestamps, c.timeFormat, c.stampColor, c.stack, c.history, c.fadeSeconds, c.neverFade, c.keepOnLeave));
		section(lb, "Mentions", "When someone says your name");
		settingRows(lb, List.of(c.mentions, c.mentionSound, c.mentionWords));
		section(lb, "Filters", "Hidden messages never show up in chat");
		settingRows(lb, List.of(c.mutedWords, c.hideJoinLeave, c.hideBlank));
		section(lb, "Minecraft chat options", "The same settings as Options > Chat Settings");
		optionRows(lb, VanillaOptions.chat(this));
		lb.space(4);
		lb.row(List.of(
				new BasicItems.ButtonItem("Clear chat", false, () -> {
					if (minecraft.gui != null) minecraft.gui.getChat().clearMessages(false);
					notice("Chat cleared.");
				}, "Remove every message from chat (your sent-message history stays)."),
				new BasicItems.ButtonItem("Reset chat settings", false, () -> minecraft.setScreen(new ConfirmDialog(this,
						"Reset chat settings?", "Every VANTA chat setting goes back to default.", "Reset", c::resetToDefaults)), null)), 120, 18);
	}

	// ---- Crosshair

	private void buildCrosshair(LayoutBuilder lb) {
		CrosshairModule c = ModuleRegistry.CROSSHAIR;
		lb.full(new CrosshairPreview(c), 70);
		lb.full(new SettingRow(c.enabledSetting(), this), SettingRow.height());
		section(lb, "Presets", null);
		List<Item> presets = new ArrayList<>();
		for (CrosshairModule.Preset p : CrosshairModule.Preset.values()) {
			presets.add(new BasicItems.ButtonItem(p.label(), false, () -> {
				c.applyPreset(p);
				c.setEnabled(true);
			}, "Load the " + p.label() + " crosshair. You can tweak it after."));
		}
		lb.row(presets, 80, 18);
		lb.row(List.of(new BasicItems.ButtonItem("Open crosshair editor", true, () -> minecraft.setScreen(new vanta.crosshair.CrosshairDrawScreen(this, c)),
				"Start from a template or three sliders, touch it up with easy tools, then save.")), 160, 18);
		section(lb, "Shape and size", null);
		settingRows(lb, List.of(c.shape, c.size, c.length, c.thickness, c.gap, c.dotSize, c.circleRadius));
		section(lb, "Color", null);
		settingRows(lb, List.of(c.color, c.chroma, c.outline, c.outlineColor, c.hitColor, c.targetColor));
		section(lb, "Behavior", null);
		settingRows(lb, List.of(c.dynamic, c.attackIndicator, c.thirdPerson));
		lb.space(4);
		lb.row(List.of(new BasicItems.ButtonItem("Reset crosshair", false, () -> minecraft.setScreen(new ConfirmDialog(this,
				"Reset crosshair?", "All crosshair settings go back to default.", "Reset", c::resetToDefaults)), null)), 110, 18);
	}

	// ---- Theme

	private void buildTheme(LayoutBuilder lb) {
		section(lb, "Interface format", "A whole layout and look in one click: " + Theme.FORMAT.get().label() + " is on. Fine-tune anything below.");
		List<Item> formats = new ArrayList<>();
		for (vanta.branding.UiFormat f : vanta.branding.UiFormat.values()) formats.add(new vanta.gui.items.FormatCard(f, this));
		lb.grid(formats, 96, 74);
		settingRows(lb, List.of(Theme.CARD_LOOK, Theme.BLUR));

		section(lb, "Font", "Used for every VANTA menu, screen, notification and (unless you split it) the HUD. "
				+ Theme.FONT.get().label() + " is on.");
		List<Item> fonts = new ArrayList<>();
		for (vanta.ui.UiFont f : vanta.ui.UiFont.values()) fonts.add(new vanta.gui.items.FontCard(f, Theme.FONT, this));
		lb.grid(fonts, 112, vanta.gui.items.FontCard.height());
		settingRows(lb, List.of(Theme.HUD_SAME_FONT));
		if (!Theme.HUD_SAME_FONT.on()) {
			section(lb, "HUD font", "Widgets, waypoints and warnings. " + Theme.HUD_FONT.get().label() + " is on.");
			List<Item> hudFonts = new ArrayList<>();
			for (vanta.ui.UiFont f : vanta.ui.UiFont.values()) hudFonts.add(new vanta.gui.items.FontCard(f, Theme.HUD_FONT, this));
			lb.grid(hudFonts, 112, vanta.gui.items.FontCard.height());
		}

		section(lb, "Styles", "Complete looks: surfaces, text, corners, card style and palette");
		List<Item> styles = new ArrayList<>();
		for (Theme.Style s : Theme.Style.values()) styles.add(new BasicItems.StyleItem(s, () -> Theme.applyStyle(s)));
		lb.grid(styles, 86, 20);

		section(lb, "Palettes", "One click sets the accent and secondary colors");
		List<Item> swatches = new ArrayList<>();
		for (Theme.Palette p : Theme.Palette.values()) swatches.add(new BasicItems.SwatchItem(p));
		lb.grid(swatches, 78, 20);

		section(lb, "Colors", "Shared by the home screen, this menu, the HUD editor and dialogs");
		settingRows(lb, List.of(Theme.CLIENT_NAME, Theme.ACCENT_MODE, Theme.ACCENT, Theme.SECONDARY, Theme.CHROMA_SPEED,
				Theme.CHROMA_SATURATION, Theme.BACKGROUND, Theme.PANEL, Theme.BORDER, Theme.TEXT, Theme.TEXT_SECONDARY, Theme.CATEGORY_COLORS));
		if (Theme.CATEGORY_COLORS.on()) settingRows(lb, new ArrayList<>(Theme.CATEGORY_COLOR.values()));

		section(lb, "Surfaces", null);
		settingRows(lb, List.of(Theme.CARD_STYLE, Theme.PANEL_OPACITY, Theme.CORNER_RADIUS, Theme.BORDER_WIDTH, Theme.SHADOWS,
				Theme.SHADOW_STRENGTH, Theme.HOVER_STRENGTH, Theme.TOGGLE_STYLE, Theme.TEXT_SHADOW, Theme.UPPERCASE_HEADINGS));

		section(lb, "Layout", null);
		settingRows(lb, List.of(Theme.UI_SCALE, Theme.FONT_SCALE, Theme.LAYOUT, Theme.DENSITY, Theme.CARD_WIDTH, Theme.MENU_POSITION,
				Theme.MENU_WIDTH, Theme.MENU_HEIGHT, Theme.SHOW_SUBTITLES, Theme.SHOW_FOOTER, Theme.SHOW_LOGO, Theme.SHOW_MENU_FPS));

		section(lb, "Sidebar", null);
		settingRows(lb, List.of(Theme.SIDEBAR_SIDE, Theme.SIDEBAR_WIDTH, Theme.SIDEBAR_LABELS, Theme.SIDEBAR_ICONS, Theme.NAV_ITEM_HEIGHT, Theme.INDICATOR));

		section(lb, "Backdrop and motion", null);
		settingRows(lb, List.of(Theme.BACKGROUND_DIM, Theme.BACKDROP, Theme.OPEN_ANIMATION, Theme.ANIMATION_SPEED,
				Theme.ANIMATION_INTENSITY, Theme.REDUCED_MOTION));

		section(lb, "My themes", "Saved in config/vanta/themes - share the files with friends");
		for (String name : ThemeManager.list()) {
			lb.full(new ActionRow(() -> name, null, null, "Apply, or delete, this saved theme.")
					.action("Apply", () -> notice(orDone(ThemeManager.apply(name), "Applied theme " + name + ".")))
					.danger("Delete", () -> minecraft.setScreen(new ConfirmDialog(this, "Delete theme " + name + "?",
							"The theme file is removed.", "Delete", () -> notice(orDone(ThemeManager.delete(name), "Deleted " + name + "."))))), 22);
		}
		List<Item> themeButtons = new ArrayList<>();
		themeButtons.add(new BasicItems.ButtonItem("Save current theme", true, () -> minecraft.setScreen(new TextInputScreen(this,
				"Save theme", "Name for this theme (letters, numbers, spaces).", "", 32, ThemeManager::save)), "Store this look to switch back to later."));
		themeButtons.add(new BasicItems.ButtonItem("Open themes folder", false, () -> openFolder(ThemeManager.folder()), "Add theme files from friends here."));
		themeButtons.add(new BasicItems.ButtonItem("Refresh", false, () -> {
			ThemeManager.refresh();
			rebuild();
		}, "Re-read the themes folder."));
		lb.row(themeButtons, 100, 18);
		lb.space(4);
		lb.row(List.of(new BasicItems.ButtonItem("Restore default theme", false, () -> minecraft.setScreen(new ConfirmDialog(this,
				"Restore default theme?", "Resets every color, layout and motion setting on this page.", "Restore", Theme::restoreDefaults)),
				"Undo all theme changes.")), 140, 18);
	}

	private static String orDone(String error, String success) {
		return error != null ? error : success;
	}

	private void openFolder(java.nio.file.Path path) {
		try {
			java.nio.file.Files.createDirectories(path);
			Util.getPlatform().openPath(path);
		} catch (Exception e) {
			notice("Couldn't open the folder: " + path.toAbsolutePath());
		}
	}

	// ---- Profiles

	private void buildProfiles(LayoutBuilder lb) {
		paragraph(lb, "A profile is your whole VANTA setup: modules, HUD layout, crosshair, theme and menu settings. "
				+ "Switching saves your current profile first. Profiles don't change Minecraft's video settings - use Performance presets for that.", 0);
		List<Item> top = new ArrayList<>();
		top.add(new BasicItems.ButtonItem("New from current", true, () -> minecraft.setScreen(new TextInputScreen(this,
				"New profile", "Name for the new profile.", "", 32, ProfileManager::create)), "Save your current setup as a new profile."));
		top.add(new BasicItems.ButtonItem(() -> "Save to " + ProfileManager.active(), false, () -> notice(ProfileManager.save(ProfileManager.active())
				? "Saved to " + ProfileManager.active() + "." : "Couldn't save the profile file."), "Store your current setup in the active profile."));
		top.add(new BasicItems.ButtonItem("Open folder", false, () -> openFolder(ProfileManager.folder()), "Share profiles: copy files in or out of this folder."));
		top.add(new BasicItems.ButtonItem("Refresh", false, () -> {
			ProfileManager.refresh();
			rebuild();
		}, "Re-read the folder after adding files."));
		lb.row(top, 90, 18);

		section(lb, "Profiles", ProfileManager.list().size() + " saved");
		for (String name : ProfileManager.list()) {
			boolean active = name.equals(ProfileManager.active());
			ActionRow row = new ActionRow(() -> name + (name.equals(ProfileManager.active()) ? "  (active)" : ""), null,
					() -> name.equals(ProfileManager.active()), "Load, copy, rename or delete this profile.");
			if (!active) row.action("Load", () -> {
				String err = ProfileManager.switchTo(name);
				notice(err != null ? err : "Switched to " + name + ".");
			});
			row.action("Copy", () -> minecraft.setScreen(new TextInputScreen(this, "Copy " + name, "Name for the copy.", name + " copy", 32,
					to -> ProfileManager.duplicate(name, to))));
			row.action("Rename", () -> minecraft.setScreen(new TextInputScreen(this, "Rename " + name, "New name.", name, 32,
					to -> ProfileManager.rename(name, to))));
			if (!active) row.danger("Delete", () -> minecraft.setScreen(new ConfirmDialog(this, "Delete " + name + "?",
					"The profile file is removed. This can't be undone.", "Delete", () -> notice(orDone(ProfileManager.delete(name), "Deleted " + name + ".")))));
			lb.full(row, 22);
		}
	}

	// ---- Settings

	private void buildSettings(LayoutBuilder lb) {
		section(lb, "Menu", null);
		settingRows(lb, MenuSettings.settings());

		section(lb, "Home screen", "VANTA's replacement for the title screen");
		lb.row(List.of(new BasicItems.ButtonItem("Home screen options", false, () -> openModule(ModuleRegistry.TITLE_SCREEN),
				"Background, sidebar, buttons and welcome text.")), 140, 18);

		section(lb, "Minecraft menus", "Pause menu, world and server lists, and other menus in the VANTA style");
		lb.row(List.of(new BasicItems.ButtonItem("Menu Style options", false, () -> openModule(ModuleRegistry.byId("menu_style")),
				"Choose which menus get the VANTA look, the pause info panel, blur and dimming.")), 140, 18);

		section(lb, "Interface extras", null);
		List<Module> extras = new ArrayList<>();
		for (String id : new String[] {"notifications", "loading_screen", "cursor", "update_checker"}) {
			Module m = ModuleRegistry.byId(id);
			if (m != null) extras.add(m);
		}
		moduleCards(lb, extras);
		lb.row(List.of(new BasicItems.ButtonItem("Run first-time setup again", false,
				() -> minecraft.setScreen(new vanta.menu.SetupWizardScreen(this)), "The quick setup: look, performance and what you play.")), 170, 18);

		section(lb, "Compatibility", "How VANTA works next to your other Fabric mods");
		settingRows(lb, List.of(vanta.compat.CompatDetector.AVOID_DUPLICATES, vanta.compat.CompatDetector.YIELD_TITLE));
		if (!CompatDetector.adjusted().isEmpty()) {
			paragraph(lb, "Turned off this time to avoid duplicates: " + String.join(", ", CompatDetector.adjusted())
					+ ". Turn them back on in Modules if you prefer VANTA's version.", Theme.textFaint());
		}
		for (CompatDetector.Detected d : CompatDetector.detected()) paragraph(lb, d.name() + ": " + d.note(), Theme.textDim());

		section(lb, "Network", "Everything VANTA can send over the internet (all optional)");
		settingRows(lb, List.of(vanta.net.RegionLookup.ONLINE, vanta.net.LivePing.LIVE, vanta.net.LivePing.INTERVAL));

		section(lb, "Keys", "Change them in Options > Controls > Key Binds > VANTA Client");
		lb.full(new BasicItems.KeyValueRow("Open this menu", VantaKeys::menuKeyName, 0), 13);
		lb.full(new BasicItems.KeyValueRow("HUD editor", () -> VantaKeys.name(VantaKeys.HUD_EDITOR), 0), 13);
		lb.full(new BasicItems.KeyValueRow("Zoom", () -> VantaKeys.name(VantaKeys.ZOOM), 0), 13);
		lb.full(new BasicItems.KeyValueRow("Freelook", () -> VantaKeys.name(VantaKeys.FREELOOK), 0), 13);
		lb.full(new BasicItems.KeyValueRow("Add waypoint", () -> VantaKeys.name(VantaKeys.ADD_WAYPOINT), 0), 13);
		lb.full(new BasicItems.KeyValueRow("Waypoint list", () -> VantaKeys.name(VantaKeys.WAYPOINT_LIST), 0), 13);
		for (int i = 0; i < VantaKeys.AUTO_TEXT.length; i++) {
			final int k = i;
			lb.full(new BasicItems.KeyValueRow("Auto Text " + (i + 1), () -> VantaKeys.name(VantaKeys.AUTO_TEXT[k]), 0), 13);
		}
		paragraph(lb, "In this menu: Tab focuses search, Esc goes back, Backspace leaves a module page.", 0);

		section(lb, "Data", "Settings are saved automatically to your config folder");
		paragraph(lb, ConfigManager.get().directory().toAbsolutePath().toString(), Theme.textFaint());
		List<Item> data = new ArrayList<>();
		data.add(new BasicItems.ButtonItem("Save now", false, () -> {
			ConfigManager.get().saveNow();
			notice("Saved.");
		}, "Write settings to disk immediately."));
		data.add(new BasicItems.ButtonItem("Open folder", false, () -> openFolder(ConfigManager.get().directory()), "Open config/vanta."));
		data.add(new BasicItems.ButtonItem("Restore backup", false, this::confirmRestoreBackup, "Load the copy saved when the game started."));
		data.add(new BasicItems.ButtonItem("Reset everything", false, () -> minecraft.setScreen(new ConfirmDialog(this,
				"Reset all VANTA settings?", "Every module, widget, theme and menu setting goes back to default.\nThe current file is backed up first.",
				"Reset all", () -> {
					ConfigManager.get().backupCurrent("before-reset");
					SettingRegistry.resetAll();
					ModuleRegistry.syncAll();
					ConfigManager.get().saveNow();
				})), "Start fresh. Your current settings are backed up first."));
		lb.row(data, 90, 18);
	}

	// ---- About

	private void buildAbout(LayoutBuilder lb) {
		section(lb, Theme.clientName() + " Client", "Version " + VantaInfo.vantaVersion() + " for Minecraft " + VantaInfo.minecraftVersion());
		statusRows(lb);
		vanta.update.UpdateChecker up = vanta.update.UpdateChecker.get();
		if (up != null) {
			lb.full(new BasicItems.KeyValueRow("Updates", up::status, 0), 13);
			lb.row(List.of(
					new BasicItems.ButtonItem("Check now", false, up::check, "Look for a newer VANTA on GitHub."),
					new BasicItems.ButtonItem("Open download page", false, () -> Util.getPlatform().openUri(up.pageUrl()), "Opens the releases page in your browser.")), 120, 18);
		}
		section(lb, "Credits", null);
		lb.full(new BasicItems.KeyValueRow("Created by", () -> VantaInfo.CREATOR, Theme.accent()), 13);
		for (int i = 0; i < VantaInfo.HELPERS.length; i++) {
			final String h = VantaInfo.HELPERS[i];
			lb.full(new BasicItems.KeyValueRow(i == 0 ? "Helpers" : "", () -> h, 0), 13);
		}
		section(lb, "Privacy", null);
		paragraph(lb, "VANTA needs no account, sends no telemetry, and stores settings only in your config folder. Network requests, all optional: "
				+ "the server region lookup next to your ping (sends the server's IP to a lookup service, once per server; Settings > Network), "
				+ "the update check (asks GitHub for the latest release when the game starts; turn off Update Checker), Tier Tagger lookups "
				+ "(only while it's on), and a presence server only if you set one. Your friends list stays on your computer.", 0);
		section(lb, "Fair play", null);
		paragraph(lb, "VANTA never clicks, aims or changes reach, and it sends the same packets as vanilla. Reach, combo, CPS and keystrokes "
				+ "only display what already happened. The optional client-side crystals and anchors (Optimizations tab, off by default) only "
				+ "update your screen sooner; some servers don't allow them, so check the rules.", 0);
		section(lb, "Compatibility", null);
		if (CompatDetector.detected().isEmpty()) {
			paragraph(lb, "No known optimization or menu mods detected. VANTA works on its own; Sodium, Lithium and similar mods can be added alongside it.", 0);
		} else {
			for (CompatDetector.Detected d : CompatDetector.detected()) paragraph(lb, d.name() + ": " + d.note(), 0);
		}
		section(lb, "This build", null);
		paragraph(lb, ModuleRegistry.all().size() + " modules, including zoom, freelook, fullbright, custom crosshair, block outline, "
				+ "hitboxes, nametags, damage indicators, hit color, item physics, time and sky, weather, minimap, waypoints, "
				+ "scoreboard and tab list restyles, a clean F3 panel, totem pop counter, item cooldowns, keystrokes, CPS, reach, combo, "
				+ "armor status, FPS, measured ping with region, auto reconnect, nick hider, client badge, themed chat with tabs, search, heads and emoji, "
				+ "totem customizer, held item view, capes and trails, friends, session stats, inventory viewer, durability warnings, "
				+ "notifications, loading screens, custom cursor, menu FPS caps, dynamic render distance and the home screen with quick join.\n"
				+ "Plus performance presets with undo, profiles, saved themes and a deeply customizable interface.", 0);
	}

	// ---- Module page

	private void buildModule(LayoutBuilder lb, Module m) {
		paragraph(lb, m.description(), 0);
		lb.full(new SettingRow(m.enabledSetting(), this), SettingRow.height());
		if (m == ModuleRegistry.CROSSHAIR) {
			lb.row(List.of(new BasicItems.ButtonItem("Open crosshair editor", true, () -> openPage(MenuPage.CROSSHAIR), null)), 140, 18);
			return;
		}
		if (m instanceof vanta.chat.ChatModule) {
			lb.row(List.of(new BasicItems.ButtonItem("Open the Chat page", true, () -> openPage(MenuPage.CHAT), null)), 140, 18);
			return;
		}
		if (m instanceof vanta.cosmetics.CosmeticsModule) {
			lb.row(List.of(new BasicItems.ButtonItem("Open the Cosmetics page", true, () -> openPage(MenuPage.COSMETICS), null)), 140, 18);
			return;
		}
		if (m instanceof vanta.friends.FriendsModule) {
			lb.row(List.of(new BasicItems.ButtonItem("Open the Friends page", true, () -> openPage(MenuPage.FRIENDS), null)), 140, 18);
			return;
		}
		if (m == ModuleRegistry.TITLE_SCREEN) backgroundExtras(lb);
		if (m instanceof vanta.waypoints.WaypointsModule) vanta.waypoints.WaypointMenu.build(this, lb);
		List<Setting<?>> settings = m.visibleSettings();
		if (!settings.isEmpty()) {
			section(lb, "Settings", null);
			settingRows(lb, settings);
		}
		lb.space(4);
		List<Item> actions = new ArrayList<>();
		if (m instanceof HudModule) actions.add(new BasicItems.ButtonItem("Open HUD editor", true, this::openHudEditor, "Place this widget on screen."));
		actions.add(new BasicItems.ButtonItem("Reset module", false, () -> minecraft.setScreen(new ConfirmDialog(this,
				"Reset " + m.name() + "?", "All of its settings go back to default.", "Reset", () -> {
					m.resetToDefaults();
					m.syncExternal();
				})), "Restore this module's defaults."));
		lb.row(actions, 110, 18);
	}

	/** Custom background images: folder button and one chip per image found. */
	private void backgroundExtras(LayoutBuilder lb) {
		section(lb, "Background images", "Put PNG, JPG or GIF files in config/vanta/backgrounds, then pick one here");
		paragraph(lb, vanta.menu.BackgroundImages.customStatus(), Theme.textFaint());
		List<Item> chips = new ArrayList<>();
		TitleScreenModule t = ModuleRegistry.TITLE_SCREEN;
		for (String file : vanta.menu.BackgroundImages.available()) {
			chips.add(new BasicItems.ChipItem(file, () -> file.equalsIgnoreCase(t.imageFile.get()), () -> {
				t.imageFile.set(file);
				t.background.set(TitleScreenModule.Background.CUSTOM_IMAGE);
				rebuild();
			}));
		}
		if (!chips.isEmpty()) lb.grid(chips, 120, 16);
		List<Item> actions = new ArrayList<>();
		actions.add(new BasicItems.ButtonItem("Open backgrounds folder", false, vanta.menu.BackgroundImages::openFolder,
				"Add your own images or GIFs here. GIFs are scaled to 640 px wide and up to 120 frames."));
		actions.add(new BasicItems.ButtonItem("Refresh list", false, this::rebuild, "Look for new files."));
		actions.add(new BasicItems.ButtonItem("Use last world", false, () -> {
			t.background.set(TitleScreenModule.Background.LAST_WORLD);
			rebuild();
		}, "Show the last world you played behind the home screen."));
		lb.row(actions, 110, 18);
	}

	// ---- Search

	private void buildSearch(LayoutBuilder lb) {
		results.clear();
		String q = query.toLowerCase(Locale.ROOT);
		List<Object[]> scored = new ArrayList<>();
		for (MenuPage p : MenuPage.values()) {
			int s = score(q, p.title, p.subtitle);
			if (s > 0) scored.add(new Object[]{s, p.icon, p.title, "Page", (Runnable) () -> openPage(p)});
		}
		for (Module m : ModuleRegistry.all()) {
			int s = score(q, m.name(), m.description() + " " + m.category().label);
			if (s > 0) scored.add(new Object[]{s + 1, m instanceof HudModule ? Icons.HUD : Icons.GRID, m.name(),
					"Module - " + m.category().label, (Runnable) () -> openModule(m)});
		}
		for (Setting<?> st : SettingRegistry.all()) {
			if (st.isHidden()) continue;
			String ownerName = ownerName(st.owner());
			int s = score(q, st.name(), st.description() + " " + ownerName);
			if (s > 0) scored.add(new Object[]{s, Icons.GEAR, st.name(), "Setting - " + ownerName, (Runnable) () -> jumpToSetting(st)});
		}
		scored.sort((a, b) -> Integer.compare((int) b[0], (int) a[0]));
		if (scored.isEmpty()) {
			paragraph(lb, "Nothing matches \"" + query + "\".", 0);
			return;
		}
		int n = Math.min(40, scored.size());
		section(lb, n + (scored.size() > n ? "+" : "") + " results", "Up/Down to choose, Enter to open, Esc to clear");
		for (int i = 0; i < n; i++) {
			Object[] r = scored.get(i);
			final int index = i;
			BasicItems.ResultRow row = new BasicItems.ResultRow((String[]) r[1], (String) r[2], (String) r[3], (Runnable) r[4], () -> selectedResult == index);
			results.add(row);
			lb.full(row, 20);
		}
	}

	private static int score(String q, String name, String extra) {
		String n = name.toLowerCase(Locale.ROOT);
		if (n.equals(q)) return 100;
		if (n.startsWith(q)) return 80;
		if (n.contains(q)) return 60;
		if (extra != null && extra.toLowerCase(Locale.ROOT).contains(q)) return 20;
		return 0;
	}

	private static String ownerName(Object owner) {
		if (owner instanceof Module m) return m.name();
		if (owner instanceof MenuPage p) return p.title;
		return "VANTA";
	}

	private void jumpToSetting(Setting<?> s) {
		Object owner = s.owner();
		clearSearch();
		if (owner == ModuleRegistry.CROSSHAIR) openPage(MenuPage.CROSSHAIR);
		else if (owner instanceof vanta.chat.ChatModule) openPage(MenuPage.CHAT);
		else if (owner instanceof vanta.cosmetics.CosmeticsModule) openPage(MenuPage.COSMETICS);
		else if (owner instanceof vanta.friends.FriendsModule) openPage(MenuPage.FRIENDS);
		else if (owner instanceof Module m) openModule(m);
		else if (owner instanceof MenuPage p) openPage(p);
		highlight(s.id());
	}

	private void highlight(String key) {
		highlightKey = key;
		highlightUntil = System.currentTimeMillis() + 2500;
		needsRebuild = true;
	}

	private void clearSearch() {
		query = "";
		if (search != null) search.setValue("");
		setFocused(null);
		needsRebuild = true;
	}

	// ------------------------------------------------------------------ MenuHost

	@Override
	public void openPage(MenuPage p) {
		page = p;
		lastPage = p;
		moduleView = null;
		selectedPreset = null;
		scroll = scrollTarget = 0;
		pageSwitchedAt = System.currentTimeMillis();
		if (!query.isEmpty()) clearSearch();
		if (p == MenuPage.PROFILES) ProfileManager.refresh();
		if (p == MenuPage.THEME) ThemeManager.refresh();
		needsRebuild = true;
	}

	@Override
	public void openModule(Module m) {
		if (!query.isEmpty()) clearSearch();
		moduleView = m;
		scroll = scrollTarget = 0;
		pageSwitchedAt = System.currentTimeMillis();
		highlight(m.id());
		needsRebuild = true;
	}

	@Override public void beginDrag(Item item) { dragItem = item; }
	@Override public Screen asScreen() { return this; }

	@Override
	public boolean isHighlighted(String key) {
		return highlightKey != null && highlightKey.equals(key) && System.currentTimeMillis() < highlightUntil;
	}

	@Override
	public void focusSearch() {
		if (search == null) return;
		setFocused(search);
		search.setFocused(true);
	}

	private void openHudEditor() {
		minecraft.setScreen(new HudEditorScreen(this));
	}

	private void confirmRestoreBackup() {
		if (!ConfigManager.get().hasBackup()) {
			minecraft.setScreen(new ConfirmDialog(this, "No backup yet", "A backup is made each time the game starts with a valid settings file.", "OK", () -> { }));
			return;
		}
		minecraft.setScreen(new ConfirmDialog(this, "Restore backup?", "Replaces current VANTA settings with the copy made when the game started.\nCurrent settings are backed up first.",
				"Restore", () -> {
					ConfigManager.get().restoreBackup();
					ModuleRegistry.syncAll();
					rebuild();
				}));
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		float open = Theme.animationTimeScale() <= 0 ? 1f : openProgress();
		if (minecraft.level == null) {
			g.fill(0, 0, width, height, Theme.background());
		} else {
			if (Theme.BLUR.on()) {
				try {
					renderBlurredBackground(g);
				} catch (IllegalStateException alreadyBlurred) {
					// something else already blurred this frame
				}
			}
			float dim = Theme.BACKGROUND_DIM.value() / 100f;
			if (dim > 0) g.fill(0, 0, width, height, Colors.withAlpha(Theme.background(), dim * open));
		}
		int faint = Colors.withAlpha(Theme.text(), 0.035f * open);
		switch (Theme.BACKDROP.get()) {
			case PLAIN -> {
			}
			case GRID -> {
				for (int x = 0; x < width; x += 16) g.fill(x, 0, x + 1, height, faint);
				for (int y = 0; y < height; y += 16) g.fill(0, y, width, y + 1, faint);
			}
			case DOTS -> {
				int dot = Colors.withAlpha(Theme.text(), 0.08f * open);
				for (int x = 8; x < width; x += 16) {
					for (int y = 8; y < height; y += 16) g.fill(x, y, x + 1, y + 1, dot);
				}
			}
			case GRADIENT -> g.fillGradient(0, 0, width, height, Colors.withAlpha(Theme.accent(), 0.16f * open), Colors.withAlpha(Theme.secondary(), 0.03f * open));
			case VIGNETTE -> {
				int edge = Colors.withAlpha(0xFF000000, 0.55f * open);
				g.fillGradient(0, 0, width, height / 4, edge, 0);
				g.fillGradient(0, height - height / 4, width, height, 0, edge);
			}
		}
	}

	@Override
	protected void renderScaled(GuiGraphics g, int mx, int my, float delta) {
		long now = System.nanoTime();
		float dt = lastFrameNanos == 0 ? 0f : Math.min(0.1f, (now - lastFrameNanos) / 1e9f);
		lastFrameNanos = now;

		if (dragItem == null && computeSignature() != layoutSignature) {
			resize(width, height); // theme layout changed: re-run layout at the new size
			return;
		}
		if (needsRebuild || (notice != null && System.currentTimeMillis() > noticeUntil && noticeUntil != 0)) {
			if (notice != null && System.currentTimeMillis() > noticeUntil) {
				notice = null;
				noticeUntil = 0;
			}
			buildContent();
		}

		float open = openProgress();
		float dist = Theme.animationDistance();
		Theme.OpenAnimation anim = Theme.OPEN_ANIMATION.get();
		offsetX = anim == Theme.OpenAnimation.SLIDE_SIDE ? Math.round((1f - open) * 24f * dist * (Theme.MENU_POSITION.get() == Theme.MenuPosition.RIGHT ? 1 : -1)) : 0;
		offsetY = anim == Theme.OpenAnimation.SLIDE_UP ? Math.round((1f - open) * 14f * dist) : 0;
		float pageT = Anim.easeOutCubic(Anim.progress(pageSwitchedAt, 160f));
		bodyOffsetX = Math.round((1f - pageT) * 10f * dist);

		boolean zoom = anim == Theme.OpenAnimation.SCALE && open < 1f;
		if (zoom) {
			float s = 0.92f + 0.08f * open;
			float cx = panelX + panelW / 2f, cy = panelY + panelH / 2f;
			g.pose().pushMatrix();
			g.pose().translate(cx, cy);
			g.pose().scale(s);
			g.pose().translate(-cx, -cy);
		}

		int px = panelX + offsetX;
		int py = panelY + offsetY;
		int r = Theme.radius() + 1;
		Ui.shadow(g, px, py, panelW, panelH, r);
		Ui.roundRect(g, px, py, panelW, panelH, r, Theme.panel());
		boolean glass = Theme.CARD_STYLE.get() == Theme.CardStyle.GLASS;
		if (glass) {
			// glass: soft light from the top and a bright rim
			g.fillGradient(px + r, py + 1, px + panelW - r, py + Math.min(60, panelH / 3), Colors.withAlpha(0xFFFFFFFF, 0.05f), 0);
		}
		boolean right = Theme.SIDEBAR_SIDE.get() == Theme.Side.RIGHT;
		switch (shell) {
			case SIDEBAR, RAIL -> {
				Ui.roundRect(g, sidebarX + offsetX, py, sidebarW, panelH, r, Theme.sidebar());
				Ui.rect(g, (right ? sidebarX : sidebarX + sidebarW - 1) + offsetX, py, 1, panelH, Theme.border());
			}
			case LIQUID -> {
				Ui.rect(g, (right ? sidebarX : sidebarX + sidebarW - 1) + offsetX, py, 1, panelH, Colors.withAlpha(Theme.border(), 0.8f));
				Ui.rect(g, px + 1, py + headerH, panelW - 2, 1, Colors.withAlpha(Theme.border(), 0.8f));
			}
			case TABS -> Ui.rect(g, px + 1, py + headerH - 4, panelW - 2, 1, Colors.withAlpha(Theme.border(), 0.8f));
			case DOCK -> Ui.rect(g, px + 1, py + headerH - 2, panelW - 2, 1, Colors.withAlpha(Theme.border(), 0.6f));
			default -> {
			}
		}
		Ui.border(g, px, py, panelW, panelH, r, glass ? Colors.withAlpha(0xFFFFFFFF, 0.12f) : Theme.border());

		g.pose().pushMatrix();
		g.pose().translate(offsetX, 0);
		renderNav(g, mx - offsetX, my, py, dt);
		renderHeader(g, mx - offsetX, my, py);
		renderBody(g, mx - offsetX, my, py, dt);
		g.pose().popMatrix();

		if (anim == Theme.OpenAnimation.FADE && open < 1f) {
			Ui.roundRect(g, px - 2, py - 2, panelW + 6, panelH + 8, r, Colors.withAlpha(Theme.background(), 1f - open));
		}
		if (zoom) g.pose().popMatrix();
	}

	private MenuPage activePage() {
		if (moduleView == null) return page;
		if (moduleView == ModuleRegistry.CROSSHAIR) return MenuPage.CROSSHAIR;
		if (moduleView instanceof vanta.chat.ChatModule) return MenuPage.CHAT;
		return MenuPage.MODULES;
	}

	private NavSlot slot(MenuPage p) {
		for (NavSlot n : nav) if (n.page() == p) return n;
		return null;
	}

	private void renderNav(GuiGraphics g, int mx, int my, int py, float dt) {
		int dy = py - panelY; // open animation offset
		boolean labels = Theme.SIDEBAR_LABELS.on() && shell != vanta.branding.UiFormat.Shell.RAIL;
		boolean icons = Theme.SIDEBAR_ICONS.on() || !labels;
		// logo / brand
		switch (shell) {
			case SIDEBAR -> {
				if (Theme.SHOW_LOGO.on() && labels) {
					int size = vanta.branding.Logo.fit(sidebarW - 20, Math.round(20 * Theme.headingScale()), true);
					vanta.branding.Logo.draw(g, sidebarX + 10, py + 6, size, true, openedAt);
				}
			}
			case RAIL -> vanta.branding.Logo.draw(g, sidebarX + (sidebarW - 18) / 2, py + 9, 18, false, openedAt);
			case LIQUID -> {
				int size = Math.min(24, headerH - 10);
				vanta.branding.Logo.draw(g, sidebarX + (sidebarW - size) / 2, py + (headerH - size) / 2, size, false, openedAt);
			}
			case TABS, DOCK -> {
				int size = Math.round(14 * Theme.headingScale());
				vanta.branding.Logo.draw(g, panelX + 10, py + 9, size, true, openedAt);
			}
			default -> {
			}
		}
		// dock background
		if (shell == vanta.branding.UiFormat.Shell.DOCK && !nav.isEmpty()) {
			NavSlot first = nav.get(0), last = nav.get(nav.size() - 1);
			int x0 = first.x() - 6, x1 = last.x() + last.w() + 6, y0 = first.y() - 3 + dy, h = first.h() + 6;
			Ui.roundRect(g, x0, y0, x1 - x0, h, Math.min(h / 2, Theme.radius() + 6), Colors.withAlpha(Theme.sidebar(), 0.95f));
			Ui.roundOutline(g, x0, y0, x1 - x0, h, Math.min(h / 2, Theme.radius() + 6), Colors.withAlpha(0xFFFFFFFF, 0.10f));
		}

		MenuPage active = activePage();
		NavSlot cur = slot(active);
		if (cur != null) {
			if (indX < 0) {
				indX = cur.x();
				indY = cur.y();
				indW = cur.w();
			}
			indX = Anim.approach(indX, cur.x(), dt, 18f);
			indY = Anim.approach(indY, cur.y(), dt, 18f);
			indW = Anim.approach(indW, cur.w(), dt, 18f);
			if (verticalNav()) g.enableScissor(sidebarX, navClipY0 + dy, sidebarX + sidebarW, navClipY1 + dy);
			int ix = Math.round(indX), iy = Math.round(indY) + dy - (verticalNav() ? Math.round(navScroll) : 0), iw = Math.round(indW), ih = cur.h();
			drawIndicator(g, ix, iy, iw, ih);
		} else if (verticalNav()) {
			g.enableScissor(sidebarX, navClipY0 + dy, sidebarX + sidebarW, navClipY1 + dy);
		}
		boolean pill = Theme.INDICATOR.get() == Theme.Indicator.PILL && shell != vanta.branding.UiFormat.Shell.LIQUID;
		String tooltip = null;
		int tipX = 0, tipY = 0;
		boolean inClip = !verticalNav() || (my >= navClipY0 + dy && my < navClipY1 + dy);
		for (NavSlot n : nav) {
			MenuPage p = n.page();
			int nx = n.x(), ny = slotY(n) + dy;
			if (verticalNav() && (ny + n.h() < navClipY0 + dy || ny > navClipY1 + dy)) continue;
			boolean hov = inClip && Ui.inside(mx, my, nx, ny, n.w(), n.h());
			if (hov && p != active) Ui.roundRect(g, nx, ny, n.w(), n.h(), navRadius(n), Theme.panelHover());
			int color = p == active ? (pill ? Colors.contrastText(Theme.accent()) : Theme.text()) : hov ? Theme.text() : Theme.textDim();
			int iconColor = p == active && !pill ? Theme.accent() : color;
			if (n.iconOnly()) {
				float sc = n.w() >= 18 && shell == vanta.branding.UiFormat.Shell.DOCK ? 1.3f : 1f;
				g.pose().pushMatrix();
				g.pose().translate(nx + n.w() / 2f - 4.5f * sc, ny + n.h() / 2f - 4.5f * sc);
				g.pose().scale(sc);
				Ui.icon(g, p.icon, 0, 0, iconColor);
				g.pose().popMatrix();
				if (shell == vanta.branding.UiFormat.Shell.DOCK && p == active) Ui.roundRect(g, nx + n.w() / 2 - 1, ny + n.h() + 1, 3, 2, 1, Theme.accent());
				if (hov) {
					tooltip = p.title;
					tipX = nx + n.w() / 2;
					tipY = shell == vanta.branding.UiFormat.Shell.DOCK ? ny - 14 : ny;
				}
			} else if (shell == vanta.branding.UiFormat.Shell.LIQUID) {
				int tx = nx + 10;
				if (icons) {
					Ui.icon(g, p.icon, tx, ny + (n.h() - 9) / 2, iconColor);
					tx += 15;
				}
				String label = Ui.ellipsize(p.title, n.w() - (tx - nx) - 6);
				drawBold(g, label, tx, ny + (n.h() - 8) / 2, p == active ? Theme.text() : color);
			} else {
				int tx = nx + (shell == vanta.branding.UiFormat.Shell.TABS ? 7 : 7);
				if (icons) {
					Ui.icon(g, p.icon, tx, ny + (n.h() - 9) / 2, iconColor);
					tx += 15;
				}
				Ui.text(g, Ui.ellipsize(p.title, n.w() - (tx - nx) - 4), tx, ny + (n.h() - 8) / 2, color);
			}
		}
		if (verticalNav()) {
			g.disableScissor();
			if (navMaxScroll > 0) {
				// fade hints that there's more above/below
				int top = navClipY0 + dy, bot = navClipY1 + dy, sb = Theme.sidebar() | 0xFF000000;
				int base = shell == vanta.branding.UiFormat.Shell.LIQUID ? Theme.panel() : Theme.sidebar();
				if (navScroll > 1) g.fillGradient(sidebarX + 2, top, sidebarX + sidebarW - 2, top + 8, base, base & 0x00FFFFFF);
				if (navScroll < navMaxScroll - 1) g.fillGradient(sidebarX + 2, bot - 8, sidebarX + sidebarW - 2, bot, base & 0x00FFFFFF, base);
				int trackH = bot - top, thumb = Math.max(10, trackH * trackH / (trackH + navMaxScroll));
				int ty = top + Math.round((trackH - thumb) * (navScroll / navMaxScroll));
				int tx = Theme.SIDEBAR_SIDE.get() == Theme.Side.RIGHT ? sidebarX + 1 : sidebarX + sidebarW - 3;
				Ui.rect(g, tx, ty, 1, thumb, Colors.withAlpha(Theme.accent(), 0.6f));
			}
		}
		if (shell == vanta.branding.UiFormat.Shell.SIDEBAR && labels) {
			String hint = VantaKeys.menuKeyName() + " to close";
			Ui.text(g, Ui.ellipsize(hint, sidebarW - 16), sidebarX + 10, py + panelH - 14, Theme.textFaint());
		}
		if (shell == vanta.branding.UiFormat.Shell.LIQUID) {
			String[] labelsB = {"Edit HUD", "Formats"};
			String[][] iconsB = {Icons.MOVE, Icons.PALETTE};
			for (int i = 0; i < chromeHits.size() && i < 2; i++) {
				Hit h = chromeHits.get(i);
				int hy = h.y() + dy;
				boolean hov = Ui.inside(mx, my, h.x(), hy, h.w(), h.h());
				int rr = Math.min(h.h() / 2, Theme.radius() + 1);
				Ui.roundRect(g, h.x(), hy, h.w(), h.h(), rr, hov ? Theme.panelHover() : Colors.withAlpha(Theme.PANEL.argb(), 0.6f));
				Ui.roundOutline(g, h.x(), hy, h.w(), h.h(), rr, Colors.withAlpha(Theme.TEXT.argb(), hov ? 0.22f : 0.12f));
				Ui.icon(g, iconsB[i], h.x() + 8, hy + (h.h() - 9) / 2, hov ? Theme.accent() : Theme.textDim());
				drawBold(g, Ui.ellipsize(labelsB[i], h.w() - 30), h.x() + 22, hy + (h.h() - 8) / 2, Theme.text());
			}
		}
		if (tooltip != null) {
			int tw = Ui.width(tooltip) + 8;
			int tx = shell == vanta.branding.UiFormat.Shell.RAIL ? sidebarX + sidebarW + 2 : tipX - tw / 2;
			int ty = shell == vanta.branding.UiFormat.Shell.RAIL ? tipY + 2 : tipY;
			Ui.roundRect(g, tx, ty, tw, 12, 3, Colors.withAlpha(Theme.background(), 0.95f));
			Ui.roundOutline(g, tx, ty, tw, 12, 3, Theme.border());
			Ui.text(g, tooltip, tx + 4, ty + 2, Theme.text());
		}
	}

	private int navRadius(NavSlot n) {
		return shell == vanta.branding.UiFormat.Shell.LIQUID || shell == vanta.branding.UiFormat.Shell.DOCK
				? Math.min(n.h() / 2, Theme.radius() + 2) : Theme.radius();
	}

	private void drawIndicator(GuiGraphics g, int x, int y, int w, int h) {
		int rad = shell == vanta.branding.UiFormat.Shell.LIQUID || shell == vanta.branding.UiFormat.Shell.DOCK
				? Math.min(h / 2, Theme.radius() + 2) : Theme.radius();
		if (shell == vanta.branding.UiFormat.Shell.LIQUID) {
			Ui.roundRect(g, x, y, w, h, rad, Colors.withAlpha(Colors.mix(Theme.PANEL.argb(), Theme.TEXT.argb(), 0.16f), 0.85f));
			Ui.roundOutline(g, x, y, w, h, rad, Colors.withAlpha(Theme.TEXT.argb(), 0.28f));
			return;
		}
		if (shell == vanta.branding.UiFormat.Shell.TABS) {
			Ui.roundRect(g, x, y, w, h, rad, Colors.fade(Theme.accent(), 0.16f));
			Ui.accentFill(g, x + 3, y + h - 2, w - 6, 2, 1, 0f);
			return;
		}
		if (shell == vanta.branding.UiFormat.Shell.DOCK) {
			Ui.roundRect(g, x - 2, y - 2, w + 4, h + 4, rad, Colors.fade(Theme.accent(), 0.22f));
			return;
		}
		switch (Theme.INDICATOR.get()) {
			case BAR -> {
				Ui.roundRect(g, x, y, w, h, Theme.radius(), Colors.fade(Theme.accent(), 0.18f));
				Ui.rect(g, x, y + 4, 2, h - 8, Theme.accent());
			}
			case PILL -> Ui.accentFill(g, x, y, w, h, Math.min(h / 2, 6), 0f);
			case UNDERLINE -> Ui.accentFill(g, x + 4, y + h - 2, w - 8, 1, 0, 0f);
			case GLOW -> {
				Ui.roundRect(g, x - 2, y - 1, w + 4, h + 2, Theme.radius() + 1, Colors.fade(Theme.accent(), 0.10f));
				Ui.roundRect(g, x, y, w, h, Theme.radius(), Colors.fade(Theme.accent(), 0.16f));
			}
			case NONE -> {
			}
		}
	}

	private static void drawBold(GuiGraphics g, String s, int x, int y, int color) {
		Ui.bold(g, s, x, y, color);
	}

	private record HeaderChip(int x, int y, int w, int h, Category category) {
	}

	private final List<HeaderChip> headerChips = new ArrayList<>();

	/** Top-left corner of the back button (shown on module pages), for the current layout. */
	private int backButtonX() {
		return switch (shell) {
			case LIQUID -> searchX + searchW + 10;
			case TABS, DOCK -> panelX + 10 + vanta.branding.Logo.width(Math.round(14 * Theme.headingScale()), true) + 12;
			default -> contentX + 8;
		};
	}

	private int backButtonY() {
		return switch (shell) {
			case LIQUID -> panelY + (headerH - 16) / 2;
			case TABS, DOCK -> panelY + 8;
			default -> panelY + 10;
		};
	}

	private static int chipWidth(Category c, boolean count) {
		int w = Ui.boldWidth(chipLabel(c)) + 14;
		if (count) w += 4 + Math.round(Ui.width(String.valueOf(ModuleRegistry.sorted(c).size())) * 0.75f);
		return w;
	}

	private static int chipsWidth(boolean count) {
		int total = chipWidth(null, count) + 4;
		for (Category c : ModuleRegistry.usedCategories()) total += chipWidth(c, count) + 4;
		return total;
	}

	/** Short category names for the top-bar chips. */
	private static String chipLabel(Category c) {
		if (c == null) return "All";
		return switch (c) {
			case PERFORMANCE -> "FPS";
			case INTERFACE -> "UI";
			case QOL -> "QoL";
			case EXPERIMENTAL -> "Lab";
			default -> c.label;
		};
	}

	private void renderHeader(GuiGraphics g, int mx, int my, int py) {
		int dy = py - panelY;
		headerChips.clear();
		String title;
		String sub;
		boolean back = moduleView != null && query.isEmpty();
		if (!query.isEmpty()) {
			title = "Search";
			sub = "Modules, settings and pages";
		} else if (moduleView != null) {
			title = moduleView.name();
			sub = moduleView.category().label + (moduleView.isExperimental() ? " - Experimental" : "");
		} else {
			title = page.title;
			sub = page.subtitle;
		}
		int bx = backButtonX(), by = backButtonY() + dy;
		if (back) {
			boolean backHover = Ui.inside(mx, my, bx, by, 16, 16);
			Ui.roundRect(g, bx, by, 16, 16, Math.min(8, Theme.radius() + 1), backHover ? Theme.panelHover() : Theme.panelRaised());
			Ui.icon(g, Icons.BACK, bx + 4, by + 4, backHover ? Theme.accent() : Theme.textDim());
		}
		switch (shell) {
			case LIQUID -> {
				int right = closeX - 12;
				int left = searchX + searchW + 10;
				// divider before the close button
				Ui.rect(g, closeX - 8, py + 1, 1, headerH - 1, Colors.withAlpha(Theme.border(), 0.8f));
				if (back || !query.isEmpty()) {
					int tx = back ? bx + 22 : left;
					drawBold(g, Ui.ellipsize(title, right - tx), tx, py + (headerH - 8) / 2, Theme.text());
				} else if (page == MenuPage.MODULES && headerChipsFit) {
					List<Category> cats = new ArrayList<>();
					cats.add(null);
					cats.addAll(ModuleRegistry.usedCategories());
					int[] widths = new int[cats.size()];
					int total = 0;
					for (int i = 0; i < cats.size(); i++) {
						Category c = cats.get(i);
						widths[i] = chipWidth(c, chipCounts);
						total += widths[i] + 4;
					}
					// drop chips from the end until they fit
					int shown = cats.size();
					while (shown > 1 && right - total < left) {
						shown--;
						total -= widths[shown] + 4;
					}
					int x = right - total + 4;
					int cy = py + (headerH - 18) / 2;
					for (int i = 0; i < shown; i++) {
						Category c = cats.get(i);
						boolean sel = categoryFilter == c;
						boolean hov = Ui.inside(mx, my, x, cy, widths[i], 18);
						int rr = 9;
						Ui.roundRect(g, x, cy, widths[i], 18, rr, sel ? Colors.withAlpha(Colors.mix(Theme.PANEL.argb(), Theme.TEXT.argb(), 0.18f), 0.9f)
								: hov ? Theme.panelHover() : Colors.withAlpha(Theme.PANEL.argb(), 0.35f));
						Ui.roundOutline(g, x, cy, widths[i], 18, rr, Colors.withAlpha(Theme.TEXT.argb(), sel ? 0.6f : 0.22f));
						String label = chipLabel(c);
						String n = String.valueOf(ModuleRegistry.sorted(c).size());
						drawBold(g, label, x + 7, cy + 5, Theme.text());
						if (chipCounts) {
							int lw = Ui.boldWidth(label);
							g.pose().pushMatrix();
							g.pose().translate(x + 7 + lw + 4, cy + 7);
							g.pose().scale(0.75f);
							Ui.text(g, n, 0, 0, Theme.textDim());
							g.pose().popMatrix();
						}
						headerChips.add(new HeaderChip(x, cy - dy, widths[i], 18, c));
						x += widths[i] + 4;
					}
				} else {
					String t = Theme.heading(title);
					int tw = Ui.boldWidth(t);
					drawBold(g, t, right - tw, py + (headerH - 8) / 2, Theme.text());
				}
			}
			case TABS, DOCK -> {
				int tx = back ? bx + 22 : bx;
				boolean showTitle = back || !query.isEmpty() || shell == vanta.branding.UiFormat.Shell.DOCK;
				if (showTitle) {
					int maxW = searchX - tx - 8;
					Ui.text(g, Ui.ellipsize(title, maxW), tx, py + 12, Theme.text());
				}
			}
			default -> {
				int titleX = contentX + 10 + (back ? 20 : 0);
				int maxW = searchX - titleX - 8;
				float hs = 1.25f * Theme.headingScale();
				Ui.textScaled(g, Ui.ellipsize(title, (int) (maxW / hs)), titleX, py + 9, hs, Theme.text(), false);
				if (Theme.SHOW_SUBTITLES.on()) Ui.text(g, Ui.ellipsize(sub, maxW), titleX, py + 23, Theme.textFaint());
			}
		}

		// search box
		int sy = searchY + dy;
		boolean focused = search != null && search.isFocused();
		int sr = shell == vanta.branding.UiFormat.Shell.LIQUID ? Math.min(searchH / 2, Theme.radius() + 2) : Theme.radius();
		Ui.roundRect(g, searchX, sy, searchW, searchH, sr, Colors.withAlpha(Theme.background(), shell == vanta.branding.UiFormat.Shell.LIQUID ? 0.35f : 0.6f));
		Ui.roundOutline(g, searchX, sy, searchW, searchH, sr, focused ? Theme.accent() : shell == vanta.branding.UiFormat.Shell.LIQUID
				? Colors.withAlpha(Theme.TEXT.argb(), 0.22f) : Theme.border());
		if (shell == vanta.branding.UiFormat.Shell.LIQUID) Ui.icon(g, Icons.SEARCH, searchX + searchW - 14, sy + (searchH - 9) / 2, focused ? Theme.accent() : Theme.textDim());
		else Ui.icon(g, Icons.SEARCH, searchX + 4, sy + (searchH - 9) / 2, focused ? Theme.accent() : Theme.textFaint());

		// close button
		if (closeX >= 0) {
			int cy = closeY + dy;
			boolean hov = Ui.inside(mx, my, closeX, cy, 18, 18);
			if (hov) Ui.roundRect(g, closeX, cy, 18, 18, Math.min(9, Theme.radius() + 2), Theme.panelHover());
			Ui.icon(g, Icons.CLOSE, closeX + 5, cy + 5, hov ? Theme.accent() : Theme.text());
		}
	}

	private void renderBody(GuiGraphics g, int mx, int my, int py, float dt) {
		scroll = Anim.approach(scroll, scrollTarget, dt, 22f);
		int by = bodyY + offsetY;
		int ox = bodyX + bodyOffsetX;
		int oy = by - Math.round(scroll);
		boolean mouseInBody = Ui.inside(mx, my, bodyX, by, bodyW, bodyH);
		String hint = null;

		g.enableScissor(bodyX, by, bodyX + bodyW, by + bodyH); // GuiGraphics applies the current pose (scale + slide) itself
		for (Item it : items) {
			int ax = ox + it.x;
			int ay = oy + it.y;
			if (ay + it.h < by || ay > by + bodyH) continue; // off-screen: skip entirely
			boolean hovered = mouseInBody && Ui.inside(mx, my, ax, ay, it.w, it.h);
			it.render(g, ax, ay, mx, my, hovered);
			if (hovered) hint = it.hint();
		}
		g.disableScissor();

		if (contentHeight > bodyH) {
			int trackX = bodyX + bodyW + 4;
			int thumbH = Math.max(16, bodyH * bodyH / contentHeight);
			int thumbY = by + Math.round((bodyH - thumbH) * (scroll / Math.max(1, contentHeight - bodyH)));
			Ui.roundRect(g, trackX, by, 2, bodyH, 1, Colors.fade(Theme.border(), 0.8f));
			Ui.roundRect(g, trackX, thumbY, 2, thumbH, 1, Theme.accent());
		}

		boolean footerShell = shell == vanta.branding.UiFormat.Shell.SIDEBAR || shell == vanta.branding.UiFormat.Shell.RAIL
				|| shell == vanta.branding.UiFormat.Shell.TABS;
		if (Theme.SHOW_FOOTER.on() && footerShell) {
			int fy = py + panelH - 14;
			String footer = hint != null ? hint
					: !query.isEmpty() ? "Esc clears the search"
					: moduleView != null ? "Esc or Backspace to go back"
					: "Tab to search  -  hover anything for details";
			Ui.text(g, Ui.ellipsize(footer, bodyW - 50), contentX + 10, fy, Theme.textDim());
			drawMenuFps(g, contentX + contentW - 10, fy);
		}
	}

	// ------------------------------------------------------------------ input

	private Item itemAt(double mx, double my) {
		int by = bodyY + offsetY;
		if (!Ui.inside(mx, my, bodyX, by, bodyW, bodyH)) return null;
		int ox = bodyX + bodyOffsetX;
		int oy = by - Math.round(scroll);
		for (int i = items.size() - 1; i >= 0; i--) {
			Item it = items.get(i);
			if (Ui.inside(mx, my, ox + it.x, oy + it.y, it.w, it.h)) return it;
		}
		return null;
	}

	@Override
	protected boolean onClick(double mxRaw, double my, int button) {
		double mx = mxRaw - offsetX;
		int py = panelY + offsetY;
		int dy = offsetY;
		boolean inNavClip = !verticalNav() || (my >= navClipY0 + dy && my < navClipY1 + dy);
		for (NavSlot n : nav) {
			if (inNavClip && Ui.inside(mx, my, n.x(), slotY(n) + dy, n.w(), n.h())) {
				MenuSettings.click();
				openPage(n.page());
				return true;
			}
		}
		for (Hit h : chromeHits) {
			if (Ui.inside(mx, my, h.x(), h.y() + dy, h.w(), h.h())) {
				MenuSettings.click();
				h.action().run();
				return true;
			}
		}
		for (HeaderChip c : headerChips) {
			if (Ui.inside(mx, my, c.x(), c.y() + dy, c.w(), c.h())) {
				MenuSettings.click();
				categoryFilter = c.category();
				scroll = scrollTarget = 0;
				rebuild();
				return true;
			}
		}
		if (closeX >= 0 && Ui.inside(mx, my, closeX, closeY + dy, 18, 18)) {
			MenuSettings.click();
			onClose();
			return true;
		}
		if (moduleView != null && query.isEmpty() && Ui.inside(mx, my, backButtonX(), backButtonY() + dy, 16, 16)) {
			MenuSettings.click();
			goBack();
			return true;
		}
		if (Ui.inside(mx, my, searchX, searchY + dy, searchW, searchH)) {
			focusSearch();
			return true;
		}
		Item it = itemAt(mx, my);
		if (it != null) {
			int ax = bodyX + bodyOffsetX + it.x;
			int ay = bodyY + offsetY - Math.round(scroll) + it.y;
			return it.click(ax, ay, mx, my, button);
		}
		if (!Ui.inside(mx, my, panelX, py, panelW, panelH) && minecraft.level != null) {
			onClose();
			return true;
		}
		return false;
	}

	@Override
	protected boolean onDrag(double mxRaw, double my, int button) {
		if (dragItem == null) return false;
		double mx = mxRaw - offsetX;
		int ax = bodyX + bodyOffsetX + dragItem.x;
		int ay = bodyY + offsetY - Math.round(scroll) + dragItem.y;
		return dragItem.drag(ax, ay, mx, my);
	}

	@Override
	protected void onRelease(double mx, double my, int button) {
		if (dragItem != null) {
			dragItem.release();
			dragItem = null;
		}
	}

	@Override
	protected boolean onScroll(double mxRaw, double my, double amount) {
		double mx = mxRaw - offsetX;
		if (navMaxScroll > 0 && verticalNav() && Ui.inside(mx, my, sidebarX, navClipY0 + offsetY, sidebarW, navClipY1 - navClipY0)) {
			navScroll = Math.max(0, Math.min(navMaxScroll, navScroll - (float) (amount * 16)));
			return true;
		}
		Item it = itemAt(mx, my);
		if (it != null) {
			int ax = bodyX + bodyOffsetX + it.x;
			int ay = bodyY + offsetY - Math.round(scroll) + it.y;
			if (it.scroll(ax, ay, mx, my, amount)) return true;
		}
		scrollTarget -= (float) (amount * 24);
		clampScroll();
		return true;
	}

	private void clampScroll() {
		float max = Math.max(0, contentHeight - bodyH);
		scrollTarget = Math.max(0, Math.min(max, scrollTarget));
		scroll = Math.max(0, Math.min(max, scroll));
	}

	private void goBack() {
		boolean crosshair = moduleView == ModuleRegistry.CROSSHAIR;
		moduleView = null;
		if (crosshair) page = MenuPage.CROSSHAIR;
		scroll = scrollTarget = 0;
		pageSwitchedAt = System.currentTimeMillis();
		needsRebuild = true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		boolean searchFocused = search != null && search.isFocused();
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			if (!query.isEmpty()) clearSearch();
			else if (searchFocused) setFocused(null);
			else if (moduleView != null) goBack();
			else if (selectedPreset != null) {
				selectedPreset = null;
				rebuild();
			} else onClose();
			return true;
		}
		if (!query.isEmpty() && !results.isEmpty()) {
			if (key == GLFW.GLFW_KEY_DOWN) {
				selectedResult = Math.min(results.size() - 1, selectedResult + 1);
				ensureVisible(results.get(selectedResult));
				return true;
			}
			if (key == GLFW.GLFW_KEY_UP) {
				selectedResult = Math.max(0, selectedResult - 1);
				ensureVisible(results.get(selectedResult));
				return true;
			}
			if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
				results.get(Math.min(selectedResult, results.size() - 1)).activate();
				return true;
			}
		}
		if (!searchFocused) {
			if (key == GLFW.GLFW_KEY_TAB) {
				focusSearch();
				return true;
			}
			if (key == GLFW.GLFW_KEY_BACKSPACE && moduleView != null) {
				goBack();
				return true;
			}
			if (MenuSettings.CLOSE_WITH_KEY.on() && VantaKeys.isMenuKey(event)) {
				onClose();
				return true;
			}
		}
		return super.keyPressed(event);
	}

	private void ensureVisible(Item it) {
		if (it.y < scrollTarget) scrollTarget = it.y;
		else if (it.y + it.h > scrollTarget + bodyH) scrollTarget = it.y + it.h - bodyH;
		clampScroll();
	}

	@Override
	public boolean isPauseScreen() {
		return MenuSettings.PAUSE_SINGLEPLAYER.on();
	}

	@Override
	public void onClose() {
		lastPage = page;
		VanillaOptions.saveIfChanged();
		super.onClose();
	}
}
