package com.lsstocks;

import net.minecraft.client.Minecraft;

import java.util.List;

/** Turns deals into on-screen popup cards (nothing is sent to chat). */
public final class Alerts {
    private Alerts() {}

    public static void notify(Minecraft mc, List<MarketStore.Deal> deals) {
        if (deals.isEmpty() || mc.player == null) return;
        deals.sort((a, b) -> Double.compare(b.pct(), a.pct()));
        int shown = Math.min(3, deals.size());
        for (int i = shown - 1; i >= 0; i--) Popups.deal(deals.get(i));
        if (deals.size() > shown) {
            Popups.info("More deals", "+" + (deals.size() - shown) + " more in the Deals tab");
        }
    }
}
