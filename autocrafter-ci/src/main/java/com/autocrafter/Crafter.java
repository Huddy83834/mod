package com.autocrafter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client-side auto crafting through the crafting table window, using only normal clicks and the normal
 * recipe-book button, so nothing is needed on the server.
 *
 * Every round:
 *   1. put the ingredients in the grid. Several ways are tried, best first:
 *        BOOK   - ask the server to fill the grid from its recipe book (best for custom server recipes)
 *        SPREAD - spread the items over their slots
 *        SPREAD_CUSTOM - same, but use the custom versions of items first
 *        ONE    - put exactly one item in each slot
 *        COUNT  - put the saved amount in each slot
 *   2. wait for the server to show the result
 *   3. take the result. Several ways are tried, until one really crafts:
 *        shift-click (or Q when dropping), pick it up with the mouse, drop it with Q
 *   4. put what is left back in the inventory
 * The way that worked is remembered for each recipe. Items you put in the grid yourself are never touched.
 */
public final class Crafter {
    private enum S { IDLE, WAIT_RESULT, TAKE, VERIFY }

    enum Mode { BOOK, SPREAD, SPREAD_CUSTOM, ONE, COUNT }

    private static final String[] TAKE_NAMES = {"shift-click", "pick it up with the mouse", "drop it with Q"};

    private static S state = S.IDLE;
    private static int wait = 0;
    private static int waited = 0;
    private static int lastMenuId = -1;
    private static int rr = -1;
    private static int cleanTries = 0;
    private static int userPause = 0;
    private static boolean ownGrid = false;
    private static boolean retry = false;

    private static RecipeDef cur = null;
    private static RecipeBookLink.Book curBook = null;
    private static List<Mode> modes = List.of();
    private static int modeIdx = 0;
    private static int takeMode = 0;
    private static int gridBefore = 0;
    private static int takeLoops = 0;
    private static int emptyWait = 0;
    private static int roundCrafts = 0;
    private static int stall = 0;

    private static final Set<RecipeDef> blocked = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<RecipeDef, Integer> goodTake = new IdentityHashMap<>();
    private static final Map<RecipeDef, Mode> goodMode = new IdentityHashMap<>();

    public static long batches = 0;
    public static long crafts = 0;
    public static String message = "Open a crafting table to start.";

    private Crafter() {}

    /** True while the mod has its own items in the grid. */
    public static boolean busy() {
        return ownGrid || state != S.IDLE;
    }

    /** After "Learn": the items in the grid may be moved by the mod now. */
    public static void adoptGrid() {
        ownGrid = true;
        userPause = 0;
    }

    /** Lets recipes that failed be tried again (after changing settings). */
    public static void unblock() {
        blocked.clear();
    }

    private static void resetRound() {
        state = S.IDLE;
        wait = 0;
        waited = 0;
        cur = null;
        curBook = null;
        retry = false;
        modeIdx = 0;
        takeMode = 0;
        roundCrafts = 0;
        cleanTries = 0;
    }

    /** Server round trip in ticks (at least 1). */
    private static int lag(Minecraft mc) {
        try {
            PlayerInfo pi = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            int ms = pi == null ? 100 : pi.getLatency();
            return Math.max(1, Math.min(20, ms / 50 + 1));
        } catch (Exception e) {
            return 3;
        }
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.gameMode == null || !(mc.screen instanceof CraftingScreen)
                || !(mc.player.containerMenu instanceof CraftingMenu menu)) {
            if (lastMenuId != -1) message = "Open a crafting table to start.";
            lastMenuId = -1;
            ownGrid = false;
            resetRound();
            return;
        }
        if (menu.containerId != lastMenuId) {
            lastMenuId = menu.containerId;
            blocked.clear();
            ownGrid = false;
            resetRound();
            RecipeBookLink.refresh(mc);
            wait = 3;
            return;
        }
        if (!ClientRecipes.DATA.autoCraft) {
            if (ownGrid && menu.getCarried().isEmpty()) {
                cleanGrid(mc, menu);
                ownGrid = false;
            }
            resetRound();
            message = "Auto craft is OFF.";
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        switch (state) {
            case IDLE -> idle(mc, menu);
            case WAIT_RESULT -> waitResult(mc, menu);
            case TAKE -> take(mc, menu);
            case VERIFY -> verify(mc, menu);
        }
    }

