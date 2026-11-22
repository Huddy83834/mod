package com.punishfx.mixin;

import com.punishfx.ControlState;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tunnel vision: smoothly narrows the real field of view (never touches your saved FOV setting). */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Unique private static float punishfx$fovMul = 1f;
    @Unique private static long punishfx$lastNanos = 0L;

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void punishfx$fov(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        long now = System.nanoTime();
        float dt = punishfx$lastNanos == 0L ? 0.016f : Math.min(0.1f, (now - punishfx$lastNanos) / 1.0e9f);
        punishfx$lastNanos = now;
        float target = ControlState.fovMul;
        punishfx$fovMul += (target - punishfx$fovMul) * Math.min(1f, dt * 4f);
        if (!useFovSetting) return; // leave the held-item FOV alone
        if (Math.abs(punishfx$fovMul - 1f) < 0.002f) return;
        cir.setReturnValue(cir.getReturnValue() * punishfx$fovMul);
    }
}
