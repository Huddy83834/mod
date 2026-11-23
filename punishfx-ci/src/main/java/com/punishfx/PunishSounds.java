package com.punishfx;

import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * A sound for every punishment: one when it starts, and for many a sound that repeats while it runs.
 * Each can be switched off on the Customize page (and all of them with "Punishment sounds").
 */
public final class PunishSounds {
    private record S(String id, float vol, float pitch) {}

    /** {@code loop} repeats every {@code every} ticks while it runs (null = none). */
    private record Def(String desc, S[] start, S loop, int every, boolean wobble) {}

    private static final Map<String, Def> DEFS = new HashMap<>();
    private static final Random R = new Random();

    private static S s(String id, float vol, float pitch) {
        return new S("minecraft:" + id, vol, pitch);
    }

    private static void def(String id, String desc, S[] start, S loop, int every, boolean wobble) {
        DEFS.put(id, new Def(desc, start, loop, every, wobble));
    }

    static {
        def("inverted_controls", "A warped whoosh, then wonky notes",
                new S[]{s("entity.illusioner.mirror_move", 0.8f, 0.7f)}, s("block.note_block.didgeridoo", 0.3f, 0.6f), 50, true);
        def("frozen", "Ice cracking, then shivering",
                new S[]{s("block.glass.break", 0.7f, 1.4f), s("entity.player.hurt_freeze", 0.8f, 1f)}, s("block.powder_snow.step", 0.5f, 1f), 40, false);
        def("sluggish", "A slow sticky squelch",
                new S[]{s("block.honey_block.slide", 0.9f, 0.6f)}, s("block.honey_block.step", 0.4f, 0.7f), 30, false);
        def("drunk", "A gulp, then burps",
                new S[]{s("entity.generic.drink", 0.9f, 0.9f)}, s("entity.player.burp", 0.8f, 0.85f), 70, true);
        def("spin", "A whirling whoosh",
                new S[]{s("entity.breeze.whirl", 1f, 1f)}, s("entity.breeze.idle_air", 0.5f, 1f), 50, true);
        def("jitter", "A rumble and a pounding beat",
                new S[]{s("block.basalt.break", 0.9f, 0.6f)}, s("block.note_block.basedrum", 0.35f, 0.6f), 8, false);
        def("glitch", "Digital bleeps and clicks",
                new S[]{s("block.note_block.bit", 0.8f, 0.5f)}, s("block.sculk_sensor.clicking", 0.25f, 1.9f), 12, false);
        def("tunnel_vision", "A pounding heartbeat",
                new S[]{s("block.beacon.deactivate", 0.7f, 1.2f)}, s("entity.warden.heartbeat", 0.9f, 1f), 24, false);
        def("mirror", "A glassy shimmer",
                new S[]{s("entity.illusioner.mirror_move", 1f, 1.2f), s("block.amethyst_block.chime", 1f, 1f)}, null, 0, false);
        def("upside_down", "Flapping wings as you flip",
                new S[]{s("entity.bat.takeoff", 0.8f, 1f)}, null, 0, false);
        def("front_camera", "A camera shutter",
                new S[]{s("item.spyglass.use", 1f, 1f), s("block.dispenser.dispense", 0.6f, 2f)}, null, 0, false);
        def("negative", "A deep electric hum",
                new S[]{s("block.respawn_anchor.charge", 0.8f, 0.6f)}, null, 0, false);
        def("disco_eyes", "Disco beats the whole time",
                new S[]{s("block.note_block.bell", 0.6f, 1f)}, s("block.note_block.bit", 0.3f, 1f), 5, true);
        def("flashbang", "A bang and a blast",
                new S[]{s("entity.generic.explode", 1f, 1.3f), s("entity.firework_rocket.blast", 1f, 0.6f)}, null, 0, false);
        def("blackout", "The lights switching off",
                new S[]{s("block.beacon.deactivate", 1f, 0.6f), s("block.lever.click", 0.8f, 0.5f)}, null, 0, false);
        def("fog", "Wind and rain",
                new S[]{s("weather.rain", 0.6f, 1f)}, s("weather.rain", 0.3f, 0.8f), 30, false);
        def("no_hud", "A whoosh as it vanishes",
                new S[]{s("ui.toast.out", 1f, 1f)}, null, 0, false);
        def("fake_lag", "A sputter as the game stalls",
                new S[]{s("block.redstone_torch.burnout", 0.7f, 1f)}, null, 0, false);
        def("pixelated", "8-bit bleeps",
                new S[]{s("block.note_block.bit", 0.7f, 0.7f), s("block.note_block.bit", 0.7f, 1.0f), s("block.note_block.bit", 0.7f, 1.4f)}, null, 0, false);
        def("language", "A parrot squawking",
                new S[]{s("entity.parrot.ambient", 1f, 1f)}, s("entity.parrot.ambient", 0.6f, 1f), 120, true);
        def("chipmunk", "A goat scream to start",
                new S[]{s("entity.goat.screaming.ambient", 1f, 1.4f)}, null, 0, false);
        def("possessed_hotbar", "Ghostly whispers and clicks",
                new S[]{s("entity.vex.ambient", 1f, 1f)}, null, 0, false);
        def("hotbar_chaos", "A teleport zap",
                new S[]{s("entity.enderman.teleport", 0.8f, 1.2f)}, null, 0, false);
        def("butterfingers", "A squish on every drop",
                new S[]{s("entity.slime.squish", 0.8f, 1f)}, null, 0, false);
        def("window", "A boing every time it hits an edge",
                new S[]{s("entity.shulker.close", 0.9f, 1f)}, null, 0, false);
        def("blur", "Underwater bubbles",
                new S[]{s("entity.player.splash", 0.7f, 0.8f)}, s("block.bubble_column.upwards_ambient", 0.6f, 1f), 40, false);
        def("fisheye", "A wobbly warp",
                new S[]{s("block.amethyst_block.resonate", 1f, 0.7f), s("entity.player.levelup", 0.3f, 0.5f)}, null, 0, false);
        def("spin_wheel", "Ticks as it spins, a bell when it stops", new S[0], null, 0, false);
        def("case_open", "The chest opening and the reel ticking", new S[0], null, 0, false);
        def("leave_server", "Countdown beeps", new S[0], null, 0, false);
        def("close_game", "Countdown beeps", new S[0], null, 0, false);
    }

