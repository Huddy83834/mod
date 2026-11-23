package com.punishfx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Running totals of who has donated to you: all time, today, and this play session. */
public final class DonorBoard {
    public static final String[] PERIOD_NAMES = {"All time", "Today", "This stream"};

    public static final class Entry {
        public String name;
        public double total;
        public int count;
        public long last;

        Entry() {}

        Entry(String name) {
            this.name = name;
        }
    }

    /** What gets written to punishfx-topdonors.json. */
    private static final class Saved {
        List<Entry> allTime = new ArrayList<>();
        String todayDate = "";
        List<Entry> today = new ArrayList<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Entry> ALL = new LinkedHashMap<>();
    private static final Map<String, Entry> TODAY = new LinkedHashMap<>();
    private static final Map<String, Entry> SESSION = new LinkedHashMap<>();
    private static String todayDate = LocalDate.now().toString();

    private DonorBoard() {}

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("punishfx-topdonors.json");
    }

    public static void load() {
        ALL.clear();
        TODAY.clear();
        try {
            Path p = path();
            if (!Files.exists(p)) return;
            Saved s = GSON.fromJson(Files.readString(p), Saved.class);
            if (s == null) return;
            if (s.allTime != null) for (Entry e : s.allTime) if (e != null && e.name != null) ALL.put(key(e.name), e);
            if (s.today != null && LocalDate.now().toString().equals(s.todayDate)) {
                for (Entry e : s.today) if (e != null && e.name != null) TODAY.put(key(e.name), e);
            }
        } catch (Exception e) {
            PunishFxClient.LOG.warn("Punish FX: could not read the top donors file", e);
        }
    }

    public static void save() {
        try {
            Saved s = new Saved();
            s.allTime.addAll(ALL.values());
            s.todayDate = todayDate;
            s.today.addAll(TODAY.values());
            Files.writeString(path(), GSON.toJson(s));
        } catch (Exception e) {
            PunishFxClient.LOG.warn("Punish FX: could not save the top donors file", e);
        }
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static void rollDay() {
        String now = LocalDate.now().toString();
        if (!now.equals(todayDate)) {
            todayDate = now;
            TODAY.clear();
        }
    }

    /** A real donation arrived (tests and unknown senders don't count). */
    public static void record(String name, double amount) {
        if (name == null || name.isBlank() || name.equals(Detector.UNKNOWN) || amount <= 0) return;
        rollDay();
        long now = System.currentTimeMillis();
        for (Map<String, Entry> m : List.of(ALL, TODAY, SESSION)) {
            Entry e = m.computeIfAbsent(key(name), k -> new Entry(name));
            e.name = name; // keep the latest capitalisation
            e.total += amount;
            e.count++;
            e.last = now;
        }
        save();
    }

    /** The biggest donors for a period (0 all time, 1 today, 2 this session), largest first. */
    public static List<Entry> top(int period, int n) {
        rollDay();
        Map<String, Entry> m = period == 1 ? TODAY : period == 2 ? SESSION : ALL;
        List<Entry> list = new ArrayList<>(m.values());
        list.sort((a, b) -> Double.compare(b.total, a.total));
        return list.subList(0, Math.min(n, list.size()));
    }

    public static int size(int period) {
        rollDay();
        return (period == 1 ? TODAY : period == 2 ? SESSION : ALL).size();
    }

    /** Start "this session" fresh (used when a stream starts). */
    public static void resetSession() {
        SESSION.clear();
    }

    public static void reset() {
        ALL.clear();
        TODAY.clear();
        SESSION.clear();
        save();
    }
}
