package vanta.menu;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import vanta.branding.Logo;
import vanta.branding.Theme;
import vanta.crosshair.CrosshairModule;
import vanta.gui.MenuSettings;
import vanta.gui.TextInputScreen;
import vanta.gui.VantaScreen;
import vanta.modules.Module;
import vanta.modules.ModuleRegistry;
import vanta.performance.PerformancePresets;
import vanta.ui.Colors;
import vanta.ui.Ui;

/**
 * First-launch setup: a few quick steps (look, performance, what you play) so VANTA is
 * set up the way you like before you even open the menu. Can be skipped and run again
 * from Settings.
 */
public final class SetupWizardScreen extends VantaScreen {
	private enum Playstyle {
		CRYSTAL("Crystal PvP", "Crystal crosshair, totem pops, armor, CPS", new String[] {"totem_pops", "armor_status", "cps", "combat_counter", "keystrokes"}),
		SWORD("Sword / Mace PvP", "Keystrokes, CPS, reach, combo, armor", new String[] {"keystrokes", "cps", "reach", "combo", "armor_status"}),
		SURVIVAL("Survival", "Minimap, waypoints, coordinates, effects", new String[] {"minimap", "waypoints", "coordinates", "direction", "potion_effects"}),
		BUILDING("Building", "Coordinates, direction, clock", new String[] {"coordinates", "direction", "clock"}),
		CHILL("Just playing", "FPS, ping and the clock", new String[] {"fps", "ping", "clock"});
		final String label, description;
		final String[] modules;
		Playstyle(String label, String description, String[] modules) {
			this.label = label;
			this.description = description;
			this.modules = modules;
		}
	}

	private record Btn(int x, int y, int w, int h, String label, String sub, Runnable action, BooleanSupplier selected, boolean primary) {
	}

	private static final String[] STEPS = {"Welcome", "Look", "Performance", "What you play", "Done"};

	private final Screen next;
	private final List<Btn> buttons = new ArrayList<>();
	private final Set<Playstyle> styles = new LinkedHashSet<>();
	private int step;
	private PerformancePresets.Preset preset;
	private long stepStart = System.currentTimeMillis();

	public SetupWizardScreen(Screen next) {
		super(Component.literal("Set up VANTA"), next);
		this.next = next;
	}

	@Override protected int minVirtualWidth() { return 420; }
	@Override protected int minVirtualHeight() { return 260; }

	private void go(int s) {
		step = Math.max(0, Math.min(STEPS.length - 1, s));
		stepStart = System.currentTimeMillis();
		layout();
	}

	@Override
	protected void layout() {
		buttons.clear();
		int cw = Math.min(380, vw - 40), cx = (vw - cw) / 2, top = 70;
		switch (step) {
			case 0 -> {
				buttons.add(new Btn(vw / 2 - 70, vh / 2 + 34, 140, 20, "Get started", null, () -> go(1), () -> false, true));
				buttons.add(new Btn(vw / 2 - 40, vh / 2 + 60, 80, 16, "Skip setup", null, this::finish, () -> false, false));
			}
			case 1 -> {
				int nameW = cw * 3 / 5;
				buttons.add(new Btn(cx + nameW + 4, top, cw - nameW - 4, 18, "Font: " + Theme.FONT.get().label(),
						"Click to try the next font. " + Theme.FONT.get().description + ". More on the Theme page.", Theme.FONT::next, () -> false, false));
				buttons.add(new Btn(cx, top, nameW, 18, "Client name: " + Theme.clientName(), "Click to rename. Your logo uses this name.", () ->
						minecraft.setScreen(new TextInputScreen(this, "Client name", "The name shown in your logo, menus and chat watermark.",
								Theme.clientName(), 16, v -> {
									Theme.CLIENT_NAME.set(v);
									return null;
								})), () -> false, false));
				int y = top + 26;
				vanta.branding.UiFormat[] all = vanta.branding.UiFormat.values();
				int cols = 5, gap = 4;
				int tw = (cw - gap * (cols - 1)) / cols, th = Math.max(26, Math.min(40, (vh - 34 - 8 - y) / 3 - gap));
				for (int i = 0; i < all.length; i++) {
					vanta.branding.UiFormat f = all[i];
					buttons.add(new Btn(cx + (i % cols) * (tw + gap), y + (i / cols) * (th + gap), tw, th, "fmt:" + f.name(), f.label(), f::apply,
							() -> Theme.FORMAT.get() == f, false));
				}
			}
			case 2 -> {
				PerformancePresets.Preset[] all = PerformancePresets.Preset.values();
				int y = top;
				for (PerformancePresets.Preset p : all) {
					buttons.add(new Btn(cx, y, cw, 22, p.label, p.description, () -> preset = preset == p ? null : p, () -> preset == p, false));
					y += 25;
				}
				buttons.add(new Btn(cx, y, cw, 16, "Keep my current settings", null, () -> preset = null, () -> preset == null, false));
			}
			case 3 -> {
				int y = top;
				for (Playstyle s : Playstyle.values()) {
					buttons.add(new Btn(cx, y, cw, 22, s.label, s.description, () -> {
						if (!styles.remove(s)) styles.add(s);
					}, () -> styles.contains(s), false));
					y += 25;
				}
			}
			case 4 -> buttons.add(new Btn(vw / 2 - 70, vh / 2 + 40, 140, 20, "Finish", null, this::finish, () -> false, true));
			default -> {
			}
		}
		if (step > 0 && step < STEPS.length - 1) {
			buttons.add(new Btn(cx, vh - 34, 70, 18, "Back", null, () -> go(step - 1), () -> false, false));
			buttons.add(new Btn(cx + cw - 90, vh - 34, 90, 18, step == 3 ? "Apply" : "Next", null, this::nextStep, () -> false, true));
		}
	}

