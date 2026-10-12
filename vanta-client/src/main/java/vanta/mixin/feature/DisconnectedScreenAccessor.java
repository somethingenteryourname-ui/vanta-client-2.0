package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.network.DisconnectionDetails;

/** Auto Reconnect reads the disconnect reason so it never retries after a ban. */
@Mixin(DisconnectedScreen.class)
public interface DisconnectedScreenAccessor {
	@Accessor("details")
	DisconnectionDetails vanta$details();
}
