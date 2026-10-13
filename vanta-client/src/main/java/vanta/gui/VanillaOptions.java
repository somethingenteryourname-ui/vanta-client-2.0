package vanta.gui;

import java.util.List;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.entity.player.ChatVisiblity;

import vanta.gui.items.MenuHost;
import vanta.gui.items.OptionRow;

/**
 * Menu rows bound directly to Minecraft's own options, so the Performance and Visuals
 * pages change the real settings (the same ones in Options > Video). Saved to
 * options.txt when the menu closes.
 */
public final class VanillaOptions {
	private static boolean changed;

	private VanillaOptions() {
	}

	private static Options o() { return Minecraft.getInstance().options; }

	public static void saveIfChanged() {
		if (changed && o() != null) {
			o().save();
			changed = false;
		}
	}

	/** Same rule Minecraft uses: 32-chunk distances need at least ~1 GB of memory. */
	public static int maxDistance() {
		return Runtime.getRuntime().maxMemory() >= 1_000_000_000L ? 32 : 16;
	}

	private static <T> void set(OptionInstance<T> option, T value) {
		option.set(value);
		changed = true;
	}

	private static OptionRow bool(MenuHost h, String name, String desc, OptionInstance<Boolean> opt) {
		return OptionRow.toggle(h, name, desc, opt::get, () -> set(opt, !opt.get()));
	}

	private static OptionRow ints(MenuHost h, String name, String desc, OptionInstance<Integer> opt, int min, int max, int step,
			java.util.function.IntFunction<String> label) {
		return OptionRow.slider(h, name, desc, min, max, step, opt::get, v -> set(opt, v), label);
	}

	/** A 0..1 double option shown as a percentage. */
	private static OptionRow percent(MenuHost h, String name, String desc, OptionInstance<Double> opt, int min, int max) {
		return OptionRow.slider(h, name, desc, min, max, 5, () -> (int) Math.round(opt.get() * 100), v -> set(opt, v / 100.0), v -> v + "%");
	}

	private static <E extends Enum<E>> OptionRow choice(MenuHost h, String name, String desc, OptionInstance<E> opt, E[] values, String[] labels) {
		return OptionRow.cycle(h, name, desc, () -> labels[opt.get().ordinal()],
				() -> set(opt, values[(opt.get().ordinal() + 1) % values.length]),
				() -> set(opt, values[(opt.get().ordinal() - 1 + values.length) % values.length]));
	}

	/** The video settings that affect FPS the most. */
	public static List<OptionRow> performance(MenuHost h) {
		Options o = o();
		return List.of(
				ints(h, "Render distance", "How far terrain is drawn. The biggest FPS setting.", o.renderDistance(), 2, maxDistance(), 1, v -> v + " ch"),
				ints(h, "Simulation distance", "How far the world ticks around you.", o.simulationDistance(), 5, maxDistance(), 1, v -> v + " ch"),
				ints(h, "Max framerate", "Frame rate cap. 260 = unlimited.", o.framerateLimit(), 10, 260, 10, v -> v >= 260 ? "Max" : String.valueOf(v)),
				bool(h, "VSync", "Syncs to your monitor. Smooth but adds input delay.", o.enableVsync()),
				choice(h, "Clouds", "Clouds cost FPS, especially Fancy.", o.cloudStatus(), CloudStatus.values(), new String[]{"Off", "Fast", "Fancy"}),
				choice(h, "Particles", "Vanilla particle amount.", o.particles(), ParticleStatus.values(), new String[]{"All", "Decreased", "Minimal"}),
				bool(h, "Smooth lighting", "Ambient occlusion. Off is slightly faster.", o.ambientOcclusion()),
				bool(h, "Entity shadows", "Round shadows under mobs and players.", o.entityShadows()),
				ints(h, "Biome blend", "Color blending between biomes. 0 is fastest.", o.biomeBlendRadius(), 0, 7, 1, v -> v == 0 ? "Off" : (v * 2 + 1) + "x" + (v * 2 + 1)),
				ints(h, "Mipmap levels", "Texture smoothing at a distance.", o.mipmapLevels(), 0, 4, 1, v -> v == 0 ? "Off" : String.valueOf(v)),
				OptionRow.slider(h, "Entity distance", "How far entities are drawn, relative to render distance.", 50, 500, 25,
						() -> (int) Math.round(o.entityDistanceScaling().get() * 100), v -> set(o.entityDistanceScaling(), v / 100.0), v -> v + "%"),
				choice(h, "Chunk updates", "Threaded is smoothest; blocking modes reduce visual lag when building.", o.prioritizeChunkUpdates(),
						PrioritizeChunkUpdates.values(), new String[]{"Threaded", "Semi-blocking", "Fully blocking"}),
				bool(h, "Improved transparency", "Better-looking glass and water. Costs FPS.", o.improvedTransparency()),
				choice(h, "Reduce FPS when idle", "Minecraft's own limit when minimized or AFK.", o.inactivityFpsLimit(),
						InactivityFpsLimit.values(), new String[]{"When minimized", "When AFK"})
		);
	}

