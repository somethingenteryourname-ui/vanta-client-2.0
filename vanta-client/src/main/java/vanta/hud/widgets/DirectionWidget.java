package vanta.hud.widgets;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import vanta.config.BoolSetting;
import vanta.hud.TextHudModule;
import vanta.modules.Category;

public final class DirectionWidget extends TextHudModule {
	private static final String[] NAMES = {"South (+Z)", "West (-X)", "North (-Z)", "East (+X)"};
	private final BoolSetting degrees = setting(new BoolSetting(key("degrees"), "Show degrees",
			"Show your exact yaw angle.", true));
	private int lastKey = Integer.MIN_VALUE;

	public DirectionWidget() {
		super("direction", "Direction", "The compass direction you are facing.", Category.HUD, false, 0, 180);
	}

	@Override
	public void update() {
		Player p = Minecraft.getInstance().player;
		if (p == null) {
			setLine("Facing", "-");
			return;
		}
		float yaw = p.getYRot() % 360f;
		if (yaw < 0) yaw += 360f;
		int key = Math.round(yaw * 10) * 2 + (degrees.on() ? 1 : 0);
		if (key == lastKey) return;
		lastKey = key;
		String name = NAMES[Math.round(yaw / 90f) & 3];
		setLine("Facing", degrees.on() ? name + String.format(Locale.ROOT, " %.1f°", yaw) : name);
	}
}
