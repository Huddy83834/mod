package com.lsstocks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
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
 * Updates the market without you opening anything: opens the Auction House and Orders with their
 * commands and turns pages at a fixed, adjustable pace until the last page.
 *
 * Safety:
 *  - it only ever clicks a slot that has no price in its text (never a listing) and whose name or
 *    text contains the configured "next page" keyword;
 *  - it stops and closes the menu the moment you take damage, or when you press ESC;
 *  - automatic updates only start when no menu is open, you have not been hurt recently, and the
 *    server address matches the filter in Settings.
 */
public final class Crawler {
    private enum State { IDLE, GAP, WAIT_OPEN, PAGE_WAIT, WAIT_CHANGE }

    private enum Mode { FULL, GOTO }

    private static final int MAX_PAGES = 400;

    private static State state = State.IDLE;
    private static Mode mode = Mode.FULL;
    private static final List<String> queue = new ArrayList<>();
    private static boolean auto;
    private static boolean phaseBid;
    private static String currentName = "";
    private static int ticks;
    private static int pages;
    private static int totalPages;
    private static int gotoPage;
    private static String sigBefore = "";
    private static long lastHurt;

    private Crawler() {}

    // ------------------------------------------------------------------ status for the UI / scanner

    public static boolean running() {
        return state != State.IDLE;
    }

    /** True while pages should be merged into a full update. */
    public static boolean collecting() {
        return mode == Mode.FULL && (state == State.WAIT_OPEN || state == State.PAGE_WAIT || state == State.WAIT_CHANGE);
    }

    /** 1-based number of the page currently on screen. */
    public static int page() {
        return pages + 1;
    }

    public static String status() {
        if (state == State.IDLE) return "";
        if (mode == Mode.GOTO) return "Going to page " + gotoPage + " (" + page() + ")";
        return "Updating " + currentName + " · page " + page();
    }

    // ------------------------------------------------------------------ starting

    public static void start(Minecraft mc, MarketStore store, boolean automatic) {
        if (mc.player == null || running()) return;
        mode = Mode.FULL;
        auto = automatic;
        queue.clear();
        queue.add("ah");
        queue.add("orders");
        totalPages = 0;
        if (mc.gui.screen() != null) mc.gui.setScreen(null);
        if (!automatic) Popups.info("Updating prices", "Reading every page. Press ESC to stop.");
        store.data.lastFullUpdate = System.currentTimeMillis();
        startNext(mc, store);
    }

    /** Opens the Auction House / Orders and turns pages until the given (1-based) page is showing. */
    public static void openPage(Minecraft mc, MarketStore store, boolean bid, int page) {
        if (mc.player == null || running() || page < 1) return;
        mode = Mode.GOTO;
        auto = false;
        gotoPage = page;
        queue.clear();
        if (mc.gui.screen() != null) mc.gui.setScreen(null);
        sendCommand(mc, bid ? store.data.orderCommand : store.data.ahCommand);
        phaseBid = bid;
        currentName = bid ? "Orders" : "Auction House";
        state = State.WAIT_OPEN;
        ticks = 0;
        pages = 0;
    }

    public static void stop(Minecraft mc, MarketStore store) {
        if (!running()) return;
        abort(mc, store, "Update stopped");
    }

    private static void sendCommand(Minecraft mc, String raw) {
        String cmd = raw.trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (!cmd.isEmpty() && mc.player != null) mc.player.connection.sendCommand(cmd);
    }

    private static void startNext(Minecraft mc, MarketStore store) {
        if (queue.isEmpty()) {
            finishAll(mc, store);
            return;
        }
        String key = queue.remove(0);
        boolean ah = key.equals("ah");
        phaseBid = !ah;
        currentName = ah ? "Auction House" : "Orders";
        String cmd = (ah ? store.data.ahCommand : store.data.orderCommand).trim();
        if (cmd.isEmpty()) {
            startNext(mc, store);
            return;
        }
        Scanner.beginSession();
        sendCommand(mc, cmd);
        state = State.WAIT_OPEN;
        ticks = 0;
        pages = 0;
    }

    // ------------------------------------------------------------------ ending

