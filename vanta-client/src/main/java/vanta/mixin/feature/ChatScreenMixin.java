package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import vanta.chat.ChatScreenExtras;
import vanta.chat.ChatStyle;

/** VANTA chat: themed input box, tabs, emoji picker and shortcodes, right-click copy and Ctrl+F search. */
@Mixin(ChatScreen.class)
abstract class ChatScreenMixin {
	@Shadow
	protected EditBox input;

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", ordinal = 0))
	private void vanta$inputBox(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color, Operation<Void> original) {
		if (!ChatStyle.inputBox(graphics, x0, y0, x1, y1)) original.call(graphics, x0, y0, x1, y1, color);
	}

	@Inject(method = "render", at = @At("HEAD"))
	private void vanta$emojiTyping(GuiGraphics graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		ChatScreenExtras.liveEmoji(input);
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void vanta$extras(GuiGraphics graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		Screen self = (Screen) (Object) this;
		ChatScreenExtras.render(graphics, input, self.width, self.height, mouseX, mouseY);
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void vanta$click(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		Screen self = (Screen) (Object) this;
		if (ChatScreenExtras.click(Minecraft.getInstance(), input, self.width, self.height, event.x(), event.y(), event.button())) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void vanta$key(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (ChatScreenExtras.key(input, event.key(), event.modifiers())) cir.setReturnValue(true);
	}

	@Inject(method = "removed", at = @At("HEAD"))
	private void vanta$closed(CallbackInfo ci) {
		ChatScreenExtras.onClosed();
	}

	@ModifyVariable(method = "handleChatInput", at = @At("HEAD"), argsOnly = true)
	private String vanta$emojiSend(String message) {
		return ChatScreenExtras.onSend(message);
	}
}
