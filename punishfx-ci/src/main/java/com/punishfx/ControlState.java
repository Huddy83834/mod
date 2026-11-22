package com.punishfx;

/** Flags the control mixin reads every input tick. The manager sets them from the active punishments. */
public final class ControlState {
    public static volatile boolean active = false;
    public static volatile boolean invert = false;
    public static volatile boolean freeze = false;
    public static volatile boolean jumpPulse = false;
    /** Radians to rotate the movement vector by (drunk stumble). 0 = none. */
    public static volatile float stumble = 0f;

    private ControlState() {}

    public static void clear() {
        active = invert = freeze = jumpPulse = false;
        stumble = 0f;
    }
}
