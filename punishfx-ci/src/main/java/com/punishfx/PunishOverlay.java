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
        int al = Math.max(0, Math.min(255, Math.round(a * 255)));
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    static int hsv(float h, float s, float v) {
        return java.awt.Color.HSBtoRGB(((h % 1f) + 1f) % 1f, s, v) & 0xFFFFFF;
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        long now = System.currentTimeMillis();
        Font font = mc.font;

        for (PunishManager.Active a : PunishManager.active()) {
            float p = a.progress();
            float vis = Math.min(1f, Math.min((now - a.start) / 200f, (a.end - now) / 400f));
            if (vis <= 0) continue;
            try {
                drawOne(g, mc, font, a, w, h, now, vis, p);
            } catch (Exception ignored) {
            }
        }

        // drunk / sick green tint (drunk is a control punishment, but it should look woozy too)
        for (PunishManager.Active a : PunishManager.active()) {
            if (a.p.id.equals("drunk")) {
                float wob = 0.12f + 0.06f * (float) Math.sin(now / 300.0);
                tintEdges(g, w, h, 0x3BA55D, wob);
            }
        }

        banner(g, font, w, now);
        countdown(g, font, w, h, now);
    }

    private static void drawOne(GuiGraphics g, Minecraft mc, Font font, PunishManager.Active a, int w, int h, long now, float vis, float p) {
        Random seed = new Random(a.p.id.hashCode());
        switch (a.p.id) {
            case "negative" -> {
                // sickly photo-negative look: strong shifting complementary wash + flicker
                float fl = ((now / 90) % 10) < 8 ? 1f : 0.5f;
                g.fill(0, 0, w, h, A(0.55f * vis * fl, 0x00FFAA));
                g.fill(0, 0, w, h, A(0.30f * vis, 0xFF00AA));
                for (int y = 0; y < h; y += 3) g.fill(0, y, w, y + 1, A(0.10f * vis, 0x000000));
            }
            case "flashbang" -> {
                float fa = p < 0.12f ? 1f : Math.max(0.15f, 1f - (p - 0.12f) * 1.6f);
                g.fill(0, 0, w, h, A(fa * vis, 0xFFFFFF));
            }
            case "blackout" -> g.fill(0, 0, w, h, A(0.96f * vis, 0x000000));
            case "fog" -> {
                g.fill(0, 0, w, h, A(0.5f * vis, 0x8A8F98));
                vignette(g, w, h, 0x202428, 0.5f * vis);
            }
            case "sick" -> {
                tintEdges(g, w, h, 0x3BA55D, 0.4f * vis);
                g.fill(0, 0, w, h, A(0.12f * vis, 0x3BA55D));
            }
            case "tunnel_vision" -> vignetteStrong(g, w, h, 0.5f + 0.45f * (float) Math.sin(now / 400.0), vis);
            case "censored" -> censor(g, font, w, h, vis, seed);
            case "cracked_screen" -> cracks(g, w, h, vis, seed);
            case "static" -> staticNoise(g, w, h, vis, now);
            case "disco_eyes" -> g.fill(0, 0, w, h, A(0.38f * vis, hsv((now % 400) / 400f, 0.9f, 1f)));
            case "heartbeat" -> {
                double beat = (now % 850) / 850.0;
                float env = (float) Math.exp(-beat * 6) + 0.5f * (float) Math.exp(-((beat - 0.22) * (beat - 0.22)) / 0.004);
                tintEdges(g, w, h, 0xC0001A, (0.15f + 0.5f * env) * vis);
            }
            case "earthquake" -> vignette(g, w, h, 0x000000, 0.25f * vis);
            case "glitch" -> glitch(g, w, h, vis, now);
            case "fake_lag" -> fakeLag(g, font, w, h, vis, now);
            case "troll_rain" -> trollRain(g, w, h, vis, now, a);
            case "rain_bugs" -> bugs(g, w, h, vis, now, a);
            case "fake_crash" -> fakeCrash(g, font, w, h, vis);
            default -> { }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void vignette(GuiGraphics g, int w, int h, int col, float a) {
        int band = h / 4;
        g.fillGradient(0, 0, w, band, A(a, col), A(0, col));
        g.fillGradient(0, h - band, w, h, A(0, col), A(a, col));
        int strips = 8, sw = Math.max(1, w / 40);
        for (int i = 0; i < strips; i++) {
            int c = A(a * (1 - i / (float) strips), col);
            g.fill(i * sw, 0, (i + 1) * sw, h, c);
            g.fill(w - (i + 1) * sw, 0, w - i * sw, h, c);
        }
    }

    private static void tintEdges(GuiGraphics g, int w, int h, int col, float a) {
        vignette(g, w, h, col, a);
    }

    private static void vignetteStrong(GuiGraphics g, int w, int h, float closeness, float vis) {
        int rings = 26;
        int maxR = Math.max(w, h);
        for (int i = 0; i < rings; i++) {
            float f = i / (float) rings;
            int sw = Math.max(1, maxR / (rings * 2));
            float a = Math.min(1f, f * (1.6f + closeness)) * vis;
            g.fill(i * sw, 0, (i + 1) * sw, h, A(a, 0x000000));
            g.fill(w - (i + 1) * sw, 0, w - i * sw, h, A(a, 0x000000));
            g.fill(0, i * sw, w, (i + 1) * sw, A(a, 0x000000));
            g.fill(0, h - (i + 1) * sw, w, h - i * sw, A(a, 0x000000));
        }
    }

    private static void censor(GuiGraphics g, Font font, int w, int h, float vis, Random r) {
        int bars = 5;
        for (int i = 0; i < bars; i++) {
            int y = (int) ((0.12 + 0.72 * r.nextDouble()) * h);
            int bh = 14 + r.nextInt(18);
            int x = (int) (r.nextDouble() * w * 0.3);
            int bw = (int) (w * (0.4 + r.nextDouble() * 0.6));
            g.fill(x, y, x + bw, y + bh, A(vis, 0x000000));
            g.drawString(font, "CENSORED", x + bw / 2 - font.width("CENSORED") / 2, y + bh / 2 - 4, A(vis, 0xFFFFFF), false);
        }
    }

    private static void cracks(GuiGraphics g, int w, int h, float vis, Random r) {
        int cx = (int) (w * (0.3 + r.nextDouble() * 0.4)), cy = (int) (h * (0.3 + r.nextDouble() * 0.4));
        // impact ring
        for (int k = 3; k <= 6; k++) ringDots(g, cx, cy, k * 5, A(vis * 0.9f, 0xEDEFF5));
        int lines = 11;
        for (int i = 0; i < lines; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            float x = cx, y = cy;
            int segs = 5 + r.nextInt(4);
            for (int s = 0; s < segs; s++) {
                ang += (r.nextDouble() - 0.5) * 0.7;
                float len = 18 + r.nextInt(40);
                float nx = x + (float) Math.cos(ang) * len, ny = y + (float) Math.sin(ang) * len;
                line(g, x, y, nx, ny, A(vis * 0.85f, 0xDADEE8));
                x = nx;
                y = ny;
            }
        }
    }

    private static void ringDots(GuiGraphics g, int cx, int cy, int r, int col) {
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12;
            g.fill((int) (cx + Math.cos(a) * r), (int) (cy + Math.sin(a) * r), (int) (cx + Math.cos(a) * r) + 1, (int) (cy + Math.sin(a) * r) + 1, col);
        }
    }

    private static void line(GuiGraphics g, float x1, float y1, float x2, float y2, int col) {
        float dx = x2 - x1, dy = y2 - y1;
        int steps = (int) Math.max(Math.abs(dx), Math.abs(dy));
        if (steps <= 0) return;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int x = Math.round(x1 + dx * t), y = Math.round(y1 + dy * t);
            g.fill(x, y, x + 1, y + 1, col);
        }
    }

    private static void staticNoise(GuiGraphics g, int w, int h, float vis, long now) {
        Random r = new Random(now / 40);
        int cell = 4;
        for (int y = 0; y < h; y += cell) {
            for (int x = 0; x < w; x += cell) {
                if (r.nextInt(2) == 0) continue;
                int v = 40 + r.nextInt(216);
                g.fill(x, y, x + cell, y + cell, A(0.55f * vis, (v << 16) | (v << 8) | v));
            }
        }
        for (int y = 0; y < h; y += 3) g.fill(0, y, w, y + 1, A(0.15f * vis, 0x000000));
    }

    private static void glitch(GuiGraphics g, int w, int h, float vis, long now) {
        Random r = new Random(now / 70);
        for (int i = 0; i < 8; i++) {
            int y = r.nextInt(h);
            int bh = 2 + r.nextInt(16);
            int off = r.nextInt(40) - 20;
            g.fill(Math.max(0, off), y, w, y + bh, A(0.25f * vis, r.nextBoolean() ? 0xFF0040 : 0x00FFFF));
            g.fill(0, y, Math.min(w, w + off), y + Math.max(1, bh / 2), A(0.2f * vis, 0xFFFFFF));
        }
    }

    private static void fakeLag(GuiGraphics g, Font font, int w, int h, float vis, long now) {
        boolean freeze = (now / 500) % 2 == 0;
        if (freeze) g.fill(0, 0, w, h, A(0.25f * vis, 0x101018));
        String s = "⚠ 999 ms";
        g.fill(4, 4, 8 + font.width(s) + 4, 18, A(0.6f * vis, 0x000000));
        g.drawString(font, s, 8, 8, A(vis, 0xFF5555), false);
    }

    private static void trollRain(GuiGraphics g, int w, int h, float vis, long now, PunishManager.Active a) {
        int n = 14;
        Random r = new Random(a.p.id.hashCode());
        for (int i = 0; i < n; i++) {
            float x = r.nextFloat() * w;
            float speed = 40 + r.nextFloat() * 70;
            float phase = r.nextFloat() * h;
            float y = ((now / 1000f * speed + phase) % (h + 40)) - 20;
            float s = 10 + r.nextFloat() * 10;
            troll(g, x, y, s, A(vis, 0xFFE24A));
        }
    }

    private static void troll(GuiGraphics g, float cx, float cy, float s, int col) {
        disc(g, cx, cy, s, col);
        g.fill((int) (cx - s * 0.4f), (int) (cy - s * 0.3f), (int) (cx - s * 0.1f), (int) (cy - s * 0.05f), A(1f, 0x000000));
        g.fill((int) (cx + s * 0.1f), (int) (cy - s * 0.3f), (int) (cx + s * 0.4f), (int) (cy - s * 0.05f), A(1f, 0x000000));
        for (int i = -2; i <= 2; i++) {
            int gx = (int) (cx + i * s * 0.25f);
            g.fill(gx, (int) (cy + s * 0.1f), gx + 2, (int) (cy + s * 0.45f), A(1f, 0x000000));
        }
    }

    private static void bugs(GuiGraphics g, int w, int h, float vis, long now, PunishManager.Active a) {
        int n = 18;
        Random r = new Random(a.p.id.hashCode() + 5);
        for (int i = 0; i < n; i++) {
            float t = now / 1000f;
            float bx = (float) ((0.5 + 0.5 * Math.sin(t * (0.6 + r.nextFloat()) + r.nextFloat() * 6)) * w);
            float by = (float) ((0.5 + 0.5 * Math.cos(t * (0.5 + r.nextFloat()) + r.nextFloat() * 6)) * h);
            disc(g, bx, by, 3, A(vis, 0x201810));
            for (int l = 0; l < 3; l++) {
                float la = (float) (Math.sin(t * 12 + l) * 0.8);
                g.fill((int) (bx - 4), (int) (by - 2 + l * 2 + la), (int) (bx + 4), (int) (by - 1 + l * 2 + la), A(vis, 0x201810));
            }
        }
    }

    private static void disc(GuiGraphics g, float cx, float cy, float r, int col) {
        for (float dy = -r; dy <= r; dy++) {
            float hw = (float) Math.sqrt(Math.max(0, r * r - dy * dy));
            g.fill((int) (cx - hw), (int) (cy + dy), (int) (cx + hw), (int) (cy + dy) + 1, col);
        }
    }

    private static void fakeCrash(GuiGraphics g, Font font, int w, int h, float vis) {
        g.fill(0, 0, w, h, A(0.98f * vis, 0x1A1A1A));
        int x = 20, y = 20;
        g.drawString(font, "---- Minecraft Crash Report ----", x, y, A(vis, 0xFFFFFF), false);
        y += 14;
        g.drawString(font, "// Everything's going to plan. No, really, that was supposed to happen.", x, y, A(vis, 0xAAAAAA), false);
        y += 18;
        String[] lines = {
                "Time: just now", "Description: Unexpected donation",
                "java.lang.RuntimeException: You got donated to.",
                "    at net.minecraft.client.Minecraft.getPunished(Minecraft.java:1225)",
                "    at com.punishfx.PunishManager.hit(PunishManager.java:84)",
                "    at java.base/java.lang.Thread.run(Thread.java:840)",
                "", "-- System Details --", "    Minecraft Version: 1.21.11",
                "    Memory: regret used / regret max", "    Mods: punishfx (active)"
        };
        for (String l : lines) {
            g.drawString(font, l, x, y, A(vis, l.startsWith("java.lang") ? 0xFF6666 : 0xDDDDDD), false);
            y += 11;
        }
        g.drawString(font, "(this is fake - it clears on its own)", x, h - 20, A(vis * 0.6f, 0x888888), false);
    }

    private static void banner(GuiGraphics g, Font font, int w, long now) {
        if (now >= PunishManager.bannerUntil) return;
        String t = PunishManager.banner;
        if (t == null || t.isEmpty()) return;
        float vis = Math.min(1f, (PunishManager.bannerUntil - now) / 500f);
        int tw = font.width(t);
        float scale = 1.8f;
        int x = w / 2;
        int y = 24;
        g.fill((int) (x - tw * scale / 2) - 8, y - 4, (int) (x + tw * scale / 2) + 8, y + (int) (10 * scale), A(0.55f * vis, 0x12040A));
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale);
        g.drawString(font, t, -tw / 2, 0, A(vis, 0xFF5C5C), true);
        g.pose().popMatrix();
    }

    private static void countdown(GuiGraphics g, Font font, int w, int h, long now) {
        if (!PunishManager.extremePending()) return;
        var p = PunishManager.pendingExtreme();
        long s = PunishManager.extremeSecondsLeft();
        String big = p.name.toUpperCase() + " IN " + s;
        String sub = "press your Punish FX cancel key to stop it";
        g.fill(0, h / 2 - 28, w, h / 2 + 24, A(0.6f, 0x200000));
        g.pose().pushMatrix();
        g.pose().translate(w / 2f, h / 2f - 20);
        g.pose().scale(2.4f);
        g.drawString(font, big, -font.width(big) / 2, 0, A(1f, 0xFF3030), true);
        g.pose().popMatrix();
        g.drawString(font, sub, w / 2 - font.width(sub) / 2, h / 2 + 10, A(1f, 0xFFFFFF), false);
    }
}
