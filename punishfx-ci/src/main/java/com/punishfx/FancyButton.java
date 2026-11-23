package com.punishfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * The one button used everywhere in Punish FX's screens. Depending on how it is configured it
 * draws as a plain button, a selected tab ({@link #hot}), an ON/OFF switch ({@link #toggle}),
 * a whole settings row with a description and a switch ({@link #desc}), or a checkbox cell
 * ({@link #check}). Every state is read fresh each frame, so it always shows the truth.
 */
public class FancyButton extends Button {
    private int accent = Ui.THEME;
    private IntSupplier accentSup;
    private BooleanSupplier onState;
    private BooleanSupplier selected;
    private BooleanSupplier hot;
    private String desc;

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

    /** Draw as a live ON/OFF switch. */
    public FancyButton toggle(BooleanSupplier state) {
        this.onState = state;
        return this;
    }

    /** Draw as a live checkbox. */
    public FancyButton check(BooleanSupplier sel) {
        this.selected = sel;
        return this;
    }

    /** Fill with the accent while true (a selected tab / active mode). */
    public FancyButton hot(BooleanSupplier h) {
        this.hot = h;
        return this;
    }

    /** Draw as a full settings row (label + this description) instead of a button. */
    public FancyButton desc(String d) {
        this.desc = d;
        return this;
    }

    private int rawAccent() {
        return accentSup != null ? accentSup.getAsInt() : accent;
    }

    private int acc() {
        return Ui.resolve(rawAccent());
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hov = isHoveredOrFocused() && this.active;
        Font f = Minecraft.getInstance().font;
        String s = getMessage().getString();

        if (desc != null) renderRow(g, f, s, x, y, w, h, hov);
        else if (onState != null) renderToggle(g, f, s, x, y, w, h, hov);
        else if (selected != null) renderCheck(g, f, s, x, y, w, h, hov);
        else renderPlain(g, f, s, x, y, w, h, hov);
    }

    // --- plain button / tab ---------------------------------------------
    private void renderPlain(GuiGraphics g, Font f, String s, int x, int y, int w, int h, boolean hov) {
        boolean isHot = hot != null && hot.getAsBoolean();
        int base = isHot ? Ui.mix(acc(), 0x000000, 0.35f) : !this.active ? Ui.tint(0x0E0C10, 0.06f) : hov ? Ui.CARD_HI : Ui.CARD;
        body(g, x, y, w, h, base);
        g.fill(x + 1, y + 1, x + 3, y + h - 1, Ui.a(isHot || this.active ? 1f : 0.35f, acc()));
        if (isHot) {
            g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, Ui.a(0.9f, Ui.mix(acc(), 0xFFFFFF, 0.3f)));
        } else if (hov) {
            g.fill(x + 1, y + 1, x + w - 1, y + 2, Ui.a(0.55f, acc()));
            g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, Ui.a(0.3f, acc()));
        }
        int col = isHot ? 0xFFFFFF : !this.active ? 0x5E5054 : hov ? 0xFFFFFF : Ui.TEXT;
        if (s.equals("\u25B6")) { // a crisp drawn "play" triangle (the font glyph is too wide for tiny buttons)
            int th = Math.min(7, h - 6), tx = x + (w - (th / 2 + 1)) / 2 + 1, ty = y + (h - th) / 2;
            for (int i = 0; i < th; i++) {
                int len = Math.min(i, th - 1 - i) + 1;
                g.fill(tx, ty + i, tx + len, ty + i + 1, Ui.a(1f, col));
            }
            return;
        }
        String t = Ui.fit(f, s, w - 8);
        g.drawString(f, t, x + (w - f.width(t)) / 2 + 1, y + (h - 8) / 2, Ui.a(1f, col), false);
    }

    // --- settings row ---------------------------------------------------
    private void renderRow(GuiGraphics g, Font f, String s, int x, int y, int w, int h, boolean hov) {
        boolean on = onState != null && onState.getAsBoolean();
        int bar = onState == null ? acc() : on ? Ui.GOOD : 0x55454A;
        Ui.rowCard(g, f, x, y, w, h, s, desc, hov, bar, onState != null ? 40 : 0);
        if (onState != null) {
            int sw = 28, sh = 12;
            Ui.switchKnob(g, x + w - sw - 8, y + (h - sh) / 2, sw, sh, on);
        }
    }

    // --- ON/OFF switch --------------------------------------------------
    private void renderToggle(GuiGraphics g, Font f, String s, int x, int y, int w, int h, boolean hov) {
        boolean on = onState.getAsBoolean();
        int onCol = rawAccent() == Ui.THEME ? Ui.GOOD : acc();
        int base = on ? Ui.mix(onCol, 0x000000, 0.62f) : (hov ? Ui.tint(0x201C24, 0.1f) : Ui.tint(0x16131A, 0.07f));
        body(g, x, y, w, h, base);
        g.fill(x + 1, y + 1, x + 3, y + h - 1, Ui.a(1f, on ? onCol : 0x55454A));
        int textCol = on ? 0xFFFFFF : 0x9A8B8F;
        int sh = Math.min(h - 6, 12);
        int sw = Math.min(24, w - f.width(s) - 16);
        if (s.isEmpty()) {
            sw = Math.min(24, w - 8);
            Ui.switchKnob(g, x + (w - sw) / 2, y + (h - sh) / 2, sw, sh, on);
        } else if (sw >= 18) {
            g.drawString(f, s, x + 8, y + (h - 8) / 2, Ui.a(1f, textCol), false);
            Ui.switchKnob(g, x + w - sw - 5, y + (h - sh) / 2, sw, sh, on);
        } else {
            String lbl = on ? "ON" : "OFF";
            g.drawString(f, lbl, x + (w - f.width(lbl)) / 2 + 1, y + (h - 8) / 2, Ui.a(1f, textCol), false);
        }
    }

    // --- checkbox cell --------------------------------------------------
    private void renderCheck(GuiGraphics g, Font f, String s, int x, int y, int w, int h, boolean hov) {
        boolean sel = selected.getAsBoolean();
        int acc = acc();
        int base = sel ? Ui.mix(acc, 0x000000, 0.66f) : (hov ? Ui.CARD_HI : Ui.tint(0x141118, 0.06f));
        body(g, x, y, w, h, base);
        int bs = Math.min(h - 6, 9);
        int bx = x + 4, by = y + (h - bs) / 2;
        g.fill(bx, by, bx + bs, by + bs, Ui.a(1f, sel ? acc : 0x2E2428));
        g.fill(bx, by, bx + bs, by + 1, Ui.a(0.8f, Ui.mix(sel ? acc : 0x4A3A40, 0xFFFFFF, 0.3f)));
        if (sel) {
            // tick mark
            g.fill(bx + 1, by + bs / 2, bx + 3, by + bs / 2 + 2, Ui.a(1f, 0xFFFFFF));
            g.fill(bx + 3, by + bs / 2 + 1, bx + 4, by + bs - 1, Ui.a(1f, 0xFFFFFF));
            g.fill(bx + 4, by + 2, bx + 5, by + bs - 2, Ui.a(1f, 0xFFFFFF));
            g.fill(bx + 5, by + 1, bx + bs - 1, by + 3, Ui.a(1f, 0xFFFFFF));
        }
        int textCol = sel ? 0xFFFFFF : (hov ? 0xE8DADF : 0x8E8084);
        Ui.text(g, f, s, bx + bs + 5, y + (h - 8) / 2, textCol, w - (bs + 11));
    }

    // --- shared ---------------------------------------------------------
    private static void body(GuiGraphics g, int x, int y, int w, int h, int base) {
        g.fillGradient(x, y, x + w, y + h, Ui.a(1f, Ui.mix(base, 0xFFFFFF, 0.08f)), Ui.a(1f, Ui.mix(base, 0x000000, 0.25f)));
        int edge = Ui.a(0.6f, Ui.mix(base, 0x000000, 0.5f));
        g.fill(x, y, x + w, y + 1, edge);
        g.fill(x, y + h - 1, x + w, y + h, edge);
        g.fill(x, y, x + 1, y + h, edge);
        g.fill(x + w - 1, y, x + w, y + h, edge);
    }
}
