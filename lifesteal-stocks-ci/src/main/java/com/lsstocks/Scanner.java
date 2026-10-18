package com.lsstocks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Passively reads whatever Auction House / Orders pages you open. It only looks at item
 * data the server already sent to your client. It never clicks, pages or sends anything.
 */
public final class Scanner {
    private static String lastSignature = "";

    private Scanner() {}

    private static final class Agg {
        String name;
        float best;
        int count;
    }

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

        for (Slot slot : menu.slots) {
            if (slot.container == mc.player.getInventory()) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            ItemLore lore = stack.get(DataComponents.LORE);
            List<Component> lines = lore == null ? List.of() : lore.lines();

            Double price = null;
            for (Component line : lines) {
                String text = line.getString();
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
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            Agg a = found.computeIfAbsent(id, k -> {
                Agg n = new Agg();
                n.name = new ItemStack(stack.getItem()).getHoverName().getString();
                n.best = Float.NaN;
                return n;
            });
            float u = (float) unit;
            if (Float.isNaN(a.best) || (isBid ? u > a.best : u < a.best)) a.best = u;
            a.count++;
            listings++;
        }

        store.lastTitle = title;
        store.lastKind = isBid ? "Orders" : isAsk ? "Auction House" : "not recognised";
        store.lastSample = sample;

        String signature = sig.toString();
        if (signature.equals(lastSignature)) return;
        lastSignature = signature;
        if (!isBid && !isAsk) return;

        long now = System.currentTimeMillis();
        List<MarketStore.Deal> fresh = new ArrayList<>();
        for (Map.Entry<String, Agg> e : found.entrySet()) {
            Agg a = e.getValue();
            MarketStore.Rec r = store.rec(e.getKey(), a.name);
            r.updated = now;
            if (isBid) {
                r.bidListings = a.count;
                store.addSample(r.bid, a.best, now);
            } else {
                r.askListings = a.count;
                store.addSample(r.ask, a.best, now);
            }
            if (store.data.alerts && (!store.data.alertWatchedOnly || r.watched)) {
                MarketStore.Deal d = store.dealFor(r);
                if (d != null) {
                    String key = r.id + "/" + d.type();
                    Long last = store.lastAlert.get(key);
                    if (last == null || now - last > 10 * 60_000L) {
                        store.lastAlert.put(key, now);
                        fresh.add(d);
                    }
                }
            }
        }
        Alerts.notify(mc, fresh);
        if (listings > 0) {
            store.lastScanTime = now;
            store.lastScanCount = listings;
        }
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
