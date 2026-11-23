package com.punishfx;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Random;

/** Draws the on-screen punishments, the donor banner and the leave/close countdown. */
public final class PunishOverlay {
    private PunishOverlay() {}

    static final Identifier VIGNETTE = Identifier.fromNamespaceAndPath("punishfx", "textures/misc/vignette.png");
    static final Identifier TUNNEL = Identifier.fromNamespaceAndPath("punishfx", "textures/misc/tunnel.png");

    static int A(float a, int rgb) {
        return Ui.a(a, rgb);
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        long now = System.currentTimeMillis();
        Font font = mc.font;

        // Real colour inversion first, in its own layer, so it flips the world and HUD but
        // not the punishment banner drawn after it.
        for (PunishManager.Active a : PunishManager.active()) {
            if (a.p.id.equals("negative") && now < a.end) {
                g.nextStratum();
                g.fill(RenderPipelines.GUI_INVERT, 0, 0, w, h, 0xFFFFFFFF);
                g.nextStratum();
                break;
            }
        }

        // the top donors board sits under the punishment effects
        TopDonorsHud.render(g, font, w, h);

        float strength = (float) Config.get().overlayStrength;
        for (PunishManager.Active a : PunishManager.active()) {
            float p = a.progress();
            float vis = Math.min(1f, Math.min((now - a.start) / 220f, (a.end - now) / 450f));
            if (vis <= 0) continue;
            vis *= strength;
            try {
                drawOne(g, font, a.p.id, w, h, now, vis, p);
            } catch (Exception ignored) {
            }
        }
        donorBanner(g, font, w, now);
        banner(g, font, w, now);
        countdown(g, font, w, h, now);
    }

    public static void drawOne(GuiGraphics g, Font font, String id, int w, int h, long now, float vis, float p) {
        switch (id) {
            case "disco_eyes" -> disco(g, w, h, now, vis);
            case "tunnel_vision" -> tunnel(g, w, h, vis);
            case "flashbang" -> flashbang(g, w, h, now, vis, p);
            case "blackout" -> blackout(g, font, w, h, now, vis);
            case "fog" -> fog(g, w, h, now, vis);
            case "drunk" -> drunk(g, w, h, now, vis);
            case "glitch" -> glitch(g, w, h, now, vis);
            default -> { }
        }
    }

    // ------------------------------------------------------------------ primitives

    /** Stretch one of our gradient textures over the whole screen, tinted. */
    private static void stretch(GuiGraphics g, Identifier tex, int w, int h, int argb) {
        if ((argb >>> 24) == 0) return;
        g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f, w, h, 256, 256, 256, 256, argb);
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

    // ------------------------------------------------------------------ the punishments

    private static void disco(GuiGraphics g, int w, int h, long now, float vis) {
        float period = new float[]{1600f, 900f, 450f}[Config.get().discoLevel];
        float hue = (now % (long) period) / period;
        float beat = 0.5f + 0.5f * (float) Math.sin(now / 150.0);
        g.fill(0, 0, w, h, A((0.34f + 0.14f * beat) * vis, Ui.hsv(hue, 0.95f, 1f)));
        g.fillGradient(0, 0, w, h, A(0.18f * vis, Ui.hsv((hue + 0.33f) % 1f, 0.95f, 1f)), A(0.18f * vis, Ui.hsv((hue + 0.66f) % 1f, 0.95f, 1f)));
        stretch(g, VIGNETTE, w, h, A(0.55f * vis, Ui.hsv((hue + 0.5f) % 1f, 0.9f, 0.8f)));
    }

    /** The field of view itself narrows (GameRendererMixin); this darkens the rim to sell it. */
    private static void tunnel(GuiGraphics g, int w, int h, float vis) {
        stretch(g, TUNNEL, w, h, A(0.72f * vis, 0x000000));
    }

    private static void flashbang(GuiGraphics g, int w, int h, long now, float vis, float p) {
        // hold a full white-out for the first ~40%, then a slow ease-out
        float hold = new float[]{0.25f, 0.4f, 0.6f}[Config.get().flashLevel];
        float fa;
        if (p < hold) fa = 1f;
        else {
            float t = (p - hold) / (1f - hold);
            fa = (1f - t) * (1f - t) * 0.97f + 0.03f * (1f - t);
        }
        g.fill(0, 0, w, h, A(fa * vis, 0xFFFFFF));
        // burnt-in after-image blob that drifts and fades once you can see again
        if (p > 0.35f) {
            float t = Math.min(1f, (p - 0.35f) / 0.6f);
            float a = (1f - t) * 0.45f * vis;
            float cx = w / 2f + (float) Math.sin(now / 900.0) * 6, cy = h / 2f + (float) Math.cos(now / 1100.0) * 4;
            glow(g, cx, cy, Math.min(w, h) * 0.32f, 0xB070FF, a);
            glow(g, cx, cy, Math.min(w, h) * 0.18f, 0x70FFC0, a * 0.8f);
        }
    }

