package vanta.optimize;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;

import vanta.client.VantaClient;
import vanta.config.BoolSetting;
import vanta.modules.Category;
import vanta.modules.Module;

/**
 * The Optimizations tab: PvP responsiveness and smoothness tweaks.
 *
 * Client-side crystals / anchors work like Marlowww's Crystal Optimizer: when your hit or
 * click will certainly destroy the crystal or blow up the anchor, it disappears on your
 * screen right away instead of after your ping. Nothing extra is sent to the server; it
 * only stops you waiting on the round trip. Some servers count this as an advantage, so
 * both are off by default.
 */
public final class OptimizationsModule extends Module {
	private static OptimizationsModule instance;

	// PvP
	public final BoolSetting crystals = setting(new BoolSetting(key("client_crystals"), "Client-side crystals",
			"End crystals vanish the instant you hit them (like Marlowww's Crystal Optimizer) instead of after your ping. "
			+ "Check the server's rules: some don't allow it.", false));
	public final BoolSetting anchors = setting(new BoolSetting(key("client_anchors"), "Client-side anchors",
			"A charged respawn anchor you detonate disappears right away instead of after your ping. Check the server's rules.", false));
	public final BoolSetting staticCrystals = setting(new BoolSetting(key("static_crystals"), "Still end crystals",
			"Crystals stop bobbing and spinning, so they're easier to track and a little cheaper to draw.", false));
	public final BoolSetting noDeathAnimation = setting(new BoolSetting(key("no_death_animation"), "Hide dead entities",
			"Skip the fall-over death animation: dead players and mobs disappear at once, so the fight area is clear.", false));
	public final BoolSetting instantSneak = setting(new BoolSetting(key("instant_sneak"), "Instant sneak camera",
			"The camera drops to sneak height at once instead of sliding down.", false));
	// Smoothness
	public final BoolSetting noMenuBlur = setting(new BoolSetting(key("no_menu_blur"), "No menu blur",
			"Turn off the background blur behind menus (it costs FPS on weaker GPUs).", false));
	public final BoolSetting noViewBobbing = setting(new BoolSetting(key("no_view_bobbing"), "No view bobbing",
			"Stop the camera bobbing while you walk.", false));
	public final BoolSetting noHurtTilt = setting(new BoolSetting(key("no_hurt_tilt"), "No hurt camera shake",
			"Stop the screen tilting when you take damage.", false));
	public final BoolSetting noIdleThrottle = setting(new BoolSetting(key("no_idle_throttle"), "Stop random 30 FPS drops",
			"Minecraft drops to 30 FPS when it thinks you're AFK, and sometimes gets that wrong while you're playing. "
			+ "This keeps full FPS whenever the game window is focused.", true));
	public final BoolSetting freeMemory = setting(new BoolSetting(key("free_memory"), "Free memory when leaving a world",
			"Clean up memory in the background after you leave a world or server, so the next join is smoother.", true));

	private Boolean appliedBlur, appliedBob, appliedTilt;
	private int savedBlur = 5;
	private boolean wasInWorld;

	public OptimizationsModule() {
		super("optimizations", "Optimizations", "PvP and smoothness tweaks: client-side crystals and anchors, still crystals, "
				+ "hidden dead entities, instant sneak, no menu blur and more. Open the Optimizations tab.", Category.PERFORMANCE, true);
		instance = this;
	}

	private static boolean on() { return instance != null && instance.isEnabled(); }
	public static boolean staticCrystals() { return on() && instance.staticCrystals.on(); }
	public static boolean hideDead() { return on() && instance.noDeathAnimation.on(); }
	public static boolean noIdleThrottle() { return on() && instance.noIdleThrottle.on(); }
	public static boolean instantSneak() { return on() && instance.instantSneak.on(); }

	@Override
	public void tick(Minecraft mc) {
		applyOptions(mc, true);
		boolean inWorld = mc.level != null;
		if (wasInWorld && !inWorld && freeMemory.on()) {
			Thread t = new Thread(System::gc, "VANTA memory cleanup");
			t.setDaemon(true);
			t.start();
		}
		wasInWorld = inWorld;
	}

