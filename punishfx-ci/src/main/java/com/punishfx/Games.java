package com.punishfx;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The two mini-games that pick a punishment on screen: Spin the Wheel and Case Opening.
 * One game shows at a time; more wait in line. The winner is decided up front (so the luck and odds
 * settings are exact) and the animation is built to land on it.
 */
public final class Games {
    public enum Kind { WHEEL, CASE }

    // ---- choices shown in the settings
    public static final int[] WHEEL_SLOTS = {4, 6, 8, 10, 12, 16};
    public static final double[] LENGTHS = {1.0, 1.5, 2.0, 3.0};
    public static final String[] LENGTH_NAMES = {"Normal", "1.5x", "2x", "3x"};
    public static final int[] SPIN_SECONDS = {3, 4, 5, 6, 8, 10, 12};
    public static final int[] OPEN_SECONDS = {3, 4, 5, 6, 8, 10};
    public static final String[] WHEEL_COLOR_NAMES = {"Theme", "Casino", "Rainbow", "Pastel", "Neon", "Game show", "Categories"};
    public static final String[] RIM_NAMES = {"None", "Gold", "Theme", "Light bulbs", "Neon"};
    public static final String[] POINTER_NAMES = {"White", "Gold", "Theme", "Red"};
    public static final String[] TICK_NAMES = {"Off", "Click", "Note block", "Pling"};
    private static final String[] TICK_SOUNDS = {null, "minecraft:block.wooden_button.click_on", "minecraft:block.note_block.hat", "minecraft:block.note_block.pling"};
    public static final String[] USE_NAMES = {"When picked", "Every donation", "Level 2 and up", "Level 3 and up", "Level 4 and up", "Level 5 and up"};
    public static final String[] RARITY_NAMES = {"Mil-Spec", "Restricted", "Classified", "Covert", "Rare Special"};
    public static final int[] RARITY_COLORS = {0x4B69FF, 0x8847FF, 0xD32CE6, 0xEB4B4B, 0xFFD700};
    private static final double[][] ODDS = {{79.92, 15.98, 3.2, 0.64, 0.26}, {1, 1, 1, 1, 1}, {40, 30, 17, 9, 4}};
    private static final double[] RARITY_LONGER = {1.0, 1.25, 1.5, 2.0, 1.0};

    // ---- special slots
    public static final String NOTHING = "#nothing", AGAIN = "#again", DOUBLE = "#double", JACKPOT = "#jackpot",
            MEGA = "#mega", DTIME = "#dtime", WHEEL_SLOT = "#wheel";

    /** One slot on the wheel / card in the case. */
    public static final class Entry {
        public final String id;
        public final String name;
        public final int rarity;

        Entry(String id, int rarity) {
            this.id = id;
            this.name = displayName(id);
            this.rarity = rarity;
        }

        public boolean special() {
            return id.startsWith("#");
        }
    }

    public static final class Game {
        public final Kind kind;
        /** Wheel: the slots in order. Case: what's in the case (one of each). */
        public final List<Entry> entries;
        /** Case only: the strip of cards that scrolls past; the winner is at {@link #win}. */
        public final List<Entry> reel;
        /** Index of the winner in entries (wheel) or reel (case). */
        public final int win;
        /** The punishments the game draws extra picks from (Double, Jackpot...). */
        final List<String> pool;
        public final String donor;       // shown under the title
        final String player;              // null for tests (not counted in stream stats)
        public final double amount;
        final int level;
        final long donation;
        final double mult;
        final int chain;
        public final long seed;
        public long start;
        public final long introMs = 450, runMs;
        /** Wheel: total turn in radians. Case: final position in cards. */
        public final double target;
        public final double from;
        final int style;
        public boolean resolved;
        public long resolvedAt, until;
        public String resultTitle = "", resultSub = "";
        public int resultColor = 0xFFFFFF;
        int lastTick = Integer.MIN_VALUE;
        long lastTickAt;

