package vanta.menu;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import vanta.branding.Theme;
import vanta.client.VantaClient;
import vanta.config.ConfigManager;
import vanta.modules.ModuleRegistry;
import vanta.modules.impl.TitleScreenModule;

/**
 * Custom menu backgrounds (PNG, JPG or animated GIF from config/vanta/backgrounds) and the
 * "last world" background: a downscaled shot of the world (without HUD) taken now and then
 * while you play, shown behind the home screen next time.
 *
 * Files are decoded on a background thread; only the texture upload happens on the render
 * thread. GIFs are limited in size and frame count so they can't eat memory.
 */
public final class BackgroundImages {
	private static final int GIF_MAX_WIDTH = 640;
	private static final int GIF_MAX_FRAMES = 120;
	private static final long GIF_MAX_PIXELS = 18_000_000L; // ~72 MB of frames at most
	private static final int STILL_MAX_SIDE = 4096;
	private static final long MAX_FILE_BYTES = 40L * 1024 * 1024;

	private static final Slot CUSTOM = new Slot(Identifier.fromNamespaceAndPath("vanta", "dynamic/background"));
	private static final Slot LAST_WORLD = new Slot(Identifier.fromNamespaceAndPath("vanta", "dynamic/last_world"));

	private static long worldSince;
	private static long lastCapture;
	private static volatile boolean captureRequested;
	private static volatile boolean capturing;

	private BackgroundImages() {
	}

	/** Decoded pixels, frames in ABGR order (what NativeImage stores). */
	record Decoded(int width, int height, List<int[]> frames, int[] delaysMs) {
	}

	private static final class Slot {
		final Identifier id;
		Path path;
		long modified = -1;
		long checkedAt;
		CompletableFuture<Decoded> pending;
		DynamicTexture texture;
		Decoded image;
		int shownFrame = -1;
		long totalMs;
		String error;

		Slot(Identifier id) {
			this.id = id;
		}
	}

	/** Linear filtering so scaled backgrounds look smooth instead of pixelated. */
	private static final class SmoothTexture extends DynamicTexture {
		SmoothTexture(NativeImage image) {
			super(() -> "VANTA background", image);
			this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		}
	}

	// ------------------------------------------------------------------ files

	public static Path folder() {
		Path dir = ConfigManager.get().directory().resolve("backgrounds");
		try {
			Files.createDirectories(dir);
		} catch (IOException ignored) {
		}
		return dir;
	}

	public static Path lastWorldFile() {
		return ConfigManager.get().directory().resolve("last_world.png");
	}

	/** Image files in the backgrounds folder, sorted by name. */
	public static List<String> available() {
		List<String> out = new ArrayList<>();
		try (Stream<Path> files = Files.list(folder())) {
			files.filter(Files::isRegularFile).map(p -> p.getFileName().toString())
					.filter(BackgroundImages::supported).sorted(String.CASE_INSENSITIVE_ORDER).forEach(out::add);
		} catch (IOException | RuntimeException ignored) {
		}
		return out;
	}

