package com.donationfx;

import com.donationfx.ThemeKit.Bag;
import com.donationfx.ThemeKit.Bit;
import com.donationfx.ThemeKit.Ctx;
import com.donationfx.ThemeKit.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.donationfx.ThemeKit.*;

/** Themes 1-10. */
final class ThemesA {
    private ThemesA() {}

    static void register(List<Theme> out) {
        out.add(new GoldRush());
        out.add(new MoneyRain());
        out.add(new HeartStorm());
        out.add(new Hyperspace());
        out.add(new Thunderstorm());
        out.add(new Inferno());
        out.add(new Frostbite());
        out.add(new DiamondShatter());
        out.add(new Matrix());
        out.add(new Disco());
    }

    // =====================================================================================
    /** A fountain of gold coins that piles up at the bottom of the screen. */
    static final class GoldRush extends Theme {
        private static final String[] LOOT = {"gold_nugget", "gold_ingot", "raw_gold", "gold_block", "golden_carrot"};

        GoldRush() {
            super("gold_rush", "Gold Rush", "A fountain of spinning gold coins that piles up on the floor.");
        }

        @Override
        public void start(Ctx c) {
            c.bag.f = new float[c.w / 5 + 3];
        }

        @Override
        public void draw(Ctx c) {
            Bag s = c.bag;
            GuiGraphics g = c.g;
            float scale = c.h / 360f;
            g.fillGradient(0, (int) (c.h * 0.45f), c.w, c.h, A(0, 0xFFB300), A(0.42f * c.vis, 0x7A4A00));
            glow(g, c.w / 2f, c.h + 10, 90 + 25 * c.L, 0xFFD54F, 0.5f * c.vis);

            if (c.t < c.T * 0.78) {
                s.acc1 += c.dt * (55 + 45 * c.L) * c.k;
                while (s.acc1 >= 1) {
                    s.acc1--;
                    Bit b = bit(s.a, 900);
                    b.x = c.w / 2f + (float) s.r.nextGaussian() * 6;
                    b.y = c.h + 6;
                    b.vx = (float) s.r.nextGaussian() * (110 + 25 * c.L) * scale;
                    b.vy = -(360 + s.r.nextFloat() * 240 + 25 * c.L) * scale;
                    b.grav = 520 * scale;
                    b.max = b.life = 5;
                    b.rot = s.r.nextFloat() * 6.28f;
                    b.vr = 8 + s.r.nextFloat() * 10;
                    b.size = 3 + s.r.nextFloat() * 2.5f;
                    b.kind = s.r.nextInt(5) == 0 ? 1 : 0;
                    if (b.kind == 1) b.item = stack(LOOT[s.r.nextInt(LOOT.length)]);
                }
            }
            float[] pile = s.f;
            float maxPile = c.h * (0.12f + 0.03f * c.L);
            for (Bit b : s.a) {
                int col = Math.max(0, Math.min(pile.length - 1, (int) (b.x / 5)));
                if (b.vy > 0 && b.y >= c.h - pile[col] - 2) {
                    b.dead = true;
                    for (int d = -2; d <= 2; d++) {
                        int cc = col + d;
                        if (cc >= 0 && cc < pile.length) pile[cc] = Math.min(maxPile, pile[cc] + (d == 0 ? 1.1f : 0.45f));
                    }
                }
            }
            step(s.a, c.dt, c.h);

            // the pile: stacked coins with shiny tops
            for (int i = 0; i < pile.length; i++) {
                float ph = pile[i];
                if (ph < 0.5f) continue;
                float x = i * 5;
                box(g, x, c.h - ph, 5.2f, ph, A(c.vis, 0xC98A00));
                for (float y = c.h - ph; y < c.h; y += 3) box(g, x, y, 5.2f, 1, A(c.vis * 0.9f, 0xFFD54F));
                box(g, x, c.h - ph, 5.2f, 1.2f, A(c.vis, 0xFFF3B0));
            }
            if (s.r.nextFloat() < c.dt * 12) {
                int i = s.r.nextInt(pile.length);
                if (pile[i] > 2) s.b.add(glint(i * 5f, c.h - pile[i]));
            }

            for (Bit b : s.a) {
                if (b.kind == 1) {
                    item(g, b.item, b.x, b.y, 0.9f + b.size * 0.12f);
                } else {
                    float spin = Math.abs((float) Math.cos(b.rot));
                    float w = b.size * 2 * (0.15f + 0.85f * spin);
                    rect(g, b.x, b.y, w + 1, b.size * 2 + 1, 0, A(c.vis, 0x8A5A00));
                    rect(g, b.x, b.y, w, b.size * 2, 0, A(c.vis, spin > 0.6f ? 0xFFD54F : 0xE0A800));
                    if (spin > 0.5f) rect(g, b.x - w * 0.15f, b.y - b.size * 0.4f, w * 0.3f, b.size * 0.6f, 0, A(c.vis * 0.9f, 0xFFF8D0));
                }
            }
            drawGlints(c, s.b);
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            Random r = new Random(7);
            double end = Math.min(2.6 + L * 0.4, T * 0.7);
            for (double t = 0.05; t < end; t += 0.075) out.add(snd("entity.experience_orb.pickup", t, 0.35f, 0.7f + r.nextFloat() * 0.9f));
            out.add(snd("entity.player.levelup", 0.0, 0.8f, 1.2f));
            out.add(snd("block.chain.place", 0.4, 0.8f, 1.6f));
            for (double t = 0.6; t < end; t += 0.45) out.add(snd("block.amethyst_block.hit", t, 0.6f, 1.4f + r.nextFloat() * 0.5f));
            out.add(snd("entity.player.levelup", Math.max(1.2, T - 1.4), 0.8f, 0.8f));
        }
    }

    static Bit glint(float x, float y) {
        Bit b = new Bit();
        b.x = x;
        b.y = y;
        b.max = b.life = 0.5f;
        b.size = 1.2f;
        return b;
    }

    static void drawGlints(Ctx c, List<Bit> list) {
        step(list, c.dt, c.h);
        for (Bit b : list) {
            float tw = (float) Math.sin(Math.PI * (1 - b.q()));
            text(c.g, c.font, "✦", b.x, b.y, b.size + tw, A(tw * c.vis, 0xFFFFF0), 0, false);
        }
    }

    // =====================================================================================
    /** Dollar bills flutter down while giant $ signs pulse. */
    static final class MoneyRain extends Theme {
        MoneyRain() {
            super("money_rain", "Money Rain", "Fluttering dollar bills rain down with giant glowing $ signs.");
        }

