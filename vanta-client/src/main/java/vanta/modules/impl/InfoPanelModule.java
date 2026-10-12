package vanta.modules.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import vanta.branding.Theme;
import vanta.config.BoolSetting;
import vanta.config.IntSetting;
import vanta.metrics.FrameMetrics;
import vanta.modules.Category;
import vanta.modules.Module;
import vanta.net.RegionLookup;
import vanta.ui.Colors;
import vanta.ui.Ui;

/**
 * Clean F3: when you press F3, VANTA shows a tidy, themed info panel instead of the wall
 * of debug text. Turn the module off to get vanilla F3 back.
 */
public final class InfoPanelModule extends Module {
	private static InfoPanelModule instance;

	public final BoolSetting performance = setting(new BoolSetting(key("performance"), "Performance", "FPS, frame time, memory, GPU.", true));
	public final BoolSetting position = setting(new BoolSetting(key("position"), "Position", "XYZ, block, chunk, facing.", true));
	public final BoolSetting world = setting(new BoolSetting(key("world"), "World", "Dimension, biome, light, time, day.", true));
	public final BoolSetting target = setting(new BoolSetting(key("target"), "Target", "What you're looking at.", true));
	public final BoolSetting server = setting(new BoolSetting(key("server"), "Server", "Server, region, ping, brand, entities.", true));
	public final IntSetting opacity = setting(new IntSetting(key("opacity"), "Panel opacity", "Background opacity.", 80, 20, 100, 5, "%"));

	private final List<String[]> rows = new ArrayList<>();
	private long nextRefresh;

	public InfoPanelModule() {
		super("info_panel", "Clean F3", "Pressing F3 shows a clean info panel instead of the debug text wall. Off = vanilla F3.",
				Category.INTERFACE, true);
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
		instance.render(g);
	}

	private void refresh(Minecraft mc) {
		rows.clear();
		LocalPlayer p = mc.player;
		ClientLevel level = mc.level;
		if (p == null || level == null) return;
		BlockPos pos = p.blockPosition();
		if (performance.on()) {
			section("Performance");
			add("FPS", FrameMetrics.fps() + "  (1% low " + (Float.isNaN(FrameMetrics.onePercentLow()) ? "..." : Math.round(FrameMetrics.onePercentLow())) + ")");
			add("Frame", String.format(Locale.ROOT, "%.1f ms avg, %.1f ms worst", FrameMetrics.averageFrameMs(), FrameMetrics.worstFrameMs()));
			Runtime rt = Runtime.getRuntime();
			long used = (rt.totalMemory() - rt.freeMemory()) >> 20, max = rt.maxMemory() >> 20;
			add("Memory", used + " / " + max + " MB (" + (used * 100 / Math.max(1, max)) + "%)");
			String gpu;
			try {
				gpu = RenderSystem.getDevice().getRenderer();
			} catch (RuntimeException e) {
				gpu = "?";
			}
			add("GPU", gpu);
			add("Java", System.getProperty("java.version") + "  (" + Runtime.getRuntime().availableProcessors() + " CPU threads)");
		}
		if (position.on()) {
			section("Position");
			add("XYZ", String.format(Locale.ROOT, "%.2f / %.2f / %.2f", p.getX(), p.getY(), p.getZ()));
			add("Block", pos.getX() + " " + pos.getY() + " " + pos.getZ());
			add("Chunk", (pos.getX() >> 4) + " " + (pos.getZ() >> 4) + "  (in chunk " + (pos.getX() & 15) + " " + (pos.getZ() & 15) + ")");
			float yaw = p.getYRot() % 360f;
			if (yaw < 0) yaw += 360f;
			add("Facing", cap(p.getDirection().getName()) + String.format(Locale.ROOT, "  (%.1f / %.1f)", yaw, p.getXRot()));
		}
		if (world.on()) {
			section("World");
			add("Dimension", cap(level.dimension().identifier().getPath().replace('_', ' ')));
			String biome = level.getBiome(pos).unwrapKey().map(k -> cap(k.identifier().getPath().replace('_', ' '))).orElse("?");
			add("Biome", biome);
			add("Light", "block " + level.getBrightness(LightLayer.BLOCK, pos) + ", sky " + level.getBrightness(LightLayer.SKY, pos));
			long t = level.getDayTime();
			long ticks = Math.floorMod(t, 24000L);
			int hours = (int) ((ticks / 1000 + 6) % 24);
			int minutes = (int) ((ticks % 1000) * 60 / 1000);
			add("Time", String.format(Locale.ROOT, "%02d:%02d  (day %d)", hours, minutes, Math.floorDiv(t, 24000L)));
		}
		if (target.on()) {
			HitResult hit = mc.hitResult;
			Entity e = mc.crosshairPickEntity;
			if (e != null) {
				section("Target");
				add("Entity", e.getDisplayName().getString());
			} else if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
				section("Target");
				BlockPos b = bhr.getBlockPos();
				add("Block", level.getBlockState(b).getBlock().getName().getString());
				add("At", b.getX() + " " + b.getY() + " " + b.getZ());
			}
		}
		if (server.on()) {
			section("Server");
			ClientPacketListener conn = mc.getConnection();
			add("Address", mc.getCurrentServer() != null ? mc.getCurrentServer().ip : mc.hasSingleplayerServer() ? "Singleplayer" : "-");
			String region = RegionLookup.current();
			if (!region.isEmpty()) add("Region", region);
			if (conn != null) {
				PlayerInfo info = conn.getPlayerInfo(p.getUUID());
				if (info != null) add("Ping", info.getLatency() + " ms");
				String brand = conn.serverBrand();
				if (brand != null) add("Brand", brand);
				add("Players", String.valueOf(conn.getOnlinePlayers().size()));
			}
			add("Entities", String.valueOf(level.getEntityCount()));
		}
	}

	private static String cap(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	private void section(String title) { rows.add(new String[]{title}); }

	private void add(String label, String value) { rows.add(new String[]{label, value}); }

	private void render(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		if (now >= nextRefresh) {
			nextRefresh = now + 100;
			refresh(mc);
		}
		if (rows.isEmpty()) return;
		int labelW = 0, valueW = 0;
		for (String[] r : rows) {
			if (r.length == 2) {
				labelW = Math.max(labelW, Ui.width(r[0]));
				valueW = Math.max(valueW, Ui.width(r[1]));
			}
		}
		int w = Math.min(g.guiWidth() / 2, labelW + valueW + 26);
		int h = 8;
		for (String[] r : rows) h += r.length == 1 ? 14 : 10;
		int x = 4, y = 4;
		Ui.roundRect(g, x, y, w, h, Theme.radius(), Colors.withAlpha(Theme.PANEL.argb(), opacity.value() / 100f));
		Ui.roundOutline(g, x, y, w, h, Theme.radius(), Theme.border());
		Ui.accentFill(g, x, y, w, 2, 0, 0f);
		int cy = y + 6;
		for (String[] r : rows) {
			if (r.length == 1) {
				cy += 2;
				g.drawString(Ui.font(), Theme.heading(r[0]), x + 8, cy, Theme.accent(), false);
				cy += 12;
			} else {
				g.drawString(Ui.font(), r[0], x + 8, cy, Theme.textDim(), false);
				g.drawString(Ui.font(), Ui.ellipsize(r[1], w - labelW - 26), x + 16 + labelW, cy, Theme.text(), false);
				cy += 10;
			}
		}
	}
}
