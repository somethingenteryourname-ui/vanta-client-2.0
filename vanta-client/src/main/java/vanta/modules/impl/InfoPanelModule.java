package vanta.modules.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.profiling.ProfileResults;
import net.minecraft.util.profiling.ResultField;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import vanta.branding.Theme;
import vanta.client.VantaClient;
import vanta.config.BoolSetting;
import vanta.config.IntSetting;
import vanta.metrics.FrameMetrics;
import vanta.modules.Category;
import vanta.modules.Module;
import vanta.net.LivePing;
import vanta.net.RegionLookup;
import vanta.ui.Colors;
import vanta.ui.Ui;

/**
 * Clean F3: when you press F3, VANTA shows tidy themed cards instead of the wall of
 * debug text: performance with a frame-time graph, position, world, what you're looking
 * at, server, a frame-time pie chart, and the lines other mods (like Sodium) add to F3.
 * Turn the module off to get vanilla F3 back.
 */
public final class InfoPanelModule extends Module {
	private static InfoPanelModule instance;

	public final BoolSetting performance = setting(new BoolSetting(key("performance"), "Performance", "FPS, frame-time graph, memory, GPU.", true));
	public final BoolSetting position = setting(new BoolSetting(key("position"), "Position", "XYZ, block, chunk, facing.", true));
	public final BoolSetting world = setting(new BoolSetting(key("world"), "World", "Dimension, biome, light, time, day.", true));
	public final BoolSetting target = setting(new BoolSetting(key("target"), "Target", "What you're looking at.", true));
	public final BoolSetting server = setting(new BoolSetting(key("server"), "Server", "Server, region, ping, brand, players.", true));
	public final BoolSetting pieChart = setting(new BoolSetting(key("pie_chart"), "Pie chart",
			"Where each frame's time goes (rendering, game tick, ...). Uses Minecraft's profiler only while F3 is open.", true));
	public final BoolSetting modLines = setting(new BoolSetting(key("mod_lines"), "Other mods' info",
			"Show the lines other mods add to F3 (Sodium and others) in a Mods card.", true));
	public final IntSetting opacity = setting(new IntSetting(key("opacity"), "Panel opacity", "Background opacity.", 80, 20, 100, 5, "%"));

	private record Row(String label, String value, int color) {
	}

	private record Card(String title, String tag, List<Row> rows) {
	}

	private record Slice(String name, double fraction, int color) {
	}

	private final List<Card> left = new ArrayList<>();
	private final List<Card> right = new ArrayList<>();
	private final List<Slice> slices = new ArrayList<>();
	private final List<String> modText = new ArrayList<>();
	private final float[] frameSamples = new float[160];
	private long nextRefresh;
	private long nextModRefresh;
	private long nextPieRefresh;
	private boolean forcedProfiler;
	private String fpsText = "";
	private int fpsColor;
	private String fpsSub = "";
	private float memFraction;
	private String memText = "";

	public InfoPanelModule() {
		super("info_panel", "Clean F3", "Pressing F3 shows clean info cards, a frame-time graph and pie chart instead of the debug text wall. "
				+ "Off = vanilla F3.", Category.INTERFACE, true);
		instance = this;
	}

	/** True when the vanilla F3 text should be replaced. */
	public static boolean replacesF3() {
		if (instance == null || !instance.isEnabled()) return false;
		Minecraft mc = Minecraft.getInstance();
		return mc.debugEntries != null && mc.debugEntries.isOverlayVisible();
	}

	public static void renderIfOpen(GuiGraphics g) {
		if (!replacesF3()) return;
		try {
			instance.render(g);
		} catch (RuntimeException e) {
			VantaClient.LOG.error("[VANTA] Clean F3 failed and was turned off (vanilla F3 is back)", e);
			instance.setEnabled(false);
		}
	}

	@Override
	public void tick(Minecraft mc) {
		// Keep Minecraft's frame profiler running only while our pie chart is on screen.
		boolean want = pieChart.on() && mc.debugEntries != null && mc.debugEntries.isOverlayVisible();
		if (want != forcedProfiler) setProfiler(mc, want);
	}

	@Override
	protected void onDisable() {
		if (forcedProfiler) setProfiler(Minecraft.getInstance(), false);
	}

