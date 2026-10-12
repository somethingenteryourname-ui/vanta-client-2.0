package vanta.hud.widgets;

import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.List;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import org.lwjgl.system.MemoryUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;

import vanta.branding.Theme;
import vanta.config.BoolSetting;
import vanta.config.ChoiceSetting;
import vanta.config.IntSetting;
import vanta.config.Labeled;
import vanta.hud.HudModule;
import vanta.modules.Category;
import vanta.ui.Colors;
import vanta.ui.Ui;
import vanta.waypoints.Waypoint;
import vanta.waypoints.WaypointManager;
import vanta.waypoints.WaypointsModule;

/**
 * A terrain minimap like a vanilla map item: the top block of each column, shaded by
 * height. It never shows caves, ores, mobs or players. In dimensions with a ceiling (the
 * Nether) it shows the floor around your own height, i.e. what you could see by looking.
 *
 * Columns are sampled a few rows per tick into a world-aligned cache, so the cost is small
 * and constant; the texture is rebuilt from the cache at most once per tick.
 */
public final class MinimapWidget extends HudModule {
	public enum Zoom implements Labeled {
		FAR("Far (128 blocks)", 128), NORMAL("Normal (64 blocks)", 64), CLOSE("Close (32 blocks)", 32);
		private final String label;
		final int blocks;
		Zoom(String label, int blocks) { this.label = label; this.blocks = blocks; }
		@Override public String label() { return label; }
	}

	private static final int TEX = 192; // blocks covered by the texture (enough for rotation)
	private static final int CACHE = 256; // world-aligned ring cache (power of two)
	private static final int ROWS_PER_TICK = 24;
	private static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath("vanta", "dynamic/minimap");

	private final IntSetting size = setting(new IntSetting(key("size"), "Map size", "Width and height of the map.", 96, 48, 192, 4, "px"));
	private final ChoiceSetting<Zoom> zoom = setting(new ChoiceSetting<>(key("zoom"), "Zoom", "How much area the map covers.", Zoom.NORMAL));
	private final BoolSetting rotate = setting(new BoolSetting(key("rotate"), "Rotate with view",
			"Turn the map so the direction you're facing is up. Off keeps north up.", false));
	private final BoolSetting coords = setting(new BoolSetting(key("coords"), "Show coordinates", "Your X, Y, Z under the map.", true));
	private final BoolSetting showWaypoints = setting(new BoolSetting(key("waypoints"), "Show waypoints",
			"Dots for your waypoints; ones outside the map sit on its edge.", true));
	private final BoolSetting north = setting(new BoolSetting(key("north"), "North marker", "Show an N where north is.", true));

	private final short[] heights = new short[CACHE * CACHE];
	private final byte[] colorIds = new byte[CACHE * CACHE];
	private final byte[] waterDepth = new byte[CACHE * CACHE];
	private final int[] keyX = new int[CACHE * CACHE];
	private final int[] keyZ = new int[CACHE * CACHE];
	private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
	private MapTexture texture;
	private int scanRow;
	private int originX, originZ; // world coords of texture pixel (0,0)
	private boolean dirty;
	private ClientLevel cachedLevel;
	private String cachedDimension;

	/** Dynamic texture whose filtering can switch between crisp (zoomed in) and smooth (zoomed out). */
	private static final class MapTexture extends DynamicTexture {
		private boolean linear;

		MapTexture() {
			super(() -> "VANTA minimap", TEX, TEX, true);
		}

		void filter(boolean wantLinear) {
			if (wantLinear == linear) return;
			linear = wantLinear;
			this.sampler = RenderSystem.getSamplerCache().getClampToEdge(wantLinear ? FilterMode.LINEAR : FilterMode.NEAREST);
		}
	}

	public MinimapWidget() {
		super("minimap", "Minimap", "A terrain map of the area around you, like a held map. Shows terrain only: no caves, mobs or players.",
				Category.HUD, false, 1000, 0);
		clearCache();
	}

	private void clearCache() {
		Arrays.fill(keyX, Integer.MIN_VALUE);
		Arrays.fill(keyZ, Integer.MIN_VALUE);
		dirty = true;
	}

	@Override
	protected void onDisable() {
		if (texture != null) {
			Minecraft.getInstance().getTextureManager().release(TEXTURE_ID);
			texture = null;
		}
	}

	@Override
	public int width() { return size.value() + pad() * 2; }

