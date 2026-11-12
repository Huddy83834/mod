package com.donationfx;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/** Donation FX settings: switches, sliders, and the 5 levels (amount, title, on/off, test). */
public class SettingsScreen extends Screen {
    private static final int BG = 0xF00B0D13;
    private static final int LINE = 0xFF2A3142;
    private static final int GOLD = 0xFFF5B83D;
    private static final int TEXT = 0xFFE8EAF0;
    private static final int DIM = 0xFF8B93A7;
    private static final int GREEN = 0xFF3DDC84;
    private static final int RED = 0xFFFF5C5C;

    private final Screen parent;
    private int px, py, pw, ph;
    private int levelsX, levelsY, levelsW;
    private int detectY;
    private boolean confirmReset = false;
    private Button resetButton;
    private final List<EditBox> minBoxes = new ArrayList<>();

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

    private Button toggle(int x, int y, int w, String name, BooleanSupplier get, Consumer<Boolean> set) {
        Button b = Button.builder(Component.literal(name + ": " + (get.getAsBoolean() ? "ON" : "OFF")), btn -> {
            set.accept(!get.getAsBoolean());
            btn.setMessage(Component.literal(name + ": " + (get.getAsBoolean() ? "ON" : "OFF")));
            Config.save();
        }).bounds(x, y, w, 20).build();
        return addRenderableWidget(b);
    }

    @Override
    protected void init() {
        Config c = Config.get();
        minBoxes.clear();
        pw = Math.min(this.width - 12, 600);
        ph = Math.min(this.height - 12, 320);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;

        // header
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(px + pw - 56, py + 6, 50, 20).build());
        toggle(px + pw - 56 - 6 - 110, py + 6, 110, "Donation FX", () -> Config.get().enabled, v -> Config.get().enabled = v);

        // effects: two columns of switches and sliders
        int top = py + 46;
        int colW = Math.max(100, Math.min(130, (pw - 30) / 4));
        int x1 = px + 10;
        int x2 = x1 + colW + 4;
        int row = 22;
        int y = top;
        toggle(x1, y, colW, "Sounds", () -> c.sounds, v -> c.sounds = v);
        y += row;
        addRenderableWidget(new Slider(x1, y, colW, 0, 1, 0.05, c.volume, v -> c.volume = (float) v, v -> "Volume: " + Math.round(v * 100) + "%"));
        y += row;
        toggle(x1, y, colW, "Flashes", () -> c.flashes, v -> c.flashes = v);
        y += row;
        toggle(x1, y, colW, "Shake", () -> c.shake, v -> c.shake = v);
        y += row;
        toggle(x1, y, colW, "Fireworks", () -> c.worldEffects, v -> c.worldEffects = v);
        y += row;
        toggle(x1, y, colW, "Item pop-up", () -> c.itemPopup, v -> c.itemPopup = v);
        y += row;
        toggle(x1, y, colW, "Chat line", () -> c.chatMessage, v -> c.chatMessage = v);

        y = top;
        toggle(x2, y, colW, "Count up", () -> c.countUp, v -> c.countUp = v);
        y += row;
        toggle(x2, y, colW, "Short $", () -> c.shortAmounts, v -> c.shortAmounts = v);
        y += row;
        toggle(x2, y, colW, "Head", () -> c.showPlayerHead, v -> c.showPlayerHead = v);
        y += row;
        toggle(x2, y, colW, "Queue", () -> c.queue, v -> c.queue = v);
        y += row;
        addRenderableWidget(new Slider(x2, y, colW, 0.5, 2.0, 0.05, c.size, v -> c.size = v, v -> "Size: " + Math.round(v * 100) + "%"));
        y += row;
        addRenderableWidget(new Slider(x2, y, colW, 0.25, 2.0, 0.05, c.speed, v -> c.speed = v, v -> "Length: " + Math.round(v * 100) + "%"));
        y += row;
        addRenderableWidget(Button.builder(Component.literal(posLabel()), b -> {
            c.position = c.position == 0 ? 1 : 0;
            b.setMessage(Component.literal(posLabel()));
            Config.save();
        }).bounds(x2, y, colW, 20).build());

        // the 5 levels
        levelsX = x2 + colW + 14;
        levelsY = top;
        levelsW = px + pw - 10 - levelsX;
        boolean titles = levelsW >= 210;
        for (int i = 0; i < c.tiers.size(); i++) {
            final int idx = i;
            Config.Tier t = c.tiers.get(i);
            int ly = levelsY + i * 26;
            int lx = levelsX + 16;
            addRenderableWidget(Button.builder(Component.literal(t.enabled ? "ON" : "OFF"), b -> {
                t.enabled = !t.enabled;
                b.setMessage(Component.literal(t.enabled ? "ON" : "OFF"));
                Config.save();
            }).bounds(lx, ly, 32, 20).build());
            lx += 34;
            addRenderableWidget(Button.builder(Component.literal("Test"), b -> test(idx)).bounds(lx, ly, 36, 20).build());
            lx += 38;
            EditBox min = new EditBox(this.font, lx, ly, 60, 20, Component.literal("Amount"));
            min.setMaxLength(16);
            min.setValue(Money.compact(t.minAmount));
            min.setResponder(v -> {
                double d = Money.parse(v);
                if (d > 0) {
                    t.minAmount = d;
                    min.setTextColor(0xFFFFFFFF);
                } else {
                    min.setTextColor(0xFFFF5555);
                }
            });
            addRenderableWidget(min);
            minBoxes.add(min);
            lx += 64;
            if (titles) {
                EditBox title = new EditBox(this.font, lx, ly, px + pw - 10 - lx, 20, Component.literal("Title"));
                title.setMaxLength(48);
                title.setValue(t.title);
                title.setResponder(v -> t.title = v.isBlank() ? t.title : v);
                addRenderableWidget(title);
            }
        }

