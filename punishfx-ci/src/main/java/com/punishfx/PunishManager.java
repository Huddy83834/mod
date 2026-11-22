package com.punishfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
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
import java.util.Random;
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
    private static float drunkAngle = 0;

    // extreme (leave/close) pending countdown
    private static Punishes.P pendingExtreme;
    private static long pendingUntil;

    private static boolean hudHidden;
    private static boolean savedHideGui;

    public static String banner = "";
    public static long bannerUntil = 0;

    private PunishManager() {}

    public static List<Active> active() {
        return ACTIVE;
    }

    public static boolean extremePending() {
        return pendingExtreme != null;
    }

    public static long extremeSecondsLeft() {
        return Math.max(0, (pendingUntil - System.currentTimeMillis() + 999) / 1000);
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
            announce(p, "⚠ " + p.name.toUpperCase());
            return;
        }
        if (p.cat == Punishes.Cat.ACTION) {
            // instant, non-extreme actions (kick to menu / fake ban)
            announce(p, p.name);
            runAction(Minecraft.getInstance(), p);
            return;
        }
        if (!c.stack) ACTIVE.clear();
        while (ACTIVE.size() >= c.maxActive) ACTIVE.remove(0);
        // don't double up the same punishment
        ACTIVE.removeIf(a -> a.p.id.equals(id));
        Active a = new Active(p, now, now + (long) (dur * 1000), level);
        ACTIVE.add(a);
        announce(p, p.name);
    }

    private static void announce(Punishes.P p, String text) {
        Config c = Config.get();
        if (!c.announce) return;
        banner = text;
        bannerUntil = System.currentTimeMillis() + 2600;
        Minecraft mc = Minecraft.getInstance();
        if (c.sounds && mc.player != null) {
            playSound(mc, "minecraft:block.note_block.pling", 0.8f, 0.6f);
            playSound(mc, "minecraft:entity.villager.no", 0.7f, 0.8f);
        }
    }

    // ------------------------------------------------------------------ tick

    public static void tick(Minecraft mc) {
        tick++;
        if (mc.player == null || mc.level == null) {
            ACTIVE.clear();
            pendingExtreme = null;
            restoreHud(mc);
            ControlState.clear();
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

        boolean anyControl = false, wantHud = false;
        drunkAngle = (float) (Math.sin(now / 420.0) * 0.8 + Math.sin(now / 137.0) * 0.4);
        ControlState.clear();

        for (Active a : ACTIVE) {
            switch (a.p.id) {
                case "inverted_controls" -> anyControl = true;
                case "frozen" -> {
                    ControlState.freeze = true;
                    anyControl = true;
                }
                case "random_jumps" -> {
                    anyControl = true;
                    if (tick % 15 == 0) ControlState.jumpPulse = true;
                }
                case "drunk" -> {
                    anyControl = true;
                    ControlState.stumble = drunkAngle;
                    refreshEffect(mc, MobEffects.NAUSEA, a, 0);
                }
                case "nausea" -> refreshEffect(mc, MobEffects.NAUSEA, a, 0);
                case "blind" -> refreshEffect(mc, MobEffects.BLINDNESS, a, 0);
                case "darkness" -> refreshEffect(mc, MobEffects.DARKNESS, a, 0);
                case "sluggish" -> {
                    refreshEffect(mc, MobEffects.SLOWNESS, a, 3);
                    refreshEffect(mc, MobEffects.MINING_FATIGUE, a, 2);
                    refreshEffect(mc, MobEffects.WEAKNESS, a, 1);
                }
                case "spin" -> mc.player.setYRot(mc.player.getYRot() + 7f);
                case "vertigo" -> {
                    mc.player.setXRot((float) (Math.sin(now / 260.0) * 35));
                    mc.player.setYRot(mc.player.getYRot() + (float) Math.sin(now / 500.0) * 1.5f);
                }
                case "no_hud" -> wantHud = true;
                case "jitter" -> mc.player.setYRot(mc.player.getYRot() + (R.nextFloat() - 0.5f) * 1.1f);
                case "earthquake" -> {
                    if (tick % 8 == 0) playSound(mc, "minecraft:entity.generic.explode", 0.3f, 0.6f);
                    mc.player.setYRot(mc.player.getYRot() + (R.nextFloat() - 0.5f) * 2.4f);
                }
                case "heartbeat" -> {
                    if (tick % 16 == 0) playSound(mc, "minecraft:entity.warden.heartbeat", 1f, 1f);
                }
                case "static", "glitch" -> {
                    if (tick % 10 == 0) playSound(mc, "minecraft:block.sculk_sensor.clicking", 0.3f, 1.8f);
                }
                case "fake_lag" -> {
                    if (tick % 20 == 0) playSound(mc, "minecraft:block.note_block.bass", 0.3f, 0.5f);
                }
                case "fog", "sick" -> refreshEffect(mc, MobEffects.DARKNESS, a, 0);
                case "fake_crash" -> {
                    if (a.progress() < 0.05f && !a.fired) {
                        a.fired = true;
                        playSound(mc, "minecraft:block.anvil.land", 0.6f, 0.5f);
                    }
                }
                default -> { }
            }
        }

        ControlState.active = anyControl;
        if (wantHud) hideHud(mc);
        else restoreHud(mc);
    }

    private static void refreshEffect(Minecraft mc, Holder<MobEffect> eff, Active a, int amp) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        int ticksLeft = (int) Math.max(10, (a.end - System.currentTimeMillis()) / 50);
        if (tick % 10 == 0 || !p.hasEffect(eff)) {
            p.addEffect(new MobEffectInstance(eff, ticksLeft + 40, amp, false, false, false));
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

    private static void runAction(Minecraft mc, Punishes.P p) {
        switch (p.id) {
            case "kick_title" -> {
                mc.disconnectFromWorld(Component.literal("Punish FX"));
                mc.setScreenAndShow(new TitleScreen());
            }
            case "fake_ban" -> {
                mc.disconnectFromWorld(Component.literal("Punish FX"));
                mc.setScreenAndShow(new FakeBanScreen());
            }
            default -> { }
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
