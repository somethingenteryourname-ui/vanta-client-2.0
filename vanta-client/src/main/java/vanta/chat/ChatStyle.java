package vanta.chat;

import java.util.List;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.entity.player.ChatVisiblity;

import vanta.branding.Logo;
import vanta.branding.Theme;
import vanta.mixin.feature.ChatComponentAccessor;
import vanta.ui.Colors;
import vanta.ui.Ui;

/**
 * Draws the VANTA chat look: one rounded panel in your theme behind the visible lines,
 * an accent bar, mention highlights, and your logo + client name faded into the
 * background (with the intro animation when chat opens).
 *
 * The panel shrinks smoothly from the top as old lines fade, and fades out with the
 * last line, so it behaves like vanilla's per-line backgrounds but looks like one piece.
 */
public final class ChatStyle {
	private static boolean wasChatting;
	private static long openedAt;
	private static boolean pushed;

	private ChatStyle() {
	}

	/** Same fade curve vanilla uses for a line that isn't in an open chat. */
	public static float lineAlpha(int ticks, int addedTime) {
		double t = (ticks - addedTime) / 200.0;
		t = (1.0 - t) * 10.0;
		t = Math.max(0.0, Math.min(1.0, t));
		return (float) (t * t);
	}

	/**
	 * Called at the start of ChatComponent.render, before vanilla draws anything.
	 * Applies the slide-in offset and draws the panel. {@link #after} undoes the offset.
	 */
	public static void before(GuiGraphics g, ChatComponent chat, int ticks, boolean chatting) {
		pushed = false;
		geo = null;
		ChatModule m = ChatModule.active();
		if (m == null) return;
		Minecraft mc = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		if (chatting && !wasChatting) openedAt = now;
		wasChatting = chatting;
		if (mc.options.chatVisibility().get() == ChatVisiblity.HIDDEN) return;

		if (!((Object) chat instanceof ChatComponentAccessor acc)) return;
		int scrollPos = acc.vanta$scrollPos();

		g.pose().pushMatrix();
		pushed = true;
		if (m.smooth.on() && scrollPos == 0 && !Theme.reducedMotion()) {
			float p = (now - ChatFeatures.lastAddedAt()) / 160f;
			if (p >= 0f && p < 1f) {
				float u = 1f - p;
				float lineH = 9f * (float) (mc.options.chatLineSpacing().get() + 1.0);
				g.pose().translate(0f, u * u * lineH * Math.min(2, Math.max(1, ChatFeatures.lastAddedLines())));
			}
		}
		float scale = (float) (double) mc.options.chatScale().get();
		if (scale <= 0f) return;
		int maxWidth = (int) Math.ceil(ChatComponent.getWidth(mc.options.chatWidth().get()) / scale);
		int chatBottom = (int) Math.floor((g.guiHeight() - 40) / scale);
		double spacing = mc.options.chatLineSpacing().get();
		int entryH = (int) (9 * (spacing + 1.0));
		int perPage = chat.getLinesPerPage();
		List<GuiMessage.Line> lines = acc.vanta$trimmedMessages();
		int visible = Math.min(lines.size() - scrollPos, perPage);
		// remembered for the heads, which are drawn after the text
		geo = new Geometry(lines, scrollPos, visible, scale, chatBottom, entryH,
				(int) Math.round(8.0 * (spacing + 1.0) - 4.0 * spacing), ticks, chatting,
				(float) (mc.options.chatOpacity().get() * 0.9 + 0.1));
		if (!m.style.on()) return;

		float coverage = 0f, maxAlpha = 0f;
		int widest = 0;
		for (int i = 0; i < visible; i++) {
			GuiMessage.Line line = lines.get(i + scrollPos);
			float a = chatting ? 1f : lineAlpha(ticks, line.addedTime());
			if (a <= 1e-5f) continue;
			coverage += a;
			maxAlpha = Math.max(maxAlpha, a);
			if (m.width.get() == ChatModule.PanelWidth.FIT) widest = Math.max(widest, mc.font.width(line.content()));
		}
		float panelH = coverage * entryH;
		if (chatting) {
			maxAlpha = 1f;
			if (m.fullWhileTyping.on()) panelH = Math.max(panelH, perPage * entryH);
		}
		if (panelH < 1f || maxAlpha <= 0f) return;

		g.pose().pushMatrix();
		g.pose().scale(scale, scale);
		g.pose().translate(4f, 0f);
		int x0 = -4;
		int x1 = m.width.get() == ChatModule.PanelWidth.FIT && !chatting ? Math.min(maxWidth, widest) + 8 : maxWidth + 8;
		int top = Math.round(chatBottom - panelH);
		boolean wm = m.watermark.on() && (chatting || !m.watermarkWhileTyping.on());
		panel(g, m, x0, top, x1, chatBottom, maxAlpha, wm, chatting ? openedAt : 0);

		// mention highlights
		for (int i = 0; i < visible; i++) {
			GuiMessage.Line line = lines.get(i + scrollPos);
			if (!ChatFeatures.isMentionTag(line.tag())) continue;
			float a = chatting ? 1f : lineAlpha(ticks, line.addedTime());
			if (a <= 1e-5f) continue;
			int bottom = chatBottom - i * entryH;
			g.fill(x0, bottom - entryH, x1, bottom, Colors.withAlpha(Theme.accent(), 0.2f * a));
		}
		g.pose().popMatrix();
	}

