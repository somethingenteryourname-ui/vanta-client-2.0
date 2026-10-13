package vanta.gui.items;

import java.time.LocalTime;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import vanta.branding.Theme;
import vanta.chat.ChatFeatures;
import vanta.chat.ChatModule;
import vanta.chat.ChatStyle;
import vanta.ui.Colors;
import vanta.ui.Ui;

/**
 * Live preview of the VANTA chat on the Chat page: the panel, accent bar, logo watermark,
 * timestamps, a stacked message and a mention highlight, all using your current settings.
 * Click it to replay the logo intro.
 */
public final class ChatPreview extends Item {
	private final ChatModule module;
	private long introStart = System.currentTimeMillis();

	public ChatPreview(ChatModule module) {
		this.module = module;
	}

	@Override
	public void render(GuiGraphics g, int ax, int ay, int mx, int my, boolean hovered) {
		Ui.card(g, ax, ay, w, h, hovered, 0);
		int sx = ax + 6, sy = ay + 6, sw = w - 12, sh = h - 12;
		// a dusk-colored "game" backdrop so the panel's transparency shows
		g.fillGradient(sx, sy, sx + sw, sy + sh, 0xFF2B3A55, 0xFF18301F);
		g.enableScissor(sx, sy, sx + sw, sy + sh);

		String you = name();
		String[][] lines = {
				{"System", "Welcome to the server!"},
				{"<Steve>", "gg that was close"},
				{"<Alex>", "anyone up for duels?"},
				{"<Steve>", "hey " + you + ", want to team?"},
				{"", "Server restarting in 5 minutes"},
		};
		int lineH = 10;
		int panelW = Math.min(sw - 8, 260);
		int bottom = sy + sh - 4;
		int top = bottom - lines.length * lineH - 2;
		int x0 = sx + 4;
		if (!module.isEnabled() || !module.style.on()) {
			for (int i = 0; i < lines.length; i++) {
				int yb = bottom - (lines.length - 1 - i) * lineH;
				g.fill(x0, yb - lineH, x0 + panelW, yb, 0x80000000);
			}
		} else {
			int fullTop = module.fullWhileTyping.on() ? sy + 4 : top;
			ChatStyle.panel(g, module, x0, fullTop, x0 + panelW, bottom, 1f, module.watermark.on(), introStart);
		}
		String stamp = module.isEnabled() && module.timestamps.on() ? ChatFeatures.stamp(LocalTime.now(), module.timeFormat.get()) + " " : "";
		int stampColor = module.stampColor.get() == ChatModule.StampColor.ACCENT ? Theme.accent() | 0xFF000000 : 0xFF8A8F98;
		boolean on = module.isEnabled();
		for (int i = 0; i < lines.length; i++) {
			int yb = bottom - (lines.length - 1 - i) * lineH;
			boolean mention = on && module.mentions.on() && i == 3;
			if (mention) {
				g.fill(x0, yb - lineH, x0 + panelW, yb, Colors.withAlpha(Theme.accent(), 0.2f));
				g.fill(x0, yb - lineH, x0 + 2, yb, Theme.accent() | 0xFF000000);
			}
			int tx = x0 + 4, ty = yb - lineH + 1;
			if (!stamp.isEmpty()) {
				g.drawString(Ui.font(), stamp, tx, ty, stampColor, true);
				tx += Ui.width(stamp);
			}
			String who = lines[i][0];
			if (who.equals("System")) {
				g.drawString(Ui.font(), lines[i][1], tx, ty, 0xFFFFFF55, true);
				continue;
			}
			if (!who.isEmpty()) {
				g.drawString(Ui.font(), who + " ", tx, ty, 0xFFFFFFFF, true);
				tx += Ui.width(who + " ");
			}
			g.drawString(Ui.font(), lines[i][1], tx, ty, who.isEmpty() ? 0xFFFF5555 : 0xFFFFFFFF, true);
			if (on && module.stack.on() && i == lines.length - 1) {
				g.drawString(Ui.font(), " (x3)", tx + Ui.width(lines[i][1]), ty, 0xFF9AA0AA, true);
			}
		}
		g.disableScissor();
		if (!on) Ui.textCentered(g, "VANTA chat is off", ax + w / 2, ay + 10, Theme.textDim());
	}

	private static String name() {
		try {
			return Minecraft.getInstance().getUser().getName();
		} catch (RuntimeException e) {
			return "you";
		}
	}

	@Override
	public boolean click(int ax, int ay, double mx, double my, int button) {
		introStart = System.currentTimeMillis();
		return true;
	}

	@Override public String hint() { return "Live preview with your settings. Click to replay the logo animation."; }
}
