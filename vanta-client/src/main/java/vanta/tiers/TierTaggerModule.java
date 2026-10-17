package vanta.tiers;

import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import vanta.config.BoolSetting;
import vanta.config.ChoiceSetting;
import vanta.config.Labeled;
import vanta.config.TextSetting;
import vanta.modules.Category;
import vanta.modules.Module;

/**
 * Tier Tagger: shows players' PvP tiers (HT1 ... LT5) from PvPTiers or SolaceTiers in front
 * of their name in nametags and the tab list. Looks up the UUIDs of players you see on the
 * chosen tier list's website; answers are cached for 30 minutes.
 */
public final class TierTaggerModule extends Module {
	public enum Source implements Labeled {
		PVPTIERS("PvPTiers"), SOLACETIERS("SolaceTiers"), BOTH("PvPTiers, then SolaceTiers"), MCTIERS("MCTiers");
		private final String label;
		Source(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	public enum Mode implements Labeled {
		HIGHEST("Highest tier", null), VANILLA("Vanilla / Crystal", "vanilla"), SWORD("Sword", "sword"), POT("Pot", "pot"),
		NETHPOT("NethPot", "nethpot"), SMP("SMP", "smp"), AXE("Axe", "axe"), UHC("UHC", "uhc"), MACE("Mace", "mace");
		private final String label;
		final String id;
		Mode(String label, String id) { this.label = label; this.id = id; }
		@Override public String label() { return label; }
	}

	private static TierTaggerModule instance;

	public final ChoiceSetting<Source> source = setting(new ChoiceSetting<>(key("source"), "Tier list", "Which tier list to show.", Source.PVPTIERS));
	public final ChoiceSetting<Mode> mode = setting(new ChoiceSetting<>(key("mode"), "Game mode",
			"Show the tier in one game mode, or each player's best tier.", Mode.HIGHEST));
	public final BoolSetting showMode = setting(new BoolSetting(key("show_mode"), "Show game mode name",
			"With Highest tier, add which mode it's in (e.g. HT3 Sword).", true));
	public final BoolSetting retired = setting(new BoolSetting(key("retired"), "Count retired tiers", "Show retired rankings (marked R).", false));
	public final BoolSetting nametags = setting(new BoolSetting(key("nametags"), "In nametags", "Tier before names above heads.", true));
	public final BoolSetting tabList = setting(new BoolSetting(key("tab_list"), "In tab list", "Tier before names in the player list.", true));
	public final TextSetting pvptiersApi = (TextSetting) setting(new TextSetting(key("pvptiers_api"), "PvPTiers API address",
			"Where PvPTiers answers lookups. Use {uuid}, {uuid_nodash} or {name}; without them /v2/profile/{uuid} is added.",
			"https://pvptiers.com/api", 200)).sensitive();
	public final TextSetting solaceApi = (TextSetting) setting(new TextSetting(key("solace_api"), "SolaceTiers API address",
			"Where SolaceTiers answers lookups. Use {uuid}, {uuid_nodash} or {name}; without them /v2/profile/{uuid} is added.",
			"https://solacetiers.com/api", 200)).sensitive();
	public final TextSetting mctiersApi = (TextSetting) setting(new TextSetting(key("mctiers_api"), "MCTiers API address",
			"MCTiers' public API.", "https://mctiers.com/api", 200)).sensitive();

	public TierTaggerModule() {
		super("tier_tagger", "Tier Tagger", "Shows players' PvPTiers / SolaceTiers tier (HT1-LT5) by their name in nametags and the tab list. "
				+ "Looks up the players you see on that tier list's site.", Category.PVP, false);
		instance = this;
	}

	public static boolean inNametags() { return instance != null && instance.isEnabled() && instance.nametags.on(); }
	public static boolean inTabList() { return instance != null && instance.isEnabled() && instance.tabList.on(); }

	@Override
	protected void onDisable() {
		TierCache.clear();
	}

	/** The tier to show for this player right now, or null (unknown, unranked or still loading). */
	public static TierParser.Tier tierFor(UUID id, String name) {
		if (instance == null || !instance.isEnabled() || id == null) return null;
		return switch (instance.source.get()) {
			case PVPTIERS -> pick(TierCache.get("pvptiers", instance.pvptiersApi.get(), id, name));
			case SOLACETIERS -> pick(TierCache.get("solace", instance.solaceApi.get(), id, name));
			case MCTIERS -> pick(TierCache.get("mctiers", instance.mctiersApi.get(), id, name));
			case BOTH -> {
				TierParser.Tier t = pick(TierCache.get("pvptiers", instance.pvptiersApi.get(), id, name));
				yield t != null ? t : pick(TierCache.get("solace", instance.solaceApi.get(), id, name));
			}
		};
	}

	private static TierParser.Tier pick(TierCache.Entry e) {
		if (e == null || e.state() != TierCache.State.RANKED) return null;
		Mode m = instance.mode.get();
		if (m == Mode.HIGHEST) return TierParser.best(e.tiers(), instance.retired.on());
		TierParser.Tier t = e.tiers().get(m.id);
		return t == null || (t.retired() && !instance.retired.on()) ? null : t;
	}

	/** "HT3 Sword | " + name, or the name unchanged. */
	public static Component decorate(UUID id, String rawName, Component name) {
		TierParser.Tier t = tierFor(id, rawName);
		if (t == null) return name;
		MutableComponent out = Component.empty();
		String text = t.label();
		if (instance.mode.get() == Mode.HIGHEST && instance.showMode.on()) text += " " + TierParser.modeName(t.mode());
		out.append(Component.literal(text).withColor(TierParser.color(t)));
		out.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
		out.append(name);
		return out;
	}

	@Override
	public String status() {
		String problem = TierCache.lastProblem();
		if (!problem.isEmpty()) return problem;
		return TierCache.lookups() == 0 ? "Tiers appear as players come into view" : TierCache.rankedCount() + " ranked players found";
	}
}
