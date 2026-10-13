package vanta.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.world.InteractionResult;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;

import vanta.branding.Theme;
import vanta.combat.CombatTracker;
import vanta.compat.CompatDetector;
import vanta.config.ConfigManager;
import vanta.gui.MenuSettings;
import vanta.gui.VantaMenuScreen;
import vanta.hud.HudEditorScreen;
import vanta.hud.HudManager;
import vanta.hud.HudStyle;
import vanta.input.VantaKeys;
import vanta.modules.ModuleRegistry;
import vanta.modules.impl.DeathCoordsModule;
import vanta.modules.impl.ShulkerPreviewModule;
import vanta.performance.PerformanceState;
import vanta.profiles.ProfileManager;
import vanta.profiles.ThemeManager;
import vanta.ui.Ui;

/** VANTA Client entry point. Everything VANTA does starts here. */
public final class VantaClient implements ClientModInitializer {
	public static final String MOD_ID = "vanta";
	public static final Logger LOG = LoggerFactory.getLogger("VANTA");

	private static boolean firstTick = true;

	@Override
	public void onInitializeClient() {
		// 1. Register every setting (theme, menu, HUD style, modules) so the config knows what to load.
		Theme.init();
		MenuSettings.init();
		HudStyle.init();
		PerformanceState.init();
		ProfileManager.init();
		ModuleRegistry.init();
		vanta.net.RegionLookup.init();
		CompatDetector.init();
		vanta.menu.SetupState.init();
		vanta.net.LivePing.init();
		// 2. Load saved values (safe: a broken file falls back to defaults).
		ConfigManager.get().load();
		ProfileManager.ensureBuiltIns();
		ThemeManager.refresh();
		// 3. Hooks.
		VantaKeys.register();
		HudManager.register();
		vanta.menu.PauseMenuExtras.register();
		vanta.modules.impl.AutoReconnectModule.register();
		vanta.social.VantaUsers.init();
		try {
			vanta.combat.PopCommands.register();
			vanta.friends.FriendCommands.register();
		} catch (RuntimeException | LinkageError e) {
			LOG.warn("[VANTA] Could not register /resetcounter", e);
		}
		CompatDetector.scan();
		ClientTickEvents.END_CLIENT_TICK.register(VantaClient::onTick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ConfigManager.get().saveIfDirty());

		// Reach/combo stats read your own attacks; they never change or cancel them.
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide()) CombatTracker.onAttack(entity);
			return InteractionResult.PASS;
		});
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> ShulkerPreviewModule.appendTooltip(stack, lines));
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof DeathScreen) {
				DeathCoordsModule.onDeathScreen(client);
				vanta.waypoints.WaypointsModule.onDeath(client);
				ScreenEvents.afterRender(screen).register((s, g, mx, my, delta) -> {
					String text = DeathCoordsModule.deathText();
					if (text != null) g.drawCenteredString(Ui.font(), text, s.width / 2, s.height - 22, 0xFFBFBFBF);
				});
			}
		});
		LOG.info("[VANTA] {} ready for Minecraft {} ({} modules)", VantaInfo.vantaVersion(), VantaInfo.minecraftVersion(), ModuleRegistry.all().size());
	}

	private static void onTick(Minecraft client) {
		if (firstTick) {
			firstTick = false;
			ModuleRegistry.syncAll(); // re-apply vanilla-side state (toggle sprint, hitboxes) now that options exist
		}
		while (VantaKeys.OPEN_MENU.consumeClick()) {
			if (client.screen == null) client.setScreen(new VantaMenuScreen(null));
		}
		while (VantaKeys.HUD_EDITOR.consumeClick()) {
			if (client.screen == null) client.setScreen(new HudEditorScreen(null));
		}
		CombatTracker.tick(client);
		vanta.combat.TotemTracker.tick(client);
		vanta.combat.SessionStats.tick(client, vanta.hud.widgets.SessionStatsWidget.resetOnJoin());
		vanta.social.VantaUsers.tick(client);
		vanta.menu.PauseMenuExtras.tick(client);
		vanta.menu.QuickPlay.tick(client);
		try {
			vanta.net.LivePing.tick(client);
		} catch (RuntimeException e) {
			LOG.debug("[VANTA] Ping measurement failed", e);
		}
		if (client.level == null) vanta.waypoints.WorldProjection.invalidate();
		if (!vanta.waypoints.WaypointsModule.active()) {
			while (VantaKeys.ADD_WAYPOINT.consumeClick()) {
				// Waypoints is off: drop the presses so they don't pile up
			}
			while (VantaKeys.WAYPOINT_LIST.consumeClick()) {
				// same
			}
		}
		ModuleRegistry.tickAll(client);
		ConfigManager.get().tick();
	}
}
