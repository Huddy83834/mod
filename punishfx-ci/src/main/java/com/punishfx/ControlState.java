package com.punishfx;

/** Flags the input and camera mixins read every tick/frame. The manager sets them from the active punishments. */
public final class ControlState {
    public static volatile boolean active = false;   // any control/camera punishment is on
    public static volatile boolean invert = false;   // inverted controls
    public static volatile boolean freeze = false;   // can't move
    /** Movement speed multiplier (1 = normal, <1 = sluggish). */
    public static volatile float slow = 1f;
    /** Radians to rotate the movement vector by (drunk stumble). 0 = none. */
    public static volatile float stumble = 0f;

    // ---- camera shake (read by CameraMixin) ----
    /** Jitter shake intensity in degrees (0 = none). */
    public static volatile float shake = 0f;
    /** Glitch horizontal view slide in degrees (signed, swings back and forth). */
    public static volatile float slide = 0f;

    private ControlState() {}

    public static void clear() {
        active = invert = freeze = false;
        slow = 1f;
        stumble = 0f;
        shake = 0f;
        slide = 0f;
    }
}