	@Override
	protected void onDisable() {
		applyOptions(Minecraft.getInstance(), false);
	}

	/** Applies the vanilla-option toggles once per change, and puts them back when turned off. */
	@SuppressWarnings("unchecked")
	private void applyOptions(Minecraft mc, boolean enabled) {
		if (mc == null || mc.options == null) return;
		boolean changed = false;
		try {
			boolean blur = enabled && noMenuBlur.on();
			if (appliedBlur == null || appliedBlur != blur) {
				if (blur) {
					int current = (Integer) mc.options.menuBackgroundBlurriness().get();
					if (current > 0) savedBlur = current;
					mc.options.menuBackgroundBlurriness().set(0);
				} else if (appliedBlur != null) {
					mc.options.menuBackgroundBlurriness().set(savedBlur);
				}
				appliedBlur = blur;
				changed = true;
			}
			boolean bob = enabled && noViewBobbing.on();
			if (appliedBob == null || appliedBob != bob) {
				if (bob) mc.options.bobView().set(false);
				else if (appliedBob != null) mc.options.bobView().set(true);
				appliedBob = bob;
				changed = true;
			}
			boolean tilt = enabled && noHurtTilt.on();
			if (appliedTilt == null || appliedTilt != tilt) {
				if (tilt) mc.options.damageTiltStrength().set(0.0);
				else if (appliedTilt != null) mc.options.damageTiltStrength().set(1.0);
				appliedTilt = tilt;
				changed = true;
			}
		} catch (RuntimeException e) {
			VantaClient.LOG.debug("[VANTA] Could not apply an optimization option", e);
		}
		if (changed && (appliedBlur != null)) mc.options.save();
	}

	// ------------------------------------------------------------------ client-side crystals

	/** Called right after you attack an entity. */
	public static void afterAttack(Minecraft mc, Player player, Entity target) {
		if (!on() || !instance.crystals.on() || !(target instanceof EndCrystal) || mc.level == null) return;
		if (player.isSpectator() || target.isInvulnerable() || target.isRemoved()) return;
		// A hit that deals no damage (weakness without a weapon) doesn't break a crystal.
		if (player.hasEffect(MobEffects.WEAKNESS) && player.getAttributeValue(Attributes.ATTACK_DAMAGE) <= 0.0) return;
		mc.level.removeEntity(target.getId(), Entity.RemovalReason.KILLED);
	}

	// ------------------------------------------------------------------ client-side anchors

	/** Before a right click on a block: true if this click will blow up a respawn anchor. */
	public static boolean willDetonate(Minecraft mc, Player player, InteractionHand hand, BlockPos pos) {
		if (!on() || !instance.anchors.on() || mc.level == null || hand != InteractionHand.MAIN_HAND) return false;
		BlockState state = mc.level.getBlockState(pos);
		if (!state.is(Blocks.RESPAWN_ANCHOR)) return false;
		int charge = state.getValue(RespawnAnchorBlock.CHARGE);
		if (charge == 0) return false;
		if (mc.level.dimension() == Level.NETHER) return false; // anchors set your spawn there instead
		ItemStack main = player.getMainHandItem(), off = player.getOffhandItem();
		if (charge < 4 && (main.is(Items.GLOWSTONE) || off.is(Items.GLOWSTONE))) return false; // that click charges it
		if (player.isSecondaryUseActive() && !(main.isEmpty() && off.isEmpty())) return false; // sneaking places the item instead
		return true;
	}

	public static void detonated(Minecraft mc, BlockPos pos) {
		if (mc.level != null) mc.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 11);
	}

	/** Dead living entities other than you are hidden when "Hide dead entities" is on. */
	public static boolean hideIfDead(Minecraft mc, Entity e) {
		return hideDead() && e instanceof LivingEntity living && e != mc.player && living.isDeadOrDying();
	}
}
