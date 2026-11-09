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
 * Client-side auto crafting. While a crafting table window is open it does what you would do by hand,
 * using ordinary inventory clicks that any server accepts:
 *   1. spread the layout's ingredients over the crafting grid,
 *   2. shift-click the result (this crafts everything the grid allows),
 *   3. shift-click what is left in the grid back into the inventory.
 * Nothing is created from nothing: it only crafts with items you really have, using the game's own recipes.
 */
public final class Crafter {
    private enum S { IDLE, PLACED, CRAFTED }

    private static S state = S.IDLE;
    private static int wait = 0;
    private static int current = -1;
    private static int lastMenuId = -1;
    private static int cleanTries = 0;
    private static final Set<Integer> blocked = new HashSet<>();
    private static int lastTried = -1;

    /** Batches crafted since the game started, and a short message for the window. */
    public static long batches = 0;
    public static String message = "Open a crafting table to start.";

    private Crafter() {}

    private static void reset() {
        state = S.IDLE;
        wait = 0;
        current = -1;
        cleanTries = 0;
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
            wait = 3; // let the window finish loading
        }
        if (wait > 0) {
            wait--;
            return;
        }

        switch (state) {
            case IDLE -> startRound(mc, menu);
            case PLACED -> {
                if (menu.slots.get(0).hasItem()) {
                    click(mc, menu, 0, 0, ClickType.QUICK_MOVE); // crafts as many as the grid holds
                    state = S.CRAFTED;
                    wait = 2;
                } else {
                    // The game has no recipe for this layout.
                    blocked.add(current);
                    message = "No game recipe for \"" + name(current) + "\".";
                    cleanGrid(mc, menu);
                    state = S.IDLE;
                    wait = 2;
                }
            }
            case CRAFTED -> {
                if (menu.slots.get(0).hasItem()) {
                    // The result could not be moved: the inventory is full.
                    blocked.add(current);
                    message = "Inventory full. Make room and reopen the table.";
                } else {
                    batches++;
                    message = "Crafted \"" + name(current) + "\".";
                }
                cleanGrid(mc, menu);
                state = S.IDLE;
                wait = 2;
            }
        }
    }

    private static String name(int idx) {
        List<RecipeDef> l = ClientRecipes.list();
        return idx >= 0 && idx < l.size() ? l.get(idx).name : "?";
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
        if (!menu.getCarried().isEmpty()) return; // wait until the cursor is free
        if (!gridEmpty(menu)) {
            if (++cleanTries > 4) {
                message = "The crafting grid is stuck. Make room in your inventory.";
                return;
            }
            cleanGrid(mc, menu);
            wait = 2;
            return;
        }
        cleanTries = 0;

        List<RecipeDef> list = ClientRecipes.list();
        for (int n = 0; n < list.size(); n++) {
            int idx = (lastTried + 1 + n) % list.size();
            RecipeDef def = list.get(idx);
            if (!def.enabled || !def.finished() || blocked.contains(idx)) continue;
            if (place(mc, menu, def)) {
                current = idx;
                lastTried = idx;
                state = S.PLACED;
                wait = 3;
                message = "Crafting \"" + def.name + "\"...";
                return;
            }
        }
        // nothing left to do
        if (batches > 0 && message.startsWith("Crafting")) message = "Done. Nothing more to craft.";
    }

    /**
     * Puts the layout's ingredients into the grid: each item is picked up once and spread evenly over
     * every slot that needs it. Returns false if the inventory does not have the ingredients.
     */
    private static boolean place(Minecraft mc, CraftingMenu menu, RecipeDef def) {
        // item -> grid slots (menu slot numbers 1..9)
        Map<Item, List<Integer>> needs = new LinkedHashMap<>();
        for (int i = 0; i < RecipeDef.SLOTS; i++) {
            Item it = RecipeDef.item(def.slots.get(i));
            if (it == null) continue;
            needs.computeIfAbsent(it, k -> new ArrayList<>()).add(i + 1);
        }
        if (needs.isEmpty()) return false;

        // Find the biggest stack of each item in the player's inventory.
        Map<Item, Integer> origin = new LinkedHashMap<>();
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
        }

        for (Map.Entry<Item, List<Integer>> e : needs.entrySet()) {
            int from = origin.get(e.getKey());
            List<Integer> targets = e.getValue();
            click(mc, menu, from, 0, ClickType.PICKUP);
            if (targets.size() == 1) {
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
