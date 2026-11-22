package com.punishfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Add, remove and edit punishment levels: on/off, name, amount, duration, how many at once. */
public class PunishTiersScreen extends Screen {
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
        ph = Math.min(this.height - 10, 290);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        rowTop = py + 44;

        addRenderableWidget(new FancyButton(px + pw - 52, py + 8, 44, 18, Component.literal("Back"), b -> onClose()));

        int onW = 30, nameW = 82, amtW = 60, durW = 48, cntW = 40, testW = 38, remW = 20, gap = 5;
        int lx = px + 12;
        int max = Math.min(c.tiers.size(), (py + ph - 30 - rowTop) / rowH);
        for (int i = 0; i < c.tiers.size() && i < max; i++) {
            final int idx = i;
            Config.Tier t = c.tiers.get(i);
            int y = rowTop + i * rowH;
            int x = lx;
            addRenderableWidget(new FancyButton(x, y, onW, 20, Component.literal("On"), b -> {
                t.enabled = !t.enabled;
                Config.save();
            }).toggle(() -> t.enabled));
            x += onW + gap;

            EditBox name = field(x, y, nameW, "name", t.name);
            name.setResponder(v -> t.name = v.isBlank() ? t.name : v);
            x += nameW + gap;

            EditBox amt = field(x, y, amtW, "amount", Money.compact(t.minAmount));
            amt.setResponder(v -> {
                double d = Money.parse(v);
                amt.setTextColor(d > 0 ? 0xFFFFFFFF : 0xFFFF5555);
                if (d > 0) t.minAmount = d;
            });
            x += amtW + gap;

            EditBox dur = field(x, y, durW, "secs", fmt(t.seconds) + "s");
            dur.setResponder(v -> {
                try {
                    double d = Double.parseDouble(v.replace("s", "").trim());
                    if (d > 0) {
                        t.seconds = Math.min(120, d);
                        dur.setTextColor(0xFFFFFFFF);
                    } else dur.setTextColor(0xFFFF5555);
                } catch (Exception e) {
                    dur.setTextColor(0xFFFF5555);
                }
            });
            x += durW + gap;

            EditBox cnt = field(x, y, cntW, "#", "x" + t.count);
            cnt.setResponder(v -> {
                try {
                    int n = Integer.parseInt(v.replace("x", "").trim());
                    if (n >= 1) {
                        t.count = Math.min(8, n);
                        cnt.setTextColor(0xFFFFFFFF);
                    } else cnt.setTextColor(0xFFFF5555);
                } catch (Exception e) {
                    cnt.setTextColor(0xFFFF5555);
                }
            });
            x += cntW + gap;

            addRenderableWidget(new FancyButton(x, y, testW, 20, Component.literal("Test"), b -> {
                Config.save();
                Minecraft mc = Minecraft.getInstance();
                onClose();
                PunishManager.hit(t.minAmount, mc.player == null ? "You" : mc.player.getGameProfile().name(), idx, true);
            }).accent(Ui.WARN));
            x += testW + gap;

            FancyButton rem = new FancyButton(x, y, remW, 20, Component.literal("✕"), b -> {
                if (c.tiers.size() <= 1) return;
                c.tiers.remove(idx);
                Config.save();
                rebuild();
            });
            rem.accent(Ui.ACCENT).active = c.tiers.size() > 1;
            addRenderableWidget(rem);
        }

        int by = py + ph - 26;
        addRenderableWidget(new FancyButton(px + 12, by, 104, 20, Component.literal("+ Add level"), b -> {
            Config.get().addTier();
            Config.save();
            rebuild();
        }).accent(Ui.GOOD));
        addRenderableWidget(new FancyButton(px + 120, by, 116, 20, Component.literal("Sort by amount"), b -> {
            c.tiers.sort((a, b2) -> Double.compare(a.minAmount, b2.minAmount));
            Config.save();
            rebuild();
        }));
    }

    private EditBox field(int x, int y, int w, String hint, String value) {
        EditBox e = new EditBox(this.font, x, y, w, 20, Component.literal(hint));
        e.setMaxLength(20);
        e.setBordered(false);
        e.setValue(value);
        addRenderableWidget(e);
        return e;
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
        Ui.panel(g, this.width, this.height, px, py, pw, ph, System.currentTimeMillis());
        // field backgrounds behind the (border-less) edit boxes
        for (var w : this.children()) {
            if (w instanceof EditBox e) Ui.card(g, e.getX() - 3, e.getY() - 1, e.getWidth() + 6, 20, Ui.CARD);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        Config c = Config.get();
        Ui.glowText(g, this.font, "LEVELS", px + 12, py + 11, Ui.ACCENT, 1.5f);
        g.drawString(this.font, c.tiers.size() + " levels  ·  on/off · name · amount · seconds · how many at once",
                px + 12 + (int) (this.font.width("LEVELS") * 1.5f) + 10, py + 15, Ui.a(1f, Ui.DIM), false);
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
