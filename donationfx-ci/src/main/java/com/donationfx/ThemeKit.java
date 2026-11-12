package com.donationfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Shared pieces for the animation themes: what a theme gets each frame, small particles, and drawing helpers. */
public final class ThemeKit {
    private ThemeKit() {}

    /** One animation style. */
    public abstract static class Theme {
        public final String id;
        public final String name;
        public final String desc;

        protected Theme(String id, String name, String desc) {
            this.id = id;
            this.name = name;
            this.desc = desc;
        }

        /** Called once on the first frame. */
        public void start(Ctx c) {}

        /** Called every frame, behind the title. */
        public abstract void draw(Ctx c);

        /** The sounds for this theme (seconds at normal speed). L = level 0-4, T = length in seconds. */
        public abstract void sounds(int L, double T, List<Config.Sound> out);
    }

    /** Everything a theme needs for one frame. */
    public static final class Ctx {
        public GuiGraphics g;
        public Font font;
        public Minecraft mc;
        public int w, h;
        /** Middle of the title. */
        public float cx, cy;
        /** Seconds since the start (normal speed) and the full length. */
        public double t, T;
        /** Seconds since the last frame (real time, already sped up/slowed down). */
        public float dt;
        /** 0..1 fade in / fade out. */
        public float vis;
        /** Level 0-4. */
        public int L;
        /** How much stuff: grows with the level and the amount. */
        public double k;
        /** The level's color (RGB). */
        public int col;
        public boolean rainbow;
        public boolean flashes;
        public double amount;
        public String player;
        public Bag bag;

        public float p() {
            return (float) Math.min(1, t / Math.max(0.1, T));
        }

        /** True while there is still time for new things to appear. */
        public boolean spawning() {
            return t < T - 0.9;
        }

        public int color(float shift) {
            if (rainbow) return Fx.hsv((float) ((t * 0.35 + shift) % 1.0 + 1.0) % 1f, 0.8f, 1f);
            return col;
        }
    }

    /** A theme's memory for one animation. */
    public static final class Bag {
        public final Random r;
        public final List<Bit> a = new ArrayList<>();
        public final List<Bit> b = new ArrayList<>();
        public final List<Bit> c = new ArrayList<>();
        public double acc1, acc2, acc3, acc4;
        public float[] f = new float[0];
        public Object extra;
        public boolean started;
        public double last = -1;

        public Bag(long seed) {
            this.r = new Random(seed);
        }
    }

    /** A small moving thing. */
    public static final class Bit {
        public float x, y, vx, vy;
        public float life, max;
        public float size = 1;
        public float rot, vr;
        public float grav, drag;
        public int col;
        public int kind;
        public float a, b, c2, d;
        public ItemStack item;
        public String glyph;
        public boolean dead;

        public float q() {
            return max <= 0 ? 1 : Math.max(0, life / max);
        }
    }

    public static Bit bit(List<Bit> list, int cap) {
        if (list.size() >= cap) list.remove(0);
        Bit b = new Bit();
        list.add(b);
        return b;
    }

    /** Moves all bits and removes the dead ones. */
    public static void step(List<Bit> list, float dt, int h) {
        Iterator<Bit> it = list.iterator();
        while (it.hasNext()) {
            Bit b = it.next();
            b.life -= dt;
            b.vy += b.grav * dt;
            if (b.drag > 0) {
                float d = Math.max(0, 1 - b.drag * dt);
                b.vx *= d;
                b.vy *= d;
            }
            b.x += b.vx * dt;
            b.y += b.vy * dt;
            b.rot += b.vr * dt;
            if (b.dead || b.life <= 0 || b.y > h + 80 || b.y < -400) it.remove();
        }
    }

    // ------------------------------------------------------------------ drawing

    public static int A(float a, int rgb) {
        return Fx.argb(Math.max(0, Math.min(1, a)), rgb);
    }

