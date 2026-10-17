package vanta.performance;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.server.level.ParticleStatus;

import vanta.client.VantaClient;
import vanta.config.ConfigManager;
import vanta.config.Labeled;

/**
 * Performance presets: named bundles of Minecraft video settings plus VANTA booster
 * settings. Every preset shows exactly what it will change before you apply it, and the
 * previous values are saved so "Undo" puts everything back.
 */
public final class PerformancePresets {
	public enum Preset implements Labeled {
		MAX_FPS("Max FPS", "Highest frame rate: short view distance, no clouds, minimal particles, no extras."),
		COMPETITIVE("Competitive", "For Crystal/Mace PvP: steady high FPS, clear view, explosion clutter removed."),
		BALANCED("Balanced", "Good FPS while keeping the game looking nice."),
		RECORDING("Recording", "Stable frame pacing for OBS or Medal: capped FPS and moderate distances."),
		POTATO("Potato", "For weak laptops and integrated graphics: everything turned down.");

		public final String label;
		public final String description;

		Preset(String label, String description) {
			this.label = label;
			this.description = description;
		}

		@Override public String label() { return label; }
	}

	/** One option a preset changes. */
	public record Change(String name, Object value, String display) {
	}

	private static final Map<String, Function<Options, OptionInstance<?>>> OPTIONS = new LinkedHashMap<>();

	static {
		OPTIONS.put("Render distance", Options::renderDistance);
		OPTIONS.put("Simulation distance", Options::simulationDistance);
		OPTIONS.put("Max framerate", Options::framerateLimit);
		OPTIONS.put("VSync", Options::enableVsync);
		OPTIONS.put("Clouds", Options::cloudStatus);
		OPTIONS.put("Particles", Options::particles);
		OPTIONS.put("Smooth lighting", Options::ambientOcclusion);
		OPTIONS.put("Entity shadows", Options::entityShadows);
		OPTIONS.put("Biome blend", Options::biomeBlendRadius);
		OPTIONS.put("Mipmap levels", Options::mipmapLevels);
		OPTIONS.put("Entity distance", Options::entityDistanceScaling);
		OPTIONS.put("Chunk updates", Options::prioritizeChunkUpdates);
		OPTIONS.put("Chunk fade-in", Options::chunkSectionFadeInTime);
		OPTIONS.put("Cutout leaves", Options::cutoutLeaves);
		OPTIONS.put("Improved transparency", Options::improvedTransparency);
		OPTIONS.put("Weather radius", Options::weatherRadius);
	}

	private PerformancePresets() {
	}

	/** The Minecraft settings each preset applies. */
	public static List<Change> changes(Preset p) {
		List<Change> c = new ArrayList<>();
		switch (p) {
			case MAX_FPS -> {
				c.add(new Change("Render distance", 6, "6 chunks"));
				c.add(new Change("Simulation distance", 5, "5 chunks"));
				c.add(new Change("Max framerate", 260, "Unlimited"));
				c.add(new Change("VSync", false, "Off"));
				c.add(new Change("Clouds", CloudStatus.OFF, "Off"));
				c.add(new Change("Particles", ParticleStatus.MINIMAL, "Minimal"));
				c.add(new Change("Smooth lighting", false, "Off"));
				c.add(new Change("Entity shadows", false, "Off"));
				c.add(new Change("Biome blend", 0, "Off"));
				c.add(new Change("Mipmap levels", 0, "Off"));
				c.add(new Change("Entity distance", 0.75, "75%"));
				c.add(new Change("Chunk updates", PrioritizeChunkUpdates.NONE, "Threaded"));
				c.add(new Change("Chunk fade-in", 0.0, "Off"));
				c.add(new Change("Cutout leaves", false, "Off"));
				c.add(new Change("Improved transparency", false, "Off"));
				c.add(new Change("Weather radius", 3, "3 blocks"));
			}
			case COMPETITIVE -> {
				c.add(new Change("Render distance", 8, "8 chunks"));
				c.add(new Change("Simulation distance", 6, "6 chunks"));
				c.add(new Change("Max framerate", 260, "Unlimited"));
				c.add(new Change("VSync", false, "Off"));
				c.add(new Change("Clouds", CloudStatus.OFF, "Off"));
				c.add(new Change("Particles", ParticleStatus.DECREASED, "Decreased"));
				c.add(new Change("Smooth lighting", true, "On"));
				c.add(new Change("Entity shadows", false, "Off"));
				c.add(new Change("Biome blend", 1, "3x3"));
				c.add(new Change("Mipmap levels", 2, "2"));
				c.add(new Change("Entity distance", 1.0, "100%"));
				c.add(new Change("Chunk updates", PrioritizeChunkUpdates.NONE, "Threaded"));
				c.add(new Change("Chunk fade-in", 0.0, "Off"));
				c.add(new Change("Improved transparency", false, "Off"));
			}
			case BALANCED -> {
				c.add(new Change("Render distance", 12, "12 chunks"));
				c.add(new Change("Simulation distance", 8, "8 chunks"));
				c.add(new Change("Max framerate", 260, "Unlimited"));
				c.add(new Change("VSync", false, "Off"));
				c.add(new Change("Clouds", CloudStatus.FAST, "Fast"));
				c.add(new Change("Particles", ParticleStatus.DECREASED, "Decreased"));
				c.add(new Change("Smooth lighting", true, "On"));
				c.add(new Change("Entity shadows", true, "On"));
				c.add(new Change("Biome blend", 2, "5x5"));
				c.add(new Change("Mipmap levels", 4, "4"));
				c.add(new Change("Entity distance", 1.0, "100%"));
				c.add(new Change("Chunk updates", PrioritizeChunkUpdates.PLAYER_AFFECTED, "Semi-blocking"));
			}
			case RECORDING -> {
				c.add(new Change("Render distance", 10, "10 chunks"));
				c.add(new Change("Simulation distance", 8, "8 chunks"));
				c.add(new Change("Max framerate", 120, "120"));
				c.add(new Change("VSync", false, "Off"));
				c.add(new Change("Clouds", CloudStatus.FAST, "Fast"));
				c.add(new Change("Particles", ParticleStatus.DECREASED, "Decreased"));
				c.add(new Change("Entity shadows", true, "On"));
				c.add(new Change("Chunk updates", PrioritizeChunkUpdates.NONE, "Threaded"));
			}
			case POTATO -> {
				c.add(new Change("Render distance", 4, "4 chunks"));
				c.add(new Change("Simulation distance", 5, "5 chunks"));
				c.add(new Change("Max framerate", 260, "Unlimited"));
				c.add(new Change("VSync", false, "Off"));
				c.add(new Change("Clouds", CloudStatus.OFF, "Off"));
				c.add(new Change("Particles", ParticleStatus.MINIMAL, "Minimal"));
				c.add(new Change("Smooth lighting", false, "Off"));
				c.add(new Change("Entity shadows", false, "Off"));
				c.add(new Change("Biome blend", 0, "Off"));
				c.add(new Change("Mipmap levels", 0, "Off"));
				c.add(new Change("Entity distance", 0.5, "50%"));
				c.add(new Change("Chunk updates", PrioritizeChunkUpdates.NONE, "Threaded"));
				c.add(new Change("Chunk fade-in", 0.0, "Off"));
				c.add(new Change("Cutout leaves", false, "Off"));
				c.add(new Change("Improved transparency", false, "Off"));
				c.add(new Change("Weather radius", 3, "3 blocks"));
			}
		}
		return c;
	}

