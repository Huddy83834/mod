package com.autocrafter;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

/** The Auto Crafter window: your crafting layouts, a 3x3 grid editor and an item picker. */
public class AutoCrafterScreen extends Screen {
    private static final int C_BG = 0xF00D1017;
    private static final int C_PANEL = 0xFF141822;
    private static final int C_PANEL2 = 0xFF1A1F2C;
    private static final int C_BORDER = 0xFF2A3142;
    private static final int C_ACCENT = 0xFFF5B83D;
    private static final int C_GREEN = 0xFF3DDC84;
    private static final int C_RED = 0xFFFF5C5C;
    private static final int C_TEXT = 0xFFE8EAF0;
    private static final int C_DIM = 0xFF8B93A7;
    private static final int C_DIMMER = 0xFF596074;

    private record Hit(int x, int y, int w, int h, IntConsumer action) {}

    private record Entry(String id, String name, String lower, ItemStack stack) {}

    private static List<Entry> catalog;

    private static synchronized List<Entry> catalog() {
        if (catalog == null) {
            List<Entry> list = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) continue;
                String id = BuiltInRegistries.ITEM.getKey(item).toString();
                ItemStack st = new ItemStack(item);
                String name = st.getHoverName().getString();
                list.add(new Entry(id, name, name.toLowerCase(Locale.ROOT), st));
            }
            list.sort(Comparator.comparing(Entry::lower));
            catalog = list;
        }
        return catalog;
    }

    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, ItemStack> stacks = new HashMap<>();
    private final int[][] slotRect = new int[RecipeDef.SLOTS][4];

    private EditBox nameBox;
    private EditBox searchBox;
    private int sel = 0;
    private int slot = -1;
    private boolean picker = false;
    private boolean dirty = false;
    private int listScroll = 0;
    private int pickScroll = 0;
    private List<Entry> filtered = new ArrayList<>();
    private int mouseX, mouseY;
    private String tipName = null;
    private String tipId = null;
    private int px, py, pw, ph;

    public AutoCrafterScreen() {
        super(Component.literal("Auto Crafter"));
    }

    // ------------------------------------------------------------------ setup

    @Override
    protected void init() {
        nameBox = new EditBox(this.font, 0, 0, 150, 18, Component.literal("Recipe name"));
        nameBox.setMaxLength(28);
        nameBox.setResponder(s -> {
            RecipeDef r = cur();
            if (r != null && !s.equals(r.name)) {
                r.name = s;
                dirty = true;
            }
        });
        addRenderableWidget(nameBox);

        searchBox = new EditBox(this.font, 0, 0, 200, 18, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search items (any mod)..."));
        searchBox.setMaxLength(40);
        searchBox.setResponder(s -> {
            refilter(s);
            pickScroll = 0;
        });
        addRenderableWidget(searchBox);

        sel = ClientRecipes.list().isEmpty() ? -1 : Math.max(0, Math.min(sel, ClientRecipes.list().size() - 1));
        syncName();
        refilter("");
        updateBoxes();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        ClientRecipes.save();
        super.removed();
    }

    private RecipeDef cur() {
        List<RecipeDef> l = ClientRecipes.list();
        return sel >= 0 && sel < l.size() ? l.get(sel) : null;
    }

    private void changed() {
        dirty = false;
        ClientRecipes.save();
    }

    private void syncName() {
        RecipeDef r = cur();
        if (nameBox != null) nameBox.setValue(r == null ? "" : r.name);
    }

    private void updateBoxes() {
        boolean have = cur() != null;
        nameBox.visible = have && !picker;
        nameBox.active = have && !picker;
        searchBox.visible = picker;
        searchBox.active = picker;
    }

    private void refilter(String q) {
        String s = q.toLowerCase(Locale.ROOT).trim();
        List<Entry> out = new ArrayList<>();
        for (Entry e : catalog()) {
            if (s.isEmpty() || e.lower().contains(s) || e.id().contains(s)) out.add(e);
        }
        filtered = out;
    }

    private ItemStack stackOf(String id) {
        if (id == null || id.isEmpty()) return null;
        ItemStack cached = stacks.get(id);
        if (cached != null) return cached.isEmpty() ? null : cached;
        Item it = RecipeDef.item(id);
        ItemStack st = it == null ? ItemStack.EMPTY : new ItemStack(it);
        stacks.put(id, st);
        return st.isEmpty() ? null : st;
    }

    // ------------------------------------------------------------------ actions

    private void newRecipe() {
        List<RecipeDef> l = ClientRecipes.list();
        if (l.size() >= 24) return;
        RecipeDef r = new RecipeDef();
        r.name = "Recipe " + (l.size() + 1);
        l.add(r);
        sel = l.size() - 1;
        slot = -1;
        syncName();
        changed();
        updateBoxes();
    }

    private void deleteRecipe() {
        List<RecipeDef> l = ClientRecipes.list();
        if (sel < 0 || sel >= l.size()) return;
        l.remove(sel);
        sel = l.isEmpty() ? -1 : Math.min(sel, l.size() - 1);
        slot = -1;
        syncName();
        changed();
        updateBoxes();
    }

    private void openPicker(int s) {
        slot = s;
        picker = true;
        pickScroll = 0;
        searchBox.setValue("");
        refilter("");
        updateBoxes();
        setFocused(searchBox);
    }

    private void closePicker() {
        picker = false;
        updateBoxes();
        setFocused(null);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        int mx = (int) event.x();
        int my = (int) event.y();
        int button = event.button();
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mx >= h.x() && mx < h.x() + h.w() && my >= h.y() && my < h.y() + h.h()) {
                h.action().accept(button);
                return true;
            }
        }
        setFocused(null);
        return picker;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int dir = sy > 0 ? -1 : 1;
        if (picker) {
            int rows = (filtered.size() + pickCols() - 1) / pickCols();
            pickScroll = Math.max(0, Math.min(Math.max(0, rows - pickRows()), pickScroll + dir));
            return true;
        }
        int lx = px + 8;
        if (mx >= lx && mx < lx + 116 && my >= py + 50 && my < py + ph) {
            int max = Math.max(0, ClientRecipes.list().size() - listRows());
            listScroll = Math.max(0, Math.min(max, listScroll + dir));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256 && picker) {
            closePicker();
            return true;
        }
        return super.keyPressed(event);
    }

    // ------------------------------------------------------------------ drawing helpers

    private void text(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(this.font, s, x, y, color, false);
    }

    private void rtext(GuiGraphics g, String s, int rightX, int y, int color) {
        g.drawString(this.font, s, rightX - this.font.width(s), y, color, false);
    }

    private static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private String fit(String s, int max) {
        if (max <= 0) return "";
        if (this.font.width(s) <= max) return s;
        int end = s.length();
        while (end > 0 && this.font.width(s.substring(0, end) + "...") > max) end--;
        return s.substring(0, end) + "...";
    }

    private List<String> wrap(String s, int max, int maxLines) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String test = line.length() == 0 ? word : line + " " + word;
            if (this.font.width(test) > max && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        if (out.size() > maxLines) {
            List<String> cut = new ArrayList<>(out.subList(0, maxLines));
            cut.set(maxLines - 1, fit(cut.get(maxLines - 1) + " ...", max));
            return cut;
        }
        return out;
    }

    private int chipW(String label) {
        return this.font.width(label) + 14;
    }

    private int chip(GuiGraphics g, int x, int y, String label, int accent, IntConsumer action) {
        int w = chipW(label);
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 16;
        g.fill(x, y, x + w, y + 16, hover ? 0xFF2D3446 : 0xFF1E2330);
        border(g, x, y, w, 16, accent != 0 ? accent : C_BORDER);
        text(g, label, x + 7, y + 4, accent != 0 ? accent : C_TEXT);
        hits.add(new Hit(x, y, w, 16, action));
        return w;
    }

    private static int alpha(int color, int a) {
        return (a << 24) | (color & 0xFFFFFF);
    }

    private int pickCols() {
        return Math.max(6, Math.min(14, (Math.min(this.width - 40, 360) - 20) / 22));
    }

    private int pickRows() {
        return Math.max(3, Math.min(7, (Math.min(this.height - 40, 230) - 70) / 22));
    }

    private int listRows() {
        return Math.max(1, (ph - 32 - 18 - 30) / 26);
    }

    // ------------------------------------------------------------------ main render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        hits.clear();
        tipName = null;
        tipId = null;

        pw = Math.min(this.width - 16, 440);
        ph = Math.min(this.height - 16, 250);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;

        g.fill(0, 0, this.width, this.height, 0xB0060810);
        g.fill(px, py, px + pw, py + ph, C_BG);
        border(g, px, py, pw, ph, C_BORDER);
        g.fill(px, py, px + pw, py + 2, C_ACCENT);

        drawHeader(g);
        drawList(g);
        drawEditor(g);
        if (picker) drawPicker(g);
        if (tipName != null) drawTip(g);
    }

    private void drawHeader(GuiGraphics g) {
        text(g, "AUTO CRAFTER", px + 12, py + 9, C_ACCENT);
        int right = px + pw - 10;
        right -= chipW("Close");
        chip(g, right, py + 5, "Close", 0, b -> {
            if (picker) closePicker();
            else onClose();
        });

        boolean on = ClientRecipes.DATA.autoCraft;
        String label = on ? "Auto-craft at table: ON" : "Auto-craft at table: OFF";
        right -= 8 + chipW(label);
        chip(g, right, py + 5, label, on ? C_GREEN : C_RED, b -> {
            if (picker) return;
            ClientRecipes.DATA.autoCraft = !ClientRecipes.DATA.autoCraft;
            changed();
        });
        if (!picker && mouseX >= right && mouseX < right + chipW(label) && mouseY >= py + 5 && mouseY < py + 21) {
            tipName = "Auto-craft at table";
            tipId = "Crafts everything the moment a crafting table opens";
        }
        g.fill(px + 8, py + 26, px + pw - 8, py + 27, C_BORDER);
    }

    // ------------------------------------------------------------------ list

    private void drawList(GuiGraphics g) {
        int lx = px + 8;
        int ly = py + 32;
        int lw = 116;
        int lh = ph - 32 - 8;
        g.fill(lx, ly, lx + lw, ly + lh, C_PANEL);
        border(g, lx, ly, lw, lh, C_BORDER);
        text(g, "YOUR RECIPES", lx + 8, ly + 6, C_DIMMER);

        List<RecipeDef> l = ClientRecipes.list();
        int rows = listRows();
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, l.size() - rows)));
        for (int i = 0; i < rows; i++) {
            int idx = listScroll + i;
            if (idx >= l.size()) break;
            RecipeDef r = l.get(idx);
            int ry = ly + 18 + i * 26;
            boolean selected = idx == sel;
            boolean hover = mouseX >= lx && mouseX < lx + lw && mouseY >= ry && mouseY < ry + 26 && !picker;
            if (selected) {
                g.fill(lx + 1, ry, lx + lw - 1, ry + 25, 0x30F5B83D);
                g.fill(lx + 1, ry, lx + 3, ry + 25, C_ACCENT);
            } else if (hover) {
                g.fill(lx + 1, ry, lx + lw - 1, ry + 25, 0x18FFFFFF);
            }
            ItemStack icon = null;
            for (String id : r.slots) {
                icon = stackOf(id);
                if (icon != null) break;
            }
            if (icon != null) g.renderItem(icon, lx + 7, ry + 4);
            else text(g, "?", lx + 12, ry + 9, C_DIMMER);
            text(g, fit(r.name, lw - 52), lx + 28, ry + 5, r.enabled ? C_TEXT : C_DIMMER);
            text(g, r.finished() ? (r.enabled ? "on" : "off") : "empty", lx + 28, ry + 15, r.finished() ? C_DIM : C_RED);
            g.fill(lx + lw - 12, ry + 9, lx + lw - 7, ry + 14, r.enabled && r.finished() ? C_GREEN : C_DIMMER);
            final int target = idx;
            hits.add(new Hit(lx, ry, lw, 26, b -> {
                if (picker) return;
                sel = target;
                slot = -1;
                syncName();
                updateBoxes();
            }));
        }
        if (l.size() < 24) {
            chip(g, lx + 8, ly + lh - 24, "+ New recipe", C_ACCENT, b -> {
                if (!picker) newRecipe();
            });
        }
    }

    // ------------------------------------------------------------------ editor

    private void drawEditor(GuiGraphics g) {
        int ex = px + 116 + 8 + 14;
        int ey = py + 34;
        int ew = pw - (ex - px) - 10;
        RecipeDef r = cur();

        if (r == null) {
            String a = "No recipe yet";
            String b = "Arrange items like a crafting table. The game's own";
            String c = "recipe is used, so modded items work too.";
            String d = "Press \"+ New recipe\" to start.";
            text(g, a, ex + ew / 2 - this.font.width(a) / 2, ey + 40, C_TEXT);
            text(g, b, ex + ew / 2 - this.font.width(b) / 2, ey + 58, C_DIM);
            text(g, c, ex + ew / 2 - this.font.width(c) / 2, ey + 70, C_DIM);
            text(g, d, ex + ew / 2 - this.font.width(d) / 2, ey + 88, C_DIM);
            return;
        }

        nameBox.setX(ex);
        nameBox.setY(ey);
        nameBox.setWidth(Math.min(150, ew - 90));
        if (!picker) nameBox.render(g, mouseX, mouseY, 0f);
        String en = r.enabled ? "Enabled" : "Disabled";
        chip(g, ex + ew - chipW(en), ey + 1, en, r.enabled ? C_GREEN : C_RED, b -> {
            if (picker) return;
            r.enabled = !r.enabled;
            changed();
        });

        int cell = 32;
        int gy = ey + 40;
        text(g, "LAYOUT (like a crafting table)", ex, gy - 12, C_DIMMER);
        for (int i = 0; i < RecipeDef.SLOTS; i++) {
            drawSlot(g, r, i, ex + (i % 3) * (cell + 2), gy + (i / 3) * (cell + 2), cell);
        }

        int ox = ex + 3 * (cell + 2) + 18;
        int ow = ex + ew - ox;
        if (ow > 90) {
            chip(g, ox, gy, "Clear layout", 0, b -> {
                if (picker) return;
                for (int i = 0; i < RecipeDef.SLOTS; i++) r.slots.set(i, "");
                changed();
            });
            chip(g, ox, gy + 22, "Delete recipe", C_RED, b -> {
                if (!picker) deleteRecipe();
            });
            int ty = gy + 50;
            for (String line : wrap("Left-click a slot to choose an item. Right-click clears it.", ow, 4)) {
                text(g, line, ox, ty, C_DIMMER);
                ty += 11;
            }
        }

        int sy = gy + 3 * (cell + 2) + 8;
        List<String> lines = new ArrayList<>();
        if (!r.finished()) {
            lines.addAll(wrap("Pick the items for each slot, exactly like on a crafting table.", ew, 2));
        } else {
            lines.addAll(wrap("Open a crafting table and it crafts this with everything you have. The game decides what it makes, so modded recipes work too.", ew, 3));
        }
        for (int i = 0; i < lines.size(); i++) text(g, lines.get(i), ex, sy + i * 11, C_DIM);

        String status = Crafter.message + "   (" + Crafter.batches + " batches crafted)";
        text(g, fit(status, pw - 24), px + 12, py + ph - 14, C_ACCENT);
    }

    private void drawSlot(GuiGraphics g, RecipeDef r, int s, int x, int y, int size) {
        slotRect[s][0] = x;
        slotRect[s][1] = y;
        slotRect[s][2] = size;
        slotRect[s][3] = size;
        boolean selected = slot == s;
        boolean hover = !picker && mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size;
        g.fill(x, y, x + size, y + size, hover ? 0xFF222A3B : C_PANEL);
        border(g, x, y, size, size, selected ? C_ACCENT : C_BORDER);

        String id = r.slots.get(s);
        ItemStack st = stackOf(id);
        if (st != null) {
            g.renderItem(st, x + (size - 16) / 2, y + (size - 16) / 2);
            if (hover) {
                tipName = st.getHoverName().getString();
                tipId = id;
            }
        } else if (!id.isEmpty()) {
            text(g, "?", x + size / 2 - 2, y + size / 2 - 4, C_RED);
            if (hover) {
                tipName = "Unknown item";
                tipId = id;
            }
        } else {
            text(g, "+", x + size / 2 - 2, y + size / 2 - 4, C_DIMMER);
        }

        final int target = s;
        hits.add(new Hit(x, y, size, size, b -> {
            if (picker) return;
            slot = target;
            if (b == 1) {
                r.slots.set(target, "");
                changed();
            } else {
                openPicker(target);
            }
        }));
    }

    // ------------------------------------------------------------------ picker

    private void drawPicker(GuiGraphics g) {
        int cols = pickCols();
        int rows = pickRows();
        int w = cols * 22 + 20;
        int h = rows * 22 + 62;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        g.fill(0, 0, this.width, this.height, 0x90000000);
        g.fill(x, y, x + w, y + h, 0xFF10131A);
        border(g, x, y, w, h, C_BORDER);
        g.fill(x, y, x + w, y + 2, C_ACCENT);

        text(g, "Choose an item for slot " + (slot + 1), x + 10, y + 9, C_ACCENT);
        rtext(g, filtered.size() + " items", x + w - 10, y + 9, C_DIMMER);

        searchBox.setX(x + 10);
        searchBox.setY(y + 22);
        searchBox.setWidth(w - 20 - 60);
        searchBox.render(g, mouseX, mouseY, 0f);
        chip(g, x + w - 10 - 52, y + 21, "Cancel", 0, b -> closePicker());

        int totalRows = (filtered.size() + cols - 1) / cols;
        pickScroll = Math.max(0, Math.min(pickScroll, Math.max(0, totalRows - rows)));
        int gx = x + 10;
        int gy = y + 46;
        RecipeDef r = cur();
        for (int i = 0; i < cols * rows; i++) {
            int idx = pickScroll * cols + i;
            if (idx >= filtered.size()) break;
            Entry e = filtered.get(idx);
            int cx = gx + (i % cols) * 22;
            int cy = gy + (i / cols) * 22;
            boolean hover = mouseX >= cx && mouseX < cx + 22 && mouseY >= cy && mouseY < cy + 22;
            g.fill(cx, cy, cx + 21, cy + 21, hover ? 0xFF2D3446 : C_PANEL2);
            g.renderItem(e.stack(), cx + 2, cy + 2);
            if (hover) {
                tipName = e.name();
                tipId = e.id();
            }
            hits.add(new Hit(cx, cy, 22, 22, b -> {
                if (r != null && slot >= 0) {
                    r.slots.set(slot, e.id());
                    changed();
                }
                closePicker();
            }));
        }
        if (filtered.isEmpty()) text(g, "No items match.", gx, gy + 6, C_DIM);
        if (totalRows > rows) {
            int trackH = rows * 22;
            int thumbH = Math.max(14, trackH * rows / totalRows);
            int thumbY = gy + (trackH - thumbH) * pickScroll / Math.max(1, totalRows - rows);
            g.fill(x + w - 6, gy, x + w - 4, gy + trackH, 0x18FFFFFF);
            g.fill(x + w - 6, thumbY, x + w - 4, thumbY + thumbH, C_ACCENT);
        }
        text(g, "Scroll for more. Items from other mods are listed too.", x + 10, y + h - 14, C_DIMMER);
    }

    private void drawTip(GuiGraphics g) {
        int w = Math.max(this.font.width(tipName), tipId == null ? 0 : this.font.width(tipId)) + 12;
        int h = tipId == null ? 18 : 29;
        int x = mouseX + 10;
        int y = mouseY - 6;
        if (x + w > this.width - 4) x = mouseX - w - 8;
        if (y + h > this.height - 4) y = this.height - 4 - h;
        g.fill(x, y, x + w, y + h, 0xF0090B10);
        border(g, x, y, w, h, C_BORDER);
        text(g, tipName, x + 6, y + 5, C_TEXT);
        if (tipId != null) text(g, tipId, x + 6, y + 17, C_DIMMER);
    }
}
