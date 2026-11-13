package com.donationfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Choose which animations are in each level's pool, with a secret-code lock in front of them. */
public class ThemesScreen extends Screen {
    private static final int BG = 0xF00B0D13;
    private static final int LINE = 0xFF2A3142;
    private static final int GOLD = 0xFFF5B83D;
    private static final int TEXT = 0xFFE8EAF0;
    private static final int DIM = 0xFF8B93A7;
    private static final int GREEN = 0xFF3DDC84;

    private final Screen parent;
    private int px, py, pw, ph;
    private int gridY, cols, cellW, rowH = 20;
    private int selLevel = 0;
    private boolean previewMode = false;
    private final List<String> ids = new ArrayList<>();
    private String hover = null;

    // secret panel (keypad)
    private static final int CODE_LEN = 4;
    private static int wrongTries = 0;
    private static long lockedUntil = 0;
    private String typed = "";
    private long wrongAt = 0;
    private long unlockedAt = 0;
    private boolean locked;

    public ThemesScreen(Screen parent) {
        super(Component.literal("Donation FX animations"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        locked = !Themes.unlocked();
        if (locked) {
            initLocked();
            return;
        }
        Config c = Config.get();
        ids.clear();
        ids.addAll(Themes.ids());
        pw = Math.min(this.width - 12, 620);
        ph = Math.min(this.height - 10, 320);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        if (selLevel >= c.tiers.size()) selLevel = 0;

        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(px + pw - 54, py + 6, 48, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Lock"), b -> {
            c.secretUnlocked = false;
            Config.save();
            Fx.playId("minecraft:block.iron_door.close", 0.8f, 1.0f);
            typed = "";
            rebuild();
        }).bounds(px + pw - 54 - 4 - 50, py + 6, 50, 18).build());

        // level selector row
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

        // controls row
        y += 22;
        int ow = (pw - 20 - 4 * 4) / 5;
        int ox = px + 10;
        addRenderableWidget(Button.builder(Component.literal("All (random)"), b -> {
            c.tiers.get(selLevel).pool.clear();
            Config.save();
            rebuild();
        }).bounds(ox, y, ow, 18).build());
        ox += ow + 4;
        addRenderableWidget(Button.builder(Component.literal("Classic only"), b -> {
            Config.Tier t = c.tiers.get(selLevel);
            t.pool.clear();
            t.pool.add(Themes.CLASSIC);
            Config.save();
            rebuild();
        }).bounds(ox, y, ow, 18).build());
        ox += ow + 4;
        addRenderableWidget(Button.builder(Component.literal("Surprise 6"), b -> {
            Config.Tier t = c.tiers.get(selLevel);
            List<String> all = new ArrayList<>();
            for (String id : ids) if (Themes.enabled(id)) all.add(id);
            java.util.Collections.shuffle(all);
            t.pool.clear();
            t.pool.addAll(all.subList(0, Math.min(6, all.size())));
            Config.save();
            rebuild();
        }).bounds(ox, y, ow, 18).build());
        ox += ow + 4;
        addRenderableWidget(Button.builder(Component.literal(previewMode ? "Click: Preview" : "Click: Choose"), b -> {
            previewMode = !previewMode;
            b.setMessage(Component.literal(previewMode ? "Click: Preview" : "Click: Choose"));
        }).bounds(ox, y, ow, 18).build());
        ox += ow + 4;
        addRenderableWidget(Button.builder(Component.literal(soundLabel()), b -> {
            c.themeSounds = !c.themeSounds;
            b.setMessage(Component.literal(soundLabel()));
            Config.save();
        }).bounds(ox, y, ow, 18).build());

        // animation grid: fit columns so every animation shows without scrolling
        gridY = y + 24;
        int gridBottom = py + ph - 24;
        int avail = gridBottom - gridY;
        int maxRows = Math.max(4, avail / rowH);
        cols = Math.max(3, (int) Math.ceil(ids.size() / (double) maxRows));
        cols = Math.min(cols, 8);
        cellW = (pw - 20) / cols;
        for (int i = 0; i < ids.size(); i++) {
            final String id = ids.get(i);
            int gx = px + 10 + (i % cols) * cellW;
            int gy = gridY + (i / cols) * rowH;
            addRenderableWidget(Button.builder(Component.literal(""), b -> cellClick(id)).bounds(gx, gy, cellW - 2, rowH - 2).build());
        }
    }

