package vanta.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import vanta.branding.Theme;
import vanta.hud.HudEditorScreen;
import vanta.input.VantaKeys;
import vanta.modules.Category;
import vanta.modules.Module;
import vanta.modules.ModuleRegistry;
import vanta.ui.Colors;
import vanta.ui.Icons;
import vanta.ui.Ui;

/**
 * The Click GUI format: one floating panel per category with its modules listed.
 * Click a module to switch it, right-click for its settings, drag a panel's title bar to
 * move it, click the title to fold it. Type to filter. Panel spots are remembered.
 */
public final class ClickGuiScreen extends VantaScreen {
	private static final int PANEL_W = 116, HEADER = 16, ROW = 13;

	private static final class Panel {
		final Category category;
		int x, y;
		boolean folded;
		float scroll;
		List<Module> modules = List.of();

		Panel(Category category) { this.category = category; }
	}

	private record Btn(int x, int y, int w, int h, String label, Runnable action) {
	}

	private final Map<Category, Panel> panels = new LinkedHashMap<>();
	private final List<Btn> buttons = new ArrayList<>();
	private EditBox search;
	private String query = "";
	private Panel dragging;
	private int dragDX, dragDY, pressX, pressY;
	private boolean moved;
	private String hint;
	/** Tallest a panel's list may get, so panels in a second row aren't covered. */
	private int maxList = 400;

	public ClickGuiScreen(Screen parent) {
		super(Component.literal("VANTA"), parent);
		load();
	}

	@Override protected int minVirtualWidth() { return 300; }
	@Override protected int minVirtualHeight() { return 200; }

	private void load() {
		int i = 0;
		for (Category c : ModuleRegistry.usedCategories()) {
			Panel p = new Panel(c);
			p.x = 10 + (i % 6) * (PANEL_W + 8);
			p.y = 40 + (i / 6) * 160;
			panels.put(c, p);
			i++;
		}
		for (String part : Theme.CLICKGUI_PANELS.get().split(";")) {
			String[] f = part.split(":");
			if (f.length < 4) continue;
			try {
				Panel p = panels.get(Category.valueOf(f[0]));
				if (p == null) continue;
				p.x = Integer.parseInt(f[1]);
				p.y = Integer.parseInt(f[2]);
				p.folded = "1".equals(f[3]);
			} catch (IllegalArgumentException ignored) {
				// renamed category or bad text: keep the default spot
			}
		}
	}

	private void save() {
		StringBuilder sb = new StringBuilder();
		for (Panel p : panels.values()) {
			if (sb.length() > 0) sb.append(';');
			sb.append(p.category.name()).append(':').append(p.x).append(':').append(p.y).append(':').append(p.folded ? 1 : 0);
		}
		Theme.CLICKGUI_PANELS.set(sb.toString());
	}

	@Override
	protected void layout() {
		buttons.clear();
		int sw = Math.min(180, vw / 3);
		int sx = (vw - sw) / 2;
		String value = search != null ? search.getValue() : query;
		search = new EditBox(font, toReal(sx + 16), toReal(12), toReal(sw - 20), toReal(10), Component.literal("Search"));
		search.setBordered(false);
		search.setMaxLength(32);
		search.setTextColor(Theme.text());
		search.setHint(Component.literal("Type to filter modules"));
		search.setValue(value);
		search.setResponder(v -> query = v.strip().toLowerCase(Locale.ROOT));
		addRenderableWidget(search);
		int bx = vw - 10;
		String[][] defs = {{"Tidy", "tidy"}, {"Full menu", "menu"}, {"Edit HUD", "hud"}, {"Formats", "theme"}};
		for (int i = defs.length - 1; i >= 0; i--) {
			int w = Ui.width(defs[i][0]) + 14;
			bx -= w;
			String id = defs[i][1];
			buttons.add(new Btn(bx, 8, w, 16, defs[i][0], () -> {
				switch (id) {
					case "menu" -> minecraft.setScreen(new VantaMenuScreen(parent));
					case "hud" -> minecraft.setScreen(new HudEditorScreen(this));
					case "tidy" -> {
						arrange();
						save();
					}
					default -> minecraft.setScreen(new VantaMenuScreen(parent, MenuPage.THEME));
				}
			}));
			bx -= 4;
		}
		int cols = Math.max(1, (vw - 10) / (PANEL_W + 8));
		int rows = Math.max(1, (panels.size() + cols - 1) / cols);
		maxList = Math.max(ROW * 4, (vh - 40) / rows - HEADER - 10);
		if (Theme.CLICKGUI_PANELS.get().isEmpty()) arrange();
		for (Panel p : panels.values()) clampPanel(p);
	}

