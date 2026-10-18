package com.lsstocks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Saved market data: price history per item for Auction House asks and Orders bids. */
public class MarketStore {
    public static final String DEFAULT_REGEX =
            "(?i)(?:price|cost|buy|sell|offer|bid|paying|pays|reward)\\s*:?\\s*\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kKmMbB]?)";
    private static final int MAX_POINTS = 600;
    private static final long SAME_PRICE_GAP_MS = 5 * 60_000L;

    public static class Point {
        public long t;
        public float p;
        /** True if the sample came from a complete scan of every page (not a single page you browsed). */
        public boolean full;

        public Point(long t, float p, boolean full) {
            this.t = t;
            this.p = p;
            this.full = full;
        }
    }

    public static class Rec {
        public String id;
        /** Vanilla item id used for the icon, e.g. minecraft:diamond. */
        public String base;
        public String name;
        /** Lowest Auction House price per unit over time. */
        public List<Point> ask = new ArrayList<>();
        /** Highest Orders price per unit over time. */
        public List<Point> bid = new ArrayList<>();
        public int askListings;
        public int bidListings;
        /** Page (1-based) where the cheapest listing / best order was in the last complete scan. */
        public int askPage;
        public int bidPage;
        public long updated;
        public boolean watched;

        public String baseId() {
            if (base != null) return base;
            int i = id.indexOf('|');
            return i < 0 ? id : id.substring(0, i);
        }
    }

    public static class Data {
        public Map<String, Rec> items = new LinkedHashMap<>();
        public String priceRegex = DEFAULT_REGEX;
        public String ahKeywords = "auction, market";
        public String orderKeywords = "order";
        public boolean autoScan = true;
        /** True if the price shown on a listing is for the whole stack. */
        public boolean priceIsPerStack = true;
        public String ahCommand = "ah";
        public String orderCommand = "orders";
        public String nextKeywords = "next";
        public int scanDelayMs = 900;

        // Automatic updating
        public boolean autoUpdate = true;
        public int autoMinutes = 10;
        /** Auto-update only runs on servers whose address contains this (empty = any). */
        public String serverFilter = "lifesteal";
        public long lastFullUpdate = 0L;

        // Items
        /** Also use the first descriptive lore line to tell custom items apart. */
        public boolean useLore = false;

        // Deals
        public int dealDropPct = 15;
        public int flipPct = 20;
        /** Which history the average is taken over: 0 all, 1 last 24h, 2 last 7 days. */
        public int avgWindow = 0;
        public int minPrice = 0;
        public boolean alerts = true;
        public boolean alertWatchedOnly = false;
        public int popupSeconds = 8;
    }

    public record Deal(Rec rec, String type, double pct, float price) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("lsstocks-market.json");

    public Data data = new Data();
    public boolean regexOk = true;
    private Pattern pattern = Pattern.compile(DEFAULT_REGEX);
    private boolean dirty;
    private long lastSave;

    /** Bumped whenever prices change, so the screen only refreshes when it has to. */
    public int version;

    /** Last time each item/type pair raised an alert (not saved). */
    public final Map<String, Long> lastAlert = new HashMap<>();

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
                    if (d.ahCommand == null) d.ahCommand = "ah";
                    if (d.orderCommand == null) d.orderCommand = "orders";
                    if (d.nextKeywords == null) d.nextKeywords = "next";
                    if (d.serverFilter == null) d.serverFilter = "lifesteal";
                    if (d.scanDelayMs < 300) d.scanDelayMs = 900;
                    if (d.autoMinutes < 1) d.autoMinutes = 10;
                    if (d.popupSeconds < 2) d.popupSeconds = 8;
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
    public float addSample(List<Point> series, float price, long now, boolean full) {
        float prev = series.isEmpty() ? Float.NaN : series.get(series.size() - 1).p;
        if (!series.isEmpty()) {
            Point last = series.get(series.size() - 1);
            if (last.p == price && now - last.t < SAME_PRICE_GAP_MS) {
                if (full) last.full = true;
                return prev;
            }
        }
        series.add(new Point(now, price, full));
        while (series.size() > MAX_POINTS) series.remove(0);
        markDirty();
        version++;
        return prev;
    }

    public void clear() {
        data.items.clear();
        version++;
        saveNow();
    }

    public static boolean matchesAny(String lowerTitle, String keywordCsv) {
        for (String k : keywordCsv.split(",")) {
            String kw = k.trim().toLowerCase(Locale.ROOT);
            if (!kw.isEmpty() && lowerTitle.contains(kw)) return true;
        }
        return false;
    }

    // ---- series helpers ----

    /**
     * The samples worth showing. Readings from a single browsed page are only an upper bound of the
     * real lowest price, so once there are two or more complete scans only those are used.
     */
    public static List<Point> view(List<Point> s) {
        int full = 0;
        for (Point p : s) if (p.full) full++;
        if (full < 2) return s;
        List<Point> out = new ArrayList<>(full);
        for (Point p : s) if (p.full) out.add(p);
        return out;
    }

