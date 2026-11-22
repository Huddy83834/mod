package com.punishfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Punishments page: pick a level, then tick which punishments it can use. Each one has a ▶ test. */
public class PunishPoolScreen extends PfxFrame {
    private static int selLevel = 0;

    private record Cell(String id, int x, int y, int w, int h) {}
    private record Header(String label, int y, int color) {}
    private final List<Cell> cells = new ArrayList<>();
    private final List<Header> headers = new ArrayList<>();
    private int summaryY;
    private boolean legend;

    public PunishPoolScreen(Screen parent) {
        super(parent, Page.PUNISHMENTS, "Punishments", "what each level can do to you");
    }

    @Override
    protected void initPage() {
        Config c = Config.get();
        cells.clear();
        headers.clear();
        if (selLevel >= c.tiers.size()) selLevel = 0;

        // --- level tabs (left) + pool shortcuts (right)
        int shortcutsW = 132;
        int n = c.tiers.size();
        int tabW = Math.max(26, Math.min(48, (cw - shortcutsW - 6 - (n - 1) * 3) / Math.max(1, n)));
        for (int i = 0; i < n; i++) {
            final int idx = i;
            addRenderableWidget(new FancyButton(cx + i * (tabW + 3), cy, tabW, 18, Component.literal(tabW >= 40 ? "Lv " + (i + 1) : String.valueOf(i + 1)), b -> {
                selLevel = idx;
                rebuild();
            }).accent(Ui.ACCENT).hot(() -> idx == selLevel));
        }
        addRenderableWidget(new FancyButton(cx + cw - shortcutsW, cy, 52, 18, Component.literal("All"), b -> {
            c.tiers.get(selLevel).pool.clear();
            Config.save();
        }).accent(Ui.GOOD).hot(() -> c.tiers.get(selLevel).pool.isEmpty()));
        addRenderableWidget(new FancyButton(cx + cw - shortcutsW + 55, cy, 77, 18, Component.literal("Random 5"), b -> {
            List<String> safe = new ArrayList<>();
            for (Punishes.P p : Punishes.all()) if (!p.extreme) safe.add(p.id);
            Collections.shuffle(safe);
            Config.Tier t = c.tiers.get(selLevel);
            t.pool.clear();
            t.pool.addAll(safe.subList(0, Math.min(5, safe.size())));
            Config.save();
        }).accent(Ui.WARN));

        summaryY = cy + 23;

        // --- the checklist. Columns are chosen so names fit; grouped under headings if there
        //     is room, otherwise one colour-coded grid (colour = category).
        int top = cy + 36, bottom = cy + ch;
        int minColW = 132;
        int cols = Math.max(2, Math.min(4, (cw + 4) / minColW));
        int colW = (cw + 4) / cols;
        int headH = 12;
        List<Punishes.P> all = Punishes.all();

        int groupedRows = 0, groups = 0;
        for (Punishes.Cat cat : Punishes.Cat.values()) {
            int count = (int) all.stream().filter(p -> p.cat == cat).count();
            if (count == 0) continue;
            groups++;
            groupedRows += (int) Math.ceil(count / (double) cols);
        }
        int cellH = 16, gapY = 2;
        boolean grouped = top + groups * headH + groupedRows * (cellH + gapY) <= bottom;
        if (!grouped) {
            int rows = (int) Math.ceil(all.size() / (double) cols);
            int fit = (bottom - top) / rows;
            cellH = Math.max(12, Math.min(16, fit - 1));
            gapY = fit - cellH >= 2 ? 2 : 1;
        }

        int y = top;
        if (grouped) {
            for (Punishes.Cat cat : Punishes.Cat.values()) {
                List<Punishes.P> list = all.stream().filter(p -> p.cat == cat).toList();
                if (list.isEmpty()) continue;
                headers.add(new Header(cat.label, y, cat.color));
                y += headH;
                for (int i = 0; i < list.size(); i++) {
                    addCell(list.get(i), cx + (i % cols) * colW, y + (i / cols) * (cellH + gapY), colW - 4, cellH);
                }
                y += (int) Math.ceil(list.size() / (double) cols) * (cellH + gapY);
            }
        } else {
            // column-major so each category stays together going down
            int rows = (int) Math.ceil(all.size() / (double) cols);
            for (int i = 0; i < all.size(); i++) {
                addCell(all.get(i), cx + (i / rows) * colW, y + (i % rows) * (cellH + gapY), colW - 4, cellH);
            }
        }
        legend = !grouped;
    }

