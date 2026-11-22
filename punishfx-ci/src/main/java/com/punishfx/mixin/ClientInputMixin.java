package com.punishfx.mixin;

import com.punishfx.ControlState;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Messes with movement input for the "inverted controls", "frozen", "jumpy legs" and "drunk" punishments. */
@Mixin(ClientInput.class)
public abstract class ClientInputMixin {
    @Shadow public Input keyPresses;
    @Shadow protected Vec2 moveVector;

    @Inject(method = "tick", at = @At("TAIL"))
    private void punishfx$tick(CallbackInfo ci) {
        if (!ControlState.active) return;
        if (ControlState.freeze) {
            this.keyPresses = Input.EMPTY;
            this.moveVector = Vec2.ZERO;
            return;
        }
        Input k = this.keyPresses;
        boolean fwd = k.forward(), back = k.backward(), left = k.left(), right = k.right();
        boolean jump = k.jump() || ControlState.jumpPulse, shift = k.shift(), sprint = k.sprint();
        Vec2 mv = this.moveVector;
        if (ControlState.invert) {
            boolean t = fwd; fwd = back; back = t;
            t = left; left = right; right = t;
            mv = new Vec2(-mv.x, -mv.y);
        }
        float ang = ControlState.stumble;
        if (ang != 0f && (mv.x != 0f || mv.y != 0f)) {
            float cos = (float) Math.cos(ang), sin = (float) Math.sin(ang);
            mv = new Vec2(mv.x * cos - mv.y * sin, mv.x * sin + mv.y * cos);
        }
        this.keyPresses = new Input(fwd, back, left, right, jump, shift, sprint);
        this.moveVector = mv;
    }
}
