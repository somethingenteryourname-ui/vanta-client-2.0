package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;

import vanta.combat.TotemTracker;

/** Totem Pop Counter: watches the totem / death animations the client already receives. Read-only. */
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {
	@Inject(method = "handleEntityEvent", at = @At("TAIL"))
	private void vanta$entityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		Entity entity = packet.getEntity(mc.level);
		if (entity != null) TotemTracker.onEntityEvent(entity, packet.getEventId());
	}
}
