package vanta.cosmetics;

import java.util.Map;
import java.util.UUID;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.phys.Vec3;

import vanta.config.BoolSetting;
import vanta.config.ChoiceSetting;
import vanta.config.IntSetting;
import vanta.modules.Category;
import vanta.modules.Module;

/**
 * Capes and trails. Your cape shows in third person (and on the inventory player);
 * trails puff out behind you while you move. Other VANTA players see yours (and you
 * see theirs) through the VANTA Bridge plugin or a shared presence server.
 */
public final class CosmeticsModule extends Module {
	private static CosmeticsModule instance;

	public final ChoiceSetting<Cosmetics.Cape> cape = setting(new ChoiceSetting<>(key("cape"), "Cape",
			"Your VANTA cape. Turn on Capes in Minecraft's Skin Customization if it doesn't show.", Cosmetics.Cape.VANTA));
	public final ChoiceSetting<Cosmetics.Trail> trail = setting(new ChoiceSetting<>(key("trail"), "Trail",
			"Particles that follow you while you move.", Cosmetics.Trail.NONE));
	public final IntSetting density = setting(new IntSetting(key("density"), "Trail amount", "How many trail particles.", 50, 10, 100, 10, "%"));
	public final BoolSetting trailFirstPerson = setting(new BoolSetting(key("trail_first_person"), "Trail in first person",
			"Also show your own trail in first person (it's behind you, so mostly off screen).", false));
	public final BoolSetting showOthers = setting(new BoolSetting(key("show_others"), "Show other players' cosmetics",
			"Show capes and trails of other VANTA players.", true));
	public final BoolSetting share = setting(new BoolSetting(key("share"), "Share my cosmetics",
			"Let other VANTA players see your cape and trail.", true));

	private int tickCount;
	private String sentKey = "";

	public CosmeticsModule() {
		super("cosmetics", "Cosmetics", "VANTA capes and particle trails. Other VANTA players can see yours too.", Category.VISUALS, true);
		instance = this;
	}

	public static CosmeticsModule active() { return instance != null && instance.isEnabled() ? instance : null; }

	/** Cape and trail ids to tell other players about, or nulls when not sharing. */
	public static String[] shared() {
		CosmeticsModule m = active();
		if (m == null || !m.share.on()) return new String[] {"none", "none"};
		return new String[] {m.cape.get().id(), m.trail.get().id()};
	}

	/** Called whenever what we share may have changed; true when it did. */
	public static boolean sharedChanged() {
		CosmeticsModule m = instance;
		if (m == null) return false;
		String[] s = shared();
		String key = s[0] + "/" + s[1];
		if (key.equals(m.sentKey)) return false;
		m.sentKey = key;
		return true;
	}

	public static void resetSent() {
		if (instance != null) instance.sentKey = "";
	}

	/** Adds the cosmetic cape to a player's skin, if they have one. */
	public static PlayerSkin withCape(PlayerSkin skin, UUID id, boolean self) {
		CosmeticsModule m = active();
		if (m == null || skin == null) return skin;
		Cosmetics.Cape c;
		if (self) c = m.cape.get();
		else {
			if (!m.showOthers.on()) return skin;
			Cosmetics.Look look = Cosmetics.other(id);
			c = look == null ? Cosmetics.Cape.NONE : look.cape();
		}
		if (c == Cosmetics.Cape.NONE) return skin;
		return new PlayerSkin(skin.body(), c.texture(), skin.elytra(), skin.model(), skin.secure());
	}

	@Override
	public void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null || mc.isPaused()) return;
		tickCount++;
		LocalPlayer self = mc.player;
		boolean thirdPerson = mc.options.getCameraType() != CameraType.FIRST_PERSON;
		if (trail.get() != Cosmetics.Trail.NONE && (thirdPerson || trailFirstPerson.on())) spawn(mc, self, trail.get());
		if (showOthers.on() && !Cosmetics.others().isEmpty()) {
			for (Map.Entry<UUID, Cosmetics.Look> e : Cosmetics.others().entrySet()) {
				if (e.getValue().trail() == Cosmetics.Trail.NONE) continue;
				Player p = mc.level.getPlayerByUUID(e.getKey());
				if (p != null && p != self && p.distanceToSqr(self) < 48 * 48 && !p.isInvisible()) spawn(mc, p, e.getValue().trail());
			}
		}
	}

	private void spawn(Minecraft mc, Player p, Cosmetics.Trail t) {
		Vec3 v = p.getDeltaMovement();
		double speed = Math.sqrt(v.x * v.x + v.z * v.z);
		if (speed < 0.03 && p.onGround()) return; // standing still
		RandomSource r = p.getRandom();
		float chance = density.value() / 100f;
		int count = speed > 0.2 ? 2 : 1;
		ParticleOptions opt = t.particle();
		if (opt == null) return;
		for (int i = 0; i < count; i++) {
			if (r.nextFloat() > chance) continue;
			double x = p.getX() - v.x * 1.5 + (r.nextDouble() - 0.5) * 0.4;
			double y = p.getY() + 0.1 + r.nextDouble() * 0.4;
			double z = p.getZ() - v.z * 1.5 + (r.nextDouble() - 0.5) * 0.4;
			double k = t.still() ? 0.0 : 0.02;
			mc.level.addParticle(opt, x, y, z, (r.nextDouble() - 0.5) * k, r.nextDouble() * k, (r.nextDouble() - 0.5) * k);
		}
	}
}
