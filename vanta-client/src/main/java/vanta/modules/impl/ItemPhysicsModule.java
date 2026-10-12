package vanta.modules.impl;

import vanta.config.BoolSetting;
import vanta.modules.Category;
import vanta.modules.Module;

/** Item Physics: dropped items lie flat on the ground instead of floating and spinning. Visual only. */
public final class ItemPhysicsModule extends Module {
	private static ItemPhysicsModule instance;

	public final BoolSetting blocksUpright = setting(new BoolSetting(key("blocks_upright"), "Blocks stay upright",
			"Block items sit on the ground like small blocks instead of lying on their side.", true));

	public ItemPhysicsModule() {
		super("item_physics", "Item Physics", "Dropped items lie flat on the ground with a fixed rotation instead of bobbing and spinning.",
				Category.VISUALS, false);
		instance = this;
	}

	public static boolean active() { return instance != null && instance.isEnabled(); }
	public static boolean blocksUpright() { return active() && instance.blocksUpright.on(); }
}