        @Override
        public void draw(Ctx c) {
            Bag s = c.bag;
            GuiGraphics g = c.g;
            g.fillGradient(0, 0, c.w, (int) (c.h * 0.4f), A(0.35f * c.vis, 0x0E4D1C), A(0, 0x0E4D1C));
            g.fillGradient(0, (int) (c.h * 0.7f), c.w, c.h, A(0, 0x0E4D1C), A(0.3f * c.vis, 0x0E4D1C));
            // giant $ in the back
            for (int i = 0; i < 3; i++) {
                float x = c.w * (0.18f + 0.32f * i) + (float) Math.sin(c.t * 0.7 + i) * 20;
                float y = c.h * 0.55f + (float) Math.cos(c.t * 0.5 + i * 2) * 25;
                float pulse = 1 + 0.1f * (float) Math.sin(c.t * 4 + i);
                text(g, c.font, "$", x, y, (9 + c.L * 1.5f) * pulse, A(0.09f * c.vis, 0x5CFF7A), (float) Math.sin(c.t + i) * 0.15f, false);
            }
            if (s.last < 0) {
                s.last = 1;
                int n = (int) ((30 + 18 * c.L) * Math.min(2, c.k));
                for (int i = 0; i < n; i++) {
                    Bit b = bit(s.a, 500);
                    double ang = -Math.PI / 2 + s.r.nextGaussian() * 0.9;
                    float sp = 160 + s.r.nextFloat() * 220;
                    b.x = c.cx;
                    b.y = c.cy;
                    b.vx = (float) Math.cos(ang) * sp;
                    b.vy = (float) Math.sin(ang) * sp;
                    b.grav = 160;
                    b.drag = 1.4f;
                    b.a = s.r.nextFloat() * 6.28f;
                    b.b = 0.8f + s.r.nextFloat() * 0.5f;
                    b.max = b.life = 12;
                }
            }
            for (Bit b : s.a) if (b.grav > 0 && b.vy > 45) {
                b.grav = 0;
                b.drag = 0;
                b.vy = 45;
                b.vx *= 0.3f;
            }
            if (c.spawning()) {
                s.acc1 += c.dt * (14 + 12 * c.L) * c.k;
                while (s.acc1 >= 1) {
                    s.acc1--;
                    Bit b = bit(s.a, 500);
                    b.x = s.r.nextFloat() * c.w;
                    b.y = -20;
                    b.vy = 38 + s.r.nextFloat() * 40;
                    b.a = s.r.nextFloat() * 6.28f; // sway phase
                    b.b = 0.8f + s.r.nextFloat() * 0.5f; // size
                    b.max = b.life = 12;
                    b.kind = s.r.nextInt(7) == 0 ? 1 : 0;
                    if (b.kind == 1) b.item = stack(s.r.nextBoolean() ? "emerald" : "paper");
                }
                s.acc2 += c.dt * (3 + 2 * c.L);
                while (s.acc2 >= 1) {
                    s.acc2--;
                    s.b.add(glint(s.r.nextFloat() * c.w, s.r.nextFloat() * c.h));
                }
            }
            for (Bit b : s.a) {
                b.a += c.dt * 2.2f;
                b.x += (float) Math.sin(b.a) * 28 * c.dt;
            }
            step(s.a, c.dt, c.h);
            for (Bit b : s.a) {
                if (b.kind == 1) {
                    item(g, b.item, b.x, b.y, 1.1f);
                    continue;
                }
                float flip = Math.abs((float) Math.cos(b.a * 0.7f));
                float rot = (float) Math.sin(b.a) * 0.5f;
                float bw = 26 * b.b * (0.25f + 0.75f * flip), bh = 12 * b.b;
                rect(g, b.x, b.y, bw + 2, bh + 2, rot, A(c.vis, 0x0D3B16));
                rect(g, b.x, b.y, bw, bh, rot, A(c.vis, flip > 0.5f ? 0x3FAE55 : 0x2E8B40));
                rect(g, b.x, b.y, bw * 0.75f, bh * 0.6f, rot, A(c.vis * 0.6f, 0x7FD98C));
                if (flip > 0.4f) {
                    rect(g, b.x, b.y, bh * 0.55f, bh * 0.55f, rot, A(c.vis, 0x2E8B40));
                    text(g, c.font, "$", b.x, b.y + 0.5f, 0.7f * b.b, A(c.vis, 0x0D3B16), rot, false);
                }
            }
            drawGlints(c, s.b);
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            Random r = new Random(11);
            out.add(snd("block.note_block.cow_bell", 0.0, 0.9f, 1.5f));
            out.add(snd("block.note_block.cow_bell", 0.12, 0.9f, 2.0f));
            out.add(snd("entity.villager.yes", 0.5, 0.7f, 1.2f));
            for (double t = 0.2; t < T * 0.75; t += 0.16) out.add(snd("item.book.page_turn", t, 0.55f, 0.8f + r.nextFloat() * 0.6f));
            for (double t = 1.0; t < T * 0.7; t += 0.9) out.add(snd("entity.experience_orb.pickup", t, 0.5f, 1.2f + r.nextFloat() * 0.6f));
            if (L >= 2) out.add(snd("block.note_block.cow_bell", 1.5, 0.9f, 2.0f));
        }
    }

    // =====================================================================================
    /** Hearts spiral into the middle while the whole screen beats like a heart. */
    static final class HeartStorm extends Theme {
        HeartStorm() {
            super("heart_storm", "Heart Storm", "A vortex of hearts swirls into a giant beating heart. Very lifesteal.");
        }

        private static double period(int L) {
            return L >= 3 ? 0.62 : 0.75;
        }

        private static float beat(double t, int L) {
            double tb = t % period(L);
            return (float) (Math.exp(-(tb * tb) / 0.004) + 0.65 * Math.exp(-((tb - 0.2) * (tb - 0.2)) / 0.004));
        }

