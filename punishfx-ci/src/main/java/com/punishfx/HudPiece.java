package com.punishfx;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

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

    /** One line in its right-click menu in the layout editor: what it says now, and what clicking does. */
    record Item(Supplier<String> text, Runnable click) {}

    /** Switch it on/off (pieces that can't be hidden ignore this). */
    default void setShown(boolean on) {}

    default boolean canHide() {
        return true;
    }

    /** Its own settings for the right-click menu. */
    default List<Item> menu() {
        return List.of();
    }

    static Item cycle(String label, String[] names, IntSupplier get, IntConsumer set) {
        return new Item(() -> label + ":  " + names[Math.max(0, Math.min(names.length - 1, get.getAsInt()))],
                () -> set.accept((get.getAsInt() + 1) % names.length));
    }

    static Item toggle(String label, BooleanSupplier get, Consumer<Boolean> set) {
        return new Item(() -> label + ":  " + (get.getAsBoolean() ? "on" : "off"), () -> set.accept(!get.getAsBoolean()));
    }

    /** Where it sits on screen right now: {x, y, width, height} (scaled). */
    default int[] bounds(Font font, int sw, int sh, boolean sample) {
        int[] s = size(font, sample);
        int w = Math.round(s[0] * (float) scale()), h = Math.round(s[1] * (float) scale());
        int x = (int) Math.round(Math.max(0, sw - w) * x());
        int y = (int) Math.round(Math.max(0, sh - h) * y());
        return new int[]{x, y, w, h};
    }
}
