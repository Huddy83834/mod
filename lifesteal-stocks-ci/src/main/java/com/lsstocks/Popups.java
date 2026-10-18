package com.lsstocks;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** On-screen deal / status popup cards. They draw over the game and over menus. */
public final class Popups {
    private static final int W = 236;
    private static final int H = 44;
    private static final int MAX_VISIBLE = 3;

    private static final class Popup {
        String kind;
        String title;
        String line1;
        String line2;
        String base;
        long created;
        long life;
    }

    private static final List<Popup> active = new ArrayList<>();
    private static final Map<String, ItemStack> icons = new HashMap<>();

    private Popups() {}

    public static void register() {
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("lsstocks", "popups"),
                (graphics, tracker) -> render(graphics));
    }

    public static void deal(MarketStore.Deal d) {
        Popup p = new Popup();
        p.kind = d.type();
        p.title = switch (d.type()) {
            case "BUY" -> "BUY DEAL";
            case "SELL" -> "SELL DEAL";
            default -> "FLIP DEAL";
        };
        p.line1 = d.rec().name;
        String price = MarketStore.fmt(d.price());
        p.line2 = switch (d.type()) {
            case "BUY" -> String.format("%.0f%% below average  ·  %s", d.pct(), price);
            case "SELL" -> String.format("%.0f%% above average  ·  %s", d.pct(), price);
            default -> String.format("+%.0f%% margin  ·  buy at %s", d.pct(), price);
        };
        p.base = d.rec().baseId();
        push(p, StocksClient.STORE.data.popupSeconds * 1000L);
    }

    public static void info(String title, String body) {
        Popup p = new Popup();
        p.kind = "INFO";
        p.title = title.toUpperCase();
        p.line1 = body;
        p.line2 = "";
        p.base = null;
        push(p, 5000L);
    }

    private static void push(Popup p, long life) {
        p.created = System.currentTimeMillis();
        p.life = life;
        active.add(p);
        while (active.size() > MAX_VISIBLE) active.remove(0);
    }

    private static int accent(String kind) {
        return switch (kind) {
            case "BUY" -> 0xFF3DDC84;
            case "SELL" -> 0xFF4FC3F7;
            case "FLIP" -> 0xFFFFA726;
            default -> 0xFFF5B83D;
        };
    }

    private static int alpha(int color, float a) {
        int base = (color >>> 24) & 0xFF;
        int na = Math.max(0, Math.min(255, (int) (base * a)));
        return (na << 24) | (color & 0xFFFFFF);
    }

    public static void render(GuiGraphicsExtractor g) {
        if (active.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        active.removeIf(p -> now - p.created > p.life);
        int sw = mc.getWindow().getGuiScaledWidth();
        int x = (sw - W) / 2;

        for (int i = 0; i < active.size(); i++) {
            Popup p = active.get(i);
            long age = now - p.created;
            long left = p.life - age;

            float in = Math.min(1f, age / 320f);
            float ease = 1f - (1f - in) * (1f - in) * (1f - in);
            float fade = left < 450 ? Math.max(0f, left / 450f) : 1f;
            int y = 10 + i * (H + 6) - (int) ((1f - ease) * (H + 20));
            int acc = accent(p.kind);

            g.fill(x, y, x + W, y + H, alpha(0xF0121419, fade));
            g.fill(x, y, x + W, y + 1, alpha(acc, 0.7f * fade));
            g.fill(x, y + H - 1, x + W, y + H, alpha(0xFF2C3242, fade));
            g.fill(x, y, x + 1, y + H, alpha(0xFF2C3242, fade));
            g.fill(x + W - 1, y, x + W, y + H, alpha(0xFF2C3242, fade));
            g.fill(x, y, x + 3, y + H, alpha(acc, fade));

            int tx = x + 12;
            if (p.base != null) {
                g.item(icon(p.base), x + 12, y + 14);
                tx = x + 36;
            }
            g.text(mc.font, p.title, tx, y + 7, alpha(acc, fade), false);
            g.text(mc.font, Component.literal(clip(mc, p.line1, W - (tx - x) - 10)).withStyle(ChatFormatting.BOLD),
                    tx, y + 18, alpha(0xFFE6E8EE, fade), false);
            if (!p.line2.isEmpty()) {
                g.text(mc.font, clip(mc, p.line2, W - (tx - x) - 10), tx, y + 30, alpha(0xFF8A90A0, fade), false);
            }
            int barW = (int) ((W - 4) * Math.max(0f, (float) left / p.life));
            g.fill(x + 2, y + H - 3, x + 2 + barW, y + H - 1, alpha(acc, 0.8f * fade));
        }
    }

    private static String clip(Minecraft mc, String s, int max) {
        if (mc.font.width(s) <= max) return s;
        int end = s.length();
        while (end > 0 && mc.font.width(s.substring(0, end) + "...") > max) end--;
        return s.substring(0, end) + "...";
    }

    private static ItemStack icon(String base) {
        return icons.computeIfAbsent(base, k -> new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(k))));
    }
}