    // ------------------------------------------------------------------ states

    private static void idle(Minecraft mc, CraftingMenu menu) {
        if (!menu.getCarried().isEmpty()) {
            // the player (or a click that did not finish) holds something: never fight the mouse
            if (ownGrid) depositCursor(mc, menu, false);
            else userPause = 20;
            return;
        }
        if (!gridEmpty(menu)) {
            if (ownGrid) {
                if (++cleanTries > 6) {
                    message = "The grid is stuck. Make room in your inventory.";
                    wait = 20;
                    cleanTries = 0;
                    return;
                }
                cleanGrid(mc, menu);
                wait = lag(mc) + 1;
                return;
            }
            message = "Paused: you are using the grid. Press \"Learn this grid\" to save it as a recipe.";
            userPause = 20;
            return;
        }
        ownGrid = false;
        cleanTries = 0;
        if (userPause > 0) {
            userPause--;
            return;
        }

        if (retry && cur != null) {
            retry = false;
            if (startMode(mc, menu)) return;
            giveUp(mc, menu, "no way of putting the items in worked");
            return;
        }

        List<RecipeDef> list = ClientRecipes.list();
        for (int n = 0; n < list.size(); n++) {
            int idx = (rr + 1 + n) % list.size();
            RecipeDef r = list.get(idx);
            if (!r.enabled || !r.autoCraft || !r.finished() || blocked.contains(r)) continue;
            RecipeBookLink.Book book = RecipeBookLink.find(r);
            List<Mode> m = modesFor(mc, menu, r, book);
            if (m.isEmpty()) continue;
            rr = idx;
            cur = r;
            curBook = book;
            modes = m;
            modeIdx = 0;
            roundCrafts = 0;
            if (startMode(mc, menu)) return;
            giveUp(mc, menu, "the items could not be put in the grid");
            return;
        }
        message = "Waiting: nothing to craft with what you have.";
    }

    private static boolean startMode(Minecraft mc, CraftingMenu menu) {
        while (modeIdx < modes.size()) {
            Mode m = modes.get(modeIdx);
            if (place(mc, menu, cur, curBook, m)) {
                ownGrid = true;
                state = S.WAIT_RESULT;
                waited = 0;
                wait = 1;
                message = "Crafting \"" + cur.name + "\"...";
                Log.add("\"" + cur.name + "\": " + modeText(m));
                return true;
            }
            modeIdx++;
        }
        return false;
    }

    private static void waitResult(Minecraft mc, CraftingMenu menu) {
        ItemStack res = menu.slots.get(0).getItem();
        Mode m = modes.get(modeIdx);
        if (!res.isEmpty()) {
            ItemStack learned = Stacks.decode(cur.learnedResult);
            if (!Stacks.sameResult(res, learned)) {
                failMode(mc, menu, "the server shows " + Stacks.name(res) + ", not " + Stacks.name(learned));
                return;
            }
            Log.add("Server shows " + Stacks.describe(res) + ".");
            takeMode = goodTake.getOrDefault(cur, 0);
            takeLoops = 0;
            emptyWait = 0;
            stall = 0;
            gridBefore = gridTotal(menu);
            state = S.TAKE;
            return;
        }
        waited++;
        int lag = lag(mc);
        if (m == Mode.BOOK && waited > 8 + 2 * lag && gridEmpty(menu)) {
            failMode(mc, menu, "the recipe book put nothing in the grid");
            return;
        }
        if (waited > 30 + 2 * lag) {
            failMode(mc, menu, "no result after placing " + gridText(menu));
        }
    }

