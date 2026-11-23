package com.punishfx.mixin;

import com.punishfx.ControlState;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shakes, slides, sways and tilts the render camera (jitter / glitch / drunk). View-only:
 *  it never changes the player entity, so nothing is sent to the server. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void move(float forward, float left, float up);
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow public abstract float yRot();
    @Shadow public abstract float xRot();
    @Shadow public abstract org.joml.Quaternionf rotation();

    @Inject(method = "setup", at = @At("TAIL"))
    private void punishfx$shake(CallbackInfo ci) {
        float shake = ControlState.shake, slide = ControlState.slide, drunk = ControlState.drunk, upside = ControlState.upside;
        if (shake == 0f && slide == 0f && drunk == 0f && upside == 0f) return;

        double t = System.currentTimeMillis();
        float swayY = drunk * (float) (Math.sin(t / 1300.0) * 6.0 + Math.sin(t / 530.0) * 1.5);
        float swayP = drunk * (float) (Math.sin(t / 1700.0) * 4.0);
        float roll = drunk * (float) (Math.sin(t / 950.0) * 10.0 + Math.sin(t / 410.0) * 2.5);

        float dYaw = slide + swayY, dPitch = swayP;
        if (shake != 0f) {
            float m = shake * 0.015f;
            move((float) ((Math.random() - 0.5) * m), (float) ((Math.random() - 0.5) * m), (float) ((Math.random() - 0.5) * m));
            dYaw += (float) ((Math.random() - 0.5) * shake * 2f);
            dPitch += (float) ((Math.random() - 0.5) * shake * 2f);
        }
        if (dYaw != 0f || dPitch != 0f) {
            setRotation(yRot() + dYaw, Math.max(-90f, Math.min(90f, xRot() + dPitch)));
        }
        roll += upside; // Upside Down
        if (roll != 0f) {
            // rotation() is the camera's live quaternion; rolling it tilts the whole view
            rotation().rotateZ((float) Math.toRadians(roll));
        }
    }
}
