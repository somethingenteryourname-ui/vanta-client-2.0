package vanta.hud.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;

import vanta.config.BoolSetting;
import vanta.hud.TextHudModule;
import vanta.modules.Category;

/**
 * Toggle Sprint / Toggle Sneak. Turns on Minecraft's own toggle options for the sprint and
 * sneak keys (the same ones in Controls), and shows your sprint/sneak state on the HUD.
 */
public final class SprintStatusWidget extends TextHudModule {
	private final BoolSetting toggleSprint = setting(new BoolSetting(key("toggle_sprint"), "Toggle sprint",
			"Press sprint once to keep sprinting.", true));
	private final BoolSetting toggleSneak = setting(new BoolSetting(key("toggle_sneak"), "Toggle sneak",
			"Press sneak once to keep sneaking.", false));
	private final BoolSetting showStatus = setting(new BoolSetting(key("show_status"), "Show status",
			"Show [Sprinting (Toggled)] etc. on the HUD.", true));
	private String shown = null;

	public SprintStatusWidget() {
		super("toggle_sprint", "Toggle Sprint", "Toggle sprint and sneak with one press, with a status on the HUD.", Category.PVP, false, 0, 1000);
		toggleSprint.onChange(v -> syncExternal());
		toggleSneak.onChange(v -> syncExternal());
	}

	@Override
	protected void onEnable() { syncExternal(); }

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		Options o = mc == null ? null : mc.options;
		if (o == null) return;
		o.toggleSprint().set(false);
		o.toggleCrouch().set(false);
		o.save();
	}

	@Override
	public void syncExternal() {
		Minecraft mc = Minecraft.getInstance();
		Options o = mc == null ? null : mc.options;
		if (o == null || !isEnabled()) return;
		o.toggleSprint().set(toggleSprint.on());
		o.toggleCrouch().set(toggleSneak.on());
		o.save();
	}

	@Override
	public void update() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		String s;
		if (p == null || !showStatus.on()) {
			s = "";
		} else if (mc.options.keyShift.isDown() && toggleSneak.on()) {
			s = "Sneaking (Toggled)";
		} else if (p.isShiftKeyDown()) {
			s = "Sneaking";
		} else if (mc.options.keySprint.isDown() && toggleSprint.on()) {
			s = "Sprinting (Toggled)";
		} else if (p.isSprinting()) {
			s = "Sprinting";
		} else {
			s = "";
		}
		if (s.equals(shown)) return;
		shown = s;
		setLine("", s.isEmpty() ? "Sprint status" : s);
	}

	@Override
	public boolean hasContent() { return shown != null && !shown.isEmpty(); }
}
