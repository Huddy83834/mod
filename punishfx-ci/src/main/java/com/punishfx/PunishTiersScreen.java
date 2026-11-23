package com.punishfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Levels page: a table of punishment levels you can edit in place. */
public class PunishTiersScreen extends PfxFrame {
    private static int scroll = 0;
    private int rowH = 22, tableY, visibleRows;
    private int colOn, colName, colAmt, colSec, colCnt, colTest, colDel;
    private int wOn = 30, wName, wAmt = 62, wSec = 44, wCnt = 38, wTest = 34, wDel = 18;

    public PunishTiersScreen(Screen parent) {
        super(parent, Page.LEVELS, "Levels", "a donation uses the highest level it reaches");
    }

    @Override
    protected void initPage() {
        Config c = Config.get();
        int gap = 4;
        wName = Math.max(50, Math.min(150, cw - (wOn + wAmt + wSec + wCnt + wTest + wDel + gap * 6)));
        colOn = cx;
        colName = colOn + wOn + gap;
        colAmt = colName + wName + gap;
        colSec = colAmt + wAmt + gap;
        colCnt = colSec + wSec + gap;
        colTest = colCnt + wCnt + gap;
        colDel = colTest + wTest + gap;

        tableY = cy + 12;
        int bottom = cy + ch - 24;
        visibleRows = Math.max(1, (bottom - tableY) / rowH);
        scroll = Math.max(0, Math.min(scroll, c.tiers.size() - visibleRows));
        boolean inWorld = Minecraft.getInstance().player != null;

        for (int r = 0; r < visibleRows && r + scroll < c.tiers.size(); r++) {
            final int idx = r + scroll;
            Config.Tier t = c.tiers.get(idx);
            int y = tableY + r * rowH;

            addRenderableWidget(new FancyButton(colOn, y, wOn, rowH - 3, Component.literal(""), b -> {
                t.enabled = !t.enabled;
                Config.save();
            }).toggle(() -> t.enabled));

            EditBox name = field(colName, y, wName, "name", t.name);
            name.setResponder(v -> {
                if (!v.isBlank()) t.name = v.trim();
                Config.save();
            });

            EditBox amt = field(colAmt, y, wAmt, "amount", Money.compact(t.minAmount));
            amt.setResponder(v -> {
                double d = Money.parse(v);
                amt.setTextColor(d > 0 ? 0xFFFFFFFF : 0xFFFF6060);
                if (d > 0) {
                    t.minAmount = d;
                    Config.save();
                }
            });

            EditBox sec = field(colSec, y, wSec, "seconds", fmt(t.seconds) + "s");
            sec.setResponder(v -> {
                try {
                    double d = Double.parseDouble(v.replace("s", "").trim());
                    if (d > 0) {
                        t.seconds = Math.min(300, d);
                        sec.setTextColor(0xFFFFFFFF);
                        Config.save();
                        return;
                    }
                } catch (Exception ignored) {
                }
                sec.setTextColor(0xFFFF6060);
            });

            addRenderableWidget(new FancyButton(colCnt, y, wCnt, rowH - 3, Component.literal("x" + t.count), b -> {
                t.count = t.count >= 6 ? 1 : t.count + 1;
                b.setMessage(Component.literal("x" + t.count));
                Config.save();
            }).accent(Ui.ACCENT2));

            FancyButton test = new FancyButton(colTest, y, wTest, rowH - 3, Component.literal("Test"), b -> {
                Config.save();
                Minecraft mc = Minecraft.getInstance();
                mc.setScreen(null);
                PunishManager.hit(t.minAmount, mc.player == null ? "You" : mc.player.getGameProfile().name(), idx, true);
            });
            test.accent(Ui.WARN).active = inWorld;
            addRenderableWidget(test);

            FancyButton del = new FancyButton(colDel, y, wDel, rowH - 3, Component.literal("x"), b -> {
                if (c.tiers.size() <= 1) return;
                c.tiers.remove(idx);
                Config.save();
                rebuild();
            });
            del.accent(Ui.ACCENT).active = c.tiers.size() > 1;
            addRenderableWidget(del);
        }

        int by = cy + ch - 20;
        addRenderableWidget(new FancyButton(cx, by, 96, 20, Component.literal("+ Add level"), b -> {
            c.addTier();
            Config.save();
            scroll = Math.max(0, c.tiers.size() - visibleRows);
            rebuild();
        }).accent(Ui.GOOD));
        addRenderableWidget(new FancyButton(cx + 100, by, 104, 20, Component.literal("Sort by amount"), b -> {
            c.tiers.sort((a, b2) -> Double.compare(a.minAmount, b2.minAmount));
            Config.save();
            rebuild();
        }));
    }

