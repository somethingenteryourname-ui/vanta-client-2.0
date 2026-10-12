package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.components.DebugScreenOverlay;

/** Clean F3: lets the pie chart switch Minecraft's frame profiler on while F3 is open. */
@Mixin(DebugScreenOverlay.class)
public interface DebugScreenOverlayAccessor {
	@Accessor("renderProfilerChart")
	void vanta$setRenderProfilerChart(boolean value);
}
