package vanta.menu;

import java.lang.ref.WeakReference;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

import vanta.client.VantaClient;
import vanta.modules.ModuleRegistry;

/**
 * Decides when a vanilla {@link TitleScreen} is swapped for the VANTA home screen.
 * Called from {@code Minecraft.setScreen} via mixin, which is the same path vanilla uses
 * on startup, after disconnecting, and when closing a world, so nothing in the normal
 * flow is bypassed.
 */
public final class TitleScreenHook {
	/** The vanilla title screen the player explicitly asked for; it is left alone. */
	private static WeakReference<Screen> allowedVanilla = new WeakReference<>(null);

	private TitleScreenHook() {
	}

	public static Screen maybeReplace(Screen screen) {
		if (screen == null || screen.getClass() != TitleScreen.class) return screen; // exact class only: subclasses from other mods are untouched
		if (allowedVanilla.get() == screen) return screen;
		try {
			if (ModuleRegistry.TITLE_SCREEN == null || !ModuleRegistry.TITLE_SCREEN.isEnabled()) return screen;
			if (net.minecraft.client.Minecraft.getInstance().isDemo()) return screen; // demo mode keeps vanilla's demo buttons
			return new VantaTitleScreen();
		} catch (Throwable t) {
			VantaClient.LOG.error("[VANTA] Could not open the VANTA home screen, falling back to vanilla", t);
			return screen;
		}
	}

	/** Creates a vanilla title screen that VANTA will not replace. */
	public static Screen vanillaTitleScreen() {
		TitleScreen vanilla = new TitleScreen(false);
		allowedVanilla = new WeakReference<>(vanilla);
		return vanilla;
	}
}
