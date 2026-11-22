package com.punishfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Choose which punishments each level may use. */
public class PunishPoolScreen extends Screen {
    private static final int BG = 0xF0120609;
    private static final int LINE = 0xFF3A2A2E;
    private static final int RED = 0xFFFF5C5C;
    private static final int TEXT = 0xFFE8EAF0;
    private static final int DIM = 0xFF9A8B8F;
    private static final int GREEN = 0xFF3DDC84;
    private static final int ORANGE = 0xFFFFAA33;

    private final Screen parent;
    private int px, py, pw, ph, gridY, cols, cellW, rowH = 20;
    private int selLevel = 0;
    private boolean preview = false;
    private final List<String> ids = new ArrayList<>();
    private String hover = null;

    public PunishPoolScreen(Screen parent) {
        super(Component.literal("Punish FX punishments"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Config c = Config.get();
        ids.clear();
        ids.addAll(Punishes.ids());
        pw = Math.min(this.width - 12, 620);
        ph = Math.min(this.height - 10, 310);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        if (selLevel >= c.tiers.size()) selLevel = 0;

        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(px + pw - 54, py + 6, 48, 18).build());

        int y = py + 42;
        int n = c.tiers.size();
        int lw = Math.min(70, (pw - 20) / Math.max(1, n));
        for (int i = 0; i < n; i++) {
            final int idx = i;
            Button b = Button.builder(Component.literal("Lv " + (i + 1)), x -> {
                selLevel = idx;
                rebuild();
            }).bounds(px + 10 + i * (lw + 2), y, lw, 18).build();
            b.active = i != selLevel;
            addRenderableWidget(b);
        }

        y += 22;
        int ow = (pw - 20 - 2 * 4) / 3;
        int ox = px + 10;
        addRenderableWidget(Button.builder(Component.literal("Any (random)"), b -> {
            c.tiers.get(selLevel).pool.clear();
            Config.save();
            rebuild();
        }).bounds(ox, y, ow, 18).build());
        ox += ow + 4;
        addRenderableWidget(Button.builder(Component.literal("Surprise 5"), b -> {
            Config.Tier t = c.tiers.get(selLevel);
            List<String> safe = new ArrayList<>();
            for (Punishes.P p : Punishes.all()) if (!p.extreme) safe.add(p.id);
            java.util.Collections.shuffle(safe);
            t.pool.clear();
            t.pool.addAll(safe.subList(0, Math.min(5, safe.size())));
            Config.save();
            rebuild();
        }).bounds(ox, y, ow, 18).build());
        ox += ow + 4;
        addRenderableWidget(Button.builder(Component.literal(preview ? "Click: Preview" : "Click: Choose"), b -> {
            preview = !preview;
            b.setMessage(Component.literal(preview ? "Click: Preview" : "Click: Choose"));
        }).bounds(ox, y, ow, 18).build());

        gridY = y + 24;
        int gridBottom = py + ph - 24;
        int avail = gridBottom - gridY;
        int maxRows = Math.max(4, avail / rowH);
        cols = Math.max(3, (int) Math.ceil(ids.size() / (double) maxRows));
        cols = Math.min(cols, 7);
        cellW = (pw - 20) / cols;
        for (int i = 0; i < ids.size(); i++) {
            final String id = ids.get(i);
            int gx = px + 10 + (i % cols) * cellW;
            int gy = gridY + (i / cols) * rowH;
            addRenderableWidget(Button.builder(Component.literal(""), b -> cellClick(id)).bounds(gx, gy, cellW - 2, rowH - 2).build());
        }
    }

    private void cellClick(String id) {
        if (preview) {
            Minecraft mc = Minecraft.getInstance();
            Config.save();
            onClose();
            if (mc.screen != null) mc.setScreen(null);
            PunishManager.force(id, selLevel);
            return;
        }
        Config.Tier t = Config.get().tiers.get(selLevel);
        Set<String> cur = new LinkedHashSet<>();
        if (t.pool.isEmpty()) {
            for (Punishes.P p : Punishes.all()) if (!p.extreme) cur.add(p.id);
        } else {
            cur.addAll(t.pool);
        }
        if (cur.contains(id)) cur.remove(id);
        else cur.add(id);
        Set<String> allSafe = new LinkedHashSet<>();
        for (Punishes.P p : Punishes.all()) if (!p.extreme) allSafe.add(p.id);
        if (cur.isEmpty() || cur.equals(allSafe)) t.pool.clear();
        else {
            t.pool.clear();
            t.pool.addAll(cur);
        }
        Config.save();
        PunishManager.playSound(Minecraft.getInstance(), "minecraft:ui.button.click", 0.4f, 1.3f);
    }

    private boolean inPool(Config.Tier t, String id) {
        Punishes.P p = Punishes.get(id);
        if (t.pool.isEmpty()) return p != null && !p.extreme;
        return t.pool.contains(id);
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, 0x90000000);
        g.fill(px, py, px + pw, py + ph, BG);
        g.fill(px, py, px + pw, py + 2, RED);
        g.fill(px, py + ph - 1, px + pw, py + ph, LINE);
        g.fill(px, py, px + 1, py + ph, LINE);
        g.fill(px + pw - 1, py, px + pw, py + ph, LINE);
        g.fill(px + 8, py + 28, px + pw - 8, py + 29, LINE);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        Config c = Config.get();
        Config.Tier sel = c.tiers.get(selLevel);
        g.drawString(this.font, "PUNISHMENTS  (" + ids.size() + ")", px + 10, py + 9, RED, true);
        int poolN = sel.pool.isEmpty() ? (ids.size() - 2) : sel.pool.size();
        g.drawString(this.font, "Level " + (selLevel + 1) + " \"" + sel.name + "\" uses: " + (sel.pool.isEmpty() ? "any random" : poolN + " chosen")
                + (preview ? "   -  click to PREVIEW" : "   -  click to add/remove"), px + 10, py + 19, DIM, false);

        hover = null;
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            Punishes.P pp = Punishes.get(id);
            int gx = px + 10 + (i % cols) * cellW;
            int gy = gridY + (i / cols) * rowH;
            boolean on = inPool(sel, id);
            boolean ext = pp != null && pp.extreme;
            if (mouseX >= gx && mouseX < gx + cellW - 2 && mouseY >= gy && mouseY < gy + rowH - 2) hover = id;
            int fillCol = on ? (ext ? 0x40FFAA33 : 0x4033DC84) : 0x33201014;
            g.fill(gx, gy, gx + cellW - 2, gy + rowH - 2, fillCol);
            g.fill(gx, gy, gx + 3, gy + rowH - 2, on ? (ext ? ORANGE : GREEN) : LINE);
            String name = Punishes.name(id);
            int maxW = cellW - 10;
            String shown = this.font.width(name) > maxW ? this.font.plainSubstrByWidth(name, maxW - 4) + ".." : name;
            g.drawString(this.font, shown, gx + 6, gy + (rowH - 2) / 2 - 3, on ? (ext ? ORANGE : TEXT) : DIM, false);
        }
        String d = hover != null
                ? Punishes.name(hover) + ": " + Punishes.desc(hover)
                : "Each donation runs random punishment(s) from the level's pool. Orange = leaves/closes your game.";
        int ty = py + ph - 22;
        for (var line : this.font.split(Component.literal(d), pw - 20)) {
            if (ty > py + ph - 2) break;
            g.drawString(this.font, line, px + 10, ty, hover != null ? TEXT : DIM, false);
            ty += 10;
        }
    }

    @Override
    public void onClose() {
        Config.save();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