    private void addCell(Punishes.P p, int x, int y, int w, int h) {
        boolean inWorld = Minecraft.getInstance().player != null;
        int testW = 14;
        addRenderableWidget(new FancyButton(x, y, w - testW - 2, h, Component.literal(p.name), b -> toggle(p.id))
                .check(() -> inPool(Config.get().tiers.get(selLevel), p.id))
                .accent(() -> p.cat.color));
        FancyButton test = new FancyButton(x + w - testW, y, testW, h, Component.literal("▶"), b -> {
            Config.save();
            Minecraft.getInstance().setScreen(null);
            PunishManager.force(p.id, selLevel);
        });
        test.accent(Ui.WARN).active = inWorld;
        addRenderableWidget(test);
        cells.add(new Cell(p.id, x, y, w, h));
    }

    private void toggle(String id) {
        Config.Tier t = Config.get().tiers.get(selLevel);
        Set<String> cur = new LinkedHashSet<>();
        if (t.pool.isEmpty()) {
            for (Punishes.P p : Punishes.all()) if (!p.extreme) cur.add(p.id);
        } else {
            cur.addAll(t.pool);
        }
        if (!cur.remove(id)) cur.add(id);
        Set<String> allSafe = new LinkedHashSet<>();
        for (Punishes.P p : Punishes.all()) if (!p.extreme) allSafe.add(p.id);
        t.pool.clear();
        if (!cur.isEmpty() && !cur.equals(allSafe)) t.pool.addAll(cur);
        Config.save();
    }

    private static boolean inPool(Config.Tier t, String id) {
        Punishes.P p = Punishes.get(id);
        if (t.pool.isEmpty()) return p != null && !p.extreme;
        return t.pool.contains(id);
    }

    @Override
    protected void renderPage(GuiGraphics g, int mouseX, int mouseY) {
        Config c = Config.get();
        Config.Tier t = c.tiers.get(selLevel);
        int chosen = 0;
        for (Punishes.P p : Punishes.all()) if (inPool(t, p.id)) chosen++;
        String sum = t.name + "  " + Money.compact(t.minAmount) + "+  " + fmt(t.seconds) + "s  x" + t.count;
        int legendW = 0;
        if (legend) for (Punishes.Cat cat : Punishes.Cat.values())
            legendW += this.font.width(cat == Punishes.Cat.VIEW ? "Camera" : cat.label) + 15;
        Ui.text(g, this.font, sum, cx, summaryY, 0xC8B4BA, cw - legendW - 6);

        for (Header h : headers) Ui.section(g, this.font, h.label(), cx, h.y() + 1, cw);
        if (legend) {
            // compact colour key in place of headings
            int lx = cx + cw;
            for (int i = Punishes.Cat.values().length - 1; i >= 0; i--) {
                Punishes.Cat cat = Punishes.Cat.values()[i];
                String word = cat == Punishes.Cat.VIEW ? "Camera" : cat.label;
                lx -= this.font.width(word);
                g.drawString(this.font, word, lx, summaryY, Ui.a(1f, cat.color), false);
                lx -= 9;
                g.fill(lx, summaryY + 1, lx + 6, summaryY + 7, Ui.a(1f, cat.color));
                lx -= 6;
            }
        }

        footer = null;
        for (Cell cell : cells) {
            if (mouseX >= cell.x() && mouseX < cell.x() + cell.w() && mouseY >= cell.y() && mouseY < cell.y() + cell.h()) {
                boolean onTest = mouseX >= cell.x() + cell.w() - 14;
                footer = onTest
                        ? (Minecraft.getInstance().player == null ? "Join a world to test punishments." : "Try " + Punishes.name(cell.id()) + " on yourself right now.")
                        : Punishes.name(cell.id()) + ": " + Punishes.desc(cell.id());
            }
        }
        if (footer == null && mouseY >= cy && mouseY < cy + 18 && mouseX >= cx + cw - 132)
            footer = mouseX < cx + cw - 80 ? "Let this level use any punishment (except the extreme two)." : "Pick 5 random punishments for this level.";
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((int) d) : String.valueOf(d);
    }
}
