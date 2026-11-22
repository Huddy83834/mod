package com.punishfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Add, remove and edit punishment levels: on/off, name, smallest amount, duration, how many punishments. */
public class PunishTiersScreen extends Screen {
    private static final int BG = 0xF0120609;
    private static final int LINE = 0xFF3A2A2E;
    private static final int RED = 0xFFFF5C5C;
    private static final int DIM = 0xFF9A8B8F;

    private final Screen parent;
    private int px, py, pw, ph, rowTop, rowH = 24;

    public PunishTiersScreen(Screen parent) {
        super(Component.literal("Punish FX levels"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Config c = Config.get();
        pw = Math.min(this.width - 12, 600);
        ph = Math.min(this.height - 10, 300);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        rowTop = py + 42;

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(px + pw - 54, py + 6, 48, 18).build());

        int onW = 30, nameW = 80, amtW = 60, durW = 48, cntW = 42, testW = 32, remW = 18, gap = 4;
        int lx = px + 10;
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
                amt.setTextColor(d > 0 ? 0xFFFFFFFF : 0xFFFF5555);
                if (d > 0) t.minAmount = d;
            });
            addRenderableWidget(amt);
            x += amtW + gap;

            EditBox dur = new EditBox(this.font, x, y, durW, 20, Component.literal("secs"));
            dur.setMaxLength(5);
            dur.setValue(fmt(t.seconds) + "s");
            dur.setResponder(v -> {
                try {
                    double d = Double.parseDouble(v.replace("s", "").trim());
                    if (d > 0) {
                        t.seconds = Math.min(120, d);
                        dur.setTextColor(0xFFFFFFFF);
                    } else {
                        dur.setTextColor(0xFFFF5555);
                    }
                } catch (Exception e) {
                    dur.setTextColor(0xFFFF5555);
                }
            });
            addRenderableWidget(dur);
            x += durW + gap;

            EditBox cnt = new EditBox(this.font, x, y, cntW, 20, Component.literal("#"));
            cnt.setMaxLength(2);
            cnt.setValue("x" + t.count);
            cnt.setResponder(v -> {
                try {
                    int n = Integer.parseInt(v.replace("x", "").trim());
                    if (n >= 1) {
                        t.count = Math.min(8, n);
                        cnt.setTextColor(0xFFFFFFFF);
                    } else {
                        cnt.setTextColor(0xFFFF5555);
                    }
                } catch (Exception e) {
                    cnt.setTextColor(0xFFFF5555);
                }
            });
            addRenderableWidget(cnt);
            x += cntW + gap;

            addRenderableWidget(Button.builder(Component.literal("Test"), b -> {
                Config.save();
                Minecraft mc = Minecraft.getInstance();
                onClose();
                PunishManager.hit(t.minAmount, mc.player == null ? "You" : mc.player.getGameProfile().name(), idx, true);
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

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((int) d) : String.valueOf(d);
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
        Config c = Config.get();
        g.drawString(this.font, "PUNISHMENT LEVELS  (" + c.tiers.size() + ")", px + 10, py + 9, RED, true);
        g.drawString(this.font, "on/off · name · amount · duration · how many at once. Pick which punishments in Punishments...", px + 10, py + 19, DIM, false);
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
