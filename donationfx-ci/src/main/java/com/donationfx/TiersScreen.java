package com.donationfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Add, remove and edit donation levels: on/off, name, smallest amount, title, color. */
public class TiersScreen extends Screen {
    private static final int BG = 0xF00B0D13;
    private static final int LINE = 0xFF2A3142;
    private static final int GOLD = 0xFFF5B83D;
    private static final int TEXT = 0xFFE8EAF0;
    private static final int DIM = 0xFF8B93A7;
    private static final int RED = 0xFFFF5C5C;

    private static final String[] COLORS = {"rainbow", "#FFFFFF", "#FF5555", "#FFAA00", "#FFD700", "#55FF55", "#55AAFF", "#5555FF", "#FF55FF"};
    private static final String[] COLOR_NAMES = {"Rainbow", "White", "Red", "Orange", "Gold", "Green", "Blue", "Violet", "Pink"};

    private final Screen parent;
    private int px, py, pw, ph, rowTop, rowH = 24;

    public TiersScreen(Screen parent) {
        super(Component.literal("Donation FX levels"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Config c = Config.get();
        pw = Math.min(this.width - 12, 620);
        ph = Math.min(this.height - 10, 330);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        rowTop = py + 42;

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(px + pw - 54, py + 6, 48, 18).build());

        int lx = px + 10;
        int onW = 30, nameW = 58, amtW = 58, colW = 52, testW = 32, remW = 18, gap = 3;
        int titleW = pw - 20 - (onW + nameW + amtW + colW + testW + remW + gap * 6);
        if (titleW < 60) titleW = 60;

        int max = Math.min(c.tiers.size(), (py + ph - 30 - rowTop) / rowH);
        for (int i = 0; i < c.tiers.size() && i < max; i++) {
            final int idx = i;
            Config.Tier t = c.tiers.get(i);
            int y = rowTop + i * rowH;
            int x = lx;
            addRenderableWidget(Button.builder(Component.literal(t.enabled ? "On" : "Off"), b -> {
                t.enabled = !t.enabled;
                b.setMessage(Component.literal(t.enabled ? "On" : "Off"));
                Config.save();
            }).bounds(x, y, onW, 20).build());
            x += onW + gap;

            EditBox name = new EditBox(this.font, x, y, nameW, 20, Component.literal("name"));
            name.setMaxLength(20);
            name.setValue(t.name);
            name.setResponder(v -> t.name = v.isBlank() ? t.name : v);
            addRenderableWidget(name);
            x += nameW + gap;

            EditBox amt = new EditBox(this.font, x, y, amtW, 20, Component.literal("amount"));
            amt.setMaxLength(14);
            amt.setValue(Money.compact(t.minAmount));
            amt.setResponder(v -> {
                double d = Money.parse(v);
                if (d > 0) {
                    t.minAmount = d;
                    amt.setTextColor(0xFFFFFFFF);
                } else {
                    amt.setTextColor(0xFFFF5555);
                }
            });
            addRenderableWidget(amt);
            x += amtW + gap;

            EditBox title = new EditBox(this.font, x, y, titleW, 20, Component.literal("title"));
            title.setMaxLength(48);
            title.setValue(t.title);
            title.setResponder(v -> t.title = v.isBlank() ? t.title : v);
            addRenderableWidget(title);
            x += titleW + gap;

            addRenderableWidget(Button.builder(Component.literal(colorName(t.color)), b -> {
                t.color = COLORS[(colorIndex(t.color) + 1) % COLORS.length];
                b.setMessage(Component.literal(colorName(t.color)));
                Config.save();
            }).bounds(x, y, colW, 20).build());
            x += colW + gap;

            addRenderableWidget(Button.builder(Component.literal("Test"), b -> {
                Config.save();
                Minecraft mc = Minecraft.getInstance();
                String me = mc.player == null ? "You" : mc.player.getGameProfile().name();
                onClose();
                if (mc.screen != null) mc.setScreen(null);
                Fx.donate(t.minAmount, me, idx, true);
            }).bounds(x, y, testW, 20).build());
            x += testW + gap;

            Button rem = Button.builder(Component.literal("✕"), b -> {
                if (c.tiers.size() <= 1) return;
                c.tiers.remove(idx);
                Config.save();
                rebuild();
            }).bounds(x, y, remW, 20).build();
            rem.active = c.tiers.size() > 1;
            addRenderableWidget(rem);
        }

        int by = py + ph - 24;
        addRenderableWidget(Button.builder(Component.literal("+ Add level"), b -> {
            Config.get().addTier();
            Config.save();
            rebuild();
        }).bounds(px + 10, by, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Sort by amount"), b -> {
            c.tiers.sort((a, b2) -> Double.compare(a.minAmount, b2.minAmount));
            Config.save();
            rebuild();
        }).bounds(px + 114, by, 110, 20).build());
    }

    private static int colorIndex(String v) {
        for (int i = 0; i < COLORS.length; i++) if (COLORS[i].equalsIgnoreCase(v == null ? "" : v)) return i;
        return 1;
    }

    private static String colorName(String v) {
        return COLOR_NAMES[colorIndex(v)];
    }

    private void rebuild() {
        clearWidgets();
        init();
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
        Config c = Config.get();
        g.drawString(this.font, "DONATION LEVELS  (" + c.tiers.size() + ")", px + 10, py + 9, GOLD, true);
        g.drawString(this.font, "On/off · name · smallest amount · title · color. Pick animations per level in Animations.", px + 10, py + 19, DIM, false);
        boolean ordered = true;
        for (int i = 1; i < c.tiers.size(); i++) if (c.tiers.get(i).minAmount <= c.tiers.get(i - 1).minAmount) ordered = false;
        if (!ordered) g.drawString(this.font, "Tip: amounts should go up each level – use \"Sort by amount\".", px + 230, py + (ph - 24) + 6, RED, false);
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
