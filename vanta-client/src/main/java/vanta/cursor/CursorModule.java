package vanta.cursor;

import java.nio.ByteBuffer;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryUtil;

import net.minecraft.client.Minecraft;

import vanta.branding.Theme;
import vanta.client.VantaClient;
import vanta.config.ChoiceSetting;
import vanta.config.ColorSetting;
import vanta.config.IntSetting;
import vanta.config.Labeled;
import vanta.modules.Category;
import vanta.modules.Module;

/**
 * Custom mouse cursor in menus: a VANTA arrow (or dot / ring) in your accent color, with
 * a matching hand when you point at buttons. Made with the system cursor API, so it's
 * exactly as smooth and lag-free as the normal cursor.
 */
public final class CursorModule extends Module {
	public enum Style implements Labeled {
		ARROW("Arrow"), DOT("Dot"), RING("Ring");
		private final String label;
		Style(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	public enum Color implements Labeled {
		ACCENT("Theme accent"), WHITE("White"), CUSTOM("Custom");
		private final String label;
		Color(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	private static CursorModule instance;

	public final ChoiceSetting<Style> style = setting(new ChoiceSetting<>(key("style"), "Cursor style", "Shape of your cursor in menus.", Style.ARROW));
	public final IntSetting size = setting(new IntSetting(key("size"), "Cursor size", "Size in screen pixels.", 22, 12, 48, 2, "px"));
	public final ChoiceSetting<Color> color = setting(new ChoiceSetting<>(key("color"), "Cursor color", "Fill color of the cursor.", Color.ACCENT));
	public final ColorSetting custom = setting(new ColorSetting(key("custom"), "Custom color", "Used when Cursor color is Custom.", 0xFFFFFFFF, false));

	private long arrow, hand;
	private String built = "";
	private boolean dirty = true;

	public CursorModule() {
		super("cursor", "Custom Cursor", "A VANTA cursor in menus in your accent color (arrow, dot or ring), with a matching hand on buttons.",
				Category.INTERFACE, false);
		instance = this;
		Runnable mark = () -> dirty = true;
		style.onChange(v -> mark.run());
		size.onChange(v -> mark.run());
		color.onChange(v -> mark.run());
		custom.onChange(v -> mark.run());
		enabledSetting().onChange(v -> mark.run());
	}

	/** The GLFW cursor to use instead of the default arrow (0 = keep Minecraft's). */
	public static long arrowHandle() {
		CursorModule m = instance;
		return m != null && m.isEnabled() ? m.arrow : 0L;
	}

	/** The GLFW cursor to use instead of the pointing hand (0 = keep Minecraft's). */
	public static long handHandle() {
		CursorModule m = instance;
		return m != null && m.isEnabled() ? m.hand : 0L;
	}

	@Override
	public void tick(Minecraft mc) {
		rebuildIfNeeded(mc);
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc != null && mc.getWindow() != null) mc.execute(() -> {
			GLFW.glfwSetCursor(mc.getWindow().handle(), 0L);
			destroy();
		});
	}

	private int fill() {
		return switch (color.get()) {
			case ACCENT -> Theme.accent() & 0xFFFFFF;
			case WHITE -> 0xFFFFFF;
			case CUSTOM -> custom.argb() & 0xFFFFFF;
		};
	}

	private void rebuildIfNeeded(Minecraft mc) {
		String key = style.get() + "/" + size.value() + "/" + Integer.toHexString(fill());
		if (!dirty && key.equals(built)) return;
		dirty = false;
		built = key;
		try {
			long oldArrow = arrow, oldHand = hand;
			CursorArt.Shape shape = switch (style.get()) {
				case DOT -> CursorArt.Shape.DOT;
				case RING -> CursorArt.Shape.RING;
				default -> CursorArt.Shape.ARROW;
			};
			int edge = (fill() & 0xFFFFFF) == 0xFFFFFF ? 0x101010 : 0xFFFFFF;
			arrow = create(shape, size.value(), fill(), edge);
			hand = shape == CursorArt.Shape.ARROW ? create(CursorArt.Shape.POINTER, size.value(), fill(), edge) : arrow;
			if (mc.screen != null && mc.getWindow() != null) GLFW.glfwSetCursor(mc.getWindow().handle(), arrow);
			if (oldArrow != 0) GLFW.glfwDestroyCursor(oldArrow);
			if (oldHand != 0 && oldHand != oldArrow) GLFW.glfwDestroyCursor(oldHand);
		} catch (RuntimeException | LinkageError e) {
			VantaClient.LOG.warn("[VANTA] Custom cursor isn't supported here", e);
			arrow = hand = 0;
		}
	}

	private void destroy() {
		if (arrow != 0) GLFW.glfwDestroyCursor(arrow);
		if (hand != 0 && hand != arrow) GLFW.glfwDestroyCursor(hand);
		arrow = hand = 0;
		built = "";
	}

	private static long create(CursorArt.Shape shape, int size, int fill, int edge) {
		int[] px = CursorArt.render(shape, size, fill, edge);
		ByteBuffer buf = MemoryUtil.memAlloc(size * size * 4);
		try {
			for (int argb : px) {
				buf.put((byte) ((argb >> 16) & 0xFF)).put((byte) ((argb >> 8) & 0xFF)).put((byte) (argb & 0xFF)).put((byte) ((argb >>> 24) & 0xFF));
			}
			buf.flip();
			GLFWImage img = GLFWImage.malloc();
			try {
				img.set(size, size, buf);
				int[] hot = CursorArt.hotspot(shape, size);
				return GLFW.glfwCreateCursor(img, hot[0], hot[1]);
			} finally {
				img.free();
			}
		} finally {
			MemoryUtil.memFree(buf);
		}
	}
}
