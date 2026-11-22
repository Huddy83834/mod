package com.punishfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** A flat, modern button with a neon accent bar and hover glow - nothing like a vanilla button. */
public class FancyButton extends Button {
    private int accent = Ui.ACCENT;

    public FancyButton(int x, int y, int w, int h, Component msg, OnPress onPress) {
        super(x, y, w, h, msg, onPress, DEFAULT_NARRATION);
    }

    public FancyButton accent(int rgb) {
        this.accent = rgb;
        return this;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hov = isHoveredOrFocused() && this.active;
        int base = !this.active ? 0x160C10 : hov ? Ui.CARD_HI : Ui.CARD;
        Ui.card(g, x, y, w, h, base);
        // accent bar on the left
        g.fill(x + 1, y + 1, x + 3, y + h - 1, Ui.a(this.active ? 1f : 0.4f, accent));
        if (hov) {
            g.fill(x + 1, y + 1, x + w - 1, y + 2, Ui.a(0.55f, accent));
            g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, Ui.a(0.3f, accent));
        }
        Font f = Minecraft.getInstance().font;
        String s = getMessage().getString();
        int maxW = w - 10;
        if (f.width(s) > maxW) s = f.plainSubstrByWidth(s, maxW);
        int col = !this.active ? 0x6A5A5E : hov ? 0xFFFFFF : Ui.TEXT;
        g.drawString(f, s, x + (w - f.width(s)) / 2, y + (h - 8) / 2, Ui.a(1f, col), false);
    }
}
