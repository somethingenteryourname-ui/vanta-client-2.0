package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.world.item.ItemStack;

/** Totem Customizer: reads the totem pop animation state for the Flat style. */
@Mixin(ScreenEffectRenderer.class)
public interface ScreenEffectRendererAccessor {
	@Accessor("itemActivationItem")
	ItemStack vanta$activationItem();

	@Accessor("itemActivationTicks")
	int vanta$activationTicks();

	@Accessor("itemActivationOffX")
	float vanta$offX();

	@Accessor("itemActivationOffY")
	float vanta$offY();
}
