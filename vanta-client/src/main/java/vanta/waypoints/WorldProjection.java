package vanta.waypoints;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import net.minecraft.world.phys.Vec3;

/**
 * Projects world positions to GUI coordinates using the camera and projection matrices
 * captured when this frame's world was rendered, so markers line up exactly with the
 * world (FOV, zoom and view bobbing included).
 */
public final class WorldProjection {
	private static final Matrix4f VIEW_PROJ = new Matrix4f();
	private static final Vector4f TMP = new Vector4f();
	private static double camX, camY, camZ;
	private static boolean valid;

	private WorldProjection() {
	}

	/** Called at the start of world rendering. */
	public static void capture(Vec3 camera, Matrix4f modelView, Matrix4f projection) {
		camX = camera.x;
		camY = camera.y;
		camZ = camera.z;
		projection.mul(modelView, VIEW_PROJ); // VIEW_PROJ = projection * modelView
		valid = true;
	}

	public static void invalidate() { valid = false; }

	public static boolean valid() { return valid; }

	public static double cameraX() { return camX; }
	public static double cameraY() { return camY; }
	public static double cameraZ() { return camZ; }

	/**
	 * Projects a world point. Returns false if it is behind the camera; otherwise writes
	 * GUI x and y to {@code out[0]}, {@code out[1]}.
	 */
	public static boolean project(double x, double y, double z, int guiWidth, int guiHeight, float[] out) {
		if (!valid) return false;
		TMP.set((float) (x - camX), (float) (y - camY), (float) (z - camZ), 1f);
		VIEW_PROJ.transform(TMP);
		if (TMP.w <= 0.05f) return false;
		float nx = TMP.x / TMP.w;
		float ny = TMP.y / TMP.w;
		out[0] = (nx + 1f) * 0.5f * guiWidth;
		out[1] = (1f - ny) * 0.5f * guiHeight;
		return true;
	}
}