	/** Camera and comfort options that pair with VANTA's visual modules. */
	public static List<OptionRow> visuals(MenuHost h) {
		Options o = o();
		return List.of(
				bool(h, "View bobbing", "Camera sway while walking.", o.bobView()),
				percent(h, "Hurt camera shake", "Screen tilt when you take damage. 0% removes it.", o.damageTiltStrength(), 0, 100),
				percent(h, "FOV effects", "FOV change from sprinting, speed and slowness.", o.fovEffectScale(), 0, 100),
				percent(h, "Distortion effects", "Nausea and portal wobble.", o.screenEffectScale(), 0, 100),
				percent(h, "Darkness pulsing", "Pulsing from the Darkness effect.", o.darknessEffectScale(), 0, 100),
				percent(h, "Brightness", "Minecraft's brightness (gamma). For full brightness use Fullbright.", o.gamma(), 0, 100),
				ints(h, "Field of view", "Normal FOV.", o.fov(), 30, 110, 1, v -> String.valueOf(v)),
				bool(h, "Vignette", "Dark screen edges.", o.vignette()),
				bool(h, "Hide lightning flashes", "No sky flash from lightning.", o.hideLightningFlash())
		);
	}

	/** Minecraft's own chat options, shown on the Chat page. */
	public static List<OptionRow> chat(MenuHost h) {
		Options o = o();
		return List.of(
				choice(h, "Chat shown", "Show all chat, only command feedback, or nothing.", o.chatVisibility(), ChatVisiblity.values(),
						new String[]{"Shown", "Commands only", "Hidden"}),
				OptionRow.slider(h, "Text opacity", "How solid chat text is.", 10, 100, 5,
						() -> (int) Math.round(o.chatOpacity().get() * 90 + 10), v -> set(o.chatOpacity(), (v - 10) / 90.0), v -> v + "%"),
				percent(h, "Vanilla background opacity", "Minecraft's black bars behind each line (used when VANTA chat style is off).",
						o.textBackgroundOpacity(), 0, 100),
				percent(h, "Chat scale", "Size of chat text.", o.chatScale(), 0, 100),
				OptionRow.slider(h, "Chat width", "How wide chat is.", 0, 100, 5, () -> (int) Math.round(o.chatWidth().get() * 100),
						v -> set(o.chatWidth(), v / 100.0), v -> ChatComponent.getWidth(v / 100.0) + "px"),
				OptionRow.slider(h, "Height while typing", "How tall chat is when open.", 0, 100, 5,
						() -> (int) Math.round(o.chatHeightFocused().get() * 100), v -> set(o.chatHeightFocused(), v / 100.0),
						v -> ChatComponent.getHeight(v / 100.0) + "px"),
				OptionRow.slider(h, "Height when closed", "How tall chat is during play.", 0, 100, 5,
						() -> (int) Math.round(o.chatHeightUnfocused().get() * 100), v -> set(o.chatHeightUnfocused(), v / 100.0),
						v -> ChatComponent.getHeight(v / 100.0) + "px"),
				percent(h, "Line spacing", "Extra space between chat lines.", o.chatLineSpacing(), 0, 100),
				OptionRow.slider(h, "Message delay", "Wait between incoming messages (helps read fast chat).", 0, 60, 1,
						() -> (int) Math.round(o.chatDelay().get() * 10), v -> set(o.chatDelay(), v / 10.0),
						v -> v == 0 ? "None" : String.format(java.util.Locale.ROOT, "%.1fs", v / 10.0)),
				bool(h, "Colors", "Show colors in chat.", o.chatColors()),
				bool(h, "Web links", "Make links in chat clickable.", o.chatLinks()),
				bool(h, "Ask before opening links", "Confirm before a link opens in your browser.", o.chatLinksPrompt()),
				bool(h, "Only show secure chat", "Hide messages that aren't signed by the sender.", o.onlyShowSecureChat()),
				bool(h, "Hide matched names", "Let servers hide sender names they format themselves.", o.hideMatchedNames())
		);
	}
}
