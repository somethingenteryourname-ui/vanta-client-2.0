package vanta.mixin.feature;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;

import vanta.waypoints.WorldProjection;

/**
 * Waypoints: remembers the exact camera and projection matrices of this frame so on-screen
 * markers line up with the world (including FOV changes, zoom and view bobbing).
 */
@Mixin(LevelRenderer.class)
abstract class LevelRenderMatricesMixin {
	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void vanta$captureMatrices(GraphicsResourceAllocator resourceAllocator, DeltaTracker deltaTracker, boolean renderOutline, Camera camera,
			Matrix4f modelViewMatrix, Matrix4f projectionMatrix, Matrix4f projectionMatrixForCulling, GpuBufferSlice terrainFog, Vector4f fogColor,
			boolean shouldRenderSky, CallbackInfo ci) {
		WorldProjection.capture(camera.position(), modelViewMatrix, projectionMatrix);
	}
}
