package com.donationfx;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** The animations: screen effects (drawn on the HUD), sounds, and particles/fireworks in the world. */
public final class Fx {
    private static final Random R = new Random();
    private static final int MAX_PARTS = 900;

    // Big moments for each level, in seconds. Flashes, shakes and shockwaves happen here (they match the default sounds).
    private static final double[][] KICKS = {
            {},
            {},
            {0.0, 0.5},
            {0.0, 0.5, 1.0, 1.4, 2.6},
            {0.0, 0.4, 1.2, 1.6, 1.9, 2.4, 3.0, 4.8, 5.4},
    };
    private static final double[][] RINGS = {
            {},
            {0.0},
            {0.0, 0.5},
            {0.0, 0.5, 1.0, 1.4},
            {0.0, 0.4, 1.2, 1.6, 1.9, 2.4, 3.0, 4.8, 5.4},
    };
    private static final double[] SHAKE = {0, 0.6, 2.0, 4.0, 7.5};
    private static final String[] POPUP = {"", "", "minecraft:totem_of_undying", "minecraft:diamond_block", "minecraft:nether_star"};

    static final class Show {
        int level;
        Config.Tier tier;
        double amount;
        String player;
        boolean test;
        long arrived = System.currentTimeMillis();
        long start = -1;
        double dur;
        double intensity;
        int nextSound;
        List<Config.Sound> sounds;
        final Set<String> once = new HashSet<>();
        double rainAcc, confAcc, glyphAcc, starAcc, burstAcc;
    }

    private static final class P {
        static final int CONFETTI = 0, SPARK = 1, ITEM = 2, GLYPH = 3;
        int type;
        float x, y, vx, vy, rot, vr, life, max, size, gravity, drag;
        int color;
        ItemStack item;
        String glyph;
    }

    private static final Deque<Show> QUEUE = new ArrayDeque<>();
    private static Show cur;
    private static final List<P> PARTS = new ArrayList<>();
    private static long lastFrame = 0;

    public static double sessionTotal = 0;
    public static int sessionCount = 0;
    public static final Map<String, Double> DONORS = new HashMap<>();

    private Fx() {}

    // ------------------------------------------------------------------ public

    public static boolean playing() {
        return cur != null;
    }

    public static int queued() {
        return QUEUE.size();
    }

    public static void stop() {
        QUEUE.clear();
        cur = null;
        PARTS.clear();
    }

