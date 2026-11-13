package com.donationfx;

import com.donationfx.ThemeKit.Bag;
import com.donationfx.ThemeKit.Bit;
import com.donationfx.ThemeKit.Ctx;
import com.donationfx.ThemeKit.Theme;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.Random;

import static com.donationfx.ThemeKit.*;

/** Themes 21-40. Each one is self-contained (the default confetti/flash/shake never plays over it). */
final class ThemesC {
    private ThemesC() {}

    static void register(List<Theme> out) {
        out.add(new Aurora());
        out.add(new Volcano());
        out.add(new Tornado());
        out.add(new NeonCity());
        out.add(new Galaxy());
        out.add(new MeteorShower());
        out.add(new RainbowRoad());
        out.add(new PixelParty());
        out.add(new Bubbles());
        out.add(new Electric());
        out.add(new Sakura());
        out.add(new Fireflies());
        out.add(new Casino());
        out.add(new Comet());
        out.add(new Plasma());
        out.add(new Phoenix());
        out.add(new Wormhole());
        out.add(new Balloons());
        out.add(new Equalizer());
        out.add(new Starfall());
    }

    private static float sc(Ctx c) {
        return c.h / 360f;
    }

    // =====================================================================================
    static final class Aurora extends Theme {
        Aurora() {
            super("aurora", "Aurora", "Northern lights ripple in glowing green and purple curtains over a starry sky.");
        }

        @Override
        public void draw(Ctx c) {
            GuiGraphics g = c.g;
            tint(c, 0x030616, 0.6f * c.vis);
            Random r = new Random(7);
            for (int i = 0; i < 80; i++) {
                float x = r.nextFloat() * c.w, y = r.nextFloat() * c.h * 0.8f;
                float tw = 0.4f + 0.6f * (float) Math.abs(Math.sin(c.t * 2 + i));
                rect(g, x, y, 1.4f, 1.4f, 0, A(tw * c.vis, 0xFFFFFF));
            }
            // curtains: vertical ribbons whose x wobbles with height
            int curtains = 5 + c.L;
            for (int ci = 0; ci < curtains; ci++) {
                float baseX = c.w * (ci + 0.5f) / curtains;
                int col = ci % 3 == 0 ? 0x5CFF9A : ci % 3 == 1 ? 0xB46CFF : 0x4CC9FF;
                float phase = ci * 1.3f;
                for (float y = 0; y < c.h * 0.78f; y += 4) {
                    float sway = (float) Math.sin(y * 0.02 + c.t * 1.3 + phase) * 40 + (float) Math.sin(y * 0.05 - c.t) * 12;
                    float topFade = clamp(y / (c.h * 0.78f));
                    float a = c.vis * 0.1f * topFade * (0.7f + 0.3f * (float) Math.sin(c.t * 2 + ci));
                    float wdt = 26 + 10 * (float) Math.sin(c.t + ci);
                    rect(g, baseX + sway, y, wdt, 5, 0, A(a, col));
                    rect(g, baseX + sway, y, wdt * 0.4f, 5, 0, A(a * 1.6f, 0xFFFFFF));
                }
            }
            // glowing horizon
            g.fillGradient(0, (int) (c.h * 0.78f), c.w, c.h, A(0.3f * c.vis, 0x0A3A2A), A(0, 0x0A3A2A));
            glow(g, c.w / 2f, c.h * 0.8f, c.w * 0.5f, 0x5CFF9A, 0.12f * c.vis);
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            Random r = new Random(2);
            for (double t = 0; t < T - 0.6; t += 0.7) out.add(snd("block.amethyst_block.chime", t, 0.6f, 0.7f + r.nextFloat() * 0.6f));
            out.add(snd("block.beacon.activate", 0.1, 0.5f, 1.3f));
            out.add(snd("entity.player.levelup", 0.2, 0.5f, 0.8f));
            if (L >= 3) out.add(snd("block.conduit.activate", T * 0.5, 0.5f, 1.0f));
        }
    }

    // =====================================================================================
    static final class Volcano extends Theme {
        private static final String[] BOMBS = {"magma_block", "magma_cream", "netherrack", "blackstone"};

        Volcano() {
            super("volcano", "Volcano", "A volcano erupts: lava fountains up, glowing bombs arc out and ash rains down.");
        }

        @Override
        public void draw(Ctx c) {
            GuiGraphics g = c.g;
            float s = sc(c);
            float peakX = c.w / 2f, peakY = c.h * 0.52f;
            tint(c, 0x160603, 0.4f * c.vis);
            // mountain
            float baseY = c.h;
            for (float x = 0; x <= c.w; x += 3) {
                float d = Math.abs(x - peakX);
                float slope = peakY + d * 0.62f;
                if (slope < baseY) box(g, x, slope, 3.2f, baseY - slope, A(c.vis, 0x241a16));
            }
            box(g, peakX - 26 * s, peakY, 52 * s, 6, A(c.vis, 0x3a2420));
            glow(g, peakX, peakY, 60 * s, 0xFF5A1E, (0.4f + 0.2f * (float) Math.sin(c.t * 6)) * c.vis);
            float erupt = clamp(c.t / 0.8) * (c.t > c.T - 1 ? clamp(c.T - c.t) : 1);
            // lava fountain + bombs
            if (c.spawning()) {
                c.bag.acc1 += c.dt * (160 + 60 * c.L) * c.k * erupt;
                while (c.bag.acc1 >= 1) {
                    c.bag.acc1--;
                    Bit b = bit(c.bag.a, 1000);
                    b.x = peakX + (float) c.bag.r.nextGaussian() * 12 * s;
                    b.y = peakY;
                    b.vx = (float) c.bag.r.nextGaussian() * 70 * s;
                    b.vy = -(260 + c.bag.r.nextFloat() * 240) * s * (1 + c.L * 0.1f);
                    b.grav = 440 * s;
                    b.max = b.life = 2.5f;
                    b.size = 1.5f + c.bag.r.nextFloat() * 2.5f;
                    b.kind = c.bag.r.nextInt(9) == 0 ? 1 : 0;
                    if (b.kind == 1) b.item = stack(BOMBS[c.bag.r.nextInt(BOMBS.length)]);
                }
                c.bag.acc2 += c.dt * (20 + 8 * c.L);
                while (c.bag.acc2 >= 1) {
                    c.bag.acc2--;
                    Bit a = bit(c.bag.b, 400);
                    a.x = c.bag.r.nextFloat() * c.w;
                    a.y = -10;
                    a.vy = 20 + c.bag.r.nextFloat() * 40;
                    a.vx = -10 - c.bag.r.nextFloat() * 20;
                    a.max = a.life = 12;
                    a.size = 0.8f + c.bag.r.nextFloat() * 1.5f;
                }
            }
            step(c.bag.a, c.dt, c.h);
            step(c.bag.b, c.dt, c.h);
            for (Bit b : c.bag.b) rect(g, b.x, b.y, b.size, b.size, 0, A(0.4f * c.vis, 0x3a3230));
            for (Bit b : c.bag.a) {
                if (b.kind == 1) {
                    item(g, b.item, b.x, b.y, 0.9f + b.size * 0.1f);
                } else {
                    float q = b.q();
                    int col = q > 0.6f ? 0xFFF2A0 : q > 0.3f ? mix(0xFF6A10, 0xFFF2A0, (q - 0.3f) / 0.3f) : mix(0x7A1008, 0xFF6A10, q / 0.3f);
                    rect(g, b.x, b.y, b.size, b.size, 0, A(c.vis, col));
                }
            }
            c.shake = (3f + c.L) * erupt;
            if (c.flashes && c.t < 0.25) tint(c, 0xFF9A50, (float) (1 - c.t / 0.25) * 0.4f);
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            out.add(snd("entity.generic.explode", 0.0, 0.9f, 0.7f));
            out.add(snd("entity.lightning_bolt.thunder", 0.05, 0.6f, 0.6f));
            Random r = new Random(3);
            for (double t = 0.1; t < T - 0.6; t += 0.3 + r.nextDouble() * 0.3) out.add(snd("block.lava.pop", t, 0.8f, 0.6f + r.nextFloat() * 0.6f));
            for (double t = 0.2; t < T - 0.6; t += 0.8) out.add(snd("block.fire.ambient", t, 0.7f, 0.7f));
            if (L >= 3) out.add(snd("entity.generic.explode", T * 0.5, 0.7f, 0.8f));
        }
    }

    // =====================================================================================
    static final class Tornado extends Theme {
        private static final String[] JUNK = {"dirt", "oak_log", "hay_block", "cobblestone", "oak_planks", "bone", "iron_ingot"};

        Tornado() {
            super("tornado", "Tornado", "A roaring twister sweeps across the screen, flinging dirt, logs and loot in a spiral.");
        }

