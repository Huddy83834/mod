package com.punishfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/** A flat, modern button with a neon accent - nothing like a vanilla button.
 *  It can also render as a live ON/OFF switch or a checkbox, reading its state
 *  fresh every frame so it always matches reality (no stale colours). */
public class FancyButton extends Button {
    private int accent = Ui.ACCENT;
    private IntSupplier accentSup;      // live accent colour (optional)
    private BooleanSupplier onState;    // present => render as an ON/OFF switch
    private BooleanSupplier selected;   // present => render as a checkbox
    private BooleanSupplier hot;        // present => fill with accent when true (selected tab)

    public FancyButton(int x, int y, int w, int h, Component msg, OnPress onPress) {
        super(x, y, w, h, msg, onPress, DEFAULT_NARRATION);
    }

    public FancyButton accent(int rgb) {
        this.accent = rgb;
        return this;
    }

    public FancyButton accent(IntSupplier rgb) {
        this.accentSup = rgb;
        return this;
    }

    /** Render this as a live ON/OFF switch driven by {@code state}. */
    public FancyButton toggle(BooleanSupplier state) {
        this.onState = state;
        return this;
    }

    /** Render this as a live checkbox driven by {@code sel}. */
    public FancyButton check(BooleanSupplier sel) {
        this.selected = sel;
        return this;
    }

    /** Fill the whole button with the accent when {@code h} is true (a selected tab). */
    public FancyButton hot(BooleanSupplier h) {
        this.hot = h;
        return this;
    }

    private int acc() {
        return accentSup != null ? accentSup.getAsInt() : accent;
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hov = isHoveredOrFocused() && this.active;
        Font f = Minecraft.getInstance().font;
        String s = getMessage().getString();

        if (onState != null) {
            renderToggle(g, f, s, x, y, w, h, hov);
            return;
        }
        if (selected != null) {
            renderCheck(g, f, s, x, y, w, h, hov);
            return;
        }
        renderPlain(g, f, s, x, y, w, h, hov);
    }

    // --- plain button ---------------------------------------------------
    private void renderPlain(GuiGraphics g, Font f, String s, int x, int y, int w, int h, boolean hov) {
        boolean isHot = hot != null && hot.getAsBoolean();
        int base = isHot ? Ui.mix(acc(), 0x000000, 0.35f) : !this.active ? 0x160C10 : hov ? Ui.CARD_HI : Ui.CARD;
        body(g, x, y, w, h, base);
        g.fill(x + 1, y + 1, x + 3, y + h - 1, Ui.a(isHot || this.active ? 1f : 0.4f, acc()));
        if (isHot) {
            g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, Ui.a(0.9f, acc()));
        } else if (hov) {
            g.fill(x + 1, y + 1, x + w - 1, y + 2, Ui.a(0.55f, acc()));
            g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, Ui.a(0.3f, acc()));
        }
        int col = isHot ? 0xFFFFFF : !this.active ? 0x6A5A5E : hov ? 0xFFFFFF : Ui.TEXT;
        text(g, f, s, x + (w - clampW(f, s, w - 10)) / 2, y + (h - 8) / 2, col, w - 10);
    }

    // --- ON/OFF switch --------------------------------------------------
    private void renderToggle(GuiGraphics g, Font f, String s, int x, int y, int w, int h, boolean hov) {
        boolean on = onState.getAsBoolean();
        int onCol = acc() == Ui.ACCENT ? Ui.GOOD : acc();
        int base = on ? Ui.mix(onCol, 0x000000, 0.62f) : (hov ? 0x241820 : 0x1A1216);
        body(g, x, y, w, h, base);
        // bright state strip on the left
        g.fill(x + 1, y + 1, x + 4, y + h - 1, Ui.a(1f, on ? onCol : 0x55454A));
        int textCol = on ? 0xFFFFFF : 0x9A8B8F;
        if (w >= 92) {
            // label left, a real switch on the right
            text(g, f, s, x + 9, y + (h - 8) / 2, textCol, w - 46);
            int sw = 26, sh = Math.min(h - 6, 12);
            Ui.switchKnob(g, x + w - sw - 6, y + (h - sh) / 2, sw, sh, on);
        } else {
            // narrow: the whole chip is the indicator
            String lbl = on ? "ON" : "OFF";
            text(g, f, lbl, x + (w - f.width(lbl)) / 2, y + (h - 8) / 2, textCol, w - 4);
        }
    }

    // --- checkbox (pool cells) -----------------------------------------
    private void renderCheck(GuiGraphics g, Font f, String s, int x, int y, int w, int h, boolean hov) {
        boolean sel = selected.getAsBoolean();
        int acc = acc();
        int base = sel ? Ui.mix(acc, 0x000000, 0.60f) : (hov ? Ui.CARD_HI : 0x1A1216);
        body(g, x, y, w, h, base);
        // checkbox box on the left
        int bs = Math.min(h - 6, 11);
        int bx = x + 4, by = y + (h - bs) / 2;
        g.fill(bx, by, bx + bs, by + bs, Ui.a(1f, sel ? acc : 0x2A2024));
        g.fill(bx, by, bx + bs, by + 1, Ui.a(0.8f, Ui.mix(sel ? acc : 0x4A3A40, 0xFFFFFF, 0.3f)));
        if (sel) {
            // tick
            g.fill(bx + 2, by + bs / 2, bx + 4, by + bs - 2, Ui.a(1f, 0xFFFFFF));
            g.fill(bx + 4, by + bs - 4, bx + bs - 1, by + 2, Ui.a(1f, 0xFFFFFF));
        }
        int textCol = sel ? 0xFFFFFF : (hov ? 0xE8DADF : 0x9A8B8F);
        text(g, f, s, bx + bs + 5, y + (h - 8) / 2, textCol, w - (bs + 11));
    }

    // --- shared helpers -------------------------------------------------
    private void body(GuiGraphics g, int x, int y, int w, int h, int base) {
        g.fillGradient(x, y, x + w, y + h, Ui.a(1f, Ui.mix(base, 0xFFFFFF, 0.08f)), Ui.a(1f, Ui.mix(base, 0x000000, 0.25f)));
        int edge = Ui.a(0.6f, Ui.mix(base, 0x000000, 0.5f));
        g.fill(x, y, x + w, y + 1, edge);
        g.fill(x, y + h - 1, x + w, y + h, edge);
        g.fill(x, y, x + 1, y + h, edge);
        g.fill(x + w - 1, y, x + w, y + h, edge);
    }

    private int clampW(Font f, String s, int maxW) {
        return Math.min(f.width(s), Math.max(0, maxW));
    }

    private void text(GuiGraphics g, Font f, String s, int x, int y, int col, int maxW) {
        if (f.width(s) > maxW && maxW > 0) s = f.plainSubstrByWidth(s, maxW);
        g.drawString(f, s, x, y, Ui.a(1f, col), false);
    }
}
