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

    /** A listing that disappeared from the Auction House: it was (very likely) bought. */
    public static class Sale {
        public long t;
        public float p;
        public int qty;

        public Sale(long t, float p, int qty) {
            this.t = t;
            this.p = p;
            this.qty = qty;
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
        /** The "usual price" over time (from estimated sales). */
        public List<Point> mkt = new ArrayList<>();
        /** Estimated recent sales, newest last. */
        public List<Sale> sales = new ArrayList<>();
        /** The cheapest listings in the last complete scan (sorted by price), to spot which got bought. */
        public float[] askUnits;
        public long[] askKeys;
        public int[] askQty;
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
        /** Auction House lines containing these show an instant "buy now" price (the one we use). */
        public String instantWords = "buy now, buy it now, instant, buyout, buy out, buy price";
        /** Lines containing these are bids and are never used as the price. */
        public String bidWords = "bid";
        public int scanDelayMs = 900;

        // Automatic updating
        public boolean autoUpdate = true;
        public int autoMinutes = 1;
        /** Seconds between automatic updates. */
        public int autoSeconds = 30;
        /** Bumped when defaults change, so old saved settings can be migrated once. */
        public int configVersion = 4;
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
        public int avgWindow = 2;
        public int minPrice = 0;
        public boolean alerts = true;
        public boolean alertWatchedOnly = false;
        public int popupSeconds = 8;
    }

    public record Deal(Rec rec, String type, double pct, float price, float usual) {}

    /** The usual price. source: 0 none, 1 current listings only, 2 price history, 3 estimated sales. */
    public record Market(float price, int sales, int source) {}

    private static final Gson GSON = new GsonBuilder().create();
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
    /** Full detail of the last items seen (name, lore, hidden data) so odd items can be diagnosed. */
    public List<String> lastDump = new ArrayList<>();

    /** Writes lastDump to config/lsstocks-items.txt. Returns the file path or null. */
    public String writeDump() {
        if (lastDump.isEmpty()) return null;
        try {
            Path out = FabricLoader.getInstance().getConfigDir().resolve("lsstocks-items.txt");
            Files.writeString(out, String.join("\n", lastDump));
            return out.toString();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

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
                    if (d.instantWords == null) d.instantWords = "buy now, buy it now, instant, buyout, buy out, buy price";
                    if (d.bidWords == null) d.bidWords = "bid";
                    if (d.serverFilter == null) d.serverFilter = "lifesteal";
                    if (d.scanDelayMs < 300) d.scanDelayMs = 500;
                    if (d.autoMinutes < 1) d.autoMinutes = 1;
                    if (d.configVersion < 2) {
                        d.autoMinutes = 1;
                        d.avgWindow = 2;
                        d.configVersion = 2;
                    }
                    if (d.configVersion < 3) {
                        d.autoSeconds = 30;
                        d.scanDelayMs = 500;
                        d.configVersion = 3;
                    }
                    if (d.configVersion < 4) {
                        // Older versions could record bid prices as if they were buy-now prices.
                        d.items.clear();
                        d.configVersion = 4;
                    }
                    if (d.autoSeconds < 10) d.autoSeconds = 30;
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
        if (dirty && System.currentTimeMillis() - lastSave > 60_000L) saveNow();
    }

    private static final java.util.concurrent.ExecutorService WRITER =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "lsstocks-writer");
                t.setDaemon(true);
                return t;
            });

    /** Serializes now (cheap) and writes the file on a background thread so the game never stalls. */
    public void saveNow() {
        final String json;
        try {
            json = GSON.toJson(data);
        } catch (RuntimeException e) {
            StocksClient.LOGGER.warn("Could not save market data", e);
            return;
        }
        dirty = false;
        lastSave = System.currentTimeMillis();
        WRITER.execute(() -> writeFile(json));
    }

    /** Same, but waits until the file is written (used when the game closes). */
    public void saveBlocking() {
        try {
            writeFile(GSON.toJson(data));
        } catch (RuntimeException e) {
            StocksClient.LOGGER.warn("Could not save market data", e);
        }
    }

    private void writeFile(String json) {
        try {
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, json);
            try {
                Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFail) {
                Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
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
        if (series.size() > MAX_POINTS) compact(series, now);
        markDirty();
        version++;
        return prev;
    }

    /**
     * Keeps history useful for days: the last 3 hours stay detailed, the last 2 days are thinned to
     * one reading per 10 minutes, and anything older to one per hour.
     */
    private static void compact(List<Point> s, long now) {
        List<Point> out = new ArrayList<>(s.size() / 2);
        long curBucket = Long.MIN_VALUE;
        Point keep = null;
        for (Point p : s) {
            long age = now - p.t;
            if (age < 3 * 3600_000L) {
                if (keep != null) {
                    out.add(keep);
                    keep = null;
                }
                out.add(p);
                continue;
            }
            long size = age < 48 * 3600_000L ? 600_000L : 3_600_000L;
            long bucket = (p.t / size) * 10 + (size == 600_000L ? 1 : 2);
            if (bucket != curBucket) {
                if (keep != null) out.add(keep);
                curBucket = bucket;
                keep = p;
            } else if (p.full || !keep.full) {
                keep = p;
            }
        }
        if (keep != null) out.add(keep);
        // Still too long (very old data): drop the oldest.
        while (out.size() > MAX_POINTS) out.remove(0);
        s.clear();
        s.addAll(out);
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
        List<Point> base = s;
        if (full >= 2) {
            base = new ArrayList<>(full);
            for (Point p : s) if (p.full) base.add(p);
        }
        if (base.size() < 5) return base;
        // Drop one-off spikes by comparing each reading with its neighbours (so a real trend is kept).
        List<Point> out = new ArrayList<>(base.size());
        int n = base.size();
        for (int i = 0; i < n; i++) {
            Point p = base.get(i);
            if (i == n - 1) {
                out.add(p);
                continue;
            }
            int lo = Math.max(0, i - 3);
            int hi = Math.min(n - 1, i + 3);
            float[] w = new float[hi - lo + 1];
            for (int k = lo; k <= hi; k++) w[k - lo] = base.get(k).p;
            java.util.Arrays.sort(w);
            float med = w[w.length / 2];
            if (med <= 0 || (p.p <= med * 2.5f && p.p >= med / 2.5f)) out.add(p);
        }
        return out.size() >= 2 ? out : base;
    }

    public static float median(List<Float> v) {
        if (v.isEmpty()) return Float.NaN;
        List<Float> c = new ArrayList<>(v);
        java.util.Collections.sort(c);
        int n = c.size();
        return n % 2 == 1 ? c.get(n / 2) : (c.get(n / 2 - 1) + c.get(n / 2)) / 2f;
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

    // ---- usual price and deals ----

    private long windowCutoff(long now) {
        return switch (data.avgWindow) {
            case 1 -> now - 24L * 3600_000L;
            case 2 -> now - 7L * 24L * 3600_000L;
            default -> 0L;
        };
    }

    /**
     * What the item usually sells for. Listings nobody buys say nothing about the price, so this is
     * based on listings that disappeared (estimated sales), then on history, and only as a last
     * resort on what is listed right now.
     */
    public Market market(Rec r) {
        long now = System.currentTimeMillis();
        long cutoff = windowCutoff(now);
        List<Float> prices = new ArrayList<>();
        for (Sale sale : r.sales) if (sale.t >= cutoff) prices.add(sale.p);
        if (prices.size() >= 2) return new Market(median(prices), prices.size(), 3);

        List<Point> hist = view(r.ask);
        if (hist.size() >= 4) {
            List<Float> v = new ArrayList<>();
            for (Point p : hist) if (p.t >= cutoff) v.add(p.p);
            if (v.size() >= 3) return new Market(median(v), 0, 2);
        }
        if (r.askUnits != null && r.askUnits.length > 0) {
            return new Market(r.askUnits[(r.askUnits.length - 1) / 4], 0, 1);
        }
        return new Market(Float.NaN, 0, 0);
    }

    /** Percent the cheapest listing is above (+) or below (-) the usual price, or NaN. */
    public double vsUsual(Rec r) {
        Market m = market(r);
        float ask = latest(r.ask);
        if (m.source() == 0 || Float.isNaN(ask) || m.price() <= 0 || r.askListings <= 0) return Double.NaN;
        return (ask - m.price()) / m.price() * 100.0;
    }

    /** The best current deal for an item, or null. */
    public Deal dealFor(Rec r) {
        Market m = market(r);
        float ask = latest(r.ask);
        float bid = latest(r.bid);
        Deal best = null;
        if (m.source() >= 2 && m.price() > 0) {
            if (r.askListings > 0 && !Float.isNaN(ask) && ask >= data.minPrice) {
                double below = (m.price() - ask) / m.price() * 100.0;
                if (below >= data.dealDropPct) best = new Deal(r, "BUY", below, ask, m.price());
            }
            if (r.bidListings > 0 && !Float.isNaN(bid) && bid >= data.minPrice) {
                double above = (bid - m.price()) / m.price() * 100.0;
                if (above >= data.dealDropPct && (best == null || above > best.pct())) {
                    best = new Deal(r, "SELL", above, bid, m.price());
                }
            }
        }
        if (r.askListings > 0 && r.bidListings > 0 && !Float.isNaN(ask) && !Float.isNaN(bid)
                && ask > 0 && ask >= data.minPrice) {
            double margin = (bid - ask) / ask * 100.0;
            if (margin >= data.flipPct && (best == null || margin > best.pct())) {
                best = new Deal(r, "FLIP", margin, ask, bid);
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
