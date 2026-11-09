package com.autocrafter;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/** Saving exact items (with custom names, lore, models) and comparing them. */
public final class Stacks {
    private static final Map<String, ItemStack> CACHE = new HashMap<>();
    private static Object cacheLevel = null;

    private Stacks() {}

    private static DynamicOps<JsonElement> ops(Minecraft mc) {
        return mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
    }

    public static String encode(ItemStack s) {
        Minecraft mc = Minecraft.getInstance();
        if (s == null || s.isEmpty() || mc.level == null) return "";
        try {
            return ItemStack.CODEC.encodeStart(ops(mc), s).result().map(JsonElement::toString).orElse("");
        } catch (Exception e) {
            return "";
        }
    }

    /** Returns the saved item, or EMPTY. */
    public static ItemStack decode(String json) {
        Minecraft mc = Minecraft.getInstance();
        if (json == null || json.isEmpty() || mc.level == null) return ItemStack.EMPTY;
        if (cacheLevel != mc.level) {
            CACHE.clear();
            cacheLevel = mc.level;
        }
        ItemStack c = CACHE.get(json);
        if (c != null) return c;
        ItemStack out;
        try {
            out = ItemStack.CODEC.parse(ops(mc), JsonParser.parseString(json)).result().orElse(ItemStack.EMPTY);
        } catch (Exception e) {
            out = ItemStack.EMPTY;
        }
        CACHE.put(json, out);
        return out;
    }

    public static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    public static String name(ItemStack s) {
        return s == null || s.isEmpty() ? "nothing" : s.getHoverName().getString();
    }

    public static String describe(ItemStack s) {
        if (s == null || s.isEmpty()) return "nothing";
        return s.getCount() + "x " + name(s) + (isCustom(s) ? " (custom)" : "");
    }

    /** True if the server changed the item's look or name (custom items, hearts, fragments...). */
    public static boolean isCustom(ItemStack s) {
        return s.hasNonDefault(DataComponents.CUSTOM_NAME) || s.hasNonDefault(DataComponents.ITEM_NAME)
                || s.hasNonDefault(DataComponents.LORE) || s.hasNonDefault(DataComponents.CUSTOM_MODEL_DATA)
                || s.hasNonDefault(DataComponents.ITEM_MODEL);
    }

    /**
     * How well an inventory item fits a recipe slot: 0 = not at all, higher = better.
     * With an exact saved item: 4 = identical, 3 = same type and name, 1 = same type only.
     * Without one: 2 = plain item, 1 = custom version of the item (or the reverse when preferCustom).
     */
    public static int fit(ItemStack inv, Item item, ItemStack exact, boolean preferCustom) {
        if (inv.isEmpty() || inv.getItem() != item) return 0;
        if (exact != null && !exact.isEmpty()) {
            if (ItemStack.isSameItemSameComponents(inv, exact)) return 4;
            if (name(inv).equals(name(exact))) return 3;
            return 1;
        }
        boolean custom = !inv.getComponentsPatch().isEmpty();
        return custom == preferCustom ? 2 : 1;
    }

    /** True if a result the server shows is the thing that was learned. */
    public static boolean sameResult(ItemStack shown, ItemStack learned) {
        if (learned == null || learned.isEmpty()) return true;
        return shown.getItem() == learned.getItem() && name(shown).equals(name(learned));
    }
}
