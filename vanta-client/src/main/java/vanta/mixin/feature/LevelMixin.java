package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;

import vanta.modules.impl.TimeSkyModule;
import vanta.modules.impl.WeatherModule;

/** Time & Sky and Weather: change what the client world reports, never the server. */
@Mixin(Level.class)
abstract class LevelMixin {
	@Inject(method = "getDayTime", at = @At("RETURN"), cancellable = true)
	private void vanta$time(CallbackInfoReturnable<Long> cir) {
		if (!((Object) this instanceof ClientLevel)) return;
		long t = TimeSkyModule.dayTime(cir.getReturnValueJ());
		if (t >= 0) cir.setReturnValue(t);
	}

	@Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
	private void vanta$rain(float partialTicks, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof ClientLevel && WeatherModule.hideRain()) cir.setReturnValue(0f);
	}

	@Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
	private void vanta$thunder(float partialTicks, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof ClientLevel && WeatherModule.hideThunder()) cir.setReturnValue(0f);
	}
}
