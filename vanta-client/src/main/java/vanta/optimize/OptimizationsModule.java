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
import vanta.config.IntSetting;
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
			"Asks Java for a full memory cleanup after you leave a world. This freezes the game for a moment while it runs, "
			+ "and Java normally cleans up well on its own, so leave it off unless the next join is noticeably smoother with it.", false));

	// What the vanilla options were before VANTA changed them, saved so turning a tweak off (even
	// after a restart) puts back your own value instead of a guess. Kept out of profiles.
	private final BoolSetting ownsBlur = hiddenFlag("owns_blur");
	private final IntSetting savedBlur = (IntSetting) setting(new IntSetting(key("saved_blur"), "Saved menu blur", "", 5, 0, 10, 1, ""))
			.hidden().excludeFromSnapshots();
	private final BoolSetting ownsBob = hiddenFlag("owns_bob");
	private final BoolSetting savedBob = (BoolSetting) setting(new BoolSetting(key("saved_bob"), "Saved view bobbing", "", true))
			.hidden().excludeFromSnapshots();
	private final BoolSetting ownsTilt = hiddenFlag("owns_tilt");
	private final IntSetting savedTilt = (IntSetting) setting(new IntSetting(key("saved_tilt"), "Saved hurt tilt", "", 100, 0, 100, 1, "%"))
			.hidden().excludeFromSnapshots();
	private boolean wasInWorld;

	private BoolSetting hiddenFlag(String name) {
		return (BoolSetting) setting(new BoolSetting(key(name), name, "", false)).hidden().excludeFromSnapshots();
	}

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

	/**
	 * Applies the vanilla-option tweaks and puts your own values back when a tweak (or the
	 * module) is turned off. The value before VANTA's change is remembered across restarts.
	 */
	private void applyOptions(Minecraft mc, boolean enabled) {
		if (mc == null || mc.options == null) return;
		boolean changed = false;
		try {
			var blurOpt = mc.options.menuBackgroundBlurriness();
			boolean blur = enabled && noMenuBlur.on();
			if (blur && !ownsBlur.on()) {
				savedBlur.set(blurOpt.get());
				blurOpt.set(0);
				ownsBlur.set(true);
				changed = true;
			} else if (!blur && ownsBlur.on()) {
				blurOpt.set(savedBlur.value());
				ownsBlur.set(false);
				changed = true;
			}

			var bobOpt = mc.options.bobView();
			boolean bob = enabled && noViewBobbing.on();
			if (bob && !ownsBob.on()) {
				savedBob.set(bobOpt.get());
				bobOpt.set(false);
				ownsBob.set(true);
				changed = true;
			} else if (!bob && ownsBob.on()) {
				bobOpt.set(savedBob.on());
				ownsBob.set(false);
				changed = true;
			}

			var tiltOpt = mc.options.damageTiltStrength();
			boolean tilt = enabled && noHurtTilt.on();
			if (tilt && !ownsTilt.on()) {
				savedTilt.set((int) Math.round(tiltOpt.get() * 100));
				tiltOpt.set(0.0);
				ownsTilt.set(true);
				changed = true;
			} else if (!tilt && ownsTilt.on()) {
				tiltOpt.set(savedTilt.value() / 100.0);
				ownsTilt.set(false);
				changed = true;
			}
		} catch (RuntimeException e) {
			VantaClient.LOG.debug("[VANTA] Could not apply an optimization option", e);
		}
		if (changed) mc.options.save();
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