        @Override
        public void draw(Ctx c) {
            GuiGraphics g = c.g;
            float s = sc(c);
            tint(c, 0x1a1c20, 0.45f * c.vis);
            float cross = clamp(c.t / c.T);
            float fx = -60 + cross * (c.w + 120);
            float groundY = c.h * 0.92f;
            // funnel: stacked ellipses, wide at top
            for (float yy = c.h * 0.08f; yy < groundY; yy += 6) {
                float f = (yy - c.h * 0.08f) / (groundY - c.h * 0.08f); // 0 top .. 1 bottom
                float width = (90 - 70 * f) * s * (1 + 0.15f * (float) Math.sin(c.t * 4 + f * 8));
                float wob = (float) Math.sin(yy * 0.03 + c.t * 8) * 10 * s;
                float a = c.vis * (0.12f + 0.12f * (1 - f));
                rect(g, fx + wob, yy, width, 7, 0, A(a, 0x9aa0ac));
                rect(g, fx + wob, yy, width * 0.6f, 7, 0, A(a, 0x6b7078));
            }
            glow(g, fx, groundY, 70 * s, 0x5a5e66, 0.3f * c.vis);
            // debris spiralling up the funnel
            if (c.spawning()) {
                c.bag.acc1 += c.dt * (40 + 20 * c.L) * c.k;
                while (c.bag.acc1 >= 1) {
                    c.bag.acc1--;
                    Bit b = bit(c.bag.a, 500);
                    b.a = c.bag.r.nextFloat() * 6.28f;
                    b.b = groundY;
                    b.c2 = 0.4f + c.bag.r.nextFloat() * 0.5f;
                    b.max = b.life = 3;
                    b.kind = c.bag.r.nextInt(3) == 0 ? 1 : 0;
                    if (b.kind == 1) b.item = stack(JUNK[c.bag.r.nextInt(JUNK.length)]);
                    b.size = 1 + c.bag.r.nextFloat() * 1.5f;
                    b.col = mix(0x8a7a5a, 0x5a5048, c.bag.r.nextFloat());
                }
            }
            for (Bit b : c.bag.a) {
                b.a += c.dt * 7;
                b.b -= c.dt * (c.h / 2.6f);
                if (b.b < c.h * 0.1f) b.dead = true;
                float f = (b.b - c.h * 0.08f) / (groundY - c.h * 0.08f);
                float width = (90 - 70 * Math.max(0, f)) * s;
                float wob = (float) Math.sin(b.b * 0.03 + c.t * 8) * 10 * s;
                b.x = fx + wob + (float) Math.cos(b.a) * width;
                b.y = b.b + (float) Math.sin(b.a) * width * 0.25f;
            }
            step(c.bag.a, c.dt, c.h);
            for (Bit b : c.bag.a) {
                if (b.kind == 1) item(g, b.item, b.x, b.y, 0.7f + b.size * 0.1f);
                else rect(g, b.x, b.y, b.size * 1.6f, b.size * 1.6f, b.a, A(c.vis, b.col));
            }
            c.shake = 1.5f + 0.5f * c.L;
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            for (double t = 0; t < T - 0.4; t += 1.4) out.add(snd("entity.wither.ambient", t, 0.3f, 0.5f));
            for (double t = 0; t < T - 0.4; t += 0.5) out.add(snd("weather.rain", t, 0.5f, 0.6f));
            out.add(snd("entity.warden.heartbeat", 0.2, 0.5f, 0.5f));
        }
    }

    // =====================================================================================
    static final class NeonCity extends Theme {
        NeonCity() {
            super("neon_city", "Neon City", "A rainy cyberpunk skyline glows with neon windows and signs under falling rain.");
        }

        @Override
        public void start(Ctx c) {
            int n = 26;
            float[] f = new float[n * 2];
            for (int i = 0; i < n; i++) {
                f[i * 2] = 0.3f + c.bag.r.nextFloat() * 0.65f; // height fraction
                f[i * 2 + 1] = c.bag.r.nextInt(6); // neon hue index
            }
            c.bag.f = f;
        }

        @Override
        public void draw(Ctx c) {
            GuiGraphics g = c.g;
            g.fillGradient(0, 0, c.w, c.h, A(0.85f * c.vis, 0x0B0320), A(0.85f * c.vis, 0x3A0A4A));
            // moon glow
            glow(g, c.w * 0.78f, c.h * 0.2f, 40, 0xFF4FA0, 0.3f * c.vis);
            int[] neon = {0xFF2D95, 0x2DE1FF, 0xB46CFF, 0x5CFF9A, 0xFFE45C, 0xFF6A3D};
            float[] f = c.bag.f;
            int n = f.length / 2;
            float bw = c.w / (float) n;
            for (int i = 0; i < n; i++) {
                float bh = f[i * 2] * c.h * 0.7f;
                float bx = i * bw;
                float topY = c.h - bh;
                box(g, bx, topY, bw + 1, bh, A(c.vis, 0x0A0618));
                box(g, bx, topY, bw + 1, 1.5f, A(c.vis * 0.7f, neon[(int) f[i * 2 + 1]]));
                // windows
                int wc = neon[(int) f[i * 2 + 1]];
                for (float wy = topY + 5; wy < c.h - 3; wy += 7) {
                    for (float wx = bx + 3; wx < bx + bw - 3; wx += 6) {
                        if (((int) (wx * 7 + wy * 13 + i) % 5) == 0) continue;
                        boolean lit = ((int) (wx + wy * 3 + i * 2 + c.t * 2) % 7) < 5;
                        rect(g, wx, wy, 3, 3.5f, 0, A(c.vis * (lit ? 0.85f : 0.2f), lit ? wc : 0x332a44));
                    }
                }
            }
            // rain
            if (c.bag.a.isEmpty()) for (int i = 0; i < 160; i++) {
                Bit d = new Bit();
                d.x = c.bag.r.nextFloat() * (c.w + 60);
                d.y = c.bag.r.nextFloat() * c.h;
                d.size = 6 + c.bag.r.nextFloat() * 8;
                c.bag.a.add(d);
            }
            for (Bit d : c.bag.a) {
                d.y += 560 * c.dt;
                d.x -= 90 * c.dt;
                if (d.y > c.h) {
                    d.y = -10;
                    d.x = c.bag.r.nextFloat() * (c.w + 60);
                }
                line(g, d.x, d.y, d.x - d.size * 0.16f, d.y + d.size, 1, A(0.3f * c.vis, 0x8fbaff));
            }
            // flicker neon title bar area
            float flick = ((int) (c.t * 8) % 9) < 7 ? 1 : 0.4f;
            glow(g, c.cx, c.cy, 90, c.color(0), 0.1f * c.vis * flick);
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            int[] bass = {0, 3, 5, 3};
            int i = 0;
            for (double t = 0; t < T - 0.4; t += 0.5, i++) {
                out.add(snd("block.note_block.bass", t, 0.7f, note(bass[i % 4])));
                out.add(snd("block.note_block.hat", t + 0.25, 0.3f, 1.6f));
            }
            out.add(snd("block.beacon.power_select", 0.0, 0.6f, 1.2f));
        }
    }

    // =====================================================================================
    static final class Galaxy extends Theme {
        Galaxy() {
            super("galaxy", "Galaxy", "A spiral galaxy slowly turns, its arms sparkling around a blazing core.");
        }

        @Override
        public void start(Ctx c) {
            for (int i = 0; i < 520; i++) {
                Bit b = new Bit();
                int arm = c.bag.r.nextInt(2);
                float dist = c.bag.r.nextFloat();
                b.b = dist * Math.max(c.w, c.h) * 0.6f + 8;
                b.a = (float) (arm * Math.PI + dist * 7 + c.bag.r.nextGaussian() * 0.25);
                b.col = dist < 0.3f ? 0xFFF4D0 : c.bag.r.nextInt(4) == 0 ? 0x9AC8FF : mix(0x9AC8FF, 0xC89AFF, c.bag.r.nextFloat());
                b.size = 0.8f + c.bag.r.nextFloat() * 1.6f;
                c.bag.a.add(b);
            }
        }

        @Override
        public void draw(Ctx c) {
            GuiGraphics g = c.g;
            tint(c, 0x03030E, 0.7f * c.vis);
            float gx = c.w / 2f, gy = c.h * 0.52f;
            float spin = (float) (c.t * 0.5 + easeOut(c.t / 2) * 0.6);
            glow(g, gx, gy, 70, 0xFFE9B0, 0.4f * c.vis);
            glow(g, gx, gy, 130, 0x6A4AD0, 0.15f * c.vis);
            for (Bit b : c.bag.a) {
                float ang = b.a + spin * (60f / Math.max(20, b.b));
                float x = gx + (float) Math.cos(ang) * b.b;
                float y = gy + (float) Math.sin(ang) * b.b * 0.52f;
                float tw = 0.6f + 0.4f * (float) Math.sin(c.t * 4 + b.b);
                rect(g, x, y, b.size, b.size, 0, A(c.vis * tw, b.col));
            }
            disc(g, gx, gy, 16, 9, A(c.vis, 0xFFF8E0));
            if (c.spawning() && c.bag.r.nextFloat() < c.dt * 8) c.bag.b.add(ThemesA.glint(c.bag.r.nextFloat() * c.w, c.bag.r.nextFloat() * c.h));
            ThemesA.drawGlints(c, c.bag.b);
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            Random r = new Random(5);
            out.add(snd("block.beacon.activate", 0.0, 0.7f, 0.8f));
            for (double t = 0.2; t < T - 0.5; t += 0.5) out.add(snd("block.amethyst_block.chime", t, 0.5f, 0.6f + r.nextFloat() * 0.8f));
            out.add(snd("block.conduit.activate", 0.3, 0.6f, 1.0f));
        }
    }

