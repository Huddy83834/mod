package com.punishfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/** A flat modern slider with a glowing fill and a bright knob. */
public class FancySlider extends AbstractSliderButton {
    private final double min, max, step;
    private final DoubleConsumer set;
    private final DoubleFunction<String> label;

    public FancySlider(int x, int y, int w, int h, double min, double max, double step, double cur,
                       DoubleConsumer set, DoubleFunction<String> label) {
        super(x, y, w, h, Component.empty(), (cur - min) / (max - min));
        this.min = min;
        this.max = max;
        this.step = step;
        this.set = set;
        this.label = label;
        updateMessage();
    }

    private double val() {
        return Math.round((min + this.value * (max - min)) / step) * step;
    }

    @Override
    protected void updateMessage() {
        if (label != null) setMessage(Component.literal(label.apply(val())));
    }

    @Override
    protected void applyValue() {
        if (set != null) set.accept(val());
        Config.save();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hov = isHoveredOrFocused();
        Ui.card(g, x, y, w, h, hov ? Ui.CARD_HI : Ui.CARD);
        int fillW = (int) (this.value * (w - 4));
        for (int i = 0; i < fillW; i++) {
            float t = i / (float) Math.max(1, w - 4);
            g.fill(x + 2 + i, y + h - 5, x + 3 + i, y + h - 2, Ui.a(0.9f, Ui.mix(Ui.ACCENT, Ui.ACCENT2, t)));
        }
        int kx = x + 2 + fillW;
        g.fill(kx - 2, y + 2, kx + 2, y + h - 2, Ui.a(1f, hov ? 0xFFFFFF : 0xFFE0D0));
        Font f = Minecraft.getInstance().font;
        String s = getMessage().getString();
        g.drawString(f, s, x + (w - f.width(s)) / 2, y + (h - 8) / 2 - 1, Ui.a(1f, 0xFFFFFF), true);
    }
}