        Game(Kind kind, List<Entry> entries, List<Entry> reel, int win, List<String> pool, String donor, String player, double amount,
             int level, long donation, double mult, int chain, long seed, long runMs, double from, double target, int style) {
            this.kind = kind;
            this.entries = entries;
            this.reel = reel;
            this.win = win;
            this.pool = pool;
            this.donor = donor;
            this.player = player;
            this.amount = amount;
            this.level = level;
            this.donation = donation;
            this.mult = mult;
            this.chain = chain;
            this.seed = seed;
            this.runMs = runMs;
            this.from = from;
            this.target = target;
            this.style = style;
        }

        public Entry winner() {
            return kind == Kind.WHEEL ? entries.get(win) : reel.get(win);
        }

        /** 0..1 through the spin (0 during the intro). */
        public double t(long now) {
            return Math.max(0, Math.min(1, (now - start - introMs) / (double) runMs));
        }

        /** Wheel rotation (radians) or case position (cards) right now. */
        public double pos(long now) {
            double t = t(now);
            if (kind == Kind.WHEEL) {
                double seg = Math.PI * 2 / entries.size();
                return switch (style) {
                    case 1 -> target * (1 - Math.pow(1 - t, 4)) + seg * 0.45 * Math.sin(Math.PI * Math.max(0, (t - 0.72) / 0.28)) * (t > 0.72 ? 1 : 0);
                    case 2 -> target * (1 - Math.pow(1 - t, 6));
                    default -> target * (1 - Math.pow(1 - t, 3.2));
                };
            }
            double e = switch (style) {
                case 1 -> 1 - Math.pow(1 - t, 2.2);
                case 2 -> 1 - Math.pow(1 - t, 5.5);
                default -> 1 - Math.pow(1 - t, 3.5);
            };
            return from + (target - from) * e;
        }

        /** The slot / card under the pointer right now. */
        public int under(long now) {
            double p = pos(now);
            if (kind == Kind.WHEEL) {
                int n = entries.size();
                double seg = Math.PI * 2 / n;
                double a = ((-p) % (Math.PI * 2) + Math.PI * 2) % (Math.PI * 2);
                return Math.min(n - 1, (int) (a / seg));
            }
            return (int) Math.floor(p);
        }

        public boolean spinning(long now) {
            return now >= start + introMs && !resolved;
        }

        public long endAt() {
            return start + introMs + runMs;
        }
    }

    private static final ArrayDeque<Game> QUEUE = new ArrayDeque<>();
    private static Game current;
    private static final Random R = new Random();

    private Games() {}

    public static Game current() {
        return current;
    }

    public static boolean busy() {
        return current != null || !QUEUE.isEmpty();
    }

    /** A game is on screen (spinning or showing its result). */
    public static boolean showing() {
        return current != null;
    }

    public static int waiting() {
        return QUEUE.size();
    }

    public static void cancelAll() {
        QUEUE.clear();
        current = null;
    }

    public static void cancelDonations(Collection<Long> ids) {
        QUEUE.removeIf(g -> ids.contains(g.donation));
        if (current != null && !current.resolved && ids.contains(current.donation)) current = null;
    }

    // ------------------------------------------------------------------ building a game

    static String displayName(String id) {
        return switch (id) {
            case NOTHING -> "NOTHING!";
            case AGAIN -> "SPIN AGAIN";
            case DOUBLE -> "DOUBLE";
            case JACKPOT -> "JACKPOT";
            case MEGA -> "MEGA COMBO";
            case DTIME -> "DOUBLE TIME";
            case WHEEL_SLOT -> "THE WHEEL";
            default -> Punishes.name(id);
        };
    }

    private static final Map<String, ItemStack> ICONS = new HashMap<>();

