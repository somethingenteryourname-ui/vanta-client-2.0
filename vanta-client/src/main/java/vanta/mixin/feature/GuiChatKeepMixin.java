package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;

import vanta.chat.ChatModule;

/** VANTA chat: "Keep chat when leaving" skips the chat wipe when you leave a world or server. */
@Mixin(Gui.class)
abstract class GuiChatKeepMixin {
	@WrapOperation(method = "onDisconnected", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/ChatComponent;clearMessages(Z)V"))
	private void vanta$keepChat(ChatComponent chat, boolean history, Operation<Void> original) {
		ChatModule m = ChatModule.active();
		if (m != null && m.keepOnLeave.on()) return;
		original.call(chat, history);
	}
}
