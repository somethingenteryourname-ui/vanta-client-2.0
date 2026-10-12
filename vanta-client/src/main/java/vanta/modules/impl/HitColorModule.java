package vanta.modules.impl;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;

import vanta.client.VantaClient;
import vanta.config.ColorSetting;
import vanta.config.IntSetting;
import vanta.modules.Category;
import vanta.modules.Module;

/**
 * Hit Color: changes the red flash entities get when hurt. Minecraft draws that flash from
 * a tiny 16x16 texture; this recolors its top half (the hurt rows) and puts the vanilla
 * red back when turned off.
 */
public final class HitColorModule extends Module {
	private static final int VANILLA = 0xB2FF0000;

	public final ColorSetting color = setting(new ColorSetting(key("color"), "Hit color",
			"Color entities flash when they take damage.", 0xFF8B5CF6, false));
	public final IntSetting strength = setting(new IntSetting(key("strength"), "Strength",
			"How strong the tint is. Vanilla is about 70%.", 70, 10, 100, 5, "%"));

	private int applied = VANILLA;

	public HitColorModule() {
		super("hit_color", "Hit Color", "Change the red flash entities get when they're hit to any color.", Category.VISUALS, false);
	}

	private int wanted() {
		int a = Math.round(strength.value() / 100f * 255f);
		return (a << 24) | (color.argb() & 0xFFFFFF);
	}

	@Override
	public void tick(Minecraft mc) {
		int want = wanted();
		if (want != applied) apply(mc, want);
	}

	@Override
	protected void onDisable() {
		apply(Minecraft.getInstance(), VANILLA);
	}

	private void apply(Minecraft mc, int argb) {
		try {
			if (mc.gameRenderer == null) return;
			DynamicTexture tex = ((vanta.mixin.feature.OverlayTextureAccessor) mc.gameRenderer.overlayTexture()).vanta$texture();
			NativeImage pixels = tex.getPixels();
			if (pixels == null) return;
			for (int y = 0; y < 8; y++) {
				for (int x = 0; x < 16; x++) pixels.setPixel(x, y, argb);
			}
			tex.upload();
			applied = argb;
		} catch (RuntimeException | LinkageError e) {
			VantaClient.LOG.warn("[VANTA] Hit Color could not change the hurt tint; turning it off", e);
			applied = argb; // don't retry every tick
			if (argb != VANILLA) setEnabled(false);
		}
	}
}
