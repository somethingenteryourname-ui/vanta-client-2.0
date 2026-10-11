package vanta.hud;

import net.minecraft.client.gui.GuiGraphics;

import vanta.branding.Theme;
import vanta.config.BoolSetting;
import vanta.config.ColorSetting;
import vanta.config.IntSetting;
import vanta.modules.Category;
import vanta.modules.Module;
import vanta.ui.Colors;
import vanta.ui.Ui;

/**
 * A movable HUD widget. Its position is saved as a fraction of the free screen space
 * (0..1000), so a widget placed against the right edge stays there at any window size
 * or GUI scale.
 */
public abstract class HudModule extends Module {
	public static final int MARGIN = 2;

	public final IntSetting posX;
	public final IntSetting posY;
	public final IntSetting scale = setting(new IntSetting(key("scale"), "Scale",
			"Size of this widget. You can also scroll over it in the HUD editor.", 100, 50, 300, 5, "%"));
	public final BoolSetting background = setting(new BoolSetting(key("background"), "Background",
			"Draw a panel behind this widget.", true));
	public final IntSetting backgroundOpacity = setting(new IntSetting(key("background_opacity"), "Background opacity",
			"How solid the panel behind this widget is.", 45, 0, 100, 5, "%"));
	public final ColorSetting textColor = setting(new ColorSetting(key("text_color"), "Text color",
			"Color of the widget's values.", 0xFFFFFFFF, false));
	public final BoolSetting accentLabels = setting(new BoolSetting(key("accent_labels"), "Accent labels",
			"Color labels using the HUD style label color.", true));
	public final BoolSetting textShadow = setting(new BoolSetting(key("text_shadow"), "Text shadow",
			"Draw a shadow under the text for readability.", true));
	public final BoolSetting locked = setting(new BoolSetting(key("locked"), "Lock position",
			"Prevents this widget from being dragged in the HUD editor.", false));

	/** Last drawn bounds in GUI pixels, used by the HUD editor for hit testing. */
	public int drawnX, drawnY, drawnW, drawnH;

	protected HudModule(String id, String name, String description, Category category, boolean enabledByDefault, int defaultX, int defaultY) {
		super(id, name, description, category, enabledByDefault);
		posX = setting(new IntSetting(key("x"), "X position", "Horizontal position.", defaultX, 0, 1000, 1, ""));
		posX.hidden();
		posY = setting(new IntSetting(key("y"), "Y position", "Vertical position.", defaultY, 0, 1000, 1, ""));
		posY.hidden();
	}

	/** Unscaled content size. */
	public abstract int width();
	public abstract int height();

	/** Refresh cached text/state. Called once per frame before drawing; keep it allocation-free when nothing changed. */
	public abstract void update();

	/** Draw the content at (0,0) in widget space. */
	protected abstract void renderContent(GuiGraphics g, boolean editor);

	/** Whether this widget needs click/CPS sampling. */
	public boolean needsInputStats() { return false; }

	/** Widgets with nothing to show (e.g. no active effects) can return false to skip drawing in game. */
	public boolean hasContent() { return true; }

	public final float scaleFactor() { return scale.value() / 100f; }

	/** Computes on-screen bounds for the current screen size. */
	public final void layout(int screenW, int screenH) {
		float s = scaleFactor();
		drawnW = Math.round(width() * s);
		drawnH = Math.round(height() * s);
		int freeW = Math.max(0, screenW - drawnW - MARGIN * 2);
		int freeH = Math.max(0, screenH - drawnH - MARGIN * 2);
		drawnX = MARGIN + Math.round(posX.value() / 1000f * freeW);
		drawnY = MARGIN + Math.round(posY.value() / 1000f * freeH);
	}

	/** Moves the widget so its top-left corner is at (x, y) in GUI pixels. */
	public final void moveTo(int x, int y, int screenW, int screenH) {
		int freeW = Math.max(1, screenW - drawnW - MARGIN * 2);
		int freeH = Math.max(1, screenH - drawnH - MARGIN * 2);
		posX.set(Math.round((x - MARGIN) * 1000f / freeW));
		posY.set(Math.round((y - MARGIN) * 1000f / freeH));
	}

	public final void render(GuiGraphics g, int screenW, int screenH, boolean editor) {
		update();
		layout(screenW, screenH);
		if (!editor && !hasContent()) return;
		g.pose().pushMatrix();
		try {
			g.pose().translate(drawnX, drawnY);
			g.pose().scale(scaleFactor());
			int w = width(), h = height();
			if (background.on() && backgroundOpacity.value() > 0) {
				Ui.roundRect(g, 0, 0, w, h, Theme.radius(), Colors.withAlpha(HudStyle.BACKGROUND.argb(), backgroundOpacity.value() / 100f));
			}
			if (HudStyle.OUTLINE.on()) Ui.roundOutline(g, 0, 0, w, h, Theme.radius(), Colors.fade(Theme.accent(), 0.8f));
			if (HudStyle.SIDE_BAR.on()) Ui.rect(g, 0, Math.min(2, h / 4), 1, h - Math.min(4, h / 2), Theme.accent());
			renderContent(g, editor);
		} finally {
			g.pose().popMatrix();
		}
	}

	// ---- helpers for subclasses ----

	protected final int valueColor() {
		return HudStyle.CHROMA_VALUES.on() ? Theme.chroma(0.3f) : textColor.argb();
	}

	protected final int labelColor() {
		return accentLabels.on() ? HudStyle.labelColor(valueColor()) : Colors.mix(textColor.argb(), 0xFF000000, 0.3f);
	}

	protected final int pad() { return HudStyle.PADDING.value(); }

	protected final void drawText(GuiGraphics g, String s, int x, int y, int color) {
		g.drawString(Ui.font(), s, x, y, color, textShadow.on());
	}
}