    private void cellClick(String id) {
        Config c = Config.get();
        if (previewMode) {
            test(id);
            return;
        }
        Config.Tier t = c.tiers.get(selLevel);
        Set<String> cur = new LinkedHashSet<>();
        if (t.pool.isEmpty()) {
            for (String x : ids) if (Themes.enabled(x)) cur.add(x);
        } else {
            cur.addAll(t.pool);
        }
        if (cur.contains(id)) cur.remove(id);
        else cur.add(id);
        // can't choose nothing: empty falls back to "all"
        Set<String> allEnabled = new LinkedHashSet<>();
        for (String x : ids) if (Themes.enabled(x)) allEnabled.add(x);
        if (cur.isEmpty() || cur.equals(allEnabled)) t.pool.clear();
        else {
            t.pool.clear();
            t.pool.addAll(cur);
        }
        Config.save();
        Fx.playId("minecraft:ui.button.click", 0.4f, 1.3f);
    }

    private boolean inPool(Config.Tier t, String id) {
        return t.pool.isEmpty() || t.pool.contains(id);
    }

    // ------------------------------------------------------------------ secret keypad

    private void initLocked() {
        pw = 190;
        ph = 236;
        px = (this.width - pw) / 2;
        py = Math.max(4, (this.height - ph) / 2);
        int kw = 46, kh = 24, gap = 6;
        int gx = px + (pw - (kw * 3 + gap * 2)) / 2;
        int gy = py + 82;
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "OK"};
        for (int i = 0; i < keys.length; i++) {
            String k = keys[i];
            int x = gx + (i % 3) * (kw + gap), y = gy + (i / 3) * (kh + gap);
            addRenderableWidget(Button.builder(Component.literal(k), b -> press(k)).bounds(x, y, kw, kh).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(px + pw / 2 - 30, py + ph - 26, 60, 20).build());
    }

    private void press(String k) {
        if (System.currentTimeMillis() < lockedUntil) return;
        switch (k) {
            case "C" -> {
                typed = "";
                Fx.playId("minecraft:block.stone_button.click_on", 0.6f, 0.8f);
            }
            case "OK" -> submit();
            default -> {
                if (typed.length() >= CODE_LEN) return;
                typed += k;
                Fx.playId("minecraft:block.note_block.hat", 0.6f, 1.0f + typed.length() * 0.15f);
                if (typed.length() == CODE_LEN) submit();
            }
        }
    }

