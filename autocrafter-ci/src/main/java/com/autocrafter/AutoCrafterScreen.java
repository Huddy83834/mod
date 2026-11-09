package com.autocrafter;

import net.minecraft.client.Minecraft;
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

/** The Auto Crafter window: a recipe list, a 3x3 recipe editor and an item picker. */
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
    private static final int C_BLUE = 0xFF4FC3F7;

    private static final int RESULT = 9;
    private static final double[] RATES = {0.5, 1, 2, 5, 10, 20};

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
    private final int[][] slotRect = new int[10][4];
    private final int[] listRect = new int[4];

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

        if (ClientRecipes.list().isEmpty()) {
            sel = -1;
        } else {
            sel = Math.max(0, Math.min(sel, ClientRecipes.list().size() - 1));
        }
        syncName();
        refilter("");
        updateBoxes();
    }

    @Override
    public boolean isPauseScreen() {
        return false; // the world keeps running, so crafting continues while this is open
    }

    @Override
    public void removed() {
        if (dirty) ClientRecipes.changed();
        super.removed();
    }

    private RecipeDef cur() {
        List<RecipeDef> l = ClientRecipes.list();
        return sel >= 0 && sel < l.size() ? l.get(sel) : null;
    }

    private void changed() {
        dirty = false;
        ClientRecipes.changed();
    }

    private void syncName() {
        RecipeDef r = cur();
        if (nameBox != null) nameBox.setValue(r == null ? "" : r.name);
    }

    private void updateBoxes() {
        boolean haveRecipe = cur() != null;
        nameBox.visible = haveRecipe && !picker;
        nameBox.active = haveRecipe && !picker;
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

    private String nameOf(String id) {
        ItemStack s = stackOf(id);
        return s == null ? "(none)" : s.getHoverName().getString();
    }

    // ------------------------------------------------------------------ recipe actions

    private void newRecipe() {
        List<RecipeDef> l = ClientRecipes.list();
        if (l.size() >= 16) return;
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

    private String idAt(RecipeDef r, int s) {
        return s == RESULT ? r.resultId : r.ingredients.get(s).id;
    }

    private int countAt(RecipeDef r, int s) {
        return s == RESULT ? r.resultCount : r.ingredients.get(s).count;
    }

    private void setCount(RecipeDef r, int s, int n) {
        if (s == RESULT) r.resultCount = Math.max(1, Math.min(4096, n));
        else r.ingredients.get(s).count = Math.max(1, Math.min(2048, n));
        changed();
    }

    private void setItem(RecipeDef r, int s, String id) {
        if (s == RESULT) {
            r.resultId = id;
            if (r.resultCount < 1) r.resultCount = 1;
        } else {
            RecipeDef.Ing ing = r.ingredients.get(s);
            ing.id = id;
            if (ing.count < 1) ing.count = 1;
        }
        changed();
    }

    private void clearSlot(RecipeDef r, int s) {
        if (s == RESULT) {
            r.resultId = "";
            r.resultCount = 1;
        } else {
            RecipeDef.Ing ing = r.ingredients.get(s);
            ing.id = "";
            ing.count = 1;
        }
        changed();
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
        return picker; // clicks outside the picker do nothing while it is open
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int dir = sy > 0 ? -1 : 1;
        if (picker) {
            int rows = (filtered.size() + pickCols() - 1) / pickCols();
            pickScroll = Math.max(0, Math.min(Math.max(0, rows - pickRows()), pickScroll + dir));
            return true;
        }
        RecipeDef r = cur();
        if (r != null) {
            for (int s = 0; s <= RESULT; s++) {
                int[] a = slotRect[s];
                if (mx >= a[0] && mx < a[0] + a[2] && my >= a[1] && my < a[1] + a[3]) {
                    if (!idAt(r, s).isEmpty()) {
                        int step = hasShiftDown() ? 10 : 1;
                        setCount(r, s, countAt(r, s) - dir * step);
                        slot = s;
                    }
                    return true;
                }
            }
        }
        if (mx >= listRect[0] && mx < listRect[0] + listRect[2] && my >= listRect[1] && my < listRect[1] + listRect[3]) {
            int max = Math.max(0, ClientRecipes.list().size() - listRows());
            listScroll = Math.max(0, Math.min(max, listScroll + dir));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256 && picker) { // Escape closes the picker first
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

    /** A clickable button. Left-click runs the action with 0, right-click with 1. */
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
        return Math.max(1, (ph - 30 - 40) / 26);
    }

    // ------------------------------------------------------------------ main render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        hits.clear();
        tipName = null;
        tipId = null;

        pw = Math.min(this.width - 16, 470);
        ph = Math.min(this.height - 16, 262);
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

        StatusPayload st = ClientRecipes.status;
        String label;
        int color;
        if (st == null) {
            label = "Connecting...";
            color = C_DIM;
        } else if (st.active()) {
            label = "ACTIVE";
            color = C_GREEN;
        } else {
            label = "OFF: server does not allow it";
            color = C_RED;
        }
        int w = this.font.width(label) + 16;
        right -= 8 + w;
        g.fill(right, py + 6, right + w, py + 20, alpha(color, 0x30));
        g.fill(right + 5, py + 11, right + 9, py + 15, color);
        text(g, label, right + 12, py + 9, color);

        if (st != null && st.active()) {
            String stats = "Crafted " + st.crafted() + "  ·  Ingredients given " + st.flowed();
            int sw = this.font.width(stats);
            if (right - sw - 20 > px + 110) rtext(g, stats, right - 10, py + 9, C_DIM);
        }
        g.fill(px + 8, py + 26, px + pw - 8, py + 27, C_BORDER);
    }

    // ------------------------------------------------------------------ recipe list

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
        listRect[0] = lx;
        listRect[1] = ly + 18;
        listRect[2] = lw;
        listRect[3] = rows * 26;
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
            ItemStack icon = stackOf(r.resultId);
            if (icon != null) g.renderItem(icon, lx + 7, ry + 4);
            else text(g, "?", lx + 12, ry + 9, C_DIMMER);
            text(g, fit(r.name, lw - 52), lx + 28, ry + 5, r.enabled ? C_TEXT : C_DIMMER);
            String sub = !r.usable() ? "not finished" : r.flow ? "auto + flow" : "auto-craft";
            text(g, fit(sub, lw - 52), lx + 28, ry + 15, !r.usable() ? C_RED : C_DIM);
            g.fill(lx + lw - 12, ry + 9, lx + lw - 7, ry + 14, r.enabled && r.usable() ? C_GREEN : C_DIMMER);
            final int target = idx;
            hits.add(new Hit(lx, ry, lw, 26, b -> {
                if (picker) return;
                sel = target;
                slot = -1;
                syncName();
                updateBoxes();
            }));
        }
        if (l.size() > rows) {
            rtext(g, (listScroll + 1) + "-" + Math.min(l.size(), listScroll + rows) + " / " + l.size(), lx + lw - 8, ly + 6, C_DIMMER);
        }
        if (l.size() < 16) {
            chip(g, lx + 8, ly + lh - 24, "+ New recipe", C_ACCENT, b -> {
                if (!picker) newRecipe();
            });
        }
    }

    // ------------------------------------------------------------------ editor

    private void drawEditor(GuiGraphics g) {
        int ex = px + 116 + 8 + 12;
        int ey = py + 34;
        int ew = pw - (ex - px) - 10;
        RecipeDef r = cur();

        if (r == null) {
            String a = "No recipe yet";
            String b = "Make your own recipe for any item, even modded ones.";
            String c = "Press \"+ New recipe\" to start.";
            text(g, a, ex + ew / 2 - this.font.width(a) / 2, ey + 50, C_TEXT);
            text(g, b, ex + ew / 2 - this.font.width(b) / 2, ey + 66, C_DIM);
            text(g, c, ex + ew / 2 - this.font.width(c) / 2, ey + 80, C_DIM);
            return;
        }

        // name + enabled
        nameBox.setX(ex);
        nameBox.setY(ey);
        nameBox.setWidth(Math.min(150, ew - 90));
        if (!picker) nameBox.render(g, mouseX, mouseY, 0f);
        String en = r.enabled ? "Enabled" : "Disabled";
        int enW = chipW(en);
        chip(g, ex + ew - enW, ey + 1, en, r.enabled ? C_GREEN : C_RED, b -> {
            if (picker) return;
            r.enabled = !r.enabled;
            changed();
        });

        // grid
        int cell = 28;
        int gy = ey + 40;
        text(g, "INGREDIENTS", ex, gy - 12, C_DIMMER);
        for (int i = 0; i < RecipeDef.SLOTS; i++) {
            int cx = ex + (i % 3) * (cell + 2);
            int cy = gy + (i / 3) * (cell + 2);
            drawSlot(g, r, i, cx, cy, cell);
        }

        // arrow + result
        int ax = ex + 3 * (cell + 2) + 8;
        int ay = gy + (cell + 2) + cell / 2;
        g.fill(ax, ay - 1, ax + 18, ay + 1, C_DIM);
        g.fill(ax + 14, ay - 4, ax + 16, ay + 4, C_DIM);
        g.fill(ax + 16, ay - 2, ax + 18, ay + 2, C_DIM);
        int rs = 36;
        int rx = ax + 28;
        int ry = gy + (cell + 2) + cell / 2 - rs / 2;
        text(g, "RESULT", rx, gy - 12, C_DIMMER);
        drawSlot(g, r, RESULT, rx, ry, rs);

        // options
        int ox = rx + rs + 18;
        int ow = ex + ew - ox;
        if (ow > 100) {
            int oy = gy - 2;
            oy += optionRow(g, ox, oy, ow, "Auto-craft: " + (r.autoCraft ? "ON" : "OFF"), r.autoCraft ? C_GREEN : C_DIM, b -> {
                r.autoCraft = !r.autoCraft;
                changed();
            });
            oy += optionRow(g, ox, oy, ow, r.drop ? "Result: drop on ground" : "Result: to inventory", 0, b -> {
                r.drop = !r.drop;
                changed();
            });
            oy += optionRow(g, ox, oy, ow, "Ingredient flow: " + (r.flow ? "ON" : "OFF"), r.flow ? C_BLUE : C_DIM, b -> {
                r.flow = !r.flow;
                changed();
            });
            oy += optionRow(g, ox, oy, ow, "Flow speed: " + rateLabel(r.flowRate) + " sets/s", r.flow ? 0 : C_DIMMER, b -> {
                int idx = 0;
                for (int i = 0; i < RATES.length; i++) if (Math.abs(RATES[i] - r.flowRate) < 0.001) idx = i;
                idx = (idx + (b == 1 ? RATES.length - 1 : 1)) % RATES.length;
                r.flowRate = RATES[idx];
                changed();
            });
            optionRow(g, ox, oy, ow, "Delete recipe", C_RED, b -> deleteRecipe());
        }

        // amount controls for the selected slot
        int cy = gy + 3 * (cell + 2) + 6;
        if (slot >= 0 && !idAt(r, slot).isEmpty()) {
            int x = ex;
            String label = (slot == RESULT ? "Result" : "Slot " + (slot + 1)) + ": " + fit(nameOf(idAt(r, slot)), 110);
            text(g, label, x, cy + 4, C_TEXT);
            x = ex + 150;
            final int s = slot;
            x += chip(g, x, cy, "-10", 0, b -> setCount(r, s, countAt(r, s) - 10)) + 3;
            x += chip(g, x, cy, "-1", 0, b -> setCount(r, s, countAt(r, s) - 1)) + 3;
            String n = String.valueOf(countAt(r, s));
            text(g, n, x + 4, cy + 4, C_ACCENT);
            x += this.font.width(n) + 12;
            x += chip(g, x, cy, "+1", 0, b -> setCount(r, s, countAt(r, s) + 1)) + 3;
            chip(g, x, cy, "+10", 0, b -> setCount(r, s, countAt(r, s) + 10));
        } else {
            text(g, "Click a slot to pick an item. Scroll over it to change the amount.", ex, cy + 4, C_DIMMER);
        }

        // plain-language summary
        int sy = cy + 24;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Item, Integer> e : r.needs().entrySet()) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append(e.getValue()).append("x ").append(new ItemStack(e.getKey()).getHoverName().getString());
        }
        List<String> lines = new ArrayList<>();
        if (!r.usable()) {
            lines.addAll(wrap("Pick at least one ingredient and a result item to finish this recipe.", ew, 2));
        } else {
            String sum = "Needs " + sb + "  =  " + r.resultCount + "x " + nameOf(r.resultId) + ".";
            sum += r.drop ? " The result is dropped next to you." : " The result goes into your inventory.";
            if (r.flow) sum += " The ingredients keep arriving, " + rateLabel(r.flowRate) + " set(s) per second.";
            lines.addAll(wrap(sum, ew, 4));
        }
        int maxLines = Math.max(1, (py + ph - 20 - sy) / 11);
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            text(g, lines.get(i), ex, sy + i * 11, r.usable() ? C_DIM : C_RED);
        }

        String foot = "Works with items from other mods. Only runs in single-player worlds (or where a server owner allows it).";
        text(g, fit(foot, pw - 24), px + 12, py + ph - 14, C_DIMMER);
    }

    private String rateLabel(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v);
    }

    private int optionRow(GuiGraphics g, int x, int y, int w, String label, int accent, IntConsumer action) {
        int cw = Math.min(w, this.font.width(label) + 14);
        chip(g, x, y, label, accent, b -> {
            if (!picker) action.accept(b);
        });
        return 20;
    }

    private void drawSlot(GuiGraphics g, RecipeDef r, int s, int x, int y, int size) {
        slotRect[s][0] = x;
        slotRect[s][1] = y;
        slotRect[s][2] = size;
        slotRect[s][3] = size;
        boolean selected = slot == s;
        boolean hover = !picker && mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size;
        g.fill(x, y, x + size, y + size, hover ? 0xFF222A3B : C_PANEL);
        border(g, x, y, size, size, selected ? C_ACCENT : s == RESULT ? C_GREEN : C_BORDER);

        String id = idAt(r, s);
        ItemStack st = stackOf(id);
        if (st != null) {
            g.renderItem(st, x + (size - 16) / 2, y + (size - 16) / 2);
            int count = countAt(r, s);
            if (count > 1) {
                String n = String.valueOf(count);
                g.drawString(this.font, n, x + size - this.font.width(n) - 2, y + size - 10, 0xFFFFFFFF, true);
            }
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
            if (b == 1) clearSlot(r, target);
            else openPicker(target);
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

        String title = slot == RESULT ? "Choose the RESULT item" : "Choose an ingredient";
        text(g, title, x + 10, y + 9, C_ACCENT);
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
                if (r != null && slot >= 0) setItem(r, slot, e.id());
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
