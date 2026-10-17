package com.lsstocks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Saved market data: price history per item for Auction House asks and Orders bids. */
public class MarketStore {
    public static final String DEFAULT_REGEX =
            "(?i)(?:price|cost|buy|sell|offer|bid|paying|pays|reward)\\s*:?\\s*\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kKmMbB]?)";
    private static final int MAX_POINTS = 400;
    private static final long SAME_PRICE_GAP_MS = 5 * 60_000L;

    public static class Point {
        public long t;
        public float p;

        public Point(long t, float p) {
            this.t = t;
            this.p = p;
        }
    }

    public static class Rec {
        public String id;
        public String name;
        /** Lowest Auction House price per unit over time. */
        public List<Point> ask = new ArrayList<>();
        /** Highest Orders price per unit over time. */
        public List<Point> bid = new ArrayList<>();
        public int askListings;
        public int bidListings;
        public long updated;
        public boolean watched;
    }

    public static class Data {
        public Map<String, Rec> items = new LinkedHashMap<>();
        public String priceRegex = DEFAULT_REGEX;
        public String ahKeywords = "auction, market";
        public String orderKeywords = "order";
        public boolean autoScan = true;
        /** True if the price shown on a listing is for the whole stack. */
        public boolean priceIsPerStack = true;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("lsstocks-market.json");

    public Data data = new Data();
    public boolean regexOk = true;
    private Pattern pattern = Pattern.compile(DEFAULT_REGEX);
    private boolean dirty;
    private long lastSave;

    // Last scan info (for the footer and the calibration panel in Settings)
    public String lastTitle = "";
    public String lastKind = "";
    public long lastScanTime;
    public int lastScanCount;
    public List<String> lastSample = new ArrayList<>();

    public void load() {
        try {
            if (Files.exists(file)) {
                Data d = GSON.fromJson(Files.readString(file), Data.class);
                if (d != null) {
                    if (d.items == null) d.items = new LinkedHashMap<>();
                    if (d.priceRegex == null) d.priceRegex = DEFAULT_REGEX;
                    if (d.ahKeywords == null) d.ahKeywords = "auction, market";
                    if (d.orderKeywords == null) d.orderKeywords = "order";
                    data = d;
                }
            }
        } catch (Exception e) {
            StocksClient.LOGGER.warn("Could not load market data", e);
        }
        setRegex(data.priceRegex);
    }

    public void markDirty() {
        dirty = true;
    }

    public void maybeSave() {
        if (dirty && System.currentTimeMillis() - lastSave > 15_000L) saveNow();
    }

    public void saveNow() {
        try {
            Files.writeString(file, GSON.toJson(data));
            dirty = false;
            lastSave = System.currentTimeMillis();
        } catch (IOException | RuntimeException e) {
            StocksClient.LOGGER.warn("Could not save market data", e);
        }
    }

    public boolean setRegex(String regex) {
        try {
            pattern = Pattern.compile(regex);
            data.priceRegex = regex;
            regexOk = true;
            markDirty();
        } catch (Exception e) {
            regexOk = false;
        }
        return regexOk;
    }

    public Pattern pattern() {
        return pattern;
    }

    public Rec rec(String id, String name) {
        Rec r = data.items.computeIfAbsent(id, k -> {
            Rec n = new Rec();
            n.id = k;
            return n;
        });
        r.name = name;
        return r;
    }

    /** Adds a sample unless the price is unchanged and recent. Returns the previous price or NaN. */
    public float addSample(List<Point> series, float price, long now) {
        float prev = series.isEmpty() ? Float.NaN : series.get(series.size() - 1).p;
        if (!series.isEmpty()) {
            Point last = series.get(series.size() - 1);
            if (last.p == price && now - last.t < SAME_PRICE_GAP_MS) return prev;
        }
        series.add(new Point(now, price));
        while (series.size() > MAX_POINTS) series.remove(0);
        markDirty();
        return prev;
    }

    public void clear() {
        data.items.clear();
        saveNow();
    }

    public static boolean matchesAny(String lowerTitle, String keywordCsv) {
        for (String k : keywordCsv.split(",")) {
            String kw = k.trim().toLowerCase(Locale.ROOT);
            if (!kw.isEmpty() && lowerTitle.contains(kw)) return true;
        }
        return false;
    }

    // ---- stats helpers ----

    public static float latest(List<Point> s) {
        return s.isEmpty() ? Float.NaN : s.get(s.size() - 1).p;
    }

    /** Percent change of the latest price versus the oldest sample in the last 24h (or the first sample). */
    public static double changePct(List<Point> s) {
        if (s.size() < 2) return Double.NaN;
        long cutoff = System.currentTimeMillis() - 24L * 3600_000L;
        Point ref = s.get(0);
        for (Point p : s) {
            if (p.t >= cutoff) {
                ref = p;
                break;
            }
        }
        Point last = s.get(s.size() - 1);
        if (ref == last || ref.p == 0) return Double.NaN;
        return (last.p - ref.p) / ref.p * 100.0;
    }

    public static float high(List<Point> s) {
        float v = Float.NaN;
        for (Point p : s) if (Float.isNaN(v) || p.p > v) v = p.p;
        return v;
    }

    public static float low(List<Point> s) {
        float v = Float.NaN;
        for (Point p : s) if (Float.isNaN(v) || p.p < v) v = p.p;
        return v;
    }

    public static float avg(List<Point> s) {
        if (s.isEmpty()) return Float.NaN;
        double sum = 0;
        for (Point p : s) sum += p.p;
        return (float) (sum / s.size());
    }

    public static String fmt(double v) {
        if (Double.isNaN(v)) return "-";
        double a = Math.abs(v);
        if (a >= 1_000_000_000) return String.format(Locale.ROOT, "%.2fB", v / 1_000_000_000);
        if (a >= 1_000_000) return String.format(Locale.ROOT, "%.2fM", v / 1_000_000);
        if (a >= 10_000) return String.format(Locale.ROOT, "%.1fk", v / 1000);
        if (a >= 100 || v == Math.floor(v)) return String.format(Locale.ROOT, "%,.0f", v);
        return String.format(Locale.ROOT, "%.2f", v);
    }

    public static String ago(long t) {
        long s = Math.max(0, (System.currentTimeMillis() - t) / 1000);
        if (s < 60) return s + "s ago";
        if (s < 3600) return (s / 60) + "m ago";
        if (s < 86400) return (s / 3600) + "h ago";
        return (s / 86400) + "d ago";
    }
}
