package vanta.mixin.feature;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;

/** Chat: read access to the message lists and scroll position for the VANTA chat panel. */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
	@Accessor("allMessages")
	List<GuiMessage> vanta$allMessages();

	@Accessor("trimmedMessages")
	List<GuiMessage.Line> vanta$trimmedMessages();

	@Accessor("chatScrollbarPos")
	int vanta$scrollPos();
}