	/** VANTA booster changes that come with a preset (shown in the preview too). */
	public static List<String> vantaChanges(Preset p) {
		return switch (p) {
			case MAX_FPS -> List.of("Particles module: 50% density, explosions hidden", "Entity Culling: items 32, orbs 24, frames 32, chests 48 blocks", "Background FPS: on (10 FPS)");
			case COMPETITIVE -> List.of("Particles module: explosions hidden, 75% density", "Entity Culling: items 40, orbs 24 blocks", "Screen Effects: low fire", "Totem Customizer: smaller totem pops");
			case BALANCED -> List.of("Particles module: 100% density, explosions shown", "Entity Culling: defaults");
			case RECORDING -> List.of("Particles module: 100% density");
			case POTATO -> List.of("Particles module: 25% density, explosions hidden", "Entity Culling: items 24, orbs 16, frames 24, mobs 48, chests 32 blocks", "Background FPS: on (5 FPS)");
		};
	}

	private static void applyVanta(Preset p) {
		ParticlesModule particles = (ParticlesModule) vanta.modules.ModuleRegistry.byId("particles");
		EntityCullingModule culling = (EntityCullingModule) vanta.modules.ModuleRegistry.byId("entity_culling");
		BackgroundFpsModule bg = (BackgroundFpsModule) vanta.modules.ModuleRegistry.byId("background_fps");
		vanta.modules.impl.ScreenEffectsModule fx = (vanta.modules.impl.ScreenEffectsModule) vanta.modules.ModuleRegistry.byId("screen_effects");
		switch (p) {
			case MAX_FPS -> {
				particles.setEnabled(true);
				particles.density.set(50);
				particles.explosions.set(true);
				culling.setEnabled(true);
				culling.items.set(32);
				culling.orbs.set(24);
				culling.decorations.set(32);
				culling.blockEntities.set(48);
				bg.setEnabled(true);
				bg.unfocused.set(10);
			}
			case COMPETITIVE -> {
				particles.setEnabled(true);
				particles.density.set(75);
				particles.explosions.set(true);
				culling.setEnabled(true);
				culling.items.set(40);
				culling.orbs.set(24);
				fx.setEnabled(true);
				fx.fire.set(vanta.modules.impl.ScreenEffectsModule.Fire.LOW);
				vanta.modules.impl.TotemModule totem = (vanta.modules.impl.TotemModule) vanta.modules.ModuleRegistry.byId("totem");
				if (totem != null) totem.size.set(50);
			}
			case BALANCED -> {
				particles.density.set(100);
				particles.explosions.set(false);
				culling.items.reset();
				culling.orbs.reset();
				culling.decorations.reset();
				culling.blockEntities.reset();
			}
			case RECORDING -> particles.density.set(100);
			case POTATO -> {
				particles.setEnabled(true);
				particles.density.set(25);
				particles.explosions.set(true);
				culling.setEnabled(true);
				culling.items.set(24);
				culling.orbs.set(16);
				culling.decorations.set(24);
				culling.mobs.set(48);
				culling.blockEntities.set(32);
				bg.setEnabled(true);
				bg.unfocused.set(5);
			}
		}
	}

