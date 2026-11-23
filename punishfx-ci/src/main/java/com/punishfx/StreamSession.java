package com.punishfx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Stream mode: everything that happens between "start stream" and "end stream", so it can be
 * turned into a recap. Saved to disk on every change so a crash mid-stream loses nothing.
 */
public final class StreamSession {
    /** One donation during the stream. */
    public static final class Donation {
        public long t;
        public String name;
        public double amount;
        public int level;

        Donation() {}

        Donation(long t, String name, double amount, int level) {
            this.t = t;
            this.name = name;
            this.amount = amount;
            this.level = level;
        }
    }

    /** A whole stream. */
    public static final class Record {
        public long start, end;
        public List<Donation> donations = new ArrayList<>();
        public Map<String, Long> punishMs = new HashMap<>();      // punishment id -> ms it was running
        public Map<String, Integer> punishCount = new HashMap<>(); // punishment id -> times it started
        public Map<String, Long> donorChaosMs = new HashMap<>();   // donor -> ms of punishment they caused
        public int cancels, extremesTriggered, extremesCancelled, cooldownSkips;
        public long punishedMs; // wall time with at least one punishment running

        public long duration() {
            return Math.max(1, (end > 0 ? end : System.currentTimeMillis()) - start);
        }

        public double total() {
            double t = 0;
            for (Donation d : donations) t += d.amount;
            return t;
        }

        /** Donors by total, biggest first. */
        public List<Map.Entry<String, Double>> donors() {
            Map<String, Double> m = new LinkedHashMap<>();
            Map<String, String> display = new HashMap<>();
            for (Donation d : donations) {
                String k = d.name.toLowerCase(Locale.ROOT);
                display.put(k, d.name);
                m.merge(k, d.amount, Double::sum);
            }
            List<Map.Entry<String, Double>> out = new ArrayList<>();
            for (var e : m.entrySet()) out.add(Map.entry(display.get(e.getKey()), e.getValue()));
            out.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            return out;
        }

        public Donation biggest() {
            Donation best = null;
            for (Donation d : donations) if (best == null || d.amount > best.amount) best = d;
            return best;
        }

        /** The 5-minute window that brought in the most money: {startMs, total}. */
        public double[] busiest() {
            double best = 0;
            long bestT = start;
            for (int i = 0; i < donations.size(); i++) {
                double sum = 0;
                long from = donations.get(i).t;
                for (int j = i; j < donations.size() && donations.get(j).t - from <= 5 * 60_000; j++) sum += donations.get(j).amount;
                if (sum > best) {
                    best = sum;
                    bestT = from;
                }
            }
            return new double[]{bestT, best};
        }

        public String mostUsed() {
            String best = null;
            int n = 0;
            for (var e : punishCount.entrySet()) if (e.getValue() > n) {
                n = e.getValue();
                best = e.getKey();
            }
            return best;
        }

        public String chaosAgent() {
            String best = null;
            long ms = 0;
            for (var e : donorChaosMs.entrySet()) if (e.getValue() > ms) {
                ms = e.getValue();
                best = e.getKey();
            }
            return best;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Record current = null;
    private static final List<Record> HISTORY = new ArrayList<>();
    private static long lastTickAt = 0, lastSaveAt = 0;

    private StreamSession() {}

    private static Path dir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    public static boolean live() {
        return current != null;
    }

    public static Record current() {
        return current;
    }

    public static List<Record> history() {
        return HISTORY;
    }

    public static void load() {
        HISTORY.clear();
        try {
            Path h = dir().resolve("punishfx-streams.json");
            if (Files.exists(h)) {
                List<Record> list = GSON.fromJson(Files.readString(h), new TypeToken<List<Record>>() {}.getType());
                if (list != null) HISTORY.addAll(list);
            }
            Path c = dir().resolve("punishfx-stream-live.json");
            if (Files.exists(c)) current = GSON.fromJson(Files.readString(c), Record.class); // resume after a crash
        } catch (Exception e) {
            PunishFxClient.LOG.warn("Punish FX: could not read stream history", e);
        }
    }

    private static void saveLive() {
        try {
            Path c = dir().resolve("punishfx-stream-live.json");
            if (current == null) Files.deleteIfExists(c);
            else Files.writeString(c, GSON.toJson(current));
        } catch (Exception ignored) {
        }
    }

    private static void saveHistory() {
        try {
            while (HISTORY.size() > 50) HISTORY.remove(0);
            Files.writeString(dir().resolve("punishfx-streams.json"), GSON.toJson(HISTORY));
        } catch (Exception e) {
            PunishFxClient.LOG.warn("Punish FX: could not save stream history", e);
        }
    }

    public static void start() {
        if (current != null) return;
        current = new Record();
        current.start = System.currentTimeMillis();
        if (Config.get().streamFreshBoard) DonorBoard.resetSession();
        saveLive();
    }

    /** Ends the stream and returns its record (or null if not live). */
    public static Record stop() {
        if (current == null) return null;
        Record r = current;
        r.end = System.currentTimeMillis();
        current = null;
        HISTORY.add(r);
        saveHistory();
        saveLive();
        return r;
    }

    public static void clearHistory() {
        HISTORY.clear();
        saveHistory();
    }

    // ---- events (ignored when not live) ----

    public static void donation(String name, double amount, int level) {
        if (current == null || name == null) return;
        current.donations.add(new Donation(System.currentTimeMillis(), name, amount, level));
        saveLive();
    }

    public static void punishmentStarted(String id) {
        if (current == null) return;
        current.punishCount.merge(id, 1, Integer::sum);
        saveLive();
    }

    public static void cancelled() {
        if (current != null) current.cancels++;
    }

    public static void extremeTriggered() {
        if (current != null) current.extremesTriggered++;
    }

    public static void extremeCancelled() {
        if (current != null) current.extremesCancelled++;
    }

    public static void cooldownSkip() {
        if (current != null) current.cooldownSkips++;
    }

    /** Called every client tick with what's running, to add up punishment time. */
    public static void tick(List<PunishManager.Active> active) {
        long now = System.currentTimeMillis();
        long dt = lastTickAt == 0 ? 50 : Math.min(250, now - lastTickAt);
        lastTickAt = now;
        if (current == null || active.isEmpty()) return;
        current.punishedMs += dt;
        for (PunishManager.Active a : active) {
            current.punishMs.merge(a.p.id, dt, Long::sum);
            if (a.donor != null) current.donorChaosMs.merge(a.donor, dt, Long::sum);
        }
        if (now - lastSaveAt > 10_000) { // keep the crash-safe copy fresh
            lastSaveAt = now;
            saveLive();
        }
    }
}
