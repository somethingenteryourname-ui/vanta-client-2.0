package vanta.performance;

import java.util.List;

import vanta.compat.CompatDetector;

/**
 * The biggest FPS gains on Fabric come from these free, well-known optimization mods.
 * VANTA can't legally bundle all of them, so it checks which are installed and explains
 * what each one does.
 */
public final class RecommendedMods {
	public record Rec(String id, String name, String why) {
		public boolean installed() { return CompatDetector.isLoaded(id); }
	}

	public static final List<Rec> MODS = List.of(
			new Rec("sodium", "Sodium", "Rewrites chunk rendering. Usually the single biggest FPS boost (often 2-3x)."),
			new Rec("lithium", "Lithium", "Faster game logic: smoother singleplayer and less lag spikes."),
			new Rec("ferritecore", "FerriteCore", "Cuts memory use, which reduces stutter from garbage collection."),
			new Rec("entityculling", "EntityCulling", "Skips entities and chests hidden behind walls."),
			new Rec("immediatelyfast", "ImmediatelyFast", "Speeds up HUD, text and menu drawing, including VANTA's."),
			new Rec("moreculling", "More Culling", "Skips hidden block faces like leaves and item frames.")
	);

	private RecommendedMods() {
	}

	public static int installedCount() {
		int n = 0;
		for (Rec r : MODS) if (r.installed()) n++;
		return n;
	}
}
