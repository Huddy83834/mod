package com.punishfx;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Something on the HUD that you can drag around and resize in the {@link MoveScreen} editor. */
public interface HudPiece {
    String name();

    /** Size at scale 1, in GUI pixels: {width, height}. {@code sample} = fill with example data if empty. */
    int[] size(Font font, boolean sample);

    void drawAt(GuiGraphics g, Font font, float x, float y, float scale, boolean sample);

    double x();

    double y();

    double scale();

    void set(double x, double y, double scale);

    /** Whether it's switched on to show while playing. */
    boolean shown();

    String showSettingName();

    void reset();

    /** Where it sits on screen right now: {x, y, width, height} (scaled). */
    default int[] bounds(Font font, int sw, int sh, boolean sample) {
        int[] s = size(font, sample);
        int w = Math.round(s[0] * (float) scale()), h = Math.round(s[1] * (float) scale());
        int x = (int) Math.round(Math.max(0, sw - w) * x());
        int y = (int) Math.round(Math.max(0, sh - h) * y());
        return new int[]{x, y, w, h};
    }
}
