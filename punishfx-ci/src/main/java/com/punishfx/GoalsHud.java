package com.punishfx;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** The goal bar (top/bottom: across; sides: up), and the "GOAL REACHED!" celebration. */
public final class GoalsHud {
    private static final int HW = 320, HH = 44; // across
    private static final int VW = 124, VH = 180; // up the side

    private GoalsHud() {}

    private static boolean vertical() {
        return Config.get().goalPos >= 2;
    }

    public static final HudPiece PIECE = new HudPiece() {
        public String name() { return "goal bar"; }
        public int[] size(Font font, boolean sample) { return vertical() ? new int[]{VW, VH} : new int[]{HW, HH}; }
        public void drawAt(GuiGraphics g, Font font, float x, float y, float scale, boolean sample) {
            GoalsHud.drawAt(g, font, x, y, scale, sample);
        }
        public double x() { return Config.get().goalX; }
        public double y() { return Config.get().goalY; }
        public double scale() { return Config.get().goalScale; }
        public void set(double x, double y, double s) {
            Config c = Config.get();
            c.goalX = x;
            c.goalY = y;
            c.goalScale = s;
        }
        public boolean shown() { return Config.get().goalShow; }
        public void setShown(boolean on) { Config.get().goalShow = on; }
        public java.util.List<HudPiece.Item> menu() {
            Config c = Config.get();
            return java.util.List.of(
                    new HudPiece.Item(() -> "Where:  " + Goals.POS_NAMES[c.goalPos], () -> place((c.goalPos + 1) % 4)),
                    new HudPiece.Item(() -> "Progress:  " + Money.compact(Goals.progress()) + "  (click to reset)", Goals::reset));
        }
        public String showSettingName() { return "Show the goal bar"; }
        public void reset() { place(Config.get().goalPos); }
    };

    /** Put the bar on a side of the screen (Top / Bottom / Left side / Right side). */
    public static void place(int pos) {
        Config c = Config.get();
        c.goalPos = pos;
        double[][] spots = {{0.5, 0.0}, {0.5, 0.82}, {0.0, 0.5}, {1.0, 0.5}};
        c.goalX = spots[pos][0];
        c.goalY = spots[pos][1];
        c.goalScale = 1.0;
    }

    public static void render(GuiGraphics g, Font font, int sw, int sh) {
        Config c = Config.get();
        if (c.goalShow && !Goals.sorted().isEmpty() && !MoveScreen.editing(PIECE)) {
            int[] b = PIECE.bounds(font, sw, sh, false);
            drawAt(g, font, b[0], b[1], (float) PIECE.scale(), false);
        }
        celebration(g, font, sw, sh);
    }

    private record Mark(double amount, String text, boolean done) {}

    private static List<Mark> marks(boolean sample) {
        List<Mark> out = new ArrayList<>();
        List<Config.Goal> goals = Goals.sorted();
        if (goals.isEmpty() && sample) {
            out.add(new Mark(1_000_000, "Shoutouts", true));
            out.add(new Mark(5_000_000, "Spin the wheel", false));
            out.add(new Mark(10_000_000, "I jump in lava", false));
            return out;
        }
        for (Config.Goal gl : goals) out.add(new Mark(gl.amount, gl.text == null ? "" : gl.text, Goals.done(gl)));
        return out;
    }

