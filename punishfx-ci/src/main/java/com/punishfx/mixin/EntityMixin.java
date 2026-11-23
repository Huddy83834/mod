package com.punishfx.mixin;

import com.punishfx.ControlState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Mirror World: turning the mouse left turns you right (only for you, the local player). */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @ModifyVariable(method = "turn", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double punishfx$mirrorTurn(double yRot) {
        if (ControlState.mirror && (Object) this instanceof LocalPlayer) return -yRot;
        return yRot;
    }
}
