package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.particles.ParticleOptions;

import vanta.performance.ParticlesModule;

/** Particles module: skips creating particles the player chose to hide or thin out. */
@Mixin(ParticleEngine.class)
abstract class ParticleEngineMixin {
	@Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
	private void vanta$filter(ParticleOptions options, double x, double y, double z, double xa, double ya, double za,
			CallbackInfoReturnable<Particle> cir) {
		if (ParticlesModule.block(options)) cir.setReturnValue(null);
	}

	@Inject(method = "add", at = @At("HEAD"), cancellable = true)
	private void vanta$filterBlockBreak(Particle particle, CallbackInfo ci) {
		if (particle instanceof TerrainParticle && ParticlesModule.blockBreakParticles()) ci.cancel();
	}
}