    public static float latest(List<Point> raw) {
        List<Point> s = view(raw);
        return s.isEmpty() ? Float.NaN : s.get(s.size() - 1).p;
    }

    /** Percent change of the latest price versus the oldest sample in the last 24h (or the first sample). */
    public static double changePct(List<Point> raw) {
        List<Point> s = view(raw);
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

    public static float high(List<Point> raw) {
        float v = Float.NaN;
        for (Point p : view(raw)) if (Float.isNaN(v) || p.p > v) v = p.p;
        return v;
    }

    public static float low(List<Point> raw) {
        float v = Float.NaN;
        for (Point p : view(raw)) if (Float.isNaN(v) || p.p < v) v = p.p;
        return v;
    }

    public static float avg(List<Point> raw) {
        List<Point> s = view(raw);
        if (s.isEmpty()) return Float.NaN;
        double sum = 0;
        for (Point p : s) sum += p.p;
        return (float) (sum / s.size());
    }

    // ---- deals ----

    /** Percent the latest price is above the average of the earlier samples (negative = below). */
    public double deviation(List<Point> raw) {
        List<Point> s = view(raw);
        if (s.size() < 4) return Double.NaN;
        long now = System.currentTimeMillis();
        long cutoff = switch (data.avgWindow) {
            case 1 -> now - 24L * 3600_000L;
            case 2 -> now - 7L * 24L * 3600_000L;
            default -> 0L;
        };
        double sum = 0;
        int n = 0;
        for (int i = 0; i < s.size() - 1; i++) {
            Point p = s.get(i);
            if (p.t >= cutoff) {
                sum += p.p;
                n++;
            }
        }
        if (n < 3) return Double.NaN;
        double avg = sum / n;
        if (avg <= 0) return Double.NaN;
        return (s.get(s.size() - 1).p - avg) / avg * 100.0;
    }

    /** The best current deal for an item, or null. */
    public Deal dealFor(Rec r) {
        Deal best = null;
        float ask = latest(r.ask);
        float bid = latest(r.bid);
        double dev = deviation(r.ask);
        if (!Double.isNaN(dev) && -dev >= data.dealDropPct && ask >= data.minPrice && r.askListings > 0) {
            best = new Deal(r, "BUY", -dev, ask);
        }
        double surge = deviation(r.bid);
        if (!Double.isNaN(surge) && surge >= data.dealDropPct && bid >= data.minPrice && r.bidListings > 0
                && (best == null || surge > best.pct())) {
            best = new Deal(r, "SELL", surge, bid);
        }
        if (r.askListings > 0 && r.bidListings > 0 && !Float.isNaN(ask) && !Float.isNaN(bid)
                && ask > 0 && ask >= data.minPrice) {
            double margin = (bid - ask) / ask * 100.0;
            if (margin >= data.flipPct && (best == null || margin > best.pct())) {
                best = new Deal(r, "FLIP", margin, ask);
            }
        }
        return best;
    }

    public List<Deal> deals() {
        List<Deal> out = new ArrayList<>();
        for (Rec r : data.items.values()) {
            Deal d = dealFor(r);
            if (d != null) out.add(d);
        }
        out.sort((a, b) -> Double.compare(b.pct(), a.pct()));
        return out;
    }

    // ---- formatting ----

    public static String fmt(double v) {
        if (Double.isNaN(v)) return "-";
        double a = Math.abs(v);
        if (a >= 1_000_000_000) return String.format(Locale.ROOT, "%.2fB", v / 1_000_000_000);
        if (a >= 1_000_000) return String.format(Locale.ROOT, "%.2fM", v / 1_000_000);
        if (a >= 10_000) return String.format(Locale.ROOT, "%.1fk", v / 1000);
        if (a >= 100 || v == Math.floor(v)) return String.format(Locale.ROOT, "%,.0f", v);
        return String.format(Locale.ROOT, "%.2f", v);
    }

    public static String pctText(double v) {
        if (Double.isNaN(v)) return "-";
        if (v >= 1000) return ">999%";
        if (v <= -1000) return "<-999%";
        return String.format(Locale.ROOT, "%+.1f%%", v);
    }

    public static String ago(long t) {
        long s = Math.max(0, (System.currentTimeMillis() - t) / 1000);
        if (s < 60) return s + "s ago";
        if (s < 3600) return (s / 60) + "m ago";
        if (s < 86400) return (s / 3600) + "h ago";
        return (s / 86400) + "d ago";
    }

    /** Duration as a compact label, e.g. 90 minutes -> "1h 30m". */
    public static String span(long ms) {
        long m = Math.max(0, ms / 60_000);
        if (m < 60) return m + "m";
        long h = m / 60;
        if (h < 48) return h + "h" + (m % 60 == 0 ? "" : " " + (m % 60) + "m");
        return (h / 24) + "d";
    }
}
