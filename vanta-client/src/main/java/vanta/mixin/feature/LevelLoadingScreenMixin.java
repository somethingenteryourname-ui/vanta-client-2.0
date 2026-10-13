package vanta.mixin.feature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.level.progress.ChunkLoadStatusView;

import vanta.loading.LoadingScreenModule;

/** Loading Screens: the world/terrain loading screen in the VANTA look. */
@Mixin(LevelLoadingScreen.class)
abstract class LevelLoadingScreenMixin {
	@Shadow
	private LevelLoadTracker loadTracker;
	@Shadow
	private float smoothedProgress;
	@Shadow
	private LevelLoadingScreen.Reason reason;

	@Unique
	private long vanta$start;

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void vanta$render(GuiGraphics graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		LoadingScreenModule m = LoadingScreenModule.active();
		if (m == null || (m.portals.on() && reason != LevelLoadingScreen.Reason.OTHER)) return;
		if (vanta$start == 0) vanta$start = System.currentTimeMillis();
		Screen self = (Screen) (Object) this;
		Minecraft mc = Minecraft.getInstance();
		ServerData server = mc.getCurrentServer();
		String status = mc.hasSingleplayerServer() || server == null ? "Loading world" : "Joining " + server.name;
		float progress = loadTracker != null && loadTracker.hasProgress() ? smoothedProgress : -1f;
		int y = LoadingScreenModule.draw(graphics, self.width, self.height, status, progress, vanta$start, true);
		ChunkLoadStatusView view = loadTracker == null ? null : loadTracker.statusView();
		if (m.chunkMap.on() && view != null) {
			LevelLoadingScreen.renderChunks(graphics, self.width / 2, y + view.radius() * 2 + 6, 2, 0, view);
		}
		ci.cancel();
	}
}
