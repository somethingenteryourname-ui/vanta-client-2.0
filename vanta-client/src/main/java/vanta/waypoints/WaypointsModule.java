package vanta.waypoints;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import vanta.config.BoolSetting;
import vanta.config.ChoiceSetting;
import vanta.config.IntSetting;
import vanta.config.Labeled;
import vanta.gui.VantaMenuScreen;
import vanta.input.VantaKeys;
import vanta.modules.Category;
import vanta.modules.Module;
import vanta.ui.Colors;
import vanta.ui.Ui;

/**
 * Waypoints: save spots per world and see them on screen with their distance. A death point
 * is added automatically when you die. Markers are only drawn for your own saved
 * coordinates; nothing about other players or hidden blocks is shown.
 */
public final class WaypointsModule extends Module {
	public enum Names implements Labeled {
		ALWAYS("Always"), LOOKING("When looking at it"), NEVER("Never");
		private final String label;
		Names(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	private static WaypointsModule instance;
	private static boolean deathRecorded;
	private static final float[] OUT = new float[2];

	public final BoolSetting markers = setting(new BoolSetting(key("markers"), "On-screen markers",
			"Show waypoints in the world with their distance.", true));
	public final ChoiceSetting<Names> names = setting(new ChoiceSetting<>(key("names"), "Show names",
			"When to show waypoint names next to the marker.", Names.ALWAYS));
	public final BoolSetting distance = setting(new BoolSetting(key("distance"), "Show distance",
			"Distance in blocks under each marker.", true));
	public final IntSetting maxDistance = setting(new IntSetting(key("max_distance"), "Hide beyond",
			"Hide markers farther than this. 0 shows them at any distance.", 0, 0, 20000, 250, " blocks"));
	public final IntSetting markerScale = setting(new IntSetting(key("marker_scale"), "Marker size",
			"Size of markers and their labels.", 100, 50, 200, 10, "%"));
	public final BoolSetting deathPoints = setting(new BoolSetting(key("death_points"), "Death waypoint",
			"Add a waypoint where you die.", true));
	public final BoolSetting keepDeaths = setting(new BoolSetting(key("keep_deaths"), "Keep old death points",
			"Off: only your latest death is kept.", false));
	public final BoolSetting chatMessages = setting(new BoolSetting(key("chat_messages"), "Chat confirmations",
			"Show a line in your own chat when a waypoint is added (only you see it).", true));

	public WaypointsModule() {
		super("waypoints", "Waypoints", "Save locations per world and see them on screen with distance. Adds a death point automatically. "
				+ "Press the Add Waypoint key (B by default) to save where you stand.", Category.HUD, true);
		instance = this;
	}

	public static WaypointsModule get() { return instance; }

	public static boolean active() { return instance != null && instance.isEnabled(); }

	@Override
	public void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null) {
			WaypointManager.resetKey();
			deathRecorded = false;
			return;
		}
		if (p.isAlive() && !(mc.screen instanceof DeathScreen)) deathRecorded = false;
		if (VantaKeys.ADD_WAYPOINT != null) {
			while (VantaKeys.ADD_WAYPOINT.consumeClick()) addHere(mc);
		}
		if (VantaKeys.WAYPOINT_LIST != null) {
			while (VantaKeys.WAYPOINT_LIST.consumeClick()) {
				if (mc.screen == null) mc.setScreen(VantaMenuScreen.forModule(null, this));
			}
		}
	}

	public void addHere(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null) return;
		BlockPos pos = p.blockPosition();
		Waypoint w = WaypointManager.add(mc, WaypointManager.nextName(mc), pos.getX(), pos.getY(), pos.getZ());
		if (w != null && chatMessages.on()) {
			p.displayClientMessage(Component.literal("[VANTA] Added waypoint \"" + w.name + "\" at " + w.coords()
					+ " - rename it in the VANTA menu > Waypoints").withStyle(ChatFormatting.GRAY), false);
		}
	}

	/** Called when the death screen opens. Records a single death point per death. */
	public static void onDeath(Minecraft mc) {
		if (!active() || !instance.deathPoints.on() || deathRecorded || mc.player == null) return;
		deathRecorded = true;
		BlockPos pos = mc.player.blockPosition();
		WaypointManager.addDeath(mc, pos.getX(), pos.getY(), pos.getZ(), instance.keepDeaths.on());
	}

	// ------------------------------------------------------------------ rendering

	public static void render(GuiGraphics g, DeltaTracker tracker) {
		Minecraft mc = Minecraft.getInstance();
		if (!active() || !instance.markers.on() || mc.options.hideGui || mc.player == null || mc.level == null) return;
		List<Waypoint> list = WaypointManager.current(mc);
		if (list.isEmpty() || !WorldProjection.valid()) return;
		String dim = WaypointManager.dimension(mc);
		int gw = g.guiWidth(), gh = g.guiHeight();
		double cx = WorldProjection.cameraX(), cy = WorldProjection.cameraY(), cz = WorldProjection.cameraZ();
		float scale = instance.markerScale.value() / 100f;
		int max = instance.maxDistance.value();
		Names names = instance.names.get();
		for (Waypoint w : list) {
			if (!w.visible || !w.dimension.equals(dim)) continue;
			double d = w.distanceTo(cx, cy, cz);
			if (max > 0 && d > max) continue;
			if (d < 2.0) continue; // standing on it
			if (!WorldProjection.project(w.x + 0.5, w.y + 1.2, w.z + 0.5, gw, gh, OUT)) continue;
			float sx = OUT[0], sy = OUT[1];
			if (sx < -50 || sy < -50 || sx > gw + 50 || sy > gh + 50) continue;
			boolean looking = Math.abs(sx - gw / 2f) < 30 && Math.abs(sy - gh / 2f) < 30;
			drawMarker(g, w, sx, sy, scale, d, names == Names.ALWAYS || names == Names.LOOKING && looking, looking);
		}
	}

	private static void drawMarker(GuiGraphics g, Waypoint w, float sx, float sy, float scale, double dist, boolean showName, boolean looking) {
		g.pose().pushMatrix();
		g.pose().translate(sx, sy);
		g.pose().scale(scale);
		int color = w.color | 0xFF000000;
		// diamond: dark outline, colored fill
		for (int dy = -4; dy <= 4; dy++) {
			int half = 4 - Math.abs(dy);
			g.fill(-half, dy, half + 1, dy + 1, 0xC0000000);
		}
		for (int dy = -3; dy <= 3; dy++) {
			int half = 3 - Math.abs(dy);
			g.fill(-half, dy, half + 1, dy + 1, color);
		}
		int ty = 7;
		if (showName) {
			String name = w.name;
			int tw = Ui.width(name);
			g.fill(-tw / 2 - 2, -16, tw / 2 + 3, -6, looking ? 0xB0000000 : 0x80000000);
			g.drawString(Ui.font(), name, -tw / 2, -15, Colors.mix(color, 0xFFFFFFFF, 0.35f), false);
		}
		if (instance.distance.on()) {
			String text = dist >= 1000 ? String.format(java.util.Locale.ROOT, "%.1fk", dist / 1000.0) : String.valueOf((int) dist) + "m";
			int tw = Ui.width(text);
			g.fill(-tw / 2 - 2, ty - 1, tw / 2 + 3, ty + 9, 0x80000000);
			g.drawString(Ui.font(), text, -tw / 2, ty, 0xFFE5E7EB, false);
		}
		g.pose().popMatrix();
	}

	@Override
	public String status() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return null;
		int n = WaypointManager.current(mc).size();
		return n == 1 ? "1 waypoint here" : n + " waypoints here";
	}
}
