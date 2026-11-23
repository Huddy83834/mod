package com.punishfx;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** The box on screen while a giveaway is open: prize, entry price, how many are in, the newest names. */
public final class GiveawayHud {
    private static final int W = 150;

    private GiveawayHud() {}

    private static String[] lines(boolean sample) {
        if (!Giveaway.live() && sample) {
            return new String[]{"Prize: $10M", "Entry: $100K", "12 entered  ·  15 tickets", "Steve, Alex, Notch"};
        }
        int n = Giveaway.count();
        List<String> latest = Giveaway.latest(3);
        String prize = Giveaway.prizeText();
        return new String[]{
                prize.isBlank() ? "Pay to enter!" : "Prize: " + prize,
                "Entry: " + Money.compact(Giveaway.state().price),
                n + " entered" + (Giveaway.tickets() > n ? "  ·  " + Giveaway.tickets() + " tickets" : ""),
                latest.isEmpty() ? "Be the first!" : String.join(", ", latest)};
    }

    public static final HudPiece PIECE = new HudPiece() {
        public String name() { return "giveaway box"; }
        public int[] size(Font font, boolean sample) { return new int[]{W, 16 + 4 * 11 + 4}; }
        public void drawAt(GuiGraphics g, Font font, float x, float y, float scale, boolean sample) {
            GiveawayHud.drawAt(g, font, x, y, scale, sample);
        }
        public double x() { return Config.get().gwX; }
        public double y() { return Config.get().gwY; }
        public double scale() { return Config.get().gwScale; }
        public void set(double x, double y, double s) {
            Config c = Config.get();
            c.gwX = x;
            c.gwY = y;
            c.gwScale = s;
        }
        public boolean shown() { return Config.get().gwHud; }
        public String showSettingName() { return "Show the giveaway box"; }
        public void reset() { set(1.0, 0.72, 1.0); }
    };

    public static void render(GuiGraphics g, Font font, int sw, int sh) {
        if (!Giveaway.live() || Giveaway.drawing() || !Config.get().gwHud || MoveScreen.editing(PIECE)) return;
        int[] b = PIECE.bounds(font, sw, sh, false);
        drawAt(g, font, b[0], b[1], (float) PIECE.scale(), false);
    }

    static void drawAt(GuiGraphics g, Font font, float x, float y, float scale, boolean sample) {
        int[] s = PIECE.size(font, sample);
        int w = s[0], h = s[1];
        long now = System.currentTimeMillis();
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale);
        g.fill(0, 0, w, h, Ui.a(0.78f, 0x0C0A0E));
        int gold = Ui.mix(0xFFD24A, 0xFFF1A0, 0.5f + 0.5f * (float) Math.sin(now / 300.0));
        g.fill(0, 0, w, 1, Ui.a(1f, gold));
        g.fill(0, 0, 2, h, Ui.a(1f, gold));
        // a little present
        g.fill(6, 4, 15, 12, Ui.a(1f, 0xE0405A));
        g.fill(10, 4, 11, 12, Ui.a(1f, 0xFFD24A));
        g.fill(6, 7, 15, 8, Ui.a(1f, 0xFFD24A));
        g.fill(8, 2, 10, 4, Ui.a(1f, 0xFFD24A));
        g.fill(11, 2, 13, 4, Ui.a(1f, 0xFFD24A));
        g.drawString(font, "GIVEAWAY", 19, 4, Ui.a(1f, gold), true);
        String[] l = lines(sample);
        int[] cols = {0xFFFFFF, 0x6BFF9E, 0xC8B4BA, 0x9A8F94};
        for (int i = 0; i < l.length; i++) g.drawString(font, Ui.fit(font, l[i], w - 12), 7, 16 + i * 11, Ui.a(1f, cols[i]), false);
        pose.popMatrix();
    }
}