    private void submit() {
        if (typed.isEmpty()) return;
        if (Themes.checkCode(typed)) {
            Config c = Config.get();
            c.secretUnlocked = true;
            c.disabledThemes.clear();
            Config.save();
            wrongTries = 0;
            unlockedAt = System.currentTimeMillis();
            Fx.playId("minecraft:block.iron_door.open", 0.9f, 1.0f);
            Fx.playId("minecraft:ui.toast.challenge_complete", 1.0f, 1.0f);
            typed = "";
            rebuild();
            return;
        }
        wrongTries++;
        wrongAt = System.currentTimeMillis();
        typed = "";
        Fx.playId("minecraft:block.note_block.bass", 1.0f, 0.5f);
        Fx.playId("minecraft:entity.villager.no", 0.8f, 1.0f);
        if (wrongTries >= 3) {
            lockedUntil = System.currentTimeMillis() + 30_000;
            wrongTries = 0;
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (locked) {
            int key = event.key();
            if (key >= 48 && key <= 57) {
                press(String.valueOf(key - 48));
                return true;
            }
            if (key >= 320 && key <= 329) {
                press(String.valueOf(key - 320));
                return true;
            }
            if (key == 257 || key == 335) {
                press("OK");
                return true;
            }
            if (key == 259) {
                if (!typed.isEmpty()) typed = typed.substring(0, typed.length() - 1);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    private void renderLocked(GuiGraphics g, int mouseX, int mouseY) {
        long now = System.currentTimeMillis();
        int cx = px + pw / 2;
        g.drawCenteredString(this.font, "SECRET ANIMATIONS", cx, py + 10, GOLD);
        g.drawCenteredString(this.font, "Enter the code to unlock", cx, py + 22, DIM);
        g.drawCenteredString(this.font, "40 extra animations.", cx, py + 32, DIM);
        int shake = now - wrongAt < 400 ? (int) (Math.sin((now - wrongAt) / 25.0) * 4) : 0;
        int dw = 24, gap = 8;
        int dx = cx - (dw * CODE_LEN + gap * (CODE_LEN - 1)) / 2 + shake;
        int dy = py + 48;
        boolean wrong = now - wrongAt < 900;
        for (int i = 0; i < CODE_LEN; i++) {
            int x = dx + i * (dw + gap);
            int border = wrong ? 0xFFFF5C5C : i < typed.length() ? GOLD : LINE;
            g.fill(x - 1, dy - 1, x + dw + 1, dy + 23, border);
            g.fill(x, dy, x + dw, dy + 22, 0xFF05060A);
            if (i < typed.length()) g.drawCenteredString(this.font, "●", x + dw / 2, dy + 7, TEXT);
        }
        String status;
        int col;
        if (now < lockedUntil) {
            status = "Too many tries. Wait " + ((lockedUntil - now) / 1000 + 1) + "s";
            col = 0xFFFF5C5C;
        } else if (wrong) {
            status = "Wrong code";
            col = 0xFFFF5C5C;
        } else {
            status = "✦ Locked";
            col = DIM;
        }
        g.drawCenteredString(this.font, status, cx, py + ph - 40, col);
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private static String soundLabel() {
        return "Music: " + (Config.get().themeSounds ? "ON" : "OFF");
    }

    private void test(String id) {
        Config c = Config.get();
        Config.save();
        Minecraft mc = Minecraft.getInstance();
        String me = mc.player == null ? "You" : mc.player.getGameProfile().name();
        Config.Tier t = c.tiers.get(selLevel);
        onClose();
        if (mc.screen != null) mc.setScreen(null);
        Fx.donate(t.minAmount, me, selLevel, true, id);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, 0x90000000);
        g.fill(px, py, px + pw, py + ph, BG);
        g.fill(px, py, px + pw, py + 2, GOLD);
        g.fill(px, py + ph - 1, px + pw, py + ph, LINE);
        g.fill(px, py, px + 1, py + ph, LINE);
        g.fill(px + pw - 1, py, px + pw, py + ph, LINE);
        if (!locked) g.fill(px + 8, py + 28, px + pw - 8, py + 29, LINE);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        if (locked) {
            renderLocked(g, mouseX, mouseY);
            return;
        }
        Config c = Config.get();
        Config.Tier sel = c.tiers.get(selLevel);
        g.drawString(this.font, "ANIMATION POOLS  (" + (ids.size()) + " animations)", px + 10, py + 9, GOLD, true);
        int poolN = sel.pool.isEmpty() ? ids.size() : sel.pool.size();
        String poolDesc = sel.pool.isEmpty() ? "all (random)" : poolN + " chosen";
        g.drawString(this.font, "Level " + (selLevel + 1) + " \"" + sel.name + "\" plays: " + poolDesc
                + (previewMode ? "   -  click an animation to PREVIEW it" : "   -  click to add/remove from this level"), px + 10, py + 19, DIM, false);
        if (System.currentTimeMillis() - unlockedAt < 2500) {
            String u = "UNLOCKED!";
            g.drawString(this.font, u, px + pw - 10 - this.font.width(u), py + 9, GREEN, true);
        }

        hover = null;
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            int gx = px + 10 + (i % cols) * cellW;
            int gy = gridY + (i / cols) * rowH;
            boolean on = inPool(sel, id);
            boolean hovering = mouseX >= gx && mouseX < gx + cellW - 2 && mouseY >= gy && mouseY < gy + rowH - 2;
            if (hovering) hover = id;
            g.fill(gx, gy, gx + cellW - 2, gy + rowH - 2, on ? 0x4033DC84 : 0x33202634);
            g.fill(gx, gy, gx + 3, gy + rowH - 2, on ? GREEN : LINE);
            String name = Themes.name(id);
            int maxW = cellW - 10;
            String shown = this.font.width(name) > maxW ? this.font.plainSubstrByWidth(name, maxW - 4) + ".." : name;
            g.drawString(this.font, shown, gx + 6, gy + (rowH - 2) / 2 - 3, on ? TEXT : DIM, false);
        }
        String d = hover != null ? Themes.name(hover) + ": " + Themes.desc(hover)
                : "Each level randomly plays one animation from its pool. Empty pool = every animation.";
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