    private PunishSounds() {}

    public static String desc(String id) {
        Def d = DEFS.get(id);
        return d == null ? "" : d.desc;
    }

    /** This punishment's sounds are switched on. */
    public static boolean on(String id) {
        Config c = Config.get();
        return c.sounds && c.punishSounds && (c.mutedSounds == null || !c.mutedSounds.contains(id));
    }

    public static void started(Minecraft mc, String id) {
        Def d = DEFS.get(id);
        if (d == null || !on(id) || mc.player == null) return;
        for (S s : d.start) PunishManager.playSound(mc, s.id, s.vol, s.pitch);
    }

    /** Called every tick for each running punishment. */
    public static void running(Minecraft mc, String id, long ticksIn) {
        Def d = DEFS.get(id);
        if (d == null || d.loop == null || !Config.get().loopSounds || !on(id) || ticksIn <= 0 || ticksIn % d.every != 0) return;
        float pitch = d.wobble ? d.loop.pitch * (0.85f + R.nextFloat() * 0.35f) : d.loop.pitch;
        if (id.equals("disco_eyes")) pitch = new float[]{0.6f, 0.75f, 0.9f, 1.2f, 1.5f}[R.nextInt(5)];
        PunishManager.playSound(mc, d.loop.id, d.loop.vol, pitch);
    }

    /** One-off moments: a hotbar hop, an item drop, the window hitting an edge... */
    public static void event(Minecraft mc, String id, String what) {
        if (!on(id) || !Config.get().loopSounds) return;
        switch (what) {
            case "hop" -> PunishManager.playSound(mc, "minecraft:ui.button.click", 0.25f, 1.6f);
            case "drop" -> PunishManager.playSound(mc, "minecraft:entity.slime.squish_small", 0.6f, 1f);
            case "bounce" -> PunishManager.playSound(mc, "minecraft:entity.slime.jump", 0.6f, 1.3f + R.nextFloat() * 0.3f);
            case "corner" -> PunishManager.playSound(mc, "minecraft:ui.toast.challenge_complete", 0.7f, 1.2f);
            case "beep" -> PunishManager.playSound(mc, "minecraft:block.note_block.pling", 0.8f, 1.6f);
            default -> { }
        }
    }
}
