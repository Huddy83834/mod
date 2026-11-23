package com.punishfx;

import com.punishfx.mixin.GameRendererAccessor;
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
    private static long pendingUntil;

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

    private PunishManager() {}

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
        if (pendingExtreme != null || !ACTIVE.isEmpty()) StreamSession.cancelled();
        pendingExtreme = null;
        ACTIVE.clear();
        ControlState.clear();
        banner = "Punishments cancelled.";
        bannerUntil = System.currentTimeMillis() + 1500;
    }

    /** A donation was detected (or a test/command). */
    public static void hit(double amount, String player, int level, boolean test) {
        Config c = Config.get();
        if (level < 0 || level >= c.tiers.size()) return;
        Config.Tier t = c.tiers.get(level);
        List<String> ids = Punishes.pick(t, Math.max(1, t.count));
        if (ids.isEmpty()) return;
        long now = System.currentTimeMillis();
        // cooldown: a donation soon after the last punishment only shows the banner
        boolean cooling = !test && c.cooldownSeconds > 0 && now - lastPunishAt < c.cooldownSeconds * 1000L;

        // Announce who paid and how much, once per donation.
        if (c.announce) {
            List<String> names = new ArrayList<>();
            for (String id : ids) names.add(Punishes.name(id));
            donor = (player == null || player.isBlank()) ? "Someone" : player;
            donorAmount = Money.full(amount);
            donorSub = cooling ? "on cooldown, no punishment" : String.join("  ·  ", names);
            donorStart = now;
            donorUntil = now + (long) (c.bannerSeconds * 1000);
            Minecraft mc = Minecraft.getInstance();
            if (c.sounds && mc.player != null) {
                playSound(mc, "minecraft:block.note_block.pling", 0.8f, 0.6f);
                playSound(mc, "minecraft:entity.villager.no", 0.7f, 0.8f);
            }
        }
        if (cooling) {
            if (!test) StreamSession.cooldownSkip();
            return;
        }
        lastPunishAt = now;
        for (String id : ids) start(id, level, test ? null : player, amount);
    }

    /** Force a specific punishment (test / command). */
    public static void force(String id, int level) {
        Config c = Config.get();
        int l = Math.max(0, Math.min(c.tiers.size() - 1, level));
        start(id, l, null, c.tiers.get(l).minAmount);
    }

    private static void start(String id, int level, String player, double amount) {
        Config c = Config.get();
        Punishes.P p = Punishes.get(id);
        if (p == null) return;
        double dur = c.tiers.get(level).seconds * c.lengthScale;
        long now = System.currentTimeMillis();

        if (player != null) StreamSession.punishmentStarted(id);
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
        if (!c.stack) ACTIVE.clear();
        while (ACTIVE.size() >= c.maxActive) ACTIVE.remove(0);
        // don't double up the same punishment
        ACTIVE.removeIf(a -> a.p.id.equals(id));
        Active a = new Active(p, now, now + (long) (dur * 1000), level);
        a.donor = player;
        ACTIVE.add(a);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            switch (id) {
                case "flashbang" -> {
                    playSound(mc, "minecraft:entity.generic.explode", 1f, 1.3f);
                    playSound(mc, "minecraft:entity.firework_rocket.blast", 1f, 0.6f);
                }
                case "drunk" -> playSound(mc, "minecraft:entity.generic.drink", 0.9f, 0.9f);
                default -> { }
            }
        }
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
            applyMirror(mc, false);
            return;
        }
        long now = System.currentTimeMillis();

        // extreme countdown
        if (pendingExtreme != null && now >= pendingUntil) {
            Punishes.P p = pendingExtreme;
            pendingExtreme = null;
            runExtreme(mc, p);
            return;
        }

        ACTIVE.removeIf(a -> now >= a.end);
        StreamSession.tick(ACTIVE);

        boolean anyControl = false, wantHud = false, wantNausea = false;
        boolean wantMirror = false, wantFront = false, wantLanguage = false;
        drunkAngle = (float) (Math.sin(now / 420.0) * 0.8 + Math.sin(now / 137.0) * 0.4);
        ControlState.clear();

        for (Active a : ACTIVE) {
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
                    if (tick % 70 == 35) playSound(mc, "minecraft:entity.player.burp", 0.8f, 0.8f + R.nextFloat() * 0.3f);
                }
                // --- camera & view (CameraMixin / GameRendererMixin) ---
                case "spin" -> mc.player.setYRot(mc.player.getYRot() + new float[]{3f, 7f, 14f, 30f}[c.spinLevel]);
                case "jitter" -> ControlState.shake = new float[]{1.6f, 3.2f, 6f}[c.jitterLevel] * envelope(a, now, 300, 400);
                case "glitch" -> {
                    // jerky back-and-forth snaps of the whole view
                    ControlState.slide = (float) (Math.sin(now / 85.0) * 12 + Math.sin(now / 37.0) * 5) * new float[]{0.5f, 1f, 1.8f}[c.glitchLevel];
                    if (tick % 12 == 0) playSound(mc, "minecraft:block.sculk_sensor.clicking", 0.25f, 1.9f);
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
                // --- screen & sound ---
                case "language" -> wantLanguage = true;
                case "chipmunk" -> {
                    ControlState.chipmunk = c.chipmunkMode != 1;
                    ControlState.goats = c.chipmunkMode != 0;
                }
                // --- hotbar & items (the same actions as scrolling / pressing Q) ---
                case "possessed_hotbar" -> {
                    int every = new int[]{40, 18, 6}[c.possessedLevel];
                    if (tick % every == 0) mc.player.getInventory().setSelectedSlot(R.nextInt(9));
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
                    }
                }
                default -> { } // negative, disco_eyes, flashbang, blackout, fog = drawn by the overlay
            }
        }

        keepEffect(mc, MobEffects.NAUSEA, wantNausea);
        applyMirror(mc, wantMirror);
        applyFrontCamera(mc, wantFront);
        applyLanguage(mc, wantLanguage, c.languageMode);
        if (ControlState.hotbarChaos && tick % 30 == 0) ControlState.hotbarSeed = R.nextLong();

        ControlState.active = anyControl;
        if (wantHud) hideHud(mc);
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

    // ---- Mirror World: our own post-processing effect (assets/punishfx/post_effect/mirror.json) ----
    private static final Identifier MIRROR = Identifier.fromNamespaceAndPath("punishfx", "mirror");

    private static void applyMirror(Minecraft mc, boolean want) {
        try {
            boolean on = MIRROR.equals(mc.gameRenderer.currentPostEffect());
            if (want && !on) ((GameRendererAccessor) mc.gameRenderer).punishfx$setPostEffect(MIRROR);
            else if (!want && on) mc.gameRenderer.clearPostEffect();
        } catch (Throwable t) {
            PunishFxClient.LOG.warn("Punish FX: mirror effect failed", t);
        }
    }

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

    public static void playSound(Minecraft mc, String id, float vol, float pitch) {
        if (!Config.get().sounds) return;
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
