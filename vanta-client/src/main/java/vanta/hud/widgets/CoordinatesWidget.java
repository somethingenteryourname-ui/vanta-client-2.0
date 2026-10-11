package vanta.hud.widgets;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import vanta.config.BoolSetting;
import vanta.hud.TextHudModule;
import vanta.modules.Category;

public final class CoordinatesWidget extends TextHudModule {
	private final BoolSetting decimals = setting(new BoolSetting(key("decimals"), "Decimals",
			"Show one decimal place instead of block coordinates.", false));
	private final BoolSetting otherDimension = setting(new BoolSetting(key("other_dimension"), "Nether/Overworld",
			"Also show the matching coordinates in the other dimension.", false));
	private long lastKey = Long.MIN_VALUE;

	public CoordinatesWidget() {
		super("coordinates", "Coordinates", "Your current X, Y and Z position.", Category.HUD, false, 0, 120);
	}

	@Override
	public void update() {
		Player p = Minecraft.getInstance().player;
		if (p == null) {
			setLine("XYZ", "-");
			return;
		}
		double x = p.getX(), y = p.getY(), z = p.getZ();
		boolean dec = decimals.on();
		long k = dec
				? Math.round(x * 10) * 31 + Math.round(y * 10) * 1_000_003L + Math.round(z * 10) * 998_244_353L
				: (long) Math.floor(x) * 31 + (long) Math.floor(y) * 1_000_003L + (long) Math.floor(z) * 998_244_353L;
		k = k * 4 + (dec ? 1 : 0) + (otherDimension.on() ? 2 : 0);
		if (k == lastKey) return;
		lastKey = k;

		String main = format(x, y, z, dec);
		if (otherDimension.on() && p.level() != null) {
			boolean nether = p.level().dimension() == Level.NETHER;
			double f = nether ? 8 : 0.125;
			String label = nether ? "Overworld" : "Nether";
			setLines(new String[]{"XYZ", label}, new String[]{main, format(x * f, y, z * f, dec)});
		} else {
			setLine("XYZ", main);
		}
	}

	private static String format(double x, double y, double z, boolean dec) {
		if (dec) return String.format(Locale.ROOT, "%.1f %.1f %.1f", x, y, z);
		return (long) Math.floor(x) + " " + (long) Math.floor(y) + " " + (long) Math.floor(z);
	}
}