	/** Default spots: as many columns as fit, then a second row. */
	private void arrange() {
		int cols = Math.max(1, (vw - 10) / (PANEL_W + 8));
		int i = 0;
		for (Panel p : panels.values()) {
			p.x = 10 + (i % cols) * (PANEL_W + 8);
			p.y = 34 + (i / cols) * (maxList + HEADER + 10);
			i++;
		}
	}

	private void clampPanel(Panel p) {
		p.x = Math.max(0, Math.min(vw - PANEL_W, p.x));
		p.y = Math.max(30, Math.min(vh - HEADER, p.y));
	}

	private List<Module> visibleModules(Category c) {
		List<Module> out = new ArrayList<>();
		for (Module m : ModuleRegistry.sorted(c)) {
			if (query.isEmpty() || m.name().toLowerCase(Locale.ROOT).contains(query) || m.description().toLowerCase(Locale.ROOT).contains(query)) out.add(m);
		}
		return out;
	}

	private int listHeight(Panel p) {
		if (p.folded) return 0;
		int full = p.modules.size() * ROW + 4;
		return Math.max(ROW, Math.min(Math.min(full, maxList), vh - p.y - HEADER - 6));
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		if (minecraft.level == null) {
			g.fill(0, 0, width, height, Theme.background());
			return;
		}
		if (Theme.BLUR.on()) {
			try {
				renderBlurredBackground(g);
			} catch (IllegalStateException ignored) {
				// already blurred this frame
			}
		}
		g.fill(0, 0, width, height, Colors.withAlpha(Theme.background(), Theme.BACKGROUND_DIM.value() / 100f * 0.8f));
	}

	@Override
	protected void renderScaled(GuiGraphics g, int mx, int my, float delta) {
		hint = null;
		vanta.branding.Logo.draw(g, 10, 9, 14, true, openedAt);
		int sw = Math.min(180, vw / 3), sx = (vw - sw) / 2;
		boolean focused = search != null && search.isFocused();
		Ui.roundRect(g, sx, 8, sw, 16, Theme.radius(), Colors.withAlpha(Theme.background(), 0.7f));
		Ui.roundOutline(g, sx, 8, sw, 16, Theme.radius(), focused ? Theme.accent() : Theme.border());
		Ui.icon(g, Icons.SEARCH, sx + 4, 12, focused ? Theme.accent() : Theme.textFaint());
		for (Btn b : buttons) Ui.button(g, b.x(), b.y(), b.w(), b.h(), b.label(), Ui.inside(mx, my, b.x(), b.y(), b.w(), b.h()), false);

		for (Panel p : panels.values()) {
			p.modules = visibleModules(p.category);
			if (!query.isEmpty() && p.modules.isEmpty()) continue;
			drawPanel(g, p, mx, my);
		}
		String bottom = hint != null ? hint : "Click: on/off   Right-click: settings   Drag a title to move   " + VantaKeys.menuKeyName() + " closes";
		Ui.textCentered(g, Ui.ellipsize(bottom, vw - 20), vw / 2, vh - 12, hint != null ? Theme.text() : Theme.textFaint());
	}

	private void drawPanel(GuiGraphics g, Panel p, int mx, int my) {
		int r = Theme.radius();
		int lh = listHeight(p);
		int total = HEADER + lh;
		int cat = Theme.categoryColor(p.category);
		Ui.shadow(g, p.x, p.y, PANEL_W, total, r);
		Ui.roundRect(g, p.x, p.y, PANEL_W, total, r, Theme.panel());
		Ui.border(g, p.x, p.y, PANEL_W, total, r, Theme.border());
		// header
		Ui.roundRect(g, p.x, p.y, PANEL_W, HEADER, r, Colors.withAlpha(cat, 0.85f));
		if (!p.folded && r > 0) Ui.rect(g, p.x, p.y + HEADER - r, PANEL_W, r, Colors.withAlpha(cat, 0.85f));
		int hc = Colors.contrastText(cat);
		Ui.bold(g, Theme.heading(p.category.label), p.x + 6, p.y + 4, hc, false);
		Ui.textRight(g, p.folded ? "+" : "-", p.x + PANEL_W - 6, p.y + 4, hc);
		if (p.folded) return;
		int maxScroll = Math.max(0, p.modules.size() * ROW + 4 - lh);
		p.scroll = Math.max(0, Math.min(maxScroll, p.scroll));
		int top = p.y + HEADER;
		g.enableScissor(p.x, top, p.x + PANEL_W, top + lh);
		int y = top + 2 - Math.round(p.scroll);
		for (Module m : p.modules) {
			boolean on = m.isEnabled();
			boolean hov = Ui.inside(mx, my, p.x, y, PANEL_W, ROW) && Ui.inside(mx, my, p.x, top, PANEL_W, lh);
			if (on) Ui.rect(g, p.x + 1, y, PANEL_W - 2, ROW, Colors.withAlpha(cat, 0.16f));
			if (hov) Ui.rect(g, p.x + 1, y, PANEL_W - 2, ROW, Theme.panelHover());
			if (on) Ui.rect(g, p.x + 1, y + 2, 2, ROW - 4, cat);
			Ui.text(g, Ui.ellipsize(m.name(), PANEL_W - 22), p.x + 7, y + 3, on ? Colors.mix(cat, 0xFFFFFFFF, 0.25f) : Theme.textDim());
			if (!m.visibleSettings().isEmpty()) Ui.textRight(g, "...", p.x + PANEL_W - 5, y + 2, Theme.textFaint());
			if (hov) hint = m.name() + ": " + m.description();
			y += ROW;
		}
		g.disableScissor();
		if (maxScroll > 0) {
			int th = Math.max(10, lh * lh / (p.modules.size() * ROW + 4));
			int ty = top + Math.round((lh - th) * (p.scroll / maxScroll));
			Ui.rect(g, p.x + PANEL_W - 2, ty, 1, th, Colors.withAlpha(cat, 0.8f));
		}
	}

