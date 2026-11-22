package com.punishfx;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Random;

/** Draws the on-screen punishments, the banner naming them, and the leave/close countdown. */
public final class PunishOverlay {
    private PunishOverlay() {}

    static int A(float a, int rgb) {
        return Ui.a(a, rgb);
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        long now = System.currentTimeMillis();
        Font font = mc.font;

        for (PunishManager.Active a : PunishManager.active()) {
            float p = a.progress();
            float vis = Math.min(1f, Math.min((now - a.start) / 220f, (a.end - now) / 450f));
            if (vis <= 0) continue;
            try {
                drawOne(g, font, a.p.id, w, h, now, vis, p);
            } catch (Exception ignored) {
            }
        }
        donorBanner(g, font, w, now);
        banner(g, font, w, now);
        countdown(g, font, w, h, now);
    }

    /** "<player> paid you $X" + the punishment names, shown for a few seconds. */
    private static void donorBanner(GuiGraphics g, Font font, int w, long now) {
        if (now >= PunishManager.donorUntil || PunishManager.donor.isEmpty()) return;
        long left = PunishManager.donorUntil - now;
        float vis = Math.min(1f, Math.min(left / 400f, (now - (PunishManager.donorUntil - 3400)) / 160f));
        if (vis <= 0) return;

        String who = PunishManager.donor;
        String amt = PunishManager.donorAmount;
        String sub = PunishManager.donorSub;

        float scale = 1.7f;
        String head = who + " paid you";
        int headW = (int) (font.width(head + " ") * scale);
        int amtW = (int) (font.width(amt) * (scale + 0.5f));
        int lineW = headW + amtW;
        int subW = font.width(sub);
        int bw = Math.max(lineW, subW) + 40;
        int x = w / 2 - bw / 2;
        int y = 12;
        int bh = 52;

        // backing card
        g.fillGradient(x, y, x + bw, y + bh, A(0.88f * vis, 0x1C0609), A(0.88f * vis, 0x0A0205));
        g.fill(x, y, x + bw, y + 2, A(vis, Ui.ACCENT));
        g.fill(x, y + bh - 2, x + bw, y + bh, A(0.7f * vis, Ui.ACCENT2));
        g.fill(x, y, x + 2, y + bh, A(vis, Ui.ACCENT));
        g.fill(x + bw - 2, y, x + bw, y + bh, A(vis, Ui.ACCENT));
        // coin pulse dot
        float pulse = 0.6f + 0.4f * (float) Math.sin(now / 160.0);
        disc(g, x + 16, y + 17, 6, A(vis, 0xFFD24A));
        disc(g, x + 16, y + 17, 6f + pulse * 2, A(0.3f * vis, 0xFFD24A));
        g.drawString(font, "$", x + 14, y + 12, A(vis, 0x5A3A00), false);

        // "<who> paid you  $AMOUNT"
        int tx = x + 30;
        Ui.glowText(g, font, head, tx, y + 10, 0xFFCDD2, scale);
        Ui.glowText(g, font, amt, tx + headW, y + 7, 0x6BFF9E, scale + 0.5f);
        // punishment names
        g.drawString(font, sub, x + 30, y + bh - 16, A(0.95f * vis, 0xE8C8CC), false);
    }

    // ------------------------------------------------------------------ one punishment

    public static void drawOne(GuiGraphics g, Font font, String id, int w, int h, long now, float vis, float p) {
        switch (id) {
            case "negative" -> negative(g, w, h, now, vis);
            case "disco_eyes" -> disco(g, w, h, now, vis);
            case "tunnel_vision" -> tunnel(g, w, h, now, vis);
            case "flashbang" -> flashbang(g, w, h, vis, p);
            case "blackout" -> blackout(g, font, w, h, now, vis);
            case "fog" -> fog(g, w, h, now, vis);
            default -> { }
        }
    }

    // ------------------------------------------------------------------ primitives

    private static void rect(GuiGraphics g, float cx, float cy, float w, float h, float rot, int argb) {
        if (w <= 0.2f || h <= 0.2f || (argb >>> 24) == 0) return;
        var p = g.pose();
        p.pushMatrix();
        p.translate(cx, cy);
        if (rot != 0) p.rotate(rot);
        p.scale(w / 2f, h / 2f);
        g.fill(-1, -1, 1, 1, argb);
        p.popMatrix();
    }

    private static void disc(GuiGraphics g, float cx, float cy, float r, int argb) {
        if (r < 0.5f || (argb >>> 24) == 0) return;
        for (float dy = -r; dy <= r; dy++) {
            float hw = (float) Math.sqrt(Math.max(0, r * r - dy * dy));
            g.fill((int) (cx - hw), (int) (cy + dy), (int) (cx + hw), (int) (cy + dy) + 1, argb);
        }
    }

