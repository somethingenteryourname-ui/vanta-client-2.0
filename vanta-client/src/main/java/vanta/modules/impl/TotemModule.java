package vanta.modules.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import vanta.branding.Theme;
import vanta.config.BoolSetting;
import vanta.config.ChoiceSetting;
import vanta.config.ColorSetting;
import vanta.config.IntSetting;
import vanta.config.Labeled;
import vanta.mixin.feature.GameRendererAccessor;
import vanta.mixin.feature.ScreenEffectRendererAccessor;
import vanta.modules.Category;
import vanta.modules.Module;
import vanta.ui.Colors;

/**
 * Totem Customizer: the totem pop animation (size, see-through flat style, length,
 * position, spin), the pop particles (amount, color), the pop sound volume and the size
 * of a totem held in your hand. Everything is visual or local sound only.
 */
public final class TotemModule extends Module {
	public enum Style implements Labeled {
		VANILLA("3D (vanilla)"), FLAT("Flat (see-through)");
		private final String label;
		Style(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	public enum Position implements Labeled {
		RANDOM("Random (vanilla)"), CENTER("Center");
		private final String label;
		Position(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	public enum ParticleColor implements Labeled {
		VANILLA("Vanilla green/yellow"), ACCENT("Theme accent"), CUSTOM("Custom color");
		private final String label;
		ParticleColor(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	private static final Identifier TOTEM_TEXTURE = Identifier.withDefaultNamespace("textures/item/totem_of_undying.png");
	private static TotemModule instance;

	// pop animation
	public final ChoiceSetting<Style> style = setting(new ChoiceSetting<>(key("style"), "Pop animation style",
			"3D is Minecraft's spinning totem. Flat is a 2D totem that can be see-through.", Style.VANILLA));
	public final IntSetting size = setting(new IntSetting(key("size"), "Pop animation size",
			"Size of the totem that flies at your screen when you pop. 0 hides it.", 60, 0, 200, 5, "%"));
	public final IntSetting opacity = setting(new IntSetting(key("opacity"), "Pop animation opacity",
			"How see-through the pop animation is (Flat style).", 100, 10, 100, 5, "%"));
	public final IntSetting length = setting(new IntSetting(key("length"), "Pop animation length",
			"How long the animation lasts compared to vanilla (2 seconds).", 100, 25, 200, 5, "%"));
	public final ChoiceSetting<Position> position = setting(new ChoiceSetting<>(key("position"), "Pop animation position",
			"Vanilla drifts to a random side. Center keeps it in the middle.", Position.RANDOM));
	public final BoolSetting spin = setting(new BoolSetting(key("spin"), "Spin",
			"Let the totem spin while it pops.", true));
	// particles and sound
	public final IntSetting particles = setting(new IntSetting(key("particles"), "Pop particles",
			"Amount of totem particles when anyone pops. 0 hides them.", 100, 0, 200, 10, "%"));
	public final ChoiceSetting<ParticleColor> particleColor = setting(new ChoiceSetting<>(key("particle_color"), "Particle color",
			"Color of totem pop particles.", ParticleColor.VANILLA));
	public final ColorSetting customColor = setting(new ColorSetting(key("custom_color"), "Custom particle color",
			"Used when Particle color is Custom.", 0xFFB86BFF, false));
	public final IntSetting volume = setting(new IntSetting(key("volume"), "Pop sound volume",
			"Volume of the totem pop sound.", 100, 0, 100, 5, "%"));
	// held
	public final IntSetting handSize = setting(new IntSetting(key("hand_size"), "Totem in hand size",
			"Size of a totem you're holding, in first person. Smaller shows more of the screen.", 100, 30, 150, 5, "%"));

	public TotemModule() {
		super("totem", "Totem Customizer", "Change the totem pop animation (size, see-through flat style, length, position, spin), "
				+ "pop particles and color, pop sound volume and the size of a held totem.", Category.VISUALS, true);
		instance = this;
	}

	public static TotemModule active() { return instance != null && instance.isEnabled() ? instance : null; }

	/** Scale for the 3D animation; 0 hides it. */
	public static float scale3d() {
		TotemModule m = active();
		if (m == null) return 1f;
		return m.style.get() == Style.FLAT ? 0f : m.size.value() / 100f;
	}

	public static boolean flat() {
		TotemModule m = active();
		return m != null && m.style.get() == Style.FLAT;
	}

	/** Length of the animation in ticks (vanilla 40). */
	public static int ticks() {
		TotemModule m = active();
		return m == null ? 40 : Math.max(10, Math.round(40 * m.length.value() / 100f));
	}

	public static boolean centered() {
		TotemModule m = active();
		return m != null && m.position.get() == Position.CENTER;
	}

	public static float spinFactor() {
		TotemModule m = active();
		return m == null || m.spin.on() ? 1f : 0f;
	}

	/** Particle emitter lifetime (vanilla 30 ticks, 16 particles each tick). */
	public static int particleTicks(int vanilla) {
		TotemModule m = active();
		return m == null ? vanilla : Math.round(vanilla * m.particles.value() / 100f);
	}

	public static float volume() {
		TotemModule m = active();
		return m == null ? 1f : m.volume.value() / 100f;
	}

	public static float handScale() {
		TotemModule m = active();
		return m == null ? 1f : m.handSize.value() / 100f;
	}

	/** RGB (0..1) for a new totem particle, or null to keep vanilla's colors. */
	public static float[] particleRgb(float jitter) {
		TotemModule m = active();
		if (m == null || m.particleColor.get() == ParticleColor.VANILLA) return null;
		int c = m.particleColor.get() == ParticleColor.ACCENT ? Theme.accent() : m.customColor.argb();
		float k = 0.8f + 0.4f * jitter;
		return new float[] {
				Math.min(1f, ((c >> 16) & 0xFF) / 255f * k),
				Math.min(1f, ((c >> 8) & 0xFF) / 255f * k),
				Math.min(1f, (c & 0xFF) / 255f * k)};
	}

	/** Same easing vanilla uses for the pop (0..1 progress in, "how close to the camera" out). */
	public static float popCurve(float p) {
		float ts = p * p, tc = p * ts;
		return 10.25f * tc * ts - 24.95f * ts * ts + 25.5f * tc - 13.8f * ts + 4.0f * p;
	}

	/** Draws the Flat style pop animation on the HUD. */
	public static void renderFlat(GuiGraphics g, DeltaTracker delta) {
		TotemModule m = active();
		if (m == null || m.style.get() != Style.FLAT || m.size.value() <= 0) return;
		Minecraft mc = Minecraft.getInstance();
		if (!((Object) mc.gameRenderer instanceof GameRendererAccessor gra)) return;
		Object ser = gra.vanta$screenEffects();
		if (!(ser instanceof ScreenEffectRendererAccessor acc)) return;
		ItemStack item = acc.vanta$activationItem();
		int left = acc.vanta$activationTicks();
		if (item == null || left <= 0) return;
		boolean totem = item.is(Items.TOTEM_OF_UNDYING);
		int total = ticks();
		float p = Math.min(1f, Math.max(0f, (total - left + delta.getGameTimeDeltaPartialTick(false)) / total));
		float curve = popCurve(p);
		float near = (float) Math.sin(curve * Math.PI); // 0 far away .. 1 right in front of you
		if (near <= 0.01f) return;
		int w = g.guiWidth(), h = g.guiHeight();
		float px = Math.min(w, h) * 0.55f * (m.size.value() / 100f) * near;
		float cx = w / 2f, cy = h / 2f;
		if (m.position.get() == Position.RANDOM) {
			float sway = (float) Math.abs(Math.sin(curve * Math.PI * 2));
			cx += acc.vanta$offX() * 0.3f * w * 0.5f * sway;
			cy += acc.vanta$offY() * 0.3f * h * 0.5f * sway;
		}
		float flip = m.spin.on() ? (float) Math.cos(Math.toRadians(900f * Math.abs(Math.sin(curve * Math.PI)))) : 1f;
		float fade = p > 0.8f ? (1f - p) / 0.2f : 1f;
		float alpha = m.opacity.value() / 100f * fade;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(px / 16f * Math.max(0.05f, Math.abs(flip)), px / 16f);
		if (totem) g.blit(RenderPipelines.GUI_TEXTURED, TOTEM_TEXTURE, -8, -8, 0f, 0f, 16, 16, 16, 16, Colors.withAlpha(0xFFFFFFFF, alpha));
		else g.renderItem(item, -8, -8); // other death-protection items: no see-through, but still shown
		g.pose().popMatrix();
	}
}