	private Panel panelAt(double mx, double my) {
		List<Panel> list = new ArrayList<>(panels.values());
		for (int i = list.size() - 1; i >= 0; i--) {
			Panel p = list.get(i);
			if (!query.isEmpty() && p.modules.isEmpty()) continue;
			if (Ui.inside(mx, my, p.x, p.y, PANEL_W, HEADER + listHeight(p))) return p;
		}
		return null;
	}

	private void toFront(Panel p) {
		panels.remove(p.category);
		panels.put(p.category, p);
	}

	@Override
	protected boolean onClick(double mx, double my, int button) {
		for (Btn b : buttons) {
			if (button == 0 && Ui.inside(mx, my, b.x(), b.y(), b.w(), b.h())) {
				MenuSettings.click();
				b.action().run();
				return true;
			}
		}
		Panel p = panelAt(mx, my);
		if (p == null) return false;
		toFront(p);
		if (my < p.y + HEADER) {
			if (button == 0) {
				dragging = p;
				dragDX = (int) mx - p.x;
				dragDY = (int) my - p.y;
				pressX = (int) mx;
				pressY = (int) my;
				moved = false;
			}
			return true;
		}
		int idx = (int) ((my - p.y - HEADER - 2 + p.scroll) / ROW);
		if (idx < 0 || idx >= p.modules.size()) return true;
		Module m = p.modules.get(idx);
		MenuSettings.click();
		if (button == 1) {
			if (!m.visibleSettings().isEmpty()) minecraft.setScreen(VantaMenuScreen.forModule(this, m));
		} else {
			m.toggle();
		}
		return true;
	}

	@Override
	protected boolean onDrag(double mx, double my, int button) {
		if (dragging == null) return false;
		if (Math.abs(mx - pressX) + Math.abs(my - pressY) > 2) moved = true;
		dragging.x = (int) mx - dragDX;
		dragging.y = (int) my - dragDY;
		clampPanel(dragging);
		return true;
	}

	@Override
	protected void onRelease(double mx, double my, int button) {
		if (dragging != null) {
			if (!moved) {
				dragging.folded = !dragging.folded;
				MenuSettings.click();
			}
			dragging = null;
			save();
		}
	}

	@Override
	protected boolean onScroll(double mx, double my, double amount) {
		Panel p = panelAt(mx, my);
		if (p == null) return false;
		p.scroll -= (float) (amount * ROW * 2);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		boolean typing = search != null && search.isFocused();
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			if (!query.isEmpty()) {
				search.setValue("");
				return true;
			}
			onClose();
			return true;
		}
		if (!typing && MenuSettings.CLOSE_WITH_KEY.on() && VantaKeys.isMenuKey(event)) {
			onClose();
			return true;
		}
		if (!typing && event.key() != GLFW.GLFW_KEY_TAB && search != null && event.key() >= GLFW.GLFW_KEY_A && event.key() <= GLFW.GLFW_KEY_Z
				&& (event.modifiers() & GLFW.GLFW_MOD_CONTROL) == 0) {
			setFocused(search);
			search.setFocused(true);
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return MenuSettings.PAUSE_SINGLEPLAYER.on();
	}

	@Override
	public void onClose() {
		save();
		VanillaOptions.saveIfChanged();
		super.onClose();
	}
}
