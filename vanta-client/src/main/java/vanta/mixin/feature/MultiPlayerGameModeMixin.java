package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

import vanta.optimize.OptimizationsModule;

/** Optimizations: client-side crystals and anchors. The packets sent are exactly vanilla's. */
@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeMixin {
	@Inject(method = "attack", at = @At("TAIL"))
	private void vanta$clientCrystal(Player player, Entity entity, CallbackInfo ci) {
		OptimizationsModule.afterAttack(Minecraft.getInstance(), player, entity);
	}

	@Inject(method = "useItemOn", at = @At("RETURN"))
	private void vanta$clientAnchor(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
		// checked against the block as it was before the click (the click itself doesn't change it on our side)
		if (vanta$detonating && cir.getReturnValue() != null && cir.getReturnValue().consumesAction()) {
			OptimizationsModule.detonated(Minecraft.getInstance(), hit.getBlockPos());
		}
		vanta$detonating = false;
	}

	@Inject(method = "useItemOn", at = @At("HEAD"))
	private void vanta$checkAnchor(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
		vanta$detonating = OptimizationsModule.willDetonate(Minecraft.getInstance(), player, hand, hit.getBlockPos());
	}

	private boolean vanta$detonating;
}
