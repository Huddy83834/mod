package com.punishfx.mixin;

import com.punishfx.ControlState;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Chipmunk: every sound is pitched way up. */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Inject(method = "calculatePitch", at = @At("RETURN"), cancellable = true)
    private void punishfx$chipmunk(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (ControlState.chipmunk) cir.setReturnValue(Math.min(3.5f, cir.getReturnValue() * 2.1f));
    }
}
