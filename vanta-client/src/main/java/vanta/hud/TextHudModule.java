package vanta.hud;

import net.minecraft.client.gui.GuiGraphics;

import vanta.modules.Category;
import vanta.ui.Ui;

/**
 * A HUD widget made of "Label value" lines, written in the global HUD text format.
 * Text is rebuilt only when the value or format changes, so drawing does not allocate
 * every frame.
 */
public abstract class TextHudModule extends HudModule {
	protected static final int LINE = 10;

	private String[] labels = new String[0];
	private String[] values = new String[0];
	// Prepared segments per line: [prefix, label, middle, value, suffix] where label/value are colored.
	private String[][] parts = new String[0][];
	private int[][] partWidths = new int[0][];
	private int contentWidth;
	private HudStyle.Format builtFormat;

	protected TextHudModule(String id, String name, String description, Category category, boolean enabledByDefault, int defaultX, int defaultY) {
		super(id, name, description, category, enabledByDefault, defaultX, defaultY);
	}

	/** Replace the displayed lines. Call only when something changed. */
	protected final void setLines(String[] newLabels, String[] newValues) {
		labels = newLabels;
		values = newValues;
		rebuild();
	}

	protected final void setLine(String label, String value) {
		if (labels.length == 1 && labels[0].equals(label) && values[0].equals(value) && builtFormat == HudStyle.FORMAT.get()) return;
		setLines(new String[]{label}, new String[]{value});
	}

	protected final boolean hasLines() { return labels.length > 0; }

	private void rebuild() {
		HudStyle.Format f = HudStyle.FORMAT.get();
		builtFormat = f;
		parts = new String[labels.length][];
		partWidths = new int[labels.length][];
		int max = 0;
		for (int i = 0; i < labels.length; i++) {
			String l = labels[i], v = values[i];
			String[] p;
			if (l.isEmpty()) {
				p = new String[]{"", "", "", v, ""};
			} else {
				p = switch (f) {
					case LABEL_VALUE -> new String[]{"", l, " ", v, ""};
					case COLON -> new String[]{"", l, ": ", v, ""};
					case BRACKETS -> new String[]{"[", l, ": ", v, "]"};
					case VALUE_LABEL -> new String[]{"", "", "", v, " " + l};
					case VALUE_ONLY -> new String[]{"", "", "", v, ""};
				};
			}
			int[] w = new int[5];
			int total = 0;
			for (int k = 0; k < 5; k++) {
				w[k] = p[k].isEmpty() ? 0 : Ui.width(p[k]);
				total += w[k];
			}
			parts[i] = p;
			partWidths[i] = w;
			max = Math.max(max, total);
		}
		contentWidth = max;
	}

	@Override
	public int width() {
		if (builtFormat != HudStyle.FORMAT.get()) rebuild();
		return Math.max(20, contentWidth + pad() * 2);
	}
	@Override public int height() { return Math.max(1, labels.length) * LINE + pad() * 2 - 2; }

	@Override
	protected void renderContent(GuiGraphics g, boolean editor) {
		if (builtFormat != HudStyle.FORMAT.get()) rebuild();
		int y = pad();
		int valueColor = valueColor();
		int labelColor = labelColor();
		for (int i = 0; i < parts.length; i++) {
			String[] p = parts[i];
			int[] w = partWidths[i];
			int x = pad();
			if (w[0] > 0) drawText(g, p[0], x, y, labelColor);
			x += w[0];
			if (w[1] > 0) drawText(g, p[1], x, y, labelColor);
			x += w[1];
			if (w[2] > 0) drawText(g, p[2], x, y, labelColor);
			x += w[2];
			drawText(g, p[3], x, y, valueColor);
			x += w[3];
			if (w[4] > 0) drawText(g, p[4], x, y, labelColor);
			y += LINE;
		}
	}
}
