package com.punishfx;

import com.punishfx.mixin.I18nAccessor;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.ArrayList;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Random;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;

/** Chooses, runs and expires punishments. Everything here is client-side only. */
public final class PunishManager {
    public static final class Active {
        public final Punishes.P p;
        public final long start;
        public long end;
        public final Random r = new Random();
        boolean fired;
        int level;
        /** Who caused it (null for tests), for the stream recap. */
        public String donor;
        /** Which donation started it (for "replace", "biggest wins", "team up"). */
        public long donation;
        /** Ticks it has been running (for repeating sounds). */
        int ticks;

        Active(Punishes.P p, long start, long end, int level) {
            this.p = p;
            this.start = start;
            this.end = end;
            this.level = level;
        }

        public float progress() {
            long now = System.currentTimeMillis();
            return (float) Math.max(0, Math.min(1, (now - start) / (double) Math.max(1, end - start)));
        }
    }

    private static final CopyOnWriteArrayList<Active> ACTIVE = new CopyOnWriteArrayList<>();
    private static final Random R = new Random();
    private static long tick = 0;
    private static final Set<Holder<MobEffect>> APPLIED = Collections.newSetFromMap(new IdentityHashMap<>());
    private static float drunkAngle = 0;

    // extreme (leave/close) pending countdown
    private static Punishes.P pendingExtreme;
    private static long pendingUntil, lastBeep;

    private static boolean hudHidden;
    private static boolean savedHideGui;

    public static String banner = "";
    public static long bannerUntil = 0;

    // Donor banner: who paid, how much, and what they triggered.
    public static String donor = "";
    public static String donorAmount = "";
    public static String donorSub = "";
    public static long donorUntil = 0;
    public static long donorStart = 0;
    private static long lastPunishAt = 0;

    // ---- several donations at once
    public static final String[] OVERLAP_NAMES = {"All at once", "Queue", "Combo", "Biggest wins", "Add time", "Replace",
            "Team up", "First only", "Surprise me"};
    public static final String[] OVERLAP_DESCS = {
            "Every donation's punishments run together",
            "They wait in line and play one after another",
            "Each extra one is a combo: everything gets longer",
            "Only the biggest donation's punishment plays",
            "Extra ones add time to what's already running",
            "The newest donation cancels what's running",
            "Amounts add up, so donors can reach a higher level",
            "Extra ones just show the banner, no punishment",
            "A random one of these each time"};
    public static final int[] OVERLAP_WINDOWS = {0, 5, 10, 30, 60};
    public static final String[] WINDOW_NAMES = {"While one runs", "Within 5s", "Within 10s", "Within 30s", "Within 60s"};
    public static final int[] QUEUE_GAPS = {0, 1, 3, 5};
    public static final double[] COMBO_STEPS = {0.25, 0.5, 1.0};

    /** A donation that's waiting in the queue. */
    public record Donation(long id, double amount, String who, String player, int level) {}

    private static final java.util.ArrayDeque<Donation> QUEUE = new java.util.ArrayDeque<>();
    private static long donationSeq = 0, lastDonationAt = 0, idleSince = 0;
    private static double windowBest = 0, windowTotal = 0;
    private static int windowLevel = -1;
    private static final List<Long> WINDOW_IDS = new ArrayList<>();
    public static int comboCount = 0;
    public static long comboUntil = 0;

    private PunishManager() {}

    /** Anything still going: punishments, a wheel/case, or a leave/close countdown. */
    public static boolean running() {
        return !ACTIVE.isEmpty() || Games.busy() || pendingExtreme != null;
    }

    public static List<Donation> queue() {
        return new ArrayList<>(QUEUE);
    }

    public static boolean donorShowing() {
        return !donor.isEmpty() && System.currentTimeMillis() < donorUntil;
    }

    public static List<Active> active() {
        return ACTIVE;
    }

    public static boolean extremePending() {
        return pendingExtreme != null;
    }

    public static long extremeSecondsLeft() {
        return Math.max(0, (pendingUntil - System.currentTimeMillis() + 999) / 1000);
    }

