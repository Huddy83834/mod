package com.punishfx;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** The little "● LIVE 1:02:33 · $3.2M · 14 donations" counter shown while a stream is running. */
public final class StreamHud {
    private StreamHud() {}

    public static String clock(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
    }

    private static List<String> parts(boolean sample) {
        Config c = Config.get();
        StreamSession.Record r = StreamSession.current();
        long ms = r != null ? r.duration() : 2_530_000;
        double total = r != null ? r.total() : 2_500_000;
        int count = r != null ? r.donations.size() : 12;
        if (r == null && !sample) return List.of();
        List<String> out = new ArrayList<>();
        if (c.streamHudTimer) out.add(clock(ms));
        if (c.streamHudMoney) out.add(Money.compact(total));
        if (c.streamHudCount) out.add(count + (count == 1 ? " donation" : " donations"));
        return out;
    }

    private static boolean on(boolean sample) {
        return sample || StreamSession.live();
    }

    private static String label(boolean sample) {
        return on(sample) ? "LIVE" : "NOT LIVE";
    }

    private static String shownText(boolean sample) {
        return on(sample) ? text(sample) : "Go live on the Stream page";
    }

    private static String text(boolean sample) {
        return String.join("  ·  ", parts(sample));
    }

    public static final HudPiece PIECE = new HudPiece() {
        public String name() { return "live counter"; }

        public int[] size(Font font, boolean sample) {
            String t = shownText(sample);
            return new int[]{8 + 7 + font.width(label(sample)) + 6 + (t.isEmpty() ? 0 : font.width(t) + 6) + 4, 15};
        }

        public void drawAt(GuiGraphics g, Font font, float x, float y, float scale, boolean sample) {
            StreamHud.drawAt(g, font, x, y, scale, sample);
        }

        public double x() { return Config.get().streamX; }
        public double y() { return Config.get().streamY; }
        public double scale() { return Config.get().streamScale; }

        public void set(double x, double y, double scale) {
            Config c = Config.get();
            c.streamX = x;
            c.streamY = y;
            c.streamScale = scale;
        }

        public boolean shown() { return Config.get().streamHud; }
        public void setShown(boolean on) { Config.get().streamHud = on; }
        public java.util.List<HudPiece.Item> menu() {
            Config c = Config.get();
            return java.util.List.of(
                    new HudPiece.Item(() -> StreamSession.live() ? "End stream (shows the recap)" : "\u25cf Go live now", () -> {
                        if (StreamSession.live()) StreamSession.stop();
                        else StreamSession.start();
                    }),
                    HudPiece.toggle("Timer", () -> c.streamHudTimer, v -> c.streamHudTimer = v),
                    HudPiece.toggle("Money", () -> c.streamHudMoney, v -> c.streamHudMoney = v),
                    HudPiece.toggle("Donation count", () -> c.streamHudCount, v -> c.streamHudCount = v),
                    HudPiece.toggle("Show \"NOT LIVE\" when off", () -> c.streamHudIdle, v -> c.streamHudIdle = v));
        }
        public String showSettingName() { return "Show the live counter"; }
        public void reset() { set(0.0, 0.15, 1.0); }
    };

    public static void render(GuiGraphics g, Font font, int sw, int sh) {
        Config c = Config.get();
        if (!c.streamHud || MoveScreen.editing(PIECE)) return;
        if (!StreamSession.live() && !c.streamHudIdle) return;
        int[] b = PIECE.bounds(font, sw, sh, false);
        drawAt(g, font, b[0], b[1], (float) PIECE.scale(), false);
    }

    public static void drawAt(GuiGraphics g, Font font, float x, float y, float scale, boolean sample) {
        int[] s = PIECE.size(font, sample);
        int w = s[0], h = s[1];
        String t = shownText(sample);
        boolean live = on(sample);
        long now = System.currentTimeMillis();
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale);
        g.fill(0, 0, w, h, Ui.a(0.7f, 0x000000));
        int dot = live ? 0xFF3B3B : 0x7A6C70;
        g.fill(0, 0, 2, h, Ui.a(1f, dot));
        float pulse = live ? 0.55f + 0.45f * (float) Math.sin(now / 280.0) : 0.8f;
        g.fill(6, 5, 11, 10, Ui.a(pulse, dot)); // blinking record dot
        int tx = 15;
        g.drawString(font, label(sample), tx, 4, Ui.a(1f, live ? 0xFF5A5A : 0xB0A4A8), false);
        tx += font.width(label(sample)) + 6;
        if (!t.isEmpty()) g.drawString(font, t, tx, 4, Ui.a(1f, live ? 0xFFFFFF : 0x9A8F94), false);
        pose.popMatrix();
    }
}