	private void nextStep() {
		if (step == 2 && preset != null) PerformancePresets.apply(preset);
		if (step == 3) applyPlaystyles();
		go(step + 1);
	}

	private void applyPlaystyles() {
		for (Playstyle s : styles) {
			for (String id : s.modules) {
				Module m = ModuleRegistry.byId(id);
				if (m != null) m.setEnabled(true);
			}
		}
		if (styles.contains(Playstyle.CRYSTAL) && ModuleRegistry.CROSSHAIR != null) {
			ModuleRegistry.CROSSHAIR.applyPreset(CrosshairModule.Preset.CRYSTAL);
			ModuleRegistry.CROSSHAIR.setEnabled(true);
		}
		if (styles.contains(Playstyle.SWORD) && ModuleRegistry.CROSSHAIR != null) {
			ModuleRegistry.CROSSHAIR.applyPreset(CrosshairModule.Preset.MACE);
			ModuleRegistry.CROSSHAIR.setEnabled(true);
		}
	}

	private void finish() {
		SetupState.DONE.set(true);
		minecraft.setScreen(next);
	}

	@Override
	public void onClose() {
		finish();
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		if (MenuBackground.wantsPanorama()) MenuBackground.animated(g, width, height);
		else MenuBackground.outOfWorld(g, width, height);
	}