    public static long extremeMillisLeft() {
        return Math.max(0, pendingUntil - System.currentTimeMillis());
    }

    /** Total length of the current pending countdown, so the overlay can draw a progress bar. */
    public static long extremeTotalMillis() {
        Config c = Config.get();
        return c.confirmExtreme ? c.countdownSeconds * 1000L : 1200;
    }

    public static Punishes.P pendingExtreme() {
        return pendingExtreme;
    }

    /** Cancel key / command: stop the pending extreme and clear running punishments. */
    public static void cancelAll() {
        if (pendingExtreme != null) StreamSession.extremeCancelled();
        if (pendingExtreme != null || !ACTIVE.isEmpty() || Games.busy()) StreamSession.cancelled();
        pendingExtreme = null;
        ACTIVE.clear();
        Games.cancelAll();
        QUEUE.clear();
        comboCount = 0;
        comboUntil = 0;
        ControlState.clear();
        banner = "Punishments cancelled.";
        bannerUntil = System.currentTimeMillis() + 1500;
    }

    /** A donation was detected (or a test/command). */
    public static void hit(double amount, String player, int level, boolean test) {
        Config c = Config.get();
        if (level < 0 || level >= c.tiers.size()) return;
        long now = System.currentTimeMillis();
        String who = (player == null || player.isBlank()) ? "Someone" : player;
        // cooldown: a donation soon after the last punishment only shows the banner
        boolean cooling = !test && c.cooldownSeconds > 0 && now - lastPunishAt < c.cooldownSeconds * 1000L;
        if (cooling) {
            announce(who, amount, "on cooldown, no punishment");
            StreamSession.cooldownSkip();
            return;
        }

        if (c.bannerOnly) {
            announce(who, amount, "thank you!");
            return;
        }
        boolean overlap = overlapping(now);
        lastDonationAt = now;
        Donation d = new Donation(++donationSeq, amount, who, test ? null : player, level);
        if (!overlap) {
            comboCount = 1;
            windowBest = amount;
            windowTotal = amount;
            windowLevel = level;
            WINDOW_IDS.clear();
            run(d, 1.0, "");
            return;
        }
        int mode = c.overlapMode;
        String prefix = "";
        if (mode == 8) {
            mode = 1 + R.nextInt(7);
            prefix = "Surprise: " + OVERLAP_NAMES[mode] + "  \u00b7  ";
        }
        switch (mode) {
            case 1 -> { // queue
                if (running()) {
                    if (QUEUE.size() < 30) QUEUE.addLast(d);
                    announce(who, amount, prefix + "queued - number " + QUEUE.size() + " in line");
                } else run(d, 1.0, prefix);
            }
            case 2 -> { // combo
                comboCount++;
                double step = COMBO_STEPS[c.comboStep];
                double mult = Math.min(4.0, 1 + step * (comboCount - 1));
                for (Active a : ACTIVE) a.end = Math.min(a.end + (long) ((a.end - now) * step), now + 180_000);
                comboUntil = now + 3500;
                run(d, mult, prefix + "COMBO x" + comboCount + "  \u00b7  ");
                if (c.sounds) playSound(Minecraft.getInstance(), "minecraft:entity.experience_orb.pickup", 0.9f, 0.8f + 0.1f * Math.min(8, comboCount));
            }
            case 3 -> { // biggest wins
                if (amount > windowBest) {
                    cancelDonations(WINDOW_IDS);
                    WINDOW_IDS.clear();
                    windowBest = amount;
                    run(d, 1.0, prefix + "new biggest!  \u00b7  ");
                } else {
                    announce(who, amount, prefix + "smaller than " + Money.compact(windowBest) + " - no punishment");
                }
            }
            case 4 -> { // add time
                if (!ACTIVE.isEmpty()) {
                    double add = c.tiers.get(level).seconds * c.lengthScale;
                    for (Active a : ACTIVE) a.end = Math.min(a.end + (long) (add * 1000), now + 180_000);
                    lastPunishAt = now;
                    announce(who, amount, prefix + "+" + fmt(add) + "s to everything running");
                } else run(d, 1.0, prefix);
            }
            case 5 -> { // replace
                cancelDonations(new ArrayList<>(WINDOW_IDS));
                ACTIVE.clear();
                Games.cancelAll();
                pendingExtreme = null;
                WINDOW_IDS.clear();
                run(d, 1.0, prefix + "replaces what was running  \u00b7  ");
            }
            case 6 -> { // team up
                windowTotal += amount;
                int nl = c.tierFor(windowTotal);
                if (nl > windowLevel) {
                    cancelDonations(WINDOW_IDS);
                    WINDOW_IDS.clear();
                    windowLevel = nl;
                    Donation up = new Donation(d.id(), amount, who, d.player(), nl);
                    run(up, 1.0, prefix + "TEAM-UP " + Money.compact(windowTotal) + " = " + c.tiers.get(nl).name + "  \u00b7  ");
                } else {
                    run(d, 1.0, prefix + "team-up total " + Money.compact(windowTotal) + "  \u00b7  ");
                }
            }
            case 7 -> announce(who, amount, prefix + "already going - just the banner"); // first only
            default -> run(d, 1.0, prefix);
        }
    }

