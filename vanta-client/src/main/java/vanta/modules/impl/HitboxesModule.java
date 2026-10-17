package vanta.modules.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;

import vanta.modules.Category;
import vanta.modules.Module;

/** Shows entity hitboxes (the same view as F3+B, kept on by the module). */
public final class HitboxesModule extends Module {
	public HitboxesModule() {
		super("hitboxes", "Hitboxes", "Shows the outline of every entity's hitbox, like F3+B.", Category.VISUALS, false);
	}

	@Override
	protected void onEnable() { apply(true); }

	@Override
	protected void onDisable() { apply(false); }

	@Override
	public void syncExternal() {
		if (isEnabled()) apply(true);
	}

	private static void apply(boolean on) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.debugEntries == null) return;
		boolean now = mc.debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES);
		if (now != on) {
			mc.debugEntries.setStatus(DebugScreenEntries.ENTITY_HITBOXES, on ? DebugScreenEntryStatus.ALWAYS_ON : DebugScreenEntryStatus.NEVER);
		}
	}
}
