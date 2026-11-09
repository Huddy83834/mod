package com.autocrafter;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One custom recipe: up to 9 ingredient slots and a result. Stored as JSON, shared by client and server. */
public final class RecipeDef {
    public static final int SLOTS = 9;

    public static final class Ing {
        public String id = "";
        public int count = 1;
        /** The exact item (with its custom name, lore, model...) saved as JSON. Empty = any item of this type. */
        public String exact = "";

        public Ing() {}

        public Ing(String id, int count) {
            this.id = id;
            this.count = count;
        }

        public boolean empty() {
            return id == null || id.isEmpty() || count <= 0;
        }
    }

    public static final class Holder {
        public List<RecipeDef> recipes = new ArrayList<>();
        /** true: only craft while a crafting table window is open. false: craft anywhere (creative custom recipes). */
        public boolean tableOnly = false;
        /** Master switch. */
        public boolean autoCraft = true;
    }

    public String name = "New recipe";
    public boolean enabled = true;
    /** Craft this recipe by itself. */
    public boolean autoCraft = true;
    public List<Ing> ingredients = new ArrayList<>();
    public String resultId = "";
    public int resultCount = 1;
    /** true: drop the result on the ground. false: put it in the inventory. */
    public boolean drop = true;
    /** Keep adding the ingredients to the inventory. */
    public boolean flow = false;
    /** How many full sets of ingredients arrive per second. */
    public double flowRate = 1.0;
    /** "" = made in the editor, "learned" = copied from the crafting grid, "server" = found in the recipe book. */
    public String source = "";
    /** The result the server showed when this was learned (JSON). Used to check the right thing is being crafted. */
    public String learnedResult = "";
    /** Identifies the recipe-book recipe this came from. */
    public String bookKey = "";

    public RecipeDef() {
        fill();
    }

    public void fill() {
        if (ingredients == null) ingredients = new ArrayList<>();
        while (ingredients.size() < SLOTS) ingredients.add(new Ing());
        while (ingredients.size() > SLOTS) ingredients.remove(ingredients.size() - 1);
        for (int i = 0; i < SLOTS; i++) if (ingredients.get(i) == null) ingredients.set(i, new Ing());
        if (name == null) name = "Recipe";
        if (resultId == null) resultId = "";
        if (source == null) source = "";
        if (learnedResult == null) learnedResult = "";
        if (bookKey == null) bookKey = "";
        for (Ing ing : ingredients) if (ing.exact == null) ing.exact = "";
    }

    public boolean hasExact() {
        for (Ing ing : ingredients) if (!ing.empty() && !ing.exact.isEmpty()) return true;
        return false;
    }

    /** Looks an item up by id (works for modded items). Returns null if it is unknown. */
    public static Item item(String id) {
        if (id == null || id.isEmpty()) return null;
        Identifier rl = Identifier.tryParse(id);
        if (rl == null) return null;
        Item it = BuiltInRegistries.ITEM.getValue(rl);
        return it == null || it == Items.AIR ? null : it;
    }

    /** Total amount needed of each item (same item in several slots is added up). */
    public Map<Item, Integer> needs() {
        Map<Item, Integer> out = new LinkedHashMap<>();
        for (Ing ing : ingredients) {
            if (ing.empty()) continue;
            Item it = item(ing.id);
            if (it == null) continue;
            out.merge(it, ing.count, Integer::sum);
        }
        return out;
    }

    /** Like needs(), but one of each filled slot: how a real crafting recipe uses its ingredients. */
    public Map<Item, Integer> unitNeeds() {
        Map<Item, Integer> out = new LinkedHashMap<>();
        for (Ing ing : ingredients) {
            if (ing.empty()) continue;
            Item it = item(ing.id);
            if (it == null) continue;
            out.merge(it, 1, Integer::sum);
        }
        return out;
    }

    /** True once at least one ingredient is chosen. The result is optional (the game's own recipe is used). */
    public boolean finished() {
        return !needs().isEmpty();
    }

    /** True if the player chose a result item (a custom recipe) instead of using the game's recipe. */
    public boolean customResult() {
        return item(resultId) != null;
    }
}
