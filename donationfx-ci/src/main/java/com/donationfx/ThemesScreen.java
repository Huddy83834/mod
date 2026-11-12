package com.donationfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Pick the animation for each level, switch themes on/off for "Random", and test any theme. */
public class ThemesScreen extends Screen {
    private static final int BG = 0xF00B0D13;
    private static final int LINE = 0xFF2A3142;
    private static final int GOLD = 0xFFF5B83D;
    private static final int TEXT = 0xFFE8EAF0;
    private static final int DIM = 0xFF8B93A7;

    private final Screen parent;
    private int px, py, pw, ph;
    private int gridY, cols, cellW;
    private static int testLevel = 2;
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
        pw = Math.min(this.width - 12, 600);
        ph = Math.min(this.height - 12, 310);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;

        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(px + pw - 56, py + 6, 50, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Lock"), b -> {
            c.secretUnlocked = false;
            Config.save();
            Fx.playId("minecraft:block.iron_door.close", 0.8f, 1.0f);
            typed = "";
            rebuild();
        }).bounds(px + pw - 56 - 4 - 64, py + 6, 64, 20).build());

        // which theme each level uses
        int y = py + 46;
        int lw = (pw - 20 - 4 * 4) / 5;
        for (int i = 0; i < c.tiers.size(); i++) {
            Config.Tier t = c.tiers.get(i);
            final int idx = i;
            addRenderableWidget(Button.builder(Component.literal(levelLabel(idx)), b -> {
                t.theme = Themes.next(t.theme, 1);
                b.setMessage(Component.literal(levelLabel(idx)));
                Config.save();
            }).bounds(px + 10 + i * (lw + 4), y, lw, 20).build());
        }

        y += 24;
        int ow = (pw - 20 - 3 * 4) / 4;
        addRenderableWidget(Button.builder(Component.literal(testLabel()), b -> {
            testLevel = (testLevel + 1) % 5;
            b.setMessage(Component.literal(testLabel()));
        }).bounds(px + 10, y, ow, 20).build());
        addRenderableWidget(Button.builder(Component.literal(soundLabel()), b -> {
            c.themeSounds = !c.themeSounds;
            b.setMessage(Component.literal(soundLabel()));
            Config.save();
        }).bounds(px + 10 + (ow + 4), y, ow, 20).build());
        addRenderableWidget(Button.builder(Component.literal("All on"), b -> {
            c.disabledThemes.clear();
            Config.save();
            rebuild();
        }).bounds(px + 10 + 2 * (ow + 4), y, ow, 20).build());
        addRenderableWidget(Button.builder(Component.literal("All off"), b -> {
            c.disabledThemes.clear();
            for (String id : ids) if (!id.equals(Themes.CLASSIC)) c.disabledThemes.add(id);
            Config.save();
            rebuild();
        }).bounds(px + 10 + 3 * (ow + 4), y, ow, 20).build());

        // every theme: on/off for Random, and a test button
        gridY = y + 34;
        cols = pw >= 380 ? 3 : 2;
        cellW = (pw - 20) / cols;
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            int cx = px + 10 + (i % cols) * cellW;
            int cy = gridY + (i / cols) * 22;
            addRenderableWidget(Button.builder(Component.literal(Themes.enabled(id) ? "ON" : "OFF"), b -> {
                Themes.setEnabled(id, !Themes.enabled(id));
                b.setMessage(Component.literal(Themes.enabled(id) ? "ON" : "OFF"));
                Config.save();
            }).bounds(cx, cy, 30, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), b -> test(id)).bounds(cx + 32, cy, 20, 20).build());
        }
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
            c.disabledThemes.clear(); // all 20 can now be picked
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
        String lock = "\u2726";
        g.drawCenteredString(this.font, "SECRET ANIMATIONS", cx, py + 10, GOLD);
        g.drawCenteredString(this.font, "Enter the code to unlock", cx, py + 22, DIM);
        g.drawCenteredString(this.font, "20 extra animations.", cx, py + 32, DIM);
        // code display
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
            if (i < typed.length()) g.drawCenteredString(this.font, "\u25cf", x + dw / 2, dy + 7, TEXT);
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
            status = lock + " Locked";
            col = DIM;
        }
        g.drawCenteredString(this.font, status, cx, py + ph - 40, col);
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private static String levelLabel(int i) {
        return "Lvl " + (i + 1) + ": " + Themes.name(Config.get().tiers.get(i).theme);
    }

    private static String testLabel() {
        return "Test as level " + (testLevel + 1);
    }

    private static String soundLabel() {
        return "Theme music: " + (Config.get().themeSounds ? "ON" : "OFF");
    }

    private void test(String id) {
        Config c = Config.get();
        Config.save();
        Minecraft mc = Minecraft.getInstance();
        String me = mc.player == null ? "You" : mc.player.getGameProfile().name();
        Config.Tier t = c.tiers.get(testLevel);
        onClose();
        if (mc.screen != null) mc.setScreen(null); // close everything so the animation can be seen
        Fx.donate(t.minAmount, me, testLevel, true, id);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, 0x90000000);
        g.fill(px, py, px + pw, py + ph, BG);
        g.fill(px, py, px + pw, py + 2, GOLD);
        g.fill(px, py + ph - 1, px + pw, py + ph, LINE);
        g.fill(px, py, px + 1, py + ph, LINE);
        g.fill(px + pw - 1, py, px + pw, py + ph, LINE);
        if (!locked) g.fill(px + 8, py + 30, px + pw - 8, py + 31, LINE);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        if (locked) {
            renderLocked(g, mouseX, mouseY);
            return;
        }
        if (System.currentTimeMillis() - unlockedAt < 2500) {
            String u = "UNLOCKED! All 20 animations are on.";
            g.drawString(this.font, u, px + pw - 10 - this.font.width(u), py + ph - 36, 0xFF3DDC84, true);
        }
        g.drawString(this.font, "ANIMATIONS  (" + ids.size() + ")", px + 10, py + 9, GOLD, true);
        g.drawString(this.font, "Each level can use one animation, or Random. ON = can be picked by Random. ▶ = test.", px + 10, py + 19, DIM, false);
        g.drawString(this.font, "LEVELS (click to change)", px + 10, py + 35, DIM, false);
        hover = null;
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            int cx = px + 10 + (i % cols) * cellW;
            int cy = gridY + (i / cols) * 22;
            String name = Themes.name(id);
            int maxW = cellW - 60;
            String shown = this.font.width(name) > maxW ? this.font.plainSubstrByWidth(name, maxW - 6) + ".." : name;
            g.drawString(this.font, shown, cx + 56, cy + 6, Themes.enabled(id) ? TEXT : DIM, false);
            if (mouseX >= cx && mouseX < cx + cellW - 4 && mouseY >= cy && mouseY < cy + 20) hover = id;
        }
        g.drawString(this.font, "THEMES", px + 10, gridY - 10, DIM, false);
        String d = hover != null ? Themes.name(hover) + ": " + Themes.desc(hover) : "Point at a theme to see what it does.";
        int ty = py + ph - 24;
        for (var line : this.font.split(Component.literal(d), pw - 20)) {
            if (ty > py + ph - 4) break;
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