        @Override
        public void draw(Ctx c) {
            Bag s = c.bag;
            GuiGraphics g = c.g;
            float env = beat(c.t, c.L);
            float R0 = Math.max(c.w, c.h) * 0.62f;
            float vx = c.w / 2f, vy = c.h * 0.7f;
            // pulsing red edges
            float va = c.vis * (0.18f + 0.32f * env);
            int band = c.h / 4;
            g.fillGradient(0, 0, c.w, band, A(va, 0xC0001A), A(0, 0xC0001A));
            g.fillGradient(0, c.h - band, c.w, c.h, A(0, 0xC0001A), A(va, 0xC0001A));
            for (int i = 0; i < 10; i++) {
                int sw = Math.max(1, c.w / 50);
                int col = A(va * (1 - i / 10f), 0xC0001A);
                g.fill(i * sw, 0, (i + 1) * sw, c.h, col);
                g.fill(c.w - (i + 1) * sw, 0, c.w - i * sw, c.h, col);
            }
            // giant heart
            float hs = (7 + c.L * 1.4f) * (1 + 0.14f * env) * (c.h / 360f);
            glow(g, vx, vy, 40 * hs / 7 * (1 + env * 0.3f), 0xFF2040, 0.5f * c.vis);
            text(g, c.font, "❤", vx, vy + 4, hs, A(0.6f * c.vis, 0xFF1E3C), 0, true);

            if (c.spawning()) {
                s.acc1 += c.dt * (30 + 22 * c.L) * c.k;
                while (s.acc1 >= 1) {
                    s.acc1--;
                    Bit b = bit(s.a, 700);
                    b.a = s.r.nextFloat() * 6.28f; // angle
                    b.b = R0 * (0.75f + s.r.nextFloat() * 0.35f); // radius
                    b.size = 1 + s.r.nextFloat() * 1.3f;
                    b.col = s.r.nextInt(4) == 0 ? 0xFF8FB0 : (s.r.nextBoolean() ? 0xFF2244 : 0xD0001E);
                    b.max = b.life = 20;
                }
            }
            // outward heart burst on each beat
            int beatIndex = (int) (c.t / period(c.L));
            if (beatIndex != (int) s.acc2 && c.spawning()) {
                s.acc2 = beatIndex;
                for (int i = 0; i < 6 + 2 * c.L; i++) {
                    Bit b = bit(s.b, 300);
                    double a = s.r.nextDouble() * Math.PI * 2;
                    b.x = vx;
                    b.y = vy;
                    b.vx = (float) Math.cos(a) * (120 + s.r.nextFloat() * 120);
                    b.vy = (float) Math.sin(a) * (120 + s.r.nextFloat() * 120);
                    b.drag = 1.5f;
                    b.max = b.life = 1.2f;
                    b.size = 1 + s.r.nextFloat();
                    b.col = 0xFF4060;
                }
            }
            for (Bit b : s.a) {
                float closeness = 1 - b.b / R0;
                b.a += c.dt * (1.0f + 4.5f * closeness * closeness + 0.6f);
                b.b -= c.dt * (70 + 220 * closeness) * (1 + c.L * 0.1f);
                if (b.b < 14) b.dead = true;
                b.x = vx + (float) Math.cos(b.a) * b.b;
                b.y = vy + (float) Math.sin(b.a) * b.b * 0.6f;
            }
            step(s.a, c.dt, c.h);
            step(s.b, c.dt, c.h);
            for (Bit b : s.a) {
                float sc = b.size * (0.6f + 1.2f * (b.b / R0));
                float a = c.vis * Math.min(1, b.b / 60f);
                text(g, c.font, "❤", b.x, b.y, sc, A(a, b.col), 0, true);
            }
            for (Bit b : s.b) text(g, c.font, "❤", b.x, b.y, b.size * 1.4f, A(b.q() * c.vis, b.col), 0, false);
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            double p = period(L);
            for (double t = 0; t < T - 0.6; t += p) out.add(snd("entity.warden.heartbeat", t, 1.0f, 1.0f));
            out.add(snd("item.totem.use", 0.0, 0.45f, 1.3f));
            out.add(snd("block.amethyst_block.chime", 0.4, 0.8f, 0.7f));
            if (L >= 2) out.add(snd("entity.player.levelup", T * 0.5, 0.7f, 0.6f));
            out.add(snd("entity.player.levelup", T - 1.0, 0.8f, 1.0f));
        }
    }

    // =====================================================================================
    /** Jump to hyperspace: stars stretch into streaks rushing past. */
    static final class Hyperspace extends Theme {
        Hyperspace() {
            super("hyperspace", "Hyperspace", "Warp jump! Stars stretch into light streaks rushing past you.");
        }

        private static float speed(double t, double T) {
            if (t < 0.6) return 0.12f;
            if (t < 1.4) return 0.12f + (float) Math.pow((t - 0.6) / 0.8, 2) * 2.4f;
            if (t > T - 1.2) return 0.9f * (float) Math.max(0.08, (T - t) / 1.2);
            return 1.0f;
        }

        @Override
        public void start(Ctx c) {
            for (int i = 0; i < 460; i++) {
                Bit b = new Bit();
                reset(b, c.bag.r, true);
                c.bag.a.add(b);
            }
        }

        private static void reset(Bit b, Random r, boolean anyZ) {
            b.x = (r.nextFloat() * 2 - 1);
            b.y = (r.nextFloat() * 2 - 1);
            b.a = anyZ ? 0.05f + r.nextFloat() : 1.0f + r.nextFloat() * 0.2f; // z
            b.col = r.nextInt(5) == 0 ? 0x9FD0FF : r.nextInt(6) == 0 ? 0xFFC8F0 : 0xFFFFFF;
        }

        @Override
        public void draw(Ctx c) {
            Bag s = c.bag;
            GuiGraphics g = c.g;
            float v = speed(c.t, c.T) * (1 + c.L * 0.12f) * (float) Math.min(1.6, c.k);
            tint(c, 0x03040F, 0.62f * c.vis);
            // nebula clouds
            for (int i = 0; i < 4; i++) {
                float x = c.w * (0.2f + 0.2f * i) + (float) Math.sin(c.t * 0.3 + i * 1.7) * 40;
                float y = c.h * (0.3f + 0.15f * (i % 2)) + (float) Math.cos(c.t * 0.25 + i) * 30;
                int col = i % 3 == 0 ? 0x6A2CFF : i % 3 == 1 ? 0x2C7BFF : 0xFF2CA8;
                glow(g, x, y, 110 + 20 * c.L, col, 0.2f * c.vis);
            }
            glow(g, c.w / 2f, c.h / 2f, 70 + v * 46, 0xBFE4FF, 0.5f * c.vis);
            glow(g, c.w / 2f, c.h / 2f, 24 + v * 14, 0xFFFFFF, 0.5f * c.vis);
            float fx = c.w * 0.5f, fy = c.h * 0.5f;
            for (Bit b : s.a) {
                float z0 = b.a;
                b.a -= v * c.dt * 0.6f;
                if (b.a <= 0.03f) {
                    reset(b, s.r, false);
                    continue;
                }
                float x1 = fx + b.x / b.a * c.w * 0.5f, y1 = fy + b.y / b.a * c.h * 0.5f;
                float zt = Math.min(1.4f, b.a + v * 0.05f + 0.004f);
                float x0 = fx + b.x / zt * c.w * 0.5f, y0 = fy + b.y / zt * c.h * 0.5f;
                if (x1 < -50 || x1 > c.w + 50 || y1 < -50 || y1 > c.h + 50) {
                    reset(b, s.r, false);
                    continue;
                }
                float near = Math.min(1, (1.2f - b.a) / 1.2f);
                float th = 0.7f + near * 2.6f;
                line(g, x0, y0, x1, y1, th, A(c.vis * (0.25f + 0.75f * near), b.col));
                if (z0 < 0) b.dead = true;
            }
            // warp flash
            double since = c.t - 1.4;
            if (c.flashes && since >= 0 && since < 0.4) tint(c, 0xEAF4FF, (float) (1 - since / 0.4) * 0.75f);
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            out.add(snd("block.beacon.power_select", 0.0, 0.9f, 1.0f));
            out.add(snd("block.respawn_anchor.charge", 0.35, 0.8f, 0.8f));
            out.add(snd("block.respawn_anchor.charge", 0.75, 0.8f, 1.1f));
            out.add(snd("entity.firework_rocket.launch", 1.1, 1.0f, 0.5f));
            out.add(snd("block.beacon.activate", 1.4, 1.0f, 1.4f));
            out.add(snd("block.conduit.activate", 1.45, 0.9f, 1.0f));
            if (L >= 2) out.add(snd("entity.firework_rocket.large_blast_far", 1.5, 0.8f, 0.6f));
            out.add(snd("block.beacon.deactivate", T - 1.1, 0.8f, 1.0f));
        }
    }

    // =====================================================================================
    /** Dark storm with rain and real-looking branching lightning. */
    static final class Thunderstorm extends Theme {
        Thunderstorm() {
            super("thunderstorm", "Thunderstorm", "The sky goes dark, rain pours and branching lightning strikes.");
        }