	public static void after(GuiGraphics g) {
		Geometry geom = geo;
		geo = null;
		ChatModule m = ChatModule.active();
		if (geom != null && m != null && m.heads.on()) {
			try {
				drawHeads(g, geom);
			} catch (RuntimeException e) {
				// a skin that isn't ready yet: skip this frame
			}
		}
		if (pushed) {
			pushed = false;
			g.pose().popMatrix();
		}
	}

	private record Geometry(List<GuiMessage.Line> lines, int scrollPos, int visible, float scale, int chatBottom, int entryH,
			int textOffset, int ticks, boolean chatting, float textOpacity) {
	}

	private static Geometry geo;

	private static void drawHeads(GuiGraphics g, Geometry geom) {
		g.pose().pushMatrix();
		g.pose().scale(geom.scale(), geom.scale());
		g.pose().translate(4f, 0f);
		for (int i = 0; i < geom.visible(); i++) {
			GuiMessage.Line line = geom.lines().get(i + geom.scrollPos());
			PlayerSkin skin = ChatHeads.skinFor(line);
			if (skin == null) continue;
			float a = geom.chatting() ? 1f : lineAlpha(geom.ticks(), line.addedTime());
			if (a <= 0.02f) continue;
			int entryBottom = geom.chatBottom() - i * geom.entryH();
			int textTop = entryBottom - geom.textOffset();
			PlayerFaceRenderer.draw(g, skin, 0, textTop, 8, Colors.withAlpha(0xFFFFFFFF, a * geom.textOpacity()));
		}
		g.pose().popMatrix();
	}

	/** Draws the panel, accent bar and watermark in the given box. Also used by the menu preview. */
	public static void panel(GuiGraphics g, ChatModule m, int x0, int top, int x1, int bottom, float alpha, boolean watermark, long introStart) {
		int w = x1 - x0, h = bottom - top;
		if (w <= 0 || h <= 0) return;
		float op = m.panelOpacity.value() / 100f * alpha;
		int fill = Colors.withAlpha(Theme.panel(), op);
		int radius = m.rounded.on() ? Math.min(4, Theme.radius()) : 0;
		if (radius > 0) Ui.roundRect(g, x0, top, w, h, radius, fill);
		else Ui.rect(g, x0, top, w, h, fill);

		if (m.accentBar.on() && h > 2) {
			int inset = radius > 0 ? Math.min(radius, h / 2) : 0;
			g.fillGradient(x0, top + inset, x0 + 1, bottom - inset, Colors.withAlpha(Theme.accent(), 0.95f * alpha),
					Colors.withAlpha(Theme.secondary(), 0.95f * alpha));
		}

		if (watermark && h >= 18 && w >= 40) {
			float wmAlpha = m.watermarkOpacity.value() / 100f * alpha;
			int preferred = Math.max(8, Math.round(Math.min(34f, h * 0.42f) * m.watermarkSize.value() / 100f));
			int size = Logo.fit(w - 12, preferred, true);
			boolean withName = size >= 9;
			if (!withName) size = Math.min(preferred, h - 4);
			int lw = Logo.width(size, withName);
			int lx = switch (m.watermarkPosition.get()) {
				case LEFT -> x0 + 6;
				case RIGHT -> x1 - 6 - lw;
				default -> x0 + (w - lw) / 2;
			};
			int ly = top + (h - size) / 2;
			long intro = m.watermarkAnimation.on() ? introStart : 0;
			Logo.draw(g, lx, ly, size, withName, intro, wmAlpha);
		}
	}

	/** Draws the chat input box in the VANTA style. Returns false to let vanilla draw it. */
	public static boolean inputBox(GuiGraphics g, int x0, int y0, int x1, int y1) {
		ChatModule m = ChatModule.active();
		if (m == null || !m.inputBox.on()) return false;
		int w = x1 - x0, h = y1 - y0;
		Ui.roundRect(g, x0, y0, w, h, Math.min(3, Theme.radius()), Colors.withAlpha(Theme.panel(), Math.max(0.35f, m.panelOpacity.value() / 100f)));
		Ui.hGradient(g, x0 + 2, y1 - 1, w - 4, 1, Theme.accent() | 0xFF000000, Theme.secondary() | 0xFF000000);
		if (m.inputLogo.on() && w > 60) {
			int size = Math.max(6, h - 4);
			Logo.draw(g, x1 - size - 3, y0 + (h - size) / 2, size, false, 0, 0.7f);
		}
		return true;
	}
}
