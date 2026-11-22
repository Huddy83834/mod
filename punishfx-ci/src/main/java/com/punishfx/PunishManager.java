package com.punishfx;

import net.minecraft.client.Minecraft;
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
        return Config.get().confirmExtreme ? 5000 : 1200;
    }

    public static Punishes.P pendingExtreme() {
        return pendingExtreme;
    }

    /** Cancel key / command: stop the pending extreme and clear running punishments. */
    public static void cancelAll() {
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

        // Announce who paid and how much, once per donation.
        if (c.announce) {
            List<String> names = new ArrayList<>();
            for (String id : ids) names.add(Punishes.name(id));
            donor = (player == null || player.isBlank()) ? "Someone" : player;
            donorAmount = Money.full(amount);
            donorSub = String.join("  ·  ", names);
            donorUntil = System.currentTimeMillis() + 3400;
            Minecraft mc = Minecraft.getInstance();
            if (c.sounds && mc.player != null) {
                playSound(mc, "minecraft:block.note_block.pling", 0.8f, 0.6f);
                playSound(mc, "minecraft:entity.villager.no", 0.7f, 0.8f);
            }
        }
        for (String id : ids) start(id, level, player, amount);
    }

    /** Force a specific punishment (test / command). */
    public static void force(String id, int level) {
        Config c = Config.get();
        int l = Math.max(0, Math.min(c.tiers.size() - 1, level));
        start(id, l, Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getGameProfile().name() : "You", c.tiers.get(l).minAmount);
    }

    private static void start(String id, int level, String player, double amount) {
        Config c = Config.get();
        Punishes.P p = Punishes.get(id);
        if (p == null) return;
        double dur = c.tiers.get(level).seconds * c.lengthScale;
        long now = System.currentTimeMillis();

        if (p.extreme) {
            pendingExtreme = p;
            pendingUntil = now + (c.confirmExtreme ? 5000 : 1200);
            return;
        }
        if (p.cat == Punishes.Cat.ACTION) {
            runAction(Minecraft.getInstance(), p);
            return;
        }
        if (!c.stack) ACTIVE.clear();
        while (ACTIVE.size() >= c.maxActive) ACTIVE.remove(0);
        // don't double up the same punishment
        ACTIVE.removeIf(a -> a.p.id.equals(id));
        Active a = new Active(p, now, now + (long) (dur * 1000), level);
        ACTIVE.add(a);
    }

    // ------------------------------------------------------------------ tick

    public static void tick(Minecraft mc) {
        tick++;
        if (mc.player == null || mc.level == null) {
            ACTIVE.clear();
            pendingExtreme = null;
            restoreHud(mc);
            applyFov(mc, false);
            ControlState.clear();
            APPLIED.clear();
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

        boolean anyControl = false, wantHud = false, wantTunnel = false;
        boolean wNausea = false;
        drunkAngle = (float) (Math.sin(now / 420.0) * 0.8 + Math.sin(now / 137.0) * 0.4);
        ControlState.clear();

        for (Active a : ACTIVE) {
            switch (a.p.id) {
                // --- movement (input mixin) ---
                case "inverted_controls" -> {
                    ControlState.invert = true;
                    anyControl = true;
                }
                case "frozen" -> {
                    ControlState.freeze = true;
                    anyControl = true;
                }
                case "sluggish" -> {
                    ControlState.slow = 0.35f;
                    anyControl = true;
                }
                case "drunk" -> {
                    ControlState.stumble = drunkAngle;
                    wNausea = true;
                    anyControl = true;
                }
                // --- effect ---
                case "nausea" -> wNausea = true;
                // --- camera / view ---
                case "spin" -> mc.player.setYRot(mc.player.getYRot() + 7f);
                case "jitter" -> {
                    ControlState.shake = 3.2f;
                    anyControl = true;
                }
                case "glitch" -> {
                    // snap the view left/right in jerky back-and-forth swings
                    ControlState.slide = (float) (Math.sin(now / 85.0) * 12 + Math.sin(now / 37.0) * 5);
                    anyControl = true;
                    if (tick % 12 == 0) playSound(mc, "minecraft:block.sculk_sensor.clicking", 0.25f, 1.9f);
                }
                case "no_hud" -> wantHud = true;
                case "tunnel_vision" -> wantTunnel = true;
                // --- real lag: actually stall the client thread in short bursts ---
                case "fake_lag" -> {
                    if (tick % 16 == 0) {
                        try {
                            Thread.sleep(130 + R.nextInt(220));
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
                default -> { } // negative, disco_eyes, flashbang, blackout, fog = overlay only
            }
        }

        applyOrRemove(mc, MobEffects.NAUSEA, wNausea, 0);

        ControlState.active = anyControl;
        if (wantHud) hideHud(mc);
        else restoreHud(mc);
        applyFov(mc, wantTunnel);
    }

    /** Keeps a real Minecraft effect applied (fresh every tick, no HUD icon) while wanted, and removes it the moment it is not. */
    private static void applyOrRemove(Minecraft mc, Holder<MobEffect> eff, boolean want, int amp) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        if (want) {
            p.addEffect(new MobEffectInstance(eff, 25, amp, false, false, false));
            APPLIED.add(eff);
        } else if (APPLIED.contains(eff)) {
            p.removeEffect(eff);
            APPLIED.remove(eff);
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

    // ---- Tunnel vision: genuinely clamp the player's FOV, then restore it ----
    private static boolean fovChanged;
    private static int savedFov;

    private static void applyFov(Minecraft mc, boolean want) {
        try {
            if (want) {
                if (!fovChanged) {
                    savedFov = mc.options.fov().get();
                    fovChanged = true;
                }
                mc.options.fov().set(30);
            } else if (fovChanged) {
                mc.options.fov().set(savedFov);
                fovChanged = false;
            }
        } catch (Throwable ignored) {
            fovChanged = false;
        }
    }

    // ------------------------------------------------------------------ sound

    public static void playSound(Minecraft mc, String id, float vol, float pitch) {
        if (!Config.get().sounds) return;
        try {
            Identifier rl = Identifier.tryParse(id);
            if (rl == null) return;
            SoundEvent ev = BuiltInRegistries.SOUND_EVENT.getValue(rl);
            if (ev == null) ev = SoundEvent.createVariableRangeEvent(rl);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ev, Math.max(0.5f, Math.min(2f, pitch)), Math.max(0f, Math.min(1f, vol))));
        } catch (Exception ignored) {
        }
    }

    public static List<String> activeNames() {
        List<String> out = new ArrayList<>();
        for (Active a : ACTIVE) out.add(a.p.name);
        return out;
    }
}