    private static void blackout(GuiGraphics g, Font font, int w, int h, long now, float vis) {
        g.fill(0, 0, w, h, A(0.985f * vis, 0x000000));
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
        g.fill(0, 0, w, h, A(0.34f * vis, 0x8E949C));
        for (int i = 0; i < 6; i++) {
            float t = now / 1000f;
            float x = (float) ((0.5 + 0.5 * Math.sin(t * (0.2 + i * 0.05) + i * 1.7)) * w);
            float y = (float) ((0.5 + 0.4 * Math.cos(t * (0.15 + i * 0.04) + i)) * h);
            glow(g, x, y, 120 + i * 20, 0xC8CED6, 0.07f * vis);
        }
        stretch(g, VIGNETTE, w, h, A(0.8f * vis, 0x30363C));
    }

    /** Drunk: the camera sways and tilts and nausea warps the world; this adds a queasy haze. */
    private static void drunk(GuiGraphics g, int w, int h, long now, float vis) {
        float breathe = 0.5f + 0.5f * (float) Math.sin(now / 700.0);
        g.fill(0, 0, w, h, A((0.08f + 0.05f * breathe) * vis, 0x9CC24A));
        for (int i = 0; i < 4; i++) {
            float x = (float) ((0.5 + 0.42 * Math.sin(now / 900.0 + i * 1.9)) * w);
            float y = (float) ((0.5 + 0.38 * Math.cos(now / 1150.0 + i * 2.3)) * h);
            glow(g, x, y, 70, 0xC8E070, 0.07f * vis);
        }
        stretch(g, VIGNETTE, w, h, A((0.55f + 0.25f * breathe) * vis, 0x2A3A10));
        // double vision: two ghost crosshairs drifting apart and back together
        float sep = (float) (4 + 5 * Math.sin(now / 600.0));
        int cx = w / 2, cy = h / 2;
        for (int s = -1; s <= 1; s += 2) {
            int ox = (int) (cx + s * sep), oy = (int) (cy + s * sep * 0.3f);
            g.fill(ox - 5, oy, ox + 5, oy + 1, A(0.35f * vis, 0xFFFFFF));
            g.fill(ox, oy - 5, ox + 1, oy + 5, A(0.35f * vis, 0xFFFFFF));
        }
    }

    private static void glitch(GuiGraphics g, int w, int h, long now, float vis) {
        vis *= new float[]{0.5f, 1f, 1.6f}[Config.get().glitchLevel];
        Random r = new Random(now / 55);
        // torn, offset horizontal bands with RGB split
        for (int i = 0; i < 12; i++) {
            int y = r.nextInt(h);
            int bh = 2 + r.nextInt(20);
            int off = r.nextInt(60) - 30;
            g.fill(Math.max(0, off), y, w + Math.min(0, off), y + bh, A(0.24f * vis, r.nextBoolean() ? 0xFF0040 : 0x00FFE0));
            g.fill(0, y + 1, w, y + Math.max(2, bh / 3), A(0.14f * vis, 0xFFFFFF));
        }
        // corrupt blocks
        for (int i = 0; i < 7; i++) {
            int x = r.nextInt(w), y = r.nextInt(h), bw = 10 + r.nextInt(60), bh = 4 + r.nextInt(18);
            g.fill(x, y, x + bw, y + bh, A(0.32f * vis, Ui.hsv(r.nextFloat(), 0.9f, 1f)));
        }
        // scanlines + the odd full-frame flash
        for (int y = 0; y < h; y += 3) g.fill(0, y, w, y + 1, A(0.07f * vis, 0x000000));
        if (r.nextInt(6) == 0) g.fill(0, 0, w, h, A(0.12f * vis, 0xFFFFFF));
    }

    // ------------------------------------------------------------------ donor banner