	/** Current value of a preset option, formatted for the preview. */
	public static String currentDisplay(String name) {
		Options o = Minecraft.getInstance().options;
		Function<Options, OptionInstance<?>> getter = OPTIONS.get(name);
		if (o == null || getter == null) return "?";
		Object v = getter.apply(o).get();
		return format(name, v);
	}

	private static String format(String name, Object v) {
		if (v instanceof Boolean b) return b ? "On" : "Off";
		if (name.equals("Max framerate") && v instanceof Integer i) return i >= 260 ? "Unlimited" : String.valueOf(i);
		if (name.endsWith("distance") && v instanceof Integer i) return i + " chunks";
		if (name.equals("Entity distance") && v instanceof Double d) return Math.round(d * 100) + "%";
		if (name.equals("Chunk fade-in") && v instanceof Double d) return d <= 0 ? "Off" : String.format(java.util.Locale.ROOT, "%.2fs", d);
		if (name.equals("Weather radius") && v instanceof Integer i) return i + " blocks";
		if (v instanceof Enum<?> e) return e.name().charAt(0) + e.name().substring(1).toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
		return String.valueOf(v);
	}

	private static Path undoFile() {
		return ConfigManager.get().directory().resolve("preset-undo.json");
	}

	public static boolean canUndo() {
		return Files.exists(undoFile());
	}

	/** Applies a preset. The previous Minecraft settings and VANTA settings are saved for Undo. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void apply(Preset p) {
		Minecraft mc = Minecraft.getInstance();
		Options o = mc.options;
		JsonObject undo = new JsonObject();
		JsonObject vanilla = new JsonObject();
		for (Change change : changes(p)) {
			OptionInstance opt = OPTIONS.get(change.name()).apply(o);
			Object old = opt.get();
			vanilla.add(change.name(), encode(old));
			try {
				opt.set(change.value());
			} catch (RuntimeException e) {
				VantaClient.LOG.warn("[VANTA] Could not apply {} = {}", change.name(), change.value(), e);
			}
		}
		undo.add("vanilla", vanilla);
		undo.add("vanta", ConfigManager.get().snapshotAll());
		undo.addProperty("preset", p.name());
		try {
			Files.writeString(undoFile(), undo.toString(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			VantaClient.LOG.warn("[VANTA] Could not save preset undo data", e);
		}
		applyVanta(p);
		PerformanceState.ACTIVE_PRESET.set(p.label);
		o.save();
		ConfigManager.get().saveNow();
	}

	/** Restores the settings from before the last preset was applied. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static boolean undo() {
		try {
			JsonObject undo = JsonParser.parseString(Files.readString(undoFile(), StandardCharsets.UTF_8)).getAsJsonObject();
			Options o = Minecraft.getInstance().options;
			JsonObject vanilla = undo.getAsJsonObject("vanilla");
			for (Map.Entry<String, com.google.gson.JsonElement> e : vanilla.entrySet()) {
				Function<Options, OptionInstance<?>> getter = OPTIONS.get(e.getKey());
				if (getter == null) continue;
				OptionInstance opt = getter.apply(o);
				Object current = opt.get();
				Object value = decode(e.getValue(), current);
				if (value != null) opt.set(value);
			}
			if (undo.has("vanta")) ConfigManager.get().applySnapshot(undo.getAsJsonObject("vanta"), true);
			PerformanceState.ACTIVE_PRESET.set("Custom");
			o.save();
			Files.deleteIfExists(undoFile());
			ConfigManager.get().saveNow();
			return true;
		} catch (Exception e) {
			VantaClient.LOG.warn("[VANTA] Could not undo preset", e);
			return false;
		}
	}

	private static com.google.gson.JsonElement encode(Object v) {
		if (v instanceof Boolean b) return new com.google.gson.JsonPrimitive(b);
		if (v instanceof Number n) return new com.google.gson.JsonPrimitive(n);
		if (v instanceof Enum<?> e) return new com.google.gson.JsonPrimitive(e.name());
		return new com.google.gson.JsonPrimitive(String.valueOf(v));
	}

	/** Decodes a stored value using the current value's type as a guide. */
	private static Object decode(com.google.gson.JsonElement el, Object current) {
		if (!el.isJsonPrimitive()) return null;
		if (current instanceof Boolean) return el.getAsBoolean();
		if (current instanceof Integer) return el.getAsInt();
		if (current instanceof Double) return el.getAsDouble();
		if (current instanceof Enum<?> e) {
			for (Object c : e.getDeclaringClass().getEnumConstants()) {
				if (((Enum<?>) c).name().equals(el.getAsString())) return c;
			}
		}
		return null;
	}
}