    private static void take(Minecraft mc, CraftingMenu menu) {
        ItemStack res = menu.slots.get(0).getItem();
        if (res.isEmpty()) {
            // after a craft the server sends the next result a moment later
            if (!gridEmpty(menu) && ++emptyWait <= lag(mc) + 3) return;
            finishRound(mc, menu);
            return;
        }
        emptyWait = 0;
        if (++takeLoops > 700) {
            finishRound(mc, menu);
            return;
        }
        int g = gridTotal(menu);
        // The game guesses a craft worked before the server answers. If the server keeps undoing it
        // (for example a limit like "max hearts"), the grid never really goes down: stop instead of spamming.
        if (takeLoops > 1 && g >= gridBefore) stall++;
        else stall = 0;
        if (stall > lag(mc) + 6) {
            stall = 0;
            goodTake.remove(cur);
            giveUp(mc, menu, "the server keeps cancelling the craft (a server limit or rule?)");
            return;
        }
        gridBefore = g;
        boolean drop = cur.drop;
        switch (takeMode) {
            case 0 -> {
                if (drop) click(mc, menu, 0, 1, ClickType.THROW);
                else click(mc, menu, 0, 0, ClickType.QUICK_MOVE);
            }
            case 1 -> {
                click(mc, menu, 0, 0, ClickType.PICKUP);
                depositCursor(mc, menu, drop);
            }
            default -> click(mc, menu, 0, 0, ClickType.THROW);
        }
        state = S.VERIFY;
        // a craft that worked once is trusted; the first one is checked against the server's answer
        wait = goodTake.containsKey(cur) && goodTake.get(cur) == takeMode ? 0 : lag(mc) + 2;
    }

    private static void verify(Minecraft mc, CraftingMenu menu) {
        if (!menu.getCarried().isEmpty()) depositCursor(mc, menu, cur.drop);
        int now = gridTotal(menu);
        if (now < gridBefore) {
            if (!Integer.valueOf(takeMode).equals(goodTake.get(cur))) {
                Log.add("Crafted! (" + TAKE_NAMES[takeMode] + " works on this server)");
            }
            goodTake.put(cur, takeMode);
            goodMode.put(cur, modes.get(modeIdx));
            roundCrafts++;
            crafts++;
            state = S.TAKE;
            return;
        }
        if (goodTake.containsKey(cur) && goodTake.get(cur) == takeMode) {
            // a trusted way did not show a craft: check it again properly (with a wait for the server)
            goodTake.remove(cur);
            state = S.TAKE;
            wait = lag(mc) + 1;
            return;
        }
        takeMode++;
        if (takeMode >= TAKE_NAMES.length) {
            giveUp(mc, menu, "the server shows the result but does not let it be taken");
            return;
        }
        Log.add("That did not craft it. Trying to " + TAKE_NAMES[takeMode] + ".");
        state = S.TAKE;
    }

    private static void finishRound(Minecraft mc, CraftingMenu menu) {
        if (roundCrafts > 0) {
            batches++;
            message = "Crafted \"" + cur.name + "\" (" + crafts + " so far).";
        }
        cleanGrid(mc, menu);
        state = S.IDLE;
        cur = null;
        curBook = null;
        wait = lag(mc) + 1;
    }

    private static void failMode(Minecraft mc, CraftingMenu menu, String why) {
        Log.add("Did not work: " + why + ".");
        cleanGrid(mc, menu);
        modeIdx++;
        if (modeIdx >= modes.size()) {
            giveUp(mc, menu, "every way was tried");
            return;
        }
        retry = true;
        state = S.IDLE;
        wait = lag(mc) + 1;
    }

    private static void giveUp(Minecraft mc, CraftingMenu menu, String why) {
        if (cur != null) {
            blocked.add(cur);
            message = "Could not craft \"" + cur.name + "\": " + why + ". See the log next to the table.";
            Log.add("Gave up on \"" + cur.name + "\" until the table is opened again.");
        }
        cleanGrid(mc, menu);
        state = S.IDLE;
        cur = null;
        curBook = null;
        retry = false;
        wait = lag(mc) + 1;
    }

