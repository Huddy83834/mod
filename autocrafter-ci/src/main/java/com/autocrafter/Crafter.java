package com.autocrafter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client-side auto crafting through the crafting table window, using ordinary inventory clicks
 * that any server accepts:
 *   1. put the layout's ingredients into the grid,
 *   2. wait for the server to show the result (any recipe the server has, including custom ones),
 *   3. shift-click the result (crafts everything the grid holds), or drop it if "drop" is on,
 *   4. shift-click what is left in the grid back into the inventory.
 */
public final class Crafter {
    private enum S { IDLE, PLACED, CRAFTED, THROWING }

    /** How long to wait for the server to show a result, in ticks (2 seconds: slow servers are fine). */
    private static final int RESULT_TIMEOUT = 40;

    private static S state = S.IDLE;
    private static int wait = 0;
    private static int current = -1;
    private static int lastMenuId = -1;
    private static int cleanTries = 0;
    private static int waited = 0;
    private static int attempt = 0; // 0 = fast spread placement, 1 = slow placement (fallback)
    private static int throwsDone = 0;
    private static final Set<Integer> blocked = new HashSet<>();
    private static int lastTried = -1;

    public static long batches = 0;
    public static String message = "Open a crafting table to start.";

    private Crafter() {}

    private static void reset() {
        state = S.IDLE;
        wait = 0;
        current = -1;
        cleanTries = 0;
        waited = 0;
        attempt = 0;
        throwsDone = 0;
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.gameMode == null || !ClientRecipes.DATA.autoCraft
                || !(mc.screen instanceof CraftingScreen) || !(mc.player.containerMenu instanceof CraftingMenu menu)) {
            if (lastMenuId != -1) message = "Open a crafting table to start.";
            lastMenuId = -1;
            reset();
            return;
        }
        if (menu.containerId != lastMenuId) {
            lastMenuId = menu.containerId;
            blocked.clear();
            lastTried = -1;
            reset();
            wait = 3;
        }
        if (wait > 0) {
            wait--;
            return;
        }

