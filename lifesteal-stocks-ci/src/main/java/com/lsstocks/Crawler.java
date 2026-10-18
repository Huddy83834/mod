package com.lsstocks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Opt-in "Scan all": opens the Auction House and Orders with their commands and turns pages
 * at a fixed, adjustable pace until the last page. Press ESC at any time to stop.
 *
 * Safety: it only ever clicks a slot that has no price in its text (so never a listing)
 * and whose name or text contains the configured "next page" keyword.
 */
public final class Crawler {
    private enum State { IDLE, GAP, WAIT_OPEN, PAGE_WAIT, WAIT_CHANGE }

    private static final int MAX_PAGES = 300;

    private static State state = State.IDLE;
    private static final List<String> queue = new ArrayList<>();
    private static String currentName = "";
    private static int ticks;
    private static int pages;
    private static int totalPages;
    private static String sigBefore = "";

    private Crawler() {}

    public static boolean running() {
        return state != State.IDLE;
    }

    public static void start(Minecraft mc, MarketStore store) {
        if (mc.player == null || running()) return;
        queue.clear();
        queue.add("ah");
        queue.add("orders");
        totalPages = 0;
        mc.gui.setScreen(null);
        say(mc, "Scanning the Auction House and Orders. Press ESC to stop.");
        startNext(mc, store);
    }

    private static void startNext(Minecraft mc, MarketStore store) {
        if (queue.isEmpty()) {
            finish(mc, "Finished. " + totalPages + " pages read.");
            return;
        }
        String key = queue.remove(0);
        boolean ah = key.equals("ah");
        currentName = ah ? "Auction House" : "Orders";
        String cmd = (ah ? store.data.ahCommand : store.data.orderCommand).trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (cmd.isEmpty()) {
            startNext(mc, store);
            return;
        }
        mc.player.connection.sendCommand(cmd);
        state = State.WAIT_OPEN;
        ticks = 0;
        pages = 0;
    }

    private static void finish(Minecraft mc, String message) {
        state = State.IDLE;
        queue.clear();
        if (mc.player != null) {
            if (mc.gui.screen() instanceof AbstractContainerScreen<?>) mc.player.closeContainer();
            say(mc, message);
        }
        StocksClient.STORE.saveNow();
    }

    private static void say(Minecraft mc, String text) {
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("[LS Market] " + text));
    }

    /** Call every client tick, after Scanner.tick so the page has been read before we turn it. */
    public static void tick(Minecraft mc, MarketStore store) {
        if (state == State.IDLE) return;
        if (mc.player == null) {
            state = State.IDLE;
            queue.clear();
            return;
        }
        AbstractContainerScreen<?> cs = mc.gui.screen() instanceof AbstractContainerScreen<?> c ? c : null;
        int delayTicks = Math.max(6, store.data.scanDelayMs / 50);
        ticks++;

        switch (state) {
            case GAP -> {
                if (ticks >= 15) startNext(mc, store);
            }
            case WAIT_OPEN -> {
                if (cs != null && isMarketTitle(store, cs.getTitle().getString())) {
                    state = State.PAGE_WAIT;
                    ticks = 0;
                } else if (ticks > 100) {
                    finish(mc, "Could not open the " + currentName + " (check the command in Settings). "
                            + totalPages + " pages read.");
                }
            }
            case PAGE_WAIT -> {
                if (cs == null) {
                    finish(mc, "Stopped. " + totalPages + " pages read.");
                    return;
                }
                if (ticks < delayTicks) return;
                int slot = pages >= MAX_PAGES ? -1 : findNext(mc, store, cs);
                if (slot < 0) {
                    say(mc, currentName + " done (" + (pages + 1) + " pages).");
                    totalPages += pages + 1;
                    mc.player.closeContainer();
                    state = State.GAP;
                    ticks = 0;
                    if (queue.isEmpty()) finish(mc, "Finished. " + totalPages + " pages read.");
                    return;
                }
                sigBefore = pageSig(cs.getMenu(), mc);
                mc.gameMode.handleContainerInput(cs.getMenu().containerId, slot, 0, ContainerInput.PICKUP, mc.player);
                pages++;
                state = State.WAIT_CHANGE;
                ticks = 0;
            }
            case WAIT_CHANGE -> {
                if (cs == null) {
                    finish(mc, "Stopped. " + totalPages + " pages read.");
                    return;
                }
                if (!pageSig(cs.getMenu(), mc).equals(sigBefore)) {
                    state = State.PAGE_WAIT;
                    ticks = 0;
                } else if (ticks > 80) {
                    // The page never changed: that was the last page.
                    say(mc, currentName + " done (" + pages + " pages).");
                    totalPages += pages;
                    mc.player.closeContainer();
                    state = State.GAP;
                    ticks = 0;
                    if (queue.isEmpty()) finish(mc, "Finished. " + totalPages + " pages read.");
                }
            }
            default -> {
            }
        }
    }

    private static boolean isMarketTitle(MarketStore store, String title) {
        String t = title.toLowerCase(Locale.ROOT);
        return MarketStore.matchesAny(t, store.data.orderKeywords) || MarketStore.matchesAny(t, store.data.ahKeywords);
    }

    private static String pageSig(AbstractContainerMenu menu, Minecraft mc) {
        StringBuilder sb = new StringBuilder();
        for (Slot slot : menu.slots) {
            if (slot.container == mc.player.getInventory()) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            sb.append(slot.index).append(':').append(st.getItem()).append(st.getCount());
            ItemLore lore = st.get(DataComponents.LORE);
            if (lore != null && !lore.lines().isEmpty()) sb.append(lore.lines().get(0).getString());
            sb.append('|');
        }
        return sb.toString();
    }

    /** Returns the menu slot number of the "next page" button, or -1. Never returns a listing. */
    private static int findNext(Minecraft mc, MarketStore store, AbstractContainerScreen<?> cs) {
        List<Slot> slots = cs.getMenu().slots;
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.container == mc.player.getInventory()) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;

            StringBuilder text = new StringBuilder(st.getHoverName().getString());
            ItemLore lore = st.get(DataComponents.LORE);
            if (lore != null) {
                for (Component line : lore.lines()) text.append(' ').append(line.getString());
            }
            String full = text.toString();
            if (store.pattern().matcher(full).find()) continue; // looks like a priced listing: never click
            String lower = full.toLowerCase(Locale.ROOT);
            if (lower.contains("previous") || lower.contains("prev ") || lower.contains("back")) continue;
            if (MarketStore.matchesAny(lower, store.data.nextKeywords)) return i;
        }
        return -1;
    }
}
