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

/** Themes 11-20. */
final class ThemesB {
    private ThemesB() {}

    static void register(List<Theme> out) {
        out.add(new EnderVortex());
        out.add(new TotemFrenzy());
        out.add(new RocketShow());
        out.add(new Treasure());
        out.add(new Supernova());
        out.add(new Dragon());
        out.add(new Royal());
        out.add(new Jackpot());
        out.add(new Synthwave());
        out.add(new BlackHole());
    }

    // =====================================================================================
    static final class EnderVortex extends Theme {
        EnderVortex() {
            super("ender_vortex", "Ender Vortex", "A purple End portal vortex with orbiting ender eyes and light beams.");
        }

        @Override
        public void draw(Ctx c) {
            Bag s = c.bag;
            GuiGraphics g = c.g;
            float vx = c.w / 2f, vy = c.h * 0.58f;
            tint(c, 0x0B0014, 0.55f * c.vis);
            glow(g, vx, vy, 140 + 20 * c.L, 0x7A1FD6, 0.35f * c.vis);
            // spiral arms
            int arms = 3 + (c.L >= 3 ? 1 : 0);
            float spin = (float) (c.t * (1.6 + c.L * 0.35));
            for (int arm = 0; arm < arms; arm++) {
                for (int i = 0; i < 70; i++) {
                    float r = 10 + i * (3.2f + c.L * 0.35f) * (c.h / 360f);
                    double ang = arm * Math.PI * 2 / arms + i * 0.16 - spin;
                    float x = vx + (float) Math.cos(ang) * r, y = vy + (float) Math.sin(ang) * r * 0.62f;
                    float a = c.vis * (1 - i / 70f) * 0.9f;
                    int col = mix(0xF0B8FF, 0x7A1FD6, i / 70f);
                    float sz = 1.5f + (1 - i / 70f) * 2.5f;
                    rect(g, x, y, sz, sz, 0, A(a, col));
                }
            }
            // portal dust falling in
            if (c.spawning()) {
                s.acc1 += c.dt * (60 + 30 * c.L) * c.k;
                while (s.acc1 >= 1) {
                    s.acc1--;
                    Bit b = bit(s.a, 600);
                    b.a = s.r.nextFloat() * 6.28f;
                    b.b = Math.max(c.w, c.h) * (0.5f + s.r.nextFloat() * 0.2f);
                    b.col = s.r.nextBoolean() ? 0xD070FF : 0x8A2BE2;
                    b.max = b.life = 10;
                }
            }
            for (Bit b : s.a) {
                b.a += c.dt * (0.8f + 120f / Math.max(20, b.b));
                b.b -= c.dt * (60 + 9000f / Math.max(30, b.b));
                if (b.b < 16) b.dead = true;
                b.x = vx + (float) Math.cos(b.a) * b.b;
                b.y = vy + (float) Math.sin(b.a) * b.b * 0.62f;
            }
            step(s.a, c.dt, c.h);
            for (Bit b : s.a) rect(g, b.x, b.y, 2, 2, 0, A(c.vis * Math.min(1, b.b / 50f), b.col));
            // beams
            if (c.t > 1.0) {
                float a = clamp((c.t - 1.0) / 0.5) * c.vis * (0.25f + 0.1f * (float) Math.sin(c.t * 6));
                for (int i = 0; i < 4; i++) {
                    float bx = vx + (i - 1.5f) * 18;
                    box(g, bx - 3, 0, 6, vy, A(a, 0xE8C8FF));
                    box(g, bx - 1, 0, 2, vy, A(a * 1.8f, 0xFFFFFF));
                }
            }
            // core
            disc(g, vx, vy, 20 + c.L * 2, 12 + c.L * 1.2f, A(c.vis, 0x05000A));
            ring(g, vx, vy, 22 + c.L * 2, 13.5f + c.L * 1.2f, 1.5f, A(c.vis, 0xE070FF), 48);
            // orbiting eyes and pearls
            int n = 6 + c.L * 2;
            for (int i = 0; i < n; i++) {
                double ang = i * Math.PI * 2 / n + c.t * 0.9;
                float r = (90 + (i % 2) * 30) * (c.h / 360f);
                float x = vx + (float) Math.cos(ang) * r * 1.4f, y = vy + (float) Math.sin(ang) * r * 0.5f;
                item(g, stack(i % 2 == 0 ? "ender_eye" : "ender_pearl"), x, y, 1.1f + (float) Math.sin(ang) * 0.3f);
            }
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            out.add(snd("block.end_portal.spawn", 0.0, 0.45f, 1.0f));
            for (int i = 0; i < 4; i++) out.add(snd("block.end_portal_frame.fill", 0.25 + i * 0.25, 0.9f, 0.8f + i * 0.2f));
            Random r = new Random(13);
            for (double t = 1.4; t < T - 1; t += 0.6 + r.nextDouble() * 0.6) out.add(snd("entity.enderman.teleport", t, 0.5f, 0.8f + r.nextFloat() * 0.6f));
            out.add(snd("block.portal.trigger", 1.0, 0.25f, 1.5f));
            out.add(snd("entity.ender_eye.death", T - 1.0, 0.8f, 1.0f));
        }
    }

    // =====================================================================================
    static final class TotemFrenzy extends Theme {
        TotemFrenzy() {
            super("totem_frenzy", "Totem Frenzy", "Dozens of totems of undying pop all over the screen in bursts of green and gold.");
        }

        static double[] pops(int L, double T) {
            int n = 8 + 6 * L;
            double[] a = new double[n];
            double span = T * 0.75;
            Random r = new Random(77 + L);
            for (int i = 0; i < n; i++) a[i] = span * i / n + r.nextDouble() * 0.12;
            return a;
        }

        @Override
        public void start(Ctx c) {
            double[] p = pops(c.L, c.T);
            float[] f = new float[p.length * 2];
            for (int i = 0; i < p.length; i++) {
                f[i * 2] = c.w * (0.08f + c.bag.r.nextFloat() * 0.84f);
                f[i * 2 + 1] = c.h * (0.12f + c.bag.r.nextFloat() * 0.78f);
            }
            c.bag.f = f;
            c.bag.extra = p;
        }

        @Override
        public void draw(Ctx c) {
            Bag s = c.bag;
            GuiGraphics g = c.g;
            double[] p = (double[]) s.extra;
            float pulse = 0.5f + 0.5f * (float) Math.sin(c.t * 5);
            glow(g, c.cx, c.cy, 160 + 30 * c.L, 0xC8FF5A, (0.15f + 0.1f * pulse) * c.vis);
            ItemStack totem = stack("totem_of_undying");
            for (int i = 0; i < p.length; i++) {
                double age = c.t - p[i];
                if (age < 0 || age > 1.6) continue;
                float x = s.f[i * 2], y = s.f[i * 2 + 1] - (float) Math.max(0, age - 0.35) * 40;
                if (i >= s.acc1) {
                    s.acc1 = i + 1;
                    for (int k = 0; k < 30 + 6 * c.L; k++) {
                        Bit b = bit(s.a, 1000);
                        double a = s.r.nextDouble() * Math.PI * 2;
                        double sp = 60 + s.r.nextDouble() * 170;
                        b.x = x;
                        b.y = y;
                        b.vx = (float) (Math.cos(a) * sp);
                        b.vy = (float) (Math.sin(a) * sp) - 60;
                        b.grav = 40;
                        b.drag = 1.8f;
                        b.max = b.life = 1.2f + s.r.nextFloat() * 0.8f;
                        b.col = s.r.nextInt(3) == 0 ? 0xFFE84A : s.r.nextBoolean() ? 0x6BFF5A : 0xB8FF4A;
                        b.size = 1 + s.r.nextFloat() * 1.2f;
                    }
                }
                float sc = (float) (age < 0.35 ? easeOutBack(age / 0.35) : 1 - (age - 0.35) / 1.25 * 0.6) * (2.4f + c.L * 0.3f);
                glow(g, x, y, 20 * sc, 0xFFF080, 0.4f * c.vis * (float) Math.max(0, 1 - age / 1.6));
                item(g, totem, x, y, sc);
            }
            step(s.a, c.dt, c.h);
            for (Bit b : s.a) {
                float a = b.q() * c.vis;
                rect(g, b.x, b.y, b.size * 2, b.size * 2, 0.78f, A(a, b.col));
            }
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            Random r = new Random(4);
            double[] p = pops(L, T);
            for (int i = 0; i < p.length; i++) out.add(snd("item.totem.use", p[i], i == 0 ? 0.8f : 0.4f, 0.85f + r.nextFloat() * 0.5f));
            out.add(snd("entity.player.levelup", 0.2, 0.8f, 1.3f));
        }
    }

