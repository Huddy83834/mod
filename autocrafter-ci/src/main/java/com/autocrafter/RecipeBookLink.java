package com.autocrafter;

import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The recipes the server put in your recipe book. Server plugins (like lifesteal heart recipes) usually add
 * their recipes there. Asking the server to "place recipe" lets the SERVER put the right items in the grid,
 * which works even when the recipe needs special custom items.
 */
public final class RecipeBookLink {
    public static final class Book {
        public final RecipeDisplayEntry entry;
        public final boolean shapeless;
        /** For each of the 9 grid slots, the item ids that fit (empty set = slot stays empty). */
        public final List<Set<String>> options;
        /** The exact item for a slot, if the recipe asks for one special item. */
        public final ItemStack[] exact;
        public final ItemStack result;
        public final String key;

        Book(RecipeDisplayEntry entry, boolean shapeless, List<Set<String>> options, ItemStack[] exact, ItemStack result) {
            this.entry = entry;
            this.shapeless = shapeless;
            this.options = options;
            this.exact = exact;
            this.result = result;
            StringBuilder sb = new StringBuilder(Stacks.name(result)).append('|');
            for (int i = 0; i < 9; i++) {
                ItemStack e = exact[i];
                sb.append(e == null || e.isEmpty() ? (options.get(i).isEmpty() ? "-" : String.join("/", new java.util.TreeSet<>(options.get(i))))
                        : Stacks.id(e.getItem()) + "~" + Stacks.name(e)).append(',');
            }
            this.key = sb.toString();
        }

        public boolean custom() {
            if (Stacks.isCustom(result)) return true;
            for (ItemStack e : exact) if (e != null && !e.isEmpty() && Stacks.isCustom(e)) return true;
            return false;
        }
    }

    private static List<Book> books = new ArrayList<>();

    private RecipeBookLink() {}

    public static List<Book> books() {
        return books;
    }

    public static void refresh(Minecraft mc) {
        List<Book> out = new ArrayList<>();
        try {
            if (mc.player == null || mc.level == null) return;
            ClientRecipeBook rb = mc.player.getRecipeBook();
            ContextMap ctx = SlotDisplayContext.fromLevel(mc.level);
            Set<Integer> seen = new HashSet<>();
            for (RecipeCollection c : rb.getCollections()) {
                for (RecipeDisplayEntry e : c.getRecipes()) {
                    if (!seen.add(e.id().index())) continue;
                    Book b = build(e, ctx);
                    if (b != null) out.add(b);
                }
            }
        } catch (Exception ex) {
            Log.add("Could not read the recipe book: " + ex.getClass().getSimpleName());
        }
        books = out;
    }

