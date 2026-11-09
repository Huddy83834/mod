package com.autocrafter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom recipes and ingredient flow, for CREATIVE mode only. In creative mode the game lets the
 * client set its own inventory slots, so no server mod is needed. In survival none of this runs
 * (the server decides what is in your inventory, so nothing can be created).
 */
public final class CreativeEngine {
    private static double[] acc = new double[0];
    private static final Deque<ItemStack> dropQueue = new ArrayDeque<>();
    private static int dropCooldown = 0;

    public static long crafted = 0;
    public static long flowed = 0;

    private CreativeEngine() {}

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.gameMode == null || !mc.player.isCreative()) {
            dropQueue.clear();
            return;
        }
        if (!ClientRecipes.DATA.autoCraft) return;

        // Drops are slow on purpose: the server limits how fast creative drops are accepted.
        if (dropCooldown > 0) dropCooldown--;
        if (dropCooldown == 0 && !dropQueue.isEmpty()) {
            mc.gameMode.handleCreativeModeItemDrop(dropQueue.poll());
            dropCooldown = 3;
        }

        List<RecipeDef> list = ClientRecipes.list();
        if (acc.length != list.size()) acc = new double[list.size()];
        Inventory inv = mc.player.getInventory();
        boolean atTable = mc.screen instanceof CraftingScreen;

        for (int r = 0; r < list.size(); r++) {
            RecipeDef def = list.get(r);
            if (!def.enabled || !def.customResult()) continue;
            Map<Item, Integer> needs = def.needs();
            Item result = RecipeDef.item(def.resultId);
            if (needs.isEmpty() || result == null) continue;

            if (def.flow) {
                acc[r] += Math.max(0.05, Math.min(20.0, def.flowRate)) / 20.0;
                int sets = (int) acc[r];
                if (sets > 0) {
                    acc[r] -= sets;
                    for (Map.Entry<Item, Integer> e : needs.entrySet()) {
                        flowed += give(mc, inv, e.getKey(), e.getValue() * Math.min(sets, 20));
                    }
                }
            }

            if (!def.autoCraft || (ClientRecipes.DATA.tableOnly && !atTable)) continue;
            Map<Item, Integer> have = new HashMap<>();
            for (int i = 0; i < 36; i++) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty() && needs.containsKey(s.getItem())) have.merge(s.getItem(), s.getCount(), Integer::sum);
            }
            int crafts = 64;
            for (Map.Entry<Item, Integer> e : needs.entrySet()) {
                crafts = Math.min(crafts, have.getOrDefault(e.getKey(), 0) / e.getValue());
            }
            if (crafts <= 0) continue;
            for (Map.Entry<Item, Integer> e : needs.entrySet()) take(mc, inv, e.getKey(), e.getValue() * crafts);
            crafted += (long) crafts * def.resultCount;
            deliver(mc, inv, result, crafts * def.resultCount, def.drop);
        }
    }

    /** Changes one inventory slot on the client and tells the server (allowed in creative mode). */
    private static void setSlot(Minecraft mc, Inventory inv, int i, ItemStack s) {
        inv.setItem(i, s);
        int menuSlot = i < 9 ? 36 + i : i;
        mc.gameMode.handleCreativeModeItemAdd(s, menuSlot);
    }

    private static int give(Minecraft mc, Inventory inv, Item item, int amount) {
        int max = new ItemStack(item).getMaxStackSize();
        int given = 0;
        for (int i = 0; i < 36 && amount > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || s.getItem() != item || s.getCount() >= max) continue;
            int n = Math.min(amount, max - s.getCount());
            ItemStack c = s.copy();
            c.setCount(s.getCount() + n);
            setSlot(mc, inv, i, c);
            amount -= n;
            given += n;
        }
        for (int i = 0; i < 36 && amount > 0; i++) {
            if (!inv.getItem(i).isEmpty()) continue;
            int n = Math.min(amount, max);
            setSlot(mc, inv, i, new ItemStack(item, n));
            amount -= n;
            given += n;
        }
        return given;
    }

    private static void take(Minecraft mc, Inventory inv, Item item, int amount) {
        for (int i = 0; i < 36 && amount > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || s.getItem() != item) continue;
            int n = Math.min(amount, s.getCount());
            ItemStack c = s.copy();
            c.setCount(s.getCount() - n);
            setSlot(mc, inv, i, c.isEmpty() ? ItemStack.EMPTY : c);
            amount -= n;
        }
    }

    private static void deliver(Minecraft mc, Inventory inv, Item item, int total, boolean drop) {
        if (drop) {
            int max = new ItemStack(item).getMaxStackSize();
            while (total > 0 && dropQueue.size() < 200) {
                int n = Math.min(total, max);
                dropQueue.add(new ItemStack(item, n));
                total -= n;
            }
        } else {
            give(mc, inv, item, total);
        }
    }
}