    // ------------------------------------------------------------------ helpers

    private static String modeText(Mode m) {
        return switch (m) {
            case BOOK -> "asking the server to fill the grid (recipe book)";
            case SPREAD -> "spreading the items over the grid";
            case SPREAD_CUSTOM -> "spreading the custom versions of the items";
            case ONE -> "putting one item in each slot";
            case COUNT -> "putting the saved amounts in each slot";
        };
    }

    private static boolean gridEmpty(AbstractContainerMenu menu) {
        for (int i = 1; i <= RecipeDef.SLOTS; i++) if (menu.slots.get(i).hasItem()) return false;
        return true;
    }

    private static int gridTotal(AbstractContainerMenu menu) {
        int n = 0;
        for (int i = 1; i <= RecipeDef.SLOTS; i++) n += menu.slots.get(i).getItem().getCount();
        return n;
    }

    private static String gridText(AbstractContainerMenu menu) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= RecipeDef.SLOTS; i++) {
            ItemStack s = menu.slots.get(i).getItem();
            if (s.isEmpty()) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(Stacks.describe(s)).append(" in slot ").append(i);
        }
        return sb.length() == 0 ? "nothing" : sb.toString();
    }

    private static void cleanGrid(Minecraft mc, AbstractContainerMenu menu) {
        if (!menu.getCarried().isEmpty()) depositCursor(mc, menu, false);
        for (int i = 1; i <= RecipeDef.SLOTS; i++) {
            if (menu.slots.get(i).hasItem()) click(mc, menu, i, 0, ClickType.QUICK_MOVE);
        }
    }

    private static boolean isInv(Minecraft mc, Slot s) {
        return s.container == mc.player.getInventory();
    }

    /** Puts what the mouse holds into the inventory (or on the ground). */
    private static void depositCursor(Minecraft mc, AbstractContainerMenu menu, boolean drop) {
        ItemStack c = menu.getCarried();
        if (c.isEmpty()) return;
        if (!drop) {
            for (int pass = 0; pass < 2 && !menu.getCarried().isEmpty(); pass++) {
                for (int s = 10; s < menu.slots.size() && !menu.getCarried().isEmpty(); s++) {
                    Slot slot = menu.slots.get(s);
                    if (!isInv(mc, slot)) continue;
                    ItemStack st = slot.getItem();
                    ItemStack held = menu.getCarried();
                    boolean ok = pass == 0
                            ? !st.isEmpty() && ItemStack.isSameItemSameComponents(st, held) && st.getCount() < st.getMaxStackSize()
                            : st.isEmpty();
                    if (ok) click(mc, menu, s, 0, ClickType.PICKUP);
                }
            }
        }
        if (!menu.getCarried().isEmpty()) click(mc, menu, -999, 0, ClickType.PICKUP); // drop it
    }

    private static void click(Minecraft mc, AbstractContainerMenu menu, int slot, int button, ClickType type) {
        mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, button, type, mc.player);
    }

    // ------------------------------------------------------------------ placing

    /** One recipe slot that needs an item. */
    private record Want(int gridSlot, Item item, ItemStack exact, int count) {}

    private static List<Want> wants(RecipeDef r) {
        List<Want> out = new ArrayList<>();
        for (int i = 0; i < RecipeDef.SLOTS; i++) {
            RecipeDef.Ing ing = r.ingredients.get(i);
            if (ing.empty()) continue;
            ItemStack ex = Stacks.decode(ing.exact);
            Item it = !ex.isEmpty() ? ex.getItem() : RecipeDef.item(ing.id);
            if (it == null) continue;
            out.add(new Want(i + 1, it, ex, Math.max(1, Math.min(64, ing.count))));
        }
        return out;
    }

    /** For each wanted slot, the best inventory item that fits (null when none). */
    private static ItemStack[] choose(Minecraft mc, AbstractContainerMenu menu, List<Want> wants, boolean preferCustom) {
        ItemStack[] pick = new ItemStack[wants.size()];
        for (int w = 0; w < wants.size(); w++) {
            Want want = wants.get(w);
            int bestFit = 0, bestCount = 0;
            ItemStack best = null;
            for (int s = 10; s < menu.slots.size(); s++) {
                Slot slot = menu.slots.get(s);
                if (!isInv(mc, slot)) continue;
                ItemStack st = slot.getItem();
                int f = Stacks.fit(st, want.item(), want.exact(), preferCustom);
                if (f == 0) continue;
                if (f > bestFit || (f == bestFit && st.getCount() > bestCount)) {
                    bestFit = f;
                    bestCount = st.getCount();
                    best = st;
                }
            }
            pick[w] = best == null ? null : best.copy();
        }
        return pick;
    }

    private static List<Integer> sources(Minecraft mc, AbstractContainerMenu menu, ItemStack variant) {
        List<Integer> out = new ArrayList<>();
        for (int s = 10; s < menu.slots.size(); s++) {
            Slot slot = menu.slots.get(s);
            if (isInv(mc, slot) && ItemStack.isSameItemSameComponents(slot.getItem(), variant)) out.add(s);
        }
        out.sort((a, b) -> menu.slots.get(b).getItem().getCount() - menu.slots.get(a).getItem().getCount());
        return out;
    }

    private static int total(AbstractContainerMenu menu, List<Integer> src) {
        int n = 0;
        for (int s : src) n += menu.slots.get(s).getItem().getCount();
        return n;
    }

    /** Groups the wanted slots by the exact item that will go in them. */
    private static List<List<Integer>> groups(List<Want> wants, ItemStack[] pick, List<ItemStack> keys) {
        List<List<Integer>> out = new ArrayList<>();
        for (int w = 0; w < wants.size(); w++) {
            int g = -1;
            for (int k = 0; k < keys.size(); k++) {
                if (ItemStack.isSameItemSameComponents(keys.get(k), pick[w])) {
                    g = k;
                    break;
                }
            }
            if (g < 0) {
                keys.add(pick[w]);
                out.add(new ArrayList<>());
                g = keys.size() - 1;
            }
            out.get(g).add(w);
        }
        return out;
    }

    private static boolean available(Minecraft mc, AbstractContainerMenu menu, List<Want> wants, boolean preferCustom, boolean useCounts) {
        ItemStack[] pick = choose(mc, menu, wants, preferCustom);
        for (ItemStack p : pick) if (p == null) return false;
        List<ItemStack> keys = new ArrayList<>();
        List<List<Integer>> gs = groups(wants, pick, keys);
        for (int g = 0; g < gs.size(); g++) {
            int need = 0;
            for (int w : gs.get(g)) need += useCounts ? wants.get(w).count() : 1;
            if (total(menu, sources(mc, menu, keys.get(g))) < need) return false;
        }
        return true;
    }

    private static List<Mode> modesFor(Minecraft mc, AbstractContainerMenu menu, RecipeDef r, RecipeBookLink.Book book) {
        List<Want> wants = wants(r);
        List<Mode> m = new ArrayList<>();
        if (wants.isEmpty()) return m;
        boolean have = available(mc, menu, wants, false, false);
        if (book != null && (have || RecipeBookLink.canCraft(mc, book))) m.add(Mode.BOOK);
        if (have) {
            m.add(Mode.SPREAD);
            if (!r.hasExact()) {
                ItemStack[] a = choose(mc, menu, wants, false);
                ItemStack[] b = choose(mc, menu, wants, true);
                boolean differs = false;
                for (int i = 0; i < a.length; i++) {
                    if (b[i] != null && a[i] != null && !ItemStack.isSameItemSameComponents(a[i], b[i])) differs = true;
                }
                if (differs && available(mc, menu, wants, true, false)) m.add(Mode.SPREAD_CUSTOM);
            }
            m.add(Mode.ONE);
            boolean counts = false;
            for (Want w : wants) if (w.count() > 1) counts = true;
            if (counts && available(mc, menu, wants, false, true)) m.add(Mode.COUNT);
        }
        Mode good = goodMode.get(r);
        if (good != null && m.remove(good)) m.add(0, good);
        return m;
    }

    private static boolean place(Minecraft mc, CraftingMenu menu, RecipeDef r, RecipeBookLink.Book book, Mode mode) {
        if (mode == Mode.BOOK) {
            if (book == null) return false;
            mc.gameMode.handlePlaceRecipe(menu.containerId, book.entry.id(), true);
            return true;
        }
        List<Want> wants = wants(r);
        if (wants.isEmpty()) return false;
        boolean preferCustom = mode == Mode.SPREAD_CUSTOM;
        boolean useCounts = mode == Mode.COUNT;
        if (!available(mc, menu, wants, preferCustom, useCounts)) return false;
        ItemStack[] pick = choose(mc, menu, wants, preferCustom);
        List<ItemStack> keys = new ArrayList<>();
        List<List<Integer>> gs = groups(wants, pick, keys);

        for (int g = 0; g < gs.size(); g++) {
            List<Integer> src = sources(mc, menu, keys.get(g));
            List<Integer> targets = new ArrayList<>();
            for (int w : gs.get(g)) targets.add(wants.get(w).gridSlot());
            if (src.isEmpty()) return false;

            if (mode == Mode.SPREAD || mode == Mode.SPREAD_CUSTOM) {
                int biggest = menu.slots.get(src.get(0)).getItem().getCount();
                if (biggest >= targets.size()) {
                    int from = src.get(0);
                    click(mc, menu, from, 0, ClickType.PICKUP);
                    if (targets.size() == 1) {
                        click(mc, menu, targets.get(0), 0, ClickType.PICKUP);
                    } else {
                        click(mc, menu, -999, AbstractContainerMenu.getQuickcraftMask(0, 0), ClickType.QUICK_CRAFT);
                        for (int t : targets) click(mc, menu, t, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT);
                        click(mc, menu, -999, AbstractContainerMenu.getQuickcraftMask(2, 0), ClickType.QUICK_CRAFT);
                    }
                    if (!menu.getCarried().isEmpty()) click(mc, menu, from, 0, ClickType.PICKUP);
                } else {
                    // single items (like unstackable ones): one whole stack per slot
                    for (int i = 0; i < targets.size(); i++) {
                        int from = src.get(i);
                        click(mc, menu, from, 0, ClickType.PICKUP);
                        click(mc, menu, targets.get(i), 0, ClickType.PICKUP);
                        if (!menu.getCarried().isEmpty()) click(mc, menu, from, 0, ClickType.PICKUP);
                    }
                }
            } else {
                // ONE / COUNT: right-click puts exactly one item
                int si = 0;
                int from = -1;
                for (int gi = 0; gi < targets.size(); gi++) {
                    int t = targets.get(gi);
                    int want = useCounts ? wants.get(gs.get(g).get(gi)).count() : 1;
                    for (int k = 0; k < want; k++) {
                        if (menu.getCarried().isEmpty()) {
                            while (si < src.size() && menu.slots.get(src.get(si)).getItem().isEmpty()) si++;
                            if (si >= src.size()) break;
                            from = src.get(si);
                            click(mc, menu, from, 0, ClickType.PICKUP);
                        }
                        click(mc, menu, t, 1, ClickType.PICKUP);
                    }
                }
                if (!menu.getCarried().isEmpty()) {
                    if (from >= 0 && menu.slots.get(from).getItem().isEmpty()) click(mc, menu, from, 0, ClickType.PICKUP);
                    else depositCursor(mc, menu, false);
                }
            }
        }
        if (!menu.getCarried().isEmpty()) depositCursor(mc, menu, false);
        return true;
    }
}
