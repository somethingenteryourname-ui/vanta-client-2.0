package vanta.tiers;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Reads a player's tiers from a tier list API answer. Tier lists don't share one format,
 * so this accepts the common shapes:
 * <ul>
 * <li>MCTiers style: {"rankings": {"sword": {"tier": 3, "pos": 0, "retired": false}, ...}}</li>
 * <li>Strings: {"tiers": {"sword": "HT3", "pot": "LT2"}} or {"sword": "HT3"}</li>
 * <li>Lists: {"rankings": [{"gamemode": "sword", "tier": "HT3"}, ...]}</li>
 * </ul>
 */
public final class TierParser {
	/** One ranking. {@code high} = HT, otherwise LT. Lower {@link #score()} is better. */
	public record Tier(String mode, int tier, boolean high, boolean retired) {
		public String label() { return (retired ? "R" : "") + (high ? "HT" : "LT") + tier; }
		public int score() { return tier * 2 + (high ? 0 : 1); }
	}

	private static final Pattern TIER_TEXT = Pattern.compile("^\\s*(R)?\\s*(HT|LT)\\s*([1-5])\\s*$", Pattern.CASE_INSENSITIVE);
	private static final String[] CONTAINERS = {"rankings", "tiers", "ranks", "gamemodes", "modes", "data"};

	private TierParser() {
	}

	public static Map<String, Tier> parse(String json) {
		Map<String, Tier> out = new LinkedHashMap<>();
		JsonElement root;
		try {
			root = JsonParser.parseString(json);
		} catch (RuntimeException e) {
			return out;
		}
		collect(root, out, 0);
		return out;
	}

	private static void collect(JsonElement el, Map<String, Tier> out, int depth) {
		if (el == null || depth > 4) return;
		if (el.isJsonArray()) {
			for (JsonElement e : el.getAsJsonArray()) {
				if (!e.isJsonObject()) continue;
				JsonObject o = e.getAsJsonObject();
				String mode = firstString(o, "gamemode", "mode", "kit", "name", "id");
				Tier t = mode == null ? null : tierFrom(mode, o);
				if (t != null) out.putIfAbsent(t.mode(), t);
			}
			return;
		}
		if (!el.isJsonObject()) return;
		JsonObject o = el.getAsJsonObject();
		if (depth == 0 || depth == 1) {
			for (String c : CONTAINERS) {
				if (o.has(c)) collect(o.get(c), out, depth + 1);
			}
			if (!out.isEmpty()) return;
		}
		for (Map.Entry<String, JsonElement> e : o.entrySet()) {
			JsonElement v = e.getValue();
			Tier t = null;
			if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) t = fromText(e.getKey(), v.getAsString());
			else if (v.isJsonObject()) t = tierFrom(e.getKey(), v.getAsJsonObject());
			if (t != null) out.putIfAbsent(t.mode(), t);
		}
		if (out.isEmpty() && depth < 2) {
			for (Map.Entry<String, JsonElement> e : o.entrySet()) {
				if (e.getValue().isJsonObject() || e.getValue().isJsonArray()) collect(e.getValue(), out, depth + 1);
				if (!out.isEmpty()) return;
			}
		}
	}

	private static Tier tierFrom(String mode, JsonObject o) {
		boolean retired = o.has("retired") && o.get("retired").isJsonPrimitive() && o.get("retired").getAsBoolean();
		JsonElement tier = o.get("tier");
		if (tier == null) tier = o.get("rank");
		if (tier == null) return null;
		if (tier.isJsonPrimitive() && tier.getAsJsonPrimitive().isString()) {
			Tier t = fromText(mode, tier.getAsString());
			return t == null ? null : new Tier(t.mode(), t.tier(), t.high(), t.retired() || retired);
		}
		if (tier.isJsonPrimitive() && tier.getAsJsonPrimitive().isNumber()) {
			int n = tier.getAsInt();
			if (n < 1 || n > 5) return null;
			boolean high;
			if (o.has("pos") && o.get("pos").isJsonPrimitive()) high = o.get("pos").getAsInt() == 0;
			else if (o.has("high")) high = o.get("high").getAsBoolean();
			else if (o.has("position") && o.get("position").isJsonPrimitive() && o.get("position").getAsJsonPrimitive().isString()) {
				high = o.get("position").getAsString().toUpperCase(Locale.ROOT).startsWith("H");
			} else high = true;
			return new Tier(normMode(mode), n, high, retired);
		}
		return null;
	}

	static Tier fromText(String mode, String text) {
		Matcher m = TIER_TEXT.matcher(text);
		if (!m.matches()) return null;
		return new Tier(normMode(mode), Integer.parseInt(m.group(3)), m.group(2).equalsIgnoreCase("HT"), m.group(1) != null);
	}

	private static String firstString(JsonObject o, String... keys) {
		for (String k : keys) {
			JsonElement e = o.get(k);
			if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) return e.getAsString();
		}
		return null;
	}

	/** Puts the many spellings of each game mode under one id. */
	public static String normMode(String raw) {
		String k = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
		return switch (k) {
			case "crystal", "cpvp", "vanilla", "vanillacrystal", "endcrystal" -> "vanilla";
			case "neth", "nethpot", "nethop", "netheritepot", "nethpots" -> "nethpot";
			case "sword", "swords" -> "sword";
			case "pot", "pots", "potion" -> "pot";
			case "smp", "survival" -> "smp";
			case "axe", "axeshield" -> "axe";
			case "uhc", "builduhc" -> "uhc";
			case "mace" -> "mace";
			default -> k;
		};
	}

	public static String modeName(String mode) {
		return switch (mode) {
			case "vanilla" -> "Vanilla";
			case "nethpot" -> "NethPot";
			case "sword" -> "Sword";
			case "pot" -> "Pot";
			case "smp" -> "SMP";
			case "axe" -> "Axe";
			case "uhc" -> "UHC";
			case "mace" -> "Mace";
			default -> mode.isEmpty() ? "?" : Character.toUpperCase(mode.charAt(0)) + mode.substring(1);
		};
	}

	/** Best tier of all modes, or null. */
	public static Tier best(Map<String, Tier> tiers, boolean includeRetired) {
		Tier best = null;
		for (Tier t : tiers.values()) {
			if (t.retired() && !includeRetired) continue;
			if (best == null || t.score() < best.score()) best = t;
		}
		return best;
	}

	/** Colors similar to the ones tier lists use: gold, silver, bronze, then cooler grays. */
	public static int color(Tier t) {
		if (t.retired()) return 0x8A8A8A;
		return switch (t.tier()) {
			case 1 -> t.high() ? 0xFFC93B : 0xE0B451;
			case 2 -> t.high() ? 0xD3DCE8 : 0xA9B3C2;
			case 3 -> t.high() ? 0xF59E5B : 0xC98552;
			case 4 -> t.high() ? 0x8FB0D9 : 0x7A93B5;
			default -> t.high() ? 0xA3ABB5 : 0x858C95;
		};
	}

}