	static boolean supported(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".gif");
	}

	/** The custom image to show: the configured file name, or the first image in the folder. */
	private static Path customPath() {
		TitleScreenModule c = ModuleRegistry.TITLE_SCREEN;
		String wanted = c == null ? "" : c.imageFile.get().strip();
		List<String> files = available();
		if (files.isEmpty()) return null;
		if (!wanted.isEmpty()) {
			for (String f : files) if (f.equalsIgnoreCase(wanted)) return folder().resolve(f);
		}
		return folder().resolve(files.get(0));
	}

	public static String customStatus() {
		if (CUSTOM.error != null) return CUSTOM.error;
		if (available().isEmpty()) return "No images yet: put PNG, JPG or GIF files in config/vanta/backgrounds";
		return CUSTOM.image == null ? "Loading..." : CUSTOM.path.getFileName() + " (" + CUSTOM.image.width() + "x" + CUSTOM.image.height()
				+ (CUSTOM.image.frames().size() > 1 ? ", " + CUSTOM.image.frames().size() + " frames" : "") + ")";
	}

	public static void openFolder() {
		Util.getPlatform().openPath(folder());
	}

	// ------------------------------------------------------------------ drawing

	/** Draws the custom background. Returns false when there is nothing to show yet. */
	public static boolean drawCustom(GuiGraphics g, int width, int height) {
		long now = Util.getMillis();
		if (now - CUSTOM.checkedAt > 1500) {
			CUSTOM.checkedAt = now;
			refresh(CUSTOM, customPath());
		}
		return draw(g, CUSTOM, width, height);
	}

	/** Draws the last world shot. Returns false when no shot exists yet. */
	public static boolean drawLastWorld(GuiGraphics g, int width, int height) {
		long now = Util.getMillis();
		if (now - LAST_WORLD.checkedAt > 3000) {
			LAST_WORLD.checkedAt = now;
			Path p = lastWorldFile();
			refresh(LAST_WORLD, Files.isRegularFile(p) ? p : null);
		}
		return draw(g, LAST_WORLD, width, height);
	}

	private static void refresh(Slot slot, Path path) {
		long modified = -1;
		try {
			if (path != null) modified = Files.getLastModifiedTime(path).toMillis();
		} catch (IOException ignored) {
		}
		if (path == null) {
			if (slot.path != null) clear(slot);
			return;
		}
		if (path.equals(slot.path) && modified == slot.modified) return;
		if (slot.pending != null && !slot.pending.isDone()) return;
		slot.path = path;
		slot.modified = modified;
		slot.error = null;
		Path p = path;
		slot.pending = CompletableFuture.supplyAsync(() -> load(p), Util.backgroundExecutor());
	}

	private static void clear(Slot slot) {
		slot.path = null;
		slot.modified = -1;
		slot.image = null;
		slot.shownFrame = -1;
		if (slot.texture != null) {
			Minecraft.getInstance().getTextureManager().release(slot.id);
			slot.texture = null;
		}
	}

	private static boolean draw(GuiGraphics g, Slot slot, int width, int height) {
		if (slot.pending != null && slot.pending.isDone()) {
			CompletableFuture<Decoded> f = slot.pending;
			slot.pending = null;
			try {
				install(slot, f.join());
			} catch (RuntimeException e) {
				Throwable cause = e.getCause() != null ? e.getCause() : e;
				slot.error = "Could not load " + (slot.path == null ? "image" : slot.path.getFileName()) + ": " + cause.getMessage();
				VantaClient.LOG.warn("[VANTA] {}", slot.error);
			}
		}
		Decoded img = slot.image;
		if (img == null || slot.texture == null) return false;

		// Animated GIF: swap in the current frame (frame 0 with Reduced motion).
		if (img.frames().size() > 1) {
			int frame = 0;
			if (!Theme.reducedMotion() && slot.totalMs > 0) {
				long t = Util.getMillis() % slot.totalMs;
				int[] d = img.delaysMs();
				while (frame < d.length - 1 && t >= d[frame]) t -= d[frame++];
			}
			if (frame != slot.shownFrame) {
				NativeImage pixels = slot.texture.getPixels();
				if (pixels != null) {
					MemoryUtil.memIntBuffer(pixels.getPointer(), img.width() * img.height()).put(img.frames().get(frame));
					slot.texture.upload();
				}
				slot.shownFrame = frame;
			}
		}

		TitleScreenModule c = ModuleRegistry.TITLE_SCREEN;
		boolean motion = c != null && c.imageMotion.on() && !Theme.reducedMotion();
		drawCover(g, slot.id, img.width(), img.height(), 0, 0, width, height, motion);
		return true;
	}

	/** Small preview of the last world shot (for the home screen's Continue card). */
	public static boolean lastWorldThumbnail(GuiGraphics g, int x, int y, int w, int h) {
		long now = Util.getMillis();
		if (now - LAST_WORLD.checkedAt > 3000) {
			LAST_WORLD.checkedAt = now;
			Path p = lastWorldFile();
			refresh(LAST_WORLD, Files.isRegularFile(p) ? p : null);
		}
		if (LAST_WORLD.pending != null && LAST_WORLD.pending.isDone()) draw(g, LAST_WORLD, 0, 0); // installs; draws nothing at 0x0
		Decoded img = LAST_WORLD.image;
		if (img == null || LAST_WORLD.texture == null) return false;
		drawCover(g, LAST_WORLD.id, img.width(), img.height(), x, y, w, h, false);
		return true;
	}

	private static void install(Slot slot, Decoded d) {
		NativeImage image = new NativeImage(d.width(), d.height(), false);
		MemoryUtil.memIntBuffer(image.getPointer(), d.width() * d.height()).put(d.frames().get(0));
		SmoothTexture tex = new SmoothTexture(image);
		Minecraft.getInstance().getTextureManager().register(slot.id, tex); // closes the previous texture
		slot.texture = tex;
		slot.image = d;
		slot.shownFrame = 0;
		long total = 0;
		for (int ms : d.delaysMs()) total += ms;
		slot.totalMs = total;
	}

	/** Scales the image to cover the screen (cropping the overflow), with optional slow pan and zoom. */
	static void drawCover(GuiGraphics g, Identifier id, int tw, int th, int x, int y, int sw, int sh, boolean motion) {
		if (sw <= 0 || sh <= 0) return;
		float screenAspect = sw / (float) Math.max(1, sh);
		float srcW, srcH;
		if (tw / (float) th > screenAspect) {
			srcH = th;
			srcW = th * screenAspect;
		} else {
			srcW = tw;
			srcH = tw / screenAspect;
		}
		float panX = 0.5f, panY = 0.5f;
		if (motion) {
			double t = (Util.getMillis() % 90_000L) / 90_000.0 * Math.PI * 2;
			float zoom = 1.08f + 0.04f * (float) Math.sin(t);
			srcW /= zoom;
			srcH /= zoom;
			panX = 0.5f + 0.35f * (float) Math.sin(t * 1.0);
			panY = 0.5f + 0.25f * (float) Math.cos(t * 1.0);
		}
		int iw = Math.max(1, Math.round(srcW));
		int ih = Math.max(1, Math.round(srcH));
		float u = (tw - iw) * panX;
		float v = (th - ih) * panY;
		g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, sw, sh, iw, ih, tw, th);
	}

	// ------------------------------------------------------------------ decoding (background thread)

	static Decoded load(Path path) {
		try {
			long size = Files.size(path);
			if (size > MAX_FILE_BYTES) throw new IllegalArgumentException("file is larger than 40 MB");
			byte[] bytes = Files.readAllBytes(path);
			if (path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gif")) {
				GifDecoder.Result r = GifDecoder.decode(bytes, GIF_MAX_WIDTH, GIF_MAX_FRAMES, GIF_MAX_PIXELS);
				List<int[]> frames = new ArrayList<>(r.frames().size());
				for (int[] f : r.frames()) frames.add(argbToAbgr(f));
				return new Decoded(r.width(), r.height(), frames, r.delaysMs());
			}
			return loadStill(bytes);
		} catch (IOException e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
	}

	/** PNG / JPG through stb_image (bundled with Minecraft). */
	private static Decoded loadStill(byte[] bytes) {
		ByteBuffer buf = MemoryUtil.memAlloc(bytes.length);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			buf.put(bytes).flip();
			IntBuffer w = stack.mallocInt(1);
			IntBuffer h = stack.mallocInt(1);
			IntBuffer comp = stack.mallocInt(1);
			ByteBuffer pixels = STBImage.stbi_load_from_memory(buf, w, h, comp, 4);
			if (pixels == null) throw new IllegalArgumentException(String.valueOf(STBImage.stbi_failure_reason()));
			try {
				int width = w.get(0), height = h.get(0);
				int[] data = new int[width * height];
				pixels.asIntBuffer().get(data); // RGBA bytes read as little-endian ints = ABGR
				if (pixels.order() != java.nio.ByteOrder.LITTLE_ENDIAN) {
					for (int i = 0; i < data.length; i++) data[i] = Integer.reverseBytes(data[i]);
				}
				int step = 1;
				while (width / step > STILL_MAX_SIDE || height / step > STILL_MAX_SIDE) step++;
				if (step > 1) {
					int nw = width / step, nh = height / step;
					data = argbToAbgr(GifDecoder.scale(abgrToArgb(data), width, height, nw, nh));
					width = nw;
					height = nh;
				}
				return new Decoded(width, height, List.of(data), new int[] {0});
			} finally {
				STBImage.stbi_image_free(pixels);
			}
		} finally {
			MemoryUtil.memFree(buf);
		}
	}

	static int[] argbToAbgr(int[] in) {
		int[] out = new int[in.length];
		for (int i = 0; i < in.length; i++) {
			int c = in[i];
			out[i] = (c & 0xFF00FF00) | ((c & 0xFF) << 16) | ((c >> 16) & 0xFF);
		}
		return out;
	}

	static int[] abgrToArgb(int[] in) {
		return argbToAbgr(in); // swapping red and blue is its own inverse
	}

	// ------------------------------------------------------------------ last world capture

	/** Ask for a shot on the next rendered frame (e.g. when the pause menu opens). */
	public static void requestCapture() {
		captureRequested = true;
	}

	/**
	 * Called right after the world is rendered (before the HUD and menus), so the shot is
	 * a clean view of the world. Only after you pause, and only on fully loaded terrain.
	 */
	public static void afterWorldRendered(Minecraft mc) {
		TitleScreenModule c = ModuleRegistry.TITLE_SCREEN;
		if (c == null || !c.captureWorld.on() || mc.level == null || mc.player == null || capturing) {
			if (mc.level == null) {
				worldSince = 0;
			}
			return;
		}
		long now = Util.getMillis();
		if (worldSince == 0) worldSince = now;
		// Only when you pause: reading the frame back costs a few milliseconds, so it never
		// happens while you're actively playing.
		boolean due = captureRequested && now - lastCapture > 5_000L;
		if (!due || now - worldSince < 8_000L) return;
		if (mc.levelRenderer.countRenderedSections() <= 10 || !mc.levelRenderer.hasRenderedAllSections()) return;
		RenderTarget target = mc.getMainRenderTarget();
		int factor = downscaleFactor(target.width, target.height, 960);
		if (factor <= 0) return;
		captureRequested = false;
		lastCapture = now;
		capturing = true;
		Path file = lastWorldFile();
		try {
			Screenshot.takeScreenshot(target, factor, image -> Util.ioPool().execute(() -> {
				try (image) {
					Path tmp = file.resolveSibling("last_world.png.tmp");
					image.writeToFile(tmp);
					Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				} catch (IOException | RuntimeException e) {
					VantaClient.LOG.debug("[VANTA] Could not save the last world view", e);
				} finally {
					capturing = false;
				}
			}));
		} catch (RuntimeException e) {
			capturing = false;
			VantaClient.LOG.debug("[VANTA] Could not capture the last world view", e);
		}
	}

	/**
	 * A factor that divides both sides (required by Screenshot) and brings the width near
	 * {@code targetWidth}. Returns 0 when no reasonable factor exists.
	 */
	static int downscaleFactor(int w, int h, int targetWidth) {
		if (w <= 0 || h <= 0) return 0;
		int ideal = Math.max(1, (int) Math.ceil(w / (double) targetWidth));
		for (int f = ideal; f <= ideal * 3; f++) if (w % f == 0 && h % f == 0) return f;
		for (int f = ideal - 1; f >= 1; f--) {
			if (w % f == 0 && h % f == 0) return w / f <= targetWidth * 3 ? f : 0;
		}
		return 0;
	}
}