	@Override
	public int height() { return size.value() + pad() * 2 + (coords.on() ? 11 : 0); }

	@Override
	public void update() {
	}

	// ------------------------------------------------------------------ sampling (client tick)

	@Override
	public void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		LocalPlayer p = mc.player;
		if (level == null || p == null) {
			cachedLevel = null;
			return;
		}
		String dim = level.dimension().identifier().toString();
		if (level != cachedLevel || !dim.equals(cachedDimension)) {
			cachedLevel = level;
			cachedDimension = dim;
			clearCache();
		}
		int px = p.getBlockX(), pz = p.getBlockZ();
		int startX = px - TEX / 2, startZ = pz - TEX / 2;
		boolean ceiling = level.dimensionType().hasCeiling();
		int playerY = p.getBlockY();
		for (int r = 0; r < ROWS_PER_TICK; r++) {
			int z = startZ + scanRow;
			for (int i = 0; i < TEX; i++) sample(level, startX + i, z, ceiling, playerY);
			scanRow = (scanRow + 1) % TEX;
		}
		dirty = true;
	}

	private void sample(ClientLevel level, int x, int z, boolean ceiling, int playerY) {
		int idx = (x & (CACHE - 1)) + (z & (CACHE - 1)) * CACHE;
		if (!level.hasChunk(x >> 4, z >> 4)) {
			keyX[idx] = Integer.MIN_VALUE;
			return;
		}
		int minY = level.getMinY();
		int top;
		if (ceiling) {
			int y = Math.min(playerY + 2, level.getMaxY());
			int budget = 48;
			while (y > minY && budget-- > 0 && !level.getBlockState(pos.set(x, y, z)).isAir()) y--; // out of walls
			while (y > minY && budget-- > 0 && level.getBlockState(pos.set(x, y - 1, z)).isAir()) y--; // down to the floor
			top = y - 1;
		} else {
			top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
		}
		if (top < minY) {
			keyX[idx] = Integer.MIN_VALUE;
			return;
		}
		BlockState state = level.getBlockState(pos.set(x, top, z));
		int depth = 0;
		if (state.getFluidState().is(FluidTags.WATER)) {
			int y = top;
			while (depth < 15 && y > minY && !level.getBlockState(pos.set(x, y - 1, z)).getFluidState().isEmpty()) {
				y--;
				depth++;
			}
		}
		MapColor color = state.getMapColor(level, pos.set(x, top, z));
		heights[idx] = (short) top;
		colorIds[idx] = (byte) color.id;
		waterDepth[idx] = (byte) depth;
		keyX[idx] = x;
		keyZ[idx] = z;
	}

	// ------------------------------------------------------------------ texture

	private void rebuild(int centerX, int centerZ) {
		if (texture == null) {
			texture = new MapTexture();
			Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, texture);
		}
		NativeImage img = texture.getPixels();
		if (img == null) return;
		originX = centerX - TEX / 2;
		originZ = centerZ - TEX / 2;
		IntBuffer out = MemoryUtil.memIntBuffer(img.getPointer(), TEX * TEX);
		for (int j = 0; j < TEX; j++) {
			int wz = originZ + j;
			for (int i = 0; i < TEX; i++) {
				int wx = originX + i;
				out.put(j * TEX + i, abgr(colorAt(wx, wz)));
			}
		}
		texture.upload();
		dirty = false;
	}

	private int colorAt(int x, int z) {
		int idx = (x & (CACHE - 1)) + (z & (CACHE - 1)) * CACHE;
		if (keyX[idx] != x || keyZ[idx] != z) return 0;
		MapColor color = MapColor.byId(colorIds[idx] & 0xFF);
		if (color == MapColor.NONE) return 0;
		MapColor.Brightness b;
		int depth = waterDepth[idx];
		if (depth > 0 || color == MapColor.WATER) {
			b = depth <= 2 ? MapColor.Brightness.HIGH : depth <= 5 ? MapColor.Brightness.NORMAL : MapColor.Brightness.LOW;
		} else {
			int n = (x & (CACHE - 1)) + ((z - 1) & (CACHE - 1)) * CACHE;
			int h = heights[idx];
			int hn = keyX[n] == x && keyZ[n] == z - 1 ? heights[n] : h;
			b = h > hn ? MapColor.Brightness.HIGH : h < hn ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
		}
		return color.calculateARGBColor(b);
	}

	private static int abgr(int argb) {
		return (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF);
	}

	// ------------------------------------------------------------------ drawing

	@Override
	protected void renderContent(GuiGraphics g, boolean editor) {
		Minecraft mc = Minecraft.getInstance();
		int s = size.value();
		int x0 = pad(), y0 = pad();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			Ui.rect(g, x0, y0, s, s, 0x80000000);
			Ui.textCentered(g, "Minimap", x0 + s / 2, y0 + s / 2 - 4, Theme.textDim());
			return;
		}
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 pp = p.getPosition(partial);
		float yaw = p.getViewYRot(partial);
		if (dirty || texture == null || Math.abs(p.getBlockX() - (originX + TEX / 2)) > 8 || Math.abs(p.getBlockZ() - (originZ + TEX / 2)) > 8) {
			rebuild(p.getBlockX(), p.getBlockZ());
		}
		float view = zoom.get().blocks;
		float k = s / view; // pixels per block
		texture.filter(k < 1f);
		float mapAngle = rotate.on() ? (float) Math.toRadians(180f - yaw) : 0f;
		float cx = x0 + s / 2f, cy = y0 + s / 2f;

		Ui.rect(g, x0, y0, s, s, 0xFF101014);
		g.enableScissor(x0, y0, x0 + s, y0 + s);
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		if (mapAngle != 0f) g.pose().rotate(mapAngle);
		g.pose().scale(k);
		g.pose().translate((float) -(pp.x - originX), (float) -(pp.z - originZ));
		g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE_ID, 0, 0, 0f, 0f, TEX, TEX, TEX, TEX);
		g.pose().popMatrix();

		if (showWaypoints.on() && WaypointsModule.active()) drawWaypoints(g, mc, pp, k, mapAngle, cx, cy, s);
		g.disableScissor();

		// you
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().rotate(rotate.on() ? 0f : (float) Math.toRadians(yaw + 180f));
		for (int i = 0; i < 4; i++) g.fill(-i, -3 + i * 2, i + 1, -1 + i * 2, 0xFFFFFFFF);
		g.fill(-3, 4, -1, 5, 0xFFFFFFFF);
		g.fill(2, 4, 4, 5, 0xFFFFFFFF);
		g.pose().popMatrix();

		Ui.roundOutline(g, x0 - 1, y0 - 1, s + 2, s + 2, 1, Colors.fade(Theme.accent(), 0.8f));
		if (north.on()) {
			float nx = 0f, ny = -(s / 2f - 6f);
			if (mapAngle != 0f) {
				float c = (float) Math.cos(mapAngle), sn = (float) Math.sin(mapAngle);
				float rx = nx * c - ny * sn, ry = nx * sn + ny * c;
				nx = rx;
				ny = ry;
			}
			int tx = Math.round(cx + nx), ty = Math.round(cy + ny);
			g.fill(tx - 4, ty - 5, tx + 4, ty + 5, 0xA0000000);
			Ui.textCentered(g, "N", tx + 1, ty - 4, 0xFFF87171);
		}
		if (coords.on()) {
			String c = p.getBlockX() + ", " + p.getBlockY() + ", " + p.getBlockZ();
			drawText(g, Ui.ellipsize(c, s), x0 + (s - Math.min(Ui.width(c), s)) / 2, y0 + s + 3, valueColor());
		}
	}

	private void drawWaypoints(GuiGraphics g, Minecraft mc, Vec3 pp, float k, float angle, float cx, float cy, int s) {
		List<Waypoint> list = WaypointManager.current(mc);
		if (list.isEmpty()) return;
		String dim = WaypointManager.dimension(mc);
		float c = (float) Math.cos(angle), sn = (float) Math.sin(angle);
		float half = s / 2f - 3f;
		for (Waypoint w : list) {
			if (!w.visible || !w.dimension.equals(dim)) continue;
			float dx = (float) (w.x + 0.5 - pp.x) * k;
			float dz = (float) (w.z + 0.5 - pp.z) * k;
			float rx = dx * c - dz * sn, ry = dx * sn + dz * c;
			float m = Math.max(Math.abs(rx), Math.abs(ry));
			if (m > half) { // keep on the edge, pointing the right way
				rx = rx / m * half;
				ry = ry / m * half;
			}
			int x = Math.round(cx + rx), y = Math.round(cy + ry);
			g.fill(x - 2, y - 2, x + 3, y + 3, 0xC0000000);
			g.fill(x - 1, y - 1, x + 2, y + 2, w.color | 0xFF000000);
		}
	}
}
