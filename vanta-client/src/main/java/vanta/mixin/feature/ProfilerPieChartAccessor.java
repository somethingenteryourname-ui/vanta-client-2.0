package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.components.debugchart.ProfilerPieChart;
import net.minecraft.util.profiling.ProfileResults;

/** Clean F3: reads the frame profiler results that vanilla's pie chart would show. */
@Mixin(ProfilerPieChart.class)
public interface ProfilerPieChartAccessor {
	@Accessor("profilerPieChartResults")
	ProfileResults vanta$results();
}