    // =====================================================================================
    static final class MeteorShower extends Theme {
        MeteorShower() {
            super("meteor_shower", "Meteor Shower", "Meteors streak down and slam into the ground in bursts of sparks.");
        }

        @Override
        public void draw(Ctx c) {
            GuiGraphics g = c.g;
            float s = sc(c);
            tint(c, 0x05060F, 0.6f * c.vis);
            Random rr = new Random(9);
            for (int i = 0; i < 60; i++) rect(g, rr.nextFloat() * c.w, rr.nextFloat() * c.h * 0.8f, 1.2f, 1.2f, 0, A(0.5f * c.vis, 0xFFFFFF));
            float groundY = c.h * 0.9f;
            g.fillGradient(0, (int) groundY, c.w, c.h, A(0, 0x201018), A(0.5f * c.vis, 0x3a1020));
            if (c.spawning()) {
                c.bag.acc1 += c.dt * (3 + 2.5 * c.L) * c.k;
                while (c.bag.acc1 >= 1) {
                    c.bag.acc1--;
                    Bit b = bit(c.bag.a, 60);
                    b.x = c.bag.r.nextFloat() * c.w * 1.2f;
                    b.y = -20;
                    float sp = (320 + c.bag.r.nextFloat() * 200) * s;
                    b.vx = -sp * 0.5f;
                    b.vy = sp;
                    b.max = b.life = 4;
                    b.size = 2 + c.bag.r.nextFloat() * 2.5f;
                    b.col = c.bag.r.nextInt(3) == 0 ? 0x9AD0FF : 0xFFC070;
                }
            }
            for (Bit b : c.bag.a) {
                float groundHit = groundY + c.bag.r.nextFloat();
                if (b.y >= groundHit && !b.dead) {
                    b.dead = true;
                    sparks(c.bag.b, b.x, groundY, 20 + 6 * c.L, 220 * s, 0xFFD2A0, false, c.bag.r);
                    glowFlash.add(new float[]{b.x, groundY, 0});
                }
            }
            step(c.bag.a, c.dt, c.h);
            for (Bit b : c.bag.a) {
                float tx = b.x - b.vx * 0.06f, ty = b.y - b.vy * 0.06f;
                line(g, tx, ty, b.x, b.y, b.size, A(0.5f * c.vis, b.col));
                line(g, (b.x + tx) / 2, (b.y + ty) / 2, b.x, b.y, b.size * 1.6f, A(0.9f * c.vis, 0xFFFFFF));
                rect(g, b.x, b.y, b.size * 1.6f, b.size * 1.6f, 0, A(c.vis, 0xFFFFFF));
            }
            for (int i = glowFlash.size() - 1; i >= 0; i--) {
                float[] fl = glowFlash.get(i);
                fl[2] += c.dt;
                float a = clamp(1 - fl[2] / 0.5) * c.vis;
                glow(g, fl[0], fl[1], 30 * s * (1 + fl[2] * 2), 0xFFE0B0, 0.6f * a);
                if (fl[2] > 0.5f) glowFlash.remove(i);
            }
            step(c.bag.b, c.dt, c.h);
            drawSparks(c, c.bag.b);
            c.shake = Math.min(5, glowFlash.size() * 1.5f);
        }