        static double[] strikes(int L, double T) {
            Random r = new Random(1234 + L);
            List<Double> out = new ArrayList<>();
            double t = 0.08;
            double gap = 1.25 / (1 + 0.35 * L);
            while (t < T - 1.0) {
                out.add(t);
                t += gap * (0.6 + r.nextDouble() * 0.8);
            }
            double[] a = new double[out.size()];
            for (int i = 0; i < a.length; i++) a[i] = out.get(i);
            return a;
        }

        private static final class Bolt {
            double at;
            final List<float[]> segs = new ArrayList<>();
            float endX, endY;
        }

        private static void build(Bolt bolt, float x1, float y1, float x2, float y2, float disp, int depth, Random r, boolean branch) {
            if (depth == 0) {
                bolt.segs.add(new float[]{x1, y1, x2, y2, branch ? 1 : 0});
                return;
            }
            float mx = (x1 + x2) / 2 + (float) r.nextGaussian() * disp;
            float my = (y1 + y2) / 2 + (float) r.nextGaussian() * disp * 0.25f;
            build(bolt, x1, y1, mx, my, disp / 2, depth - 1, r, branch);
            build(bolt, mx, my, x2, y2, disp / 2, depth - 1, r, branch);
            if (!branch && depth >= 3 && r.nextInt(3) == 0) {
                float bx = mx + (float) r.nextGaussian() * 60 + (mx > x1 ? 40 : -40);
                float by = my + 40 + r.nextFloat() * 70;
                build(bolt, mx, my, bx, by, disp / 2.5f, depth - 2, r, true);
            }
        }

