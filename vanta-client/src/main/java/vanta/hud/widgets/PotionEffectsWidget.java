package vanta.hud.widgets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.effect.MobEffectInstance;

import vanta.config.BoolSetting;
import vanta.hud.HudModule;
import vanta.modules.Category;
import vanta.ui.Ui;

/** Active potion effects with icons, levels and time left. */
public final class PotionEffectsWidget extends HudModule {
	private static final String[] ROMAN = {"", "", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};

	private final BoolSetting showNames = setting(new BoolSetting(key("names"), "Show names", "Show effect names next to icons.", true));
	private final BoolSetting blinkEnding = setting(new BoolSetting(key("blink"), "Blink when ending", "Flash effects with less than 10 seconds left.", true));

	private final List<MobEffectInstance> effects = new ArrayList<>();
	private final List<String> lines = new ArrayList<>();
	private int textWidth;
	private long nextRefresh;

	public PotionEffectsWidget() {
		super("potion_effects", "Potion Effects", "Your active effects with time left.", Category.HUD, false, 1000, 200);
	}

	@Override
	public void update() {
		long now = System.currentTimeMillis();
		if (now < nextRefresh) return;
		nextRefresh = now + 250;
		effects.clear();
		lines.clear();
		textWidth = 0;
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) return;
		Collection<MobEffectInstance> active = p.getActiveEffects();
		for (MobEffectInstance e : active) {
			effects.add(e);
			int amp = e.getAmplifier() + 1;
			String level = amp < ROMAN.length ? ROMAN[amp] : " " + amp;
			String time;
			if (e.isInfiniteDuration()) {
				time = "**:**";
			} else {
				int secs = e.getDuration() / 20;
				time = (secs / 60) + ":" + String.format("%02d", secs % 60);
			}
			String line = showNames.on() ? e.getEffect().value().getDisplayName().getString() + level + "  " + time : time;
			lines.add(line);
			textWidth = Math.max(textWidth, Ui.width(line));
		}
	}

	@Override
	public boolean hasContent() { return !effects.isEmpty(); }

	@Override public int width() { return 22 + Math.max(30, textWidth) + 4; }
	@Override public int height() { return Math.max(1, effects.size()) * 20 + 2; }

	@Override
	protected void renderContent(GuiGraphics g, boolean editor) {
		if (effects.isEmpty()) {
			if (editor) drawText(g, "Potion effects", 4, 6, textColor.argb());
			return;
		}
		boolean blinkOff = blinkEnding.on() && (System.currentTimeMillis() / 300) % 2 == 0;
		for (int i = 0; i < effects.size(); i++) {
			MobEffectInstance e = effects.get(i);
			int y = 2 + i * 20;
			boolean ending = !e.isInfiniteDuration() && e.getDuration() < 200;
			if (ending && blinkOff) continue;
			g.blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(e.getEffect()), 2, y, 18, 18);
			drawText(g, lines.get(i), 24, y + 5, e.getEffect().value().isBeneficial() ? valueColor() : 0xFFFF7777);
		}
	}
}