    /** The item drawn for a slot. */
    public static ItemStack icon(String id) {
        return ICONS.computeIfAbsent(id, k -> {
            String item = switch (k) {
                case NOTHING -> "totem_of_undying";
                case AGAIN -> "recovery_compass";
                case DOUBLE -> "tnt_minecart";
                case JACKPOT, MEGA -> "nether_star";
                case DTIME -> "clock";
                case WHEEL_SLOT -> "target";
                default -> {
                    Punishes.P p = Punishes.get(k);
                    yield p == null ? "barrier" : p.icon;
                }
            };
            try {
                return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(item)));
            } catch (Throwable t) {
                return ItemStack.EMPTY;
            }
        });
    }

    /** What the game can land on: a level's punishments, or all of them (never the games themselves). */
    static List<String> contents(int mode, int level) {
        Config c = Config.get();
        List<String> out = new ArrayList<>();
        if (mode == 0 && level >= 0 && level < c.tiers.size()) {
            for (String id : Punishes.poolFor(c.tiers.get(level))) {
                Punishes.P p = Punishes.get(id);
                if (p != null && !p.game()) out.add(p.id);
            }
        }
        if (out.isEmpty()) for (Punishes.P p : Punishes.all()) if (!p.optIn && !p.game()) out.add(p.id);
        return out;
    }

    static List<Entry> wheelSlots(List<String> pool, Random r) {
        Config c = Config.get();
        int n = WHEEL_SLOTS[c.wheelSlots];
        List<String> specials = new ArrayList<>();
        for (int i = 0; i < c.wheelNothing; i++) specials.add(NOTHING);
        for (int i = 0; i < c.wheelAgain; i++) specials.add(AGAIN);
        for (int i = 0; i < c.wheelDouble; i++) specials.add(DOUBLE);
        for (int i = 0; i < c.wheelJackpot; i++) specials.add(JACKPOT);
        while (specials.size() > n - 2) specials.remove(specials.size() - 1); // always at least two punishments
        List<String> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, r);
        String[] slots = new String[n];
        // spread the specials evenly round the wheel
        for (int k = 0; k < specials.size(); k++) {
            int at = (int) Math.round((k + 0.5) * n / specials.size()) % n;
            while (slots[at] != null) at = (at + 1) % n;
            slots[at] = specials.get(k);
        }
        int pi = 0;
        String prev = null;
        for (int i = 0; i < n; i++) {
            if (slots[i] != null) {
                prev = slots[i];
                continue;
            }
            String id = shuffled.get(pi++ % shuffled.size());
            // avoid the same punishment twice in a row when the list is short
            if (id.equals(prev) && shuffled.size() > 1) id = shuffled.get(pi++ % shuffled.size());
            slots[i] = id;
            prev = id;
        }
        List<Entry> out = new ArrayList<>();
        for (String id : slots) out.add(new Entry(id, 0));
        return out;
    }

    static int rarityOf(String id, Random r) {
        Config c = Config.get();
        if (id.equals(NOTHING)) return 0;
        if (id.startsWith("#")) return 4;
        Punishes.P p = Punishes.get(id);
        if (p == null) return 0;
        return switch (c.caseRarity) {
            case 1 -> switch (p.cat) {
                case SCREEN -> 0;
                case VIEW, HOTBAR -> 1;
                case MOVEMENT -> 2;
                default -> 3;
            };
            case 2 -> r.nextInt(4);
            default -> Math.max(0, Math.min(3, p.severity));
        };
    }

    static List<Entry> caseContents(List<String> pool, Random r) {
        Config c = Config.get();
        List<Entry> out = new ArrayList<>();
        for (String id : pool) out.add(new Entry(id, rarityOf(id, r)));
        for (int i = 0; i < c.caseSafe; i++) out.add(new Entry(NOTHING, 0));
        String gold = switch (c.caseGold) {
            case 1 -> MEGA;
            case 2 -> DTIME;
            case 3 -> WHEEL_SLOT;
            default -> null;
        };
        if (gold != null) out.add(new Entry(gold, 4));
        return out;
    }

    /** Chance (0..1) of unboxing each entry, from the odds setting. */
    public static double[] chances(List<Entry> items) {
        Config c = Config.get();
        double[] w = new double[items.size()];
        if (c.caseOdds == 1) {
            java.util.Arrays.fill(w, 1.0 / items.size());
            return w;
        }
        int[] per = new int[5];
        for (Entry e : items) per[e.rarity]++;
        double total = 0;
        for (int k = 0; k < 5; k++) if (per[k] > 0) total += ODDS[c.caseOdds][k];
        for (int i = 0; i < items.size(); i++) {
            int k = items.get(i).rarity;
            w[i] = ODDS[c.caseOdds][k] / total / per[k];
        }
        return w;
    }

    /** The chance of each rarity (for the odds strip under the case). */
    public static double[] rarityChances(List<Entry> items) {
        double[] w = chances(items);
        double[] out = new double[5];
        for (int i = 0; i < items.size(); i++) out[items.get(i).rarity] += w[i];
        return out;
    }

    private static int draw(double[] w, Random r) {
        double x = r.nextDouble(), acc = 0;
        for (int i = 0; i < w.length; i++) {
            acc += w[i];
            if (x < acc) return i;
        }
        return w.length - 1;
    }

    public static Game build(Kind kind, int level, String donor, String player, double amount, long donation, double mult, int chain, long seed) {
        Config c = Config.get();
        Random r = new Random(seed);
        if (kind == Kind.WHEEL) {
            List<String> pool = contents(c.wheelContents, level);
            List<Entry> slots = wheelSlots(pool, r);
            int n = slots.size();
            List<Integer> choices = new ArrayList<>();
            for (int i = 0; i < n; i++) if (c.wheelLuck == 0 || !slots.get(i).id.equals(NOTHING)) choices.add(i);
            int win = choices.get(r.nextInt(choices.size()));
            double f = 0.15 + r.nextDouble() * 0.7;
            if (c.wheelLuck == 2) {
                // a near miss: crawl just past a "NOTHING" slot (the one before the winner)
                List<Integer> teases = new ArrayList<>();
                for (int i : choices) if (slots.get((i + 1) % n).id.equals(NOTHING)) teases.add(i);
                if (!teases.isEmpty()) win = teases.get(r.nextInt(teases.size()));
                f = 0.9 + r.nextDouble() * 0.07;
            }
            double seg = Math.PI * 2 / n;
            int secs = SPIN_SECONDS[c.wheelSpin];
            int turns = Math.max(2, (int) Math.round(secs * 0.9 + 1));
            double land = (((-(win + f) * seg) % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2);
            double target = turns * Math.PI * 2 + land;
            return new Game(kind, slots, null, win, pool, donor, player, amount, level, donation, mult, chain, seed,
                    secs * 1000L, 0, target, c.wheelStyle);
        }
        List<String> pool = contents(c.caseContents, level);
        List<Entry> items = caseContents(pool, r);
        double[] w = chances(items);
        int secs = OPEN_SECONDS[c.caseOpen];
        int len = 26 + secs * 6;
        int winSlot = len - 5;
        List<Entry> reel = new ArrayList<>();
        for (int i = 0; i < len; i++) reel.add(items.get(draw(w, r)));
        reel.set(winSlot, items.get(draw(w, r)));
        double f = c.caseStyle == 2 ? (r.nextBoolean() ? 0.02 + r.nextDouble() * 0.05 : 0.93 + r.nextDouble() * 0.05)
                : 0.12 + r.nextDouble() * 0.76;
        return new Game(kind, items, reel, winSlot, pool, donor, player, amount, level, donation, mult, chain, seed,
                secs * 1000L, 2.5, winSlot + f, c.caseStyle);
    }

    /** Queue a game for a donation. {@code front} puts it next in line (Spin again, the case's wheel). */
    public static void request(Kind kind, int level, String donor, String player, double amount, long donation, double mult, int chain, boolean front) {
        Game g = build(kind, level, donor, player, amount, donation, mult, chain, R.nextLong());
        if (front) QUEUE.addFirst(g);
        else if (QUEUE.size() < 20) QUEUE.addLast(g);
    }

    // ------------------------------------------------------------------ running

    public static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (current != null && current.resolved && now >= current.until) current = null;
        if (current == null && !QUEUE.isEmpty()) {
            current = QUEUE.pollFirst();
            current.start = now;
            PunishManager.playSound(mc, current.kind == Kind.WHEEL ? "minecraft:block.note_block.bell" : "minecraft:block.chest.open", 0.8f, 1.0f);
        }
        if (current != null && !current.resolved && now >= current.endAt()) resolve(mc, current, now);
    }

    /** Called every frame while drawing: the ticking sound as slots pass the pointer. */
    public static void frame(Game g, long now) {
        if (g == null || !g.spinning(now)) return;
        int u = g.under(now);
        if (u != g.lastTick) {
            boolean first = g.lastTick == Integer.MIN_VALUE;
            g.lastTick = u;
            int idx = g.kind == Kind.WHEEL ? Config.get().wheelTick : Config.get().caseTick;
            String snd = TICK_SOUNDS[Math.max(0, Math.min(TICK_SOUNDS.length - 1, idx))];
            if (!first && snd != null && now - g.lastTickAt > 30) {
                g.lastTickAt = now;
                float pitch = 0.9f + 0.6f * (float) (1 - g.t(now)) + (g.kind == Kind.CASE ? 0.3f : 0f);
                PunishManager.playSound(Minecraft.getInstance(), snd, 0.45f, pitch);
            }
        }
    }

    private static String pickOne(Game g, Random r) {
        return g.pool.get(r.nextInt(g.pool.size()));
    }

    private static List<String> pickSome(Game g, int n, Random r) {
        List<String> copy = new ArrayList<>(g.pool);
        Collections.shuffle(copy, r);
        return new ArrayList<>(copy.subList(0, Math.min(n, copy.size())));
    }

    private static void resolve(Minecraft mc, Game g, long now) {
        Config c = Config.get();
        g.resolved = true;
        g.resolvedAt = now;
        g.until = now + 2600;
        Entry e = g.winner();
        Random r = new Random(g.seed ^ 0x5DEECE66DL);
        double mult = g.mult * (g.kind == Kind.WHEEL ? LENGTHS[c.wheelLength] : (c.caseLonger ? RARITY_LONGER[e.rarity] : 1.0));
        List<String> run = new ArrayList<>();
        g.resultColor = g.kind == Kind.CASE ? GameRender.rarityColor(e.rarity) : 0xFFFFFF;
        switch (e.id) {
            case NOTHING -> {
                g.resultTitle = "NOTHING!";
                g.resultSub = "You got lucky this time";
                g.resultColor = 0x6BFF9E;
                PunishManager.playSound(mc, "minecraft:entity.player.levelup", 0.8f, 1.2f);
            }
            case AGAIN -> {
                g.resultTitle = "SPIN AGAIN!";
                if (g.chain < 3) {
                    g.resultSub = "One more go...";
                    g.until = now + 1500;
                    request(g.kind, g.level, g.donor, g.player, g.amount, g.donation, g.mult, g.chain + 1, true);
                } else {
                    run.add(pickOne(g, r));
                    g.resultSub = "Too many spins - " + Punishes.name(run.get(0)) + " it is";
                }
            }
            case DOUBLE -> {
                run.addAll(pickSome(g, 2, r));
                g.resultTitle = "DOUBLE!";
            }
            case JACKPOT -> {
                run.addAll(pickSome(g, 3, r));
                mult *= 2;
                g.resultTitle = "JACKPOT!";
                g.resultColor = 0xFFD700;
            }
            case MEGA -> {
                run.addAll(pickSome(g, 3, r));
                mult *= 1.5;
                g.resultTitle = "★ MEGA COMBO ★";
            }
            case DTIME -> {
                run.add(pickOne(g, r));
                mult *= 2;
                g.resultTitle = "★ DOUBLE TIME ★";
            }
            case WHEEL_SLOT -> {
                g.resultTitle = "★ THE WHEEL ★";
                g.resultSub = "Now spin for it...";
                g.until = now + 1500;
                request(Kind.WHEEL, g.level, g.donor, g.player, g.amount, g.donation, g.mult, g.chain + 1, true);
            }
            default -> {
                run.add(e.id);
                g.resultTitle = e.name.toUpperCase(java.util.Locale.ROOT) + "!";
            }
        }
        if (!run.isEmpty()) {
            if (g.resultSub.isEmpty()) {
                List<String> names = new ArrayList<>();
                for (String id : run) names.add(Punishes.name(id));
                int lvl = Math.max(0, Math.min(c.tiers.size() - 1, g.level));
                long secs = Math.round(c.tiers.get(lvl).seconds * c.lengthScale * mult);
                String len = "for " + secs + "s" + (mult > 1.01 ? " (" + (Math.round(mult * 10) / 10.0) + "x)" : "");
                g.resultSub = run.size() > 1 ? String.join(" + ", names) + "  ·  " + len : len;
            }
            if (g.kind == Kind.CASE && !e.special()) g.resultSub = RARITY_NAMES[e.rarity] + "  ·  " + g.resultSub;
            for (String id : run) PunishManager.startFromGame(id, g.level, g.player, g.amount, mult, g.donation);
            boolean big = e.id.equals(JACKPOT) || e.id.equals(MEGA) || e.rarity >= 3;
            PunishManager.playSound(mc, big ? "minecraft:ui.toast.challenge_complete" : "minecraft:block.note_block.bit", 0.8f, big ? 1f : 0.7f);
        }
    }

    // ------------------------------------------------------------------ previews

    private static Game demoWheel, demoCase;
    private static String demoKeyW = "", demoKeyC = "";
    private static long demoCycleW = -1, demoCycleC = -1;
    private static long previewKick = 0;

    /** Start the settings-page preview over right now. */
    public static void restartPreview() {
        previewKick = System.currentTimeMillis();
        demoCycleW = demoCycleC = -1;
    }

    /** A looping example game for the settings preview and the move editor. */
    public static Game demo(Kind kind, long now) {
        Config c = Config.get();
        String key = kind == Kind.WHEEL
                ? c.wheelSlots + "," + c.wheelNothing + "," + c.wheelAgain + "," + c.wheelDouble + "," + c.wheelJackpot + "," + c.wheelContents
                + "," + c.wheelSpin + "," + c.wheelStyle + "," + c.wheelLuck
                : c.caseSafe + "," + c.caseGold + "," + c.caseRarity + "," + c.caseOdds + "," + c.caseContents + "," + c.caseOpen + "," + c.caseStyle;
        Game cur = kind == Kind.WHEEL ? demoWheel : demoCase;
        long period = cur == null ? 1 : cur.introMs + cur.runMs + 2200;
        long cycle = (now - previewKick) / Math.max(1, period);
        boolean stale = cur == null || !key.equals(kind == Kind.WHEEL ? demoKeyW : demoKeyC) || cycle != (kind == Kind.WHEEL ? demoCycleW : demoCycleC);
        if (stale) {
            Game g = build(kind, 0, "Steve", null, 1_000_000, -1, 1, 0, 1234 + cycle * 7919);
            period = g.introMs + g.runMs + 2200;
            cycle = (now - previewKick) / period;
            g.start = previewKick + cycle * period;
            if (kind == Kind.WHEEL) {
                demoWheel = g;
                demoKeyW = key;
                demoCycleW = cycle;
            } else {
                demoCase = g;
                demoKeyC = key;
                demoCycleC = cycle;
            }
            cur = g;
        }
        if (!cur.resolved && now >= cur.endAt()) {
            cur.resolved = true;
            cur.resolvedAt = cur.endAt();
            Entry e = cur.winner();
            cur.resultTitle = e.special() ? displayName(e.id) : e.name.toUpperCase(java.util.Locale.ROOT) + "!";
            cur.resultSub = kind == Kind.CASE && !e.special() ? RARITY_NAMES[e.rarity] : "example";
            cur.resultColor = kind == Kind.CASE ? GameRender.rarityColor(e.rarity) : 0xFFFFFF;
        }
        return cur;
    }
}
