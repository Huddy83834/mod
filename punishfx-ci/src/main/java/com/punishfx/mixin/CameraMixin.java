package com.punishfx.mixin;

import com.punishfx.ControlState;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shakes/slides the render camera for "jitter" and "glitch". View-only: it never touches
 *  the player entity, so no movement or look packets are sent (safe on any server). */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void move(float forward, float left, float up);
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow public abstract float yRot();
    @Shadow public abstract float xRot();

    @Inject(method = "setup", at = @At("TAIL"))
    private void punishfx$shake(CallbackInfo ci) {
        float shake = ControlState.shake;
        float slide = ControlState.slide;
        if (shake == 0f && slide == 0f) return;

        if (shake != 0f) {
            // positional shimmy then a rotational judder
            float m = shake * 0.015f;
            move((float) ((Math.random() - 0.5) * m),
                 (float) ((Math.random() - 0.5) * m),
                 (float) ((Math.random() - 0.5) * m));
            float jy = (float) ((Math.random() - 0.5) * shake * 2f);
            float jp = (float) ((Math.random() - 0.5) * shake * 2f);
            setRotation(yRot() + jy, xRot() + jp);
        }
        if (slide != 0f) {
            // swing the whole view left/right
            setRotation(yRot() + slide, xRot());
        }
    }
}
