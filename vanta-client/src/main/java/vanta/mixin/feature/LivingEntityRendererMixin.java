package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import vanta.modules.impl.NametagsModule;

/** Nametags: your own nametag in third person, and health / totem pops after player names. */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
			at = @At("TAIL"))
	private void vanta$nametag(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		if (!NametagsModule.active() || !(entity instanceof Player player)) return;
		Minecraft mc = Minecraft.getInstance();
		if (state.nameTag == null && entity == mc.player && NametagsModule.showOwn() && Minecraft.renderNames()
				&& !entity.isInvisible() && !mc.options.hideGui) {
			state.nameTag = entity.getDisplayName();
			state.nameTagAttachment = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(partialTicks));
		}
		if (state.nameTag != null) state.nameTag = NametagsModule.decorate(player, state.nameTag);
	}
}
