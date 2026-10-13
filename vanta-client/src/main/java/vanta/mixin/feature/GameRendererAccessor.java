package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ScreenEffectRenderer;

/** Totem Customizer: reaches the screen effect renderer that runs the totem pop animation. */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
	@Accessor("screenEffectRenderer")
	ScreenEffectRenderer vanta$screenEffects();
}