        switch (state) {
            case IDLE -> startRound(mc, menu);
            case PLACED -> {
                RecipeDef def = def(current);
                if (def == null) {
                    reset();
                    return;
                }
                if (menu.slots.get(0).hasItem()) {
                    // The server showed a result.
                    if (def.drop) {
                        state = S.THROWING;
                        throwsDone = 0;
                    } else {
                        click(mc, menu, 0, 0, ClickType.QUICK_MOVE);
                        state = S.CRAFTED;
                        wait = 2;
                    }
                } else if (++waited > RESULT_TIMEOUT) {
                    if (attempt == 0 && multiSlot(def)) {
                        // Try again, placing items one by one instead of spreading them.
                        cleanGrid(mc, menu);
                        attempt = 1;
                        waited = 0;
                        state = S.IDLE;
                        wait = 4;
                    } else {
                        blocked.add(current);
                        message = "The server has no recipe for \"" + def.name + "\" (or needs items in other slots).";
                        cleanGrid(mc, menu);
                        state = S.IDLE;
                        attempt = 0;
                        waited = 0;
                        wait = 3;
                    }
                }
            }
            case THROWING -> {
                // Drop mode: one craft's worth per click, as fast as the server answers.
                if (menu.slots.get(0).hasItem() && throwsDone < 2000) {
                    click(mc, menu, 0, 1, ClickType.THROW);
                    throwsDone++;
                } else {
                    batches++;
                    message = "Dropped \"" + name(current) + "\" x" + throwsDone + ".";
                    cleanGrid(mc, menu);
                    state = S.IDLE;
                    attempt = 0;
                    waited = 0;
                    wait = 3;
                }
            }
            case CRAFTED -> {
                if (menu.slots.get(0).hasItem()) {
                    blocked.add(current);
                    message = "Inventory full. Make room and reopen the table.";
                } else {
                    batches++;
                    message = "Crafted \"" + name(current) + "\".";
                }
                cleanGrid(mc, menu);
                state = S.IDLE;
                attempt = 0;
                waited = 0;
                wait = 3;
            }
        }
    }

    private static RecipeDef def(int idx) {
        List<RecipeDef> l = ClientRecipes.list();
        return idx >= 0 && idx < l.size() ? l.get(idx) : null;
    }

    private static String name(int idx) {
        RecipeDef d = def(idx);
        return d == null ? "?" : d.name;
    }

    private static boolean multiSlot(RecipeDef def) {
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (RecipeDef.Ing ing : def.ingredients) {
            if (ing.empty()) continue;
            if (seen.merge(ing.id, 1, Integer::sum) > 1) return true;
        }
        return false;
    }

    private static boolean gridEmpty(AbstractContainerMenu menu) {
        for (int i = 1; i <= RecipeDef.SLOTS; i++) if (menu.slots.get(i).hasItem()) return false;
        return true;
    }

    private static void cleanGrid(Minecraft mc, AbstractContainerMenu menu) {
        for (int i = 1; i <= RecipeDef.SLOTS; i++) {
            if (menu.slots.get(i).hasItem()) click(mc, menu, i, 0, ClickType.QUICK_MOVE);
        }
    }

    private static void startRound(Minecraft mc, CraftingMenu menu) {
        if (!menu.getCarried().isEmpty()) return;
        if (!gridEmpty(menu)) {
            if (++cleanTries > 4) {
                message = "The crafting grid is stuck. Make room in your inventory.";
                return;
            }
            cleanGrid(mc, menu);
            wait = 3;
            return;
        }
        cleanTries = 0;

        List<RecipeDef> list = ClientRecipes.list();
        // Retrying the same layout in slow mode?
        if (attempt == 1 && current >= 0 && current < list.size()) {
            RecipeDef def = list.get(current);
            if (place(mc, menu, def, true)) {
                state = S.PLACED;
                waited = 0;
                wait = 3;
                return;
            }
            attempt = 0;
        }
        for (int n = 0; n < list.size(); n++) {
            int idx = (lastTried + 1 + n) % list.size();
            RecipeDef def = list.get(idx);
            if (!def.enabled || !def.autoCraft || !def.finished() || blocked.contains(idx)) continue;
            if (place(mc, menu, def, false)) {
                current = idx;
                lastTried = idx;
                state = S.PLACED;
                waited = 0;
                wait = 3;
                message = "Crafting \"" + def.name + "\"...";
                return;
            }
        }
        if (message.startsWith("Crafting")) message = "Done. Nothing more to craft.";
    }

    /**
     * Puts the layout's ingredients into the grid. Fast mode picks each item up once and spreads it
     * evenly over its slots. Slow mode places a few items per slot with single clicks.
     * Returns false if the inventory does not have the ingredients.
     */
    private static boolean place(Minecraft mc, CraftingMenu menu, RecipeDef def, boolean slow) {
        Map<Item, List<Integer>> needs = new LinkedHashMap<>();
        for (int i = 0; i < RecipeDef.SLOTS; i++) {
            RecipeDef.Ing ing = def.ingredients.get(i);
            Item it = ing.empty() ? null : RecipeDef.item(ing.id);
            if (it == null) continue;
            needs.computeIfAbsent(it, k -> new ArrayList<>()).add(i + 1);
        }
        if (needs.isEmpty()) return false;

        Map<Item, Integer> origin = new LinkedHashMap<>();
        Map<Item, Integer> stackSize = new LinkedHashMap<>();
        for (Map.Entry<Item, List<Integer>> e : needs.entrySet()) {
            int best = -1;
            int bestCount = 0;
            for (int s = 10; s < menu.slots.size(); s++) {
                Slot slot = menu.slots.get(s);
                if (slot.container != mc.player.getInventory()) continue;
                ItemStack st = slot.getItem();
                if (!st.isEmpty() && st.getItem() == e.getKey() && st.getCount() > bestCount) {
                    best = s;
                    bestCount = st.getCount();
                }
            }
            if (best < 0 || bestCount < e.getValue().size()) return false;
            origin.put(e.getKey(), best);
            stackSize.put(e.getKey(), bestCount);
        }

        for (Map.Entry<Item, List<Integer>> e : needs.entrySet()) {
            int from = origin.get(e.getKey());
            List<Integer> targets = e.getValue();
            click(mc, menu, from, 0, ClickType.PICKUP);
            if (slow) {
                int each = Math.max(1, Math.min(8, stackSize.get(e.getKey()) / targets.size()));
                for (int t : targets) {
                    for (int k = 0; k < each; k++) click(mc, menu, t, 1, ClickType.PICKUP); // right-click puts one
                }
            } else if (targets.size() == 1) {
                click(mc, menu, targets.get(0), 0, ClickType.PICKUP);
            } else {
                click(mc, menu, -999, AbstractContainerMenu.getQuickcraftMask(0, 0), ClickType.QUICK_CRAFT);
                for (int t : targets) click(mc, menu, t, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT);
                click(mc, menu, -999, AbstractContainerMenu.getQuickcraftMask(2, 0), ClickType.QUICK_CRAFT);
            }
            if (!menu.getCarried().isEmpty()) click(mc, menu, from, 0, ClickType.PICKUP);
        }
        return true;
    }

    private static void click(Minecraft mc, AbstractContainerMenu menu, int slot, int button, ClickType type) {
        mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, button, type, mc.player);
    }
}