        // bottom buttons
        int by = py + ph - 28;
        int bw = (pw - 20 - 5 * 4) / 6;
        int bx = px + 10;
        addRenderableWidget(Button.builder(Component.literal(Themes.unlocked() ? "Animations..." : "Secret..."), b -> {
            Config.save();
            Minecraft.getInstance().setScreen(new ThemesScreen(this));
        }).bounds(bx, by, bw, 20).build());
        bx += bw + 4;
        addRenderableWidget(Button.builder(Component.literal("Test random"), b -> test(-1)).bounds(bx, by, bw, 20).build());
        bx += bw + 4;
        addRenderableWidget(Button.builder(Component.literal("Stop"), b -> Fx.stop()).bounds(bx, by, bw, 20).build());
        bx += bw + 4;
        addRenderableWidget(Button.builder(Component.literal("Folder"), b -> {
            Config.save();
            Util.getPlatform().openPath(FabricLoader.getInstance().getConfigDir());
        }).bounds(bx, by, bw, 20).build());
        bx += bw + 4;
        addRenderableWidget(Button.builder(Component.literal("Reload"), b -> {
            Config.load();
            Detector.clearCache();
            rebuild();
        }).bounds(bx, by, bw, 20).build());
        bx += bw + 4;
        resetButton = addRenderableWidget(Button.builder(Component.literal("Reset all"), b -> {
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
        detectY = Math.max(levelsY + 5 * 26 + 6, top + 7 * row + 4);
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private static String posLabel() {
        return "Place: " + (Config.get().position == 0 ? "Middle" : "Top");
    }

    private void test(int level) {
        Config c = Config.get();
        Config.save();
        int l = level < 0 ? (int) (Math.random() * c.tiers.size()) : level;
        Config.Tier t = c.tiers.get(l);
        Minecraft mc = Minecraft.getInstance();
        String me = mc.player == null ? "You" : mc.player.getGameProfile().name();
        double amount = t.minAmount * (level < 0 ? 1 + Math.random() * 0.9 : 1);
        onClose(); // close so the animation can be seen
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
        g.fill(px + 8, py + 30, px + pw - 8, py + 31, LINE);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        Config c = Config.get();
        g.drawString(this.font, "DONATION FX", px + 10, py + 9, GOLD, true);
        String stats = "This session: " + Fx.sessionCount + " donation" + (Fx.sessionCount == 1 ? "" : "s") + ", " + Money.full(Fx.sessionTotal);
        int statsRight = px + pw - 56 - 6 - 110 - 8;
        if (this.font.width(stats) < statsRight - (px + 90)) g.drawString(this.font, stats, statsRight - this.font.width(stats), py + 12, DIM, false);
        g.drawString(this.font, "Plays when someone pays you. Only you see it.", px + 10, py + 19, DIM, false);

        g.drawString(this.font, "EFFECTS", px + 10, levelsY - 11, DIM, false);
        g.drawString(this.font, "LEVELS  (on/off, test, smallest amount" + (levelsW >= 210 ? ", title)" : ")"), levelsX, levelsY - 11, DIM, false);
        for (int i = 0; i < c.tiers.size(); i++) {
            Config.Tier t = c.tiers.get(i);
            int ly = levelsY + i * 26;
            int col = "rainbow".equalsIgnoreCase(t.color) ? Fx.hsv((float) ((System.currentTimeMillis() % 3000) / 3000.0), 0.8f, 1f) : Fx.parseColor(t.color, 0xFFFFFF);
            g.fill(levelsX, ly + 2, levelsX + 12, ly + 18, 0xFF000000 | col);
            g.drawString(this.font, String.valueOf(i + 1), levelsX + 4, ly + 6, 0xFF000000, false);
        }
        boolean ordered = true;
        for (int i = 1; i < c.tiers.size(); i++) if (c.tiers.get(i).minAmount <= c.tiers.get(i - 1).minAmount) ordered = false;
        int ty = detectY;
        if (!ordered) {
            g.drawString(this.font, "Each level's amount should be bigger than the one before.", levelsX, levelsY + 5 * 26, RED, false);
        }

        // detection help
        int maxW = pw - 20;
        g.drawString(this.font, "LAST MONEY MESSAGE SEEN", px + 10, ty, DIM, false);
        ty += 11;
        if (Detector.lastMoneyLine.isEmpty()) {
            g.drawString(this.font, trim("None yet. When someone pays you, it shows here. Try /donationfx simulate <message>.", maxW), px + 10, ty, TEXT, false);
        } else {
            g.drawString(this.font, trim(Detector.lastMoneyLine, maxW), px + 10, ty, TEXT, false);
            ty += 10;
            boolean ok = Detector.lastMoneyVerdict.startsWith("LEVEL");
            g.drawString(this.font, trim("-> " + Detector.lastMoneyVerdict, maxW), px + 10, ty, ok ? GREEN : RED, false);
        }
        if (Fx.playing() || Fx.queued() > 0) {
            String q = "Playing now" + (Fx.queued() > 0 ? ", " + Fx.queued() + " waiting" : "");
            g.drawString(this.font, q, px + pw - 10 - this.font.width(q), py + ph - 40, GOLD, false);
        }
    }

    private String trim(String s, int w) {
        if (this.font.width(s) <= w) return s;
        return this.font.plainSubstrByWidth(s, w - this.font.width("...")) + "...";
    }

    @Override
    public void removed() {
        // sort out the amounts and save
        Config.save();
        super.removed();
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
