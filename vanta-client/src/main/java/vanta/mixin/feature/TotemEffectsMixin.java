package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

import vanta.modules.impl.TotemModule;

/** Totem Customizer: amount of pop particles and the pop sound volume (local only). */
@Mixin(ClientPacketListener.class)
abstract class TotemEffectsMixin {
	@WrapOperation(method = "handleEntityEvent", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/particle/ParticleEngine;createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V"))
	private void vanta$popParticles(ParticleEngine engine, Entity entity, ParticleOptions particle, int lifeTime, Operation<Void> original) {
		int t = TotemModule.particleTicks(lifeTime);
		if (t > 0) original.call(engine, entity, particle, t);
	}

	@WrapOperation(method = "handleEntityEvent", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/ClientLevel;playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"))
	private void vanta$popSound(ClientLevel level, double x, double y, double z, SoundEvent sound, SoundSource source, float volume, float pitch,
			boolean delay, Operation<Void> original) {
		float v = sound == SoundEvents.TOTEM_USE ? volume * TotemModule.volume() : volume;
		if (v > 0f) original.call(level, x, y, z, sound, source, v, pitch, delay);
	}
}