        private final java.util.List<float[]> glowFlash = new java.util.ArrayList<>();

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            Random r = new Random(L + 1);
            for (double t = 0.1; t < T - 0.8; t += 0.5 / (1 + 0.3 * L)) {
                out.add(snd("entity.firework_rocket.large_blast_far", t + 0.3, 0.5f, 0.6f + r.nextFloat() * 0.4f));
                out.add(snd("entity.generic.explode", t + 0.35, 0.4f, 1.2f + r.nextFloat() * 0.5f));
            }
        }
    }

    // =====================================================================================
    static final class RainbowRoad extends Theme {
        RainbowRoad() {
            super("rainbow_road", "Rainbow Road", "A giant rainbow arcs across the sky with sparkles racing along it.");
        }

        @Override
        public void draw(Ctx c) {
            GuiGraphics g = c.g;
            tint(c, 0x061024, 0.4f * c.vis);
            float cxp = c.w / 2f, base = c.h * 1.05f;
            float R = Math.max(c.w, c.h) * 0.62f;
            int bands = 7;
            float grow = (float) easeOut(c.t / 1.2);
            float bandW = 7 * sc(c);
            int[] rb = {0xFF4040, 0xFF9F40, 0xFFE640, 0x40E060, 0x40C0FF, 0x6040FF, 0xB040FF};
            for (int i = 0; i < bands; i++) {
                float rr = R - i * bandW;
                arc(g, cxp, base, rr, rr, bandW, A(c.vis, rb[i]), Math.PI + (1 - grow) * 0.5, Math.PI * 2 - (1 - grow) * 0.5, 48);
            }
            // sparkles racing along the top band
            if (c.spawning()) {
                c.bag.acc1 += c.dt * (30 + 15 * c.L) * c.k;
                while (c.bag.acc1 >= 1) {
                    c.bag.acc1--;
                    Bit b = bit(c.bag.a, 400);
                    b.a = (float) (Math.PI + c.bag.r.nextFloat() * Math.PI);
                    b.b = R - c.bag.r.nextInt(bands) * bandW;
                    b.c2 = (c.bag.r.nextBoolean() ? 1 : -1) * (0.6f + c.bag.r.nextFloat());
                    b.max = b.life = 1.5f;
                    b.size = 1 + c.bag.r.nextFloat() * 1.5f;
                }
            }
            for (Bit b : c.bag.a) {
                b.a += b.c2 * c.dt;
                b.x = cxp + (float) Math.cos(b.a) * b.b;
                b.y = base + (float) Math.sin(b.a) * b.b;
            }
            step(c.bag.a, c.dt, c.h);
            for (Bit b : c.bag.a) {
                float tw = (float) Math.sin(Math.PI * (1 - b.q()));
                text(g, c.font, "✦", b.x, b.y, b.size, A(tw * c.vis, 0xFFFFFF), 0, true);
            }
            // clouds at the ends
            disc(g, cxp - R, base, 30, 16, A(c.vis * grow, 0xF0F4FF));
            disc(g, cxp + R, base, 30, 16, A(c.vis * grow, 0xF0F4FF));
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            for (int i = 0; i < 7; i++) out.add(snd("block.note_block.bell", 0.1 + i * 0.12, 0.7f, note(6 + i * 2)));
            out.add(snd("entity.player.levelup", 1.0, 0.7f, 1.3f));
            Random r = new Random(4);
            for (double t = 1.2; t < T - 0.5; t += 0.2) out.add(snd("block.amethyst_block.chime", t, 0.4f, 1.0f + r.nextFloat()));
        }
    }

    // =====================================================================================
    static final class PixelParty extends Theme {
        PixelParty() {
            super("pixel", "Pixel Party", "Retro 8-bit: a pixel coin spins while chunky blocks rain and a chiptune plays.");
        }

        @Override
        public void draw(Ctx c) {
            GuiGraphics g = c.g;
            tint(c, 0x0B0B1E, 0.6f * c.vis);
            // big pixel coin spinning in the middle-ish
            float s = sc(c) * (4 + c.L * 0.6f);
            float spin = Math.abs((float) Math.cos(c.t * 4));
            float cw = (0.2f + 0.8f * spin);
            drawCoin(g, c.cx, c.cy, s, cw, c.vis);
            // falling pixel blocks
            if (c.spawning()) {
                c.bag.acc1 += c.dt * (18 + 12 * c.L) * c.k;
                while (c.bag.acc1 >= 1) {
                    c.bag.acc1--;
                    Bit b = bit(c.bag.a, 500);
                    b.x = (int) (c.bag.r.nextInt(c.w / 8)) * 8;
                    b.y = -10;
                    b.vy = 60 + c.bag.r.nextFloat() * 70;
                    b.size = 6 + c.bag.r.nextInt(3) * 2;
                    b.col = PAL[c.bag.r.nextInt(PAL.length)];
                    b.max = b.life = 8;
                }
            }
            step(c.bag.a, c.dt, c.h);
            for (Bit b : c.bag.a) {
                box(g, b.x, b.y, b.size, b.size, A(c.vis, b.col));
                box(g, b.x, b.y, b.size, 1.5f, A(c.vis, 0xFFFFFF));
                box(g, b.x, b.y + b.size - 1.5f, b.size, 1.5f, A(c.vis * 0.5f, 0x000000));
            }
            // scanlines
            for (int y = 0; y < c.h; y += 3) g.fill(0, y, c.w, y + 1, A(0.08f * c.vis, 0x000000));
        }

        private static final int[] PAL = {0xFF4040, 0x40E060, 0x4080FF, 0xFFE640, 0xFF40FF, 0x40E0E0, 0xFFFFFF};

        private static void drawCoin(GuiGraphics g, float cx, float cy, float s, float cw, float vis) {
            // pixel-art coin: ring + $ sign, built from square cells
            float cell = s / 7f;
            int[][] art = {
                    {0, 0, 1, 1, 1, 0, 0},
                    {0, 1, 2, 3, 2, 1, 0},
                    {1, 2, 3, 2, 3, 2, 1},
                    {1, 2, 2, 3, 2, 2, 1},
                    {1, 2, 3, 2, 3, 2, 1},
                    {0, 1, 2, 3, 2, 1, 0},
                    {0, 0, 1, 1, 1, 0, 0},
            };
            for (int yy = 0; yy < 7; yy++) {
                for (int xx = 0; xx < 7; xx++) {
                    int v = art[yy][xx];
                    if (v == 0) continue;
                    int col = v == 1 ? 0x9A6A00 : v == 2 ? 0xFFD54F : 0xFFF4C0;
                    float px = cx + (xx - 3) * cell * cw;
                    rect(g, px, cy + (yy - 3) * cell, cell * cw + 0.5f, cell + 0.5f, 0, A(vis, col));
                }
            }
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            int[] mel = {12, 12, 19, 12, 17, 19, 24, 19};
            int[] bass = {0, 0, 5, 7};
            int i = 0;
            for (double t = 0; t < T - 0.3; t += 0.18, i++) {
                out.add(snd("block.note_block.bit", t, 0.5f, note(mel[i % mel.length])));
                if (i % 2 == 0) out.add(snd("block.note_block.bass", t, 0.6f, note(bass[(i / 2) % bass.length])));
            }
            out.add(snd("entity.experience_orb.pickup", 0.0, 0.7f, 1.4f));
        }
    }

    // =====================================================================================
    static final class Bubbles extends Theme {
        Bubbles() {
            super("bubbles", "Bubbles", "Shimmering soap bubbles drift up and pop into tiny sparkles. Calm and pretty.");
        }

        @Override
        public void draw(Ctx c) {
            GuiGraphics g = c.g;
            g.fillGradient(0, 0, c.w, c.h, A(0.4f * c.vis, 0x08283A), A(0.4f * c.vis, 0x0A4A5A));
            if (c.spawning()) {
                c.bag.acc1 += c.dt * (12 + 9 * c.L) * c.k;
                while (c.bag.acc1 >= 1) {
                    c.bag.acc1--;
                    Bit b = bit(c.bag.a, 300);
                    b.x = c.bag.r.nextFloat() * c.w;
                    b.y = c.h + 20;
                    b.vy = -(25 + c.bag.r.nextFloat() * 55);
                    b.a = c.bag.r.nextFloat() * 6.28f;
                    b.size = 5 + c.bag.r.nextFloat() * (16 + c.L * 2);
                    b.max = b.life = 10;
                    b.col = Fx.hsv(c.bag.r.nextFloat(), 0.35f, 1f);
                }
            }
            for (Bit b : c.bag.a) {
                b.a += c.dt * 1.5f;
                b.x += (float) Math.sin(b.a) * 18 * c.dt;
                if (b.y < c.h * 0.12f && !b.dead) {
                    b.dead = true;
                    sparks(c.bag.b, b.x, b.y, 10, 60, b.col, true, c.bag.r);
                }
            }
            step(c.bag.a, c.dt, c.h);
            for (Bit b : c.bag.a) {
                float a = c.vis * 0.8f;
                int segs = b.size > 14 ? 14 : 10;
                ring(g, b.x, b.y, b.size, b.size, 1.4f, A(a, 0xCFFFFF), segs);
                ring(g, b.x, b.y, b.size * 0.72f, b.size * 0.72f, 1.0f, A(a * 0.5f, b.col), segs);
                disc(g, b.x - b.size * 0.3f, b.y - b.size * 0.35f, b.size * 0.2f, b.size * 0.2f, A(a * 0.8f, 0xFFFFFF), 5);
            }
            step(c.bag.b, c.dt, c.h);
            drawSparks(c, c.bag.b);
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            Random r = new Random(6);
            for (double t = 0; t < T - 0.4; t += 0.25 + r.nextDouble() * 0.2) out.add(snd("block.pointed_dripstone.drip_water_into_cauldron", t, 0.5f, 1.2f + r.nextFloat() * 0.8f));
            for (double t = 0.3; t < T - 0.4; t += 0.6) out.add(snd("entity.item.pickup", t, 0.3f, 1.8f));
            out.add(snd("block.amethyst_block.chime", 0.1, 0.5f, 1.4f));
        }
    }

    // =====================================================================================
    static final class Electric extends Theme {
        Electric() {
            super("electric", "Electric", "Lightning arcs crackle around the screen edges between glowing electric orbs.");
        }

        private static void bolt(GuiGraphics g, float x1, float y1, float x2, float y2, Random r, float vis, float th) {
            int seg = 6;
            float px = x1, py = y1;
            for (int i = 1; i <= seg; i++) {
                float f = i / (float) seg;
                float nx = x1 + (x2 - x1) * f + (i < seg ? (float) r.nextGaussian() * 10 : 0);
                float ny = y1 + (y2 - y1) * f + (i < seg ? (float) r.nextGaussian() * 10 : 0);
                line(g, px, py, nx, ny, th * 2.5f, A(0.25f * vis, 0x6AA0FF));
                line(g, px, py, nx, ny, th, A(vis, 0xDCEBFF));
                px = nx;
                py = ny;
            }
        }

        @Override
        public void start(Ctx c) {
            int n = 5 + c.L;
            float[] f = new float[n * 2];
            for (int i = 0; i < n; i++) {
                f[i * 2] = c.bag.r.nextFloat() * c.w;
                f[i * 2 + 1] = c.bag.r.nextFloat() * c.h;
            }
            c.bag.f = f;
        }

        @Override
        public void draw(Ctx c) {
            GuiGraphics g = c.g;
            tint(c, 0x05070F, 0.55f * c.vis);
            float[] f = c.bag.f;
            int n = f.length / 2;
            Random r = new Random((long) (c.t * 30));
            // orbs
            for (int i = 0; i < n; i++) {
                float x = f[i * 2] + (float) Math.sin(c.t * 1.5 + i) * 12;
                float y = f[i * 2 + 1] + (float) Math.cos(c.t * 1.2 + i) * 12;
                f[i * 2] = x - (float) Math.sin(c.t * 1.5 + i) * 12;
                f[i * 2 + 1] = y - (float) Math.cos(c.t * 1.2 + i) * 12;
                glow(g, x, y, 22, 0x6AB0FF, (0.4f + 0.2f * (float) Math.sin(c.t * 8 + i)) * c.vis);
                disc(g, x, y, 5, 5, A(c.vis, 0xEAF4FF), 10);
            }
            // arcs between nearby orbs + from edges
            for (int i = 0; i < n; i++) {
                float ax = f[i * 2] + (float) Math.sin(c.t * 1.5 + i) * 12, ay = f[i * 2 + 1] + (float) Math.cos(c.t * 1.2 + i) * 12;
                for (int j = i + 1; j < n; j++) {
                    float bx = f[j * 2] + (float) Math.sin(c.t * 1.5 + j) * 12, by = f[j * 2 + 1] + (float) Math.cos(c.t * 1.2 + j) * 12;
                    float d = (float) Math.hypot(ax - bx, ay - by);
                    if (d < c.w * 0.45f && r.nextInt(3) == 0) bolt(g, ax, ay, bx, by, r, c.vis, 1.4f);
                }
                if (r.nextInt(4) == 0) {
                    int edge = r.nextInt(4);
                    float ex = edge == 2 ? 0 : edge == 3 ? c.w : r.nextFloat() * c.w;
                    float ey = edge == 0 ? 0 : edge == 1 ? c.h : r.nextFloat() * c.h;
                    bolt(g, ex, ey, ax, ay, r, c.vis, 1.2f);
                }
            }
            // border crackle
            float border = 0.4f + 0.3f * (float) Math.sin(c.t * 20);
            int bc = A(0.15f * c.vis * border, 0x6AB0FF);
            g.fill(0, 0, c.w, 3, bc);
            g.fill(0, c.h - 3, c.w, c.h, bc);
            g.fill(0, 0, 3, c.h, bc);
            g.fill(c.w - 3, 0, c.w, c.h, bc);
            if (c.flashes && r.nextInt(14) == 0) tint(c, 0xBFD6FF, 0.2f * c.vis);
            c.shake = r.nextInt(8) == 0 ? 1.5f + c.L * 0.3f : 0;
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            Random r = new Random(L + 2);
            for (double t = 0; t < T - 0.3; t += 0.18 + r.nextDouble() * 0.2) out.add(snd("entity.lightning_bolt.impact", t, 0.4f, 1.3f + r.nextFloat() * 0.6f));
            for (double t = 0.1; t < T - 0.3; t += 0.5) out.add(snd("block.conduit.ambient", t, 0.4f, 1.4f));
            out.add(snd("block.beacon.activate", 0.0, 0.6f, 1.6f));
        }
    }

    // =====================================================================================
    static final class Sakura extends Theme {
        Sakura() {
            super("sakura", "Cherry Blossom", "Pink sakura petals drift gently down past a blossoming branch. Peaceful.");
        }

        @Override
        public void draw(Ctx c) {
            GuiGraphics g = c.g;
            g.fillGradient(0, 0, c.w, c.h, A(0.4f * c.vis, 0x3A1A2E), A(0.4f * c.vis, 0x5A2A3A));
            // branch across the top-left
            int branch = 0x2A1810;
            line(g, 0, c.h * 0.1f, c.w * 0.4f, c.h * 0.22f, 4, A(c.vis, branch));
            line(g, c.w * 0.2f, c.h * 0.16f, c.w * 0.28f, c.h * 0.05f, 2.5f, A(c.vis, branch));
            line(g, c.w * 0.33f, c.h * 0.2f, c.w * 0.45f, c.h * 0.12f, 2.5f, A(c.vis, branch));
            Random r = new Random(3);
            for (int i = 0; i < 40; i++) {
                float bx = r.nextFloat() * c.w * 0.45f, by = c.h * (0.05f + r.nextFloat() * 0.18f);
                disc(g, bx, by, 3 + r.nextFloat() * 2, 2.5f, A(c.vis * 0.9f, r.nextBoolean() ? 0xFFC0D8 : 0xFFD8E8), 6);
            }
            if (c.spawning()) {
                c.bag.acc1 += c.dt * (14 + 9 * c.L) * c.k;
                while (c.bag.acc1 >= 1) {
                    c.bag.acc1--;
                    Bit b = bit(c.bag.a, 500);
                    b.x = c.bag.r.nextFloat() * c.w;
                    b.y = -8;
                    b.vy = 22 + c.bag.r.nextFloat() * 32;
                    b.a = c.bag.r.nextFloat() * 6.28f;
                    b.size = 2.5f + c.bag.r.nextFloat() * 2.5f;
                    b.max = b.life = 14;
                    b.col = c.bag.r.nextInt(3) == 0 ? 0xFFFFFF : c.bag.r.nextBoolean() ? 0xFFB6CE : 0xFFCADD;
                }
            }
            for (Bit b : c.bag.a) {
                b.a += c.dt * 2;
                b.x += (float) Math.sin(b.a) * 26 * c.dt;
            }
            step(c.bag.a, c.dt, c.h);
            for (Bit b : c.bag.a) {
                float flip = 0.3f + 0.7f * Math.abs((float) Math.cos(b.a));
                disc(g, b.x, b.y, b.size * flip, b.size, A(c.vis * 0.95f, b.col), 6);
                rect(g, b.x, b.y, b.size * 0.4f * flip, b.size * 1.6f, b.a, A(c.vis * 0.3f, 0xD06A90));
            }
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            int[] koto = {0, 2, 4, 7, 9, 12, 9, 7};
            Random r = new Random(8);
            int i = 0;
            for (double t = 0; t < T - 0.4; t += 0.5, i++) out.add(snd("block.note_block.harp", t, 0.6f, note(koto[i % koto.length])));
            for (double t = 0.25; t < T - 0.4; t += 1.0) out.add(snd("block.note_block.flute", t, 0.4f, note(12)));
            out.add(snd("block.amethyst_block.chime", 0.0, 0.4f, 0.8f));
        }
    }

    // =====================================================================================
    static final class Fireflies extends Theme {
        Fireflies() {
            super("fireflies", "Fireflies", "Glowing fireflies drift and blink through a calm dark meadow at night.");
        }

        @Override
        public void start(Ctx c) {
            int n = 30 + 12 * c.L;
            for (int i = 0; i < n; i++) {
                Bit b = new Bit();
                b.x = c.bag.r.nextFloat() * c.w;
                b.y = c.bag.r.nextFloat() * c.h;
                b.a = c.bag.r.nextFloat() * 6.28f;
                b.b = c.bag.r.nextFloat() * 6.28f; // blink phase
                b.size = 1.2f + c.bag.r.nextFloat() * 1.3f;
                c.bag.a.add(b);
            }
        }

        @Override
        public void draw(Ctx c) {
            GuiGraphics g = c.g;
            g.fillGradient(0, 0, c.w, c.h, A(0.7f * c.vis, 0x030A08), A(0.7f * c.vis, 0x0A1810));
            // grass silhouette
            Random r = new Random(2);
            for (float x = 0; x < c.w; x += 5) {
                float gh = 10 + r.nextFloat() * 24;
                line(g, x, c.h, x + (float) Math.sin(c.t + x) * 3, c.h - gh, 2, A(c.vis, 0x05140A));
            }
            for (Bit b : c.bag.a) {
                b.a += c.dt * 0.8f;
                b.x += (float) Math.cos(b.a) * 22 * c.dt;
                b.y += (float) Math.sin(b.a * 1.3f) * 18 * c.dt;
                if (b.x < 0) b.x += c.w;
                if (b.x > c.w) b.x -= c.w;
                if (b.y < 0) b.y += c.h;
                if (b.y > c.h) b.y -= c.h;
                float blink = (float) Math.max(0, Math.sin(c.t * 3 + b.b));
                blink *= blink;
                glow(g, b.x, b.y, 10 * b.size, 0xCFFF6A, 0.5f * blink * c.vis);
                rect(g, b.x, b.y, b.size, b.size, 0, A(c.vis * (0.3f + 0.7f * blink), 0xF0FFC0));
            }
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            Random r = new Random(5);
            for (double t = 0; t < T - 0.5; t += 0.6 + r.nextDouble() * 0.5) out.add(snd("block.note_block.harp", t, 0.4f, note(new int[]{0, 4, 7, 12, 16}[r.nextInt(5)])));
            for (double t = 0.3; t < T - 0.5; t += 1.3) out.add(snd("entity.allay.ambient_without_item", t, 0.4f, 1.0f));
            out.add(snd("block.amethyst_block.chime", 0.0, 0.4f, 1.2f));
        }
    }

    // =====================================================================================
    static final class Casino extends Theme {
        Casino() {
            super("casino", "Big Win", "Playing cards fan out and fly while gold chips rain and the lights spell BIG WIN.");
        }

        @Override
        public void draw(Ctx c) {
            GuiGraphics g = c.g;
            float s = sc(c);
            g.fillGradient(0, 0, c.w, c.h, A(0.6f * c.vis, 0x0A1A0E), A(0.6f * c.vis, 0x05300F));
            // spotlight rays from top
            for (int i = 0; i < 5; i++) {
                float ang = (float) (Math.PI / 2 + Math.sin(c.t * 1.2 + i) * 0.6);
                rect(g, c.w / 2f + (float) Math.cos(ang) * c.h * 0.5f, c.h * 0.5f, c.h * 1.1f, 20 + i * 6, (float) ang, A(0.04f * c.vis, 0xFFF0A0));
            }
            // cards fanning / flying from center
            if (c.spawning()) {
                c.bag.acc1 += c.dt * (8 + 6 * c.L) * c.k;
                while (c.bag.acc1 >= 1) {
                    c.bag.acc1--;
                    Bit b = bit(c.bag.a, 160);
                    double ang = -Math.PI / 2 + c.bag.r.nextGaussian() * 0.8;
                    float sp = 150 + c.bag.r.nextFloat() * 220;
                    b.x = c.cx;
                    b.y = c.cy;
                    b.vx = (float) Math.cos(ang) * sp;
                    b.vy = (float) Math.sin(ang) * sp;
                    b.grav = 260;
                    b.vr = (c.bag.r.nextFloat() - 0.5f) * 10;
                    b.max = b.life = 3;
                    b.kind = c.bag.r.nextInt(4); // suit
                }
                c.bag.acc2 += c.dt * (14 + 10 * c.L);
                while (c.bag.acc2 >= 1) {
                    c.bag.acc2--;
                    Bit b = bit(c.bag.b, 300);
                    b.x = c.bag.r.nextFloat() * c.w;
                    b.y = -8;
                    b.vy = 70 + c.bag.r.nextFloat() * 90;
                    b.vx = (float) c.bag.r.nextGaussian() * 20;
                    b.max = b.life = 6;
                    b.size = 3 + c.bag.r.nextFloat() * 2;
                    b.col = c.bag.r.nextInt(3) == 0 ? 0xFFFFFF : c.bag.r.nextBoolean() ? 0xFFD54F : 0xC98A00;
                }
            }
            step(c.bag.b, c.dt, c.h);
            for (Bit b : c.bag.b) {
                disc(g, b.x, b.y, b.size, b.size * 0.5f, A(c.vis, b.col), 6);
                ring(g, b.x, b.y, b.size, b.size * 0.5f, 1, A(c.vis, 0x8A5A00), 10);
            }
            step(c.bag.a, c.dt, c.h);
            String[] suit = {"♠", "♥", "♦", "♣"};
            for (Bit b : c.bag.a) {
                g.pose().pushMatrix();
                g.pose().translate(b.x, b.y);
                g.pose().rotate(b.rot);
                float cwf = 12 * s, chf = 17 * s;
                rect(g, 0, 0, cwf, chf, 0, A(c.vis, 0xFFFFFF));
                rect(g, 0, 0, cwf - 2, chf - 2, 0, A(c.vis, 0xF4F4FA));
                int col = (b.kind == 1 || b.kind == 2) ? 0xD02030 : 0x101014;
                text(g, c.font, suit[b.kind], 0, 0, s * 1.1f, A(c.vis, col), 0, false);
                g.pose().popMatrix();
            }
            if (c.flashes && ((int) (c.t * 6) % 2 == 0)) glow(g, c.cx, c.cy, 80, 0xFFE45C, 0.12f * c.vis);
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            out.add(snd("entity.player.levelup", 0.0, 0.9f, 1.2f));
            out.add(snd("ui.toast.challenge_complete", 0.1, 0.8f, 1.0f));
            Random r = new Random(7);
            for (double t = 0.1; t < T - 0.5; t += 0.09) out.add(snd("block.note_block.hat", t, 0.3f, 1.3f + r.nextFloat() * 0.5f));
            for (double t = 0.2; t < T - 0.5; t += 0.4) out.add(snd("block.amethyst_block.chime", t, 0.5f, 1.0f + r.nextFloat()));
            for (double t = 0.3; t < T - 0.5; t += 0.3) out.add(snd("entity.experience_orb.pickup", t, 0.4f, 1.0f + r.nextFloat()));
        }
    }

    // =====================================================================================
    static final class Comet extends Theme {
        Comet() {
            super("comet", "Comet", "A blazing comet streaks across the sky trailing fire, then bursts into stars.");
        }

        @Override
        public void draw(Ctx c) {
            GuiGraphics g = c.g;
            float s = sc(c);
            tint(c, 0x04040E, 0.6f * c.vis);
            Random rr = new Random(11);
            for (int i = 0; i < 70; i++) rect(g, rr.nextFloat() * c.w, rr.nextFloat() * c.h, 1.1f, 1.1f, 0, A(0.5f * c.vis, 0xFFFFFF));
            double fly = c.T * 0.6;
            float burstX = c.w * 0.72f, burstY = c.h * 0.38f;
            if (c.t < fly) {
                float f = (float) (c.t / fly);
                float x = -40 + f * (burstX + 40);
                float y = c.h * 0.9f - f * (c.h * 0.9f - burstY);
                // tail
                for (int i = 1; i <= 24; i++) {
                    float tf = i / 24f;
                    float tx = x - (x - (-40)) * tf * 0.12f;
                    float ty = y + (c.h * 0.9f - y) * tf * 0.12f;
                    float sz = (10 - tf * 9) * s;
                    int col = tf < 0.3f ? 0xFFF4C0 : mix(0xFF8030, 0x6A2000, (tf - 0.3f) / 0.7f);
                    rect(g, tx, ty, sz, sz, 0, A(c.vis * (1 - tf) * 0.8f, col));
                }
                glow(g, x, y, 24 * s, 0xFFE0A0, 0.7f * c.vis);
                disc(g, x, y, 7 * s, 7 * s, A(c.vis, 0xFFFFFF), 10);
                c.bag.extra = new float[]{x, y};
            } else if (c.bag.last < 0) {
                c.bag.last = 1;
                int n = (int) ((120 + 50 * c.L) * Math.min(2, c.k));
                for (int i = 0; i < n; i++) {
                    Bit b = bit(c.bag.a, 800);
                    double a = c.bag.r.nextDouble() * Math.PI * 2;
                    double sp = (60 + c.bag.r.nextDouble() * 320) * s;
                    b.x = burstX;
                    b.y = burstY;
                    b.vx = (float) (Math.cos(a) * sp);
                    b.vy = (float) (Math.sin(a) * sp);
                    b.drag = 1.1f;
                    b.max = b.life = 1.5f + c.bag.r.nextFloat() * 1.3f;
                    b.size = 1 + c.bag.r.nextFloat() * 1.8f;
                    b.col = c.bag.r.nextInt(3) == 0 ? 0xFFFFFF : c.bag.r.nextBoolean() ? 0xFFE0A0 : c.color(0);
                }
                c.shake = 4 + c.L;
                if (c.flashes) tint(c, 0xFFF0D0, 0.5f * c.vis);
            }
            step(c.bag.a, c.dt, c.h);
            for (Bit b : c.bag.a) {
                float q = b.q();
                line(g, b.x, b.y, b.x - b.vx * 0.04f, b.y - b.vy * 0.04f, b.size, A(q * c.vis, b.col));
                rect(g, b.x, b.y, b.size * 1.4f, b.size * 1.4f, 0, A(q * c.vis, 0xFFFFFF));
            }
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            out.add(snd("entity.firework_rocket.launch", 0.0, 0.6f, 0.5f));
            out.add(snd("block.respawn_anchor.charge", 0.2, 0.5f, 0.8f));
            double boom = T * 0.6;
            out.add(snd("entity.firework_rocket.large_blast", boom, 1.0f, 0.9f));
            out.add(snd("entity.generic.explode", boom, 0.5f, 1.2f));
            out.add(snd("entity.firework_rocket.twinkle", boom + 0.4, 0.8f, 1.0f));
            if (L >= 2) out.add(snd("entity.firework_rocket.twinkle_far", boom + 0.8, 0.8f, 1.0f));
        }
    }

    // =====================================================================================
    static final class Plasma extends Theme {
        Plasma() {
            super("plasma", "Plasma", "Psychedelic blobs of molten color morph and flow across the whole screen.");
        }

        @Override
        public void draw(Ctx c) {
            GuiGraphics g = c.g;
            // layered moving blobs; additive-ish with alpha
            int blobs = 7 + c.L;
            for (int i = 0; i < blobs; i++) {
                float ph = i * 1.7f;
                float x = c.w * (0.5f + 0.42f * (float) Math.sin(c.t * (0.4 + i * 0.05) + ph));
                float y = c.h * (0.5f + 0.42f * (float) Math.cos(c.t * (0.35 + i * 0.045) + ph * 1.3f));
                float rad = (60 + 40 * (float) Math.sin(c.t * 0.7 + i)) * sc(c) + 40;
                int col = c.rainbow ? Fx.hsv((float) ((c.t * 0.1 + i / (float) blobs) % 1.0), 0.8f, 1f)
                        : Fx.hsv((float) ((i / (float) blobs + c.t * 0.05) % 1.0), 0.7f, 1f);
                disc(g, x, y, rad, rad, A(0.16f * c.vis, col), 12);
                disc(g, x, y, rad * 0.5f, rad * 0.5f, A(0.16f * c.vis, 0xFFFFFF), 10);
            }
            // subtle scan ripple
            for (int y = 0; y < c.h; y += 4) {
                float a = 0.04f + 0.04f * (float) Math.sin(y * 0.1 + c.t * 4);
                g.fill(0, y, c.w, y + 1, A(a * c.vis, 0x000000));
            }
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            Random r = new Random(9);
            for (double t = 0; t < T - 0.4; t += 0.3) out.add(snd("block.note_block.didgeridoo", t, 0.5f, note(r.nextInt(12))));
            for (double t = 0.15; t < T - 0.4; t += 0.5) out.add(snd("block.note_block.flute", t, 0.4f, note(12 + r.nextInt(12))));
            out.add(snd("block.beacon.activate", 0.0, 0.5f, 0.7f));
        }
    }

    // =====================================================================================
    static final class Phoenix extends Theme {
        Phoenix() {
            super("phoenix", "Phoenix", "A great firebird rises from flames, wings beating, and soars upward trailing embers.");
        }

        @Override
        public void draw(Ctx c) {
            GuiGraphics g = c.g;
            float s = sc(c);
            tint(c, 0x160402, 0.45f * c.vis);
            float rise = (float) easeOut(Math.min(1, c.t / (c.T * 0.7)));
            float bx = c.w / 2f + (float) Math.sin(c.t * 1.2) * c.w * 0.12f;
            float by = c.h * (1.0f - rise * 0.75f);
            float flap = (float) Math.sin(c.t * 7);
            float wing = (40 + c.L * 6) * s;
            // body
            glow(g, bx, by, 40 * s, 0xFF8A2A, 0.6f * c.vis);
            disc(g, bx, by, 8 * s, 12 * s, A(c.vis, 0xFFD24A), 12);
            // head + beak
            disc(g, bx, by - 13 * s, 5 * s, 5 * s, A(c.vis, 0xFFE27A), 8);
            rect(g, bx, by - 13 * s, 5 * s, 2.5f * s, 0, A(c.vis, 0xFF6A10));
            // tail flames
            for (int i = 0; i < 5; i++) {
                float ta = (float) (Math.PI / 2 + (i - 2) * 0.22 + Math.sin(c.t * 4 + i) * 0.1);
                float tl = (30 + i % 2 * 14) * s;
                int col = i % 2 == 0 ? 0xFF6A10 : 0xFFD24A;
                line(g, bx, by + 8 * s, bx + (float) Math.cos(ta) * tl, by + 8 * s + (float) Math.sin(ta) * tl, 4 * s, A(c.vis, col));
            }
            // wings (feather fans) each side
            for (int side = -1; side <= 1; side += 2) {
                float lift = flap * 0.5f;
                for (int fth = 0; fth < 6; fth++) {
                    float ang = side * (0.2f + fth * 0.22f) - lift * side;
                    float len = wing * (1 - fth * 0.08f);
                    float ex = bx + side * (float) Math.cos(ang) * len;
                    float ey = by - 2 * s + (float) Math.sin(ang - Math.PI) * len * 0.5f + fth * 2;
                    int col = fth < 2 ? 0xFFE27A : fth < 4 ? 0xFF8A2A : 0xC2420A;
                    line(g, bx, by - 2 * s, ex, ey, (4 - fth * 0.4f) * s, A(c.vis, col));
                }
            }
            // embers
            if (c.spawning()) {
                c.bag.acc1 += c.dt * (40 + 20 * c.L) * c.k;
                while (c.bag.acc1 >= 1) {
                    c.bag.acc1--;
                    Bit b = bit(c.bag.a, 600);
                    b.x = bx + (float) c.bag.r.nextGaussian() * wing * 0.6f;
                    b.y = by + (float) c.bag.r.nextGaussian() * 10;
                    b.vy = 30 + c.bag.r.nextFloat() * 60;
                    b.vx = (float) c.bag.r.nextGaussian() * 20;
                    b.max = b.life = 1.5f;
                    b.size = 1 + c.bag.r.nextFloat() * 1.8f;
                    b.col = c.bag.r.nextBoolean() ? 0xFF8A2A : 0xFFD24A;
                }
            }
            step(c.bag.a, c.dt, c.h);
            for (Bit b : c.bag.a) rect(g, b.x, b.y, b.size, b.size, 0, A(b.q() * c.vis, b.col));
            if (c.flashes && c.t < 0.3) tint(c, 0xFFB060, (float) (1 - c.t / 0.3) * 0.4f);
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            out.add(snd("item.firecharge.use", 0.0, 0.9f, 0.8f));
            out.add(snd("entity.blaze.ambient", 0.1, 0.7f, 1.2f));
            for (double t = 0.1; t < T - 0.5; t += 0.5) out.add(snd("entity.ender_dragon.flap", t, 0.7f, 1.3f));
            for (double t = 0.2; t < T - 0.5; t += 0.7) out.add(snd("block.fire.ambient", t, 0.6f, 1.0f));
            out.add(snd("entity.player.levelup", 0.3, 0.6f, 1.4f));
            if (L >= 3) out.add(snd("entity.ender_dragon.growl", T * 0.5, 0.4f, 1.5f));
        }
    }

    // =====================================================================================
    static final class Wormhole extends Theme {
        Wormhole() {
            super("wormhole", "Wormhole", "A swirling blue-and-orange wormhole pulls in light, then blasts particles back out.");
        }

        @Override
        public void draw(Ctx c) {
            GuiGraphics g = c.g;
            float cxp = c.w / 2f, cyp = c.h * 0.54f;
            tint(c, 0x02030C, 0.6f * c.vis);
            double blast = c.T - 1.4;
            // tunnel rings receding
            int rings = 16;
            for (int i = 0; i < rings; i++) {
                float f = ((i + (float) (c.t * 2) % 1) / rings);
                float rad = f * f * Math.max(c.w, c.h) * 0.75f + 6;
                int col = (i % 2 == 0) ? 0x2E7BFF : 0xFF7A2E;
                float a = c.vis * (0.1f + 0.4f * (1 - f));
                ring(g, cxp, cyp, rad, rad * 0.8f, 2 + (1 - f) * 3, A(a, col), 40);
            }
            glow(g, cxp, cyp, 60, 0xBFE0FF, (0.4f + 0.15f * (float) Math.sin(c.t * 8)) * c.vis);
            disc(g, cxp, cyp, 14, 11, A(c.vis, 0xEAF4FF), 12);
            if (c.t < blast) {
                // suck in
                c.bag.acc1 += c.dt * (90 + 30 * c.L);
                while (c.bag.acc1 >= 1) {
                    c.bag.acc1--;
                    Bit b = bit(c.bag.a, 700);
                    b.a = c.bag.r.nextFloat() * 6.28f;
                    b.b = Math.max(c.w, c.h) * (0.5f + c.bag.r.nextFloat() * 0.2f);
                    b.col = c.bag.r.nextBoolean() ? 0x6AB0FF : 0xFFB06A;
                    b.max = b.life = 4;
                }
                for (Bit b : c.bag.a) {
                    b.b -= c.dt * (180 + 20000f / Math.max(30, b.b));
                    b.a += c.dt * (60f / Math.max(20, b.b));
                    if (b.b < 12) b.dead = true;
                    b.x = cxp + (float) Math.cos(b.a) * b.b;
                    b.y = cyp + (float) Math.sin(b.a) * b.b * 0.8f;
                }
            } else {
                if (c.bag.last < 0) {
                    c.bag.last = 1;
                    c.bag.a.clear();
                    int n = (int) ((160 + 60 * c.L) * Math.min(2, c.k));
                    for (int i = 0; i < n; i++) {
                        Bit b = bit(c.bag.a, 800);
                        double a = c.bag.r.nextDouble() * Math.PI * 2;
                        double sp = 100 + c.bag.r.nextDouble() * 400;
                        b.x = cxp;
                        b.y = cyp;
                        b.vx = (float) (Math.cos(a) * sp);
                        b.vy = (float) (Math.sin(a) * sp);
                        b.drag = 1.0f;
                        b.max = b.life = 1.8f;
                        b.size = 1 + c.bag.r.nextFloat() * 1.6f;
                        b.col = c.bag.r.nextBoolean() ? 0x6AB0FF : 0xFFB06A;
                    }
                    c.shake = 4 + c.L;
                    if (c.flashes) tint(c, 0xDCEBFF, 0.5f * c.vis);
                }
                step(c.bag.a, c.dt, c.h);
            }
            if (c.t < blast) step(c.bag.a, c.dt, c.h);
            for (Bit b : c.bag.a) {
                if (c.t >= blast) line(g, b.x, b.y, b.x - b.vx * 0.04f, b.y - b.vy * 0.04f, b.size, A(b.q() * c.vis, b.col));
                rect(g, b.x, b.y, b.size * 1.4f, b.size * 1.4f, 0, A(c.vis * (c.t >= blast ? b.q() : Math.min(1, b.b / 50f)), b.col));
            }
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            out.add(snd("block.portal.trigger", 0.0, 0.5f, 1.0f));
            for (double t = 0; t < T - 1.5; t += 0.6) out.add(snd("block.portal.ambient", t, 0.5f, 1.2f));
            out.add(snd("block.respawn_anchor.charge", 0.3, 0.6f, 0.8f));
            double blast = T - 1.4;
            out.add(snd("entity.generic.explode", blast, 0.7f, 0.9f));
            out.add(snd("block.beacon.activate", blast, 0.8f, 1.4f));
            out.add(snd("entity.firework_rocket.large_blast", blast + 0.05, 0.7f, 1.0f));
        }
    }

    // =====================================================================================
    static final class Balloons extends Theme {
        Balloons() {
            super("balloons", "Balloon Party", "Colorful balloons and streamers float up in a cheerful carnival burst.");
        }

        @Override
        public void draw(Ctx c) {
            GuiGraphics g = c.g;
            float s = sc(c);
            g.fillGradient(0, 0, c.w, c.h, A(0.3f * c.vis, 0x102A4A), A(0.15f * c.vis, 0x3A5A8A));
            if (c.spawning()) {
                c.bag.acc1 += c.dt * (5 + 4 * c.L) * c.k;
                while (c.bag.acc1 >= 1) {
                    c.bag.acc1--;
                    Bit b = bit(c.bag.a, 120);
                    b.x = c.bag.r.nextFloat() * c.w;
                    b.y = c.h + 30;
                    b.vy = -(40 + c.bag.r.nextFloat() * 50);
                    b.a = c.bag.r.nextFloat() * 6.28f;
                    b.size = 12 + c.bag.r.nextFloat() * (10 + c.L * 2);
                    b.max = b.life = 10;
                    b.col = PARTY[c.bag.r.nextInt(PARTY.length)];
                }
                c.bag.acc2 += c.dt * (12 + 8 * c.L);
                while (c.bag.acc2 >= 1) {
                    c.bag.acc2--;
                    Bit b = bit(c.bag.b, 400);
                    b.x = c.bag.r.nextFloat() * c.w;
                    b.y = -8;
                    b.vy = 40 + c.bag.r.nextFloat() * 60;
                    b.vx = (float) c.bag.r.nextGaussian() * 25;
                    b.rot = c.bag.r.nextFloat() * 6;
                    b.vr = (c.bag.r.nextFloat() - 0.5f) * 12;
                    b.max = b.life = 7;
                    b.col = PARTY[c.bag.r.nextInt(PARTY.length)];
                }
            }
            step(c.bag.b, c.dt, c.h);
            for (Bit b : c.bag.b) rect(g, b.x, b.y, 3 + 3 * Math.abs((float) Math.cos(b.rot)), 5, b.rot, A(c.vis, b.col));
            for (Bit b : c.bag.a) {
                b.a += c.dt * 1.5f;
                b.x += (float) Math.sin(b.a) * 12 * c.dt;
            }
            step(c.bag.a, c.dt, c.h);
            for (Bit b : c.bag.a) {
                float sway = (float) Math.sin(b.a) * 0.15f;
                // string
                line(g, b.x, b.y + b.size * 0.9f, b.x - sway * 20, b.y + b.size * 2.2f, 1, A(c.vis * 0.6f, 0xDDDDDD));
                disc(g, b.x, b.y, b.size * 0.8f, b.size, A(c.vis, b.col), 14);
                disc(g, b.x - b.size * 0.25f, b.y - b.size * 0.3f, b.size * 0.22f, b.size * 0.26f, A(c.vis * 0.7f, 0xFFFFFF), 8);
                rect(g, b.x, b.y + b.size * 0.92f, 2.5f, 2.5f, 0.78f, A(c.vis, b.col));
            }
        }

        private static final int[] PARTY = {0xFF4D6A, 0x4DA6FF, 0xFFD24D, 0x6AE06A, 0xB46CFF, 0xFF8A4D};

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            int[] mel = {12, 16, 19, 16, 17, 21, 19, 12};
            int i = 0;
            for (double t = 0; t < T - 0.3; t += 0.28, i++) {
                out.add(snd("block.note_block.pling", t, 0.6f, note(mel[i % mel.length])));
                if (i % 4 == 0) out.add(snd("block.note_block.bell", t, 0.5f, note(12)));
            }
            out.add(snd("entity.player.levelup", 0.0, 0.7f, 1.3f));
        }
    }

    // =====================================================================================
    static final class Equalizer extends Theme {
        private static final double BEAT = 0.4;

        Equalizer() {
            super("equalizer", "Equalizer", "Neon sound bars dance to a pumping beat with a glowing waveform.");
        }

        @Override
        public void draw(Ctx c) {
            GuiGraphics g = c.g;
            float phase = (float) ((c.t % BEAT) / BEAT);
            float env = (float) Math.exp(-phase * 4);
            g.fillGradient(0, 0, c.w, c.h, A(0.7f * c.vis, 0x0A0220), A(0.5f * c.vis, 0x200A3A));
            glow(g, c.cx, c.h * 0.5f, c.w * 0.5f, c.color(0), 0.08f * c.vis * (0.5f + env));
            int bars = Math.min(40, c.w / 10);
            float bw = c.w / (float) bars;
            float baseY = c.h * 0.9f;
            for (int i = 0; i < bars; i++) {
                float freq = 0.5f + 1.5f * (1 - Math.abs(i - bars / 2f) / (bars / 2f));
                float hgt = (c.h * 0.5f) * (0.15f + 0.85f * Math.abs((float) Math.sin(c.t * freq * 3 + i * 0.5)) * (0.4f + 0.6f * env));
                int col = Fx.hsv((float) ((i / (float) bars + c.t * 0.1) % 1.0), 0.85f, 1f);
                float x = i * bw;
                g.fillGradient((int) x + 1, (int) (baseY - hgt), (int) (x + bw - 1), (int) baseY, A(c.vis, col), A(c.vis * 0.4f, col));
                rect(g, x + bw / 2, baseY - hgt, bw - 2, 3, 0, A(c.vis, 0xFFFFFF));
                // mirror reflection
                g.fillGradient((int) x + 1, (int) baseY, (int) (x + bw - 1), (int) (baseY + hgt * 0.4f), A(c.vis * 0.3f, col), A(0, col));
            }
            // waveform line across the middle
            float midY = c.h * 0.4f;
            float px = 0, py = midY;
            for (float x = 0; x <= c.w; x += 6) {
                float y = midY + (float) Math.sin(x * 0.05 + c.t * 8) * 16 * (0.5f + env);
                line(g, px, py, x, y, 2, A(c.vis * 0.8f, 0x4DE1FF));
                px = x;
                py = y;
            }
            if (phase < 0.1f) { // kick confetti
                c.shake = 1.5f + c.L * 0.3f;
            }
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            int[] bass = {0, 0, 3, 5, 0, 0, 7, 5};
            int[] lead = {12, 15, 19, 24, 22, 19, 15, 17};
            int i = 0;
            for (double t = 0; t < T - 0.3; t += BEAT, i++) {
                out.add(snd("block.note_block.basedrum", t, 0.9f, 1.0f));
                out.add(snd("block.note_block.hat", t + BEAT / 2, 0.4f, 1.7f));
                if (i % 2 == 1) out.add(snd("block.note_block.snare", t, 0.6f, 1.0f));
                out.add(snd("block.note_block.bass", t, 0.8f, note(bass[i % bass.length])));
                out.add(snd("block.note_block.bit", t, 0.4f, note(lead[i % lead.length])));
            }
        }
    }

    // =====================================================================================
    static final class Starfall extends Theme {
        Starfall() {
            super("starfall", "Starfall", "Shooting stars rain across a night sky, then stars connect into a glowing heart.");
        }

        // a heart outline in unit coordinates (-1..1)
        private static final float[][] HEART = heart();

        private static float[][] heart() {
            int n = 16;
            float[][] p = new float[n][2];
            for (int i = 0; i < n; i++) {
                double t = i / (double) n * Math.PI * 2;
                p[i][0] = (float) (16 * Math.pow(Math.sin(t), 3)) / 17f;
                p[i][1] = (float) -(13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t)) / 17f;
            }
            return p;
        }

        @Override
        public void draw(Ctx c) {
            GuiGraphics g = c.g;
            tint(c, 0x03041A, 0.65f * c.vis);
            Random rr = new Random(13);
            for (int i = 0; i < 90; i++) {
                float x = rr.nextFloat() * c.w, y = rr.nextFloat() * c.h;
                float tw = 0.3f + 0.7f * (float) Math.abs(Math.sin(c.t * 3 + i));
                rect(g, x, y, 1.2f, 1.2f, 0, A(tw * c.vis, 0xFFFFFF));
            }
            double connect = c.T * 0.55;
            // shooting stars during the first part
            if (c.t < connect && c.spawning()) {
                c.bag.acc1 += c.dt * (4 + 3 * c.L) * c.k;
                while (c.bag.acc1 >= 1) {
                    c.bag.acc1--;
                    Bit b = bit(c.bag.a, 40);
                    b.x = c.bag.r.nextFloat() * c.w;
                    b.y = c.bag.r.nextFloat() * c.h * 0.5f;
                    float sp = 260 + c.bag.r.nextFloat() * 160;
                    b.vx = sp * 0.6f;
                    b.vy = sp * 0.4f;
                    b.max = b.life = 1.2f;
                    b.size = 1.5f + c.bag.r.nextFloat() * 1.5f;
                }
            }
            step(c.bag.a, c.dt, c.h);
            for (Bit b : c.bag.a) {
                line(g, b.x, b.y, b.x - b.vx * 0.08f, b.y - b.vy * 0.08f, b.size, A(b.q() * c.vis, 0xCFE0FF));
                rect(g, b.x, b.y, b.size * 1.5f, b.size * 1.5f, 0, A(b.q() * c.vis, 0xFFFFFF));
            }
            // constellation heart
            float hs = Math.min(c.w, c.h) * (0.22f + 0.02f * c.L);
            float hx = c.cx, hy = c.cy + hs * 0.1f;
            float appear = clamp((c.t - connect) / 1.2);
            int lit = (int) (appear * HEART.length);
            for (int i = 0; i < HEART.length; i++) {
                float x = hx + HEART[i][0] * hs, y = hy + HEART[i][1] * hs;
                if (i < lit) {
                    int j = (i + 1) % HEART.length;
                    if (j < lit) {
                        float x2 = hx + HEART[j][0] * hs, y2 = hy + HEART[j][1] * hs;
                        line(g, x, y, x2, y2, 1.4f, A(c.vis * appear * 0.7f, 0xFF6A8A));
                    }
                    float tw = 0.7f + 0.3f * (float) Math.sin(c.t * 5 + i);
                    glow(g, x, y, 7, 0xFF8AA6, 0.4f * c.vis * appear);
                    rect(g, x, y, 2.5f, 2.5f, 0, A(c.vis * tw, 0xFFFFFF));
                }
            }
            if (appear > 0.98f) glow(g, hx, hy, hs, 0xFF4D6A, 0.1f * c.vis);
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            Random r = new Random(14);
            double connect = T * 0.55;
            for (double t = 0.1; t < connect; t += 0.35 + r.nextDouble() * 0.3) out.add(snd("entity.firework_rocket.twinkle_far", t, 0.4f, 1.2f + r.nextFloat() * 0.5f));
            for (int i = 0; i < 8; i++) out.add(snd("block.note_block.bell", connect + i * 0.1, 0.6f, note(6 + i * 2)));
            out.add(snd("entity.player.levelup", connect + 0.9, 0.7f, 1.0f));
            out.add(snd("block.amethyst_block.chime", T - 0.8, 0.7f, 1.2f));
        }
    }
}
