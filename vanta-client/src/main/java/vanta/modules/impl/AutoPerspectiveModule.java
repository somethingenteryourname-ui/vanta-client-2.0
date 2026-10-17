package vanta.modules.impl;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import vanta.config.BoolSetting;
import vanta.modules.Category;
import vanta.modules.Module;

/** Switches to third person while flying with an elytra (and optionally riding), then back. */
public final class AutoPerspectiveModule extends Module {
	public final BoolSetting elytra = setting(new BoolSetting(key("elytra"), "While elytra flying",
			"Third person while gliding.", true));
	public final BoolSetting riding = setting(new BoolSetting(key("riding"), "While riding",
			"Third person on horses, boats and minecarts.", false));

	private CameraType previous;

	public AutoPerspectiveModule() {
		super("auto_perspective", "Auto Perspective", "Third person while elytra flying or riding, back to first person after.",
				Category.QOL, false);
	}

	@Override
	public void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null) {
			previous = null;
			return;
		}
		boolean want = (elytra.on() && p.isFallFlying()) || (riding.on() && p.isPassenger());
		if (want && previous == null) {
			previous = mc.options.getCameraType();
			if (previous == CameraType.FIRST_PERSON) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		} else if (!want && previous != null) {
			if (mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(previous);
			previous = null;
		}
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (previous != null && mc.options != null) mc.options.setCameraType(previous);
		previous = null;
	}
}
