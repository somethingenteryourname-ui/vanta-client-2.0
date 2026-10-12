package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;

import vanta.optimize.OptimizationsModule;

/** Optimizations: still end crystals (no bob, no spin). */
@Mixin(EndCrystalRenderer.class)
abstract class EndCrystalRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;F)V",
			at = @At("TAIL"))
	private void vanta$still(EndCrystal entity, EndCrystalRenderState state, float partialTicks, CallbackInfo ci) {
		if (OptimizationsModule.staticCrystals()) state.ageInTicks = 0f;
	}
}