    static void drawAt(GuiGraphics g, Font font, float x, float y, float scale, boolean sample) {
        Config c = Config.get();
        List<Mark> marks = marks(sample);
        double max = marks.isEmpty() ? 1 : marks.get(marks.size() - 1).amount;
        double prog = Goals.sorted().isEmpty() && sample ? 3_200_000 : Goals.progress();
        long now = System.currentTimeMillis();
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale);
        String title = c.goalTitle == null || c.goalTitle.isBlank() ? "GOALS" : c.goalTitle;
        String amt = Money.compact(prog) + " / " + Money.compact(max);
        if (!vertical()) across(g, font, marks, max, prog, title, amt, now);
        else up(g, font, marks, max, prog, title, amt, now, c.goalPos == 3);
        pose.popMatrix();
    }

    private static int fillColor(long now) {
        return Ui.mix(Ui.GOOD, 0xFFFFFF, 0.12f + 0.12f * (float) Math.sin(now / 400.0));
    }

    private static void across(GuiGraphics g, Font font, List<Mark> marks, double max, double prog, String title, String amt, long now) {
        int w = HW, barY = 12, barH = 8;
        g.fill(0, 0, w, HH, Ui.a(0.55f, 0x0C0A0E));
        g.drawString(font, Ui.fit(font, title, w - font.width(amt) - 12), 4, 2, Ui.a(1f, Ui.ACCENT), true);
        g.drawString(font, amt, w - 4 - font.width(amt), 2, Ui.a(1f, 0x7CFFA0), true);
        int bx0 = 4, bx1 = w - 4, bw = bx1 - bx0;
        g.fill(bx0 - 1, barY - 1, bx1 + 1, barY + barH + 1, Ui.a(0.9f, 0x000000));
        g.fill(bx0, barY, bx1, barY + barH, Ui.a(1f, 0x22202A));
        int fw = (int) Math.round(bw * Math.min(1, prog / max));
        if (fw > 0) {
            g.fill(bx0, barY, bx0 + fw, barY + barH, Ui.a(1f, fillColor(now)));
            g.fill(bx0, barY, bx0 + fw, barY + 2, Ui.a(0.35f, 0xFFFFFF));
            int shine = (int) ((now / 6) % (bw + 40)) - 20; // a highlight sliding along
            if (shine > 0 && shine < fw) g.fill(bx0 + shine, barY, bx0 + Math.min(fw, shine + 6), barY + barH, Ui.a(0.35f, 0xFFFFFF));
        }
        float ls = 0.75f;
        int row = 0;
        int[] lastEnd = {-999, -999};
        for (Mark m : marks) {
            int mx = bx0 + (int) Math.round(bw * Math.min(1, m.amount / max));
            int col = m.done ? 0xFFD24A : 0xFFFFFF;
            g.fill(mx - 1, barY - 2, mx + 1, barY + barH + 2, Ui.a(1f, col));
            String label = (m.done ? "✔ " : "") + Money.compact(m.amount) + (m.text.isBlank() ? "" : " " + m.text);
            label = Ui.fit(font, label, (int) (84 / ls));
            int tw = (int) (font.width(label) * ls);
            int lx = Math.max(0, Math.min(w - tw, mx - tw / 2));
            // two rows of labels so neighbours don't overlap
            row = lx < lastEnd[row] + 3 ? 1 - row : row;
            if (lx < lastEnd[row] + 3) lx = lastEnd[row] + 3;
            lastEnd[row] = lx + tw;
            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(lx, barY + barH + 4 + row * 8);
            pose.scale(ls);
            g.drawString(font, label, 0, 0, Ui.a(m.done ? 0.9f : 1f, m.done ? 0xFFD24A : 0xE8E2E6), true);
            pose.popMatrix();
        }
    }

    private static void up(GuiGraphics g, Font font, List<Mark> marks, double max, double prog, String title, String amt, long now, boolean right) {
        int w = VW, top = 26, bot = VH - 4, bh = bot - top;
        g.fill(0, 0, w, VH, Ui.a(0.5f, 0x0C0A0E));
        String t = Ui.fit(font, title, w - 8);
        g.drawString(font, t, right ? w - 4 - font.width(t) : 4, 3, Ui.a(1f, Ui.ACCENT), true);
        g.drawString(font, amt, right ? w - 4 - font.width(amt) : 4, 13, Ui.a(1f, 0x7CFFA0), true);
        int bx = right ? w - 12 : 4;
        g.fill(bx - 1, top - 1, bx + 9, bot + 1, Ui.a(0.9f, 0x000000));
        g.fill(bx, top, bx + 8, bot, Ui.a(1f, 0x22202A));
        int fh = (int) Math.round(bh * Math.min(1, prog / max));
        if (fh > 0) {
            g.fill(bx, bot - fh, bx + 8, bot, Ui.a(1f, fillColor(now)));
            g.fill(bx, bot - fh, bx + 2, bot, Ui.a(0.35f, 0xFFFFFF));
        }
        float ls = 0.8f;
        int lastY = 99999;
        for (Mark m : marks) {
            int my = bot - (int) Math.round(bh * Math.min(1, m.amount / max));
            int col = m.done ? 0xFFD24A : 0xFFFFFF;
            g.fill(bx - 2, my - 1, bx + 10, my + 1, Ui.a(1f, col));
            String label = (m.done ? "✔ " : "") + Money.compact(m.amount) + (m.text.isBlank() ? "" : " " + m.text);
            int room = (int) ((w - 18) / ls);
            label = Ui.fit(font, label, room);
            int ly = Math.min(my - 4, lastY - 9); // keep labels from piling on top of each other
            ly = Math.max(top - 2, ly);
            lastY = ly;
            int tw = (int) (font.width(label) * ls);
            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(right ? bx - 4 - tw : bx + 13, ly);
            pose.scale(ls);
            g.drawString(font, label, 0, 0, Ui.a(1f, m.done ? 0xFFD24A : 0xE8E2E6), true);
            pose.popMatrix();
        }
    }

    /** "GOAL REACHED!" in the middle of the screen, with confetti. */
    private static void celebration(GuiGraphics g, Font font, int sw, int sh) {
        Config.Goal goal = Goals.celebrating;
        long now = System.currentTimeMillis();
        if (goal == null || now > Goals.celebrateUntil) return;
        float t = (now - Goals.celebrateAt) / 1000f;
        float vis = Math.min(1f, Math.min(t / 0.25f, (Goals.celebrateUntil - now) / 500f));
        if (vis < 0.05f) return;
        int cy = (int) (sh * 0.32f);
        g.fill(0, cy - 26, sw, cy + 30, Ui.a(0.6f * vis, 0x000000));
        g.fill(0, cy - 26, sw, cy - 25, Ui.a(vis, 0xFFD24A));
        g.fill(0, cy + 29, sw, cy + 30, Ui.a(vis, 0xFFD24A));
        float pop = (float) Math.min(1, t / 0.3) ;
        float s = 2.2f * (0.7f + 0.3f * pop);
        String head = "GOAL REACHED!";
        int hw = (int) (font.width(head) * s);
        Ui.glowText(g, font, head, sw / 2 - hw / 2, cy - 20, Ui.mix(0xFFD24A, 0xFFFFFF, 0.2f + 0.2f * (float) Math.sin(now / 120.0)), s);
        String what = Money.compact(goal.amount) + (goal.text == null || goal.text.isBlank() ? "" : ":  " + goal.text);
        float s2 = Math.min(1.4f, (sw - 20) / (float) Math.max(1, font.width(what)));
        int ww = (int) (font.width(what) * s2);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(sw / 2f - ww / 2f, cy + 10);
        pose.scale(s2);
        g.drawString(font, what, 0, 0, Ui.a(vis, 0xFFFFFF), true);
        pose.popMatrix();
        Random r = new Random((long) goal.amount);
        float ct = t % 2.6f;
        for (int i = 0; i < 90; i++) {
            float vx = (r.nextFloat() - 0.5f) * sw * 0.9f, vy = -40 - r.nextFloat() * 160;
            float px = sw / 2f + vx * ct, py = cy + vy * ct + 150 * ct * ct;
            float sz = 1.5f + r.nextFloat() * 2;
            g.fill(Math.round(px), Math.round(py), Math.round(px + sz), Math.round(py + sz), Ui.a(vis, Ui.hsv(r.nextFloat(), 0.8f, 1f)));
        }
    }
}
