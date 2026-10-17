package com.lsstocks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Stores price history per item and computes stock-style stats. */
public class PriceTracker {
    public static final String DEFAULT_REGEX =
            "(?i)(?:price|cost|value|buy|sell)\\s*:?\\s*\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kKmM]?)";
    private static final int MAX_POINTS = 500;
    private static final long MIN_SAMPLE_GAP_MS = 60_000;
    private static final String BARS = "▁▂▃▄▅▆▇█";

    public static class Point {
        public long t;
        public double p;
        public Point(long t, double p) { this.t = t; this.p = p; }
    }

    private static class Data {
        Map<String, List<Point>> history = new LinkedHashMap<>();
        Set<String> watched = new LinkedHashSet<>();
        String regex = DEFAULT_REGEX;
        boolean hud = true;
        boolean scan = true;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("lsstocks.json");
    private Data data = new Data();
    private final Map<String, Long> lastSeen = new HashMap<>();

    public void load() {
        try {
            if (Files.exists(file)) {
                Data d = GSON.fromJson(Files.readString(file), Data.class);
                if (d != null) {
                    if (d.history == null) d.history = new LinkedHashMap<>();
                    if (d.watched == null) d.watched = new LinkedHashSet<>();
                    if (d.regex == null) d.regex = DEFAULT_REGEX;
                    data = d;
                }
            }
        } catch (Exception e) {
            StocksClient.LOGGER.warn("Could not load price data", e);
        }
    }

    public void save() {
        try {
            Files.writeString(file, GSON.toJson(data));
        } catch (IOException e) {
            StocksClient.LOGGER.warn("Could not save price data", e);
        }
    }

    public static String normalize(String id) {
        String s = id.toLowerCase(Locale.ROOT).trim();
        if (s.startsWith("minecraft:")) s = s.substring(10);
        return s.replace(' ', '_');
    }

    /** Record a price. Skips noisy duplicates (same price within the sample gap). */
    public boolean record(String item, double price) {
        String key = normalize(item);
        long now = System.currentTimeMillis();
        List<Point> list = data.history.computeIfAbsent(key, k -> new ArrayList<>());
        if (!list.isEmpty()) {
            Point last = list.get(list.size() - 1);
            if (last.p == price && now - last.t < MIN_SAMPLE_GAP_MS) return false;
        }
        list.add(new Point(now, price));
        while (list.size() > MAX_POINTS) list.remove(0);
        lastSeen.put(key, now);
        save();
        return true;
    }

    public OptionalDouble latest(String item) {
        List<Point> l = data.history.get(normalize(item));
        if (l == null || l.isEmpty()) return OptionalDouble.empty();
        return OptionalDouble.of(l.get(l.size() - 1).p);
    }

    /** Percent change between the latest price and the one before it. */
    public OptionalDouble changePct(String item) {
        List<Point> l = data.history.get(normalize(item));
        if (l == null || l.size() < 2) return OptionalDouble.empty();
        double prev = l.get(l.size() - 2).p;
        double cur = l.get(l.size() - 1).p;
        if (prev == 0) return OptionalDouble.empty();
        return OptionalDouble.of((cur - prev) / prev * 100.0);
    }

    public double high(String item) {
        return history(item).stream().mapToDouble(p -> p.p).max().orElse(0);
    }

    public double low(String item) {
        return history(item).stream().mapToDouble(p -> p.p).min().orElse(0);
    }

    public List<Point> history(String item) {
        return data.history.getOrDefault(normalize(item), List.of());
    }

    public String sparkline(String item, int width) {
        List<Point> l = history(item);
        if (l.isEmpty()) return "";
        List<Point> tail = l.subList(Math.max(0, l.size() - width), l.size());
        double min = tail.stream().mapToDouble(p -> p.p).min().orElse(0);
        double max = tail.stream().mapToDouble(p -> p.p).max().orElse(0);
        StringBuilder sb = new StringBuilder();
        for (Point p : tail) {
            int idx = max == min ? 3 : (int) Math.round((p.p - min) / (max - min) * (BARS.length() - 1));
            sb.append(BARS.charAt(idx));
        }
        return sb.toString();
    }

    public Set<String> allItems() { return data.history.keySet(); }
    public Set<String> watched() { return data.watched; }
    public boolean watch(String item) { boolean r = data.watched.add(normalize(item)); save(); return r; }
    public boolean unwatch(String item) { boolean r = data.watched.remove(normalize(item)); save(); return r; }

    /** Items for the HUD: watched ones, or the most recently updated if nothing is watched. */
    public List<String> hudItems() {
        if (!data.watched.isEmpty()) return new ArrayList<>(data.watched);
        return lastSeen.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(5).map(Map.Entry::getKey).toList();
    }

    public void clear() { data.history.clear(); lastSeen.clear(); save(); }

    public boolean hud() { return data.hud; }
    public void setHud(boolean v) { data.hud = v; save(); }
    public boolean scan() { return data.scan; }
    public void setScan(boolean v) { data.scan = v; save(); }
    public String regex() { return data.regex; }
    public void setRegex(String r) { data.regex = r; save(); }

    public static String fmt(double v) {
        if (v >= 1_000_000) return String.format(Locale.ROOT, "%.2fM", v / 1_000_000);
        if (v >= 10_000) return String.format(Locale.ROOT, "%.1fk", v / 1000);
        if (v == Math.floor(v)) return String.format(Locale.ROOT, "%.0f", v);
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