    private static void endPhase(Minecraft mc, MarketStore store, boolean complete) {
        if (mode == Mode.FULL) {
            Scanner.commit(mc, store, phaseBid, complete);
            totalPages += pages + 1;
        }
        if (mc.gui.screen() instanceof AbstractContainerScreen<?>) mc.player.closeContainer();
        state = State.GAP;
        ticks = 0;
        if (queue.isEmpty()) finishAll(mc, store);
    }

    private static void finishAll(Minecraft mc, MarketStore store) {
        state = State.IDLE;
        queue.clear();
        store.data.lastFullUpdate = System.currentTimeMillis();
        store.saveNow();
        if (!auto && mode == Mode.FULL) {
            Popups.info("Prices updated", totalPages + " pages read");
        }
    }

    private static void abort(Minecraft mc, MarketStore store, String reason) {
        if (mode == Mode.FULL && (state == State.PAGE_WAIT || state == State.WAIT_CHANGE)) {
            Scanner.commit(mc, store, phaseBid, false);
        }
        state = State.IDLE;
        queue.clear();
        if (mc.player != null && mc.gui.screen() instanceof AbstractContainerScreen<?>) mc.player.closeContainer();
        if (!auto || !reason.startsWith("Update stopped")) Popups.info("Update stopped", reason);
        store.saveNow();
    }

    // ------------------------------------------------------------------ tick

    /** Call every client tick, after Scanner.tick so the page has been read before we turn it. */
    public static void tick(Minecraft mc, MarketStore store) {
        if (mc.player != null && mc.player.hurtTime > 0) lastHurt = System.currentTimeMillis();
        autoTick(mc, store);
        if (state == State.IDLE) return;
        if (mc.player == null) {
            state = State.IDLE;
            queue.clear();
            return;
        }
        if (mc.player.hurtTime > 0) {
            abort(mc, store, "You took damage, so the update was cancelled");
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
                    abort(mc, store, "Could not open the " + currentName + ". Check its command in Settings.");
                }
            }
            case PAGE_WAIT -> {
                if (cs == null) {
                    abort(mc, store, "Update stopped");
                    return;
                }
                if (ticks < delayTicks) return;
                if (mode == Mode.GOTO && page() >= gotoPage) {
                    state = State.IDLE;
                    Popups.info("Page " + gotoPage, "Look for it on this page");
                    return;
                }
                int slot = pages >= MAX_PAGES ? -1 : findNext(mc, store, cs);
                if (slot < 0) {
                    if (mode == Mode.GOTO) {
                        state = State.IDLE;
                        Popups.info("Last page", "That listing is no longer there");
                    } else {
                        endPhase(mc, store, true);
                    }
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
                    abort(mc, store, "Update stopped");
                    return;
                }
                if (!pageSig(cs.getMenu(), mc).equals(sigBefore)) {
                    state = State.PAGE_WAIT;
                    ticks = 0;
                } else if (ticks > 80) {
                    // The page never changed: that was the last page.
                    pages--;
                    if (mode == Mode.GOTO) {
                        state = State.IDLE;
                        Popups.info("Last page", "That listing is no longer there");
                    } else {
                        endPhase(mc, store, true);
                    }
                }
            }
            default -> {
            }
        }
    }

    /** Starts a full update by itself every few minutes, when it is safe to. */
    private static void autoTick(Minecraft mc, MarketStore store) {
        if (state != State.IDLE || !store.data.autoUpdate) return;
        if (mc.player == null || mc.level == null || mc.gui.screen() != null) return;
        if (mc.player.tickCount < 1200 || mc.player.isDeadOrDying()) return;
        long now = System.currentTimeMillis();
        if (now - lastHurt < 30_000L) return;
        if (now < store.data.lastFullUpdate + store.data.autoSeconds * 1000L) return;

        String filter = store.data.serverFilter.trim().toLowerCase(Locale.ROOT);
        if (!filter.isEmpty()) {
            ServerData server = mc.getCurrentServer();
            String ip = server == null ? "" : server.ip.toLowerCase(Locale.ROOT);
            if (!ip.contains(filter)) return;
        }
        start(mc, store, true);
    }

    // ------------------------------------------------------------------ helpers

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
