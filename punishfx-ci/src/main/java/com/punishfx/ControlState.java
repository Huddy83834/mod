package com.punishfx;

/** Flags the input/camera/FOV mixins read. PunishManager sets them every tick from the active punishments. */
public final class ControlState {
    public static volatile boolean active = false;   // any movement punishment is on
    public static volatile boolean invert = false;   // inverted controls
    public static volatile boolean freeze = false;   // can't move
    /** Movement speed multiplier (1 = normal, below 1 = sluggish). */
    public static volatile float slow = 1f;
    /** Radians to rotate the movement vector by (drunk stumble). */
    public static volatile float stumble = 0f;

    // ---- camera (CameraMixin) ----
    public static volatile float shake = 0f;      // jitter intensity (degrees)
    public static volatile float slide = 0f;      // glitch yaw swing (degrees)
    /** Drunk intensity 0..1 (camera sways and tilts; computed per frame in CameraMixin). */
    public static volatile float drunk = 0f;

    // ---- FOV (GameRendererMixin); 1 = normal ----
    public static volatile float fovMul = 1f;

    private ControlState() {}

    public static void clear() {
        active = invert = freeze = false;
        slow = 1f;
        stumble = 0f;
        shake = slide = drunk = 0f;
        fovMul = 1f;
    }
}