	private void setProfiler(Minecraft mc, boolean on) {
		try {
			((vanta.mixin.feature.DebugScreenOverlayAccessor) mc.getDebugOverlay()).vanta$setRenderProfilerChart(on);
			forcedProfiler = on;
		} catch (RuntimeException | LinkageError e) {
			forcedProfiler = on; // accessor missing: no pie chart, but don't retry every tick
		}
	}

	// ------------------------------------------------------------------ data

	private void refresh(Minecraft mc) {
		left.clear();
		right.clear();
		LocalPlayer p = mc.player;
		ClientLevel level = mc.level;
		if (p == null || level == null) return;
		BlockPos pos = p.blockPosition();

		if (performance.on()) {
			int fps = FrameMetrics.fps();
			fpsText = String.valueOf(fps);
			fpsColor = fps >= 120 ? 0xFF4ADE80 : fps >= 60 ? 0xFFA3E635 : fps >= 30 ? 0xFFFACC15 : 0xFFF87171;
			float low = FrameMetrics.onePercentLow();
			fpsSub = String.format(Locale.ROOT, "%.1f ms", FrameMetrics.averageFrameMs()) + (Float.isNaN(low) ? "" : "  ·  1% low " + Math.round(low));
			Runtime rt = Runtime.getRuntime();
			long used = rt.totalMemory() - rt.freeMemory(), max = rt.maxMemory();
			memFraction = max > 0 ? (float) used / max : 0f;
			memText = (used >> 20) + " / " + (max >> 20) + " MB";
			List<Row> rows = new ArrayList<>();
			String gpu;
			try {
				gpu = RenderSystem.getDevice().getRenderer();
			} catch (RuntimeException e) {
				gpu = "?";
			}
			rows.add(new Row("GPU", gpu, 0));
			left.add(new Card("Performance", null, rows));
		}
		if (position.on()) {
			List<Row> rows = new ArrayList<>();
			rows.add(new Row("XYZ", String.format(Locale.ROOT, "%.1f  %.1f  %.1f", p.getX(), p.getY(), p.getZ()), 0));
			rows.add(new Row("Block", pos.getX() + " " + pos.getY() + " " + pos.getZ(), 0));
			rows.add(new Row("Chunk", (pos.getX() >> 4) + " " + (pos.getZ() >> 4) + "   in " + (pos.getX() & 15) + " " + (pos.getZ() & 15), 0));
			float yaw = p.getYRot() % 360f;
			if (yaw < 0) yaw += 360f;
			rows.add(new Row("Facing", cap(p.getDirection().getName()) + " " + axis(p.getDirection().getName())
					+ String.format(Locale.ROOT, "   %.0f° / %.0f°", yaw, p.getXRot()), 0));
			left.add(new Card("Position", null, rows));
		}
		if (world.on()) {
			List<Row> rows = new ArrayList<>();
			String biome = level.getBiome(pos).unwrapKey().map(k -> cap(k.identifier().getPath().replace('_', ' '))).orElse("?");
			rows.add(new Row("Biome", biome, 0));
			rows.add(new Row("Light", "block " + level.getBrightness(LightLayer.BLOCK, pos) + "  ·  sky " + level.getBrightness(LightLayer.SKY, pos), 0));
			long t = level.getDayTime();
			long ticks = Math.floorMod(t, 24000L);
			int hours = (int) ((ticks / 1000 + 6) % 24);
			int minutes = (int) ((ticks % 1000) * 60 / 1000);
			rows.add(new Row("Time", String.format(Locale.ROOT, "%02d:%02d  ·  day %d", hours, minutes, Math.floorDiv(t, 24000L)), 0));
			left.add(new Card("World", cap(level.dimension().identifier().getPath().replace('_', ' ')), rows));
		}
		if (target.on()) {
			HitResult hit = mc.hitResult;
			Entity e = mc.crosshairPickEntity;
			List<Row> rows = new ArrayList<>();
			if (e != null) {
				rows.add(new Row("Entity", e.getDisplayName().getString(), 0));
			} else if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
				BlockPos b = bhr.getBlockPos();
				rows.add(new Row("Block", level.getBlockState(b).getBlock().getName().getString(), 0));
				rows.add(new Row("At", b.getX() + " " + b.getY() + " " + b.getZ(), 0));
			}
			if (!rows.isEmpty()) right.add(new Card("Looking at", null, rows));
		}
		if (server.on()) {
			List<Row> rows = new ArrayList<>();
			ClientPacketListener conn = mc.getConnection();
			boolean sp = mc.hasSingleplayerServer();
			rows.add(new Row("Server", mc.getCurrentServer() != null ? mc.getCurrentServer().ip : sp ? "Singleplayer" : "-", 0));
			String region = RegionLookup.current();
			if (!region.isEmpty() && !sp) {
				String detail = RegionLookup.detail();
				rows.add(new Row("Region", region + (detail.isEmpty() ? "" : "  (" + detail + ")"), Theme.accent()));
			}
			if (!sp) {
				long measured = LivePing.measured();
				int reported = LivePing.reported(mc);
				String ping = measured >= 0 ? measured + " ms" + (reported >= 0 ? "  ·  server says " + reported : "") : reported >= 0 ? reported + " ms" : "-";
				rows.add(new Row("Ping", ping, 0));
			}
			if (conn != null) {
				String brand = conn.serverBrand();
				if (brand != null) rows.add(new Row("Brand", brand, 0));
				rows.add(new Row("Players", conn.getOnlinePlayers().size() + "   ·   entities " + level.getEntityCount(), 0));
			}
			right.add(new Card("Server", null, rows));
		}
	}

	private void refreshPie(Minecraft mc) {
		slices.clear();
		ProfileResults results;
		try {
			results = ((vanta.mixin.feature.ProfilerPieChartAccessor) mc.getDebugOverlay().getProfilerPieChart()).vanta$results();
		} catch (RuntimeException | LinkageError e) {
			return;
		}
		if (results == null) return;
		List<ResultField> times = new ArrayList<>(results.getTimes("root"));
		if (times.isEmpty()) return;
		times.remove(0); // the root itself
		times.sort((a, b) -> Double.compare(b.percentage, a.percentage));
		double other = 0;
		int i = 0;
		for (ResultField f : times) {
			if (f.percentage <= 0) continue;
			if (i < 5 && f.percentage >= 2.0) {
				slices.add(new Slice(niceName(f.name), f.percentage / 100.0, sliceColor(i)));
				i++;
			} else {
				other += f.percentage;
			}
		}
		if (other > 0.5) slices.add(new Slice("Other", other / 100.0, 0xFF6B7280));
	}

	private void refreshModLines(Minecraft mc) {
		modText.clear();
		if (!modLines.on() || mc.debugEntries == null) return;
		DebugScreenDisplayer collector = new DebugScreenDisplayer() {
			@Override public void addPriorityLine(String line) { modText.add(line); }
			@Override public void addLine(String line) { modText.add(line); }
			@Override public void addToGroup(Identifier group, Collection<String> lines) { modText.addAll(lines); }
			@Override public void addToGroup(Identifier group, String line) { modText.add(line); }
		};
		Collection<Identifier> enabled = mc.debugEntries.getCurrentlyEnabled();
		for (Identifier id : enabled) {
			if ("minecraft".equals(id.getNamespace())) continue;
			DebugScreenEntry entry = DebugScreenEntries.getEntry(id);
			if (entry == null) continue;
			try {
				entry.display(collector, mc.level, null, null);
			} catch (RuntimeException e) {
				modText.add(id.getNamespace() + ": (error)");
			}
			if (modText.size() > 24) break;
		}
		modText.removeIf(String::isBlank);
	}

	private static String niceName(String raw) {
		String n = ProfileResults.demanglePath(raw);
		return switch (n) {
			case "gameRenderer" -> "Rendering";
			case "tick" -> "Game tick";
			case "unspecified" -> "Other";
			case "scheduledExecutables" -> "Tasks";
			case "textures" -> "Textures";
			case "sound" -> "Sound";
			case "toasts" -> "Toasts";
			case "blit" -> "Display";
			case "updateDisplay" -> "Display";
			default -> cap(n);
		};
	}

	private static int sliceColor(int i) {
		if (i == 0) return Theme.accent() | 0xFF000000;
		if (i == 1) return Theme.secondary() | 0xFF000000;
		float hue = hueOf(Theme.accent()) + i * 0.17f;
		return Colors.hsb(hue - (float) Math.floor(hue), 0.55f, 0.95f);
	}

	static float hueOf(int argb) {
		float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
		float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
		if (d < 1e-4f) return 0f;
		float h;
		if (max == r) h = ((g - b) / d) % 6f;
		else if (max == g) h = (b - r) / d + 2f;
		else h = (r - g) / d + 4f;
		h /= 6f;
		return h < 0 ? h + 1f : h;
	}

	private static String axis(String dir) {
		return switch (dir) {
			case "north" -> "(-Z)";
			case "south" -> "(+Z)";
			case "east" -> "(+X)";
			case "west" -> "(-X)";
			default -> "";
		};
	}

	private static String cap(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	// ------------------------------------------------------------------ drawing

	private static final int PAD = 7;
	private static final int ROW_H = 10;

	private void render(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		if (now >= nextRefresh) {
			nextRefresh = now + 100;
			refresh(mc);
		}
		if (now >= nextModRefresh) {
			nextModRefresh = now + 500;
			refreshModLines(mc);
		}
		if (pieChart.on() && now >= nextPieRefresh) {
			nextPieRefresh = now + 500;
			refreshPie(mc);
		}
		int gw = g.guiWidth(), gh = g.guiHeight();
		int colW = Math.max(150, Math.min(230, gw / 3));
		int y = 6;
		for (Card c : left) {
			int h = cardHeight(c);
			if (y + h > gh - 4) break;
			drawCard(g, c, 6, y, colW, h);
			y += h + 5;
		}
		int x = gw - colW - 6;
		y = 6;
		for (Card c : right) {
			int h = cardHeight(c);
			if (y + h > gh - 4) break;
			drawCard(g, c, x, y, colW, h);
			y += h + 5;
		}
		if (pieChart.on()) {
			int h = 18 + Math.max(64, Math.max(1, slices.size()) * 11 + 6);
			if (y + h <= gh - 4) {
				drawPieCard(g, x, y, colW, h);
				y += h + 5;
			}
		}
		if (modLines.on() && !modText.isEmpty()) {
			int lines = Math.min(modText.size(), Math.max(1, (gh - 4 - y - 24) / ROW_H));
			if (lines > 0) {
				int h = 18 + lines * ROW_H + PAD - 2;
				drawFrame(g, x, y, colW, h, "Mods", modText.size() > lines ? "+" + (modText.size() - lines) + " more" : null);
				for (int i = 0; i < lines; i++) {
					Ui.draw(g, Ui.ellipsize(modText.get(i), colW - PAD * 2), x + PAD, y + 18 + i * ROW_H, Theme.textDim(), false);
				}
			}
		}
	}

	private int cardHeight(Card c) {
		int h = 18 + c.rows().size() * ROW_H + PAD - 2;
		if (c.title().equals("Performance")) h += 22 + 22 + 14; // big fps, graph, memory bar
		return h;
	}

	private void drawFrame(GuiGraphics g, int x, int y, int w, int h, String title, String tag) {
		float alpha = opacity.value() / 100f;
		Ui.roundRect(g, x, y, w, h, Theme.radius(), Colors.withAlpha(Theme.PANEL.argb(), alpha));
		Ui.roundOutline(g, x, y, w, h, Theme.radius(), Colors.fade(Theme.border(), Math.max(0.4f, alpha)));
		Ui.rect(g, x + PAD, y + 7, 2, 7, Theme.accent());
		Ui.draw(g, Theme.heading(title), x + PAD + 6, y + 6, Theme.text(), false);
		if (tag != null) Ui.textRight(g, Ui.ellipsize(tag, w / 2), x + w - PAD, y + 6, Theme.accent());
	}

	private void drawCard(GuiGraphics g, Card c, int x, int y, int w, int h) {
		drawFrame(g, x, y, w, h, c.title(), c.tag());
		int cy = y + 18;
		if (c.title().equals("Performance")) {
			Ui.textScaled(g, fpsText, x + PAD, cy, 2f, fpsColor, false);
			int fw = Math.round(Ui.width(fpsText) * 2f);
			Ui.draw(g, "FPS", x + PAD + fw + 4, cy + 1, Theme.textDim(), false);
			Ui.draw(g, Ui.ellipsize(fpsSub, w - PAD * 2 - fw - 30), x + PAD + fw + 4, cy + 10, Theme.textFaint(), false);
			cy += 22;
			drawGraph(g, x + PAD, cy, w - PAD * 2, 18);
			cy += 22;
			Ui.draw(g, "Memory", x + PAD, cy, Theme.textDim(), false);
			Ui.textRight(g, memText, x + w - PAD, cy, Theme.text());
			cy += 10;
			int barColor = memFraction > 0.85f ? 0xFFF87171 : memFraction > 0.65f ? 0xFFFACC15 : Theme.accent();
			Ui.bar(g, x + PAD, cy, w - PAD * 2, 2, memFraction, barColor);
			cy += 4;
		}
		int labelW = 0;
		for (Row r : c.rows()) labelW = Math.max(labelW, Ui.width(r.label()));
		for (Row r : c.rows()) {
			Ui.draw(g, r.label(), x + PAD, cy, Theme.textDim(), false);
			int vx = x + PAD + labelW + 8;
			Ui.draw(g, Ui.ellipsize(r.value(), x + w - PAD - vx), vx, cy, r.color() != 0 ? r.color() : Theme.text(), false);
			cy += ROW_H;
		}
	}

	private void drawGraph(GuiGraphics g, int x, int y, int w, int h) {
		Ui.rect(g, x, y, w, h, Colors.withAlpha(0xFF000000, 0.25f));
		int n = FrameMetrics.recentFrameTimes(frameSamples, Math.min(frameSamples.length, w / 2));
		if (n == 0) return;
		float avg = Math.max(1f, FrameMetrics.averageFrameMs());
		float top = Math.max(33.3f, avg * 3f);
		int target = y + h - Math.round(16.7f / top * h);
		Ui.rect(g, x, target, w, 1, Colors.fade(Theme.border(), 0.8f));
		int barW = Math.max(1, w / Math.max(1, n));
		int start = x + w - n * barW;
		for (int i = 0; i < n; i++) {
			float ms = Math.min(top, frameSamples[i]);
			int bh = Math.max(1, Math.round(ms / top * h));
			int color = frameSamples[i] > avg * 2.2f && frameSamples[i] > 20f ? 0xFFF87171 : Theme.accent();
			Ui.rect(g, start + i * barW, y + h - bh, Math.max(1, barW - (barW > 2 ? 1 : 0)), bh, Colors.fade(color, 0.85f));
		}
	}

	private void drawPieCard(GuiGraphics g, int x, int y, int w, int h) {
		drawFrame(g, x, y, w, h, "Frame time", FrameMetrics.averageFrameMs() > 0
				? String.format(Locale.ROOT, "%.1f ms", FrameMetrics.averageFrameMs()) : null);
		int r = 28;
		int cx = x + PAD + r, cy = y + 18 + r + 2;
		if (slices.isEmpty()) {
			donut(g, cx, cy, r, r - 9, List.of(new Slice("", 1, Colors.withAlpha(Theme.textFaint(), 0.4f))));
			Ui.draw(g, "Measuring...", x + PAD + r * 2 + 10, cy - 4, Theme.textDim(), false);
			return;
		}
		donut(g, cx, cy, r, r - 9, slices);
		int lx = x + PAD + r * 2 + 10;
		int ly = y + 20;
		for (Slice s : slices) {
			Ui.rect(g, lx, ly + 1, 6, 6, s.color());
			String pct = Math.round(s.fraction() * 100) + "%";
			Ui.textRight(g, pct, x + w - PAD, ly, Theme.text());
			Ui.draw(g, Ui.ellipsize(s.name(), x + w - PAD - Ui.width(pct) - 6 - (lx + 10)), lx + 10, ly, Theme.textDim(), false);
			ly += 11;
		}
	}

	/** A flat donut chart: one fill per run of same-colored pixels in each row. Slices go clockwise from the top. */
	private static void donut(GuiGraphics g, int cx, int cy, int r, int inner, List<Slice> slices) {
		double total = 0;
		for (Slice s : slices) total += s.fraction();
		if (total <= 0) return;
		double[] ends = new double[slices.size()];
		double acc = 0;
		for (int i = 0; i < ends.length; i++) {
			acc += slices.get(i).fraction() / total;
			ends[i] = acc;
		}
		int r2 = r * r, i2 = inner * inner;
		for (int dy = -r; dy < r; dy++) {
			int runStart = Integer.MIN_VALUE, runColor = 0;
			for (int dx = -r; dx <= r; dx++) {
				double px = dx + 0.5, py = dy + 0.5;
				double d2 = px * px + py * py;
				int color = 0;
				if (dx < r && d2 <= r2 && d2 >= i2) {
					double a = Math.atan2(px, -py) / (Math.PI * 2);
					if (a < 0) a += 1;
					int k = 0;
					while (k < ends.length - 1 && a > ends[k]) k++;
					color = slices.get(k).color();
				}
				if (color != runColor) {
					if (runColor != 0) g.fill(cx + runStart, cy + dy, cx + dx, cy + dy + 1, runColor);
					runStart = dx;
					runColor = color;
				}
			}
		}
	}
}
