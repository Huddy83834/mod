package com.punishfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The end-of-stream recap: how much you made, who gave it, when it came in and what it did to you.
 * Laid out on a fixed 480x270 canvas and scaled to fit, so it looks the same on any window size.
 */
public class RecapScreen extends Screen {
    private static final int VW = 480, VH = 270;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final Screen parent;
    private int index; // into StreamSession.history()
    private StreamSession.Record r;
    private long openedAt;
    private float scale;
    private int ox, oy;

    /** Shows the most recent stream. */
    public RecapScreen(Screen parent) {
        this(parent, StreamSession.history().size() - 1);
    }

    public RecapScreen(Screen parent, int index) {
        super(Component.literal("Stream recap"));
        this.parent = parent;
        show(index);
    }

    private void show(int i) {
        List<StreamSession.Record> h = StreamSession.history();
        index = Math.max(0, Math.min(h.size() - 1, i));
        r = h.isEmpty() ? null : h.get(index);
        openedAt = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        scale = Math.min(this.width / (float) VW, this.height / (float) VH);
        ox = Math.round((this.width - VW * scale) / 2f);
        oy = Math.round((this.height - VH * scale) / 2f);
    }

    // ---------------------------------------------------------------- formatting

    static String length(long ms) {
        long m = ms / 60_000;
        if (m >= 60) return (m / 60) + "h " + String.format("%02d", m % 60) + "m";
        return m + "m " + String.format("%02d", ms / 1000 % 60) + "s";
    }

