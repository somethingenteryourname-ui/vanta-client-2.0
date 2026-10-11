package vanta.modules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import vanta.hud.HudModule;
import vanta.hud.widgets.CoordinatesWidget;
import vanta.hud.widgets.CpsWidget;
import vanta.hud.widgets.DirectionWidget;
import vanta.hud.widgets.FpsWidget;
import vanta.hud.widgets.FrameTimeWidget;
import vanta.hud.widgets.KeystrokesWidget;
import vanta.hud.widgets.MemoryWidget;
import vanta.hud.widgets.PingWidget;
import vanta.crosshair.CrosshairModule;
import vanta.hud.widgets.ArmorStatusWidget;
import vanta.hud.widgets.ClockWidget;
import vanta.hud.widgets.ComboWidget;
import vanta.hud.widgets.CombatCounterWidget;
import vanta.hud.widgets.PotionEffectsWidget;
import vanta.hud.widgets.ReachWidget;
import vanta.hud.widgets.ServerWidget;
import vanta.hud.widgets.SpeedWidget;
import vanta.hud.widgets.SprintStatusWidget;
import vanta.modules.impl.AutoPerspectiveModule;
import vanta.modules.impl.AutoTextModule;
import vanta.modules.impl.BlockOutlineModule;
import vanta.modules.impl.DeathCoordsModule;
import vanta.modules.impl.FreelookModule;
import vanta.modules.impl.FullbrightModule;
import vanta.modules.impl.HitboxesModule;
import vanta.modules.impl.HudTweaksModule;
import vanta.modules.impl.ScreenEffectsModule;
import vanta.modules.impl.ShulkerPreviewModule;
import vanta.modules.impl.TitleScreenModule;
import vanta.modules.impl.ZoomModule;
import vanta.performance.BackgroundFpsModule;
import vanta.performance.EntityCullingModule;
import vanta.performance.ParticlesModule;

/** All modules, in display order. Only fully working features are registered here. */
public final class ModuleRegistry {
	private static final Map<String, Module> MODULES = new LinkedHashMap<>();
	private static final List<HudModule> HUD = new ArrayList<>();

	public static TitleScreenModule TITLE_SCREEN;
	public static CrosshairModule CROSSHAIR;

	private ModuleRegistry() {
	}

	public static void init() {
		if (!MODULES.isEmpty()) return;
		// Performance
		add(new ParticlesModule());
		add(new EntityCullingModule());
		add(new BackgroundFpsModule());
		// Visuals
		add(new ZoomModule());
		add(new FreelookModule());
		add(new FullbrightModule());
		CROSSHAIR = add(new CrosshairModule());
		add(new BlockOutlineModule());
		add(new ScreenEffectsModule());
		add(new HitboxesModule());
		// PvP
		add(new SprintStatusWidget());
		add(new KeystrokesWidget());
		add(new CpsWidget());
		add(new ReachWidget());
		add(new ComboWidget());
		add(new ArmorStatusWidget());
		add(new CombatCounterWidget());
		// HUD
		add(new FpsWidget());
		add(new FrameTimeWidget());
		add(new CoordinatesWidget());
		add(new DirectionWidget());
		add(new PingWidget());
		add(new MemoryWidget());
		add(new PotionEffectsWidget());
		add(new ClockWidget());
		add(new SpeedWidget());
		add(new ServerWidget());
		// Interface / QoL
		TITLE_SCREEN = add(new TitleScreenModule());
		add(new HudTweaksModule());
		add(new ShulkerPreviewModule());
		add(new DeathCoordsModule());
		add(new AutoPerspectiveModule());
		add(new AutoTextModule());
	}

	private static <M extends Module> M add(M module) {
		if (MODULES.containsKey(module.id())) throw new IllegalStateException("Duplicate module id " + module.id());
		MODULES.put(module.id(), module);
		if (module instanceof HudModule hud) HUD.add(hud);
		return module;
	}

	public static List<Module> all() { return List.copyOf(MODULES.values()); }

	/** Runs every enabled module's tick. One module failing never stops the others. */
	public static void tickAll(net.minecraft.client.Minecraft mc) {
		for (Module m : MODULES.values()) {
			if (!m.isEnabled()) continue;
			try {
				m.tick(mc);
			} catch (RuntimeException e) {
				vanta.client.VantaClient.LOG.error("[VANTA] {} failed and was turned off", m.name(), e);
				m.setEnabled(false);
			}
		}
	}

	public static void syncAll() {
		for (Module m : MODULES.values()) {
			try {
				m.syncExternal();
			} catch (RuntimeException e) {
				vanta.client.VantaClient.LOG.warn("[VANTA] Could not sync {}", m.id(), e);
			}
		}
	}

	public static List<HudModule> hud() { return Collections.unmodifiableList(HUD); }

	public static Module byId(String id) { return MODULES.get(id); }

	/** Categories that actually contain modules, in enum order. */
	public static Set<Category> usedCategories() {
		Set<Category> used = EnumSet.noneOf(Category.class);
		for (Module m : MODULES.values()) used.add(m.category());
		return used;
	}

	/** Favorites first, then registration order. Pass null for all categories. */
	public static List<Module> sorted(Category filter) {
		List<Module> out = new ArrayList<>();
		for (Module m : MODULES.values()) if (filter == null || m.category() == filter) out.add(m);
		List<Module> order = new ArrayList<>(MODULES.values());
		out.sort(Comparator.comparing((Module m) -> !m.isFavorite()).thenComparingInt(order::indexOf));
		return out;
	}

	/** Most recently toggled or edited modules, newest first. */
	public static List<Module> recentlyChanged(int limit) {
		List<Module> out = new ArrayList<>();
		for (Module m : MODULES.values()) if (m.lastChanged() > 0) out.add(m);
		out.sort(Comparator.comparingLong(Module::lastChanged).reversed());
		return out.size() > limit ? out.subList(0, limit) : out;
	}
}
