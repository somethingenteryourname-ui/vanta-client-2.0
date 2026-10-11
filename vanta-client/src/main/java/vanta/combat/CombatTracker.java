package vanta.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Read-only combat stats for the Reach and Combo widgets. It listens to your own attacks
 * (the same event Minecraft fires when you click) and never sends or changes anything.
 */
public final class CombatTracker {
	private static double lastReach = -1;
	private static long lastReachTime;
	private static int combo;
	private static long lastHitTime;
	private static int lastHurtTime;

	private CombatTracker() {
	}

	public static void onAttack(Entity target) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || !(target instanceof LivingEntity)) return;
		Vec3 eye = p.getEyePosition();
		AABB box = target.getBoundingBox();
		double cx = Math.max(box.minX, Math.min(eye.x, box.maxX));
		double cy = Math.max(box.minY, Math.min(eye.y, box.maxY));
		double cz = Math.max(box.minZ, Math.min(eye.z, box.maxZ));
		double dx = eye.x - cx, dy = eye.y - cy, dz = eye.z - cz;
		lastReach = Math.sqrt(dx * dx + dy * dy + dz * dz);
		lastReachTime = System.currentTimeMillis();
		long now = lastReachTime;
		if (now - lastHitTime > 2500) combo = 0;
		combo++;
		lastHitTime = now;
	}

	/** Called every client tick: a combo ends when you take damage or stop hitting. */
	public static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null) {
			combo = 0;
			return;
		}
		if (p.hurtTime > lastHurtTime) combo = 0;
		lastHurtTime = p.hurtTime;
		if (combo > 0 && System.currentTimeMillis() - lastHitTime > 2500) combo = 0;
	}

	public static double lastReach() { return lastReach; }
	public static long lastReachTime() { return lastReachTime; }
	public static int combo() { return combo; }
}