    /** "<player> paid you $X" + the punishment names, shown for a few seconds. */
    private static void donorBanner(GuiGraphics g, Font font, int w, long now) {
        if (now >= PunishManager.donorUntil || PunishManager.donor.isEmpty()) return;
        Config cfg = Config.get();
        long left = PunishManager.donorUntil - now;
        float vis = Math.min(1f, Math.min(left / 400f, (now - PunishManager.donorStart) / 160f));
        if (vis <= 0) return;
        float bs = (float) cfg.bannerScale;
        int sh = g.guiHeight();
        int vw = (int) (w / bs); // lay out in the banner's own (scaled) space
        var pose = g.pose();
        pose.pushMatrix();
        pose.scale(bs);
        w = vw;

        String who = PunishManager.donor;
        String amt = PunishManager.donorAmount;
        String sub = cfg.bannerShowPunishments ? PunishManager.donorSub : "";
        String head = who + " paid you";

        // shrink to fit narrow screens / long names
        float scale = 1.7f, amtScale = 2.2f;
        int bw;
        while (true) {
            int lineW = (int) (font.width(head + " ") * scale) + (int) (font.width(amt) * amtScale);
            bw = Math.max(lineW, font.width(sub)) + 40;
            if (bw <= w - 8 || scale <= 1f) break;
            scale -= 0.1f;
            amtScale -= 0.12f;
        }
        int headW = (int) (font.width(head + " ") * scale);
        int bh = sub.isEmpty() ? 38 : 52;
        int x = w / 2 - bw / 2;
        int vh = (int) (sh / bs);
        int y = cfg.bannerPos == 1 ? vh / 2 - bh / 2 - 30 : cfg.bannerPos == 2 ? vh - bh - 58 : 10;

        g.fillGradient(x, y, x + bw, y + bh, A(0.9f * vis, Ui.tint(0x120E14, 0.12f)), A(0.9f * vis, Ui.tint(0x08070A, 0.07f)));
        g.fill(x, y, x + bw, y + 2, A(vis, Ui.ACCENT));
        g.fill(x, y + bh - 2, x + bw, y + bh, A(0.7f * vis, Ui.ACCENT2));
        g.fill(x, y, x + 2, y + bh, A(vis, Ui.ACCENT));
        g.fill(x + bw - 2, y, x + bw, y + bh, A(vis, Ui.ACCENT));
        float pulse = 0.6f + 0.4f * (float) Math.sin(now / 160.0);
        disc(g, x + 16, y + 17, 6, A(vis, 0xFFD24A));
        disc(g, x + 16, y + 17, 6f + pulse * 2, A(0.3f * vis, 0xFFD24A));
        g.drawString(font, "$", x + 14, y + 13, A(vis, 0x5A3A00), false);

        int tx = x + 30;
        Ui.glowText(g, font, head, tx, y + 10, 0xFFCDD2, scale);
        Ui.glowText(g, font, amt, tx + headW, y + 8, 0x6BFF9E, amtScale);
        if (!sub.isEmpty()) g.drawString(font, sub, x + 30, y + bh - 16, A(0.95f * vis, 0xE8C8CC), false);
        pose.popMatrix();
    }

    // ------------------------------------------------------------------ banner + countdown

    private static void banner(GuiGraphics g, Font font, int w, long now) {
        if (now >= PunishManager.bannerUntil) return;
        String t = PunishManager.banner;
        if (t == null || t.isEmpty()) return;
        long age = PunishManager.bannerUntil - now;
        float vis = Math.min(1f, age / 500f);
        float scale = 1.6f;
        int tw = (int) (font.width(t) * scale);
        int bw = tw + 30;
        int x = w / 2 - bw / 2;
        int y = 16;
        g.fillGradient(x, y, x + bw, y + 28, A(0.85f * vis, Ui.tint(0x120E14, 0.12f)), A(0.85f * vis, Ui.tint(0x08070A, 0.07f)));
        g.fill(x, y, x + bw, y + 2, A(vis, Ui.ACCENT));
        g.fill(x, y + 26, x + bw, y + 28, A(0.6f * vis, Ui.ACCENT2));
        Ui.glowText(g, font, t, w / 2 - tw / 2, y + 9, 0xFF8080, scale);
    }

    private static void countdown(GuiGraphics g, Font font, int w, int h, long now) {
        if (!PunishManager.extremePending()) return;
        var p = PunishManager.pendingExtreme();
        long ms = Math.max(0, PunishManager.extremeMillisLeft());
        long s = (ms + 999) / 1000;
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 120.0);
        g.fill(0, 0, w, h, A(0.35f + 0.15f * pulse, 0x1A0000));
        String big = p.name.toUpperCase() + " IN " + s;
        float bigScale = Math.min(2.6f, (w - 20) / (float) Math.max(1, font.width(big)));
        int cy = h / 2 - 24;
        g.fillGradient(0, cy - 10, w, cy + 34, A(0.6f, 0x200000), A(0.6f, 0x080000));
        g.fill(0, cy - 10, w, cy - 8, A(pulse, Ui.ACCENT));
        g.fill(0, cy + 32, w, cy + 34, A(pulse, Ui.ACCENT));
        Ui.glowText(g, font, big, w / 2 - (int) (font.width(big) * bigScale / 2), cy, Ui.mix(0xFF3030, 0xFFFFFF, pulse * 0.4f), bigScale);
        String sub = "press " + PunishFxClient.cancelKeyName() + " to cancel";
        g.drawString(font, sub, w / 2 - font.width(sub) / 2, cy + 40, A(1f, 0xFFFFFF), true);
        float frac = Math.min(1f, ms / (float) Math.max(1, PunishManager.extremeTotalMillis()));
        int bw = (int) (w * 0.5f);
        int bx = w / 2 - bw / 2, by = cy + 54;
        g.fill(bx, by, bx + bw, by + 5, A(0.5f, 0x000000));
        g.fill(bx, by, bx + (int) (bw * frac), by + 5, A(1f, Ui.mix(Ui.ACCENT, Ui.ACCENT2, 1 - frac)));
    }
}
