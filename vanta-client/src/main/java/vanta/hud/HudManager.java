package vanta.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import vanta.crosshair.CrosshairRenderer;
import vanta.metrics.InputStats;
import vanta.modules.ModuleRegistry;
import vanta.modules.impl.HudTweaksModule;

/**
 * Draws enabled HUD widgets through Fabric's HUD element API (below chat, above the
 * hotbar), and wraps a few vanilla HUD elements for the crosshair and HUD Tweaks.
 * Each wrapper calls the original element whenever VANTA has nothing to change.
 */
public final class HudManager {
	public static final Identifier HUD_ID = Identifier.fromNamespaceAndPath("vanta", "hud");
	public static final Identifier DAMAGE_ID = Identifier.fromNamespaceAndPath("vanta", "damage_indicators");
	public static final Identifier DURABILITY_ID = Identifier.fromNamespaceAndPath("vanta", "durability_warning");
	public static final Identifier TOTEM_ID = Identifier.fromNamespaceAndPath("vanta", "totem_flat");
	public static final Identifier WAYPOINTS_ID = Identifier.fromNamespaceAndPath("vanta", "waypoints");

	private HudManager() {
	}

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, WAYPOINTS_ID, (g, t) -> {
			try {
				vanta.waypoints.WaypointsModule.render(g, t);
			} catch (RuntimeException e) {
				vanta.client.VantaClient.LOG.error("[VANTA] Waypoint markers failed and were turned off", e);
				if (vanta.waypoints.WaypointsModule.get() != null) vanta.waypoints.WaypointsModule.get().setEnabled(false);
			}
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, DAMAGE_ID, (g, t) -> {
			try {
				vanta.modules.impl.DamageIndicatorsModule.render(g, t);
			} catch (RuntimeException e) {
				vanta.client.VantaClient.LOG.error("[VANTA] Damage indicators failed", e);
			}
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, TOTEM_ID, (g, t) -> {
			try {
				vanta.modules.impl.TotemModule.renderFlat(g, t);
			} catch (RuntimeException e) {
				vanta.client.VantaClient.LOG.error("[VANTA] Flat totem animation failed", e);
			}
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, DURABILITY_ID, (g, t) -> {
			try {
				vanta.hud.widgets.DurabilityWarningModule.render(g, t);
			} catch (RuntimeException e) {
				vanta.client.VantaClient.LOG.error("[VANTA] Durability warning failed", e);
			}
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, HUD_ID, HudManager::render);
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, HudManager::crosshair);
		HudElementRegistry.replaceElement(VanillaHudElements.BOSS_BAR, original -> scaled(original, Anchor.TOP_CENTER, HudTweaksModule::bossBarScale));
		HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, original -> scaled((g, t) -> {
			if (!vanta.modules.impl.ScoreboardModule.render(g)) original.render(g, t);
		}, Anchor.RIGHT_MIDDLE, HudTweaksModule::scoreboardScale));
		HudElementRegistry.replaceElement(VanillaHudElements.TITLE_AND_SUBTITLE, original -> scaled(original, Anchor.CENTER, HudTweaksModule::titleScale));
		HudElementRegistry.replaceElement(VanillaHudElements.STATUS_EFFECTS, original -> (g, t) -> {
			if (!HudTweaksModule.hideEffects()) original.render(g, t);
		});
	}

	private enum Anchor { TOP_CENTER, RIGHT_MIDDLE, CENTER }

	private static HudElement scaled(HudElement original, Anchor anchor, java.util.function.Supplier<Float> scale) {
		return (g, t) -> {
			float s = scale.get();
			if (s == 1f) {
				original.render(g, t);
				return;
			}
			if (s <= 0f) return;
			float ax, ay;
			switch (anchor) {
				case TOP_CENTER -> { ax = g.guiWidth() / 2f; ay = 0f; }
				case RIGHT_MIDDLE -> { ax = g.guiWidth(); ay = g.guiHeight() / 2f; }
				default -> { ax = g.guiWidth() / 2f; ay = g.guiHeight() / 2f; }
			}
			g.pose().pushMatrix();
			g.pose().translate(ax, ay);
			g.pose().scale(s);
			g.pose().translate(-ax, -ay);
			original.render(g, t);
			g.pose().popMatrix();
		};
	}

	private static HudElement crosshair(HudElement original) {
		return (g, t) -> {
			Minecraft mc = Minecraft.getInstance();
			var c = ModuleRegistry.CROSSHAIR;
			boolean custom = c != null && c.isEnabled() && mc.player != null && !mc.player.isSpectator()
					&& !mc.debugEntries.isCurrentlyEnabled(DebugScreenEntries.THREE_DIMENSIONAL_CROSSHAIR);
			if (!custom) {
				original.render(g, t);
				return;
			}
			if (c.shape.get() == vanta.crosshair.CrosshairModule.Shape.VANILLA) {
				// Minecraft's own crosshair (and attack indicator), just resized.
				float s = c.size.value() / 100f;
				if (s == 1f) {
					original.render(g, t);
					return;
				}
				float cx = g.guiWidth() / 2f, cy = g.guiHeight() / 2f;
				g.pose().pushMatrix();
				g.pose().translate(cx, cy);
				g.pose().scale(s);
				g.pose().translate(-cx, -cy);
				original.render(g, t);
				g.pose().popMatrix();
				return;
			}
			if (!mc.options.getCameraType().isFirstPerson() && !c.thirdPerson.on()) return;
			CrosshairRenderer.drawScaled(g, c, g.guiWidth() / 2, g.guiHeight() / 2, false);
		};
	}

	private static void render(GuiGraphics g, DeltaTracker tracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui || mc.screen instanceof HudEditorScreen) return;
		int w = g.guiWidth();
		int h = g.guiHeight();
		boolean needInput = false;
		for (HudModule m : ModuleRegistry.hud()) {
			if (!m.isEnabled()) continue; // hidden widgets are never drawn or updated
			try {
				m.render(g, w, h, false);
			} catch (RuntimeException e) {
				vanta.client.VantaClient.LOG.error("[VANTA] HUD widget {} failed and was turned off", m.name(), e);
				m.setEnabled(false);
			}
			needInput |= m.needsInputStats();
		}
		InputStats.setActive(needInput);
	}
}