    private static Book build(RecipeDisplayEntry e, ContextMap ctx) {
        RecipeDisplay d = e.display();
        List<SlotDisplay> grid = new ArrayList<>(java.util.Collections.nCopies(9, (SlotDisplay) null));
        boolean shapeless;
        if (d instanceof ShapedCraftingRecipeDisplay s) {
            if (s.width() > 3 || s.height() > 3) return null;
            for (int y = 0; y < s.height(); y++) {
                for (int x = 0; x < s.width(); x++) grid.set(y * 3 + x, s.ingredients().get(y * s.width() + x));
            }
            shapeless = false;
        } else if (d instanceof ShapelessCraftingRecipeDisplay s) {
            if (s.ingredients().size() > 9) return null;
            for (int i = 0; i < s.ingredients().size(); i++) grid.set(i, s.ingredients().get(i));
            shapeless = true;
        } else {
            return null;
        }
        List<Set<String>> options = new ArrayList<>();
        ItemStack[] exact = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            Set<String> ids = new HashSet<>();
            SlotDisplay sd = grid.get(i);
            if (sd != null) {
                if (sd instanceof SlotDisplay.ItemStackSlotDisplay isd) {
                    exact[i] = isd.stack().copy();
                    ids.add(Stacks.id(isd.stack().getItem()));
                } else {
                    for (ItemStack st : sd.resolveForStacks(ctx)) if (!st.isEmpty()) ids.add(Stacks.id(st.getItem()));
                }
            }
            options.add(ids);
        }
        ItemStack result = d.result().resolveForFirstStack(ctx);
        if (result.isEmpty()) return null;
        return new Book(e, shapeless, options, exact, result.copy());
    }

    /** Finds the recipe-book recipe that matches a saved recipe, or null. */
    public static Book find(RecipeDef r) {
        if (!r.bookKey.isEmpty()) {
            for (Book b : books) if (b.key.equals(r.bookKey)) return b;
        }
        String[] ids = new String[9];
        for (int i = 0; i < 9; i++) {
            RecipeDef.Ing ing = r.ingredients.get(i);
            ids[i] = ing.empty() ? "" : ing.id;
        }
        ItemStack learned = Stacks.decode(r.learnedResult);
        Book best = null;
        for (Book b : books) {
            if (!matches(b, ids)) continue;
            if (!learned.isEmpty() && !Stacks.sameResult(b.result, learned)) continue;
            // prefer a server (custom) recipe over a plain game recipe with the same layout
            if (best == null || (b.custom() && !best.custom())) best = b;
        }
        return best;
    }

    private static boolean matches(Book b, String[] ids) {
        if (b.shapeless) {
            List<String> want = new ArrayList<>();
            for (String s : ids) if (!s.isEmpty()) want.add(s);
            List<Set<String>> have = new ArrayList<>();
            for (Set<String> o : b.options) if (!o.isEmpty()) have.add(o);
            if (want.size() != have.size()) return false;
            boolean[] used = new boolean[have.size()];
            outer:
            for (String w : want) {
                for (int j = 0; j < have.size(); j++) {
                    if (!used[j] && have.get(j).contains(w)) {
                        used[j] = true;
                        continue outer;
                    }
                }
                return false;
            }
            return true;
        }
        // shaped: compare with both layouts moved to the top-left corner
        int[] a = shift(i -> !ids[i].isEmpty());
        int[] c = shift(i -> !b.options.get(i).isEmpty());
        if (a == null || c == null) return false;
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                int ay = y + a[0], ax = x + a[1], cy = y + c[0], cx = x + c[1];
                String id = ay < 3 && ax < 3 ? ids[ay * 3 + ax] : "";
                Set<String> opt = cy < 3 && cx < 3 ? b.options.get(cy * 3 + cx) : Set.of();
                if (id.isEmpty() != opt.isEmpty()) return false;
                if (!id.isEmpty() && !opt.contains(id)) return false;
            }
        }
        return true;
    }

    /** Top row and left column that are used, or null if the grid is empty. */
    private static int[] shift(java.util.function.IntPredicate used) {
        int top = 3, left = 3;
        for (int i = 0; i < 9; i++) {
            if (used.test(i)) {
                top = Math.min(top, i / 3);
                left = Math.min(left, i % 3);
            }
        }
        return top == 3 ? null : new int[]{top, left};
    }

    public static boolean canCraft(Minecraft mc, Book b) {
        try {
            StackedItemContents sc = new StackedItemContents();
            mc.player.getInventory().fillStackedContents(sc);
            return b.entry.canCraft(sc);
        } catch (Exception e) {
            return false;
        }
    }

    /** Adds the server's custom recipes (ones that make a custom item) to the list, switched off. Returns how many. */
    public static int importCustom() {
        List<RecipeDef> list = ClientRecipes.list();
        int added = 0;
        for (Book b : books) {
            if (!b.custom() || list.size() >= 24) continue;
            boolean known = false;
            for (RecipeDef r : list) {
                if (b.key.equals(r.bookKey) || find(r) == b) {
                    known = true;
                    break;
                }
            }
            if (known) continue;
            RecipeDef r = new RecipeDef();
            r.source = "server";
            r.name = Stacks.name(b.result);
            if (r.name.length() > 28) r.name = r.name.substring(0, 28);
            r.enabled = false;
            r.drop = false;
            r.bookKey = b.key;
            r.learnedResult = Stacks.encode(b.result);
            for (int i = 0; i < 9; i++) {
                RecipeDef.Ing ing = r.ingredients.get(i);
                if (b.options.get(i).isEmpty()) continue;
                ItemStack ex = b.exact[i];
                if (ex != null && !ex.isEmpty()) {
                    ing.id = Stacks.id(ex.getItem());
                    ing.count = Math.max(1, ex.getCount());
                    ing.exact = Stacks.encode(ex);
                } else {
                    ing.id = b.options.get(i).stream().sorted().findFirst().orElse("");
                    ing.count = 1;
                }
            }
            list.add(r);
            added++;
            Log.add("Found server recipe: " + r.name + " (switch it on to auto-craft).");
        }
        if (added > 0) ClientRecipes.changed();
        return added;
    }

    static String describeIds(List<Set<String>> o) {
        return Arrays.toString(o.toArray());
    }
}
