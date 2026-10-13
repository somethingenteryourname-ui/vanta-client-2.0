package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.util.FormattedCharSequence;

import vanta.chat.ChatFeatures;
import vanta.chat.ChatHeads;
import vanta.chat.ChatTabs;
import vanta.chat.ChatModule;
import vanta.chat.ChatStyle;

/**
 * VANTA chat: the themed panel with the logo watermark, message processing (filters,
 * stacking, timestamps, mentions), longer history and the custom fade time.
 */
@Mixin(ChatComponent.class)
abstract class ChatComponentMixin {
	private static GuiMessage currentMessage;

	@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V", at = @At("HEAD"))
	private void vanta$chatPanel(GuiGraphics graphics, Font font, int ticks, int mouseX, int mouseY, boolean chatting, boolean insertions,
			CallbackInfo ci) {
		ChatStyle.before(graphics, (ChatComponent) (Object) this, ticks, chatting);
	}

	@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V", at = @At("RETURN"))
	private void vanta$chatPanelEnd(GuiGraphics graphics, Font font, int ticks, int mouseX, int mouseY, boolean chatting, boolean insertions,
			CallbackInfo ci) {
		ChatStyle.after(graphics);
	}

	/** The second Double.floatValue() in render is the text background opacity: the VANTA panel replaces those bars. */
	@ModifyExpressionValue(method = "render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V",
			at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 1))
	private float vanta$hideLineBackgrounds(float backgroundOpacity) {
		return ChatModule.styled() ? 0f : backgroundOpacity;
	}

	@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
			at = @At("HEAD"), cancellable = true)
	private void vanta$processMessage(Component contents, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
		if (ChatFeatures.onAdd((ChatComponent) (Object) this, contents, signature, tag)) ci.cancel();
	}

	@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
			at = @At("TAIL"))
	private void vanta$afterMessage(Component contents, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
		ChatFeatures.afterAdd((ChatComponent) (Object) this);
	}

	@Inject(method = "addRecentChat", at = @At("HEAD"))
	private void vanta$sent(String message, CallbackInfo ci) {
		ChatFeatures.onSent(message);
	}

	@ModifyConstant(method = {"addMessageToDisplayQueue", "addMessageToQueue"}, constant = @Constant(intValue = 100))
	private int vanta$history(int original) {
		return ChatModule.historyLimit();
	}

	/** Chat tabs and search: hidden messages stay in history but aren't shown. */
	@Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true)
	private void vanta$filter(GuiMessage message, CallbackInfo ci) {
		if (!ChatTabs.visible(message)) {
			ci.cancel();
			return;
		}
		currentMessage = message;
	}

	/** Player heads: remember which chat line starts which message. */
	@WrapOperation(method = "addMessageToDisplayQueue", at = @At(value = "NEW",
			target = "(ILnet/minecraft/util/FormattedCharSequence;Lnet/minecraft/client/GuiMessageTag;Z)Lnet/minecraft/client/GuiMessage$Line;"))
	private GuiMessage.Line vanta$line(int addedTime, FormattedCharSequence content, GuiMessageTag tag, boolean endOfEntry,
			Operation<GuiMessage.Line> original) {
		GuiMessage.Line line = original.call(addedTime, content, tag, endOfEntry);
		if (currentMessage != null) ChatHeads.onLine(currentMessage, line);
		return line;
	}

	@ModifyExpressionValue(method = "addMessageToDisplayQueue", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/GuiMessage;addedTime()I"))
	private int vanta$fadeTime(int addedTime) {
		return ChatModule.shiftAddedTime(addedTime);
	}
}