    private static void glow(GuiGraphics g, float cx, float cy, float r, int rgb, float a) {
        if (a <= 0.004f || r < 1) return;
        for (int i = 4; i >= 0; i--) disc(g, cx, cy, r * (0.35f + i * 0.2f), A(a * 0.2f, rgb));
    }

    private static void line(GuiGraphics g, float x1, float y1, float x2, float y2, float th, int argb) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) return;
        rect(g, (x1 + x2) / 2, (y1 + y2) / 2, len, th, (float) Math.atan2(dy, dx), argb);
    }

    /** A smooth radial darkening by stacking translucent frames inward. hole = clear radius fraction. */
    private static void radialDark(GuiGraphics g, int w, int h, float holeFrac, float strength, int col) {
        int steps = 22;
        float cx = w / 2f, cy = h / 2f;
        float maxR = (float) Math.hypot(w, h) / 2f;
        float hole = maxR * holeFrac;
        for (int i = 0; i < steps; i++) {
            float f = i / (float) steps;
            float inner = hole + (maxR - hole) * f;
            float a = strength * f * f;
            // draw a ring band as four rects around a shrinking clear box
            int bx = (int) (cx - inner * (w / (float) Math.hypot(w, h)) * 1.42f);
            // simpler: draw full translucent layer with a transparent centered box via 4 bands
            int boxHalfW = (int) (inner * 0.82f);
            int boxHalfH = (int) (inner * 0.82f);
            int argb = A(a, col);
            g.fill(0, 0, w, Math.max(0, (int) cy - boxHalfH), argb);
            g.fill(0, (int) cy + boxHalfH, w, h, argb);
            g.fill(0, Math.max(0, (int) cy - boxHalfH), Math.max(0, (int) cx - boxHalfW), Math.min(h, (int) cy + boxHalfH), argb);
            g.fill(Math.min(w, (int) cx + boxHalfW), Math.max(0, (int) cy - boxHalfH), w, Math.min(h, (int) cy + boxHalfH), argb);
        }
    }

    private static void vignette(GuiGraphics g, int w, int h, int col, float a) {
        int band = h / 4;
        g.fillGradient(0, 0, w, band, A(a, col), A(0, col));
        g.fillGradient(0, h - band, w, h, A(0, col), A(a, col));
        int strips = 10, sw = Math.max(1, w / 36);
        for (int i = 0; i < strips; i++) {
            int c = A(a * (1 - i / (float) strips), col);
            g.fill(i * sw, 0, (i + 1) * sw, h, c);
            g.fill(w - (i + 1) * sw, 0, w - i * sw, h, c);
        }
    }

    // ------------------------------------------------------------------ the punishments

    private static void negative(GuiGraphics g, int w, int h, long now, float vis) {
        // "colours inverted / broken signal": the whole screen pushed to a wrong, shifting complementary hue
        boolean flip = (now / 650) % 2 == 0;          // flips which complementary dominates
        float fl = ((now / 55) % 13) < 11 ? 1f : 0.45f; // occasional dropout flicker
        int hueA = flip ? 0x00E6C8 : 0xE0109A;        // teal <-> magenta
        int hueB = flip ? 0xE0109A : 0x00E6C8;
        g.fill(0, 0, w, h, A(0.52f * vis * fl, hueA));
        g.fillGradient(0, 0, w, h, A(0.30f * vis, hueB), A(0.42f * vis, 0x3010C0));
        g.fill(0, 0, w, h, A(0.14f * vis, 0xFFF000));
        // bold chromatic aberration: red ghost shoved one way, cyan the other
        int ca = (int) (5 + 3 * Math.sin(now / 230.0));
        for (int y = 0; y < h; y += 3) {
            g.fill(ca, y, w, y + 2, A(0.16f * vis, 0xFF0030));
            g.fill(0, y + 1, w - ca, y + 3, A(0.16f * vis, 0x00C8FF));
        }
        // bright rolling sync bar
        int by = (int) ((now / 5) % (h + 60)) - 30;
        g.fillGradient(0, by, w, by + 26, A(0, 0xFFFFFF), A(0.28f * vis, 0xFFFFFF));
        g.fillGradient(0, by + 26, w, by + 52, A(0.28f * vis, 0xFFFFFF), A(0, 0xFFFFFF));
        for (int y = 0; y < h; y += 3) g.fill(0, y, w, y + 1, A(0.12f * vis, 0x000000));
        vignette(g, w, h, flip ? 0x100030 : 0x300010, 0.4f * vis);
    }

    private static void flashbang(GuiGraphics g, int w, int h, float vis, float p) {
        float fa = p < 0.1f ? 1f : Math.max(0.12f, 1f - (p - 0.1f) * 1.5f);
        g.fill(0, 0, w, h, A(fa * vis, 0xFFFFFF));
        // expanding after-image bloom ring + chromatic fringe as it fades
        if (p > 0.1f && p < 0.6f) {
            float r = (p - 0.1f) / 0.5f * Math.max(w, h) * 0.6f;
            float ra = (1 - (p - 0.1f) / 0.5f) * 0.5f * vis;
            glow(g, w / 2f, h / 2f, r, 0xFFFFFF, ra);
            glow(g, w / 2f - 6, h / 2f, r * 0.9f, 0xFF4040, ra * 0.4f);
            glow(g, w / 2f + 6, h / 2f, r * 0.9f, 0x4080FF, ra * 0.4f);
        }
    }

    private static void blackout(GuiGraphics g, Font font, int w, int h, long now, float vis) {
        g.fill(0, 0, w, h, A(0.985f * vis, 0x000000));
        // faint moving grain
        Random r = new Random(now / 120);
        for (int i = 0; i < 30; i++) {
            int x = r.nextInt(w), y = r.nextInt(h);
            g.fill(x, y, x + 1, y + 1, A(0.06f * vis, 0x404040));
        }
        float blink = ((now / 500) % 2 == 0) ? 1f : 0.3f;
        String s = "— SIGNAL LOST —";
        g.drawString(font, s, w / 2 - font.width(s) / 2, h - 24, A(0.5f * vis * blink, 0xFF4444), false);
    }

    private static void fog(GuiGraphics g, int w, int h, long now, float vis) {
        g.fill(0, 0, w, h, A(0.32f * vis, 0x8E949C));
        for (int i = 0; i < 6; i++) {
            float t = now / 1000f;
            float x = (float) ((0.5 + 0.5 * Math.sin(t * (0.2 + i * 0.05) + i * 1.7)) * w);
            float y = (float) ((0.5 + 0.4 * Math.cos(t * (0.15 + i * 0.04) + i)) * h);
            glow(g, x, y, 120 + i * 20, 0xC8CED6, 0.07f * vis);
        }
        vignette(g, w, h, 0x202428, 0.45f * vis);
    }

    private static void sick(GuiGraphics g, int w, int h, long now, float vis) {
        // woozy green haze with moving wavy bands + queasy vignette
        for (int y = 0; y < h; y += 6) {
            float wv = (float) Math.sin(y * 0.05 + now / 300.0);
            float a = 0.10f + 0.07f * wv;
            g.fill(0, y, w, y + 6, A(a * vis, 0x4FB36A));
        }
        g.fill(0, 0, w, h, A(0.08f * vis, 0x2E7D32));
        // drifting blobs
        for (int i = 0; i < 4; i++) {
            float x = (float) ((0.5 + 0.45 * Math.sin(now / 700.0 + i * 2)) * w);
            float y = (float) ((0.5 + 0.45 * Math.cos(now / 820.0 + i * 2)) * h);
            glow(g, x, y, 60, 0x6FCF8A, 0.06f * vis);
        }
        vignette(g, w, h, 0x1B3B22, 0.4f * vis);
    }

    private static void tunnel(GuiGraphics g, int w, int h, long now, float vis) {
        float hole = 0.14f + 0.05f * (float) Math.sin(now / 420.0);
        radialDark(g, w, h, hole, 0.9f * vis, 0x000000);
    }

    private static void censor(GuiGraphics g, Font font, int w, int h, long now, float vis) {
        Random r = new Random(12345);
        // pixel-mosaic patches
        for (int b = 0; b < 3; b++) {
            int mx = (int) (r.nextDouble() * w * 0.6), my = (int) (r.nextDouble() * h * 0.7);
            int mw = 60 + r.nextInt(120), mh = 40 + r.nextInt(70);
            int cell = 10;
            Random cr = new Random(b * 7 + now / 300);
            for (int y = my; y < my + mh && y < h; y += cell)
                for (int x = mx; x < mx + mw && x < w; x += cell) {
                    int v = 30 + cr.nextInt(120);
                    g.fill(x, y, x + cell, y + cell, A(0.9f * vis, (v << 16) | (v << 8) | v));
                }
        }
        // black bars
        for (int i = 0; i < 4; i++) {
            int y = (int) ((0.14 + 0.72 * r.nextDouble()) * h) + (int) (Math.sin(now / 250.0 + i) * 3);
            int bh = 16 + r.nextInt(16);
            int x = (int) (r.nextDouble() * w * 0.25);
            int bw = (int) (w * (0.45 + r.nextDouble() * 0.55));
            g.fill(x, y, x + bw, y + bh, A(vis, 0x000000));
            g.fill(x, y, x + bw, y + 1, A(vis, 0xFFCC00));
            g.fill(x, y + bh - 1, x + bw, y + bh, A(vis, 0xFFCC00));
            String t = "CENSORED";
            g.drawString(font, t, x + bw / 2 - font.width(t) / 2, y + bh / 2 - 4, A(vis, 0xFFFFFF), false);
        }
    }

    private static void cracks(GuiGraphics g, int w, int h, float vis) {
        Random r = new Random(777);
        float cx = w * 0.46f, cy = h * 0.42f;
        // frosted shatter zone + dark bruise + hot white impact core
        glow(g, cx, cy, 34, 0x0A0A0E, 0.45f * vis);
        disc(g, cx, cy, 16, A(0.12f * vis, 0xE8ECF4));
        glow(g, cx, cy, 12, 0xFFFFFF, 0.8f * vis);
        // store radial endpoints so we can web them with concentric chords (glass look)
        int lines = 16;
        float[] ex = new float[lines], ey = new float[lines];
        for (int i = 0; i < lines; i++) {
            double ang = i / (float) lines * Math.PI * 2 + r.nextGaussian() * 0.08;
            float x = cx, y = cy;
            int segs = 5 + r.nextInt(4);
            for (int s = 0; s < segs; s++) {
                ang += (r.nextDouble() - 0.5) * 0.45;
                float len = 16 + r.nextInt(40);
                float nx = x + (float) Math.cos(ang) * len, ny = y + (float) Math.sin(ang) * len;
                line(g, x, y, nx, ny, 2f, A(vis * 0.85f, 0xB9BECB));
                line(g, x, y, nx, ny, 0.7f, A(vis * 0.95f, 0xFFFFFF));
                if (r.nextInt(3) == 0) {
                    double ba = ang + (r.nextBoolean() ? 0.6 : -0.6);
                    line(g, nx, ny, nx + (float) Math.cos(ba) * len * 0.5f, ny + (float) Math.sin(ba) * len * 0.5f, 1f, A(vis * 0.55f, 0xADB3C0));
                }
                x = nx;
                y = ny;
            }
            ex[i] = x;
            ey[i] = y;
        }
        // concentric webbing: chords between neighbouring radial cracks at a few radii
        for (float frac : new float[]{0.3f, 0.55f, 0.8f}) {
            for (int i = 0; i < lines; i++) {
                int j = (i + 1) % lines;
                float ax = cx + (ex[i] - cx) * frac, ay = cy + (ey[i] - cy) * frac;
                float bx = cx + (ex[j] - cx) * frac, by = cy + (ey[j] - cy) * frac;
                line(g, ax, ay, bx, by, 1f, A(vis * 0.5f, 0xC6CAD4));
            }
        }
        // a couple of secondary star-cracks
        for (int k = 0; k < 2; k++) {
            float sx = cx + (r.nextFloat() - 0.5f) * 120, sy = cy + (r.nextFloat() - 0.5f) * 90;
            disc(g, sx, sy, 3, A(0.5f * vis, 0xFFFFFF));
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3 + r.nextFloat();
                line(g, sx, sy, sx + (float) Math.cos(a) * (12 + r.nextInt(16)), sy + (float) Math.sin(a) * (12 + r.nextInt(16)), 0.8f, A(0.6f * vis, 0xD8DCE6));
            }
        }
    }

    private static void tvStatic(GuiGraphics g, Font font, int w, int h, long now, float vis) {
        Random r = new Random(now / 33);
        int cell = 4;
        for (int y = 0; y < h; y += cell)
            for (int x = 0; x < w; x += cell) {
                if (r.nextInt(2) == 0) continue;
                int v = 30 + r.nextInt(220);
                g.fill(x, y, x + cell, y + cell, A(0.6f * vis, (v << 16) | (v << 8) | v));
            }
        // rolling desync band
        int by = (int) ((now / 4) % (h + 30)) - 15;
        g.fill(0, by, w, by + 14, A(0.3f * vis, 0xFFFFFF));
        g.fill(0, by + 14, w, by + 16, A(0.3f * vis, 0x000000));
        for (int y = 0; y < h; y += 3) g.fill(0, y, w, y + 1, A(0.16f * vis, 0x000000));
        // SMPTE colour bars + NO SIGNAL
        int[] bars = {0xC0C0C0, 0xC0C000, 0x00C0C0, 0x00C000, 0xC000C0, 0xC00000, 0x0000C0};
        int bw = w / bars.length;
        for (int i = 0; i < bars.length; i++) g.fill(i * bw, h - 30, (i + 1) * bw, h - 12, A(0.5f * vis, bars[i]));
        String s = "NO SIGNAL";
        int tx = w / 2 - font.width(s) * 2;
        g.fill(tx - 8, h / 2 - 14, tx + font.width(s) * 4 + 8, h / 2 + 14, A(0.6f * vis, 0x000000));
        Ui.glowText(g, font, s, tx, h / 2 - 8, 0xFFFFFF, 2f);
    }

    private static void disco(GuiGraphics g, int w, int h, long now, float vis) {
        // dark club + beat strobe + vivid sweeping spotlight cones from the top edge
        float beat = (float) Math.exp(-((now % 480) / 480.0) * 5);
        g.fill(0, 0, w, h, A((0.30f + 0.10f * beat) * vis, 0x05000A));
        g.fill(0, 0, w, h, A(0.14f * beat * vis, Ui.hsv((now % 600) / 600f, 0.9f, 1f)));
        int beams = 5;
        for (int i = 0; i < beams; i++) {
            float baseX = w * (i + 0.5f) / beams;
            float sweep = (float) Math.sin(now / 820.0 * (0.7 + i * 0.13) + i * 1.9) * w * 0.28f;
            int col = Ui.hsv((i / (float) beams + now / 2600f) % 1f, 0.85f, 1f);
            // cone: wedges widening toward the floor, drawn as stacked rotated rects
            float apexX = baseX, apexY = -4;
            for (int k = 0; k < 10; k++) {
                float f0 = k / 10f, f1 = (k + 1) / 10f;
                float y0 = apexY + f0 * (h + 10), y1 = apexY + f1 * (h + 10);
                float midX = apexX + sweep * (f0 + f1) / 2f;
                float wdt = 6 + (f0 + f1) * 34;
                float ang = (float) Math.atan2(sweep, h);
                rect(g, midX, (y0 + y1) / 2, wdt, y1 - y0 + 1, ang, A((0.10f + 0.08f * beat) * vis, col));
            }
            // bright core + a glowing spot where it hits the floor
            float floorX = baseX + sweep;
            glow(g, floorX, h * 0.72f, 26, col, 0.22f * vis);
            disc(g, floorX, h * 0.72f, 5, A(0.5f * vis, 0xFFFFFF));
        }
        // sparkle flecks on the beat
        if (beat > 0.4f) {
            Random r = new Random(now / 120);
            for (int i = 0; i < 18; i++) disc(g, r.nextInt(w), r.nextInt(h), 1.5f, A(0.7f * beat * vis, 0xFFFFFF));
        }
    }

    private static void heartbeat(GuiGraphics g, int w, int h, long now, float vis) {
        double beat = (now % 850) / 850.0;
        float env = (float) Math.exp(-beat * 6) + 0.55f * (float) Math.exp(-((beat - 0.22) * (beat - 0.22)) / 0.003);
        vignette(g, w, h, 0xC0001A, (0.12f + 0.5f * env) * vis);
        g.fill(0, 0, w, h, A(0.06f * (1 - env) * vis, 0x100000));
        // ECG line across the lower third
        int base = (int) (h * 0.8);
        int prevY = base;
        for (int x = 0; x < w; x += 3) {
            double ph = ((x / (double) w) + now / 850.0) % 1.0;
            int y = base;
            if (ph > 0.45 && ph < 0.55) {
                double q = (ph - 0.45) / 0.1;
                y = base - (int) (Math.sin(q * Math.PI) * 26);
                if (q > 0.5) y = base + (int) (Math.sin((q - 0.5) * Math.PI) * 10);
            }
            line(g, x - 3, prevY, x, y, 1.4f, A(0.8f * vis, 0xFF3344));
            prevY = y;
        }
    }

    private static void quake(GuiGraphics g, int w, int h, long now, float vis) {
        float rumble = 0.5f + 0.5f * (float) Math.sin(now / 90.0);
        // hazard tint pulsing at top and bottom
        int band = h / 3;
        g.fillGradient(0, 0, w, band, A((0.28f + 0.18f * rumble) * vis, 0x5A1A00), A(0, 0x5A1A00));
        g.fillGradient(0, h - band, w, h, A(0, 0x5A1A00), A((0.28f + 0.18f * rumble) * vis, 0x5A1A00));
        vignette(g, w, h, 0x000000, (0.35f + 0.15f * rumble) * vis);
        // falling dust + chunky debris
        Random r = new Random(now / 55);
        for (int i = 0; i < 55; i++) {
            int x = r.nextInt(w);
            int y = (int) ((r.nextInt(h) + now / 8) % h);
            int s = r.nextInt(5) == 0 ? 2 : 1;
            g.fill(x, y, x + s, y + s + 1, A(0.35f * vis, r.nextBoolean() ? 0x9A8F82 : 0x6E655A));
        }
        // multiple creeping cracks from every edge, thicker, with branches
        Random cr = new Random(42);
        float grow = (float) Math.min(1, (now % 3500) / 3500.0);
        for (int i = 0; i < 8; i++) {
            int edge = i % 4;
            float x = edge == 0 ? cr.nextFloat() * w : edge == 1 ? w : edge == 2 ? cr.nextFloat() * w : 0;
            float y = edge == 0 ? 0 : edge == 1 ? cr.nextFloat() * h : edge == 2 ? h : cr.nextFloat() * h;
            double ang = Math.atan2(h / 2f - y, w / 2f - x) + cr.nextGaussian() * 0.3;
            float len = (40 + cr.nextInt(40)) * (0.4f + grow);
            float px = x, py = y;
            for (int s = 0; s < 4; s++) {
                ang += cr.nextGaussian() * 0.35;
                float nx = px + (float) Math.cos(ang) * len / 4, ny = py + (float) Math.sin(ang) * len / 4;
                line(g, px, py, nx, ny, 2f, A(0.6f * vis, 0x0A0806));
                line(g, px, py, nx, ny, 0.8f, A(0.4f * vis, 0x3A322A));
                px = nx;
                py = ny;
            }
        }
    }

    private static void jitter(GuiGraphics g, int w, int h, long now, float vis) {
        // can't-focus double vision: whole screen doubled into shaking red/cyan ghosts
        int jx = (int) (Math.sin(now / 33.0) * 5 + Math.sin(now / 11.0) * 2);
        int jy = (int) (Math.cos(now / 29.0) * 4);
        g.fill(jx, jy, w + jx, h + jy, A(0.16f * vis, 0xFF0022));
        g.fill(-jx, -jy, w - jx, h - jy, A(0.16f * vis, 0x00D0FF));
        // juddering doubled scanlines
        for (int y = 0; y < h; y += 5) {
            int o = (int) (Math.sin(y * 0.3 + now / 40.0) * 4);
            g.fill(o, y, w, y + 1, A(0.10f * vis, 0xFFFFFF));
        }
        // trembling frame
        int b = 2 + Math.abs(jx);
        g.fill(0, 0, w, b, A(0.3f * vis, 0x000000));
        g.fill(0, h - b, w, h, A(0.3f * vis, 0x000000));
        vignette(g, w, h, 0x000000, 0.25f * vis);
    }

    private static void glitch(GuiGraphics g, int w, int h, long now, float vis) {
        Random r = new Random(now / 55);
        // sliced horizontal offset bands with RGB split
        for (int i = 0; i < 10; i++) {
            int y = r.nextInt(h);
            int bh = 3 + r.nextInt(22);
            int off = r.nextInt(50) - 25;
            g.fill(Math.max(0, off), y, w, y + bh, A(0.22f * vis, r.nextBoolean() ? 0xFF0040 : 0x00FFE0));
            g.fill(0, y + 1, Math.min(w, w + off), y + Math.max(1, bh / 2), A(0.18f * vis, 0xFFFFFF));
        }
        // corrupt blocks
        for (int i = 0; i < 6; i++) {
            int x = r.nextInt(w), y = r.nextInt(h), bw = 10 + r.nextInt(50), bh = 6 + r.nextInt(20);
            g.fill(x, y, x + bw, y + bh, A(0.3f * vis, Ui.hsv(r.nextFloat(), 0.9f, 1f)));
        }
        if (r.nextInt(5) == 0) g.fill(0, 0, w, h, A(0.1f * vis, 0xFFFFFF));
    }

    private static void fakeLag(GuiGraphics g, Font font, int w, int h, long now, float vis) {
        boolean freeze = (now / 450) % 3 == 0;
        if (freeze) g.fill(0, 0, w, h, A(0.28f * vis, 0x0A0C12));
        // spinner top-right
        float ang = (now / 120f);
        int sx = w - 26, sy = 22;
        for (int i = 0; i < 8; i++) {
            float a2 = ang + i * 0.785f;
            float fade = (i / 8f);
            disc(g, sx + (float) Math.cos(a2) * 9, sy + (float) Math.sin(a2) * 9, 2f, A((0.3f + 0.6f * fade) * vis, 0xFFFFFF));
        }
        // ping + packet bars
        String s = "⚠ 999 ms   packet loss 87%";
        g.fill(6, 6, 12 + font.width(s), 20, A(0.6f * vis, 0x000000));
        g.drawString(font, s, 10, 10, A(vis, 0xFF5555), false);
        for (int i = 0; i < 20; i++) {
            boolean lost = (i + now / 200) % 3 == 0;
            g.fill(10 + i * 6, 24, 14 + i * 6, 30, A(vis, lost ? 0xFF3030 : 0x30C030));
        }
        if (freeze) {
            String r2 = "Reconnecting...";
            g.drawString(font, r2, w / 2 - font.width(r2) / 2, h / 2, A(vis, 0xFFFFFF), true);
        }
    }

    private static void trollRain(GuiGraphics g, int w, int h, long now, float vis) {
        Random r = new Random(9001);
        for (int i = 0; i < 16; i++) {
            float x = r.nextFloat() * w;
            float speed = 45 + r.nextFloat() * 80;
            float phase = r.nextFloat() * h;
            float y = ((now / 1000f * speed + phase) % (h + 50)) - 25;
            float s = 9 + r.nextFloat() * 11;
            float rot = (float) Math.sin(now / 400.0 + i) * 0.3f;
            trollAlpha(g, x, y, s, rot, vis);
        }
    }

    private static void trollAlpha(GuiGraphics g, float cx, float cy, float s, float rot, float vis) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.rotate(rot);
        // face
        disc(g, 0, 0, s, A(vis, 0xF2E9C0));
        disc(g, 0, 0, s * 0.92f, A(vis * 0.25f, 0xC9BE92)); // soft inner shade
        // brows + eyes
        disc(g, -s * 0.36f, -s * 0.22f, s * 0.17f, A(vis, 0x1A160E));
        disc(g, s * 0.36f, -s * 0.22f, s * 0.17f, A(vis, 0x1A160E));
        disc(g, -s * 0.31f, -s * 0.27f, s * 0.05f, A(vis, 0xFFFFFF));
        disc(g, s * 0.41f, -s * 0.27f, s * 0.05f, A(vis, 0xFFFFFF));
        // the big smug grin: corners curl UP, dropping in the middle (a wide U) + a white tooth line
        for (float t = -1f; t <= 1f; t += 0.13f) {
            float gx = t * s * 0.64f;
            float gy = s * 0.42f - (t * t) * s * 0.40f;
            disc(g, gx, gy, s * 0.11f, A(vis, 0x1A160E));
        }
        for (float t = -0.8f; t <= 0.8f; t += 0.13f) {
            float gx = t * s * 0.56f;
            float gy = s * 0.37f - (t * t) * s * 0.36f;
            disc(g, gx, gy, s * 0.05f, A(vis, 0xF8F4E4));
        }
        pose.popMatrix();
    }

    private static void bugs(GuiGraphics g, int w, int h, long now, float vis) {
        Random r = new Random(55);
        for (int i = 0; i < 16; i++) {
            float t = now / 1000f;
            float sp = 0.4f + r.nextFloat();
            float bx = (float) ((0.5 + 0.5 * Math.sin(t * sp + r.nextFloat() * 6)) * w);
            float by = (float) ((0.5 + 0.5 * Math.cos(t * sp * 0.9f + r.nextFloat() * 6)) * h);
            float dir = (float) Math.atan2(Math.cos(t * sp * 0.9f + 1), Math.cos(t * sp + 1));
            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(bx, by);
            pose.rotate(dir);
            // body
            disc(g, 2, 0, 3.2f, A(vis, 0x1C140E));
            disc(g, -2, 0, 2.4f, A(vis, 0x2A1E14));
            disc(g, 5, 0, 1.6f, A(vis, 0x120C08));
            // legs
            for (int l = 0; l < 3; l++) {
                float la = (float) (Math.sin(t * 14 + l + i) * 1.2);
                g.fill(-2 + l * 2, (int) (-5 + la), -1 + l * 2, 0, A(vis, 0x120C08));
                g.fill(-2 + l * 2, 0, -1 + l * 2, (int) (5 + la), A(vis, 0x120C08));
            }
            // antennae
            g.fill(6, -2, 9, -1, A(vis, 0x120C08));
            g.fill(6, 1, 9, 2, A(vis, 0x120C08));
            pose.popMatrix();
        }
    }

    private static void fakeCrash(GuiGraphics g, Font font, int w, int h, float vis) {
        g.fill(0, 0, w, h, A(0.985f * vis, 0x161616));
        int x = 22, y = 18;
        Ui.glowText(g, font, ":(", x, y, 0xFFFFFF, 2.2f);
        y += 30;
        g.drawString(font, "Your game ran into a problem and needs to cry about it.", x, y, A(vis, 0xDDDDDD), false);
        y += 20;
        String[] lines = {
                "---- Minecraft Crash Report ----",
                "// Everything's going to plan. No, really, that was supposed to happen.",
                "",
                "Time: just now",
                "Description: Unexpected donation",
                "",
                "java.lang.RuntimeException: You got donated to and the game gave up.",
                "    at net.minecraft.client.Minecraft.sufferConsequences(Minecraft.java:1225)",
                "    at com.punishfx.PunishManager.hit(PunishManager.java:84)",
                "    at net.minecraft.client.main.Main.main(Main.java:205)",
                "",
                "-- System Details --",
                "    Minecraft Version: 1.21.11",
                "    Memory: all of your regret",
                "    Mods: punishfx (very active)",
        };
        for (String l : lines) {
            g.drawString(font, l, x, y, A(vis, l.startsWith("java.lang") ? 0xFF6B6B : l.startsWith("//") ? 0x66BB66 : 0xCCCCCC), false);
            y += 11;
        }
        boolean cursor = (System.currentTimeMillis() / 500) % 2 == 0;
        g.drawString(font, "(this is fake - it clears itself)" + (cursor ? " _" : ""), x, h - 22, A(vis * 0.7f, 0x888888), false);
    }

    // ------------------------------------------------------------------ banner + countdown

    private static void banner(GuiGraphics g, Font font, int w, long now) {
        if (now >= PunishManager.bannerUntil) return;
        String t = PunishManager.banner;
        if (t == null || t.isEmpty()) return;
        long age = PunishManager.bannerUntil - now;
        float vis = Math.min(1f, age / 500f);
        float slide = Math.min(1f, (2600 - age) / 180f); // slides down on arrival
        float scale = 1.9f;
        int tw = (int) (font.width(t) * scale);
        int bw = tw + 36;
        int x = w / 2 - bw / 2;
        int y = (int) (18 * slide) - 2;
        // panel
        g.fillGradient(x, y, x + bw, y + 30, A(0.82f * vis, 0x1A0409), A(0.82f * vis, 0x0A0205));
        g.fill(x, y, x + bw, y + 2, A(vis, Ui.ACCENT));
        g.fill(x, y + 28, x + bw, y + 30, A(0.6f * vis, Ui.ACCENT2));
        g.fill(x, y, x + 2, y + 30, A(vis, Ui.ACCENT));
        g.fill(x + bw - 2, y, x + bw, y + 30, A(vis, Ui.ACCENT));
        Ui.glowText(g, font, t, w / 2 - tw / 2, y + 9, 0xFF5C5C, scale);
    }

    private static void countdown(GuiGraphics g, Font font, int w, int h, long now) {
        if (!PunishManager.extremePending()) return;
        var p = PunishManager.pendingExtreme();
        long ms = Math.max(0, PunishManager.extremeMillisLeft());
        long s = (ms + 999) / 1000;
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 120.0);
        // dim everything
        g.fill(0, 0, w, h, A(0.35f + 0.15f * pulse, 0x1A0000));
        String big = p.name.toUpperCase() + " IN " + s;
        int cy = h / 2 - 24;
        g.fillGradient(0, cy - 10, w, cy + 34, A(0.6f, 0x200000), A(0.6f, 0x080000));
        g.fill(0, cy - 10, w, cy - 8, A(pulse, Ui.ACCENT));
        g.fill(0, cy + 32, w, cy + 34, A(pulse, Ui.ACCENT));
        Ui.glowText(g, font, big, w / 2 - (int) (font.width(big) * 2.6f / 2), cy, Ui.mix(0xFF3030, 0xFFFFFF, pulse * 0.4f), 2.6f);
        String sub = "press your Punish FX cancel key to stop it";
        g.drawString(font, sub, w / 2 - font.width(sub) / 2, cy + 40, A(1f, 0xFFFFFF), true);
        // shrinking progress bar
        float frac = Math.min(1f, ms / (float) Math.max(1, PunishManager.extremeTotalMillis()));
        int bw = (int) (w * 0.5f);
        int bx = w / 2 - bw / 2, by = cy + 54;
        g.fill(bx, by, bx + bw, by + 5, A(0.5f, 0x000000));
        g.fill(bx, by, bx + (int) (bw * frac), by + 5, A(1f, Ui.mix(Ui.ACCENT, Ui.ACCENT2, 1 - frac)));
    }
}
