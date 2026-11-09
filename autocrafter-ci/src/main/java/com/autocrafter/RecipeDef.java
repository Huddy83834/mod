package com.autocrafter;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** One crafting layout: what goes in each of the 9 crafting table slots (row by row). */
public final class RecipeDef {
    public static final int SLOTS = 9;

    public static final class Holder {
        public List<RecipeDef> recipes = new ArrayList<>();
        /** Craft by itself whenever a crafting table window opens. */
        public boolean autoCraft = true;
    }

    public String name = "New recipe";
    public boolean enabled = true;
    /** Item ids per slot, "" = empty. */
    public List<String> slots = new ArrayList<>();

    public RecipeDef() {
        fill();
    }

    public void fill() {
        if (slots == null) slots = new ArrayList<>();
        while (slots.size() < SLOTS) slots.add("");
        while (slots.size() > SLOTS) slots.remove(slots.size() - 1);
        for (int i = 0; i < SLOTS; i++) if (slots.get(i) == null) slots.set(i, "");
        if (name == null) name = "Recipe";
    }

    /** Looks an item up by id (works for modded items). Returns null if unknown. */
    public static Item item(String id) {
        if (id == null || id.isEmpty()) return null;
        Identifier rl = Identifier.tryParse(id);
        if (rl == null) return null;
        Item it = BuiltInRegistries.ITEM.getValue(rl);
        return it == null || it == Items.AIR ? null : it;
    }

    public boolean finished() {
        for (String s : slots) if (item(s) != null) return true;
        return false;
    }
}