    private EditBox field(int x, int y, int w, String hint, String value) {
        EditBox e = new EditBox(this.font, x + 4, y + 5, w - 6, rowH - 8, Component.literal(hint));
        e.setMaxLength(24);
        e.setBordered(false);
        e.setValue(value);
        e.setTextColor(0xFFFFFFFF);
        addRenderableWidget(e);
        return e;
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((int) d) : String.valueOf(d);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, Config.get().tiers.size() - visibleRows);
        int ns = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
        if (ns != scroll) {
            scroll = ns;
            rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void renderPageBackground(GuiGraphics g, int mouseX, int mouseY) {
        Config c = Config.get();
        for (int r = 0; r < visibleRows && r + scroll < c.tiers.size(); r++) {
            int y = tableY + r * rowH;
            boolean on = c.tiers.get(r + scroll).enabled;
            // a faint stripe behind the whole row, then a field box behind each text box
            g.fill(cx - 2, y - 1, cx + cw + 2, y + rowH - 2, Ui.a(on ? 0.35f : 0.18f, Ui.tint(0x1C1820, 0.08f)));
            Ui.card(g, colName, y, wName, rowH - 3, Ui.tint(0x18141C, 0.08f));
            Ui.card(g, colAmt, y, wAmt, rowH - 3, Ui.tint(0x18141C, 0.08f));
            Ui.card(g, colSec, y, wSec, rowH - 3, Ui.tint(0x18141C, 0.08f));
        }
    }

    @Override
    protected void renderPage(GuiGraphics g, int mouseX, int mouseY) {
        Config c = Config.get();
        int hy = cy + 1, col = 0x9A7A82;
        g.drawString(this.font, "ON", colOn + 7, hy, Ui.a(1f, col), false);
        g.drawString(this.font, "NAME", colName + 4, hy, Ui.a(1f, col), false);
        g.drawString(this.font, "FROM", colAmt + 4, hy, Ui.a(1f, col), false);
        g.drawString(this.font, "LASTS", colSec + 4, hy, Ui.a(1f, col), false);
        g.drawString(this.font, "AT ONCE", colCnt - 2, hy, Ui.a(1f, col), false);

        int total = c.tiers.size();
        if (total > visibleRows) {
            String s = (scroll + 1) + "-" + Math.min(total, scroll + visibleRows) + " of " + total + " (scroll)";
            g.drawString(this.font, s, cx + cw - this.font.width(s), cy + ch - 14, Ui.a(1f, 0x8C7E82), false);
        }

        footer = null;
        if (mouseX >= colCnt && mouseX < colCnt + wCnt && mouseY >= tableY) footer = "How many different punishments this level runs at the same time. Click to change.";
        else if (mouseX >= colAmt && mouseX < colAmt + wAmt && mouseY >= tableY) footer = "Smallest donation for this level. Type 50k, 1.5m, $2,000,000...";
        else if (mouseX >= colSec && mouseX < colSec + wSec && mouseY >= tableY) footer = "How many seconds each punishment lasts at this level.";
        else if (mouseX >= colTest && mouseX < colTest + wTest && mouseY >= tableY)
            footer = Minecraft.getInstance().player == null ? "Join a world to test a level." : "Fire this level's punishments on yourself now.";
    }
}
