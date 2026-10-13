package vanta.cosmetics;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.ClientAsset;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;

import vanta.branding.Theme;
import vanta.config.Labeled;

/**
 * VANTA cosmetics: capes and trails. Your own are always shown to you; other VANTA
 * players' are shown when the server runs the VANTA Bridge plugin or you share a
 * presence server. Only cosmetic ids from this list are ever used, so nobody can make
 * your client load something else.
 */
public final class Cosmetics {
	public enum Cape implements Labeled {
		NONE("None"), VANTA("VANTA"), MIDNIGHT("Midnight"), EMBER("Ember"), OCEAN("Ocean"), SAKURA("Sakura"),
		EMERALD("Emerald"), GOLD("Gold"), MONO("Mono"), GALAXY("Galaxy");
		private final String label;
		private ClientAsset.Texture texture;
		Cape(String label) { this.label = label; }
		@Override public String label() { return label; }

		public String id() { return name().toLowerCase(Locale.ROOT); }

		public ClientAsset.Texture texture() {
			if (this == NONE) return null;
			if (texture == null) {
				texture = new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath("vanta", "cape/" + id()),
						Identifier.fromNamespaceAndPath("vanta", "textures/cape/" + id() + ".png"));
			}
			return texture;
		}
	}

	public enum Trail implements Labeled {
		NONE("None"), HEARTS("Hearts"), FLAMES("Flames"), SOUL("Soul flames"), NOTES("Music notes"), SPARKLES("Sparkles"),
		PORTAL("Portal"), SNOW("Snow"), CHERRY("Cherry petals"), TOTEM("Totem"), ENCHANT("Enchant runes"), GLOW("Glow"),
		ACCENT("Theme color dust");
		private final String label;
		Trail(String label) { this.label = label; }
		@Override public String label() { return label; }

		public String id() { return name().toLowerCase(Locale.ROOT); }

		public ParticleOptions particle() {
			return switch (this) {
				case HEARTS -> ParticleTypes.HEART;
				case FLAMES -> ParticleTypes.FLAME;
				case SOUL -> ParticleTypes.SOUL_FIRE_FLAME;
				case NOTES -> ParticleTypes.NOTE;
				case SPARKLES -> ParticleTypes.END_ROD;
				case PORTAL -> ParticleTypes.PORTAL;
				case SNOW -> ParticleTypes.SNOWFLAKE;
				case CHERRY -> ParticleTypes.CHERRY_LEAVES;
				case TOTEM -> ParticleTypes.TOTEM_OF_UNDYING;
				case ENCHANT -> ParticleTypes.ENCHANT;
				case GLOW -> ParticleTypes.GLOW;
				case ACCENT -> new DustParticleOptions(Theme.accent() & 0xFFFFFF, 1.0f);
				case NONE -> null;
			};
		}

		/** Particles that fly up or around look better with almost no starting speed. */
		boolean still() {
			return this == HEARTS || this == NOTES || this == PORTAL || this == ENCHANT;
		}
	}

	/** Another player's chosen cosmetics. */
	public record Look(Cape cape, Trail trail) {
	}

	private static final Map<UUID, Look> OTHERS = new ConcurrentHashMap<>();

	private Cosmetics() {
	}

	public static Cape cape(String id) {
		if (id != null) for (Cape c : Cape.values()) if (c.id().equals(id)) return c;
		return Cape.NONE;
	}

	public static Trail trail(String id) {
		if (id != null) for (Trail t : Trail.values()) if (t.id().equals(id)) return t;
		return Trail.NONE;
	}

	public static void setOther(UUID id, String cape, String trail) {
		Look l = new Look(cape(cape), trail(trail));
		if (l.cape() == Cape.NONE && l.trail() == Trail.NONE) OTHERS.remove(id);
		else OTHERS.put(id, l);
	}

	public static void clearOthers() { OTHERS.clear(); }

	public static Look other(UUID id) { return OTHERS.get(id); }

	public static Map<UUID, Look> others() { return OTHERS; }
}
