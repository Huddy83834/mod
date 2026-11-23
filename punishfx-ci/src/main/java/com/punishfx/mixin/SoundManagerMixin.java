package com.punishfx.mixin;

import com.punishfx.ControlState;
import com.punishfx.PunishManager;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Chipmunk (goat mode): sounds get swapped for goat screams. */
@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {
    @Unique private static long punishfx$lastGoat = 0L;

    @ModifyVariable(method = "play", at = @At("HEAD"), argsOnly = true)
    private SoundInstance punishfx$goat(SoundInstance sound) {
        if (!ControlState.goats || sound == null || sound.getSource() == SoundSource.MUSIC) return sound;
        long now = System.currentTimeMillis();
        if (now - punishfx$lastGoat < 160) return sound; // one scream at a time, not a wall of noise
        SoundEvent goat = PunishManager.soundEvent("minecraft:entity.goat.screaming.ambient");
        if (goat == null) return sound;
        punishfx$lastGoat = now;
        return SimpleSoundInstance.forUI(goat, 0.8f + (float) Math.random() * 0.6f, 0.9f);
    }
}