	@Override
	protected void renderScaled(GuiGraphics g, int mx, int my, float delta) {
		// step dots
		int dotsW = STEPS.length * 10;
		for (int i = 0; i < STEPS.length; i++) {
			int c = i == step ? Theme.accent() : i < step ? Colors.withAlpha(Theme.accent(), 0.5f) : Colors.withAlpha(Theme.text(), 0.2f);
			Ui.roundRect(g, vw / 2 - dotsW / 2 + i * 10, 14, i == step ? 8 : 6, 6, 3, c);
		}
		int cw = Math.min(380, vw - 40), cx = (vw - cw) / 2;
		switch (step) {
			case 0 -> {
				int size = 34;
				Logo.draw(g, (vw - Logo.width(size, true)) / 2, vh / 2 - 60, size, true, stepStart);
				Ui.textCentered(g, "Welcome! Let's set things up in four quick steps.", vw / 2, vh / 2 - 6, Theme.text());
				Ui.textCentered(g, "You can change everything later in the menu (Right Shift).", vw / 2, vh / 2 + 8, Theme.textFaint());
			}
			case 1 -> heading(g, cx, "Pick your look", "A format sets the layout and colors of the menu. Change it any time on the Theme page.");
			case 2 -> heading(g, cx, "Performance", "Pick a preset for your PC. You can undo it from the Performance page.");
			case 3 -> heading(g, cx, "What do you play?", "Pick any. The matching HUD widgets are turned on for you.");
			case 4 -> {
				int size = 26;
				Logo.draw(g, (vw - Logo.width(size, true)) / 2, vh / 2 - 66, size, true, stepStart);
				Ui.textCentered(g, "You're all set.", vw / 2, vh / 2 - 22, Theme.text());
				Ui.textCentered(g, "Right Shift opens the VANTA menu. The HUD editor is on the HUD page.", vw / 2, vh / 2 - 8, Theme.textDim());
				Ui.textCentered(g, "Chat: Ctrl+F searches, right-click copies, :skull: makes a symbol.", vw / 2, vh / 2 + 6, Theme.textDim());
				Ui.textCentered(g, "The crosshair editor is on the Crosshair page.", vw / 2, vh / 2 + 20, Theme.textDim());
			}
			default -> {
			}
		}
		for (Btn b : buttons) {
			boolean hov = Ui.inside(mx, my, b.x(), b.y(), b.w(), b.h());
			boolean sel = b.selected().getAsBoolean();
			if (b.label().startsWith("fmt:")) {
				vanta.branding.UiFormat f = vanta.branding.UiFormat.valueOf(b.label().substring(4));
				Ui.roundRect(g, b.x() - 1, b.y() - 1, b.w() + 2, b.h() + 2, 3, sel ? Theme.accent() : hov ? Theme.textDim() : Theme.border());
				vanta.gui.items.FormatCard.drawPreview(g, f, b.x(), b.y(), b.w(), b.h() - 10);
				Ui.rect(g, b.x(), b.y() + b.h() - 10, b.w(), 10, Colors.withAlpha(Theme.background(), 0.9f));
				Ui.textCentered(g, Ui.ellipsize(f.label(), b.w() - 2), b.x() + b.w() / 2, b.y() + b.h() - 9, sel ? Theme.accent() : Theme.text());
				if (hov) Ui.textCentered(g, f.description, vw / 2, vh - 48, Theme.textFaint());
				continue;
			}
			if (b.label().isEmpty()) {
				// palette swatch
				Theme.Palette p = paletteFor(b.sub());
				Ui.roundRect(g, b.x() - 1, b.y() - 1, b.w() + 2, b.h() + 2, 4, sel ? Theme.text() : hov ? Theme.textDim() : Theme.border());
				if (p != null) Ui.hGradient(g, b.x(), b.y(), b.w(), b.h(), p.accent | 0xFF000000, p.secondary | 0xFF000000);
				continue;
			}
			if (b.sub() != null && b.h() >= 20) {
				Ui.card(g, b.x(), b.y(), b.w(), b.h(), hov, sel ? Theme.accent() : 0);
				if (sel) Ui.rect(g, b.x(), b.y() + 3, 2, b.h() - 6, Theme.accent());
				Ui.text(g, b.label(), b.x() + 8, b.y() + 3, sel ? Theme.accent() : Theme.text());
				Ui.text(g, Ui.ellipsize(b.sub(), b.w() - 16), b.x() + 8, b.y() + 12, Theme.textFaint());
			} else {
				Ui.button(g, b.x(), b.y(), b.w(), b.h(), b.label(), hov, b.primary() || sel);
				if (b.sub() != null && hov) Ui.textCentered(g, b.sub(), vw / 2, b.y() + b.h() + 4, Theme.textFaint());
			}
		}
	}

	private static Theme.Palette paletteFor(String label) {
		for (Theme.Palette p : Theme.Palette.values()) if (p.label.equals(label)) return p;
		return null;
	}

	private void heading(GuiGraphics g, int x, String title, String sub) {
		Ui.textScaled(g, title, x, 34, 1.4f, Theme.text(), false);
		Ui.text(g, sub, x, 50, Theme.textFaint());
	}

	@Override
	protected boolean onClick(double mx, double my, int button) {
		if (button != 0) return false;
		for (Btn b : new ArrayList<>(buttons)) {
			if (Ui.inside(mx, my, b.x(), b.y(), b.w(), b.h())) {
				MenuSettings.click();
				b.action().run();
				if (step == 1) layout(); // client name label
				return true;
			}
		}
		return false;
	}
}
