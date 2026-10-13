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

import vanta.modules.impl.ClientBadgeModule;
import vanta.modules.impl.NametagsModule;
import vanta.social.VantaUsers;

/**
 * Client Badge / Nametags: your own nametag in F5 and freelook, the VANTA logo in front of
 * VANTA players' names, and health / totem pops after names. Other players' nametags still
 * appear only when vanilla would show them.
 */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
			at = @At("TAIL"))
	private void vanta$nametag(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		if (!(entity instanceof Player player)) return;
		boolean badge = ClientBadgeModule.active();
		boolean tags = NametagsModule.active();
		boolean tiers = vanta.tiers.TierTaggerModule.inNametags();
		boolean friend = vanta.friends.FriendsModule.markNametag(player.getName().getString());
		if (!badge && !tags && !tiers && !friend) return;
		Minecraft mc = Minecraft.getInstance();
		if (state.nameTag == null && entity == mc.player && (ClientBadgeModule.showOwnNametag() || NametagsModule.showOwn())
				&& Minecraft.renderNames() && !entity.isInvisible() && !mc.options.hideGui) {
			state.nameTag = entity.getDisplayName();
			state.nameTagAttachment = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(partialTicks));
		}
		if (state.nameTag == null) return;
		if (tags) state.nameTag = NametagsModule.decorate(player, state.nameTag);
		if (tiers) state.nameTag = vanta.tiers.TierTaggerModule.decorate(player.getUUID(), player.getName().getString(), state.nameTag);
		if (friend) state.nameTag = vanta.friends.FriendsModule.decorate(state.nameTag);
		if (ClientBadgeModule.badgeFor(player.getUUID())) state.nameTag = VantaUsers.withBadge(state.nameTag);
	}
}
