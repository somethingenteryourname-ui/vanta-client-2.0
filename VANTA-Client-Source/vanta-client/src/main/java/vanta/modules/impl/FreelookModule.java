package vanta.modules.impl;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import vanta.config.BoolSetting;
import vanta.config.ChoiceSetting;
import vanta.config.Labeled;
import vanta.input.VantaKeys;
import vanta.modules.Category;
import vanta.modules.Module;

/**
 * Freelook / Perspective (default key Left Alt): look around your character in
 * third person while you keep moving and facing the same way. Only the camera
 * moves; your real rotation is unchanged.
 */
public final class FreelookModule extends Module {
	public enum View implements Labeled {
		BACK("Behind"), FRONT("In front"), FIRST("First person");
		private final String label;
		View(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	private static FreelookModule instance;

	public final ChoiceSetting<ActivationMode> mode = setting(new ChoiceSetting<>(key("mode"), "Activation",
			"Hold the key to look around, or press once to toggle.", ActivationMode.HOLD));
	public final ChoiceSetting<View> view = setting(new ChoiceSetting<>(key("view"), "Camera",
			"Where the camera goes while freelooking.", View.BACK));
	public final BoolSetting invertPitch = setting(new BoolSetting(key("invert_pitch"), "Invert up/down",
			"Flip vertical mouse movement while freelooking.", false));

	private boolean active;
	private CameraType previous;
	private float yaw;
	private float pitch;

	public FreelookModule() {
		super("freelook", "Freelook", "Look around your player without turning (Left Alt by default). Some servers don't allow it.",
				Category.VISUALS, false);
		instance = this;
	}

	@Override
	public void tick(Minecraft mc) {
		boolean want;
		if (mode.get() == ActivationMode.HOLD) {
			want = mc.screen == null && VantaKeys.FREELOOK.isDown();
			while (VantaKeys.FREELOOK.consumeClick()) {
				// drain
			}
		} else {
			want = active;
			while (VantaKeys.FREELOOK.consumeClick()) want = !want;
		}
		if (mc.player == null) want = false;
		if (want != active) setActive(mc, want);
	}

	private void setActive(Minecraft mc, boolean on) {
		active = on;
		LocalPlayer p = mc.player;
		if (on && p != null) {
			yaw = p.getYRot();
			pitch = p.getXRot();
			previous = mc.options.getCameraType();
			CameraType target = switch (view.get()) {
				case BACK -> CameraType.THIRD_PERSON_BACK;
				case FRONT -> CameraType.THIRD_PERSON_FRONT;
				case FIRST -> CameraType.FIRST_PERSON;
			};
			mc.options.setCameraType(target);
		} else if (previous != null) {
			mc.options.setCameraType(previous);
			previous = null;
		}
	}

	@Override
	protected void onDisable() {
		if (active) setActive(Minecraft.getInstance(), false);
	}

	public static boolean isActive() {
		return instance != null && instance.isEnabled() && instance.active;
	}

	/** Mouse movement while freelooking turns the camera only. Mirrors Entity.turn. */
	public static void turn(double dx, double dy) {
		FreelookModule f = instance;
		f.yaw += (float) dx * 0.15f;
		f.pitch += (float) (f.invertPitch.on() ? -dy : dy) * 0.15f;
		f.pitch = Math.max(-90f, Math.min(90f, f.pitch));
	}

	public static float yaw() { return instance.yaw; }
	public static float pitch() { return instance.pitch; }

	@Override
	public String status() { return "Key: " + VantaKeys.name(VantaKeys.FREELOOK); }
}
