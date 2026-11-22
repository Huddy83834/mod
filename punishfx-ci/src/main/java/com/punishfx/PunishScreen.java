package com.punishfx;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

/** Punish FX global settings, with a custom neon UI. */
public class PunishScreen extends Screen {
    private final Screen parent;
    private int px, py, pw, ph;
    private boolean confirmReset = false;
    private int gx0, gy0, colW, rowH, rowsPerCol, ctrl;

    public PunishScreen(Screen parent) {
        super(Component.literal("Punish FX"));
        this.parent = parent;
    }

    private int[] next() {
        int col = ctrl / rowsPerCol, row = ctrl % rowsPerCol;
        ctrl++;
        return new int[]{gx0 + col * (colW + 5), gy0 + row * rowH};
    }

    private void toggle(String name, BooleanSupplier get, Consumer<Boolean> set) {
        int[] p = next();
        addRenderableWidget(new FancyButton(p[0], p[1], colW, 20, Component.literal(name), btn -> {
            set.accept(!get.getAsBoolean());
            Config.save();
        }).toggle(get));
    }

    private void stepper(double[] vals, DoubleSupplier get, DoubleConsumer set, DoubleFunction<String> label) {
        int[] p = next();
        addRenderableWidget(new FancyButton(p[0], p[1], colW, 18, Component.literal(label.apply(get.getAsDouble())), b -> {
            double cur = get.getAsDouble();
            int idx = 0;
            for (int i = 0; i < vals.length; i++) if (Math.abs(vals[i] - cur) < 1e-6) idx = i;
            double nv = vals[(idx + 1) % vals.length];
            set.accept(nv);
            Config.save();
            b.setMessage(Component.literal(label.apply(nv)));
        }).accent(Ui.ACCENT2));
    }

    @Override
    protected void init() {
        Config c = Config.get();
        pw = Math.min(this.width - 12, 540);
        ph = Math.min(this.height - 10, 290);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;

        addRenderableWidget(new FancyButton(px + pw - 52, py + 8, 44, 18, Component.literal("Done"), b -> onClose()));
        addRenderableWidget(new FancyButton(px + pw - 52 - 6 - 130, py + 7, 130, 20,
                Component.literal("PUNISH FX"), b -> {
            c.enabled = !c.enabled;
            if (!c.enabled) PunishManager.cancelAll();
            Config.save();
        }).toggle(() -> c.enabled));

        gx0 = px + 12;
        gy0 = py + 44;
        rowH = 22;
        int gcols = 3;
        colW = (pw - 24 - (gcols - 1) * 5) / gcols;
        int avail = (py + ph - 32) - gy0;
        rowsPerCol = Math.max(5, avail / rowH);
        ctrl = 0;

        toggle("Announce", () -> c.announce, v -> c.announce = v);
        toggle("Sounds", () -> c.sounds, v -> c.sounds = v);
        toggle("Stack", () -> c.stack, v -> c.stack = v);
        toggle("Combine pays", () -> c.combine, v -> c.combine = v);
        toggle("Confirm extreme", () -> c.confirmExtreme, v -> c.confirmExtreme = v);
        stepper(new double[]{0.5, 0.75, 1.0, 1.5, 2.0, 3.0}, () -> c.lengthScale, v -> c.lengthScale = v, v -> "Length " + Math.round(v * 100) + "%");
        stepper(new double[]{1, 2, 3, 4, 6, 8, 10}, () -> c.maxActive, v -> c.maxActive = (int) v, v -> "Max at once " + (int) v);

        int bw = (pw - 24 - 4 * 5) / 5;
        int bx = px + 12;
        int by = py + ph - 26;
        addRenderableWidget(new FancyButton(bx, by, bw, 20, Component.literal("Levels"), b -> {
            Config.save();
            Minecraft.getInstance().setScreen(new PunishTiersScreen(this));
        }));
        bx += bw + 5;
        addRenderableWidget(new FancyButton(bx, by, bw, 20, Component.literal("Punishments"), b -> {
            Config.save();
            Minecraft.getInstance().setScreen(new PunishPoolScreen(this));
        }).accent(Ui.WARN));
        bx += bw + 5;
        addRenderableWidget(new FancyButton(bx, by, bw, 20, Component.literal("Test me"), b -> {
            Config.save();
            int l = (int) (Math.random() * c.tiers.size());
            Minecraft mc = Minecraft.getInstance();
            onClose();
            PunishManager.hit(c.tiers.get(l).minAmount, mc.player == null ? "You" : mc.player.getGameProfile().name(), l, true);
        }));
        bx += bw + 5;
        addRenderableWidget(new FancyButton(bx, by, bw, 20, Component.literal("Folder"), b -> {
            Config.save();
            Util.getPlatform().openPath(FabricLoader.getInstance().getConfigDir());
        }));
        bx += bw + 5;
        addRenderableWidget(new FancyButton(bx, by, bw, 20, Component.literal(confirmReset ? "Sure?" : "Reset"), b -> {
            if (!confirmReset) {
                confirmReset = true;
                b.setMessage(Component.literal("Sure?"));
                return;
            }
            confirmReset = false;
            Config.reset();
            rebuild();
        }).accent(Ui.ACCENT));
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
        Ui.glowText(g, this.font, "PUNISH FX", px + 12, py + 11, Ui.ACCENT, 1.5f);
        g.drawString(this.font, "it hurts when they pay you", px + 12 + (int) (this.font.width("PUNISH FX") * 1.5f) + 10, py + 15, Ui.a(1f, Ui.DIM), false);
        g.drawString(this.font, "GLOBAL", px + 12, py + 34, Ui.a(1f, Ui.DIM), false);
        g.fill(px + 12 + this.font.width("GLOBAL") + 6, py + 38, px + pw - 12, py + 39, Ui.a(0.4f, Ui.ACCENT));
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
