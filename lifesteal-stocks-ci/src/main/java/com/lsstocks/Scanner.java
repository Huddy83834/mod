package com.lsstocks;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Reads Auction House / Orders pages as they are shown on your screen.
 *
 * - Browsing a page yourself records a "partial" reading (a single page).
 * - During a full update ({@link Crawler}) every page is merged into one session, and the whole
 *   result is saved as a single "complete" reading, so lowest prices are real market lows.
 */
public final class Scanner {
    private static String lastSignature = "";

    private Scanner() {}

    /** One individual listing: unit price, how many items, and an identity key. */
    record Lst(float unit, int qty, long key) {}

    static final class Agg {
        String id;
        String base;
        String name;
        float best = Float.NaN;
        int count;
        int bestPage = 1;
        final List<Lst> listings = new ArrayList<>();
    }

    private static final java.util.regex.Pattern SELLER =
            java.util.regex.Pattern.compile("(?i)(?:seller|listed by|sold by|owner)\\s*:?\\s*([A-Za-z0-9_]{3,16})");

    /** Items merged across all pages of the current full update. */
    private static final Map<String, Agg> session = new HashMap<>();

    public static void beginSession() {
        session.clear();
        lastSignature = "";
    }

    public static int sessionSize() {
        return session.size();
    }

    // ------------------------------------------------------------------ item identity

    private record Variant(String id, String base, String name) {}

    /** Works out which item a stack is, including custom-named items and enchanted books. */
    private static Variant variantOf(ItemStack stack, MarketStore store, List<Component> lore) {
        String base = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String defaultName = new ItemStack(stack.getItem()).getHoverName().getString();
        StringBuilder key = new StringBuilder(base);
        String name = defaultName;

        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        Component custom = stack.get(DataComponents.CUSTOM_NAME);
        if (stored != null && !stored.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (Object2IntMap.Entry<Holder<Enchantment>> e : stored.entrySet()) {
                parts.add(Enchantment.getFullname(e.getKey(), e.getIntValue()).getString());
            }
            Collections.sort(parts);
            String joined = String.join(", ", parts);
            key.append('|').append(joined.toLowerCase(Locale.ROOT));
            name = "Book: " + joined;
        } else if (custom != null && !custom.getString().isBlank()) {
            String cn = custom.getString().trim();
            key.append('|').append(cn.toLowerCase(Locale.ROOT));
            name = cn;
        } else if (store.data.useLore) {
            for (Component line : lore) {
                String t = line.getString().trim();
                if (t.isEmpty() || t.contains(":") || store.pattern().matcher(t).find()) continue;
                key.append('|').append(t.toLowerCase(Locale.ROOT));
                name = defaultName + " (" + t + ")";
                break;
            }
        }
        return new Variant(key.toString(), base, name);
    }

    // ------------------------------------------------------------------ tick

    public static void tick(Minecraft mc, MarketStore store) {
        if (!store.data.autoScan || mc.player == null) return;
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
            lastSignature = "";
            return;
        }

        String title = screen.getTitle().getString();
        String lower = title.toLowerCase(Locale.ROOT);
        boolean isBid = MarketStore.matchesAny(lower, store.data.orderKeywords);
        boolean isAsk = !isBid && MarketStore.matchesAny(lower, store.data.ahKeywords);

        AbstractContainerMenu menu = screen.getMenu();
        Map<String, Agg> found = new HashMap<>();
        List<String> sample = new ArrayList<>();
        StringBuilder sig = new StringBuilder(title);
        int listings = 0;
        int page = Crawler.page();

        for (Slot slot : menu.slots) {
            if (slot.container == mc.player.getInventory()) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            ItemLore itemLore = stack.get(DataComponents.LORE);
            List<Component> lines = itemLore == null ? List.of() : itemLore.lines();

            Double price = null;
            String seller = "";
            for (Component line : lines) {
                String text = line.getString();
                if (seller.isEmpty()) {
                    Matcher sm = SELLER.matcher(text);
                    if (sm.find()) seller = sm.group(1);
                }
                if (sample.size() < 10 && !text.isBlank()) {
                    sample.add(stack.getHoverName().getString() + " :: " + text);
                }
                if (price == null) {
                    Matcher m = store.pattern().matcher(text);
                    if (m.find()) price = parse(m.group(1), m.groupCount() >= 2 ? m.group(2) : null);
                }
            }
            sig.append('|').append(slot.index).append(stack.getCount()).append(price);
            if (price == null) continue;

            double unit = store.data.priceIsPerStack ? price / Math.max(1, stack.getCount()) : price;
            Variant v = variantOf(stack, store, lines);
            Agg a = found.computeIfAbsent(v.id(), k -> {
                Agg n = new Agg();
                n.id = v.id();
                n.base = v.base();
                n.name = v.name();
                return n;
            });
            float u = (float) unit;
            if (Float.isNaN(a.best) || (isBid ? u > a.best : u < a.best)) {
                a.best = u;
                a.bestPage = page;
            }
            a.count++;
            listings++;
            long key = (((long) Float.floatToIntBits(u)) << 32) ^ ((long) stack.getCount() * 1_000_003L) ^ seller.hashCode();
            a.listings.add(new Lst(u, Math.max(1, stack.getCount()), key));
        }

        store.lastTitle = title;
        store.lastKind = isBid ? "Orders" : isAsk ? "Auction House" : "not recognised";
        store.lastSample = sample;