        @Override
        public void start(Ctx c) {
            List<Bolt> bolts = new ArrayList<>();
            for (double at : strikes(c.L, c.T)) {
                Bolt b = new Bolt();
                b.at = at;
                float x = c.w * (0.1f + c.bag.r.nextFloat() * 0.8f);
                b.endX = x + (float) c.bag.r.nextGaussian() * 60;
                b.endY = c.h * (0.6f + c.bag.r.nextFloat() * 0.35f);
                build(b, x, -10, b.endX, b.endY, 90, 6, c.bag.r, false);
                bolts.add(b);
            }
            c.bag.extra = bolts;
            for (int i = 0; i < 180; i++) {
                Bit d = new Bit();
                d.x = c.bag.r.nextFloat() * (c.w + 100);
                d.y = c.bag.r.nextFloat() * c.h;
                d.size = 8 + c.bag.r.nextFloat() * 10;
                c.bag.a.add(d);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public void draw(Ctx c) {
            GuiGraphics g = c.g;
            List<Bolt> bolts = (List<Bolt>) c.bag.extra;
            tint(c, 0x05070F, 0.58f * c.vis);
            g.fillGradient(0, 0, c.w, c.h / 3, A(0.4f * c.vis, 0x10141F), A(0, 0x10141F));
            // rain
            for (Bit d : c.bag.a) {
                d.y += 620 * c.dt;
                d.x -= 140 * c.dt;
                if (d.y > c.h + 20) {
                    d.y = -20;
                    d.x = c.bag.r.nextFloat() * (c.w + 100);
                }
                line(g, d.x, d.y, d.x - d.size * 0.22f, d.y + d.size, 1, A(0.35f * c.vis, 0xA6B8FF));
            }
            float flash = 0;
            for (Bolt b : bolts) {
                double age = c.t - b.at;
                if (age < 0 || age > 0.42) continue;
                float a = age < 0.05 ? 1 : age < 0.09 ? 0.25f : age < 0.2 ? 0.95f : (float) (1 - (age - 0.2) / 0.22);
                flash = Math.max(flash, a * (age < 0.2 ? 0.32f : 0.1f));
                for (float[] sg : b.segs) {
                    float m = sg[4] > 0 ? 0.55f : 1f;
                    line(g, sg[0], sg[1], sg[2], sg[3], 7 * m, A(0.22f * a * c.vis, 0x7FA8FF));
                    line(g, sg[0], sg[1], sg[2], sg[3], 3.2f * m, A(0.5f * a * c.vis, 0xC8DCFF));
                    line(g, sg[0], sg[1], sg[2], sg[3], 1.4f * m, A(a * c.vis, 0xFFFFFF));
                }
                if (age < 0.06 && !b.segs.isEmpty() && c.bag.last != b.at) {
                    c.bag.last = b.at;
                    sparks(c.bag.b, b.endX, b.endY, 30 + 10 * c.L, 220, 0xD8E6FF, false, c.bag.r);
                }
                glow(g, b.endX, b.endY, 40, 0xBFD6FF, 0.5f * a * c.vis);
            }
            if (c.flashes && flash > 0.01f) tint(c, 0xDDE8FF, flash);
            step(c.bag.b, c.dt, c.h);
            drawSparks(c, c.bag.b);
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            for (double t : strikes(L, T)) {
                out.add(snd("entity.lightning_bolt.impact", t, 0.7f, 1.0f));
                out.add(snd("entity.lightning_bolt.thunder", t + 0.12, 0.55f, 0.9f + (float) (t % 0.3)));
            }
            for (double t = 0; t < T - 0.5; t += 2.2) out.add(snd("weather.rain", t, 0.6f, 1.0f));
        }
    }

    // =====================================================================================
    /** A roaring wall of fire with embers and smoke. */
    static final class Inferno extends Theme {
        private static final String[] HOT = {"fire_charge", "blaze_powder", "magma_cream", "blaze_rod", "lava_bucket"};

        Inferno() {
            super("inferno", "Inferno", "A roaring wall of fire rises from below, with embers, smoke and blaze powder.");
        }

        @Override
        public void draw(Ctx c) {
            Bag s = c.bag;
            GuiGraphics g = c.g;
            float grow = clamp(c.t / 1.2) * (c.t > c.T - 1 ? clamp(c.T - c.t) : 1);
            g.fillGradient(0, (int) (c.h * 0.4f), c.w, c.h, A(0, 0xFF4500), A(0.55f * c.vis * grow, 0xFF2200));
            float wall = c.h * (0.08f + 0.05f * c.L) * grow;
            if (c.spawning()) {
                s.acc1 += c.dt * (230 + 90 * c.L) * c.k * (0.3 + 0.7 * grow);
                while (s.acc1 >= 1) {
                    s.acc1--;
                    Bit b = bit(s.a, 1300);
                    b.x = s.r.nextFloat() * c.w;
                    b.y = c.h + 4 - s.r.nextFloat() * wall;
                    b.vx = (float) s.r.nextGaussian() * 14;
                    b.vy = -(45 + s.r.nextFloat() * 85) * (1 + c.L * 0.12f) * (c.h / 360f);
                    b.max = b.life = 0.6f + s.r.nextFloat() * 0.7f;
                    b.size = 5 + s.r.nextFloat() * 6 + c.L;
                }
                s.acc2 += c.dt * (18 + 10 * c.L) * c.k;
                while (s.acc2 >= 1) {
                    s.acc2--;
                    Bit b = bit(s.b, 400);
                    b.x = s.r.nextFloat() * c.w;
                    b.y = c.h - s.r.nextFloat() * wall;
                    b.vx = (float) s.r.nextGaussian() * 30;
                    b.vy = -(160 + s.r.nextFloat() * 220);
                    b.max = b.life = 1.5f + s.r.nextFloat() * 1.5f;
                    b.size = 0.8f + s.r.nextFloat();
                    b.a = s.r.nextFloat() * 6.28f;
                }
                s.acc3 += c.dt * (1 + 0.8 * c.L);
                while (s.acc3 >= 1) {
                    s.acc3--;
                    Bit b = bit(s.c, 60);
                    b.item = stack(HOT[s.r.nextInt(HOT.length)]);
                    b.x = s.r.nextFloat() * c.w;
                    b.y = c.h + 10;
                    b.vx = (float) s.r.nextGaussian() * 40;
                    b.vy = -(260 + s.r.nextFloat() * 160) * (c.h / 360f);
                    b.grav = 300 * (c.h / 360f);
                    b.max = b.life = 3;
                }
            }
            for (Bit b : s.a) b.vx += (float) Math.sin(c.t * 3 + b.x * 0.04) * 30 * c.dt;
            for (Bit b : s.b) {
                b.a += c.dt * 4;
                b.vx += (float) Math.sin(b.a) * 60 * c.dt;
            }
            step(s.a, c.dt, c.h);
            step(s.b, c.dt, c.h);
            step(s.c, c.dt, c.h);
            for (Bit b : s.a) {
                float q = b.q();
                int col;
                float a = c.vis;
                if (q > 0.82f) col = 0xFFF6C0;
                else if (q > 0.6f) col = mix(0xFFD23C, 0xFFF6C0, (q - 0.6f) / 0.22f);
                else if (q > 0.35f) col = mix(0xFF6A10, 0xFFD23C, (q - 0.35f) / 0.25f);
                else if (q > 0.15f) col = mix(0xB8160E, 0xFF6A10, (q - 0.15f) / 0.2f);
                else {
                    col = 0x2E2A2A;
                    a *= q / 0.15f * 0.22f;
                }
                float sz = b.size * (q > 0.15f ? 0.4f + q * 0.8f : 1.1f + (0.15f - q) * 6);
                rect(g, b.x, b.y, sz, sz, 0, A(a * (q > 0.15f ? 0.85f : 1f), col));
            }
            for (Bit b : s.b) {
                float tw = 0.6f + 0.4f * (float) Math.sin(b.a * 5);
                rect(g, b.x, b.y, b.size * 2, b.size * 2, 0, A(b.q() * c.vis * tw, 0xFFB040));
            }
            for (Bit b : s.c) item(g, b.item, b.x, b.y, 1.2f);
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            Random r = new Random(3);
            out.add(snd("item.firecharge.use", 0.0, 1.0f, 0.8f));
            out.add(snd("entity.blaze.shoot", 0.25, 0.8f, 1.0f));
            out.add(snd("entity.blaze.shoot", 0.8, 0.8f, 0.8f));
            for (double t = 0.1; t < T - 0.8; t += 0.7) out.add(snd("block.fire.ambient", t, 1.0f, 0.8f + r.nextFloat() * 0.4f));
            for (double t = 0.5; t < T - 0.8; t += 0.35 + r.nextDouble() * 0.4) out.add(snd("block.lava.pop", t, 0.7f, 0.8f + r.nextFloat() * 0.5f));
            if (L >= 2) out.add(snd("entity.ghast.shoot", 1.4, 0.7f, 1.0f));
            if (L >= 3) out.add(snd("entity.blaze.ambient", 2.2, 0.8f, 0.9f));
            out.add(snd("block.fire.extinguish", T - 0.9, 0.7f, 1.0f));
        }
    }

    // =====================================================================================
    /** Frost creeps in from the edges, snow falls, then the ice shatters. */
    static final class Frostbite extends Theme {
        Frostbite() {
            super("frostbite", "Frostbite", "Ice crystals creep in from the screen edges, snow falls, then it all shatters.");
        }

        private static void grow(List<float[]> out, float x, float y, float ang, float len, float birth, int depth, Random r, float th) {
            if (depth == 0 || len < 3) return;
            float x2 = x + (float) Math.cos(ang) * len, y2 = y + (float) Math.sin(ang) * len;
            out.add(new float[]{x, y, x2, y2, birth, th, r.nextFloat()});
            float nb = birth + 0.11f;
            // ice grows in straight lines with side spikes at 60 degrees (like real frost)
            grow(out, x2, y2, ang + (float) r.nextGaussian() * 0.08f, len * 0.85f, nb, depth - 1, r, th * 0.85f);
            float side = len * (0.35f + r.nextFloat() * 0.2f);
            if (r.nextInt(10) < 8) out.add(new float[]{x2, y2, x2 + (float) Math.cos(ang + 1.047f) * side, y2 + (float) Math.sin(ang + 1.047f) * side, nb, th * 0.6f, r.nextFloat()});
            if (r.nextInt(10) < 8) out.add(new float[]{x2, y2, x2 + (float) Math.cos(ang - 1.047f) * side, y2 + (float) Math.sin(ang - 1.047f) * side, nb, th * 0.6f, r.nextFloat()});
            if (depth >= 4 && r.nextInt(3) == 0) grow(out, x2, y2, ang + (r.nextBoolean() ? 1.047f : -1.047f), len * 0.6f, nb, depth - 2, r, th * 0.7f);
        }

        @Override
        public void start(Ctx c) {
            List<float[]> segs = new ArrayList<>();
            Random r = c.bag.r;
            int roots = 16 + 5 * c.L;
            for (int i = 0; i < roots; i++) {
                int edge = r.nextInt(4);
                float x, y, ang;
                switch (edge) {
                    case 0 -> { x = r.nextFloat() * c.w; y = 0; ang = (float) (Math.PI / 2); }
                    case 1 -> { x = r.nextFloat() * c.w; y = c.h; ang = (float) (-Math.PI / 2); }
                    case 2 -> { x = 0; y = r.nextFloat() * c.h; ang = 0; }
                    default -> { x = c.w; y = r.nextFloat() * c.h; ang = (float) Math.PI; }
                }
                ang += (float) r.nextGaussian() * 0.35f;
                grow(segs, x, y, ang, 14 + r.nextFloat() * 10 + c.L * 2, r.nextFloat() * 0.6f, 7, r, 2.0f);
            }
            c.bag.extra = segs;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void draw(Ctx c) {
            Bag s = c.bag;
            GuiGraphics g = c.g;
            List<float[]> segs = (List<float[]>) s.extra;
            double shatter = c.T - 1.1;
            tint(c, 0xBFE8FF, 0.16f * c.vis);
            int band = c.h / 4;
            g.fillGradient(0, 0, c.w, band, A(0.35f * c.vis, 0xE8F8FF), A(0, 0xE8F8FF));
            g.fillGradient(0, c.h - band, c.w, c.h, A(0, 0xE8F8FF), A(0.35f * c.vis, 0xE8F8FF));
            for (float[] sg : segs) {
                double age = c.t - sg[4];
                if (age <= 0) continue;
                float f = clamp(age / 0.11);
                float x2 = sg[0] + (sg[2] - sg[0]) * f, y2 = sg[1] + (sg[3] - sg[1]) * f;
                float dy = 0, a = c.vis;
                if (c.t > shatter) {
                    double st = c.t - shatter;
                    dy = (float) (st * st * (250 + sg[6] * 400));
                    a *= clamp(1 - st / 1.0);
                }
                line(g, sg[0], sg[1] + dy, x2, y2 + dy, sg[5] * 2.4f, A(0.22f * a, 0x8FD3FF));
                line(g, sg[0], sg[1] + dy, x2, y2 + dy, sg[5], A(0.9f * a, 0xF2FBFF));
            }
            if (c.spawning()) {
                s.acc1 += c.dt * (20 + 14 * c.L) * c.k;
                while (s.acc1 >= 1) {
                    s.acc1--;
                    Bit b = bit(s.a, 500);
                    b.x = s.r.nextFloat() * c.w;
                    b.y = -10;
                    b.vy = 25 + s.r.nextFloat() * 45;
                    b.a = s.r.nextFloat() * 6.28f;
                    b.size = 0.8f + s.r.nextFloat() * 1.6f;
                    b.vr = (s.r.nextFloat() - 0.5f) * 2;
                    b.max = b.life = 14;
                }
                s.acc2 += c.dt * 8;
                while (s.acc2 >= 1) {
                    s.acc2--;
                    s.b.add(glint(s.r.nextFloat() * c.w, s.r.nextFloat() * c.h));
                }
            }
            for (Bit b : s.a) {
                b.a += c.dt * 1.5f;
                b.x += (float) Math.sin(b.a) * 18 * c.dt;
            }
            step(s.a, c.dt, c.h);
            for (Bit b : s.a) text(g, c.font, "❄", b.x, b.y, b.size, A(0.9f * c.vis, 0xF4FBFF), b.rot, false);
            drawGlints(c, s.b);
            if (c.t > shatter && c.bag.last < 0) {
                c.bag.last = 1;
                for (int i = 0; i < 4; i++) sparks(s.c, c.w * (0.2f + 0.2f * i), c.h * 0.5f, 30, 260, 0xDFF4FF, false, s.r);
            }
            step(s.c, c.dt, c.h);
            drawSparks(c, s.c);
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            out.add(snd("block.powder_snow.place", 0.0, 1.0f, 0.8f));
            for (int i = 0; i < 7; i++) out.add(snd("block.amethyst_cluster.place", 0.1 + i * 0.22, 0.7f, 0.8f + i * 0.12f));
            out.add(snd("entity.player.hurt_freeze", 0.5, 0.6f, 1.0f));
            out.add(snd("block.amethyst_block.chime", 1.2, 1.0f, 1.5f));
            if (L >= 2) out.add(snd("block.amethyst_block.chime", 2.0, 1.0f, 1.8f));
            out.add(snd("block.glass.break", T - 1.1, 1.0f, 0.9f));
            out.add(snd("block.amethyst_block.break", T - 1.05, 1.0f, 1.0f));
            out.add(snd("block.glass.break", T - 0.95, 0.7f, 1.3f));
        }
    }

    // =====================================================================================
    /** A giant diamond appears, cracks, and explodes into shards. */
    static final class DiamondShatter extends Theme {
        private static final double BREAK = 1.8;

        DiamondShatter() {
            super("diamond_shatter", "Diamond Shatter", "A giant shining diamond cracks... then explodes into hundreds of shards.");
        }

        @Override
        public void draw(Ctx c) {
            Bag s = c.bag;
            GuiGraphics g = c.g;
            float dx = c.w / 2f, dy = c.h * 0.7f;
            tint(c, 0x001A26, 0.3f * c.vis);
            if (c.t < BREAK) {
                float sc = (float) easeOutBack(c.t / 0.6) * (4.5f + c.L * 0.8f) * (c.h / 360f);
                float bob = (float) Math.sin(c.t * 4) * 3;
                float shake = c.t > 1.0 ? (float) (Math.sin(c.t * 90) * (c.t - 1.0) * 4) : 0;
                glow(g, dx, dy + bob, 16 * sc, 0x6FF6FF, 0.55f * c.vis);
                item(g, stack("diamond"), dx + shake, dy + bob, sc);
                // shine sweeping over it
                float half = 8 * sc;
                double sweep = (c.t * 0.9) % 1.0;
                g.enableScissor((int) (dx - half), (int) (dy + bob - half), (int) (dx + half), (int) (dy + bob + half));
                float bx = dx - half * 2 + (float) sweep * half * 4;
                rect(g, bx, dy + bob, half * 0.35f, half * 3, 0.6f, A(0.35f * c.vis, 0xFFFFFF));
                g.disableScissor();
                // cracks
                Random r = new Random(99);
                for (int k = 0; k < 2; k++) {
                    if (c.t < 1.0 + k * 0.4) continue;
                    float x = dx + shake, y = dy + bob;
                    double ang = r.nextDouble() * Math.PI * 2;
                    for (int i = 0; i < 5; i++) {
                        ang += r.nextGaussian() * 0.5;
                        float nx = x + (float) Math.cos(ang) * half * 0.28f, ny = y + (float) Math.sin(ang) * half * 0.28f;
                        line(g, x, y, nx, ny, 1.5f, A(c.vis, 0xFFFFFF));
                        x = nx;
                        y = ny;
                    }
                }
            } else if (s.last < 0) {
                s.last = 1;
                int n = (int) ((60 + 30 * c.L) * Math.min(2, c.k));
                for (int i = 0; i < n; i++) {
                    Bit b = bit(s.a, 600);
                    double a = s.r.nextDouble() * Math.PI * 2;
                    double sp = 150 + s.r.nextDouble() * 380;
                    b.x = dx;
                    b.y = dy;
                    b.vx = (float) (Math.cos(a) * sp);
                    b.vy = (float) (Math.sin(a) * sp) - 120;
                    b.grav = 260;
                    b.drag = 0.8f;
                    b.vr = (s.r.nextFloat() - 0.5f) * 20;
                    b.size = 2 + s.r.nextFloat() * 4;
                    b.max = b.life = 2.5f + s.r.nextFloat();
                    b.col = s.r.nextInt(3) == 0 ? 0xFFFFFF : s.r.nextBoolean() ? 0x7DF9FF : 0x2FD8E8;
                    b.kind = s.r.nextInt(5) == 0 ? 1 : 0;
                    if (b.kind == 1) b.item = stack(s.r.nextInt(4) == 0 ? "diamond_block" : "diamond");
                }
                sparks(s.b, dx, dy, 80, 420, 0xCFFFFF, false, s.r);
            }
            if (c.t >= BREAK) {
                double since = c.t - BREAK;
                if (since < 1.0) {
                    ring(g, dx, dy, (float) (since * c.w * 0.8), (float) (since * c.w * 0.65), 3, A((float) (1 - since) * c.vis, 0x9FFFFF), 64);
                    ring(g, dx, dy, (float) (since * c.w * 0.5), (float) (since * c.w * 0.4), 2, A((float) (1 - since) * c.vis, 0xFFFFFF), 48);
                }
                if (c.flashes && since < 0.3) tint(c, 0xE0FFFF, (float) (1 - since / 0.3) * 0.5f);
                if (c.spawning() && s.r.nextFloat() < c.dt * (10 + 4 * c.L)) s.c.add(glint(s.r.nextFloat() * c.w, s.r.nextFloat() * c.h));
                // a slow rain of diamonds and a glowing gem where the diamond was
                if (c.spawning()) {
                    s.acc1 += c.dt * (4 + 3 * c.L) * c.k;
                    while (s.acc1 >= 1) {
                        s.acc1--;
                        Bit b = bit(s.a, 600);
                        b.kind = 1;
                        b.item = stack(s.r.nextInt(5) == 0 ? "diamond_block" : "diamond");
                        b.x = s.r.nextFloat() * c.w;
                        b.y = -12;
                        b.vy = 50 + s.r.nextFloat() * 60;
                        b.max = b.life = 8;
                    }
                }
                float pulse = 0.6f + 0.4f * (float) Math.sin(c.t * 4);
                glow(g, dx, dy, 60 + 10 * c.L, 0x6FF6FF, 0.35f * c.vis * pulse * clamp(since / 0.6));
                for (int i = 0; i < 6; i++) {
                    float ang = (float) (c.t * 0.6 + i * Math.PI / 3);
                    line(g, dx, dy, dx + (float) Math.cos(ang) * 90, dy + (float) Math.sin(ang) * 90, 3, A(0.18f * c.vis * clamp(since / 0.6), 0xBFFFFF));
                }
            }
            step(s.a, c.dt, c.h);
            for (Bit b : s.a) {
                if (b.kind == 1) {
                    item(g, b.item, b.x, b.y, 1.1f);
                } else {
                    rect(g, b.x, b.y, b.size * 0.7f, b.size * 1.8f, b.rot, A(c.vis * Math.min(1, b.q() * 2), b.col));
                    rect(g, b.x, b.y, b.size * 0.25f, b.size * 1.2f, b.rot, A(c.vis * 0.8f * Math.min(1, b.q() * 2), 0xFFFFFF));
                }
            }
            step(s.b, c.dt, c.h);
            drawSparks(c, s.b);
            drawGlints(c, s.c);
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            out.add(snd("block.amethyst_block.chime", 0.0, 1.0f, 1.0f));
            out.add(snd("block.amethyst_block.chime", 0.3, 1.0f, 1.3f));
            out.add(snd("block.amethyst_block.chime", 0.6, 1.0f, 1.6f));
            out.add(snd("block.amethyst_block.hit", 1.0, 1.0f, 0.8f));
            out.add(snd("block.amethyst_block.hit", 1.4, 1.0f, 0.7f));
            out.add(snd("block.glass.break", BREAK, 1.0f, 0.8f));
            out.add(snd("block.amethyst_cluster.break", BREAK, 1.0f, 1.0f));
            out.add(snd("entity.generic.explode", BREAK + 0.02, 0.4f, 1.6f));
            out.add(snd("entity.player.levelup", BREAK + 0.15, 0.9f, 1.4f));
            Random r = new Random(5);
            for (double t = BREAK + 0.3; t < Math.min(T - 0.5, BREAK + 2.5); t += 0.18) out.add(snd("block.amethyst_block.chime", t, 0.6f, 1.0f + r.nextFloat()));
        }
    }

    // =====================================================================================
    /** Green code rain with a terminal reading out the donation. */
    static final class Matrix extends Theme {
        private static final String CHARS = "0123456789ABCDEFGHJKLMNPRSTUVWXYZ$#%&*+=<>?";

        Matrix() {
            super("matrix", "The Matrix", "Green code pours down the screen while a hacker terminal reads out the donation.");
        }

        @Override
        public void start(Ctx c) {
            int cols = c.w / 7 + 1;
            float[] f = new float[cols * 3];
            for (int i = 0; i < cols; i++) {
                f[i * 3] = -c.bag.r.nextFloat() * c.h; // head y
                f[i * 3 + 1] = 70 + c.bag.r.nextFloat() * 160; // speed
                f[i * 3 + 2] = 8 + c.bag.r.nextInt(16); // length
            }
            c.bag.f = f;
            c.bag.extra = new char[cols * 24];
            char[] ch = (char[]) c.bag.extra;
            for (int i = 0; i < ch.length; i++) ch[i] = CHARS.charAt(c.bag.r.nextInt(CHARS.length()));
        }

        @Override
        public void draw(Ctx c) {
            Bag s = c.bag;
            GuiGraphics g = c.g;
            tint(c, 0x000A02, 0.74f * c.vis);
            float[] f = s.f;
            char[] ch = (char[]) s.extra;
            int cols = f.length / 3;
            float speedUp = 1 + c.L * 0.15f;
            for (int i = 0; i < cols; i++) {
                f[i * 3] += f[i * 3 + 1] * c.dt * speedUp;
                int len = (int) f[i * 3 + 2];
                if (f[i * 3] - len * 9 > c.h) {
                    f[i * 3] = -s.r.nextFloat() * 80;
                    f[i * 3 + 1] = 70 + s.r.nextFloat() * 160;
                }
                if (s.r.nextFloat() < c.dt * 8) ch[i * 24 + s.r.nextInt(24)] = CHARS.charAt(s.r.nextInt(CHARS.length()));
                for (int k = 0; k < len; k++) {
                    float y = f[i * 3] - k * 9;
                    if (y < -10 || y > c.h + 10) continue;
                    float a = c.vis * (1 - k / (float) len);
                    int col = k == 0 ? 0xE6FFE6 : k < 3 ? 0x7DFF8C : 0x1EE04A;
                    g.drawString(c.font, String.valueOf(ch[i * 24 + (k % 24)]), i * 7, (int) y, A(a, col), false);
                }
            }
            // glitch bars
            double gl = (c.t * 1.4) % 1.0;
            if (gl < 0.08) {
                Random r = new Random((long) (c.t * 20));
                for (int i = 0; i < 4; i++) {
                    int y = r.nextInt(c.h);
                    g.fill(0, y, c.w, y + 2 + r.nextInt(5), A(0.25f * c.vis, r.nextBoolean() ? 0xFF0040 : 0x00FFFF));
                }
            }
            // terminal
            String lvl = c.L >= 4 ? "LEGENDARY" : c.L >= 3 ? "INSANE" : c.L >= 2 ? "HUGE" : c.L >= 1 ? "BIG" : "NICE";
            String[] lines = {
                    "> incoming transfer detected...",
                    "> sender: " + c.player,
                    "> amount: " + Money.full(c.amount),
                    "> threat level: " + lvl,
                    "> access granted. wake up, " + (c.mc.player != null ? c.mc.player.getGameProfile().name() : "player") + "."
            };
            int chars = (int) (c.t * 28);
            int y = c.h - 12 - lines.length * 10;
            int bx = 8;
            g.fill(bx - 4, y - 4, bx + 220, c.h - 6, A(0.55f * c.vis, 0x000000));
            g.fill(bx - 4, y - 4, bx + 220, y - 3, A(0.8f * c.vis, 0x1EE04A));
            for (String line : lines) {
                if (chars <= 0) break;
                String shown = line.substring(0, Math.min(line.length(), chars));
                chars -= line.length();
                boolean cursor = chars < 0 && (c.t * 3) % 1 < 0.5;
                g.drawString(c.font, shown + (cursor ? "_" : ""), bx, y, A(c.vis, 0x5CFF6E), false);
                y += 10;
            }
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            float[] scale = {note(6), note(9), note(11), note(13), note(16), note(18), note(21), note(23)};
            Random r = new Random(21);
            out.add(snd("block.beacon.power_select", 0.0, 0.8f, 1.6f));
            for (double t = 0.05; t < T * 0.85; t += 0.12) out.add(snd("block.note_block.bit", t, 0.32f, scale[r.nextInt(scale.length)]));
            for (double t = 0.2; t < 2.0; t += 0.035) {
                if (r.nextInt(3) == 0) out.add(snd("block.note_block.hat", t, 0.2f, 1.8f));
            }
            out.add(snd("block.beacon.deactivate", T - 0.7, 0.8f, 1.4f));
        }
    }

    // =====================================================================================
    /** A disco ball, sweeping spotlights and a flashing dance floor, to a beat. */
    static final class Disco extends Theme {
        private static final double BEAT = 0.484;

        Disco() {
            super("disco", "Disco Party", "A spinning disco ball, sweeping spotlights and a light-up dance floor, with a beat.");
        }

        @Override
        public void draw(Ctx c) {
            Bag s = c.bag;
            GuiGraphics g = c.g;
            int beatN = (int) (c.t / BEAT);
            float phase = (float) ((c.t % BEAT) / BEAT);
            float env = (float) Math.exp(-phase * 5);
            tint(c, 0x07000F, 0.5f * c.vis);
            float bx = c.w / 2f, by = c.h * 0.11f;
            float R = (20 + c.L * 3) * (c.h / 360f);
            // spotlights
            int beams = 5 + c.L;
            for (int i = 0; i < beams; i++) {
                double ang = Math.PI / 2 + Math.sin(c.t * (0.7 + i * 0.13) + i * 1.3) * 1.0;
                int col = Fx.hsv((float) ((i / (double) beams + c.t * 0.08) % 1.0), 0.8f, 1f);
                float a = c.vis * (0.08f + 0.1f * env);
                float len = c.h * 1.1f;
                for (int k = 0; k < 6; k++) {
                    float d1 = len * k / 6f, d2 = len * (k + 1) / 6f;
                    float wdt = 4 + k * 9;
                    float mx = bx + (float) Math.cos(ang) * (d1 + d2) / 2, my = by + (float) Math.sin(ang) * (d1 + d2) / 2;
                    rect(g, mx, my, d2 - d1 + 1, wdt, (float) ang, A(a, col));
                }
            }
            // dance floor
            float top = c.h * 0.74f;
            int rows = 4, colsN = 8;
            for (int rI = 0; rI < rows; rI++) {
                float y0 = top + (c.h - top) * (float) Math.pow(rI / (double) rows, 1.3);
                float y1 = top + (c.h - top) * (float) Math.pow((rI + 1) / (double) rows, 1.3);
                float spread = 0.55f + 0.45f * (rI + 0.5f) / rows;
                float rowW = c.w * spread;
                for (int cI = 0; cI < colsN; cI++) {
                    float x0 = c.w / 2f - rowW / 2 + rowW * cI / colsN;
                    boolean lit = (rI + cI + beatN) % 2 == 0;
                    int col = Fx.hsv((float) (((rI * 3 + cI + beatN) * 0.13) % 1.0), 0.85f, 1f);
                    float a = c.vis * (lit ? 0.25f + 0.45f * env : 0.08f);
                    box(g, x0 + 1, y0 + 1, rowW / colsN - 2, y1 - y0 - 2, A(a, col));
                }
            }
            // reflections circling around
            if (s.a.isEmpty()) {
                for (int i = 0; i < 60; i++) {
                    Bit b = new Bit();
                    b.a = s.r.nextFloat() * 6.28f;
                    b.b = 60 + s.r.nextFloat() * Math.max(c.w, c.h) * 0.6f;
                    b.c2 = 0.2f + s.r.nextFloat() * 0.5f;
                    b.col = Fx.hsv(s.r.nextFloat(), 0.4f, 1f);
                    s.a.add(b);
                }
            }
            for (Bit b : s.a) {
                b.a += b.c2 * c.dt;
                float x = bx + (float) Math.cos(b.a) * b.b, y = by + 40 + (float) Math.sin(b.a) * b.b * 0.55f;
                rect(g, x, y, 3, 3, 0.78f, A(c.vis * (0.4f + 0.5f * env), b.col));
            }
            // the ball
            line(g, bx, 0, bx, by - R, 1, A(c.vis, 0xAAAAAA));
            glow(g, bx, by, R * 2.2f, 0xFFFFFF, (0.25f + 0.35f * env) * c.vis);
            disc(g, bx, by, R, A(c.vis, 0x6E6E78));
            for (int lat = -5; lat <= 5; lat++) {
                double phi = lat / 6.0 * Math.PI / 2;
                float yy = by + (float) Math.sin(phi) * R;
                float rr = (float) Math.cos(phi) * R;
                int n = Math.max(4, (int) (14 * Math.cos(phi)));
                for (int k = 0; k < n; k++) {
                    double th = k * Math.PI * 2 / n + c.t * 1.4;
                    if (Math.cos(th) < 0.05) continue;
                    float xx = bx + (float) Math.sin(th) * rr;
                    float tile = Math.max(1.2f, R / 6f * (float) Math.cos(th));
                    int shade = ((k * 7 + lat * 3 + beatN) % 5 == 0) ? 0xFFFFFF : mix(0x9A9AA8, 0xE8E8F0, (float) Math.cos(th));
                    rect(g, xx, yy, tile, R / 6f, 0, A(c.vis, shade));
                }
            }
            // confetti on the beat
            if (phase < 0.08 && s.last != beatN && c.spawning()) {
                s.last = beatN;
                for (int i = 0; i < 10 + 6 * c.L; i++) {
                    Bit b = bit(s.b, 500);
                    b.x = s.r.nextFloat() * c.w;
                    b.y = -6;
                    b.vy = 40 + s.r.nextFloat() * 60;
                    b.vx = (float) s.r.nextGaussian() * 20;
                    b.rot = s.r.nextFloat() * 6;
                    b.vr = (s.r.nextFloat() - 0.5f) * 10;
                    b.max = b.life = 6;
                    b.col = Fx.hsv(s.r.nextFloat(), 0.8f, 1f);
                }
            }
            step(s.b, c.dt, c.h);
            for (Bit b : s.b) rect(g, b.x, b.y, 2 + 2 * Math.abs((float) Math.cos(b.rot)), 3, b.rot, A(c.vis, b.col));
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            float[] bass = {note(0), note(0), note(5), note(3), note(0), note(0), note(7), note(5)};
            float[] mel = {note(12), note(16), note(19), note(24), note(19), note(16), note(14), note(12)};
            int i = 0;
            for (double t = 0; t < T - 0.4; t += BEAT, i++) {
                out.add(snd("block.note_block.basedrum", t, 0.9f, 1.0f));
                out.add(snd("block.note_block.hat", t + BEAT / 2, 0.45f, 1.6f));
                if (i % 2 == 1) out.add(snd("block.note_block.snare", t, 0.6f, 1.0f));
                out.add(snd("block.note_block.bass", t, 0.8f, bass[i % bass.length]));
                if (L >= 1) out.add(snd("block.note_block.pling", t + (i % 2 == 0 ? 0 : BEAT / 2), 0.4f, mel[i % mel.length]));
            }
        }
    }
}