    private static String at(long t) {
        return TIME.format(Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()));
    }

    private static float ease(float t) {
        t = Math.max(0, Math.min(1, t));
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    // ---------------------------------------------------------------- buttons (canvas coordinates)

    private record Btn(int x, int y, int w, int h, String label, boolean on) {}

    private Btn older() {
        return new Btn(12, 248, 62, 16, "< Older", index > 0);
    }

    private Btn newer() {
        return new Btn(78, 248, 62, 16, "Newer >", index < StreamSession.history().size() - 1);
    }

    private Btn close() {
        return new Btn(VW - 12 - 70, 248, 70, 16, "Close", true);
    }

    private double vx(double mx) {
        return (mx - ox) / scale;
    }

    private double vy(double my) {
        return (my - oy) / scale;
    }

    private static boolean in(Btn b, double x, double y) {
        return x >= b.x && x < b.x + b.w && y >= b.y && y < b.y + b.h;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double x = vx(e.x()), y = vy(e.y());
        if (e.button() == 0) {
            if (older().on && in(older(), x, y)) {
                show(index - 1);
                return true;
            }
            if (newer().on && in(newer(), x, y)) {
                show(index + 1);
                return true;
            }
            if (in(close(), x, y)) {
                onClose();
                return true;
            }
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == GLFW.GLFW_KEY_LEFT && index > 0) {
            show(index - 1);
            return true;
        }
        if (e.key() == GLFW.GLFW_KEY_RIGHT && index < StreamSession.history().size() - 1) {
            show(index + 1);
            return true;
        }
        return super.keyPressed(e);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fillGradient(0, 0, this.width, this.height, Ui.a(0.92f, 0x0C0810), Ui.a(0.96f, 0x050407));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        long now = System.currentTimeMillis();
        float t = ease((now - openedAt) / 1400f);
        double mx = vx(mouseX), my = vy(mouseY);

        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(ox, oy);
        pose.scale(scale);
        Ui.panel(g, VW, VH, 0, 0, VW, VH, now);

        Ui.glowText(g, this.font, "STREAM", 12, 10, Ui.ACCENT, 1.6f);
        Ui.glowText(g, this.font, "RECAP", 12 + (int) (this.font.width("STREAM ") * 1.6f), 10, 0xFFFFFF, 1.6f);

        if (r == null) {
            String s = "No streams yet. Start one from the Stream page or with /pfx stream start";
            g.drawString(this.font, s, VW / 2 - this.font.width(s) / 2, VH / 2 - 4, Ui.a(1f, Ui.TEXT), false);
            button(g, close(), mx, my, Ui.GOOD);
            pose.popMatrix();
            return;
        }

        String when = DAY.format(Instant.ofEpochMilli(r.start).atZone(ZoneId.systemDefault()))
                + "  ·  " + at(r.start) + " - " + at(r.end > 0 ? r.end : now) + "  ·  " + length(r.duration());
        g.drawString(this.font, when, VW - 12 - this.font.width(when), 14, Ui.a(1f, 0x9A8F94), false);
        g.fill(12, 28, VW - 12, 29, Ui.a(0.35f, Ui.ACCENT));

        List<Map.Entry<String, Double>> donors = r.donors();
        double total = r.total();
        double hours = r.duration() / 3_600_000.0;

        // ---- hero: total made
        Ui.card(g, 12, 34, 190, 72, Ui.tint(0x140C12, 0.08f));
        g.fill(12, 34, 14, 106, Ui.a(1f, Ui.GOOD));
        g.drawString(this.font, "YOU MADE", 20, 40, Ui.a(1f, 0xB88A92), false);
        String big = Money.compact(total * t);
        float bs = Math.min(2.6f, 172f / Math.max(1, this.font.width(big)));
        Ui.glowText(g, this.font, big, 20, 53, Ui.GOOD, bs);
        String sub = r.donations.size() + (r.donations.size() == 1 ? " donation" : " donations")
                + "  ·  " + donors.size() + (donors.size() == 1 ? " donor" : " donors");
        g.drawString(this.font, sub, 20, 82, Ui.a(1f, Ui.TEXT), false);
        String rate = hours > 0.02 ? Money.compact(total / hours) + " an hour" : "";
        g.drawString(this.font, rate, 20, 93, Ui.a(1f, 0x9A8F94), false);

        // ---- stat tiles
        StreamSession.Donation best = r.biggest();
        double[] busy = r.busiest();
        String used = r.mostUsed();
        int pct = (int) Math.round(100.0 * r.punishedMs / r.duration());
        String[][] tiles = {
                {"Biggest gift", best == null ? "-" : Money.compact(best.amount), best == null ? "" : best.name},
                {"Best 5 minutes", busy[1] <= 0 ? "-" : Money.compact(busy[1]), busy[1] <= 0 ? "" : "from " + at((long) busy[0])},
                {"Punished for", length(r.punishedMs), pct + "% of the stream"},
                {"Most used", used == null ? "-" : Punishes.name(used), used == null ? "" : r.punishCount.get(used) + " times"},
                {"Cancelled", String.valueOf(r.cancels), r.cooldownSkips > 0 ? r.cooldownSkips + " on cooldown" : "you stopped them"},
                {"Dodged", r.extremesCancelled + " / " + r.extremesTriggered, r.extremesTriggered == 0 ? "no extremes" : "extremes cancelled"},
        };
        int tw = 84, th = 34;
        for (int i = 0; i < tiles.length; i++) {
            int x = 210 + (i % 3) * (tw + 3), y = 34 + (i / 3) * (th + 4);
            Ui.card(g, x, y, tw, th, Ui.tint(0x140C12, 0.08f));
            g.fill(x, y, x + tw, y + 1, Ui.a(0.8f, i < 2 ? Ui.GOOD : i < 4 ? Ui.ACCENT2 : Ui.ACCENT));
            g.drawString(this.font, Ui.fit(this.font, tiles[i][0], tw - 8), x + 4, y + 4, Ui.a(1f, 0x9A8F94), false);
            g.drawString(this.font, Ui.fit(this.font, tiles[i][1], tw - 8), x + 4, y + 14, Ui.a(1f, 0xFFFFFF), false);
            pose.pushMatrix();
            pose.translate(x + 4, y + 25);
            pose.scale(0.75f);
            g.drawString(this.font, Ui.fit(this.font, tiles[i][2], (int) ((tw - 8) / 0.75f)), 0, 0, Ui.a(1f, 0x8C7E82), false);
            pose.popMatrix();
        }

        // ---- top donors
        int dx = 12, dy = 112, dw = 222, dh = 88;
        Ui.card(g, dx, dy, dw, dh, Ui.tint(0x140C12, 0.08f));
        Ui.section(g, this.font, "Top donors", dx + 6, dy + 5, dw - 12);
        if (donors.isEmpty()) {
            g.drawString(this.font, "Nobody donated this stream", dx + 6, dy + 40, Ui.a(1f, 0x8C7E82), false);
        } else {
            double max = donors.get(0).getValue();
            int[] medal = {0xFFC83D, 0xC9D1D9, 0xD08A4E, Ui.ACCENT2, Ui.ACCENT2};
            for (int i = 0; i < Math.min(5, donors.size()); i++) {
                var d = donors.get(i);
                int y = dy + 18 + i * 13;
                String amt = Money.compact(d.getValue());
                int barX = dx + 94, barW = dw - 94 - 8 - this.font.width(amt) - 6;
                g.drawString(this.font, (i + 1) + ".", dx + 6, y + 2, Ui.a(1f, medal[i]), false);
                Ui.text(g, this.font, d.getKey(), dx + 18, y + 2, 0xFFFFFF, 74);
                g.fill(barX, y + 1, barX + barW, y + 10, Ui.a(0.18f, 0xFFFFFF));
                int fill = (int) Math.max(2, barW * (d.getValue() / max) * t);
                g.fill(barX, y + 1, barX + fill, y + 10, Ui.a(0.9f, medal[i]));
                g.drawString(this.font, amt, dx + dw - 8 - this.font.width(amt), y + 2, Ui.a(1f, Ui.TEXT), false);
            }
        }

        // ---- money over time
        int gx = 240, gy = 112, gw = 228, gh = 88;
        Ui.card(g, gx, gy, gw, gh, Ui.tint(0x140C12, 0.08f));
        Ui.section(g, this.font, "Money over time", gx + 6, gy + 5, gw - 12);
        chart(g, gx + 8, gy + 18, gw - 16, gh - 30, total, t);
        g.drawString(this.font, at(r.start), gx + 8, gy + gh - 10, Ui.a(1f, 0x6E6064), false);
        String endS = at(r.end > 0 ? r.end : now);
        g.drawString(this.font, endS, gx + gw - 8 - this.font.width(endS), gy + gh - 10, Ui.a(1f, 0x6E6064), false);

        // ---- awards
        String first = r.donations.isEmpty() ? null : r.donations.get(0).name;
        String last = r.donations.isEmpty() ? null : r.donations.get(r.donations.size() - 1).name;
        String chaos = r.chaosAgent();
        long chaosMs = chaos == null ? 0 : r.donorChaosMs.getOrDefault(chaos, 0L);
        String[][] awards = {
                {"MVP", donors.isEmpty() ? null : donors.get(0).getKey(), donors.isEmpty() ? "" : Money.compact(donors.get(0).getValue()) + " total"},
                {"BIG SPENDER", best == null ? null : best.name, best == null ? "" : Money.compact(best.amount) + " at once"},
                {"FIRST BLOOD", first, first == null ? "" : "first at " + at(r.donations.get(0).t)},
                {"LAST CALL", last, last == null ? "" : "last at " + at(r.donations.get(r.donations.size() - 1).t)},
                {"CHAOS AGENT", chaos, chaos == null ? "" : length(chaosMs) + " of chaos"},
        };
        int[] ac = {0xFFC83D, Ui.GOOD, 0xFF5A5A, 0x6FA8FF, 0xC77DFF};
        int aw = (VW - 24 - 4 * 4) / 5;
        for (int i = 0; i < awards.length; i++) {
            int x = 12 + i * (aw + 4), y = 206;
            float appear = ease((now - openedAt - 500 - i * 120) / 400f);
            if (appear <= 0) continue;
            Ui.card(g, x, y, aw, 36, Ui.tint(0x140C12, 0.08f));
            g.fill(x, y, x + aw, y + 2, Ui.a(appear, ac[i]));
            g.drawString(this.font, awards[i][0], x + 5, y + 5, Ui.a(appear, ac[i]), false);
            String who = awards[i][1] == null ? "-" : awards[i][1];
            g.drawString(this.font, Ui.fit(this.font, who, aw - 10), x + 5, y + 15, Ui.a(appear, 0xFFFFFF), false);
            pose.pushMatrix();
            pose.translate(x + 5, y + 26);
            pose.scale(0.75f);
            g.drawString(this.font, Ui.fit(this.font, awards[i][2], (int) ((aw - 10) / 0.75f)), 0, 0, Ui.a(appear, 0x8C7E82), false);
            pose.popMatrix();
        }

        // ---- footer
        button(g, older(), mx, my, Ui.ACCENT2);
        button(g, newer(), mx, my, Ui.ACCENT2);
        button(g, close(), mx, my, Ui.GOOD);
        String pos = "Stream " + (index + 1) + " of " + StreamSession.history().size() + "   \u00b7   arrow keys to browse";
        g.drawString(this.font, pos, 148, 252, Ui.a(1f, 0x8C7E82), false);
        pose.popMatrix();
    }

    /** Running total as a filled area, with a dot for each donation. */
    private void chart(GuiGraphics g, int x, int y, int w, int h, double total, float t) {
        g.fill(x, y + h, x + w, y + h + 1, Ui.a(0.3f, 0xFFFFFF));
        for (int i = 1; i <= 3; i++) g.fill(x, y + h - h * i / 4, x + w, y + h - h * i / 4 + 1, Ui.a(0.07f, 0xFFFFFF));
        if (total <= 0) {
            String s = "No money came in";
            g.drawString(this.font, s, x + w / 2 - this.font.width(s) / 2, y + h / 2 - 4, Ui.a(1f, 0x8C7E82), false);
            return;
        }
        long dur = r.duration();
        int shown = (int) (w * t);
        int di = 0;
        double cum = 0;
        int prevTop = y + h;
        for (int px = 0; px < shown; px++) {
            long until = r.start + dur * (px + 1) / w;
            while (di < r.donations.size() && r.donations.get(di).t <= until) cum += r.donations.get(di++).amount;
            int top = y + h - (int) Math.round(h * cum / total);
            if (top < y + h) g.fillGradient(x + px, top, x + px + 1, y + h, Ui.a(0.55f, Ui.GOOD), Ui.a(0.05f, Ui.GOOD));
            int a = Math.min(top, prevTop), b = Math.max(top, prevTop);
            g.fill(x + px, a, x + px + 1, Math.max(b, a + 1) + 1, Ui.a(1f, 0x9BFFC4));
            prevTop = top;
        }
        cum = 0;
        for (StreamSession.Donation d : r.donations) {
            cum += d.amount;
            int px = (int) ((d.t - r.start) * (long) w / dur);
            if (px > shown) break;
            int py = y + h - (int) Math.round(h * cum / total);
            g.fill(x + px - 1, py - 1, x + px + 2, py + 2, Ui.a(1f, 0xFFFFFF));
        }
        String top = Money.compact(total);
        g.drawString(this.font, top, x + 2, y, Ui.a(0.9f, 0x9BFFC4), false);
    }

    private void button(GuiGraphics g, Btn b, double mx, double my, int accent) {
        boolean hov = b.on && in(b, mx, my);
        int base = !b.on ? 0x1A1416 : hov ? Ui.CARD_HI : Ui.CARD;
        Ui.card(g, b.x, b.y, b.w, b.h, base);
        g.fill(b.x, b.y + b.h - 1, b.x + b.w, b.y + b.h, Ui.a(b.on ? 0.9f : 0.2f, accent));
        int col = b.on ? (hov ? 0xFFFFFF : Ui.TEXT) : 0x5E5256;
        g.drawString(this.font, b.label, b.x + b.w / 2 - this.font.width(b.label) / 2, b.y + 4, Ui.a(1f, col), false);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