    private static boolean overlapping(long now) {
        Config c = Config.get();
        if (lastDonationAt == 0) return false;
        if (c.overlapWindow == 0) return running();
        return now - lastDonationAt <= OVERLAP_WINDOWS[c.overlapWindow] * 1000L;
    }

    private static void cancelDonations(List<Long> ids) {
        if (ids.isEmpty()) return;
        List<Long> copy = new ArrayList<>(ids);
        ACTIVE.removeIf(a -> copy.contains(a.donation));
        Games.cancelDonations(copy);
    }

    /** A short message across the top of the screen. */
    public static void showBanner(String text) {
        banner = text;
        bannerUntil = System.currentTimeMillis() + 4000;
    }

    /** Replaces "Name paid you" on the banner when set (giveaway joins). */
    public static String donorHead = null;

    /** Someone typed the join word: "Steve joined the giveaway!" */
    public static void announceJoin(String who, String sub) {
        Config c = Config.get();
        long now = System.currentTimeMillis();
        donor = who;
        donorHead = who;
        donorAmount = "";
        donorSub = sub;
        donorStart = now;
        donorUntil = now + (long) (c.bannerSeconds * 1000);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) playSound(mc, "minecraft:entity.experience_orb.pickup", 0.8f, 1.4f);
    }

    /** Someone entered the giveaway: the donor banner, with a cheerful sound instead of the punishment one. */
    public static void announceEntry(String who, double amount, String sub) {
        Config c = Config.get();
        long now = System.currentTimeMillis();
        donor = who;
        donorHead = null;
        donorAmount = Money.full(amount);
        donorSub = sub;
        donorStart = now;
        donorUntil = now + (long) (c.bannerSeconds * 1000);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            playSound(mc, "minecraft:entity.experience_orb.pickup", 0.8f, 1.2f);
            playSound(mc, "minecraft:block.note_block.chime", 0.6f, 1.5f);
        }
    }

    /** Who paid + how much (and what it does) across the top of the screen. */
    private static void announce(String who, double amount, String sub) {
        Config c = Config.get();
        if (!c.announce) return;
        long now = System.currentTimeMillis();
        donor = who;
        donorHead = null;
        donorAmount = Money.full(amount);
        donorSub = sub;
        donorStart = now;
        donorUntil = now + (long) (c.bannerSeconds * 1000);
        Minecraft mc = Minecraft.getInstance();
        if (c.sounds && mc.player != null) {
            playSound(mc, "minecraft:block.note_block.pling", 0.8f, 0.6f);
            playSound(mc, "minecraft:entity.villager.no", 0.7f, 0.8f);
        }
    }

    /** Wheel / case set to run for every donation (or from a level up) instead of the level's own picks. */
    private static boolean forced(int use, int level) {
        return use == 1 || (use >= 2 && level + 1 >= use);
    }

    private static List<String> pickFor(int level) {
        Config c = Config.get();
        boolean wheel = forced(c.wheelUse, level), kase = forced(c.caseUse, level);
        if (wheel && kase) return List.of(R.nextBoolean() ? "spin_wheel" : "case_open");
        if (wheel) return List.of("spin_wheel");
        if (kase) return List.of("case_open");
        Config.Tier t = c.tiers.get(level);
        List<String> out = new ArrayList<>();
        boolean game = false;
        for (String id : Punishes.pick(t, Math.max(1, t.count))) {
            Punishes.P p = Punishes.get(id);
            if (p != null && p.game()) {
                if (game) continue; // one mini-game per donation
                game = true;
            }
            out.add(id);
        }
        return out;
    }

    /** Punish for one donation now. */
    private static void run(Donation d, double mult, String prefix) {
        List<String> ids = pickFor(d.level());
        if (ids.isEmpty()) return;
        List<String> names = new ArrayList<>();
        for (String id : ids) names.add(Punishes.name(id));
        announce(d.who(), d.amount(), prefix + String.join("  \u00b7  ", names));
        lastPunishAt = System.currentTimeMillis();
        WINDOW_IDS.add(d.id());
        for (String id : ids) start(id, d.level(), d.player(), d.amount(), mult, d.id(), d.who());
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(Math.round(v * 10) / 10.0);
    }

    /** Force a specific punishment (test / command). */
    public static void force(String id, int level) {
        Config c = Config.get();
        int l = Math.max(0, Math.min(c.tiers.size() - 1, level));
        start(id, l, null, c.tiers.get(l).minAmount, 1.0, ++donationSeq, "You");
    }

    /** A wheel / case landed on this. */
    public static void startFromGame(String id, int level, String player, double amount, double mult, long donation) {
        Punishes.P p = Punishes.get(id);
        if (p == null || p.game()) return;
        start(id, level, player, amount, mult, donation, null);
    }

    private static void start(String id, int level, String player, double amount, double mult, long donation, String who) {
        Config c = Config.get();
        Punishes.P p = Punishes.get(id);
        if (p == null) return;
        level = Math.max(0, Math.min(c.tiers.size() - 1, level));
        double dur = c.tiers.get(level).seconds * c.lengthScale * mult;
        long now = System.currentTimeMillis();

        if (player != null) StreamSession.punishmentStarted(id);
        if (p.game()) {
            Games.request(id.equals("spin_wheel") ? Games.Kind.WHEEL : Games.Kind.CASE, level, who == null ? player : who, player,
                    amount, donation, mult, 0, false);
            return;
        }
        if (p.extreme) {
            pendingExtreme = p;
            pendingUntil = now + extremeTotalMillis();
            if (player != null) StreamSession.extremeTriggered();
            return;
        }
        if (p.cat == Punishes.Cat.ACTION) {
            runAction(Minecraft.getInstance(), p);
            return;
        }
        if (id.equals("flashbang")) dur *= new double[]{1.2, 1.6, 2.2}[c.flashLevel]; // the white-out lingers
        while (ACTIVE.size() >= c.maxActive) ACTIVE.remove(0);
        // don't double up the same punishment
        ACTIVE.removeIf(a -> a.p.id.equals(id));
        Active a = new Active(p, now, now + (long) (dur * 1000), level);
        a.donor = player;
        a.donation = donation;
        ACTIVE.add(a);
        PunishSounds.started(Minecraft.getInstance(), id);
    }

    // ------------------------------------------------------------------ tick

    public static void tick(Minecraft mc) {
        tick++;
        Config c = Config.get();
        if (mc.player == null || mc.level == null) {
            ACTIVE.clear();
            pendingExtreme = null;
            restoreHud(mc);
            ControlState.clear();
            APPLIED.clear();
            applyFrontCamera(mc, false);
            applyLanguage(mc, false, 0);
            ScreenFx.clear();
            Games.cancelAll();
            QUEUE.clear();
            return;
        }
        long now = System.currentTimeMillis();

        // extreme countdown (a beep each second)
        if (pendingExtreme != null && now < pendingUntil) {
            long sec = (pendingUntil - now + 999) / 1000;
            if (sec != lastBeep) {
                lastBeep = sec;
                PunishSounds.event(mc, pendingExtreme.id, "beep");
            }
        }
        if (pendingExtreme != null && now >= pendingUntil) {
            Punishes.P p = pendingExtreme;
            pendingExtreme = null;
            runExtreme(mc, p);
            return;
        }

        ACTIVE.removeIf(a -> now >= a.end);
        StreamSession.tick(ACTIVE);
        Games.tick(mc);
        Giveaway.tick();

        // the queue: next donation once everything has finished (plus the gap)
        if (!QUEUE.isEmpty()) {
            if (running()) idleSince = 0;
            else {
                if (idleSince == 0) idleSince = now;
                if (now - idleSince >= QUEUE_GAPS[c.queueGap] * 1000L) {
                    idleSince = 0;
                    run(QUEUE.pollFirst(), 1.0, "up next  \u00b7  ");
                }
            }
        }
        if (comboUntil > 0 && now > comboUntil && ACTIVE.isEmpty()) comboCount = 0;

        boolean anyControl = false, wantHud = false, wantNausea = false;
        boolean wantMirror = false, wantFront = false, wantLanguage = false;
        int pixel = -1;
        boolean wantBlur = false, wantFish = false, wantWindow = false;
        drunkAngle = (float) (Math.sin(now / 420.0) * 0.8 + Math.sin(now / 137.0) * 0.4);
        ControlState.clear();

        for (Active a : ACTIVE) {
            PunishSounds.running(mc, a.p.id, ++a.ticks);
            switch (a.p.id) {
                // --- movement (KeyboardInputMixin) ---
                case "inverted_controls" -> {
                    ControlState.invert = true;
                    anyControl = true;
                }
                case "frozen" -> {
                    ControlState.freeze = true;
                    anyControl = true;
                }
                case "sluggish" -> {
                    ControlState.slow = new float[]{0.5f, 0.33f, 0.2f, 0.1f}[c.sluggishLevel];
                    anyControl = true;
                }
                case "drunk" -> {
                    float env = envelope(a, now, 1500, 1200);
                    float mult = new float[]{0.55f, 1f, 1.6f}[c.drunkLevel];
                    ControlState.stumble = drunkAngle * env * mult;
                    ControlState.drunk = Math.max(ControlState.drunk, env * mult);
                    wantNausea = true;
                    anyControl = true;
                }
                // --- camera & view (CameraMixin / GameRendererMixin) ---
                case "spin" -> mc.player.setYRot(mc.player.getYRot() + new float[]{3f, 7f, 14f, 30f}[c.spinLevel]);
                case "jitter" -> ControlState.shake = new float[]{1.6f, 3.2f, 6f}[c.jitterLevel] * envelope(a, now, 300, 400);
                case "glitch" -> {
                    // jerky back-and-forth snaps of the whole view
                    ControlState.slide = (float) (Math.sin(now / 85.0) * 12 + Math.sin(now / 37.0) * 5) * new float[]{0.5f, 1f, 1.8f}[c.glitchLevel];
                }
                case "tunnel_vision" -> ControlState.fovMul = Math.min(ControlState.fovMul, 1f - new float[]{0.3f, 0.6f, 0.72f, 0.82f}[c.tunnelLevel] * envelope(a, now, 900, 900));
                case "no_hud" -> wantHud = true;
                // --- real lag: actually stall the client thread in short bursts ---
                case "fake_lag" -> {
                    int[][] lag = {{24, 80, 120}, {16, 130, 220}, {10, 250, 350}}; // every N ticks, base ms, random ms
                    int[] L = lag[c.lagLevel];
                    if (tick % L[0] == 0) {
                        try {
                            Thread.sleep(L[1] + R.nextInt(L[2]));
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
                // --- new view punishments ---
                case "mirror" -> {
                    wantMirror = true;
                    ControlState.mirror = true;
                    anyControl = true;
                }
                case "upside_down" -> {
                    float env = envelope(a, now, 450, 450);
                    ControlState.upside = c.upsideMode == 1
                            ? (float) ((now - a.start) / 20.0 % 360.0) * env   // keeps rolling
                            : 180f * env * env * (3 - 2 * env);                  // flips over
                }
                case "front_camera" -> wantFront = true;
                case "blur" -> wantBlur = true;
                case "fisheye" -> wantFish = true;
                case "window" -> wantWindow = true;
                case "pixelated" -> {
                    int target = c.pixelRes;
                    if (c.pixelDegrade) {
                        double prog = Math.min(1, (now - a.start) / (0.6 * Math.max(1, a.end - a.start)));
                        target = (int) Math.round(prog * c.pixelRes);
                    }
                    pixel = Math.max(pixel, target);
                }
                // --- screen & sound ---
                case "language" -> wantLanguage = true;
                case "chipmunk" -> {
                    ControlState.chipmunk = c.chipmunkMode != 1;
                    ControlState.goats = c.chipmunkMode != 0;
                }
                // --- hotbar & items (the same actions as scrolling / pressing Q) ---
                case "possessed_hotbar" -> {
                    int every = new int[]{40, 18, 6}[c.possessedLevel];
                    if (tick % every == 0) {
                        mc.player.getInventory().setSelectedSlot(R.nextInt(9));
                        PunishSounds.event(mc, "possessed_hotbar", "hop");
                    }
                }
                case "hotbar_chaos" -> ControlState.hotbarChaos = true;
                case "butterfingers" -> {
                    if (tick % 30 == 0 && mc.player.canDropItems()) {
                        if (c.butterLevel == 2) {
                            List<Integer> full = new ArrayList<>();
                            for (int i = 0; i < 9; i++) if (!mc.player.getInventory().getItem(i).isEmpty()) full.add(i);
                            if (!full.isEmpty()) mc.player.getInventory().setSelectedSlot(full.get(R.nextInt(full.size())));
                        }
                        mc.player.drop(c.butterLevel == 1);
                        PunishSounds.event(mc, "butterfingers", "drop");
                    }
                }
                default -> { } // negative, disco_eyes, flashbang, blackout, fog = drawn by the overlay
            }
        }

        keepEffect(mc, MobEffects.NAUSEA, wantNausea);
        ScreenFx.apply(mc, wantMirror, pixel, c.pixelPalette, wantBlur ? c.blurLevel : -1, wantFish ? c.fisheyeLevel : -1,
                wantWindow ? c.windowSize : -1, c.windowSpeed);
        applyFrontCamera(mc, wantFront);
        applyLanguage(mc, wantLanguage, c.languageMode);
        if (ControlState.hotbarChaos && tick % 30 == 0) ControlState.hotbarSeed = R.nextLong();

        ControlState.active = anyControl;
        if (wantHud && !Games.showing()) hideHud(mc); // never hide a wheel / case that's on screen
        else restoreHud(mc);
    }

    /** 0 -> 1 over {@code inMs}, holds, then 1 -> 0 over the last {@code outMs} of a punishment. */
    static float envelope(Active a, long now, long inMs, long outMs) {
        float in = Math.min(1f, (now - a.start) / (float) inMs);
        float out = Math.min(1f, (a.end - now) / (float) outMs);
        return Math.max(0f, Math.min(in, out));
    }

    /**
     * Keeps a real effect on the player while wanted. It is given a long duration and only
     * topped up when it runs low: effects like nausea fade OUT once they are about to end, so
     * re-applying a short one every tick (what the old version did) never let the warp show.
     * It is removed the moment it is no longer wanted.
     */
    private static void keepEffect(Minecraft mc, Holder<MobEffect> eff, boolean want) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        if (want) {
            MobEffectInstance cur = p.getEffect(eff);
            if (cur == null || (!cur.isInfiniteDuration() && cur.getDuration() < 400)) {
                p.addEffect(new MobEffectInstance(eff, 1200, 0, false, false, false));
            }
            APPLIED.add(eff);
        } else if (APPLIED.remove(eff)) {
            p.removeEffect(eff);
        }
    }

    private static void hideHud(Minecraft mc) {
        if (!hudHidden) {
            savedHideGui = mc.options.hideGui;
            hudHidden = true;
        }
        mc.options.hideGui = true;
    }

    private static void restoreHud(Minecraft mc) {
        if (hudHidden) {
            mc.options.hideGui = savedHideGui;
            hudHidden = false;
        }
    }

    private static void runExtreme(Minecraft mc, Punishes.P p) {
        switch (p.id) {
            case "leave_server" -> mc.disconnectFromWorld(Component.literal("Punish FX: you got donated to."));
            case "close_game" -> mc.stop();
            default -> { }
        }
    }

    /** No non-extreme instant actions remain (nothing here may disconnect from a server). */
    private static void runAction(Minecraft mc, Punishes.P p) {
    }

    // ------------------------------------------------------------------ sound

    // ---- Front Camera: force third-person front view, then put back what you had ----
    private static CameraType savedCamera = null;

    private static void applyFrontCamera(Minecraft mc, boolean want) {
        if (want) {
            if (savedCamera == null) savedCamera = mc.options.getCameraType();
            if (mc.options.getCameraType() != CameraType.THIRD_PERSON_FRONT) mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        } else if (savedCamera != null) {
            mc.options.setCameraType(savedCamera);
            savedCamera = null;
        }
    }

    // ---- Language Swap: load a joke language straight into the game, no slow resource reload ----
    public static final String[] LANGUAGE_NAMES = {"Pirate", "Upside down", "LOLCAT", "Shakespeare", "Random"};
    private static final String[] LANGUAGE_CODES = {"en_pt", "en_ud", "lol_us", "enws"};
    private static String swappedTo = null;

    private static void applyLanguage(Minecraft mc, boolean want, int mode) {
        try {
            if (want && swappedTo == null) {
                String code = LANGUAGE_CODES[mode >= LANGUAGE_CODES.length ? R.nextInt(LANGUAGE_CODES.length) : mode];
                ClientLanguage lang = ClientLanguage.loadFrom(mc.getResourceManager(), List.of("en_us", code), false);
                Language.inject(lang);
                I18nAccessor.punishfx$setLanguage(lang);
                swappedTo = code;
            } else if (!want && swappedTo != null) {
                swappedTo = null;
                mc.getLanguageManager().onResourceManagerReload(mc.getResourceManager()); // back to your language
            }
        } catch (Throwable t) {
            swappedTo = null;
            PunishFxClient.LOG.warn("Punish FX: language swap failed", t);
        }
    }

    public static String swappedLanguage() {
        return swappedTo;
    }

    /** Looks up a sound by id (null if it doesn't exist). */
    public static SoundEvent soundEvent(String id) {
        Identifier rl = Identifier.tryParse(id);
        return rl == null ? null : BuiltInRegistries.SOUND_EVENT.getValue(rl);
    }

    /** How many sounds Punish FX has played (lets the tests check sounds without speakers). */
    public static int soundsPlayed = 0;

    public static void playSound(Minecraft mc, String id, float vol, float pitch) {
        if (!Config.get().sounds) return;
        soundsPlayed++;
        try {
            Identifier rl = Identifier.tryParse(id);
            if (rl == null) return;
            SoundEvent ev = BuiltInRegistries.SOUND_EVENT.getValue(rl);
            if (ev == null) ev = SoundEvent.createVariableRangeEvent(rl);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ev, Math.max(0.5f, Math.min(2f, pitch)), Math.max(0f, Math.min(1f, vol * (float) Config.get().volume))));
        } catch (Exception ignored) {
        }
    }

    public static List<String> activeNames() {
        List<String> out = new ArrayList<>();
        for (Active a : ACTIVE) out.add(a.p.name);
        return out;
    }
}
