package vanta.performance;

import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;

import vanta.config.BoolSetting;
import vanta.config.IntSetting;
import vanta.modules.Category;
import vanta.modules.Module;

/**
 * Particle control: an overall density cut plus per-type hiding. Particles that are
 * never created cost nothing to update or draw, which helps most in crystal/anchor
 * fights, farms and big explosions.
 */
public final class ParticlesModule extends Module {
	private static ParticlesModule instance;

	public final IntSetting density = setting(new IntSetting(key("density"), "Particle density",
			"Share of particles that are kept. Lower = more FPS in busy scenes.", 100, 0, 100, 5, "%"));
	public final BoolSetting explosions = setting(new BoolSetting(key("hide_explosions"), "Hide explosions",
			"Removes explosion clouds from crystals, anchors, TNT and creepers. Big help in crystal PvP.", true));
	public final BoolSetting crits = setting(new BoolSetting(key("hide_crits"), "Hide crit particles",
			"Removes critical hit stars.", false));
	public final BoolSetting enchantedHits = setting(new BoolSetting(key("hide_enchanted_hits"), "Hide enchanted hit particles",
			"Removes the blue sharpness sparkles.", false));
	public final BoolSetting damage = setting(new BoolSetting(key("hide_damage"), "Hide damage hearts",
			"Removes the dark hearts when you hit mobs.", false));
	public final BoolSetting sweep = setting(new BoolSetting(key("hide_sweep"), "Hide sweep attack",
			"Removes the sword sweep swoosh.", false));
	public final BoolSetting totem = setting(new BoolSetting(key("hide_totem"), "Hide totem particles",
			"Removes the green/yellow burst when a totem pops.", false));
	public final BoolSetting blockBreak = setting(new BoolSetting(key("hide_block_break"), "Hide block break particles",
			"Removes the fragments when blocks break.", false));
	public final BoolSetting weather = setting(new BoolSetting(key("hide_weather"), "Hide rain splashes",
			"Removes rain splash particles on the ground.", false));

	public ParticlesModule() {
		super("particles", "Particles", "Cut particle count and hide specific particles to keep busy fights smooth.",
				Category.PERFORMANCE, true);
		instance = this;
	}

	/** True if a particle of this type should not be created. */
	public static boolean block(ParticleOptions options) {
		ParticlesModule m = instance;
		if (m == null || !m.isEnabled()) return false;
		ParticleType<?> type = options.getType();
		if (m.explosions.on() && (type == ParticleTypes.EXPLOSION || type == ParticleTypes.EXPLOSION_EMITTER)) return true;
		if (m.crits.on() && type == ParticleTypes.CRIT) return true;
		if (m.enchantedHits.on() && type == ParticleTypes.ENCHANTED_HIT) return true;
		if (m.damage.on() && type == ParticleTypes.DAMAGE_INDICATOR) return true;
		if (m.sweep.on() && type == ParticleTypes.SWEEP_ATTACK) return true;
		if (m.totem.on() && type == ParticleTypes.TOTEM_OF_UNDYING) return true;
		if (m.weather.on() && (type == ParticleTypes.RAIN || type == ParticleTypes.SPLASH)) return true;
		return dropByDensity();
	}

	public static boolean blockBreakParticles() {
		ParticlesModule m = instance;
		if (m == null || !m.isEnabled()) return false;
		return m.blockBreak.on() || dropByDensity();
	}

	private static boolean dropByDensity() {
		int d = instance.density.value();
		if (d >= 100) return false;
		if (d <= 0) return true;
		return ThreadLocalRandom.current().nextInt(100) >= d;
	}

	@Override
	public String status() { return density.value() + "% density"; }
}
