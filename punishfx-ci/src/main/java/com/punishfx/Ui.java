package com.punishfx;

import net.minecraft.client.gui.GuiGraphics;

/** Shared drawing helpers for Punish FX's custom (non-vanilla) screens. */
public final class Ui {
    public static final int ACCENT = 0xE23C4E;   // neon red
    public static final int ACCENT2 = 0xFF7A3D;  // orange
    public static final int GOOD = 0x3DDC84;
    public static final int WARN = 0xFFAA33;
    public static final int TEXT = 0xE8DADF;
    public static final int DIM = 0x9A8B8F;
    public static final int CARD = 0x201218;
    public static final int CARD_HI = 0x2E1A22;

    private Ui() {}

    public static int a(float alpha, int rgb) {
        int al = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    public static int mix(int c1, int c2, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
        int g = (int) (((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
        int b = (int) ((c1 & 255) * (1 - t) + (c2 & 255) * t);
        return (r << 16) | (g << 8) | b;
    }

    public static int hsv(float h, float s, float v) {
        return java.awt.Color.HSBtoRGB(((h % 1f) + 1f) % 1f, s, v) & 0xFFFFFF;
    }

    /** A small flat card with a soft top-light gradient and a thin dark edge. Corners are nipped for a rounded look. */
    public static void card(GuiGraphics g, int x, int y, int w, int h, int base) {
        g.fillGradient(x, y, x + w, y + h, a(1f, mix(base, 0xFFFFFF, 0.08f)), a(1f, mix(base, 0x000000, 0.25f)));
        // nipped corners
        int bg = 0x00000000;
        g.fill(x, y, x + 1, y + 1, bg);
        g.fill(x + w - 1, y, x + w, y + 1, bg);
        g.fill(x, y + h - 1, x + 1, y + h, bg);
        g.fill(x + w - 1, y + h - 1, x + w, y + h, bg);
        // thin edge
        int edge = a(0.5f, mix(base, 0x000000, 0.5f));
        g.fill(x + 1, y, x + w - 1, y + 1, edge);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, edge);
        g.fill(x, y + 1, x + 1, y + h - 1, edge);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, edge);
    }

    /** The big window frame: dim backdrop, outer glow, gradient body and a glowing accent header strip. */
    public static void panel(GuiGraphics g, int screenW, int screenH, int x, int y, int w, int h, long now) {
        g.fillGradient(0, 0, screenW, screenH, a(0.55f, 0x05020A), a(0.78f, 0x05020A));
        // outer glow
        for (int i = 6; i >= 1; i--) {
            int gc = a(0.05f, ACCENT);
            g.fill(x - i, y - i, x + w + i, y + h + i, gc);
        }
        // body
        g.fillGradient(x, y, x + w, y + h, a(1f, 0x180C12), a(1f, 0x0C0609));
        // moving accent sheen along the top
        int tw = w;
        for (int i = 0; i < tw; i++) {
            float t = i / (float) tw;
            float wave = 0.5f + 0.5f * (float) Math.sin(t * 6.28 - now / 600.0);
            g.fill(x + i, y, x + i + 1, y + 2, a(0.5f + 0.5f * wave, mix(ACCENT, ACCENT2, t)));
        }
        // edges
        g.fill(x, y + h - 1, x + w, y + h, a(0.6f, 0x000000));
        g.fill(x, y, x + 1, y + h, a(0.5f, mix(ACCENT, 0x000000, 0.6f)));
        g.fill(x + w - 1, y, x + w, y + h, a(0.5f, mix(ACCENT, 0x000000, 0.6f)));
    }

    /** Crisp title text: a single tight drop-shadow, no fuzzy halo. */
    public static void glowText(GuiGraphics g, net.minecraft.client.gui.Font f, String s, int x, int y, int color, float scale) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale);
        g.drawString(f, s, 1, 1, a(0.85f, 0x000000), false);
        g.drawString(f, s, 0, 0, a(1f, color), false);
        g.pose().popMatrix();
    }

    /** Draws {@code s}, cut short with "..." if it is wider than {@code maxW}. */
    public static void text(GuiGraphics g, net.minecraft.client.gui.Font f, String s, int x, int y, int rgb, int maxW) {
        g.drawString(f, fit(f, s, maxW), x, y, a(1f, rgb), false);
    }

    public static String fit(net.minecraft.client.gui.Font f, String s, int maxW) {
        if (s == null) return "";
        if (maxW <= 0) return "";
        if (f.width(s) <= maxW) return s;
        String dots = "...";
        String cut = f.plainSubstrByWidth(s, Math.max(0, maxW - f.width(dots)));
        return cut.stripTrailing() + dots;
    }

    /** A small all-caps section heading with a hairline running to the right. */
    public static void section(GuiGraphics g, net.minecraft.client.gui.Font f, String s, int x, int y, int w) {
        String t = s.toUpperCase();
        g.drawString(f, t, x, y, a(1f, 0xB88A92), false);
        int lx = x + f.width(t) + 6;
        if (lx < x + w) g.fill(lx, y + 4, x + w, y + 5, a(0.35f, ACCENT));
    }

    /** The card behind a settings row: label on top, a dim description under it. */
    public static void rowCard(GuiGraphics g, net.minecraft.client.gui.Font f, int x, int y, int w, int h,
                               String label, String desc, boolean hovered, int bar, int rightReserve) {
        int base = hovered ? CARD_HI : CARD;
        g.fillGradient(x, y, x + w, y + h, a(1f, mix(base, 0xFFFFFF, 0.06f)), a(1f, mix(base, 0x000000, 0.2f)));
        g.fill(x, y, x + w, y + 1, a(0.5f, mix(base, 0xFFFFFF, 0.15f)));
        g.fill(x, y + h - 1, x + w, y + h, a(0.6f, 0x000000));
        g.fill(x, y + 1, x + 2, y + h - 1, a(1f, bar));
        int maxW = w - 12 - rightReserve;
        boolean twoLines = desc != null && !desc.isEmpty() && h >= 22;
        if (twoLines) {
            int ty = y + (h - 19) / 2;
            text(g, f, label, x + 8, ty, hovered ? 0xFFFFFF : TEXT, maxW);
            text(g, f, desc, x + 8, ty + 10, 0x8C7E82, maxW);
        } else {
            text(g, f, label, x + 8, y + (h - 8) / 2, hovered ? 0xFFFFFF : TEXT, maxW);
        }
    }

    /** A pill-shaped mini switch: filled track + knob that sits right when on, left when off. */
    public static void switchKnob(GuiGraphics g, int x, int y, int w, int h, boolean on) {
        int track = on ? GOOD : 0x4A3A40;
        g.fill(x, y + 1, x + w, y + h - 1, a(1f, mix(track, 0x000000, 0.25f)));
        g.fill(x, y, x + w, y + 1, a(0.8f, mix(track, 0xFFFFFF, 0.2f)));
        int kw = h - 2;
        int kx = on ? x + w - kw - 1 : x + 1;
        g.fill(kx, y + 1, kx + kw, y + h - 1, a(1f, on ? 0xFFFFFF : 0xBFB2B6));
    }
}
