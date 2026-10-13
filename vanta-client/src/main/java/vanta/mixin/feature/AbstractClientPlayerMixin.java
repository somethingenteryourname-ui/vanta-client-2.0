package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;

import vanta.cosmetics.CosmeticsModule;
import vanta.modules.impl.NickHiderModule;

/** Nick Hider (your own skin becomes a default skin, on your screen only) and VANTA capes. */
@Mixin(AbstractClientPlayer.class)
abstract class AbstractClientPlayerMixin {
	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void vanta$skin(CallbackInfoReturnable<PlayerSkin> cir) {
		AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
		boolean isSelf = self == Minecraft.getInstance().player;
		PlayerSkin skin = cir.getReturnValue();
		if (isSelf && NickHiderModule.hidesSkin()) skin = DefaultPlayerSkin.get(self.getUUID());
		skin = CosmeticsModule.withCape(skin, self.getUUID(), isSelf);
		if (skin != cir.getReturnValue()) cir.setReturnValue(skin);
	}
}
