package vanta.mixin.feature;

import java.util.concurrent.ThreadLocalRandom;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.TotemParticle;

import vanta.modules.impl.TotemModule;

/** Totem Customizer: recolors totem pop particles (accent or a custom color). */
@Mixin(TotemParticle.class)
abstract class TotemParticleMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void vanta$color(CallbackInfo ci) {
		float[] rgb = TotemModule.particleRgb(ThreadLocalRandom.current().nextFloat());
		if (rgb != null) ((SingleQuadParticle) (Object) this).setColor(rgb[0], rgb[1], rgb[2]);
	}
}
