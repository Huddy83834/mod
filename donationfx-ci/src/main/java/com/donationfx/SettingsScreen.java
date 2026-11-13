package com.donationfx;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/** Donation FX global settings: a grid of every switch and slider, plus buttons to the level and animation editors. */
public class SettingsScreen extends Screen {
    private static final int BG = 0xF00B0D13;
    private static final int LINE = 0xFF2A3142;
    private static final int GOLD = 0xFFF5B83D;
    private static final int TEXT = 0xFFE8EAF0;
    private static final int DIM = 0xFF8B93A7;
    private static final int GREEN = 0xFF3DDC84;
    private static final int RED = 0xFFFF5C5C;

    private static final String[] COLOR_OPTIONS = {"", "rainbow", "#FFFFFF", "#FF5555", "#FFAA00", "#FFD700", "#55FF55", "#55AAFF", "#FF55FF"};
    private static final String[] COLOR_NAMES = {"Per level", "Rainbow", "White", "Red", "Orange", "Gold", "Green", "Blue", "Pink"};
    private static final double[] GOAL_OPTIONS = {0, 1_000_000, 10_000_000, 50_000_000, 100_000_000, 1_000_000_000};

    private final Screen parent;
    private int px, py, pw, ph;
    private boolean confirmReset = false;

    // grid placement
    private int gx0, gy0, colW, rowH, rowsPerCol;
    private int ctrl;

    public SettingsScreen(Screen parent) {
        super(Component.literal("Donation FX"));
        this.parent = parent;
    }

    private static final class Slider extends AbstractSliderButton {
        private final double min, max, step;
        private final DoubleConsumer set;
        private final DoubleFunction<String> label;

        Slider(int x, int y, int w, double min, double max, double step, double cur, DoubleConsumer set, DoubleFunction<String> label) {
            super(x, y, w, 20, Component.empty(), (cur - min) / (max - min));
            this.min = min;
            this.max = max;
            this.step = step;
            this.set = set;
            this.label = label;
            updateMessage();
        }

        private double val() {
            double v = min + this.value * (max - min);
            return Math.round(v / step) * step;
        }

        @Override
        protected void updateMessage() {
            if (label != null) setMessage(Component.literal(label.apply(val())));
        }

        @Override
        protected void applyValue() {
            if (set != null) set.accept(val());
        }
    }

    private int[] next() {
        int col = ctrl / rowsPerCol;
        int row = ctrl % rowsPerCol;
        ctrl++;
        return new int[]{gx0 + col * (colW + 4), gy0 + row * rowH};
    }

    private void toggle(String name, BooleanSupplier get, Consumer<Boolean> set) {
        int[] p = next();
        addRenderableWidget(Button.builder(Component.literal(name + ": " + (get.getAsBoolean() ? "ON" : "OFF")), btn -> {
            set.accept(!get.getAsBoolean());
            btn.setMessage(Component.literal(name + ": " + (get.getAsBoolean() ? "ON" : "OFF")));
            Config.save();
        }).bounds(p[0], p[1], colW, 20).build());
    }

    private void slider(double min, double max, double step, double cur, DoubleConsumer set, DoubleFunction<String> label) {
        int[] p = next();
        addRenderableWidget(new Slider(p[0], p[1], colW, min, max, step, cur, v -> {
            set.accept(v);
            Config.save();
        }, label));
    }

    private void cycle(java.util.function.Supplier<String> label, Runnable onClick) {
        int[] p = next();
        addRenderableWidget(Button.builder(Component.literal(label.get()), btn -> {
            onClick.run();
            btn.setMessage(Component.literal(label.get()));
            Config.save();
        }).bounds(p[0], p[1], colW, 20).build());
    }

