package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemCooldowns;

import vanta.hud.widgets.ItemCooldownWidget;

/** Item Cooldowns widget: learns each cooldown's length so it can show seconds left. Read-only. */
@Mixin(ItemCooldowns.class)
abstract class ItemCooldownsMixin {
	@Inject(method = "addCooldown(Lnet/minecraft/resources/Identifier;I)V", at = @At("TAIL"))
	private void vanta$started(Identifier group, int time, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && mc.player.getCooldowns() == (Object) this) ItemCooldownWidget.started(group, time);
	}

	@Inject(method = "removeCooldown", at = @At("TAIL"))
	private void vanta$removed(Identifier group, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && mc.player.getCooldowns() == (Object) this) ItemCooldownWidget.ended(group);
	}
}
