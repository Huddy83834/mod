package com.punishfx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Goals: a bar that fills up as money comes in, with your goals along it ("$10M: I jump in lava").
 * When the bar passes one it celebrates on screen and (optionally) does something - spins the wheel,
 * opens a case, starts a giveaway or runs a punishment. Progress lives in its own file so presets don't reset it.
 */
public final class Goals {
    public static final String[] POS_NAMES = {"Top", "Bottom", "Left side", "Right side"};
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final class State {
        double progress;
        List<Double> done = new ArrayList<>();
    }

    private static State state = new State();
    /** The goal being celebrated on screen right now. */
    public static Config.Goal celebrating;
    public static long celebrateAt, celebrateUntil;

    private Goals() {}

    // ------------------------------------------------------------------ what a goal can also do

    public static List<String> actionIds() {
        List<String> ids = new ArrayList<>(List.of("", "#wheel", "#case", "#giveaway"));
        for (Punishes.P p : Punishes.all()) if (!p.game() && !p.extreme) ids.add(p.id);
        return ids;
    }

    public static String actionName(String id) {
        return switch (id == null ? "" : id) {
            case "" -> "Nothing";
            case "#wheel" -> "Spin the wheel";
            case "#case" -> "Open a case";
            case "#giveaway" -> "Start a giveaway";
            default -> Punishes.name(id);
        };
    }

    // ------------------------------------------------------------------ saving

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("punishfx-goals.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (!Files.exists(p)) return;
            State s = GSON.fromJson(Files.readString(p), State.class);
            if (s != null) {
                if (s.done == null) s.done = new ArrayList<>();
                state = s;
            }
        } catch (Exception e) {
            PunishFxClient.LOG.warn("Punish FX: could not read goals", e);
        }
    }

    private static void save() {
        try {
            Files.writeString(path(), GSON.toJson(state));
        } catch (Exception e) {
            PunishFxClient.LOG.warn("Punish FX: could not save goals", e);
        }
    }

    // ------------------------------------------------------------------ progress

    public static double progress() {
        return state.progress;
    }

    /** Your goals with an amount, smallest first. */
    public static List<Config.Goal> sorted() {
        List<Config.Goal> out = new ArrayList<>();
        for (Config.Goal g : Config.get().goals) if (g.amount > 0) out.add(g);
        out.sort((a, b) -> Double.compare(a.amount, b.amount));
        return out;
    }

    /** The end of the bar: the biggest goal. */
    public static double max() {
        double m = 0;
        for (Config.Goal g : sorted()) m = Math.max(m, g.amount);
        return m <= 0 ? 1 : m;
    }

    public static boolean done(Config.Goal g) {
        return state.done.contains(g.amount) || state.progress >= g.amount;
    }

    /** The next goal not reached yet, or null. */
    public static Config.Goal next() {
        for (Config.Goal g : sorted()) if (state.progress < g.amount) return g;
        return null;
    }

    /** Money came in (every real donation and paid giveaway entry). */
    public static void add(double amount) {
        if (amount <= 0) return;
        double before = state.progress;
        state.progress += amount;
        for (Config.Goal g : sorted()) {
            if (before < g.amount && state.progress >= g.amount && !state.done.contains(g.amount)) {
                state.done.add(g.amount);
                reach(g);
            }
        }
        save();
    }

    public static void reset() {
        state.progress = 0;
        state.done.clear();
        celebrating = null;
        save();
    }

    public static void streamStarted() {
        if (Config.get().goalResetOnStream) reset();
    }

    /** Celebrate, then do the goal's extra thing. */
    private static void reach(Config.Goal g) {
        long now = System.currentTimeMillis();
        celebrating = g;
        celebrateAt = now;
        celebrateUntil = now + 5500;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PunishManager.playSound(mc, "minecraft:ui.toast.challenge_complete", 1f, 1f);
        PunishManager.playSound(mc, "minecraft:entity.firework_rocket.large_blast", 0.8f, 1f);
        Config c = Config.get();
        int level = Math.max(0, c.tierFor(g.amount));
        switch (g.action == null ? "" : g.action) {
            case "" -> { }
            case "#wheel" -> PunishManager.force("spin_wheel", level);
            case "#case" -> PunishManager.force("case_open", level);
            case "#giveaway" -> {
                if (!Giveaway.live()) Giveaway.start();
            }
            default -> PunishManager.force(g.action, level);
        }
    }
}
