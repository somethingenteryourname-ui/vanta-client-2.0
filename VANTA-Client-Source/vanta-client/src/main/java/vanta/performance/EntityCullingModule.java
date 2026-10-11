package vanta.performance;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.entity.BlockEntity;

import vanta.config.IntSetting;
import vanta.modules.Category;
import vanta.modules.Module;

/**
 * Stops drawing far-away clutter: dropped items, XP orbs, armor stands, item frames,
 * mobs, projectiles and block entities (chests, signs, banners, heads) beyond a chosen
 * distance. Players and end crystals are always drawn. Rendering only — nothing about
 * the world or the server changes.
 */
public final class EntityCullingModule extends Module {
	private static EntityCullingModule instance;

	public final IntSetting items = setting(new IntSetting(key("items"), "Dropped items",
			"Hide dropped items farther than this. 128 = never hide.", 48, 8, 128, 4, " blocks"));
	public final IntSetting orbs = setting(new IntSetting(key("orbs"), "XP orbs",
			"Hide experience orbs farther than this. 128 = never hide.", 32, 8, 128, 4, " blocks"));
	public final IntSetting decorations = setting(new IntSetting(key("decorations"), "Armor stands and frames",
			"Hide armor stands, item frames and paintings farther than this. 128 = never hide.", 48, 8, 128, 4, " blocks"));
	public final IntSetting mobs = setting(new IntSetting(key("mobs"), "Mobs",
			"Hide mobs farther than this. 128 = never hide.", 128, 16, 128, 4, " blocks"));
	public final IntSetting projectiles = setting(new IntSetting(key("projectiles"), "Projectiles",
			"Hide arrows, tridents and other projectiles farther than this. 128 = never hide.", 128, 16, 128, 4, " blocks"));
	public final IntSetting blockEntities = setting(new IntSetting(key("block_entities"), "Chests, signs and banners",
			"Hide block entities farther than this. 128 = never hide.", 64, 16, 128, 4, " blocks"));

	private long culledThisSecond;
	private long culledLastSecond;
	private long secondStart;

	public EntityCullingModule() {
		super("entity_culling", "Entity Culling", "Skip drawing far-away items, orbs, armor stands, frames and chests. Players and crystals always show.",
				Category.PERFORMANCE, true);
		instance = this;
	}

	/** True if this entity should not be drawn this frame. */
	public static boolean cull(Entity e, double camX, double camY, double camZ) {
		EntityCullingModule m = instance;
		if (m == null || !m.isEnabled() || e instanceof Player) return false;
		int limit;
		if (e instanceof ItemEntity) limit = m.items.value();
		else if (e instanceof ExperienceOrb) limit = m.orbs.value();
		else if (e instanceof ArmorStand || e instanceof HangingEntity) limit = m.decorations.value();
		else if (e instanceof Mob) limit = m.mobs.value();
		else if (e instanceof Projectile) limit = m.projectiles.value();
		else return false; // end crystals, vehicles, TNT, etc. always render
		if (limit >= 128) return false;
		double dx = e.getX() - camX, dy = e.getY() - camY, dz = e.getZ() - camZ;
		boolean far = dx * dx + dy * dy + dz * dz > (double) limit * limit;
		if (far) m.count();
		return far;
	}

	/** True if this block entity should not be drawn this frame. */
	public static boolean cullBlockEntity(BlockEntity be, double camX, double camY, double camZ) {
		EntityCullingModule m = instance;
		if (m == null || !m.isEnabled()) return false;
		int limit = m.blockEntities.value();
		if (limit >= 128) return false;
		double dx = be.getBlockPos().getX() + 0.5 - camX;
		double dy = be.getBlockPos().getY() + 0.5 - camY;
		double dz = be.getBlockPos().getZ() + 0.5 - camZ;
		boolean far = dx * dx + dy * dy + dz * dz > (double) limit * limit;
		if (far) m.count();
		return far;
	}

	private void count() {
		long now = System.currentTimeMillis();
		if (now - secondStart >= 1000) {
			culledLastSecond = culledThisSecond;
			culledThisSecond = 0;
			secondStart = now;
		}
		culledThisSecond++;
	}

	@Override
	public String status() {
		return culledLastSecond > 0 ? "Skipping ~" + culledLastSecond + " draws/s" : "Active";
	}
}
