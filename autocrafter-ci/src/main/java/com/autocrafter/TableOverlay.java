package com.autocrafter;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Buttons and a live log on the right side of the crafting table window. */
public final class TableOverlay {
    private static final int ROW = 20;

    private TableOverlay() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof CraftingScreen)) return;
            RecipeBookLink.refresh(client);
            RecipeBookLink.importCustom();

            int side = (w - 176) / 2;
            int pw = Math.max(100, Math.min(160, side - 12));
            int x = w - pw - 4;
            int y = 4;
            List<AbstractWidget> buttons = Screens.getButtons(screen);

            Button master = Button.builder(masterLabel(), b -> {
                ClientRecipes.DATA.autoCraft = !ClientRecipes.DATA.autoCraft;
                Crafter.unblock();
                ClientRecipes.changed();
                b.setMessage(masterLabel());
            }).bounds(x, y, pw, 18).build();
            buttons.add(master);
            y += ROW;

            Button learn = Button.builder(Component.literal("Learn this grid"), b -> learn(client)).bounds(x, y, pw, 18).build();
            buttons.add(learn);
            y += ROW;

            buttons.add(new SpeedSlider(x, y, pw, 18));
            y += ROW + 4;
            ScreenEvents.remove(screen).register(s -> ClientRecipes.save());

            int logLines = 8;
            int room = (h - y - 6 - logLines * 10) / ROW;
            int rows = Math.max(1, Math.min(10, room));
            List<Button> toggles = new ArrayList<>();
            for (int i = 0; i < rows; i++) {
                final int idx = i;
                Button t = Button.builder(Component.literal(""), b -> toggle(idx)).bounds(x, y + i * ROW, pw, 18).build();
                buttons.add(t);
                toggles.add(t);
            }
            final int logY = y + rows * ROW + 4;
            final int fx = x;
            final int fw = pw;

            ScreenEvents.afterRender(screen).register((s, g, mx, my, delta) -> {
                List<RecipeDef> list = ClientRecipes.list();
                for (int i = 0; i < toggles.size(); i++) {
                    Button t = toggles.get(i);
                    if (i < list.size()) {
                        RecipeDef r = list.get(i);
                        t.visible = true;
                        String label = (r.enabled && r.autoCraft ? "ON  " : "OFF ") + r.name;
                        t.setMessage(Component.literal(client.font.plainSubstrByWidth(label, fw - 10)));
                    } else {
                        t.visible = false;
                    }
                }
                int ly = logY;
                g.drawString(client.font, client.font.plainSubstrByWidth(Crafter.message, fw), fx, ly, 0xFFF5B83D, true);
                ly += 12;
                for (String line : Log.lines()) {
                    for (String part : wrap(client, line, fw)) {
                        if (ly > s.height - 10) break;
                        g.drawString(client.font, part, fx, ly, 0xFFD0D4DE, true);
                        ly += 10;
                    }
                }
            });
        });
    }

    private static Component masterLabel() {
        return Component.literal(ClientRecipes.DATA.autoCraft ? "Auto craft: ON" : "Auto craft: OFF");
    }

    private static void toggle(int idx) {
        List<RecipeDef> list = ClientRecipes.list();
        if (idx >= list.size()) return;
        RecipeDef r = list.get(idx);
        boolean on = !(r.enabled && r.autoCraft);
        r.enabled = on;
        r.autoCraft = on;
        Crafter.unblock();
        ClientRecipes.changed();
        Log.add((on ? "Switched on: " : "Switched off: ") + r.name);
    }

    private static List<String> wrap(Minecraft mc, String s, int w) {
        List<String> out = new ArrayList<>();
        String rest = s;
        while (!rest.isEmpty() && out.size() < 4) {
            String part = mc.font.plainSubstrByWidth(rest, w);
            if (part.isEmpty()) break;
            if (part.length() < rest.length()) {
                int sp = part.lastIndexOf(' ');
                if (sp > 10) part = part.substring(0, sp);
            }
            out.add(part);
            rest = rest.substring(part.length()).trim();
        }
        return out;
    }

    /** Saves what is in the crafting grid right now (the exact items) as a recipe. */
    private static void learn(Minecraft mc) {
        if (mc.player == null || !(mc.player.containerMenu instanceof CraftingMenu menu)) return;
        if (Crafter.busy()) {
            Log.add("The mod is using the grid right now. Turn Auto craft OFF, put the recipe in, then press Learn.");
            return;
        }
        RecipeDef r = new RecipeDef();
        r.source = "learned";
        r.drop = false;
        boolean any = false;
        for (int i = 0; i < RecipeDef.SLOTS; i++) {
            ItemStack st = menu.slots.get(i + 1).getItem();
            if (st.isEmpty()) continue;
            RecipeDef.Ing ing = r.ingredients.get(i);
            ing.id = Stacks.id(st.getItem());
            ing.count = st.getCount();
            ing.exact = Stacks.encode(st.copyWithCount(1));
            any = true;
        }
        if (!any) {
            Log.add("Put one craft's worth of the recipe in the grid first, then press Learn.");
            return;
        }
        ItemStack res = menu.slots.get(0).getItem();
        if (!res.isEmpty()) {
            r.learnedResult = Stacks.encode(res);
            r.name = Stacks.name(res);
        } else {
            r.name = "Learned recipe";
            Log.add("Warning: the server shows no result for this grid, so it may not be a real recipe.");
        }
        if (r.name.length() > 28) r.name = r.name.substring(0, 28);

        // replace an older copy of the same recipe
        List<RecipeDef> list = ClientRecipes.list();
        for (int i = list.size() - 1; i >= 0; i--) {
            RecipeDef o = list.get(i);
            if (!o.source.equals("learned")) continue;
            boolean same = true;
            for (int k = 0; k < RecipeDef.SLOTS; k++) {
                if (!o.ingredients.get(k).exact.equals(r.ingredients.get(k).exact)) same = false;
            }
            if (same) list.remove(i);
        }
        list.add(0, r);
        // switch off other recipes with the same result so they don't fight over the items
        ItemStack learned = Stacks.decode(r.learnedResult);
        if (!learned.isEmpty()) {
            for (RecipeDef o : list) {
                if (o != r && Stacks.sameResult(Stacks.decode(o.learnedResult), learned) && !o.learnedResult.isEmpty()) {
                    o.enabled = false;
                }
            }
        }
        // switch off hand-made recipes with the same layout: the learned one replaces them
        for (RecipeDef o : list) {
            if (o == r || !o.source.isEmpty() || !o.enabled) continue;
            boolean same = true;
            for (int k = 0; k < RecipeDef.SLOTS; k++) {
                RecipeDef.Ing a = o.ingredients.get(k), b = r.ingredients.get(k);
                if (a.empty() != b.empty() || (!a.empty() && !a.id.equals(b.id))) same = false;
            }
            if (same) {
                o.enabled = false;
                Log.add("Switched off \"" + o.name + "\" (the learned recipe replaces it).");
            }
        }
        ClientRecipes.changed();
        Crafter.unblock();
        if (ClientRecipes.DATA.autoCraft) {
            Crafter.adoptGrid();
            Log.add("Learned \"" + r.name + "\" with the exact items. It will now auto-craft.");
        } else {
            Log.add("Learned \"" + r.name + "\". Turn Auto craft ON to start.");
        }
    }
}
