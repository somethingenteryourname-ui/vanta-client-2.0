package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.EnvironmentAttributes;

import vanta.modules.impl.TimeSkyModule;

/** Time & Sky: custom sky (and matching fog) color for the camera. Keeps the original alpha. */
@Mixin(EnvironmentAttributeProbe.class)
abstract class EnvironmentAttributeProbeMixin {
	@Inject(method = "getValue", at = @At("RETURN"), cancellable = true)
	private void vanta$sky(EnvironmentAttribute<?> attribute, float partialTicks, CallbackInfoReturnable<Object> cir) {
		int rgb;
		if (attribute == EnvironmentAttributes.SKY_COLOR) rgb = TimeSkyModule.skyColor();
		else if (attribute == EnvironmentAttributes.FOG_COLOR) rgb = TimeSkyModule.fogColor();
		else return;
		if (rgb < 0 || !(cir.getReturnValue() instanceof Integer original)) return;
		cir.setReturnValue((original & 0xFF000000) | rgb);
	}
}
