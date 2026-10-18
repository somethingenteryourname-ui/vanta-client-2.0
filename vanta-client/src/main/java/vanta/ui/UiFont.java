package vanta.ui;

import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import vanta.config.Labeled;

/**
 * Fonts for VANTA's menus and HUD. Minecraft's own fonts plus open-license fonts that ship
 * inside the mod (see assets/vanta/font/licenses). Characters a font doesn't have (symbols,
 * badges) fall back to Minecraft's font automatically.
 */
public enum UiFont implements Labeled {
	MINECRAFT("Minecraft", "The classic blocky game font", null),
	UNIFORM("Minecraft Smooth", "Minecraft's own smooth font (Unifont)", Identifier.withDefaultNamespace("uniform")),
	INTER("Inter", "Clean and modern, easy to read small", vanta("inter")),
	POPPINS("Poppins", "Round and friendly, great with glass looks", vanta("poppins")),
	MONTSERRAT("Montserrat", "Wide and bold, poster-style", vanta("montserrat")),
	NUNITO("Nunito", "Soft rounded letters", vanta("nunito")),
	OUTFIT("Outfit", "Geometric and tidy", vanta("outfit")),
	SPACE_GROTESK("Space Grotesk", "Quirky tech style", vanta("spacegrotesk")),
	JETBRAINS_MONO("JetBrains Mono", "Monospaced, every letter the same width", vanta("jetbrainsmono")),
	RAJDHANI("Rajdhani", "Narrow, squared PvP-client look", vanta("rajdhani")),
	ORBITRON("Orbitron", "Futuristic sci-fi capitals", vanta("orbitron")),
	PRESS_START("Press Start 2P", "Chunky retro arcade pixels", vanta("pressstart2p"));

	private final String label;
	public final String description;
	private final Identifier id;
	private Style style;
	private Style bold;

	UiFont(String label, String description, Identifier id) {
		this.label = label;
		this.description = description;
		this.id = id;
	}

	private static Identifier vanta(String name) {
		return Identifier.fromNamespaceAndPath("vanta", name);
	}

	@Override public String label() { return label; }

	/** True for Minecraft's normal font (text is drawn the vanilla way, no styling needed). */
	public boolean isDefault() { return id == null; }

	public Style style() {
		if (style == null) style = id == null ? Style.EMPTY : Style.EMPTY.withFont(new FontDescription.Resource(id));
		return style;
	}

	public Style bold() {
		if (bold == null) bold = style().withBold(true);
		return bold;
	}
}
