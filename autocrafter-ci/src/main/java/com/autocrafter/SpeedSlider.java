package com.autocrafter;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** Drag to choose how fast the crafter works (1 = careful, 10 = as fast as possible). */
public final class SpeedSlider extends AbstractSliderButton {
    public SpeedSlider(int x, int y, int w, int h) {
        super(x, y, w, h, Component.empty(), (Crafter.speed() - 1) / 9.0);
        updateMessage();
    }

    public static String label(int s) {
        String word = s <= 2 ? "careful" : s <= 5 ? "normal" : s <= 7 ? "fast" : s <= 9 ? "super fast" : "MAX";
        return "Speed: " + s + " (" + word + ")";
    }

    private int fromValue() {
        return 1 + (int) Math.round(this.value * 9);
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal(label(fromValue())));
    }

    @Override
    protected void applyValue() {
        int s = fromValue();
        if (s != ClientRecipes.DATA.speed) {
            ClientRecipes.DATA.speed = s;
            Log.add(label(s) + (s >= 9 ? " - some servers kick for clicking this fast." : ""));
        }
    }

    /** Called when the slider is let go: snap to a whole step and save. */
    @Override
    public void onRelease(net.minecraft.client.input.MouseButtonEvent event) {
        super.onRelease(event);
        this.value = (fromValue() - 1) / 9.0;
        updateMessage();
        ClientRecipes.changed();
    }
}