    // =====================================================================================
    static final class RocketShow extends Theme {
        private static final float G = 300;

        RocketShow() {
            super("rocket_show", "Rocket Show", "Rockets launch and burst into rings, stars, hearts and golden willows.");
        }

        /** [launch time, x fraction, apex y fraction, pattern] for each rocket. */
        static double[][] plan(int L, double T) {
            int n = 4 + 3 * L;
            Random r = new Random(55 + L);
            double[][] a = new double[n][];
            double span = Math.max(0.5, T * 0.65);
            for (int i = 0; i < n; i++) {
                a[i] = new double[]{0.05 + span * i / n + r.nextDouble() * 0.1, 0.15 + r.nextDouble() * 0.7, 0.1 + r.nextDouble() * 0.35, r.nextInt(6)};
            }
            return a;
        }

        /** Gravity grows with the screen height so rockets take the same time on any screen. */
        static double grav(int h) {
            return G * h / 360.0;
        }

        static double flight(double apexFrac, int h) {
            double dist = h * (1 - apexFrac) + 10;
            return Math.sqrt(2 * dist / grav(h));
        }

        @Override
        public void draw(Ctx c) {
            Bag s = c.bag;
            GuiGraphics g = c.g;
            tint(c, 0x02030A, 0.4f * c.vis);
            double[][] plan = plan(c.L, c.T);
            if (s.f.length != plan.length) s.f = new float[plan.length];
            for (int i = 0; i < plan.length; i++) {
                double[] r = plan[i];
                double ft = flight(r[2], c.h);
                double age = c.t - r[0];
                if (age < 0) continue;
                float x0 = (float) (r[1] * c.w);
                double gg = grav(c.h);
                float v = (float) (gg * ft);
                if (age < ft) {
                    float y = (float) (c.h + 10 - (v * age - 0.5 * gg * age * age));
                    float vyNow = (float) (-(v - gg * age));
                    float x = x0 + (float) Math.sin(age * 3 + i) * 6;
                    float ang = (float) Math.atan2(vyNow, Math.cos(age * 3 + i) * 18) + (float) Math.PI / 2;
                    // smoke
                    if (s.r.nextFloat() < 0.8f) {
                        Bit sm = bit(s.c, 600);
                        sm.x = x + (float) s.r.nextGaussian();
                        sm.y = y + 8;
                        sm.vx = (float) s.r.nextGaussian() * 6;
                        sm.vy = 10;
                        sm.max = sm.life = 1.0f;
                        sm.size = 2;
                    }
                    rect(g, x, y + 7, 3, 6 + s.r.nextFloat() * 4, ang, A(c.vis, s.r.nextBoolean() ? 0xFFB020 : 0xFFF080));
                    rect(g, x, y, 4, 10, ang, A(c.vis, i % 2 == 0 ? 0xD02020 : 0x2050D0));
                    rect(g, x, y - 1, 4, 2, ang, A(c.vis, 0xFFFFFF));
                    rect(g, x, y - 6, 2.5f, 3, ang, A(c.vis, 0xE8E8E8));
                } else if (s.f[i] == 0) {
                    s.f[i] = 1;
                    explode(c, x0, (float) (r[2] * c.h), (int) r[3], i);
                }
            }
            step(s.c, c.dt, c.h);
            for (Bit sm : s.c) {
                float q = sm.q();
                rect(g, sm.x, sm.y, sm.size + (1 - q) * 6, sm.size + (1 - q) * 6, 0, A(q * 0.35f * c.vis, 0x9A9AA0));
            }
            for (Bit b : s.a) {
                if (b.kind == 3) b.vy = Math.min(b.vy, 40); // willow droops
            }
            step(s.a, c.dt, c.h);
            for (Bit b : s.a) {
                float q = b.q();
                float a = c.vis * (b.kind == 5 ? ((int) (b.life * 20) % 2 == 0 ? 1 : 0.2f) : Math.min(1, q * 1.6f));
                line(g, b.x, b.y, b.x - b.vx * (b.kind == 3 ? 0.09f : 0.04f), b.y - b.vy * (b.kind == 3 ? 0.09f : 0.04f), b.size, A(a * 0.5f, b.col));
                rect(g, b.x, b.y, b.size * 1.6f, b.size * 1.6f, 0, A(a, b.col));
            }
        }

