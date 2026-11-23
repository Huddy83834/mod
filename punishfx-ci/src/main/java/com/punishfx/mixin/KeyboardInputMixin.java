package com.punishfx.mixin;

import com.punishfx.ControlState;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Movement punishments. In 1.21.11 the player's input is a {@link KeyboardInput}, whose tick()
 * reads the keys into {@code keyPresses} and {@code moveVector} WITHOUT calling the base tick(),
 * so we change both right after it runs. LocalPlayer then turns that vector into real movement.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {

    @Inject(method = "tick", at = @At("TAIL"))
    private void punishfx$afterTick(CallbackInfo ci) {
        if (!ControlState.active) return;

        if (ControlState.freeze) {
            this.keyPresses = Input.EMPTY;
            this.moveVector = Vec2.ZERO;
            return;
        }

        Input k = this.keyPresses;
        boolean fwd = k.forward(), back = k.backward(), left = k.left(), right = k.right();
        boolean jump = k.jump(), shift = k.shift(), sprint = k.sprint();
        Vec2 mv = this.moveVector;

        if (ControlState.invert) {
            boolean t = fwd; fwd = back; back = t;
            t = left; left = right; right = t;
            mv = new Vec2(-mv.x, -mv.y);
            sprint = false; // can't sprint backwards
        }

        if (ControlState.mirror) { // Mirror World: left and right swap
            boolean t = left; left = right; right = t;
            mv = new Vec2(-mv.x, mv.y);
        }

        float ang = ControlState.stumble;
        if (ang != 0f && (mv.x != 0f || mv.y != 0f)) {
            float cos = (float) Math.cos(ang), sin = (float) Math.sin(ang);
            mv = new Vec2(mv.x * cos - mv.y * sin, mv.x * sin + mv.y * cos);
        }

        float slow = ControlState.slow;
        if (slow < 0.999f) {
            mv = new Vec2(mv.x * slow, mv.y * slow);
            sprint = false;
        }

        this.keyPresses = new Input(fwd, back, left, right, jump, shift, sprint);
        this.moveVector = mv;
    }
}
