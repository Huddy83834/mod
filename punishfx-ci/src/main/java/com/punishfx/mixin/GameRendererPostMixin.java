package com.punishfx.mixin;

import com.punishfx.ScreenFx;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Right after the world is drawn (before the HUD), run Punish FX's screen effects one after another. */
@Mixin(GameRenderer.class)
public abstract class GameRendererPostMixin {
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void punishfx$screenEffects(DeltaTracker delta, CallbackInfo ci) {
        ScreenFx.process(Minecraft.getInstance(), ((GameRendererAccessor) this).punishfx$resourcePool());
    }
}