    /** Called when money arrives. level = 0..4. */
    public static void donate(double amount, String player, int level, boolean test) {
        Config c = Config.get();
        if (level < 0 || level >= c.tiers.size()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!test) {
            sessionTotal += amount;
            sessionCount++;
            DONORS.merge(player, amount, Double::sum);
        }
        if (c.chatMessage && mc.player != null) {
            Config.Tier t = c.tiers.get(level);
            int col = parseColor(t.color, 0xFFFFFF);
            Component msg = Component.literal("✦ ").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(col)))
                    .append(Component.literal(player).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF)).withBold(true)))
                    .append(Component.literal(" donated ").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA))))
                    .append(Component.literal(Money.full(amount)).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFD700)).withBold(true)))
                    .append(Component.literal("  " + t.title + (test ? " (test)" : "")).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(col))));
            mc.gui.getChat().addMessage(msg);
        }

        long now = System.currentTimeMillis();
        if (c.combine && !test) {
            for (Show s : QUEUE) {
                if (s.player.equalsIgnoreCase(player) && !player.equals(Detector.UNKNOWN) && now - s.arrived <= c.combineSeconds * 1000) {
                    s.amount += amount;
                    int nl = c.tierFor(s.amount);
                    if (nl > s.level) {
                        s.level = nl;
                        s.tier = c.tiers.get(nl);
                    }
                    return;
                }
            }
        }
        Show s = new Show();
        s.level = level;
        s.tier = c.tiers.get(level);
        s.amount = amount;
        s.player = player;
        s.test = test;
        if (!c.queue) {
            QUEUE.clear();
            cur = null;
        } else if (QUEUE.size() >= c.maxQueue) {
            Show smallest = QUEUE.stream().min(Comparator.comparingDouble(q -> q.amount)).orElse(null);
            if (smallest != null && smallest.amount < amount) QUEUE.remove(smallest);
            else return;
        }
        QUEUE.add(s);
    }

    // ------------------------------------------------------------------ tick (sounds, world effects)

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            if (cur != null || !QUEUE.isEmpty()) stop();
            return;
        }
        if (cur == null && !QUEUE.isEmpty()) begin(mc, QUEUE.poll());
        if (cur == null) return;
        Config c = Config.get();
        double el = elapsed(cur);
        while (cur.nextSound < cur.sounds.size() && cur.sounds.get(cur.nextSound).at * 1000 * c.speed <= el) {
            if (c.sounds) play(mc, cur.sounds.get(cur.nextSound));
            cur.nextSound++;
        }
        if (c.worldEffects) world(mc, cur, el / 1000.0 / c.speed);
        if (el >= cur.dur) cur = null;
    }

    private static double elapsed(Show s) {
        return s.start < 0 ? 0 : System.currentTimeMillis() - s.start;
    }

    private static void begin(Minecraft mc, Show s) {
        Config c = Config.get();
        s.start = System.currentTimeMillis();
        s.dur = s.tier.seconds * 1000 * c.speed;
        // more money inside a level = a bit more stuff (up to +60%)
        double next = s.level + 1 < c.tiers.size() ? c.tiers.get(s.level + 1).minAmount : s.tier.minAmount * 10;
        double inside = s.tier.minAmount > 0 && next > s.tier.minAmount
                ? Math.log(Math.max(1, s.amount / s.tier.minAmount)) / Math.log(next / s.tier.minAmount) : 0;
        s.intensity = s.tier.intensity * (1 + 0.6 * Math.max(0, Math.min(1, inside)));
        s.sounds = new ArrayList<>(s.tier.sounds);
        s.sounds.removeIf(x -> x == null || x.id == null);
        s.sounds.sort(Comparator.comparingDouble(x -> x.at));
        cur = s;

        // opening burst on screen
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int n = (int) Math.min(420, (40 + 45 * s.level) * s.intensity);
        for (int i = 0; i < n; i++) confetti(w / 2f, h * 0.35f, 140 + 60 * s.level, 260 + 40 * s.level, s);
        if (s.level >= 2) {
            int side = (int) Math.min(200, 30 * s.level * s.intensity);
            for (int i = 0; i < side; i++) {
                cannon(0, h, 1, s);
                cannon(w, h, -1, s);
            }
        }
        if (c.itemPopup && s.level >= 2) {
            Item it = item(POPUP[Math.min(s.level, POPUP.length - 1)]);
            if (it != null) mc.gameRenderer.displayItemActivation(new ItemStack(it));
        }
    }

    private static void play(Minecraft mc, Config.Sound snd) {
        Identifier id = Identifier.tryParse(snd.id);
        if (id == null) return;
        SoundEvent ev = BuiltInRegistries.SOUND_EVENT.getValue(id);
        if (ev == null) ev = SoundEvent.createVariableRangeEvent(id); // sounds from resource packs
        float vol = Math.max(0f, Math.min(1f, snd.volume)) * Config.get().volume;
        if (vol <= 0.001f) return;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(ev, Math.max(0.5f, Math.min(2f, snd.pitch)), vol));
    }

    /** One-time event at a moment of the animation (seconds in normal speed). */
    private static boolean at(Show s, String key, double sec, double now) {
        if (now < sec) return false;
        return s.once.add(key + "@" + sec);
    }

    private static void world(Minecraft mc, Show s, double t) {
        var lvl = mc.level;
        var p = mc.player;
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 ahead = eye.add(look.x * 5, 0, look.z * 5);
        int L = s.level;
        double end = s.tier.seconds;
        int[] cols = fireColors(s);

        // hearts and sparkles for every level (it is lifesteal after all)
        if (at(s, "hearts", 0, t)) {
            for (int i = 0; i < 6 + 6 * L; i++) {
                lvl.addParticle(ParticleTypes.HEART, eye.x + R.nextGaussian() * 1.2, eye.y + R.nextDouble() * 1.5 - 0.5,
                        eye.z + R.nextGaussian() * 1.2, 0, 0.05, 0);
            }
            for (int i = 0; i < 25 + 10 * L; i++) {
                double a = R.nextDouble() * Math.PI * 2;
                lvl.addParticle(ParticleTypes.HAPPY_VILLAGER, eye.x + Math.cos(a) * 1.5, eye.y - 1 + R.nextDouble() * 2,
                        eye.z + Math.sin(a) * 1.5, 0, 0.1, 0);
            }
        }
        if (L >= 1) {
            if (at(s, "fw", 0.3, t)) firework(lvl, ahead.add(0, 3, 0), FireworkExplosion.Shape.SMALL_BALL, cols, false, true);
            if (at(s, "fw", 1.1, t)) firework(lvl, ahead.add(R.nextGaussian(), 4, R.nextGaussian()), FireworkExplosion.Shape.LARGE_BALL, cols, true, true);
        }
        if (L >= 2) {
            if (at(s, "totem", 0, t)) mc.particleEngine.createTrackingEmitter(p, ParticleTypes.TOTEM_OF_UNDYING, 30);
            if (at(s, "fw", 0.5, t)) firework(lvl, ahead.add(-2, 4, 0), FireworkExplosion.Shape.LARGE_BALL, cols, true, false);
            if (at(s, "fw", 2.2, t)) firework(lvl, ahead.add(2, 5, 0), FireworkExplosion.Shape.LARGE_BALL, cols, true, true);
            if (t < 2.5) spiral(lvl, eye, t, ParticleTypes.END_ROD, 1.2, 1);
        }
        if (L >= 3) {
            if (at(s, "fw", 1.0, t)) firework(lvl, ahead.add(0, 6, 0), FireworkExplosion.Shape.STAR, cols, true, true);
            if (at(s, "fw", 1.4, t)) firework(lvl, ahead.add(R.nextGaussian() * 2, 5, R.nextGaussian() * 2), FireworkExplosion.Shape.CREEPER, new int[]{0x55FF55, 0x00AA00}, false, true);
            if (at(s, "ring", 2.6, t)) {
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3;
                    firework(lvl, eye.add(Math.cos(a) * 6, 3, Math.sin(a) * 6), FireworkExplosion.Shape.BURST, cols, true, false);
                }
            }
            if (t < 3.5) spiral(lvl, eye, t, ParticleTypes.FLAME, 1.6, 2);
            if (R.nextInt(3) == 0) lvl.addParticle(ParticleTypes.HEART, eye.x + R.nextGaussian() * 2, eye.y + 1, eye.z + R.nextGaussian() * 2, 0, 0.1, 0);
        }
        if (L >= 4) {
            if (at(s, "totem2", 2.4, t)) {
                mc.particleEngine.createTrackingEmitter(p, ParticleTypes.TOTEM_OF_UNDYING, 30);
                if (Config.get().itemPopup) mc.gameRenderer.displayItemActivation(new ItemStack(Items.TOTEM_OF_UNDYING));
            }
            if (at(s, "totem3", 4.8, t)) mc.particleEngine.createTrackingEmitter(p, ParticleTypes.TOTEM_OF_UNDYING, 40);
            // firework barrage
            if (t > 1.6 && t < end - 2 && s.once.add("barrage" + (int) (t * 2.5))) {
                FireworkExplosion.Shape[] shapes = FireworkExplosion.Shape.values();
                double a = R.nextDouble() * Math.PI * 2;
                double r = 4 + R.nextDouble() * 5;
                firework(lvl, eye.add(Math.cos(a) * r, 3 + R.nextDouble() * 5, Math.sin(a) * r), shapes[R.nextInt(shapes.length)],
                        new int[]{hsv(R.nextFloat(), 1, 1), hsv(R.nextFloat(), 1, 1), hsv(R.nextFloat(), 0.6f, 1)}, R.nextBoolean(), true);
            }
            spiral(lvl, eye, t, ParticleTypes.END_ROD, 2.2, 1);
            spiral(lvl, eye, t + 0.5, ParticleTypes.SOUL_FIRE_FLAME, 2.2, 1);
            if (R.nextInt(2) == 0) lvl.addParticle(ParticleTypes.HEART, eye.x + R.nextGaussian() * 3, eye.y + R.nextDouble() * 2, eye.z + R.nextGaussian() * 3, 0, 0.1, 0);
        }
    }

    private static void spiral(net.minecraft.client.multiplayer.ClientLevel lvl, Vec3 c, double t, ParticleOptions type, double radius, int arms) {
        for (int k = 0; k < arms; k++) {
            double a = t * 6 + k * Math.PI;
            double y = (t * 1.5) % 3.0 - 1.4;
            lvl.addParticle(type, c.x + Math.cos(a) * radius, c.y + y, c.z + Math.sin(a) * radius, 0, 0.02, 0);
            lvl.addParticle(type, c.x + Math.cos(a + Math.PI) * radius, c.y + y, c.z + Math.sin(a + Math.PI) * radius, 0, 0.02, 0);
        }
    }

    private static void firework(net.minecraft.client.multiplayer.ClientLevel lvl, Vec3 pos, FireworkExplosion.Shape shape, int[] colors, boolean trail, boolean twinkle) {
        IntList c = new IntArrayList(colors);
        IntList fade = new IntArrayList(new int[]{0xFFFFFF});
        lvl.createFireworks(pos.x, pos.y, pos.z, 0, 0, 0, List.of(new FireworkExplosion(shape, c, fade, trail, twinkle)));
    }

    private static int[] fireColors(Show s) {
        if ("rainbow".equalsIgnoreCase(s.tier.color)) {
            return new int[]{0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55, 0x55FFFF, 0x5555FF, 0xFF55FF};
        }
        int c = parseColor(s.tier.color, 0xFFFFFF);
        return new int[]{c, 0xFFFFFF, 0xFFD700};
    }

    // ------------------------------------------------------------------ screen particles

    private static P add(int type) {
        if (PARTS.size() >= MAX_PARTS) PARTS.remove(0);
        P p = new P();
        p.type = type;
        PARTS.add(p);
        return p;
    }

    private static int partColor(Show s) {
        if ("rainbow".equalsIgnoreCase(s.tier.color) || R.nextInt(4) == 0) return hsv(R.nextFloat(), 0.85f, 1f);
        int c = parseColor(s.tier.color, 0xFFFFFF);
        return R.nextInt(3) == 0 ? 0xFFD700 : c;
    }

    private static void confetti(float x, float y, float spread, float up, Show s) {
        P p = add(P.CONFETTI);
        p.x = x + (float) R.nextGaussian() * 10;
        p.y = y + (float) R.nextGaussian() * 6;
        double a = R.nextDouble() * Math.PI * 2;
        double sp = R.nextDouble() * spread;
        p.vx = (float) (Math.cos(a) * sp);
        p.vy = (float) (Math.sin(a) * sp * 0.6 - R.nextDouble() * up);
        p.rot = R.nextFloat() * 6.28f;
        p.vr = (R.nextFloat() - 0.5f) * 14;
        p.max = p.life = 2.2f + R.nextFloat() * 1.8f;
        p.size = 1.5f + R.nextFloat() * 2f;
        p.gravity = 230;
        p.drag = 1.2f;
        p.color = partColor(s);
    }

    private static void cannon(float x, float y, int dir, Show s) {
        P p = add(P.CONFETTI);
        p.x = x;
        p.y = y;
        p.vx = dir * (120 + R.nextFloat() * 260);
        p.vy = -(250 + R.nextFloat() * 300);
        p.rot = R.nextFloat() * 6.28f;
        p.vr = (R.nextFloat() - 0.5f) * 16;
        p.max = p.life = 2.5f + R.nextFloat() * 1.5f;
        p.size = 1.5f + R.nextFloat() * 2f;
        p.gravity = 260;
        p.drag = 1.0f;
        p.color = partColor(s);
    }

    private static void burst(float x, float y, Show s) {
        int hueBase = R.nextInt(360);
        int n = (int) (36 * Math.min(2, s.intensity));
        for (int i = 0; i < n; i++) {
            P p = add(P.SPARK);
            double a = i * Math.PI * 2 / n + R.nextGaussian() * 0.05;
            double sp = 70 + R.nextDouble() * 90;
            p.x = x;
            p.y = y;
            p.vx = (float) (Math.cos(a) * sp);
            p.vy = (float) (Math.sin(a) * sp);
            p.max = p.life = 1.0f + R.nextFloat() * 0.6f;
            p.size = 1.2f + R.nextFloat();
            p.gravity = 70;
            p.drag = 1.6f;
            p.color = "rainbow".equalsIgnoreCase(s.tier.color) ? hsv(((hueBase + i * 5) % 360) / 360f, 0.9f, 1f) : partColor(s);
        }
    }

    private static void spawnContinuous(Show s, float dt, int w, int h, double p) {
        if (p > 0.85) return;
        int L = s.level;
        double k = s.intensity;
        s.confAcc += dt * (8 + 10 * L) * k;
        while (s.confAcc >= 1) {
            s.confAcc--;
            P c = add(P.CONFETTI);
            c.x = R.nextFloat() * w;
            c.y = -8;
            c.vx = (R.nextFloat() - 0.5f) * 30;
            c.vy = 30 + R.nextFloat() * 50;
            c.rot = R.nextFloat() * 6.28f;
            c.vr = (R.nextFloat() - 0.5f) * 10;
            c.max = c.life = 6;
            c.size = 1.4f + R.nextFloat() * 1.8f;
            c.gravity = 25;
            c.drag = 0.3f;
            c.color = partColor(s);
        }
        if (L >= 1 && !s.tier.rainItems.isEmpty()) {
            s.rainAcc += dt * (3 + 7 * L) * k;
            while (s.rainAcc >= 1) {
                s.rainAcc--;
                Item it = item(s.tier.rainItems.get(R.nextInt(s.tier.rainItems.size())));
                if (it == null) continue;
                P c = add(P.ITEM);
                c.item = new ItemStack(it);
                c.x = R.nextFloat() * w;
                c.y = -16;
                c.vx = (R.nextFloat() - 0.5f) * 20;
                c.vy = 50 + R.nextFloat() * 70 + 15 * L;
                c.max = c.life = 7;
                c.size = 0.7f + R.nextFloat() * (0.6f + 0.2f * L);
                c.gravity = 40;
                c.drag = 0.1f;
            }
        }
        // rising $ and hearts
        s.glyphAcc += dt * (2 + 3 * L) * k;
        while (s.glyphAcc >= 1) {
            s.glyphAcc--;
            P c = add(P.GLYPH);
            boolean heart = R.nextInt(3) == 0;
            c.glyph = heart ? "❤" : "$";
            c.color = heart ? 0xFF4444 : (R.nextBoolean() ? 0x55FF55 : 0xFFD700);
            c.x = R.nextFloat() * w;
            c.y = h + 10;
            c.vx = (R.nextFloat() - 0.5f) * 15;
            c.vy = -(40 + R.nextFloat() * 60);
            c.max = c.life = 4;
            c.size = 1f + R.nextFloat() * (0.5f + 0.3f * L);
            c.gravity = 0;
            c.drag = 0;
        }
        if (L >= 3) {
            s.starAcc += dt * (5 + 6 * (L - 3)) * k;
            while (s.starAcc >= 1) {
                s.starAcc--;
                P c = add(P.GLYPH);
                c.glyph = R.nextBoolean() ? "✦" : "★";
                c.color = L >= 4 ? hsv(R.nextFloat(), 0.6f, 1) : 0xFFFFAA;
                c.x = R.nextFloat() * w;
                c.y = R.nextFloat() * h;
                c.max = c.life = 0.7f + R.nextFloat() * 0.6f;
                c.size = 1f + R.nextFloat() * 1.5f;
            }
            s.burstAcc += dt * (L >= 4 ? 2.2 : 0.6) * Math.min(2, k);
            while (s.burstAcc >= 1) {
                s.burstAcc--;
                burst(w * (0.1f + R.nextFloat() * 0.8f), h * (0.08f + R.nextFloat() * 0.45f), s);
            }
        }
    }

    private static void updateAndDraw(GuiGraphics g, float dt, int h) {
        Iterator<P> it = PARTS.iterator();
        while (it.hasNext()) {
            P p = it.next();
            p.life -= dt;
            p.vy += p.gravity * dt;
            float d = Math.max(0, 1 - p.drag * dt);
            p.vx *= d;
            p.vy *= p.type == P.CONFETTI ? Math.max(0, 1 - p.drag * 0.5f * dt) : d;
            if (p.type == P.CONFETTI && p.vy > 70) p.vy = 70; // confetti floats down
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.rot += p.vr * dt;
            if (p.life <= 0 || p.y > h + 30 || p.y < -60) {
                it.remove();
                continue;
            }
            float a = Math.min(1f, p.life / Math.min(0.6f, p.max));
            switch (p.type) {
                case P.CONFETTI -> {
                    g.pose().pushMatrix();
                    g.pose().translate(p.x, p.y);
                    g.pose().rotate(p.rot);
                    float flip = Math.abs((float) Math.cos(p.rot * 1.7f));
                    float hw = p.size * (0.35f + 0.65f * flip);
                    g.fill(Math.round(-hw), Math.round(-p.size / 2), Math.max(1, Math.round(hw)), Math.max(1, Math.round(p.size / 2)), argb(a, p.color));
                    g.pose().popMatrix();
                }
                case P.SPARK -> {
                    int s = Math.max(1, Math.round(p.size));
                    g.fill(Math.round(p.x) - s, Math.round(p.y) - s, Math.round(p.x) + s, Math.round(p.y) + s, argb(a, p.color));
                }
                case P.ITEM -> {
                    g.pose().pushMatrix();
                    g.pose().translate(p.x, p.y);
                    g.pose().scale(p.size);
                    g.renderItem(p.item, -8, -8);
                    g.pose().popMatrix();
                }
                case P.GLYPH -> {
                    Font f = Minecraft.getInstance().font;
                    float tw = p.max < 2 ? (float) Math.sin(Math.PI * (1 - p.life / p.max)) : a;
                    g.pose().pushMatrix();
                    g.pose().translate(p.x, p.y);
                    g.pose().scale(p.size);
                    g.drawString(f, p.glyph, -f.width(p.glyph) / 2, -4, argb(Math.max(0.05f, tw), p.color), true);
                    g.pose().popMatrix();
                }
                default -> { }
            }
        }
    }

    // ------------------------------------------------------------------ HUD render

    public static void render(GuiGraphics g, DeltaTracker delta) {
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0 : Math.max(0, Math.min(0.1f, (now - lastFrame) / 1000f));
        lastFrame = now;
        if (cur == null && PARTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Config c = Config.get();
        int w = g.guiWidth();
        int h = g.guiHeight();
        Show s = cur;

        double el = s == null ? 0 : elapsed(s);
        double p = s == null ? 1 : Math.min(1, el / s.dur);
        double tNorm = el / 1000.0 / c.speed; // seconds at normal speed, matches KICKS/RINGS
        float fadeIn = s == null ? 0 : (float) Math.min(1, el / 350.0);
        float fadeOut = s == null ? 0 : (float) Math.min(1, (s.dur - el) / Math.min(900, s.dur * 0.2));
        float vis = Math.max(0, Math.min(fadeIn, fadeOut));

        // screen shake
        float sx = 0, sy = 0;
        if (s != null && c.shake && SHAKE[s.level] > 0) {
            double amp = 0;
            for (double k : KICKS[s.level]) if (tNorm >= k) amp += SHAKE[s.level] * Math.exp(-(tNorm - k) * 3.2);
            if (s.level >= 4) amp += 0.8;
            amp = Math.min(12, amp * Math.sqrt(s.intensity)) * vis;
            sx = (float) (R.nextGaussian() * amp);
            sy = (float) (R.nextGaussian() * amp);
        }

        float cx = w / 2f;
        float cy = h * (c.position == 0 ? 0.36f : 0.2f);

        g.pose().pushMatrix();
        g.pose().translate(sx, sy);

        if (s != null) {
            int L = s.level;
            int col = color(s, 0);
            if (L >= 4) rainbowSweep(g, w, h, el, vis);
            if (L >= 1) vignette(g, w, h, col, vis * (0.35f + 0.15f * (float) Math.sin(el / 160.0)) * Math.min(1, 0.4f + 0.2f * L));
            if (L >= 2) rays(g, cx, cy, Math.max(w, h), s, el, vis);
            for (double r : RINGS[L]) {
                double since = tNorm - r;
                if (since >= 0 && since < 1.2) ring(g, cx, cy, (float) (since * (w * 0.75)), argb((float) (1 - since / 1.2) * 0.8f, color(s, (int) (r * 100))), L >= 3 ? 3 : 2);
            }
            spawnContinuous(s, dt, w, h, p);
        }

        updateAndDraw(g, dt, h);

        if (s != null) title(g, mc, s, cx, cy, el, vis, w);

        g.pose().popMatrix();

        // flashes (not shaken)
        if (s != null && c.flashes && s.level >= 3) {
            float fa = 0;
            for (double k : KICKS[s.level]) {
                double since = tNorm - k;
                if (since >= 0 && since < 0.35) fa = Math.max(fa, (float) (1 - since / 0.35) * (k == 0 ? 0.75f : 0.35f));
            }
            if (fa > 0.01f) g.fill(0, 0, w, h, argb(fa, s.level >= 4 && tNorm > 0.1 ? color(s, 50) : 0xFFFFFF));
        }
    }

    private static void title(GuiGraphics g, Minecraft mc, Show s, float cx, float cy, double el, float vis, int w) {
        Config c = Config.get();
        Font f = mc.font;
        int L = s.level;
        String title = s.tier.title;
        if (L >= 4) {
            int shown = (int) Math.min(title.length(), el / 55.0);
            title = title.substring(0, shown);
        }
        double inP = Math.min(1, el / 450.0);
        double scale = (1.7 + 0.32 * L) * c.size * easeOutBack(inP);
        double pulse = 1 + 0.035 * (L + 1) * Math.sin(el / (L >= 3 ? 90.0 : 130.0));
        scale *= pulse;
        int fullW = f.width(s.tier.title);
        double maxScale = (w * 0.92) / Math.max(1, fullW);
        scale = Math.min(scale, maxScale * pulse);
        if (fadeOutGrow(s, el) > 0) scale *= 1 + 0.25 * fadeOutGrow(s, el);

        // dark panel behind the text
        float sub = (float) (1.15 * c.size);
        int panelW = (int) Math.min(w - 8, Math.max(fullW * Math.min(scale, maxScale), 200 * c.size) + 30);
        int panelH = (int) (12 * scale + 34 * sub + (L >= 2 ? 10 * sub : 0) + 16);
        int px = (int) (cx - panelW / 2f);
        int py = (int) (cy - 6 * scale - 10);
        int col = color(s, 0);
        g.fill(px, py, px + panelW, py + panelH, argb(0.55f * vis, 0x05060A));
        float glow = (float) (0.55 + 0.45 * Math.sin(el / 140.0));
        int bc = argb(vis * glow, col);
        g.fill(px, py, px + panelW, py + 2, bc);
        g.fill(px, py + panelH - 2, px + panelW, py + panelH, bc);
        g.fill(px, py, px + 2, py + panelH, bc);
        g.fill(px + panelW - 2, py, px + panelW, py + panelH, bc);

        // title
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale((float) scale);
        if (L >= 4 && Math.sin(el / 400.0) > 0.7) g.pose().rotate((float) (Math.sin(el / 60.0) * 0.03));
        int tw = f.width(title);
        if (L >= 3) {
            int x = -fullW / 2;
            for (int i = 0; i < title.length(); i++) {
                String ch = String.valueOf(title.charAt(i));
                float wave = (float) Math.sin(el / 140.0 + i * 0.55) * (L >= 4 ? 2.2f : 1.4f);
                int cc = L >= 4 ? hsv((float) ((i * 0.06 + el / 1500.0) % 1.0), 0.75f, 1f) : (i % 2 == 0 ? col : lighten(col));
                g.drawString(f, ch, x, Math.round(-4 + wave), argb(vis, cc), true);
                x += f.width(ch);
            }
        } else {
            g.drawString(f, title, -tw / 2, -4, argb(vis, col), true);
        }
        g.pose().popMatrix();

        // subtitle: who and how much
        double subIn = Math.max(0, Math.min(1, (el - 200) / 350.0));
        float subY = (float) (cy + 7 * scale + 6 + (1 - easeOut(subIn)) * 18);
        double shownAmount = s.amount;
        if (c.countUp) {
            double cp = Math.max(0, Math.min(1, (el - 200) / Math.max(400, s.dur * 0.35)));
            shownAmount = s.amount * easeOut(cp);
        }
        String text = s.tier.subtitle.replace("{player}", s.player).replace("{amount}", Money.format(shownAmount));
        float subA = vis * (float) subIn;
        g.pose().pushMatrix();
        g.pose().translate(cx, subY);
        g.pose().scale(sub);
        int stw = f.width(text);
        int headSize = 12;
        boolean head = c.showPlayerHead && drawHeadPossible(mc, s.player);
        int total = stw + (head ? headSize + 4 : 0);
        int x0 = -total / 2;
        if (head) {
            PlayerInfo pi = mc.getConnection().getPlayerInfo(s.player);
            if (pi != null && subA > 0.3f) PlayerFaceRenderer.draw(g, pi.getSkin(), x0, -3, headSize);
            x0 += headSize + 4;
        }
        g.drawString(f, text, x0, 0, argb(subA, 0xFFFFFF), true);
        g.pose().popMatrix();

        // level stars
        if (L >= 0) {
            StringBuilder stars = new StringBuilder();
            for (int i = 0; i < 5; i++) stars.append(i <= L ? '★' : '☆');
            String lv = stars + "  " + s.tier.name.toUpperCase(Locale.ROOT);
            g.pose().pushMatrix();
            g.pose().translate(cx, subY + 13 * sub);
            g.pose().scale(sub * 0.9f);
            g.drawString(f, lv, -f.width(lv) / 2, 0, argb(subA, L >= 4 ? hsv((float) ((el / 2000.0) % 1), 0.5f, 1) : 0xFFD700), true);
            g.pose().popMatrix();
        }
        if (queued() > 0) {
            String q = "+" + queued() + " more";
            g.drawString(f, q, (int) (cx - f.width(q) / 2f), py + panelH + 4, argb(vis * 0.8f, 0xAAAAAA), true);
        }
    }

    private static boolean drawHeadPossible(Minecraft mc, String name) {
        return mc.getConnection() != null && name != null && !name.equals(Detector.UNKNOWN) && mc.getConnection().getPlayerInfo(name) != null;
    }

    private static double fadeOutGrow(Show s, double el) {
        double left = s.dur - el;
        double window = Math.min(900, s.dur * 0.2);
        return left < window ? 1 - left / window : 0;
    }

    private static void rays(GuiGraphics g, float cx, float cy, int len, Show s, double el, float vis) {
        int n = 10 + s.level * 4;
        float rot = (float) (el / 1000.0 * (0.35 + 0.15 * s.level));
        for (int i = 0; i < n; i++) {
            int c = s.level >= 4 ? hsv((float) ((i / (double) n + el / 4000.0) % 1), 0.7f, 1) : (i % 2 == 0 ? color(s, 0) : 0xFFD700);
            float a = vis * (s.level >= 4 ? 0.20f : 0.14f);
            g.pose().pushMatrix();
            g.pose().translate(cx, cy);
            g.pose().rotate(rot + (float) (i * Math.PI * 2 / n));
            for (int k = 0; k < 4; k++) {
                int from = len * k / 4;
                int to = len * (k + 1) / 4;
                int hw = 2 + k * 5;
                g.fill(from, -hw, to, hw, argb(a * (1 - k * 0.18f), c));
            }
            g.pose().popMatrix();
        }
    }

    private static void ring(GuiGraphics g, float cx, float cy, float r, int color, int thick) {
        if (r < 2) return;
        int n = 72;
        float seg = (float) (2 * Math.PI * r / n) + 1.5f;
        for (int i = 0; i < n; i++) {
            float a = (float) (i * 2 * Math.PI / n);
            g.pose().pushMatrix();
            g.pose().translate(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r * 0.8f);
            g.pose().rotate(a + (float) Math.PI / 2);
            g.fill(Math.round(-seg / 2), -thick, Math.round(seg / 2), thick, color);
            g.pose().popMatrix();
        }
    }

    private static void vignette(GuiGraphics g, int w, int h, int col, float a) {
        if (a <= 0.01f) return;
        int band = h / 5;
        g.fillGradient(0, 0, w, band, argb(a, col), argb(0, col));
        g.fillGradient(0, h - band, w, h, argb(0, col), argb(a, col));
        int strips = 8;
        int sw = Math.max(1, w / 40);
        for (int i = 0; i < strips; i++) {
            int c = argb(a * (1 - i / (float) strips) * 0.8f, col);
            g.fill(i * sw, 0, (i + 1) * sw, h, c);
            g.fill(w - (i + 1) * sw, 0, w - i * sw, h, c);
        }
    }

    private static void rainbowSweep(GuiGraphics g, int w, int h, double el, float vis) {
        int bands = 14;
        float bw = w / (float) bands;
        double shift = (el / 2500.0) % 1.0;
        for (int i = 0; i <= bands; i++) {
            int c = hsv((float) ((i / (double) bands + shift) % 1.0), 0.9f, 1f);
            int x = Math.round((float) (i - shift * 2) * bw);
            g.fill(x, 0, Math.round(x + bw) + 1, h, argb(vis * 0.13f, c));
        }
    }

    // ------------------------------------------------------------------ colors

    private static int color(Show s, int offset) {
        if ("rainbow".equalsIgnoreCase(s.tier.color)) {
            return hsv((float) (((System.currentTimeMillis() + offset * 37L) % 3000) / 3000.0), 0.8f, 1f);
        }
        return parseColor(s.tier.color, 0xFFFFFF);
    }

    public static int parseColor(String s, int def) {
        if (s == null) return def;
        String t = s.trim().replace("#", "");
        try {
            return Integer.parseInt(t, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int lighten(int c) {
        int r = Math.min(255, ((c >> 16) & 255) + 70);
        int gg = Math.min(255, ((c >> 8) & 255) + 70);
        int b = Math.min(255, (c & 255) + 70);
        return (r << 16) | (gg << 8) | b;
    }

    static int argb(float a, int rgb) {
        int al = Math.max(0, Math.min(255, Math.round(a * 255)));
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    static int hsv(float h, float s, float v) {
        float r, g, b;
        int i = (int) Math.floor(h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        switch (((i % 6) + 6) % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    private static double easeOutBack(double x) {
        double c1 = 1.70158, c3 = c1 + 1;
        return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
    }

    private static double easeOut(double x) {
        return 1 - Math.pow(1 - x, 3);
    }

    static Item item(String id) {
        if (id == null || id.isEmpty()) return null;
        Identifier rl = Identifier.tryParse(id);
        if (rl == null) return null;
        Item it = BuiltInRegistries.ITEM.getValue(rl);
        return it == Items.AIR ? null : it;
    }
}
