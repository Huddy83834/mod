package com.punishfx;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

/** General settings page: one clear row per setting, with a description and its control on the right. */
public class PunishScreen extends PfxFrame {
    private int rowY, rowH;
    private boolean confirmReset = false;

    /** Stepper rows are drawn by the page (the -/+ buttons are separate widgets). */
    private record StepRow(int y, String label, String desc, DoubleSupplier get, DoubleFunction<String> fmt) {}
    private final List<StepRow> steps = new ArrayList<>();

    public PunishScreen(Screen parent) {
        super(parent, Page.GENERAL, "General", "how punishments are shown and run");
    }

    @Override
    protected void initPage() {
        Config c = Config.get();
        steps.clear();
        rowH = Math.max(20, Math.min(26, (ch - 26) / 7));
        rowY = cy;

        toggle("Show who donated", "Banner with their name and the amount", () -> c.announce, v -> c.announce = v);
        toggle("Sounds", "Play a sound when a punishment starts", () -> c.sounds, v -> c.sounds = v);
        toggle("Stack punishments", "New ones add on instead of replacing", () -> c.stack, v -> c.stack = v);
        toggle("Combine quick payments", "Payments close together count as one", () -> c.combine, v -> c.combine = v);
        toggle("Confirm leave / close", "5 second cancel before Leave Server or Close Minecraft", () -> c.confirmExtreme, v -> c.confirmExtreme = v);
        stepper("Punishment length", "Multiplies every level's duration",
                new double[]{0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 3.0}, () -> c.lengthScale, v -> c.lengthScale = v, v -> Math.round(v * 100) + "%");
        stepper("Max at once", "Most punishments running together",
                new double[]{1, 2, 3, 4, 5, 6, 8, 10}, () -> c.maxActive, v -> c.maxActive = (int) v, v -> String.valueOf((int) v));

        int by = cy + ch - 20;
        addRenderableWidget(new FancyButton(cx, by, 96, 20, Component.literal("Config folder"), b -> {
            Config.save();
            net.minecraft.util.Util.getPlatform().openPath(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir());
        }).accent(Ui.DIM));
        addRenderableWidget(new FancyButton(cx + cw - 110, by, 110, 20, Component.literal(confirmReset ? "Click again to reset" : "Reset everything"), b -> {
            if (!confirmReset) {
                confirmReset = true;
                b.setMessage(Component.literal("Click again to reset"));
                return;
            }
            confirmReset = false;
            Config.reset();
            rebuild();
        }).accent(Ui.ACCENT));
    }

    private void toggle(String label, String desc, BooleanSupplier get, Consumer<Boolean> set) {
        addRenderableWidget(new FancyButton(cx, rowY, cw, rowH - 3, Component.literal(label), b -> {
            set.accept(!get.getAsBoolean());
            Config.save();
        }).toggle(get).desc(desc));
        rowY += rowH;
    }

    private void stepper(String label, String desc, double[] vals, DoubleSupplier get, DoubleConsumer set, DoubleFunction<String> fmt) {
        int y = rowY, h = rowH - 3;
        int bw = 16, bh = 14, by = y + (h - bh) / 2;
        int plusX = cx + cw - 8 - bw, minusX = plusX - 44 - bw;
        addRenderableWidget(new FancyButton(minusX, by, bw, bh, Component.literal("-"), b -> {
            set.accept(step(vals, get.getAsDouble(), -1));
            Config.save();
        }).accent(Ui.ACCENT2));
        addRenderableWidget(new FancyButton(plusX, by, bw, bh, Component.literal("+"), b -> {
            set.accept(step(vals, get.getAsDouble(), 1));
            Config.save();
        }).accent(Ui.ACCENT2));
        steps.add(new StepRow(y, label, desc, get, fmt));
        rowY += rowH;
    }

    private static double step(double[] vals, double cur, int dir) {
        int idx = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < vals.length; i++) {
            double d = Math.abs(vals[i] - cur);
            if (d < best) {
                best = d;
                idx = i;
            }
        }
        return vals[Math.max(0, Math.min(vals.length - 1, idx + dir))];
    }

    @Override
    protected void renderPageBackground(GuiGraphics g, int mouseX, int mouseY) {
        int h = rowH - 3;
        for (StepRow r : steps) {
            boolean hov = mouseX >= cx && mouseX < cx + cw && mouseY >= r.y() && mouseY < r.y() + h;
            Ui.rowCard(g, this.font, cx, r.y(), cw, h, r.label(), r.desc(), hov, Ui.ACCENT2, 90);
        }
    }

    @Override
    protected void renderPage(GuiGraphics g, int mouseX, int mouseY) {
        int h = rowH - 3;
        for (StepRow r : steps) {
            String v = r.fmt().apply(r.get().getAsDouble());
            int plusX = cx + cw - 8 - 16;
            int mid = plusX - 22;
            g.drawString(this.font, v, mid - this.font.width(v) / 2, r.y() + (h - 8) / 2, Ui.a(1f, 0xFFFFFF), false);
        }
    }
}
