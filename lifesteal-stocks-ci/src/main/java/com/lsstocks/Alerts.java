package com.lsstocks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

import java.util.List;

/** On-screen deal popups (toast) plus a few chat lines. */
public final class Alerts {
    private Alerts() {}

    public static void notify(Minecraft mc, List<MarketStore.Deal> deals) {
        if (deals.isEmpty() || mc.player == null) return;
        deals.sort((a, b) -> Double.compare(b.pct(), a.pct()));
        MarketStore.Deal top = deals.get(0);
        String title = "LS Market: " + deals.size() + (deals.size() == 1 ? " deal" : " deals");
        mc.gui.toastManager().addToast(new SystemToast(
                SystemToast.SystemToastId.NARRATOR_TOGGLE,
                Component.literal(title),
                Component.literal(describe(top))));
        for (int i = 0; i < Math.min(4, deals.size()); i++) {
            mc.player.sendSystemMessage(Component.literal("[LS Market] " + describe(deals.get(i))));
        }
        if (deals.size() > 4) {
            mc.player.sendSystemMessage(Component.literal("[LS Market] +" + (deals.size() - 4) + " more in the Deals tab"));
        }
    }

    public static String describe(MarketStore.Deal d) {
        String name = d.rec().name;
        String price = MarketStore.fmt(d.price());
        return switch (d.type()) {
            case "BUY" -> String.format("%s is %.0f%% below its average: %s", name, d.pct(), price);
            case "SELL" -> String.format("%s orders pay %.0f%% above average: %s", name, d.pct(), price);
            default -> String.format("%s flip: buy %s, sell for +%.0f%%", name, price, d.pct());
        };
    }
}