    public static float clamp(double v) {
        return (float) Math.max(0, Math.min(1, v));
    }

    public static double easeOut(double x) {
        x = Math.max(0, Math.min(1, x));
        return 1 - Math.pow(1 - x, 3);
    }

    public static double easeOutBack(double x) {
        x = Math.max(0, Math.min(1, x));
        double c1 = 1.70158, c3 = c1 + 1;
        return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
    }

    public static double easeOutBounce(double x) {
        x = Math.max(0, Math.min(1, x));
        double n1 = 7.5625, d1 = 2.75;
        if (x < 1 / d1) return n1 * x * x;
        if (x < 2 / d1) return n1 * (x -= 1.5 / d1) * x + 0.75;
        if (x < 2.5 / d1) return n1 * (x -= 2.25 / d1) * x + 0.9375;
        return n1 * (x -= 2.625 / d1) * x + 0.984375;
    }

    public static int mix(int c1, int c2, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
        int g = (int) (((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
        int b = (int) ((c1 & 255) * (1 - t) + (c2 & 255) * t);
        return (r << 16) | (g << 8) | b;
    }

    /** A rectangle centered on x,y, any size (fractions too), turned by rot radians. */
    public static void rect(GuiGraphics g, float x, float y, float w, float h, float rot, int argb) {
        if (w <= 0.01f || h <= 0.01f || (argb >>> 24) == 0) return;
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate(x, y);
        if (rot != 0) p.rotate(rot);
        p.scale(w / 2f, h / 2f);
        g.fill(-1, -1, 1, 1, argb);
        p.popMatrix();
    }

    /** A rectangle from its top-left corner. */
    public static void box(GuiGraphics g, float x, float y, float w, float h, int argb) {
        rect(g, x + w / 2f, y + h / 2f, w, h, 0, argb);
    }

    public static void line(GuiGraphics g, float x1, float y1, float x2, float y2, float th, int argb) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.1f) return;
        rect(g, (x1 + x2) / 2f, (y1 + y2) / 2f, len + th * 0.5f, th, (float) Math.atan2(dy, dx), argb);
    }

    /** A filled circle (or oval when ry != rx). */
    public static void disc(GuiGraphics g, float cx, float cy, float rx, float ry, int argb) {
        disc(g, cx, cy, rx, ry, argb, 28);
    }

    /** rows = how many strips it is made of (fewer = faster, softer edges are fine for glows). */
    public static void disc(GuiGraphics g, float cx, float cy, float rx, float ry, int argb, int rows) {
        if (rx < 0.5f || ry < 0.5f || (argb >>> 24) == 0) return;
        float step = Math.max(1f, ry * 2 / rows);
        for (float dy = -ry; dy < ry; dy += step) {
            float mid = dy + step / 2f;
            float hw = rx * (float) Math.sqrt(Math.max(0, 1 - (mid * mid) / (ry * ry)));
            if (hw > 0.2f) rect(g, cx, cy + mid, hw * 2, step + 0.05f, 0, argb);
        }
    }

    public static void disc(GuiGraphics g, float cx, float cy, float r, int argb) {
        disc(g, cx, cy, r, r, argb);
    }

    /** A soft glow: several see-through circles. */
    public static void glow(GuiGraphics g, float cx, float cy, float r, int rgb, float a) {
        if (a <= 0.005f || r < 1) return;
        for (int i = 4; i >= 0; i--) {
            float rr = r * (0.35f + i * 0.2f);
            disc(g, cx, cy, rr, rr, A(a * 0.22f, rgb), 14);
        }
    }

    /** A ring (or oval ring). */
    public static void ring(GuiGraphics g, float cx, float cy, float rx, float ry, float th, int argb, int segs) {
        if (rx < 1 || (argb >>> 24) == 0) return;
        double prevX = cx + rx, prevY = cy;
        for (int i = 1; i <= segs; i++) {
            double a = i * Math.PI * 2 / segs;
            double x = cx + Math.cos(a) * rx, y = cy + Math.sin(a) * ry;
            line(g, (float) prevX, (float) prevY, (float) x, (float) y, th, argb);
            prevX = x;
            prevY = y;
        }
    }

    /** Part of an oval ring, from angle a0 to a1 (radians). */
    public static void arc(GuiGraphics g, float cx, float cy, float rx, float ry, float th, int argb, double a0, double a1, int segs) {
        if (rx < 1 || (argb >>> 24) == 0) return;
        double px = cx + Math.cos(a0) * rx, py = cy + Math.sin(a0) * ry;
        for (int i = 1; i <= segs; i++) {
            double a = a0 + (a1 - a0) * i / segs;
            double x = cx + Math.cos(a) * rx, y = cy + Math.sin(a) * ry;
            line(g, (float) px, (float) py, (float) x, (float) y, th, argb);
            px = x;
            py = y;
        }
    }

    /** Text centered on x,y. */
    public static void text(GuiGraphics g, Font f, String s, float x, float y, float scale, int argb, float rot, boolean shadow) {
        if ((argb >>> 24) < 6 || scale <= 0.01f) return;
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate(x, y);
        if (rot != 0) p.rotate(rot);
        p.scale(scale);
        g.drawString(f, s, -f.width(s) / 2, -4, argb, shadow);
        p.popMatrix();
    }

    /** An item centered on x,y (16 px times scale). */
    public static void item(GuiGraphics g, ItemStack st, float x, float y, float scale) {
        if (st == null || st.isEmpty() || scale <= 0.02f) return;
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate(x, y);
        p.scale(scale);
        g.renderItem(st, -8, -8);
        p.popMatrix();
    }

    public static ItemStack stack(String id) {
        Item it = Fx.item(id.contains(":") ? id : "minecraft:" + id);
        return it == null ? ItemStack.EMPTY : new ItemStack(it);
    }

    public static Config.Sound snd(String id, double at, float vol, float pitch) {
        return new Config.Sound("minecraft:" + id, at, vol, pitch);
    }

    /** Musical note pitch: 0 = F#, 12 = F# one octave up (like note blocks). */
    public static float note(int n) {
        return (float) Math.pow(2, (n - 12) / 12.0);
    }

    /** Screen-wide see-through color. */
    public static void tint(Ctx c, int rgb, float a) {
        if (a > 0.004f) c.g.fill(0, 0, c.w, c.h, A(a, rgb));
    }

    /** Explosion of small sparks. */
    public static void sparks(List<Bit> list, float x, float y, int n, float speed, int col, boolean rainbow, Random r) {
        for (int i = 0; i < n; i++) {
            Bit b = bit(list, 1400);
            double a = r.nextDouble() * Math.PI * 2;
            double sp = speed * (0.3 + r.nextDouble() * 0.7);
            b.x = x;
            b.y = y;
            b.vx = (float) (Math.cos(a) * sp);
            b.vy = (float) (Math.sin(a) * sp);
            b.max = b.life = 0.6f + r.nextFloat() * 0.8f;
            b.size = 1 + r.nextFloat() * 1.5f;
            b.grav = 90;
            b.drag = 2.2f;
            b.col = rainbow ? Fx.hsv(r.nextFloat(), 0.85f, 1f) : col;
        }
    }

    /** Draws sparks made by {@link #sparks}. */
    public static void drawSparks(Ctx c, List<Bit> list) {
        for (Bit b : list) {
            float a = b.q() * c.vis;
            float s = b.size;
            rect(c.g, b.x, b.y, s * 2, s * 2, 0, A(a, b.col));
            if (Math.abs(b.vx) + Math.abs(b.vy) > 40) line(c.g, b.x, b.y, b.x - b.vx * 0.03f, b.y - b.vy * 0.03f, s, A(a * 0.5f, b.col));
        }
    }
}
