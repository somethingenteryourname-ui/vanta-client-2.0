package vanta.gui.items;

import java.util.ArrayList;
import java.util.List;

/** Stacks items vertically and lays out grids, at the menu's density. */
public final class LayoutBuilder {
	public final List<Item> items = new ArrayList<>();
	public final int width;
	public final int gap;
	public int y;

	public LayoutBuilder(int width, int gap) {
		this.width = width;
		this.gap = gap;
	}

	public <T extends Item> T full(T item, int height) {
		item.x = 0;
		item.y = y;
		item.w = width;
		item.h = height;
		items.add(item);
		y += height + gap;
		return item;
	}

	/** Lays items out in as many columns of at least minWidth as fit. */
	public void grid(List<? extends Item> list, int minWidth, int height) {
		if (list.isEmpty()) return;
		int cols = Math.max(1, Math.min(list.size(), (width + gap) / (minWidth + gap)));
		int colW = (width - gap * (cols - 1)) / cols;
		for (int i = 0; i < list.size(); i++) {
			Item it = list.get(i);
			int c = i % cols;
			int r = i / cols;
			it.x = c * (colW + gap);
			it.y = y + r * (height + gap);
			it.w = (c == cols - 1) ? width - it.x : colW;
			it.h = height;
			items.add(it);
		}
		int rows = (list.size() + cols - 1) / cols;
		y += rows * (height + gap);
	}

	/** Items side by side with equal widths on one row (wraps when narrower than minWidth). */
	public void row(List<? extends Item> list, int minWidth, int height) {
		grid(new ArrayList<>(list), minWidth, height);
	}

	public void space(int px) { y += px; }

	public int contentHeight() { return Math.max(0, y - gap); }
}
