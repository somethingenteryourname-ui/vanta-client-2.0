package vanta.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import vanta.menu.TitleScreenHook;
import vanta.metrics.FrameMetrics;

/**
 * VANTA's only mixin. Two tiny hooks into Minecraft (method names verified against
 * Fabric API's own 1.21.11 mixins):
 * <ul>
 * <li>{@code setScreen}: swap the vanilla TitleScreen for the VANTA home screen.</li>
 * <li>{@code runTick}: record one timestamp per rendered frame for real FPS / frame-time stats.</li>
 * </ul>
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen vanta$replaceTitleScreen(Screen screen) {
		return TitleScreenHook.maybeReplace(screen);
	}

	@Inject(method = "runTick", at = @At("HEAD"))
	private void vanta$onFrame(CallbackInfo ci) {
		FrameMetrics.onFrame();
	}
}
