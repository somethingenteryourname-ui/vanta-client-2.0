package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;

import vanta.modules.impl.NickHiderModule;

/** Nick Hider: your tab-list head uses the default skin too. */
@Mixin(PlayerInfo.class)
abstract class PlayerInfoMixin {
	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void vanta$hideSkin(CallbackInfoReturnable<PlayerSkin> cir) {
		if (!NickHiderModule.hidesSkin()) return;
		PlayerInfo self = (PlayerInfo) (Object) this;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && self.getProfile().id().equals(mc.player.getUUID())) {
			cir.setReturnValue(DefaultPlayerSkin.get(self.getProfile().id()));
		}
	}
}
