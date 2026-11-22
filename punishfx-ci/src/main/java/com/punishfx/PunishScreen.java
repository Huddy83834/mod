package com.punishfx;

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

/** Punish FX global settings, plus buttons into the level and punishment-pool editors. */
public class PunishScreen extends Screen {
    private static final int BG = 0xF0120609;
    private static final int LINE = 0xFF3A2A2E;
    private static final int RED = 0xFFFF5C5C;
    private static final int TEXT = 0xFFE8EAF0;
    private static final int DIM = 0xFF9A8B8F;

    private final Screen parent;
    private int px, py, pw, ph;
    private boolean confirmReset = false;
    private int gx0, gy0, colW, rowH, rowsPerCol, ctrl;

    public PunishScreen(Screen parent) {
        super(Component.literal("Punish FX"));
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
            return Math.round((min + this.value * (max - min)) / step) * step;
        }

        @Override
        protected void updateMessage() {
            if (label != null) setMessage(Component.literal(label.apply(val())));
        }

        @Override
        protected void applyValue() {
            if (set != null) set.accept(val());
            Config.save();
        }
    }

    private int[] next() {
        int col = ctrl / rowsPerCol, row = ctrl % rowsPerCol;
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
        addRenderableWidget(new Slider(p[0], p[1], colW, min, max, step, cur, set, label));
    }

    @Override
    protected void init() {
        Config c = Config.get();
        pw = Math.min(this.width - 12, 560);
        ph = Math.min(this.height - 10, 300);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(px + pw - 54, py + 6, 48, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Punish FX: " + (c.enabled ? "ON" : "OFF")), b -> {
            c.enabled = !c.enabled;
            b.setMessage(Component.literal("Punish FX: " + (c.enabled ? "ON" : "OFF")));
            if (!c.enabled) PunishManager.cancelAll();
            Config.save();
        }).bounds(px + pw - 54 - 4 - 120, py + 6, 120, 18).build());

        gx0 = px + 10;
        gy0 = py + 40;
        rowH = 22;
        int gcols = 3;
        colW = (pw - 20 - (gcols - 1) * 4) / gcols;
        int avail = (py + ph - 30) - gy0;
        rowsPerCol = Math.max(5, avail / rowH);
        ctrl = 0;

        toggle("Announce", () -> c.announce, v -> c.announce = v);
        toggle("Sounds", () -> c.sounds, v -> c.sounds = v);
        toggle("Stack punishments", () -> c.stack, v -> c.stack = v);
        toggle("Combine payments", () -> c.combine, v -> c.combine = v);
        toggle("Confirm extreme", () -> c.confirmExtreme, v -> c.confirmExtreme = v);
        slider(0.25, 3.0, 0.05, c.lengthScale, v -> c.lengthScale = v, v -> "Length: " + Math.round(v * 100) + "%");
        slider(1, 10, 1, c.maxActive, v -> c.maxActive = (int) v, v -> "Max at once: " + (int) v);

        int bw = (pw - 20 - 4 * 4) / 5;
        int bx = px + 10;
        int by = py + ph - 24;
        addRenderableWidget(Button.builder(Component.literal("Levels..."), b -> {
            Config.save();
            Minecraft.getInstance().setScreen(new PunishTiersScreen(this));
        }).bounds(bx, by, bw, 20).build());
        bx += bw + 4;
        addRenderableWidget(Button.builder(Component.literal("Punishments..."), b -> {
            Config.save();
            Minecraft.getInstance().setScreen(new PunishPoolScreen(this));
        }).bounds(bx, by, bw, 20).build());
        bx += bw + 4;
        addRenderableWidget(Button.builder(Component.literal("Test"), b -> {
            Config.save();
            int l = (int) (Math.random() * c.tiers.size());
            Minecraft mc = Minecraft.getInstance();
            onClose();
            PunishManager.hit(c.tiers.get(l).minAmount, mc.player == null ? "You" : mc.player.getGameProfile().name(), l, true);
        }).bounds(bx, by, bw, 20).build());
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
            rebuild();
        }).bounds(bx, by, bw, 20).build());
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
        g.drawString(this.font, "PUNISH FX  -  SETTINGS", px + 10, py + 9, RED, true);
        g.drawString(this.font, "Punishes you when you get a donation. Set levels, durations and which punishments.", px + 10, py + 30 - 2, DIM, false);
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
