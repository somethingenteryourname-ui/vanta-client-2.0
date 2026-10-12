package vanta.modules.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import vanta.branding.Theme;
import vanta.config.BoolSetting;
import vanta.config.IntSetting;
import vanta.modules.Category;
import vanta.modules.Module;
import vanta.ui.Colors;
import vanta.ui.Ui;

/**
 * Restyled sidebar scoreboard: a themed panel, optional hidden score numbers (the red
 * numbers most servers don't use), adjustable height position. Shows exactly the same
 * lines as vanilla. HUD Tweaks' scoreboard size still applies.
 */
public final class ScoreboardModule extends Module {
	private static final Comparator<PlayerScoreEntry> ORDER = Comparator.comparingInt(PlayerScoreEntry::value).reversed()
			.thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);
	private static ScoreboardModule instance;

	public final BoolSetting hideNumbers = setting(new BoolSetting(key("hide_numbers"), "Hide score numbers",
			"Hide the numbers on the right (usually red). Lines and their order stay the same.", true));
	public final BoolSetting themed = setting(new BoolSetting(key("themed"), "VANTA style",
			"Rounded panel in your theme colors with an accent title. Off keeps vanilla bars.", true));
	public final IntSetting opacity = setting(new IntSetting(key("opacity"), "Background opacity",
			"How solid the panel is.", 45, 0, 100, 5, "%"));
	public final BoolSetting textShadow = setting(new BoolSetting(key("text_shadow"), "Text shadow",
			"Shadow under the lines.", false));
	public final IntSetting height = setting(new IntSetting(key("height"), "Vertical position",
			"Where the scoreboard sits on the right edge. 50 is vanilla (middle).", 50, 5, 95, 1, "%"));

	public ScoreboardModule() {
		super("scoreboard", "Scoreboard", "Cleaner sidebar scoreboard: themed panel, hide the red numbers, move it up or down.",
				Category.INTERFACE, false);
		instance = this;
	}

	private record Line(Component name, Component score, int scoreWidth) {
	}

	/** Draws the sidebar if this module is on. Returns false to let vanilla draw it. */
	public static boolean render(GuiGraphics g) {
		if (instance == null || !instance.isEnabled()) return false;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return true;
		Scoreboard scoreboard = mc.level.getScoreboard();
		Objective objective = null;
		PlayerTeam team = scoreboard.getPlayersTeam(mc.player.getScoreboardName());
		if (team != null) {
			DisplaySlot slot = DisplaySlot.teamColorToSlot(team.getColor());
			if (slot != null) objective = scoreboard.getDisplayObjective(slot);
		}
		if (objective == null) objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (objective == null) return true;
		g.nextStratum();
		instance.draw(g, mc.font, scoreboard, objective);
		return true;
	}

	private void draw(GuiGraphics g, Font font, Scoreboard scoreboard, Objective objective) {
		NumberFormat format = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
		boolean numbers = !hideNumbers.on();
		List<PlayerScoreEntry> entries = new ArrayList<>();
		for (PlayerScoreEntry e : scoreboard.listPlayerScores(objective)) if (!e.isHidden()) entries.add(e);
		entries.sort(ORDER);
		if (entries.size() > 15) entries = entries.subList(0, 15);

		List<Line> lines = new ArrayList<>(entries.size());
		Component title = objective.getDisplayName();
		int width = font.width(title);
		int spacer = font.width(": ");
		for (PlayerScoreEntry e : entries) {
			Component name = PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(e.owner()), e.ownerName());
			Component score = numbers ? e.formatValue(format) : Component.empty();
			int sw = numbers ? font.width(score) : 0;
			lines.add(new Line(name, score, sw));
			width = Math.max(width, font.width(name) + (sw > 0 ? spacer + sw : 0));
		}

		int count = lines.size();
		int lineH = 9;
		int bodyH = count * lineH;
		boolean styled = themed.on();
		int padX = styled ? 4 : 2;
		int right = g.guiWidth() - 3 + 2;
		int left = g.guiWidth() - width - 3;
		int totalH = bodyH + lineH + 2 + (styled ? 4 : 0);
		int center = Math.round(g.guiHeight() * height.value() / 100f);
		int bottom = height.value() == 50 ? g.guiHeight() / 2 + bodyH / 3 : center + totalH / 2;
		bottom = Math.max(totalH + 2, Math.min(g.guiHeight() - 2, bottom));
		int headerY = bottom - bodyH;
		float alpha = opacity.value() / 100f;

		if (styled) {
			int x0 = left - padX, x1 = right + 1;
			int y0 = headerY - lineH - 4, y1 = bottom + 2;
			Ui.roundRect(g, x0, y0, x1 - x0, y1 - y0, Theme.radius(), Colors.withAlpha(Theme.background(), alpha));
			Ui.roundRect(g, x0, y0, x1 - x0, lineH + 3, Theme.radius(), Colors.withAlpha(Theme.accent(), Math.min(1f, alpha + 0.2f)));
			Ui.rect(g, x0, headerY - 1, x1 - x0, 1, Colors.withAlpha(Theme.accent(), 0.8f));
		} else {
			g.fill(left - 2, headerY - lineH - 1, right, headerY - 1, Colors.withAlpha(0xFF000000, Math.min(1f, alpha + 0.1f)));
			g.fill(left - 2, headerY - 1, right, bottom, Colors.withAlpha(0xFF000000, alpha));
		}
		int titleColor = styled ? Colors.contrastText(Theme.accent()) : 0xFFFFFFFF;
		g.drawString(font, title, left + width / 2 - font.width(title) / 2, headerY - lineH - (styled ? 1 : 0), titleColor, textShadow.on());
		for (int i = 0; i < count; i++) {
			Line l = lines.get(i);
			int y = bottom - (count - i) * lineH;
			g.drawString(font, l.name(), left, y, 0xFFFFFFFF, textShadow.on());
			if (l.scoreWidth() > 0) g.drawString(font, l.score(), right - l.scoreWidth(), y, 0xFFFFFFFF, textShadow.on());
		}
	}
}
