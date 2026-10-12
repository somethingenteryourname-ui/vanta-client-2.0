package vanta.mixin.feature;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.util.StringDecomposer;

import vanta.modules.impl.NickHiderModule;

/**
 * Nick Hider: every string Minecraft draws passes through StringDecomposer.iterate or
 * iterateFormatted. Swapping your name here hides it in chat, tab list, nametags,
 * scoreboards and menus at once. Only applies when iteration starts at the beginning.
 */
@Mixin(StringDecomposer.class)
abstract class StringDecomposerMixin {
	@ModifyVariable(method = "iterate", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private static String vanta$hideName(String string) {
		return NickHiderModule.filter(string);
	}

	@ModifyVariable(method = "iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
			at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private static String vanta$hideNameFormatted(String string, @Local(argsOnly = true) int offset) {
		return offset == 0 ? NickHiderModule.filter(string) : string;
	}
}
