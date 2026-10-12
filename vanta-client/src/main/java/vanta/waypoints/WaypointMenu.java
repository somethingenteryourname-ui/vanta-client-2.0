package vanta.waypoints;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;

import vanta.branding.Theme;
import vanta.gui.MenuSettings;
import vanta.gui.TextInputScreen;
import vanta.gui.VantaMenuScreen;
import vanta.gui.items.BasicItems;
import vanta.gui.items.Item;
import vanta.gui.items.LayoutBuilder;
import vanta.input.VantaKeys;
import vanta.ui.Colors;
import vanta.ui.Icons;
import vanta.ui.Ui;

/** The waypoint list on the Waypoints module page: rename, recolor, hide and delete. */
public final class WaypointMenu {
	private WaypointMenu() {
	}

	public static void build(VantaMenuScreen host, LayoutBuilder lb) {
		Minecraft mc = Minecraft.getInstance();
		lb.full(new BasicItems.SectionTitle("Waypoints in this world", "Add with " + VantaKeys.name(VantaKeys.ADD_WAYPOINT)
				+ ". Click a name to rename, the dot to change color."), BasicItems.SectionTitle.height("x"));
		if (mc.level == null || mc.player == null) {
			BasicItems.Paragraph p = new BasicItems.Paragraph("Join a world or server to see and edit its waypoints.", lb.width, Theme.textFaint());
			lb.full(p, p.preferredHeight());
			return;
		}
		List<Item> actions = new ArrayList<>();
		actions.add(new BasicItems.ButtonItem("Add where I stand", true, () -> {
			WaypointsModule m = WaypointsModule.get();
			if (m != null) m.addHere(mc);
			host.rebuild();
		}, "Saves your current position in this dimension."));
		actions.add(new BasicItems.ButtonItem("Delete death points", false, () -> {
			WaypointManager.current(mc).removeIf(w -> w.death);
			WaypointManager.save();
			host.rebuild();
		}, "Removes every automatic death waypoint in this world."));
		lb.row(actions, 120, 18);

		List<Waypoint> list = WaypointManager.current(mc);
		if (list.isEmpty()) {
			BasicItems.Paragraph p = new BasicItems.Paragraph("No waypoints in this world yet.", lb.width, Theme.textFaint());
			lb.full(p, p.preferredHeight());
			return;
		}
		for (Waypoint w : new ArrayList<>(list)) lb.full(new Row(host, w), 20);
	}

	/** One waypoint line: color dot, name, coordinates, distance, show/hide and delete. */
	private static final class Row extends Item {
		private final VantaMenuScreen host;
		private final Waypoint wp;

		Row(VantaMenuScreen host, Waypoint w) {
			this.host = host;
			this.wp = w;
		}

		private int eyeX(int ax) { return ax + w - 38; }
		private int delX(int ax) { return ax + w - 18; }

		@Override
		public void render(GuiGraphics g, int ax, int ay, int mx, int my, boolean hovered) {
			Ui.card(g, ax, ay, w, h, hovered, 0);
			int cy = ay + (h - 8) / 2;
			Ui.roundRect(g, ax + 6, ay + (h - 8) / 2, 8, 8, 4, wp.visible ? wp.color | 0xFF000000 : Colors.fade(wp.color | 0xFF000000, 0.35f));
			int tx = ax + 20;
			String coords = wp.coords() + "  " + wp.dimensionLabel();
			LocalPlayer p = Minecraft.getInstance().player;
			String dist = "";
			if (p != null && wp.dimension.equals(WaypointManager.dimension(Minecraft.getInstance()))) {
				dist = (int) wp.distanceTo(p.getX(), p.getY(), p.getZ()) + "m";
			}
			int right = eyeX(ax) - 6;
			Ui.textRight(g, dist, right, cy, Theme.textFaint());
			int nameW = Math.min(Ui.width(wp.name), (right - tx) / 2);
			Ui.text(g, Ui.ellipsize(wp.name, nameW + 1), tx, cy, wp.visible ? Theme.text() : Theme.textFaint());
			Ui.text(g, Ui.ellipsize(coords, right - tx - nameW - 12 - Ui.width(dist)), tx + nameW + 8, cy, Theme.textFaint());
			boolean eyeHov = Ui.inside(mx, my, eyeX(ax), ay, 16, h);
			boolean delHov = Ui.inside(mx, my, delX(ax), ay, 16, h);
			Ui.icon(g, Icons.EYE, eyeX(ax) + 3, ay + (h - 9) / 2, eyeHov ? Theme.accent() : wp.visible ? Theme.textDim() : Theme.textFaint());
			Ui.icon(g, Icons.RESET, delX(ax) + 3, ay + (h - 9) / 2, delHov ? 0xFFF87171 : Theme.textDim());
		}

		@Override
		public boolean click(int ax, int ay, double mx, double my, int button) {
			Minecraft mc = Minecraft.getInstance();
			MenuSettings.click();
			if (Ui.inside(mx, my, delX(ax), ay, 16, h)) {
				WaypointManager.remove(mc, wp);
				host.rebuild();
			} else if (Ui.inside(mx, my, eyeX(ax), ay, 16, h)) {
				wp.visible = !wp.visible;
				WaypointManager.save();
			} else if (mx < ax + 18) {
				int[] colors = WaypointManager.COLORS;
				int i = 0;
				while (i < colors.length && colors[i] != wp.color) i++;
				wp.color = colors[(i + 1) % colors.length];
				WaypointManager.save();
			} else {
				mc.setScreen(new TextInputScreen(host, "Rename waypoint", wp.coords() + " in the " + wp.dimensionLabel(), wp.name, 32, value -> {
					String v = value.strip();
					if (v.isEmpty()) return "Enter a name.";
					wp.name = v;
					WaypointManager.save();
					host.rebuild();
					return null;
				}));
			}
			return true;
		}

		@Override
		public String hint() {
			return "Click the name to rename, the dot to recolor, the eye to hide, the arrow to delete.";
		}
	}
}
