package vanta.modules.impl;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import vanta.config.BoolSetting;
import vanta.config.IntSetting;
import vanta.modules.Category;
import vanta.modules.Module;

/**
 * Held item position and size in first person, per hand, plus a lower shield.
 * Only changes how items are drawn on your screen.
 */
public final class ViewModelModule extends Module {
	private static ViewModelModule instance;

	public final IntSetting mainX = setting(new IntSetting(key("main_x"), "Main hand left/right", "Move the main hand item sideways.", 0, -100, 100, 5, ""));
	public final IntSetting mainY = setting(new IntSetting(key("main_y"), "Main hand up/down", "Move the main hand item up or down.", 0, -100, 100, 5, ""));
	public final IntSetting mainZ = setting(new IntSetting(key("main_z"), "Main hand near/far", "Move the main hand item closer or further away.", 0, -100, 100, 5, ""));
	public final IntSetting mainSize = setting(new IntSetting(key("main_size"), "Main hand size", "Size of the item in your main hand.", 100, 30, 150, 5, "%"));
	public final IntSetting offX = setting(new IntSetting(key("off_x"), "Offhand left/right", "Move the offhand item sideways.", 0, -100, 100, 5, ""));
	public final IntSetting offY = setting(new IntSetting(key("off_y"), "Offhand up/down", "Move the offhand item up or down.", 0, -100, 100, 5, ""));
	public final IntSetting offZ = setting(new IntSetting(key("off_z"), "Offhand near/far", "Move the offhand item closer or further away.", 0, -100, 100, 5, ""));
	public final IntSetting offSize = setting(new IntSetting(key("off_size"), "Offhand size", "Size of the item in your offhand.", 100, 30, 150, 5, "%"));
	public final IntSetting shieldLower = setting(new IntSetting(key("shield_lower"), "Lower shield",
			"Push a held shield down so it covers less of the screen.", 0, 0, 100, 5, "%"));
	public final BoolSetting mirrorOffhand = setting(new BoolSetting(key("mirror_offhand"), "Offhand mirrors main hand",
			"Use the main hand values for the offhand too (mirrored).", false));

	public ViewModelModule() {
		super("view_model", "Held Item View", "Move and resize the items in your hands in first person, per hand, "
				+ "and lower your shield. Visual only.", Category.VISUALS, false);
		instance = this;
	}

	/** Applies the offsets for one first-person hand to the pose. Called right before the item is drawn. */
	public static void apply(PoseStack pose, ItemStack stack, ItemDisplayContext context) {
		ViewModelModule m = instance;
		boolean totem = stack != null && stack.is(Items.TOTEM_OF_UNDYING);
		float totemScale = totem ? TotemModule.handScale() : 1f;
		if (m == null || !m.isEnabled()) {
			if (totemScale != 1f) pose.scale(totemScale, totemScale, totemScale);
			return;
		}
		boolean left = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
		boolean rightHanded = true;
		try {
			rightHanded = net.minecraft.client.Minecraft.getInstance().options.mainHand().get() == net.minecraft.world.entity.HumanoidArm.RIGHT;
		} catch (RuntimeException ignored) {
			// options not ready
		}
		boolean off = left == rightHanded;
		boolean useMain = !off || m.mirrorOffhand.on();
		float x = (useMain ? m.mainX.value() : m.offX.value()) / 100f * 0.6f;
		float y = (useMain ? m.mainY.value() : m.offY.value()) / 100f * 0.6f;
		float z = (useMain ? m.mainZ.value() : m.offZ.value()) / 100f * 0.6f;
		float s = (useMain ? m.mainSize.value() : m.offSize.value()) / 100f * totemScale;
		if (stack != null && stack.is(Items.SHIELD)) y -= m.shieldLower.value() / 100f * 0.35f;
		if (left) x = -x;
		pose.translate(x, y, -z);
		if (s != 1f) pose.scale(s, s, s);
	}
}