        String signature = sig.toString();
        if (signature.equals(lastSignature)) return;
        lastSignature = signature;
        if (!isBid && !isAsk) return;

        long now = System.currentTimeMillis();
        if (listings > 0) {
            store.lastScanTime = now;
            store.lastScanCount = listings;
        }

        if (Crawler.collecting()) {
            // Merge this page into the full update.
            for (Agg a : found.values()) {
                Agg s = session.computeIfAbsent(a.id, k -> {
                    Agg n = new Agg();
                    n.id = a.id;
                    n.base = a.base;
                    n.name = a.name;
                    return n;
                });
                s.count += a.count;
                s.listings.addAll(a.listings);
                if (Float.isNaN(s.best) || (isBid ? a.best > s.best : a.best < s.best)) {
                    s.best = a.best;
                    s.bestPage = a.bestPage;
                }
            }
            return;
        }

        // Browsing by hand: record a partial reading of this page.
        List<MarketStore.Deal> fresh = new ArrayList<>();
        for (Agg a : found.values()) {
            MarketStore.Rec r = store.rec(a.id, a.name);
            r.base = a.base;
            r.updated = now;
            if (isBid) {
                r.bidListings = Math.max(r.bidListings, a.count);
                store.addSample(r.bid, a.best, now, false);
            } else {
                r.askListings = Math.max(r.askListings, a.count);
                store.addSample(r.ask, a.best, now, false);
            }
            collectDeal(store, r, now, fresh);
        }
        Alerts.notify(mc, fresh);
    }

    /** Saves a finished (or stopped) full update as one reading per item. */
    public static void commit(Minecraft mc, MarketStore store, boolean bid, boolean complete) {
        long now = System.currentTimeMillis();
        List<MarketStore.Deal> fresh = new ArrayList<>();
        for (Agg a : session.values()) {
            MarketStore.Rec r = store.rec(a.id, a.name);
            r.base = a.base;
            r.updated = now;
            if (bid) {
                r.bidListings = a.count;
                r.bidPage = a.bestPage;
                store.addSample(r.bid, a.best, now, complete);
            } else {
                r.askListings = a.count;
                r.askPage = a.bestPage;
                store.addSample(r.ask, a.best, now, complete);
                if (complete) {
                    inferSales(r, a.listings, now);
                    MarketStore.Market m = store.market(r);
                    if (m.source() >= 2 && !Float.isNaN(m.price())) store.addSample(r.mkt, m.price(), now, true);
                }
            }
            collectDeal(store, r, now, fresh);
        }
        if (complete) {
            // Anything we knew about that is no longer listed has sold out (or was taken down).
            for (MarketStore.Rec r : store.data.items.values()) {
                if (session.containsKey(r.id)) continue;
                if (bid) {
                    r.bidListings = 0;
                } else {
                    if (r.askListings > 0) inferSales(r, List.of(), now);
                    r.askListings = 0;
                }
            }
        }
        session.clear();
        store.version++;
        store.markDirty();
        Alerts.notify(mc, fresh);
    }

    /**
     * Compares the cheapest listings from the previous complete scan with the new ones. Walking up
     * from the cheapest, every listing that vanished counts as bought; the first one that is still
     * there stops it, because anything pricier that vanished was most likely cancelled or expired.
     * An overpriced listing that just sits there never counts as a sale.
     */
    private static void inferSales(MarketStore.Rec r, List<Lst> now, long t) {
        float[] pu = r.askUnits;
        long[] pk = r.askKeys;
        int[] pq = r.askQty;
        if (pk != null && pk.length > 0) {
            java.util.Set<Long> current = new java.util.HashSet<>();
            for (Lst l : now) current.add(l.key());
            int added = 0;
            for (int i = 0; i < pk.length && added < 25; i++) {
                if (current.contains(pk[i])) break;
                r.sales.add(new MarketStore.Sale(t, pu[i], pq[i]));
                added++;
            }
            while (r.sales.size() > 80) r.sales.remove(0);
        }
        List<Lst> sorted = new ArrayList<>(now);
        sorted.sort((x, y) -> Float.compare(x.unit(), y.unit()));
        int n = Math.min(60, sorted.size());
        r.askUnits = new float[n];
        r.askKeys = new long[n];
        r.askQty = new int[n];
        for (int i = 0; i < n; i++) {
            r.askUnits[i] = sorted.get(i).unit();
            r.askKeys[i] = sorted.get(i).key();
            r.askQty[i] = sorted.get(i).qty();
        }
    }

    private static void collectDeal(MarketStore store, MarketStore.Rec r, long now, List<MarketStore.Deal> fresh) {
        if (!store.data.alerts || (store.data.alertWatchedOnly && !r.watched)) return;
        MarketStore.Deal d = store.dealFor(r);
        if (d == null) return;
        String key = r.id + "/" + d.type();
        Long last = store.lastAlert.get(key);
        if (last != null && now - last < 10 * 60_000L) return;
        store.lastAlert.put(key, now);
        fresh.add(d);
    }

    private static double parse(String num, String suffix) {
        double v = Double.parseDouble(num.replace(",", ""));
        if (suffix == null) return v;
        return switch (suffix.toLowerCase(Locale.ROOT)) {
            case "k" -> v * 1_000;
            case "m" -> v * 1_000_000;
            case "b" -> v * 1_000_000_000;
            default -> v;
        };
    }
}
