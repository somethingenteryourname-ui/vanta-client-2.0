package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;

import vanta.modules.impl.NickHiderModule;

/** Nick Hider: your own skin becomes a default skin (on your screen only). */
@Mixin(AbstractClientPlayer.class)
abstract class AbstractClientPlayerMixin {
	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void vanta$hideSkin(CallbackInfoReturnable<PlayerSkin> cir) {
		if (!NickHiderModule.hidesSkin()) return;
		AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
		if (self == Minecraft.getInstance().player) cir.setReturnValue(DefaultPlayerSkin.get(self.getUUID()));
	}
}