        private static void explode(Ctx c, float x, float y, int pattern, int idx) {
            Bag s = c.bag;
            int col = c.rainbow ? Fx.hsv(s.r.nextFloat(), 0.85f, 1f) : (idx % 2 == 0 ? c.col : Fx.hsv(s.r.nextFloat(), 0.8f, 1f));
            int n = (int) ((70 + 20 * c.L) * Math.min(1.8, c.k));
            for (int i = 0; i < n; i++) {
                Bit b = bit(s.a, 1600);
                double t = i * Math.PI * 2 / n;
                double vx, vy;
                switch (pattern) {
                    case 1 -> { // heart
                        double hx = 16 * Math.pow(Math.sin(t), 3);
                        double hy = -(13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t));
                        vx = hx * 9;
                        vy = hy * 9;
                        b.col = 0xFF3355;
                    }
                    case 2 -> { // star
                        double rr = 120 + 90 * Math.cos(5 * t);
                        vx = Math.cos(t) * rr;
                        vy = Math.sin(t) * rr;
                        b.col = 0xFFE066;
                    }
                    case 3 -> { // willow
                        double sp = 80 + s.r.nextDouble() * 80;
                        vx = Math.cos(t) * sp;
                        vy = Math.sin(t) * sp;
                        b.col = 0xFFC040;
                        b.kind = 3;
                    }
                    case 4 -> { // double ring
                        double sp = i % 2 == 0 ? 170 : 95;
                        vx = Math.cos(t) * sp;
                        vy = Math.sin(t) * sp;
                        b.col = i % 2 == 0 ? col : 0xFFFFFF;
                    }
                    case 5 -> { // crackle
                        double sp = 40 + s.r.nextDouble() * 140;
                        vx = Math.cos(t) * sp;
                        vy = Math.sin(t) * sp;
                        b.col = 0xFFFFF0;
                        b.kind = 5;
                    }
                    default -> { // ring
                        vx = Math.cos(t) * 160;
                        vy = Math.sin(t) * 160;
                        b.col = col;
                    }
                }
                b.x = x;
                b.y = y;
                b.vx = (float) vx;
                b.vy = (float) vy;
                b.grav = pattern == 3 ? 60 : 45;
                b.drag = pattern == 3 ? 1.2f : 1.6f;
                b.max = b.life = pattern == 3 ? 2.4f : 1.4f + s.r.nextFloat() * 0.4f;
                b.size = pattern == 5 ? 1 : 1.4f;
                if (b.kind == 0) b.kind = 1;
            }
            glow(c.g, x, y, 50, 0xFFFFFF, 0.6f * c.vis);
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            double[][] plan = plan(L, T);
            Random r = new Random(8);
            for (double[] p : plan) {
                out.add(snd("entity.firework_rocket.launch", p[0], 0.7f, 0.9f + r.nextFloat() * 0.3f));
                double boom = p[0] + flight(p[2], 360);
                out.add(snd(r.nextBoolean() ? "entity.firework_rocket.large_blast" : "entity.firework_rocket.blast", boom, 0.9f, 0.9f + r.nextFloat() * 0.2f));
                if ((int) p[3] == 3 || (int) p[3] == 5) out.add(snd("entity.firework_rocket.twinkle", boom + 0.4, 0.8f, 1.0f));
            }
        }
    }

    // =====================================================================================
    static final class Treasure extends Theme {
        private static final String[] LOOT = {"diamond", "emerald", "gold_ingot", "netherite_ingot", "enchanted_book", "golden_apple",
                "diamond", "emerald", "gold_ingot", "experience_bottle"};
        private static final double OPEN = 0.8;

        Treasure() {
            super("treasure", "Treasure Chest", "A treasure chest rattles, bursts open and sprays out loot in a beam of light.");
        }

        @Override
        public void draw(Ctx c) {
            Bag s = c.bag;
            GuiGraphics g = c.g;
            float S = (52 + c.L * 6) * (c.h / 360f);
            float x = c.w / 2f, base = c.h * 0.9f;
            float bw = S * 1.4f, bh = S * 0.62f, lh = S * 0.34f;
            if (c.t > 0.15 && c.t < OPEN) x += (float) Math.sin(c.t * 70) * 2.5f;
            double openP = easeOutBack((c.t - OPEN) / 0.35);
            // light from inside
            if (c.t > OPEN) {
                float a = c.vis * clamp((c.t - OPEN) / 0.3) * (0.85f + 0.15f * (float) Math.sin(c.t * 7));
                int rays = 9 + c.L * 2;
                for (int i = 0; i < rays; i++) {
                    float ang = (float) (-Math.PI / 2 + (i - (rays - 1) / 2f) * 0.16f + Math.sin(c.t * 1.5 + i) * 0.04);
                    for (int k = 0; k < 5; k++) {
                        float d1 = c.h * 0.9f * k / 5, d2 = c.h * 0.9f * (k + 1) / 5;
                        float mx = x + (float) Math.cos(ang) * (d1 + d2) / 2, my = base - bh + (float) Math.sin(ang) * (d1 + d2) / 2;
                        rect(g, mx, my, d2 - d1, 3 + k * 6, ang, A(a * 0.13f, 0xFFE58A));
                    }
                }
                glow(g, x, base - bh, S * 1.6f, 0xFFE070, a * 0.7f);
            }
            // body
            float left = x - bw / 2, top = base - bh;
            box(g, left - 1, top - 1, bw + 2, bh + 2, A(c.vis, 0x2E1A08));
            box(g, left, top, bw, bh, A(c.vis, 0x8A5426));
            for (int i = 1; i < 4; i++) box(g, left, top + bh * i / 4f, bw, 1, A(c.vis, 0x5E3614));
            box(g, left, top, bw * 0.08f, bh, A(c.vis, 0xE0B040));
            box(g, left + bw * 0.92f, top, bw * 0.08f, bh, A(c.vis, 0xE0B040));
            box(g, left, top, bw, 2, A(c.vis, 0xE0B040));
            // lid
            float lidScale = (float) Math.max(0.12, Math.cos(Math.min(1.0, Math.max(0, openP)) * 1.35));
            float lt = top - lh * lidScale - (float) Math.max(0, openP) * lh * 0.8f;
            if (c.t > OPEN) box(g, left + 2, top - 3, bw - 4, 4, A(c.vis, 0x1A0E04));
            box(g, left - 1, lt - 1, bw + 2, lh * lidScale + 2, A(c.vis, 0x2E1A08));
            box(g, left, lt, bw, lh * lidScale, A(c.vis, 0x9C6230));
            box(g, left, lt, bw * 0.08f, lh * lidScale, A(c.vis, 0xE0B040));
            box(g, left + bw * 0.92f, lt, bw * 0.08f, lh * lidScale, A(c.vis, 0xE0B040));
            // lock
            if (c.t < OPEN + 0.1) {
                box(g, x - S * 0.09f, top - S * 0.08f, S * 0.18f, S * 0.22f, A(c.vis, 0xF2C94C));
                box(g, x - S * 0.025f, top, S * 0.05f, S * 0.08f, A(c.vis, 0x2E1A08));
            }
            // loot fountain
            if (c.t > OPEN && c.spawning()) {
                s.acc1 += c.dt * (16 + 10 * c.L) * c.k;
                while (s.acc1 >= 1) {
                    s.acc1--;
                    Bit b = bit(s.a, 400);
                    String id = LOOT[s.r.nextInt(LOOT.length)];
                    if (c.L >= 3 && s.r.nextInt(6) == 0) id = "enchanted_golden_apple";
                    if (c.L >= 4 && s.r.nextInt(6) == 0) id = s.r.nextBoolean() ? "nether_star" : "totem_of_undying";
                    b.item = stack(id);
                    b.x = x + (float) s.r.nextGaussian() * bw * 0.15f;
                    b.y = top - 4;
                    b.vx = (float) s.r.nextGaussian() * 90;
                    b.vy = -(280 + s.r.nextFloat() * 200) * (c.h / 360f);
                    b.grav = 400 * (c.h / 360f);
                    b.max = b.life = 4;
                    b.size = 0.9f + s.r.nextFloat() * 0.6f;
                }
                if (s.r.nextFloat() < c.dt * 20) s.b.add(ThemesA.glint(x + (float) s.r.nextGaussian() * bw, top - s.r.nextFloat() * c.h * 0.5f));
            }
            step(s.a, c.dt, c.h);
            for (Bit b : s.a) item(g, b.item, b.x, b.y, b.size);
            ThemesA.drawGlints(c, s.b);
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            out.add(snd("block.chest.locked", 0.2, 0.9f, 1.0f));
            out.add(snd("block.chest.locked", 0.5, 0.9f, 1.1f));
            out.add(snd("block.chest.open", OPEN, 1.0f, 0.9f));
            out.add(snd("entity.player.levelup", OPEN + 0.1, 0.9f, 1.0f));
            Random r = new Random(17);
            for (double t = OPEN + 0.2; t < T - 1; t += 0.11) out.add(snd("entity.experience_orb.pickup", t, 0.3f, 0.8f + r.nextFloat() * 0.8f));
            for (int i = 0; i < 6; i++) out.add(snd("block.amethyst_block.chime", OPEN + 0.3 + i * 0.3, 0.7f, 1.0f + i * 0.15f));
            out.add(snd("block.chest.close", T - 0.6, 0.6f, 1.0f));
        }
    }

    // =====================================================================================
    static final class Supernova extends Theme {
        private static final double COLLAPSE = 1.6, BOOM = 1.9;

        Supernova() {
            super("supernova", "Supernova", "A star charges up, collapses... then explodes in a blinding supernova.");
        }

        @Override
        public void draw(Ctx c) {
            Bag s = c.bag;
            GuiGraphics g = c.g;
            float x = c.w / 2f, y = c.h * 0.55f;
            float scale = c.h / 360f;
            if (c.t < BOOM) {
                tint(c, 0x000000, (float) (0.35 + 0.35 * clamp(c.t / BOOM)) * c.vis);
                float r;
                if (c.t < COLLAPSE) {
                    r = (float) (10 + 34 * easeOut(c.t / COLLAPSE)) * scale + (float) Math.sin(c.t * (12 + c.t * 24)) * 3;
                } else {
                    r = (float) (44 * scale * (1 - (c.t - COLLAPSE) / (BOOM - COLLAPSE)) + 3);
                }
                glow(g, x, y, r * 3.2f, 0xFF9A3C, 0.55f * c.vis);
                glow(g, x, y, r * 1.8f, 0xFFE08A, 0.7f * c.vis);
                disc(g, x, y, r, A(c.vis, 0xFFFFF4));
                // matter pulled in
                s.acc1 += c.dt * (90 + 40 * c.L);
                while (s.acc1 >= 1) {
                    s.acc1--;
                    Bit b = bit(s.a, 800);
                    b.a = s.r.nextFloat() * 6.28f;
                    b.b = Math.max(c.w, c.h) * 0.55f;
                    b.col = s.r.nextBoolean() ? 0xFFD080 : 0xFF8040;
                    b.max = b.life = 3;
                }
                for (Bit b : s.a) {
                    float prev = b.b;
                    b.b -= c.dt * (200 + 30000f / Math.max(40, b.b));
                    if (b.b < r) b.dead = true;
                    float x1 = x + (float) Math.cos(b.a) * b.b, y1 = y + (float) Math.sin(b.a) * b.b;
                    float x0 = x + (float) Math.cos(b.a) * prev * 1.04f, y0 = y + (float) Math.sin(b.a) * prev * 1.04f;
                    line(g, x0, y0, x1, y1, 1.2f, A(c.vis * 0.8f, b.col));
                }
                step(s.a, c.dt, c.h);
                return;
            }
            double since = c.t - BOOM;
            if (s.last < 0) {
                s.last = 1;
                s.a.clear();
                int n = (int) ((320 + 120 * c.L) * Math.min(2, c.k));
                for (int i = 0; i < n; i++) {
                    Bit b = bit(s.a, 1600);
                    double a = s.r.nextDouble() * Math.PI * 2;
                    double sp = (80 + s.r.nextDouble() * 520) * scale;
                    b.x = x;
                    b.y = y;
                    b.vx = (float) (Math.cos(a) * sp);
                    b.vy = (float) (Math.sin(a) * sp);
                    b.drag = 1.1f;
                    b.max = b.life = 1.5f + s.r.nextFloat() * 1.8f;
                    b.size = 1 + s.r.nextFloat() * 1.6f;
                }
                for (int i = 0; i < 7; i++) {
                    Bit n2 = new Bit();
                    n2.x = x + (float) s.r.nextGaussian() * 40;
                    n2.y = y + (float) s.r.nextGaussian() * 30;
                    n2.col = i % 3 == 0 ? 0xB040FF : i % 3 == 1 ? 0xFF4FA0 : 0x3C7BFF;
                    n2.size = 40 + s.r.nextFloat() * 60;
                    s.b.add(n2);
                }
            }
            tint(c, 0x000000, 0.35f * c.vis);
            for (Bit n2 : s.b) glow(g, n2.x, n2.y, n2.size * (1 + (float) since * 0.5f), n2.col, 0.35f * c.vis * clamp(1 - since / (c.T - BOOM + 0.1)));
            float[] speeds = {1.0f, 0.75f, 0.5f};
            int[] cols = {0xFFFFFF, 0xFFA040, 0xB050FF};
            for (int i = 0; i < 3; i++) {
                double age = since - i * 0.08;
                if (age < 0 || age > 1.3) continue;
                float rr = (float) (age * c.w * speeds[i]);
                ring(g, x, y, rr, rr * 0.92f, 5 - i, A((float) (1 - age / 1.3) * c.vis, cols[i]), 72);
            }
            step(s.a, c.dt, c.h);
            for (Bit b : s.a) {
                float q = b.q();
                int col = q > 0.7f ? 0xFFFFF0 : q > 0.4f ? mix(0xFF8030, 0xFFFFF0, (q - 0.4f) / 0.3f) : mix(0x8030C0, 0xFF8030, q / 0.4f);
                line(g, b.x, b.y, b.x - b.vx * 0.04f, b.y - b.vy * 0.04f, b.size, A(q * c.vis, col));
            }
            // the leftover pulsar
            float pr = 6 + (float) Math.sin(c.t * 20) * 1.5f;
            glow(g, x, y, pr * 5, 0x9FD8FF, 0.5f * c.vis);
            disc(g, x, y, pr, A(c.vis, 0xFFFFFF));
            float ja = (float) (c.t * 0.8);
            line(g, x - (float) Math.cos(ja) * c.h, y - (float) Math.sin(ja) * c.h, x + (float) Math.cos(ja) * c.h, y + (float) Math.sin(ja) * c.h, 2, A(0.35f * c.vis, 0xBFE6FF));
            if (c.flashes && since < 0.45) tint(c, 0xFFFFFF, (float) (1 - since / 0.45) * 0.85f);
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            out.add(snd("block.respawn_anchor.charge", 0.0, 0.9f, 0.7f));
            out.add(snd("block.respawn_anchor.charge", 0.5, 0.9f, 1.0f));
            out.add(snd("block.respawn_anchor.charge", 1.0, 0.9f, 1.3f));
            out.add(snd("entity.warden.sonic_charge", 0.35, 0.5f, 1.2f));
            out.add(snd("block.beacon.deactivate", COLLAPSE, 1.0f, 0.6f));
            out.add(snd("entity.generic.explode", BOOM, 1.0f, 0.7f));
            out.add(snd("entity.dragon_fireball.explode", BOOM, 0.9f, 0.8f));
            out.add(snd("entity.lightning_bolt.thunder", BOOM + 0.05, 0.6f, 0.7f));
            if (L >= 2) out.add(snd("entity.warden.sonic_boom", BOOM, 0.5f, 1.0f));
            out.add(snd("block.beacon.ambient", BOOM + 0.8, 1.0f, 1.5f));
            out.add(snd("block.amethyst_block.chime", BOOM + 1.2, 0.8f, 0.6f));
        }
    }

    // =====================================================================================
    static final class Dragon extends Theme {
        Dragon() {
            super("dragon", "Dragon Flight", "The Ender Dragon swoops across your screen, wings flapping, breathing purple fire.");
        }

        private static float[] path(Ctx c, double t) {
            double pass = 3.4;
            int n = (int) (t / pass);
            double f = (t % pass) / pass;
            boolean right = n % 2 == 0;
            float x = (float) (right ? -80 + f * (c.w + 160) : c.w + 80 - f * (c.w + 160));
            float y = (float) (c.h * (n % 2 == 0 ? 0.62 : 0.45) + Math.sin(f * Math.PI * 2) * c.h * 0.1);
            return new float[]{x, y, right ? 1 : -1};
        }

        @Override
        public void draw(Ctx c) {
            Bag s = c.bag;
            GuiGraphics g = c.g;
            float sc = (1.0f + c.L * 0.12f) * (c.h / 360f);
            tint(c, 0x0A0012, 0.35f * c.vis);
            if (s.r.nextFloat() < c.dt * 12) s.c.add(ThemesA.glint(s.r.nextFloat() * c.w, s.r.nextFloat() * c.h * 0.6f));
            ThemesA.drawGlints(c, s.c);
            double tt = Math.min(c.t, c.T - 0.6);
            float[] head = path(c, tt);
            // breath
            if (c.spawning()) {
                s.acc1 += c.dt * (50 + 20 * c.L);
                while (s.acc1 >= 1) {
                    s.acc1--;
                    Bit b = bit(s.a, 500);
                    b.x = head[0] + head[2] * 22 * sc;
                    b.y = head[1] + 4 * sc;
                    b.vx = head[2] * (120 + s.r.nextFloat() * 80);
                    b.vy = (float) s.r.nextGaussian() * 25 + 20;
                    b.drag = 1.2f;
                    b.max = b.life = 1.2f + s.r.nextFloat() * 0.6f;
                    b.size = 3;
                    b.col = s.r.nextBoolean() ? 0xD050FF : 0x8A2BE2;
                }
            }
            step(s.a, c.dt, c.h);
            for (Bit b : s.a) {
                float q = b.q();
                rect(g, b.x, b.y, b.size + (1 - q) * 14, b.size + (1 - q) * 14, 0, A(q * 0.5f * c.vis, b.col));
            }
            // body: a chain of segments following the head's path
            int segs = 20;
            float[][] pts = new float[segs][];
            for (int i = 0; i < segs; i++) pts[i] = path(c, Math.max(0, tt - i * 0.035));
            for (int i = segs - 1; i >= 0; i--) {
                float r = (i == 0 ? 8 : 9 - i * 0.38f) * sc;
                disc(g, pts[i][0], pts[i][1], Math.max(1.5f, r), Math.max(1.2f, r * 0.8f), A(c.vis, 0x15091E));
                if (i % 2 == 0 && i > 0 && i < 14) rect(g, pts[i][0], pts[i][1] - r * 0.9f, 2 * sc, 3 * sc, 0, A(c.vis, 0x3A1A4A));
            }
            // wings
            float flap = (float) Math.sin(c.t * 9);
            float[] sh = pts[4];
            for (int side = -1; side <= 1; side += 2) {
                float base = (float) (-Math.PI / 2 * side);
                float ang = base + side * (0.2f + flap * 0.7f) * -1;
                float[] lens = {56, 44, 32};
                for (int k = 0; k < 3; k++) {
                    float a = ang + side * k * 0.35f * (head[2] > 0 ? 1 : -1);
                    float L = lens[k] * sc;
                    float ex = sh[0] + (float) Math.cos(a) * L * 0.6f * -head[2], ey = sh[1] + (float) Math.sin(a) * L;
                    line(g, sh[0], sh[1], ex, ey, 2.2f * sc, A(c.vis, 0x2A1236));
                    for (int m = 1; m <= 6; m++) {
                        float f = m / 6f;
                        line(g, sh[0] + (ex - sh[0]) * f, sh[1] + (ey - sh[1]) * f,
                                pts[7][0] + (ex - sh[0]) * f * 0.6f, pts[7][1] + (ey - sh[1]) * f * 0.7f, 2.5f * sc, A(c.vis * 0.85f, 0x1A0A24));
                    }
                }
            }
            // head and glowing eyes
            float hx = head[0] + head[2] * 10 * sc, hy = head[1];
            disc(g, hx, hy, 10 * sc, 6 * sc, A(c.vis, 0x15091E));
            rect(g, hx + head[2] * 10 * sc, hy + 2 * sc, 10 * sc, 4 * sc, 0, A(c.vis, 0x15091E));
            glow(g, hx + head[2] * 4 * sc, hy - 2 * sc, 8 * sc, 0xE070FF, 0.8f * c.vis);
            rect(g, hx + head[2] * 4 * sc, hy - 2 * sc, 4 * sc, 1.6f * sc, 0, A(c.vis, 0xF4B8FF));
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            out.add(snd("entity.ender_dragon.growl", 0.2, 0.6f, 1.0f));
            for (double t = 0.1; t < T - 0.6; t += 0.7) out.add(snd("entity.ender_dragon.flap", t, 0.8f, 0.9f));
            out.add(snd("entity.ender_dragon.shoot", 1.2, 0.8f, 1.0f));
            if (T > 5) out.add(snd("entity.ender_dragon.ambient", T * 0.5, 0.6f, 1.0f));
            if (L >= 3) out.add(snd("entity.dragon_fireball.explode", T * 0.6, 0.7f, 1.0f));
        }
    }

    // =====================================================================================
    static final class Royal extends Theme {
        Royal() {
            super("royal", "Royal Decree", "Purple banners unroll, a red carpet rolls out and a golden crown drops with a fanfare.");
        }

        @Override
        public void draw(Ctx c) {
            Bag s = c.bag;
            GuiGraphics g = c.g;
            float scale = c.h / 360f;
            tint(c, 0x12001F, 0.3f * c.vis);
            // red carpet
            float unroll = (float) easeOut(c.t / 1.2);
            float top = c.h * 0.78f;
            int rows = 12;
            for (int i = 0; i < rows; i++) {
                float f0 = i / (float) rows, f1 = (i + 1) / (float) rows;
                if (f0 > unroll) break;
                float y0 = top + (c.h - top) * f0, y1 = top + (c.h - top) * Math.min(f1, unroll);
                float w0 = c.w * (0.12f + 0.25f * f0);
                box(g, c.w / 2f - w0 / 2, y0, w0, y1 - y0 + 0.5f, A(c.vis, 0xA4161A));
                box(g, c.w / 2f - w0 / 2, y0, 2, y1 - y0 + 0.5f, A(c.vis, 0xE6B422));
                box(g, c.w / 2f + w0 / 2 - 2, y0, 2, y1 - y0 + 0.5f, A(c.vis, 0xE6B422));
            }
            // banners
            float bannerLen = c.h * 0.72f * (float) easeOut(c.t / 0.9);
            float bw = (34 + c.L * 4) * scale;
            for (int side = 0; side < 2; side++) {
                float bx = side == 0 ? c.w * 0.1f : c.w * 0.9f;
                box(g, bx - bw / 2 - 6, 2, bw + 12, 3, A(c.vis, 0xC9A227));
                for (float y = 5; y < bannerLen; y += 6) {
                    float wave = (float) Math.sin(y * 0.045 - c.t * 4 + side) * 3.5f * (y / Math.max(1, bannerLen));
                    box(g, bx - bw / 2 + wave, y, bw, 6.5f, A(c.vis, 0x5B1A8C));
                    box(g, bx - bw / 2 + wave, y, 2, 6.5f, A(c.vis, 0xE6B422));
                    box(g, bx + bw / 2 - 2 + wave, y, 2, 6.5f, A(c.vis, 0xE6B422));
                }
                if (bannerLen > 40) {
                    float wave = (float) Math.sin(bannerLen * 0.045 - c.t * 4 + side) * 3.5f;
                    for (int k = 0; k < 8; k++) {
                        float wdt = bw * (1 - k / 8f);
                        box(g, bx - wdt / 2 + wave, bannerLen + k * 2, wdt, 2.2f, A(c.vis, 0x5B1A8C));
                    }
                    text(g, c.font, "♛", bx + (float) Math.sin(bannerLen * 0.3 * 0.045 - c.t * 4 + side) * 2, bannerLen * 0.35f, 2.4f * scale, A(c.vis, 0xFFD34D), 0, true);
                    for (int k = 0; k < 5; k++) {
                        float tx = bx - bw / 2 + bw * (k + 0.5f) / 5 + wave;
                        line(g, tx, bannerLen + 14, tx, bannerLen + 22 + (float) Math.sin(c.t * 5 + k) * 2, 1.2f, A(c.vis, 0xE6B422));
                    }
                }
            }
            // crown drop
            double d = easeOutBounce(c.t / 1.2);
            float crownY = (float) (-40 + (c.cy - 46 * scale - c.L * 3 + 40) * d);
            float cs = (5 + c.L * 0.8f) * scale;
            glow(g, c.cx, crownY, 30 * cs / 5, 0xFFD34D, 0.45f * c.vis);
            text(g, c.font, "♛", c.cx, crownY, cs, A(c.vis, 0xFFD34D), (float) Math.sin(c.t * 2) * 0.05f, true);
            if (c.t > 1.2 && s.r.nextFloat() < c.dt * 14) s.b.add(ThemesA.glint(c.cx + (float) s.r.nextGaussian() * 30 * scale, crownY + (float) s.r.nextGaussian() * 18 * scale));
            ThemesA.drawGlints(c, s.b);
            // gold confetti and nuggets
            if (c.spawning()) {
                s.acc1 += c.dt * (20 + 15 * c.L) * c.k;
                while (s.acc1 >= 1) {
                    s.acc1--;
                    Bit b = bit(s.a, 600);
                    b.x = s.r.nextFloat() * c.w;
                    b.y = -6;
                    b.vy = 40 + s.r.nextFloat() * 50;
                    b.vx = (float) s.r.nextGaussian() * 15;
                    b.rot = s.r.nextFloat() * 6;
                    b.vr = (s.r.nextFloat() - 0.5f) * 10;
                    b.max = b.life = 8;
                    b.col = s.r.nextInt(3) == 0 ? 0xFFFFFF : s.r.nextBoolean() ? 0xFFD34D : 0xC98A00;
                    b.kind = s.r.nextInt(12) == 0 ? 1 : 0;
                    if (b.kind == 1) b.item = stack("gold_nugget");
                }
            }
            step(s.a, c.dt, c.h);
            for (Bit b : s.a) {
                if (b.kind == 1) item(g, b.item, b.x, b.y, 0.9f);
                else rect(g, b.x, b.y, 2 + 2 * Math.abs((float) Math.cos(b.rot)), 3, b.rot, A(c.vis, b.col));
            }
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            int[] fan = {6, 10, 13, 18, 13, 18};
            double[] at = {0.15, 0.3, 0.45, 0.7, 0.95, 1.1};
            for (int i = 0; i < fan.length; i++) {
                out.add(snd("block.note_block.flute", at[i], 1.0f, note(fan[i])));
                out.add(snd("block.note_block.bell", at[i], 0.5f, note(fan[i])));
            }
            out.add(snd("block.bell.use", 1.2, 1.0f, 1.0f));
            out.add(snd("ui.toast.challenge_complete", 1.25, 0.8f, 1.0f));
            if (L >= 2) out.add(snd("block.bell.resonate", 1.4, 0.6f, 1.0f));
            out.add(snd("block.note_block.flute", T - 1.0, 0.8f, note(18)));
        }
    }

    // =====================================================================================
    static final class Jackpot extends Theme {
        private static final String[] SYM = {"gold_ingot", "emerald", "diamond", "totem_of_undying", "nether_star",
                "netherite_ingot", "enchanted_golden_apple", "redstone"};
        private static final double[] STOP = {1.0, 1.5, 2.0};

        Jackpot() {
            super("jackpot", "Jackpot", "A slot machine spins... and lands on triple! Lights flash and coins explode out.");
        }

        private static int win(int L) {
            return new int[]{0, 1, 2, 3, 4}[Math.min(4, L)];
        }

        private static double pos(double t, int reel, int L) {
            double v = 16;
            double stop = STOP[reel];
            int n = SYM.length;
            double ease = 0.4;
            double startEase = stop - ease;
            double at = v * startEase;
            double fin = Math.ceil((at + 3) / n) * n + win(L);
            if (t < startEase) return v * t;
            if (t >= stop) return fin;
            double f = (t - startEase) / ease;
            return at + (fin - at) * easeOutBack(f);
        }

        @Override
        public void draw(Ctx c) {
            Bag s = c.bag;
            GuiGraphics g = c.g;
            float sc = (1.0f + c.L * 0.06f) * (c.h / 360f);
            float cell = 34 * sc;
            float mw = cell * 3 + 24 * sc, mh = cell + 40 * sc;
            float mx = c.w / 2f - mw / 2, my = c.h * 0.88f - mh;
            boolean won = c.t >= STOP[2];
            double since = c.t - STOP[2];
            if (won) glow(g, c.w / 2f, my + mh / 2, mw * 1.2f, 0xFFD34D, 0.4f * c.vis * (0.7f + 0.3f * (float) Math.sin(c.t * 10)));
            // cabinet
            box(g, mx - 4, my - 4, mw + 8, mh + 8, A(c.vis, 0xC9A227));
            box(g, mx, my, mw, mh, A(c.vis, 0x8E0F14));
            box(g, mx + 6 * sc, my + 4 * sc, mw - 12 * sc, 14 * sc, A(c.vis, 0x1A0204));
            String marquee = won ? "JACKPOT!" : "SPINNING...";
            int mcol = won ? Fx.hsv((float) ((c.t * 2) % 1), 0.7f, 1f) : 0xFFD34D;
            text(g, c.font, marquee, c.w / 2f, my + 11.5f * sc, sc * (won ? 1.2f + 0.1f * (float) Math.sin(c.t * 12) : 1f), A(c.vis, mcol), 0, true);
            // bulbs around the edge
            int bulbs = 22;
            for (int i = 0; i < bulbs; i++) {
                float f = i / (float) bulbs;
                float bx, by;
                if (f < 0.25f) { bx = mx + mw * (f / 0.25f); by = my - 2; }
                else if (f < 0.5f) { bx = mx + mw + 2; by = my + mh * ((f - 0.25f) / 0.25f); }
                else if (f < 0.75f) { bx = mx + mw - mw * ((f - 0.5f) / 0.25f); by = my + mh + 2; }
                else { bx = mx - 2; by = my + mh - mh * ((f - 0.75f) / 0.25f); }
                boolean lit = won ? ((int) (c.t * 10) % 2 == 0) : (i + (int) (c.t * 14)) % 3 == 0;
                rect(g, bx, by, 3 * sc, 3 * sc, 0, A(c.vis, lit ? 0xFFFFB0 : 0x6A5010));
            }
            // reels
            for (int r = 0; r < 3; r++) {
                float wx = mx + 12 * sc + r * cell, wy = my + 22 * sc;
                box(g, wx + 1, wy, cell - 2, cell, A(c.vis, 0xF4F0E6));
                double p = pos(c.t, r, c.L);
                int base = (int) Math.floor(p);
                float frac = (float) (p - base);
                g.enableScissor((int) (wx + 1), (int) wy, (int) (wx + cell - 1), (int) (wy + cell));
                for (int k = -1; k <= 1; k++) {
                    int idx = Math.floorMod(base + k, SYM.length);
                    float iy = wy + cell / 2 + (k - frac) * cell * -1;
                    item(g, stack(SYM[idx]), wx + cell / 2, iy, 1.5f * sc);
                }
                g.disableScissor();
                g.fillGradient((int) wx + 1, (int) wy, (int) (wx + cell - 1), (int) (wy + cell * 0.3f), A(0.5f * c.vis, 0x000000), A(0, 0x000000));
                g.fillGradient((int) wx + 1, (int) (wy + cell * 0.7f), (int) (wx + cell - 1), (int) (wy + cell), A(0, 0x000000), A(0.5f * c.vis, 0x000000));
                if (c.t >= STOP[r] && c.t < STOP[r] + 0.25) box(g, wx + 1, wy, cell - 2, cell, A((float) (1 - (c.t - STOP[r]) / 0.25) * 0.6f * c.vis, 0xFFFFFF));
            }
            // lever
            float pull = c.t < 0.3 ? (float) Math.sin(c.t / 0.3 * Math.PI) : 0;
            float lx = mx + mw + 10 * sc, ly0 = my + 8 * sc, ly = ly0 + pull * 26 * sc;
            line(g, lx, my + mh * 0.6f, lx, ly, 3 * sc, A(c.vis, 0xB0B0B0));
            disc(g, lx, ly, 5 * sc, A(c.vis, 0xD02020));
            // win: coins burst out
            if (won && s.last < 0) {
                s.last = 1;
                int n = (int) ((50 + 30 * c.L) * Math.min(2, c.k));
                for (int i = 0; i < n; i++) {
                    Bit b = bit(s.a, 500);
                    b.x = c.w / 2f + (float) s.r.nextGaussian() * mw * 0.2f;
                    b.y = my;
                    b.vx = (float) s.r.nextGaussian() * 160;
                    b.vy = -(250 + s.r.nextFloat() * 300) * (c.h / 360f);
                    b.grav = 420 * (c.h / 360f);
                    b.max = b.life = 3;
                    b.size = 0.9f + s.r.nextFloat() * 0.6f;
                    b.item = stack(s.r.nextInt(3) == 0 ? SYM[win(c.L)] : s.r.nextBoolean() ? "gold_nugget" : "gold_ingot");
                }
            }
            if (won && c.spawning() && s.r.nextFloat() < c.dt * (6 + 3 * c.L)) {
                Bit b = bit(s.a, 500);
                b.x = c.w / 2f;
                b.y = my;
                b.vx = (float) s.r.nextGaussian() * 120;
                b.vy = -(250 + s.r.nextFloat() * 200) * (c.h / 360f);
                b.grav = 420 * (c.h / 360f);
                b.max = b.life = 3;
                b.size = 1;
                b.item = stack("gold_nugget");
            }
            step(s.a, c.dt, c.h);
            for (Bit b : s.a) item(g, b.item, b.x, b.y, b.size);
            if (won && c.flashes && since < 0.25) tint(c, 0xFFF4C0, (float) (1 - since / 0.25) * 0.4f);
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            out.add(snd("block.lever.click", 0.05, 1.0f, 0.8f));
            for (double t = 0.1; t < STOP[2]; t += 0.065) out.add(snd("block.note_block.hat", t, 0.25f, 1.2f + (float) (t % 0.2)));
            for (int i = 0; i < 3; i++) out.add(snd("block.note_block.chime", STOP[i], 1.0f, note(12 + i * 4)));
            out.add(snd("block.note_block.bell", STOP[2] + 0.1, 1.0f, note(24)));
            out.add(snd("entity.player.levelup", STOP[2] + 0.15, 1.0f, 1.2f));
            if (L >= 2) out.add(snd("ui.toast.challenge_complete", STOP[2] + 0.2, 0.8f, 1.0f));
            Random r = new Random(31);
            for (double t = STOP[2] + 0.2; t < Math.min(T - 0.5, STOP[2] + 2.5); t += 0.07) out.add(snd("entity.experience_orb.pickup", t, 0.3f, 0.8f + r.nextFloat() * 0.9f));
        }
    }

    // =====================================================================================
    static final class Synthwave extends Theme {
        private static final double BEAT = 0.6;

        Synthwave() {
            super("synthwave", "Synthwave", "Retro 80s: a striped neon sun, glowing mountains and a moving laser grid.");
        }

        @Override
        public void start(Ctx c) {
            Random r = c.bag.r;
            float[] m = new float[34];
            for (int i = 0; i < m.length; i++) m[i] = 0.15f + r.nextFloat() * (i % 4 == 0 ? 0.85f : 0.5f);
            c.bag.f = m;
        }

        @Override
        public void draw(Ctx c) {
            Bag s = c.bag;
            GuiGraphics g = c.g;
            int hz = (int) (c.h * 0.62f);
            float beat = (float) Math.exp(-((c.t % BEAT) / BEAT) * 5);
            g.fillGradient(0, 0, c.w, hz, A(0.85f * c.vis, 0x12002E), A(0.85f * c.vis, 0xC2186B));
            // stars
            Random r = new Random(9);
            for (int i = 0; i < 70; i++) {
                float x = r.nextFloat() * c.w, y = r.nextFloat() * hz * 0.7f;
                float tw = 0.4f + 0.6f * (float) Math.abs(Math.sin(c.t * 2 + i));
                rect(g, x, y, 1.5f, 1.5f, 0, A(tw * c.vis, 0xFFFFFF));
            }
            // sun with stripes
            float R = (62 + c.L * 6) * (c.h / 360f);
            float sx = c.w / 2f, sy = hz - R * 0.35f;
            glow(g, sx, sy, R * 1.8f, 0xFF4F9A, (0.35f + 0.2f * beat) * c.vis);
            for (float dy = -R; dy < R; dy += 1) {
                float yy = sy + dy;
                if (yy > hz) break;
                float f = (dy + R) / (2 * R);
                if (f > 0.5f) {
                    float band = (float) (((dy + c.t * 14) % 9 + 9) % 9);
                    float gap = 1.5f + (f - 0.5f) * 9;
                    if (band < gap) continue;
                }
                float hw = R * (float) Math.sqrt(Math.max(0, 1 - (dy * dy) / (R * R)));
                box(g, sx - hw, yy, hw * 2, 1.05f, A(c.vis, mix(0xFFE45C, 0xFF2D95, f)));
            }
            // mountains
            float[] m = s.f;
            float step = c.w / (float) (m.length - 1);
            for (int i = 0; i < m.length - 1; i++) {
                float x0 = i * step, x1 = (i + 1) * step;
                float y0 = hz - m[i] * c.h * 0.16f, y1 = hz - m[i + 1] * c.h * 0.16f;
                for (float x = x0; x < x1; x += 2) {
                    float f = (x - x0) / step;
                    float y = y0 + (y1 - y0) * f;
                    box(g, x, y, 2.1f, hz - y, A(c.vis, 0x1A0533));
                }
                line(g, x0, y0, x1, y1, 1.6f, A(c.vis, 0x3EF2FF));
            }
            // floor grid
            box(g, 0, hz, c.w, c.h - hz, A(0.9f * c.vis, 0x0E001F));
            int lines = 16;
            for (int i = 0; i < lines; i++) {
                double z = ((i + c.t * 1.6) % lines) / lines;
                float y = hz + (c.h - hz) * (float) Math.pow(z, 2.3);
                line(g, 0, y, c.w, y, 1 + (float) z * 1.5f, A(c.vis * (0.25f + 0.75f * (float) z), 0xFF2BD6));
            }
            for (int i = -14; i <= 14; i++) {
                float bx = c.w / 2f + i * c.w * 0.11f;
                line(g, c.w / 2f + i * 6, hz, bx, c.h, 1.2f, A(c.vis * 0.8f, 0xFF2BD6));
            }
            box(g, 0, hz - 1, c.w, 2, A(c.vis, 0xFF9CF0));
            // scan lines
            for (int y = 0; y < c.h; y += 3) g.fill(0, y, c.w, y + 1, A(0.07f * c.vis, 0x000000));
            // shooting star
            double ss = (c.t * 0.4) % 1.0;
            if (ss < 0.15) {
                float f = (float) (ss / 0.15);
                line(g, c.w * (0.9f - f * 0.6f), c.h * (0.05f + f * 0.2f), c.w * (0.95f - f * 0.6f), c.h * (0.03f + f * 0.2f), 1.5f, A(c.vis * (1 - f), 0xFFFFFF));
            }
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            int[] bass = {0, 0, 3, 3, 5, 5, 3, 3};
            int[] lead = {12, 15, 19, 22, 19, 15, 17, 14};
            int i = 0;
            for (double t = 0; t < T - 0.5; t += BEAT / 2, i++) {
                out.add(snd("block.note_block.bass", t, 0.8f, note(bass[(i / 2) % bass.length])));
                if (i % 2 == 0) out.add(snd("block.note_block.basedrum", t, 0.8f, 1.0f));
                if (i % 4 == 2) out.add(snd("block.note_block.snare", t, 0.6f, 1.0f));
                out.add(snd("block.note_block.bit", t, 0.3f, note(lead[i % lead.length])));
            }
        }
    }

    // =====================================================================================
    static final class BlackHole extends Theme {
        BlackHole() {
            super("black_hole", "Black Hole", "A black hole with a blazing spinning disk swallows the stars, then fires twin jets.");
        }

        @Override
        public void start(Ctx c) {
            Random r = c.bag.r;
            for (int i = 0; i < 260; i++) {
                Bit b = new Bit();
                b.a = r.nextFloat() * 6.28f;
                b.b = 50 + r.nextFloat() * 120;
                b.c2 = r.nextFloat();
                c.bag.a.add(b);
            }
            for (int i = 0; i < 140; i++) {
                Bit b = new Bit();
                b.a = r.nextFloat() * 6.28f;
                b.b = 80 + r.nextFloat() * Math.max(c.w, c.h) * 0.7f;
                c.bag.b.add(b);
            }
        }

        @Override
        public void draw(Ctx c) {
            Bag s = c.bag;
            GuiGraphics g = c.g;
            float scale = (1 + c.L * 0.08f) * (c.h / 360f);
            float x = c.w / 2f, y = c.h * 0.6f;
            double jets = c.T - 1.3;
            tint(c, 0x000000, (0.35f + 0.3f * clamp(c.t / 2)) * c.vis);
            // stars being pulled in
            for (Bit b : s.b) {
                float prev = b.b;
                b.b -= c.dt * (20 + 6000f / Math.max(30, b.b));
                b.a += c.dt * (40f / Math.max(30, b.b));
                if (b.b < 24 * scale) {
                    b.b = Math.max(c.w, c.h) * (0.5f + s.r.nextFloat() * 0.3f);
                    b.a = s.r.nextFloat() * 6.28f;
                    continue;
                }
                float x1 = x + (float) Math.cos(b.a) * b.b, y1 = y + (float) Math.sin(b.a) * b.b * 0.8f;
                float x0 = x + (float) Math.cos(b.a - 0.05f) * prev, y0 = y + (float) Math.sin(b.a - 0.05f) * prev * 0.8f;
                line(g, x0, y0, x1, y1, 1, A(c.vis * 0.8f, 0xE8EEFF));
            }
            // gravity waves going in
            for (int i = 0; i < 3; i++) {
                double f = 1 - ((c.t * 0.8 + i / 3.0) % 1.0);
                float rr = (float) (30 + f * Math.max(c.w, c.h) * 0.5) * scale;
                ring(g, x, y, rr, rr * 0.8f, 1.2f, A(c.vis * 0.18f * (float) (1 - f), 0x9FB8FF), 60);
            }
            glow(g, x, y, 120 * scale, 0xFF7A2A, 0.3f * c.vis);
            // disk: back half
            drawDisk(c, s, x, y, scale, true);
            // the hole and photon ring
            ring(g, x, y - 2 * scale, 34 * scale, 22 * scale, 2, A(c.vis * 0.9f, 0xFFC27A), 64);
            disc(g, x, y, 26 * scale, A(c.vis, 0x000000));
            ring(g, x, y, 27 * scale, 27 * scale, 1.4f, A(c.vis, 0xFFE2B0), 64);
            // disk: front half
            drawDisk(c, s, x, y, scale, false);
            // jets at the end
            if (c.t > jets) {
                double f = c.t - jets;
                float len = (float) Math.min(1, f / 0.35) * c.h;
                float a = c.vis * (float) Math.min(1, f / 0.1);
                for (int dir = -1; dir <= 1; dir += 2) {
                    line(g, x, y, x + dir * 8, y + dir * -len, 12 * scale, A(a * 0.25f, 0x7FD8FF));
                    line(g, x, y, x + dir * 8, y + dir * -len, 4 * scale, A(a * 0.7f, 0xCFF2FF));
                    line(g, x, y, x + dir * 8, y + dir * -len, 1.6f * scale, A(a, 0xFFFFFF));
                }
                if (c.flashes && f < 0.25) tint(c, 0xE6F6FF, (float) (1 - f / 0.25) * 0.5f);
            }
        }

        private static void drawDisk(Ctx c, Bag s, float x, float y, float scale, boolean back) {
            for (Bit b : s.a) {
                if (back) b.a += c.dt * (2.4f * 80f / b.b) * (1 + c.L * 0.15f);
                double sin = Math.sin(b.a);
                if ((sin < 0) != back) continue;
                float rx = b.b * scale, ry = rx * 0.26f;
                float px = x + (float) Math.cos(b.a) * rx, py = y + (float) sin * ry;
                float f = (b.b - 50) / 120f;
                int col = f < 0.25f ? 0xFFF6E0 : f < 0.6f ? mix(0xFFF6E0, 0xFF9A3C, (f - 0.25f) / 0.35f) : mix(0xFF9A3C, 0xB0301A, (f - 0.6f) / 0.4f);
                float bright = (float) (0.6 + 0.4 * Math.cos(b.a)); // the side coming toward you is brighter
                rect(g(c), px, py, 2.2f, 1.4f, 0, A(c.vis * bright, col));
            }
        }

        private static GuiGraphics g(Ctx c) {
            return c.g;
        }

        @Override
        public void sounds(int L, double T, List<Config.Sound> out) {
            out.add(snd("entity.warden.sonic_charge", 0.0, 0.45f, 0.7f));
            out.add(snd("block.portal.ambient", 0.2, 0.6f, 0.5f));
            out.add(snd("entity.wither.ambient", 0.7, 0.5f, 0.5f));
            if (T > 6) out.add(snd("block.portal.ambient", T * 0.5, 0.5f, 0.6f));
            out.add(snd("block.beacon.deactivate", T - 1.5, 0.9f, 0.5f));
            out.add(snd("entity.warden.sonic_boom", T - 1.3, 0.7f, 0.9f));
            out.add(snd("entity.generic.explode", T - 1.3, 0.6f, 0.6f));
        }
    }
}
