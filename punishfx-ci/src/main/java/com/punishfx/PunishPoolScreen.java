package com.punishfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Choose which punishments each level may use, and test any one with its own play button. */
public class PunishPoolScreen extends Screen {
    private final Screen parent;
    private int px, py, pw, ph, gridY, cols, cellW, rowH = 20;
    private int selLevel = 0;
    private final List<String> ids = new ArrayList<>();
    private int[] cx, cy;
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
        pw = Math.min(this.width - 12, 640);
        ph = Math.min(this.height - 10, 300);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        if (selLevel >= c.tiers.size()) selLevel = 0;

        addRenderableWidget(new FancyButton(px + pw - 52, py + 8, 44, 18, Component.literal("Back"), b -> onClose()));

        int y = py + 44;
        int n = c.tiers.size();
        int lw = Math.min(66, (pw - 24) / Math.max(1, n));
        for (int i = 0; i < n; i++) {
            final int idx = i;
            addRenderableWidget(new FancyButton(px + 12 + i * (lw + 3), y, lw, 18, Component.literal("Lv " + (i + 1)), x -> {
                selLevel = idx;
                rebuild();
            }).accent(Ui.ACCENT).hot(() -> idx == selLevel));
        }

        y += 24;
        int ow = (pw - 24 - 5) / 3;
        addRenderableWidget(new FancyButton(px + 12, y, ow, 18, Component.literal("Any (random)"), b -> {
            c.tiers.get(selLevel).pool.clear();
            Config.save();
            rebuild();
        }).accent(Ui.GOOD).hot(() -> c.tiers.get(selLevel).pool.isEmpty()));
        addRenderableWidget(new FancyButton(px + 12 + ow + 2, y, ow, 18, Component.literal("Surprise 5"), b -> {
            Config.Tier t = c.tiers.get(selLevel);
            List<String> safe = new ArrayList<>();
            for (Punishes.P p : Punishes.all()) if (!p.extreme) safe.add(p.id);
            java.util.Collections.shuffle(safe);
            t.pool.clear();
            t.pool.addAll(safe.subList(0, Math.min(5, safe.size())));
            Config.save();
            rebuild();
        }));
        addRenderableWidget(new FancyButton(px + 12 + 2 * (ow + 2), y, ow, 18, Component.literal("Test random"), b -> {
            Config.save();
            Minecraft mc = Minecraft.getInstance();
            onClose();
            if (mc.screen != null) mc.setScreen(null);
            PunishManager.hit(c.tiers.get(selLevel).minAmount, mc.player == null ? "You" : mc.player.getGameProfile().name(), selLevel, true);
        }).accent(Ui.WARN));

        gridY = y + 24;
        int gridBottom = py + ph - 24;
        int avail = gridBottom - gridY;
        int maxRows = Math.max(4, avail / rowH);
        cols = Math.max(2, (int) Math.ceil(ids.size() / (double) maxRows));
        cols = Math.min(cols, 5);
        cellW = (pw - 24) / cols;
        cx = new int[ids.size()];
        cy = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            final String id = ids.get(i);
            int gx = px + 12 + (i % cols) * cellW;
            int gy = gridY + (i / cols) * rowH;
            cx[i] = gx;
            cy[i] = gy;
            Punishes.P pp = Punishes.get(id);
            boolean extreme = pp != null && pp.extreme;
            // checkbox cell (name) - selection state read live every frame
            addRenderableWidget(new FancyButton(gx, gy, cellW - 20, rowH - 2, Component.literal(Punishes.name(id)), b -> {
                toggle(id);
            }).check(() -> inPool(Config.get().tiers.get(selLevel), id)).accent(() -> extreme ? Ui.WARN : Ui.GOOD));
            // per-punishment test
            addRenderableWidget(new FancyButton(gx + cellW - 18, gy, 16, rowH - 2, Component.literal("▶"), b -> test(id)).accent(Ui.WARN));
        }
    }

    private void test(String id) {
        Minecraft mc = Minecraft.getInstance();
        Config.save();
        onClose();
        if (mc.screen != null) mc.setScreen(null);
        PunishManager.force(id, selLevel);
    }

    private void toggle(String id) {
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
        Ui.panel(g, this.width, this.height, px, py, pw, ph, System.currentTimeMillis());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        Config c = Config.get();
        Config.Tier sel = c.tiers.get(selLevel);
        Ui.glowText(g, this.font, "PUNISHMENTS", px + 12, py + 11, Ui.ACCENT, 1.5f);
        int poolN = sel.pool.isEmpty() ? (ids.size() - 2) : sel.pool.size();
        g.drawString(this.font, "Level " + (selLevel + 1) + " \"" + sel.name + "\": " + (sel.pool.isEmpty() ? "any random" : poolN + " chosen")
                        + "   ·   ▶ tests one   ·   orange = leaves/closes game",
                px + 12 + (int) (this.font.width("PUNISHMENTS") * 1.5f) + 10, py + 15, Ui.a(1f, Ui.DIM), false);

        hover = null;
        if (cx != null) {
            for (int i = 0; i < ids.size(); i++) {
                if (mouseX >= cx[i] && mouseX < cx[i] + cellW - 2 && mouseY >= cy[i] && mouseY < cy[i] + rowH - 2) hover = ids.get(i);
            }
        }
        String d = hover != null ? Punishes.name(hover) + ": " + Punishes.desc(hover)
                : "Each donation runs random punishment(s) from the level's pool. Click a name to add/remove.";
        int ty = py + ph - 20;
        for (var line : this.font.split(Component.literal(d), pw - 24)) {
            if (ty > py + ph - 2) break;
            g.drawString(this.font, line, px + 12, ty, Ui.a(1f, hover != null ? Ui.TEXT : Ui.DIM), false);
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