    @Override
    protected void init() {
        Config c = Config.get();
        pw = Math.min(this.width - 12, 620);
        ph = Math.min(this.height - 10, 330);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(px + pw - 54, py + 6, 48, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Donation FX: " + (c.enabled ? "ON" : "OFF")), b -> {
            c.enabled = !c.enabled;
            b.setMessage(Component.literal("Donation FX: " + (c.enabled ? "ON" : "OFF")));
            if (!c.enabled) Fx.stop();
            Config.save();
        }).bounds(px + pw - 54 - 4 - 120, py + 6, 120, 18).build());

        gx0 = px + 10;
        gy0 = py + 40;
        rowH = 22;
        int gcols = pw >= 520 ? 4 : 3;
        colW = (pw - 20 - (gcols - 1) * 4) / gcols;
        int bottomButtons = py + ph - 26;
        int avail = bottomButtons - 6 - gy0;
        rowsPerCol = Math.max(6, avail / rowH);
        ctrl = 0;

        // sound
        toggle("Sounds", () -> c.sounds, v -> c.sounds = v);
        slider(0, 1, 0.05, c.volume, v -> c.volume = (float) v, v -> "Volume: " + Math.round(v * 100) + "%");
        toggle("Music", () -> c.themeSounds, v -> c.themeSounds = v);
        slider(0, 10, 1, c.minLevelForSound, v -> c.minLevelForSound = (int) v, v -> v < 1 ? "Sound: all levels" : "Sound: Lv " + (int) v + "+");
        // visual strength
        toggle("Flashes", () -> c.flashes, v -> c.flashes = v);
        slider(0, 1, 0.05, c.flashAmount, v -> c.flashAmount = v, v -> v < 0.01 ? "Flash: off" : "Flash: " + Math.round(v * 100) + "%");
        toggle("Shake", () -> c.shake, v -> c.shake = v);
        slider(0, 2, 0.1, c.shakeAmount, v -> c.shakeAmount = v, v -> "Shake: " + Math.round(v * 100) + "%");
        slider(0.25, 2, 0.05, c.particleAmount, v -> c.particleAmount = v, v -> "Particles: " + Math.round(v * 100) + "%");
        slider(0.5, 2, 0.05, c.size, v -> c.size = v, v -> "Size: " + Math.round(v * 100) + "%");
        slider(0.25, 2, 0.05, c.speed, v -> c.speed = v, v -> "Length: " + Math.round(v * 100) + "%");
        slider(0.5, 2, 0.05, c.titleScale, v -> c.titleScale = v, v -> "Title size: " + Math.round(v * 100) + "%");
        // classic-only extras
        toggle("Fireworks", () -> c.worldEffects, v -> c.worldEffects = v);
        toggle("Item pop-up", () -> c.itemPopup, v -> c.itemPopup = v);
        cycle(() -> "Place: " + (c.position == 0 ? "Middle" : "Top"), () -> c.position = c.position == 0 ? 1 : 0);
        // title text
        toggle("Show title", () -> c.showTitle, v -> c.showTitle = v);
        toggle("Show amount", () -> c.showAmount, v -> c.showAmount = v);
        toggle("Show stars", () -> c.showStars, v -> c.showStars = v);
        toggle("Donor head", () -> c.showPlayerHead, v -> c.showPlayerHead = v);
        toggle("Count up", () -> c.countUp, v -> c.countUp = v);
        toggle("Short $", () -> c.shortAmounts, v -> c.shortAmounts = v);
        toggle("Chat line", () -> c.chatMessage, v -> c.chatMessage = v);
        // behaviour
        toggle("Queue", () -> c.queue, v -> c.queue = v);
        toggle("Combine", () -> c.combine, v -> c.combine = v);
        toggle("Combo counter", () -> c.combo, v -> c.combo = v);
        slider(0, 30, 1, c.cooldownSeconds, v -> c.cooldownSeconds = v, v -> v < 1 ? "Cooldown: off" : "Cooldown: " + (int) v + "s");
        // color
        cycle(() -> "Color: " + colorName(c.fixedColor), () -> c.fixedColor = COLOR_OPTIONS[(colorIndex(c.fixedColor) + 1) % COLOR_OPTIONS.length]);
        toggle("Random color", () -> c.randomColor, v -> c.randomColor = v);
        toggle("Cycle pool", () -> c.cyclePool, v -> c.cyclePool = v);
        // session bar
        toggle("Session bar", () -> c.sessionBar, v -> c.sessionBar = v);
        cycle(() -> "Goal: " + goalName(c.sessionGoal), () -> c.sessionGoal = GOAL_OPTIONS[(goalIndex(c.sessionGoal) + 1) % GOAL_OPTIONS.length]);

        // bottom buttons
        int bw = (pw - 20 - 5 * 4) / 6;
        int bx = px + 10;
        int by = py + ph - 24;
        addRenderableWidget(Button.builder(Component.literal("Levels..."), b -> {
            Config.save();
            Minecraft.getInstance().setScreen(new TiersScreen(this));
        }).bounds(bx, by, bw, 20).build());
        bx += bw + 4;
        addRenderableWidget(Button.builder(Component.literal(Themes.unlocked() ? "Animations..." : "Secret..."), b -> {
            Config.save();
            Minecraft.getInstance().setScreen(new ThemesScreen(this));
        }).bounds(bx, by, bw, 20).build());
        bx += bw + 4;
        addRenderableWidget(Button.builder(Component.literal("Test"), b -> test(-1)).bounds(bx, by, bw, 20).build());
        bx += bw + 4;
        addRenderableWidget(Button.builder(Component.literal("Stop"), b -> Fx.stop()).bounds(bx, by, bw, 20).build());
        bx += bw + 4;
        addRenderableWidget(Button.builder(Component.literal("Folder"), b -> {
            Config.save();
            Util.getPlatform().openPath(FabricLoader.getInstance().getConfigDir());
        }).bounds(bx, by, bw, 20).build());
        bx += bw + 4;
        addRenderableWidget(Button.builder(Component.literal(confirmReset ? "Sure?" : "Reset"), b -> {
            if (!confirmReset) {
                confirmReset = true;
                b.setMessage(Component.literal("Sure?"));
                return;
            }
            confirmReset = false;
            Config.reset();
            Detector.clearCache();
            rebuild();
        }).bounds(bx, by, bw, 20).build());
    }

    private static int colorIndex(String v) {
        for (int i = 0; i < COLOR_OPTIONS.length; i++) if (COLOR_OPTIONS[i].equalsIgnoreCase(v == null ? "" : v)) return i;
        return 0;
    }

    private static String colorName(String v) {
        return COLOR_NAMES[colorIndex(v)];
    }

    private static int goalIndex(double v) {
        for (int i = 0; i < GOAL_OPTIONS.length; i++) if (Math.abs(GOAL_OPTIONS[i] - v) < 1) return i;
        return 0;
    }

    private static String goalName(double v) {
        return v < 1 ? "off" : Money.compact(v);
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private void test(int level) {
        Config c = Config.get();
        Config.save();
        int l = level < 0 ? (int) (Math.random() * c.tiers.size()) : level;
        Config.Tier t = c.tiers.get(l);
        Minecraft mc = Minecraft.getInstance();
        String me = mc.player == null ? "You" : mc.player.getGameProfile().name();
        double amount = t.minAmount * (level < 0 ? 1 + Math.random() * 0.9 : 1);
        onClose();
        Fx.donate(amount, me, l, true);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, 0x90000000);
        g.fill(px, py, px + pw, py + ph, BG);
        g.fill(px, py, px + pw, py + 2, GOLD);
        g.fill(px, py + ph - 1, px + pw, py + ph, LINE);
        g.fill(px, py, px + 1, py + ph, LINE);
        g.fill(px + pw - 1, py, px + pw, py + ph, LINE);
        g.fill(px + 8, py + 28, px + pw - 8, py + 29, LINE);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawString(this.font, "DONATION FX  -  SETTINGS", px + 10, py + 9, GOLD, true);
        if (Fx.playing() || Fx.queued() > 0) {
            String q = "Playing" + (Fx.queued() > 0 ? " +" + Fx.queued() : "");
            g.drawString(this.font, q, px + 10 + this.font.width("DONATION FX  -  SETTINGS") + 12, py + 9, GREEN, false);
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
