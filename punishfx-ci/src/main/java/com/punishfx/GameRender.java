package com.punishfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Random;

/** Draws Spin the Wheel and Case Opening: on the HUD, in the move editor and in the settings preview. */
public final class GameRender {
    static final int R = 64, RIM = 7;
    static final int HEAD = 31;
    static final int WHEEL_W = 2 * (R + RIM) + 12, WHEEL_H = HEAD + 2 * (R + RIM) + 6;
    static final int CASE_W = 300;
    private static final int CARD_W = 58, CARD_H = 56, STEP = 62, BOX_L = 6, BOX_R = CASE_W - 6, BOX_T = 26, BOX_H = 64;
    private static final int[] GAME_SHOW = {0xFFD23F, 0x3A86FF, 0xFF006E, 0x06D6A0, 0xFB5607, 0x8338EC};

    private GameRender() {}

    static int caseH() {
        return BOX_T + BOX_H + (Config.get().caseShowOdds ? 14 : 4) + 30;
    }

    // ------------------------------------------------------------------ pieces (move & resize)

    public static final HudPiece WHEEL_PIECE = new HudPiece() {
        public String name() { return "wheel"; }
        public int[] size(Font font, boolean sample) { return new int[]{WHEEL_W, WHEEL_H}; }
        public void drawAt(GuiGraphics g, Font font, float x, float y, float scale, boolean sample) {
            long now = System.currentTimeMillis();
            draw(g, font, Games.demo(Games.Kind.WHEEL, now), x, y, scale, now, 1f, false);
        }
        public double x() { return Config.get().wheelX; }
        public double y() { return Config.get().wheelY; }
        public double scale() { return Config.get().wheelScale; }
        public void set(double x, double y, double s) {
            Config c = Config.get();
            c.wheelX = x;
            c.wheelY = y;
            c.wheelScale = s;
        }
        public boolean shown() { return true; }
        public boolean canHide() { return false; }
        public boolean onScreenNow() { return false; } // only pops up during a game
        public String showSettingName() { return ""; }
        public void reset() { set(0.5, 0.55, 1.0); }
        public java.util.List<HudPiece.Item> menu() {
            Config c = Config.get();
            return java.util.List.of(
                    HudPiece.cycle("Colours", Games.WHEEL_COLOR_NAMES, () -> c.wheelColors, v -> c.wheelColors = v),
                    HudPiece.cycle("Rim", Games.RIM_NAMES, () -> c.wheelRim, v -> c.wheelRim = v),
                    HudPiece.cycle("Labels", new String[]{"Names", "Icons", "Icons + names"}, () -> c.wheelLabels, v -> c.wheelLabels = v),
                    HudPiece.cycle("Pointer", Games.POINTER_NAMES, () -> c.wheelPointer, v -> c.wheelPointer = v),
                    HudPiece.cycle("Background", new String[]{"Clear", "Dim", "Dark"}, () -> c.wheelBg, v -> c.wheelBg = v));
        }
    };

    public static final HudPiece CASE_PIECE = new HudPiece() {
        public String name() { return "case"; }
        public int[] size(Font font, boolean sample) { return new int[]{CASE_W, caseH()}; }
        public void drawAt(GuiGraphics g, Font font, float x, float y, float scale, boolean sample) {
            long now = System.currentTimeMillis();
            draw(g, font, Games.demo(Games.Kind.CASE, now), x, y, scale, now, 1f, false);
        }
        public double x() { return Config.get().caseX; }
        public double y() { return Config.get().caseY; }
        public double scale() { return Config.get().caseScale; }
        public void set(double x, double y, double s) {
            Config c = Config.get();
            c.caseX = x;
            c.caseY = y;
            c.caseScale = s;
        }
        public boolean shown() { return true; }
        public boolean canHide() { return false; }
        public boolean onScreenNow() { return false; } // only pops up during a game
        public String showSettingName() { return ""; }
        public void reset() { set(0.5, 0.5, 1.0); }
        public java.util.List<HudPiece.Item> menu() {
            Config c = Config.get();
            return java.util.List.of(
                    HudPiece.cycle("Cards", new String[]{"CS", "Flat", "Minimal"}, () -> c.caseCard, v -> c.caseCard = v),
                    HudPiece.cycle("Rarity colours", new String[]{"CS", "Theme", "Rainbow"}, () -> c.caseColors, v -> c.caseColors = v),
                    HudPiece.toggle("Odds strip", () -> c.caseShowOdds, v -> c.caseShowOdds = v),
                    HudPiece.toggle("Icons", () -> c.caseIcons, v -> c.caseIcons = v),
                    HudPiece.cycle("Background", new String[]{"Clear", "Dim", "Dark"}, () -> c.caseBg, v -> c.caseBg = v));
        }
    };

    public static HudPiece piece(Games.Kind k) {
        return switch (k) {
            case WHEEL -> WHEEL_PIECE;
            case CASE -> CASE_PIECE;
            default -> null;
        };
    }

    static final int ELIM_W = 300, RAFFLE_W = 240, RAFFLE_H = 26 + 74 + 40;

    static int elimRows(Games.Game g) {
        return (g.entries.size() + elimCols(g) - 1) / elimCols(g);
    }

    static int elimCols(Games.Game g) {
        int n = g.entries.size();
        return n <= 4 ? 2 : n <= 9 ? 3 : n <= 20 ? 4 : 5;
    }

    /** Unscaled {width, height} of a game. */
    static int[] size(Games.Game g) {
        return switch (g.kind) {
            case WHEEL -> new int[]{WHEEL_W, WHEEL_H};
            case CASE -> new int[]{CASE_W, caseH(g)};
            case ELIMINATION -> new int[]{ELIM_W, 28 + elimRows(g) * 16 + 40};
            case RAFFLE -> new int[]{RAFFLE_W, RAFFLE_H};
        };
    }

    static int caseH(Games.Game g) {
        return BOX_T + BOX_H + (Config.get().caseShowOdds && !g.giveaway ? 14 : 4) + 30;
    }

    // ------------------------------------------------------------------ HUD

    /** The live game, over everything else on the HUD. A game that fails to draw is dropped, never left stuck. */
    public static void render(GuiGraphics g, Font font, int sw, int sh) {
        try {
            renderLive(g, font, sw, sh);
        } catch (Throwable t) {
            PunishFxClient.LOG.warn("Punish FX: a wheel/case/draw failed and was dropped", t);
            Games.cancelAll();
        }
    }

    private static void renderLive(GuiGraphics g, Font font, int sw, int sh) {
        Games.Game game = Games.current();
        if (game == null || game.start == 0) return;
        HudPiece piece = piece(game.kind);
        if (piece != null && MoveScreen.editing(piece)) return;
        Config c = Config.get();
        long now = System.currentTimeMillis();
        float vis = Math.min(1f, (now - game.start) / 250f);
        if (game.resolved) vis = Math.min(vis, (game.until - now) / 350f);
        if (vis < 0.05f) return;
        int bg = game.kind == Games.Kind.WHEEL ? c.wheelBg : c.caseBg;
        if (bg > 0) g.fill(0, 0, sw, sh, Ui.a((bg == 1 ? 0.3f : 0.62f) * vis, 0x000000));
        int win = game.kind == Games.Kind.WHEEL ? c.wheelWin : c.caseWin;
        if (game.resolved && win < 2) {
            float f = 1f - (now - game.resolvedAt) / 380f;
            if (f > 0.05f) g.fill(0, 0, sw, sh, Ui.a(0.4f * f, 0xFFFFFF));
        }
        if (piece != null) {
            int[] b = piece.bounds(font, sw, sh, false);
            draw(g, font, game, b[0], b[1], (float) piece.scale(), now, vis, true);
        } else { // elimination / raffle: centred, as big as fits
            int[] wh = size(game);
            float s = (float) Math.min(Config.get().caseScale * 1.1, Math.min((sw - 16) / (double) wh[0], (sh - 50) / (double) wh[1]));
            draw(g, font, game, (sw - wh[0] * s) / 2f, (sh - wh[1] * s) / 2f - sh * 0.04f, s, now, vis, true);
        }
        Games.frame(game, now);
    }

    /** Draws a game with its top-left at (x, y). */
    public static void draw(GuiGraphics g, Font font, Games.Game game, float x, float y, float scale, long now, float alpha, boolean live) {
        int[] wh = size(game);
        int w = wh[0], h = wh[1];
        // pop in during the intro
        float intro = (float) Math.max(0, Math.min(1, (now - game.start) / (double) game.introMs));
        float pop = live ? (float) (0.7 + 0.3 * backOut(intro)) : 1f;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x + w * scale / 2f, y + h * scale / 2f);
        pose.scale(scale * pop);
        pose.translate(-w / 2f, -h / 2f);
        switch (game.kind) {
            case WHEEL -> drawWheel(g, font, game, now, alpha, scale * pop);
            case CASE -> drawCase(g, font, game, now, alpha);
            case ELIMINATION -> drawElimination(g, font, game, now, alpha, w, h);
            case RAFFLE -> drawRaffle(g, font, game, now, alpha, w);
        }
        confetti(g, game, now, w, h);
        pose.popMatrix();
    }

    private static double backOut(double t) {
        double c1 = 1.70158, c3 = c1 + 1;
        return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
    }

    private static String title(String t, String fallback, Games.Game game) {
        String s = t == null || t.isBlank() ? fallback : t;
        return s.replace("{name}", game.donor == null ? "Someone" : game.donor);
    }

    private static void header(GuiGraphics g, Font font, String title, boolean donorLine, Games.Game game, int w, float alpha) {
        if (game.titleOverride != null) title = game.titleOverride;
        float ts = Math.min(1.3f, (w - 8) / (float) Math.max(1, font.width(title)));
        int tw = (int) (font.width(title) * ts);
        g.pose().pushMatrix();
        g.pose().translate((w - tw) / 2f, 0);
        g.pose().scale(ts);
        g.drawString(font, title, 1, 1, Ui.a(0.85f * alpha, 0x000000), false);
        g.drawString(font, title, 0, 0, Ui.a(alpha, Ui.mix(0xFFFFFF, Ui.ACCENT, 0.35f)), false);
        g.pose().popMatrix();
        if (game.subtitle != null) {
            String d = Ui.fit(font, game.subtitle, w - 8);
            g.drawString(font, d, (w - font.width(d)) / 2, 13, Ui.a(0.95f * alpha, 0xFFD24A), true);
        } else if (donorLine && game.donor != null) {
            String d = game.donor + " paid " + Money.compact(game.amount);
            d = Ui.fit(font, d, w - 8);
            g.drawString(font, d, (w - font.width(d)) / 2, 13, Ui.a(0.9f * alpha, 0xC8B4BA), true);
        }
    }

    // ------------------------------------------------------------------ wheel

    static int segColor(Games.Game game, int i, long now) {
        Config c = Config.get();
        Games.Entry e = game.entries.get(i);
        int n = game.entries.size();
        if (e.special()) {
            return switch (e.id) {
                case Games.NOTHING -> 0x2FA35C;
                case Games.AGAIN -> 0x2B6CD9;
                case Games.DOUBLE -> 0x8E0E25;
                default -> Ui.mix(0xFFC21A, 0xFFF1A6, 0.5f + 0.5f * (float) Math.sin(now / 160.0)); // jackpot shimmers
            };
        }
        return switch (c.wheelColors) {
            case 1 -> i % 2 == 0 ? 0xC8102E : 0x17171B;
            case 2 -> Ui.hsv(i / (float) n, 0.72f, 0.95f);
            case 3 -> Ui.hsv(i / (float) n, 0.3f, 1f);
            case 4 -> Ui.hsv(i / (float) n, 1f, 0.5f);
            case 5 -> GAME_SHOW[i % GAME_SHOW.length];
            case 6 -> {
                Punishes.P p = Punishes.get(e.id);
                int col = p == null ? 0x888888 : p.cat.color;
                yield i % 2 == 0 ? col : Ui.mix(col, 0x000000, 0.3f);
            }
            default -> i % 3 == 0 ? Ui.ACCENT : i % 3 == 1 ? Ui.mix(Ui.ACCENT, 0x000000, 0.5f) : Ui.ACCENT2;
        };
    }

    private static int rimColor(long now) {
        return switch (Config.get().wheelRim) {
            case 1 -> 0xD9A520;
            case 2 -> Ui.ACCENT;
            case 3 -> 0x7A1414;
            case 4 -> Ui.hsv((now % 4000) / 4000f, 0.85f, 1f);
            default -> 0x15151A;
        };
    }

    private static int dividerColor() {
        return switch (Config.get().wheelColors) {
            case 1 -> 0xE0B030;
            case 3, 6 -> 0x2A2A30;
            case 4 -> 0xFFFFFF;
            default -> 0xF4F4F4;
        };
    }

    private static float lum(int rgb) {
        return (0.299f * ((rgb >> 16) & 255) + 0.587f * ((rgb >> 8) & 255) + 0.114f * (rgb & 255)) / 255f;
    }

    private static void span(GuiGraphics g, double x0, double x1, int row, int argb) {
        int a = (int) Math.round(x0), b = (int) Math.round(x1);
        if (b > a) g.fill(a, row, b, row + 1, argb);
    }

    /** A filled ring (or disc when inner = 0) in high-res units. */
    private static void ring(GuiGraphics g, double outer, double inner, int argb) {
        int top = (int) Math.floor(-outer);
        for (int row = top; row < Math.ceil(outer); row++) {
            double y = row + 0.5;
            if (Math.abs(y) >= outer) continue;
            double wo = Math.sqrt(outer * outer - y * y);
            double wi = Math.abs(y) < inner ? Math.sqrt(inner * inner - y * y) : 0;
            if (wi <= 0) span(g, -wo, wo, row, argb);
            else {
                span(g, -wo, -wi, row, argb);
                span(g, wi, wo, row, argb);
            }
        }
    }

    private static void drawWheel(GuiGraphics g, Font font, Games.Game game, long now, float alpha, float scale) {
        Config c = Config.get();
        var pose = g.pose();
        int n = game.entries.size();
        double seg = Math.PI * 2 / n;
        double rot = game.start == 0 ? 0 : game.pos(now);
        float cx = WHEEL_W / 2f, cy = HEAD + R + RIM;

        header(g, font, title(c.wheelTitle, "SPIN THE WHEEL", game), c.wheelShowDonor, game, WHEEL_W, alpha);

        // ---- the disc, drawn in high-res units (about one screen pixel each) so the edges stay smooth
        double gs = Minecraft.getInstance().getWindow().getGuiScale();
        int res = (int) Math.max(1, Math.min(3, Math.round(gs * scale)));
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.scale(1f / res);
        double rd = R * res, ro = (R + (c.wheelRim == 0 ? 1.5 : RIM)) * res;
        int rim = rimColor(now);
        ring(g, ro + res * 1.5, ro, Ui.a(0.35f * alpha, 0x000000)); // soft shadow edge
        ring(g, ro, rd, Ui.a(alpha, rim));
        if (c.wheelRim == 1 || c.wheelRim == 3) ring(g, ro, ro - res, Ui.a(alpha, Ui.mix(rim, 0xFFFFFF, 0.35f)));
        if (c.wheelRim == 4) ring(g, ro + res * 3, ro, Ui.a(0.25f * alpha, rim));

        boolean blinkWin = game.resolved && ((now - game.resolvedAt) / 140) % 2 == 0;
        int[] colors = new int[n];
        for (int i = 0; i < n; i++) {
            colors[i] = segColor(game, i, now);
            if (blinkWin && i == game.win) colors[i] = Ui.mix(colors[i], 0xFFFFFF, 0.5f);
        }
        double[] xs = new double[n + 2];
        for (int row = (int) Math.floor(-rd); row < Math.ceil(rd); row++) {
            double y = row + 0.5;
            if (Math.abs(y) >= rd) continue;
            double half = Math.sqrt(rd * rd - y * y);
            int k = 0;
            xs[k++] = -half;
            for (int b = 0; b < n; b++) {
                double th = rot + b * seg;
                double dy = -Math.cos(th);
                if (Math.abs(dy) < 1e-9 || Math.signum(dy) != Math.signum(y)) continue;
                double x = y / dy * Math.sin(th);
                if (x > -half && x < half) xs[k++] = x;
            }
            xs[k++] = half;
            java.util.Arrays.sort(xs, 1, k - 1);
            for (int s = 0; s + 1 < k; s++) {
                double xm = (xs[s] + xs[s + 1]) / 2;
                double a = Math.atan2(xm, -y) - rot;
                a = ((a % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2);
                int idx = Math.min(n - 1, (int) (a / seg));
                span(g, xs[s], xs[s + 1], row, Ui.a(alpha, colors[idx]));
            }
        }
        // dividers between slots
        double t = Math.max(1, res * 0.9);
        int div = Ui.a(0.9f * alpha, dividerColor());
        for (int b = 0; b < n; b++) {
            double th = rot + b * seg, s = Math.sin(th), co = Math.cos(th);
            for (int row = (int) Math.floor(-rd); row < Math.ceil(rd); row++) {
                double y = row + 0.5;
                if (Math.abs(y) >= rd) continue;
                double half = Math.sqrt(rd * rd - y * y);
                double lo, hi;
                if (Math.abs(co) > 1e-4) {
                    double p = (-s * y - t / 2) / co, q = (-s * y + t / 2) / co;
                    lo = Math.min(p, q);
                    hi = Math.max(p, q);
                } else {
                    if (Math.abs(s * y) > t / 2) continue;
                    lo = -half;
                    hi = half;
                }
                // only the half-line going out from the centre
                if (Math.abs(s) > 1e-4) {
                    double edge = co * y / s;
                    if (s > 0) lo = Math.max(lo, edge);
                    else hi = Math.min(hi, edge);
                } else if (co * y > 0) continue;
                lo = Math.max(lo, -half);
                hi = Math.min(hi, half);
                if (hi > lo) span(g, lo, Math.max(hi, lo + 1), row, div);
            }
        }
        // hub
        ring(g, R * 0.21 * res, 0, Ui.a(alpha, rim));
        ring(g, R * 0.16 * res, 0, Ui.a(alpha, 0x1C1C22));
        pose.popMatrix();

        // ---- light bulbs on the rim
        if (c.wheelRim == 3) {
            int bulbs = 20;
            boolean spinning = game.spinning(now);
            for (int k = 0; k < bulbs; k++) {
                double a = k * Math.PI * 2 / bulbs;
                boolean lit = spinning ? (k + now / 90) % 3 == 0 : game.resolved ? (now / 200) % 2 == k % 2 : k % 2 == 0;
                float bx = cx + (float) Math.sin(a) * (R + RIM / 2f), by = cy - (float) Math.cos(a) * (R + RIM / 2f);
                int col = lit ? 0xFFF1A0 : 0x6A5520;
                g.fill(Math.round(bx - 1.5f), Math.round(by - 1.5f), Math.round(bx + 1.5f), Math.round(by + 1.5f), Ui.a(alpha, col));
            }
        }

        // ---- labels
        int mode = game.giveaway ? 0 : c.wheelLabels;
        for (int i = 0; i < n; i++) {
            Games.Entry e = game.entries.get(i);
            double mid = rot + (i + 0.5) * seg;
            mid = ((mid % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2);
            if (mode != 0) {
                double ri = mode == 1 ? R * 0.6 : R * 0.83;
                float is = (float) Math.max(0.45, Math.min(mode == 1 ? 1.3 : 0.85, ri * seg / 18.0));
                float ix = cx + (float) (Math.sin(mid) * ri), iy = cy - (float) (Math.cos(mid) * ri);
                pose.pushMatrix();
                pose.translate(ix, iy);
                pose.scale(is);
                g.renderItem(Games.icon(e.id), -8, -8);
                pose.popMatrix();
            }
            if (mode != 1) {
                double rIn = R * 0.23, rOut = mode == 2 ? R * 0.71 : R * 0.93;
                double arc = (rIn + rOut) / 2 * seg;
                // shrink long names to fit before cutting them short
                int fullW = font.width(TopDonorsHud.fontText(e.name, c.wheelFont, e.special()));
                float ls = (float) Math.max(0.55, Math.min(Math.min(1.0, arc * 0.85 / 9), (rOut - rIn) / Math.max(1, fullW)));
                String s = Ui.fit(font, e.name, (int) ((rOut - rIn) / ls));
                var comp = TopDonorsHud.fontText(s, c.wheelFont, e.special());
                int tw = font.width(comp);
                boolean flip = mid > Math.PI;
                int col = segColor(game, i, now);
                int tc = lum(col) > 0.62f ? 0x1A1A1A : 0xFFFFFF;
                pose.pushMatrix();
                pose.translate(cx, cy);
                pose.rotate((float) (mid - Math.PI / 2 + (flip ? Math.PI : 0)));
                pose.translate((float) (flip ? -rOut : rOut), 0);
                pose.scale(ls);
                g.drawString(font, comp, flip ? 0 : -tw, -4, Ui.a(alpha, tc), tc == 0xFFFFFF);
                pose.popMatrix();
            }
        }

        // ---- what's under the pointer, in the hub
        if (game.start != 0) {
            Games.Entry under = game.entries.get(Math.max(0, Math.min(n - 1, game.resolved ? game.win : game.under(now))));
            pose.pushMatrix();
            pose.translate(cx, cy);
            pose.scale(0.8f);
            g.renderItem(Games.icon(under.id), -8, -8);
            pose.popMatrix();
        }

        // ---- pointer at the top
        int pc = switch (c.wheelPointer) {
            case 1 -> 0xFFD24A;
            case 2 -> Ui.ACCENT;
            case 3 -> 0xFF3B3B;
            default -> 0xFFFFFF;
        };
        float top = cy - R - RIM - 5, tip = cy - R + 9;
        boolean nudge = game.spinning(now) && now - game.lastTickAt < 70;
        for (int row = 0; row < tip - top; row++) {
            float f = 1 - row / (tip - top);
            float hw = 8 * f + 0.6f;
            g.fill(Math.round(cx - hw - 1 + (nudge ? 1 : 0)), Math.round(top + row), Math.round(cx + hw + 1 + (nudge ? 1 : 0)), Math.round(top + row + 1), Ui.a(alpha, 0x101014));
        }
        for (int row = 1; row < tip - top - 1; row++) {
            float f = 1 - row / (tip - top);
            float hw = 8 * f - 0.4f;
            if (hw <= 0) continue;
            g.fill(Math.round(cx - hw + (nudge ? 1 : 0)), Math.round(top + row), Math.round(cx + hw + (nudge ? 1 : 0)), Math.round(top + row + 1),
                    Ui.a(alpha, row < 3 ? Ui.mix(pc, 0xFFFFFF, 0.4f) : pc));
        }

        // ---- result
        if (game.resolved) resultBanner(g, font, game, now, alpha, WHEEL_W, (int) (cy + R * 0.38f));
    }

    private static void resultBanner(GuiGraphics g, Font font, Games.Game game, long now, float alpha, int w, int y) {
        float p = (float) Math.min(1, (now - game.resolvedAt) / 220.0);
        float s = (float) (0.6 + 0.4 * backOut(p));
        int bh = game.resultSub.isEmpty() ? 22 : 32;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(w / 2f, y + bh / 2f);
        pose.scale(s);
        pose.translate(-w / 2f, -bh / 2f);
        int col = game.resultColor;
        g.fill(2, 0, w - 2, bh, Ui.a(0.88f * alpha, 0x0C0A0E));
        g.fill(2, 0, w - 2, 1, Ui.a(alpha, col));
        g.fill(2, bh - 1, w - 2, bh, Ui.a(alpha, col));
        String t = game.resultTitle;
        float ts = Math.min(1.5f, (w - 12) / (float) Math.max(1, font.width(t)));
        int tw = (int) (font.width(t) * ts);
        pose.pushMatrix();
        pose.translate((w - tw) / 2f, 5);
        pose.scale(ts);
        g.drawString(font, t, 1, 1, Ui.a(0.9f * alpha, 0x000000), false);
        g.drawString(font, t, 0, 0, Ui.a(alpha, col), false);
        pose.popMatrix();
        if (!game.resultSub.isEmpty()) {
            String sub = Ui.fit(font, game.resultSub, (int) ((w - 10) / 0.75f));
            pose.pushMatrix();
            pose.translate(w / 2f - font.width(sub) * 0.75f / 2f, bh - 10);
            pose.scale(0.75f);
            g.drawString(font, sub, 0, 0, Ui.a(alpha, 0xE8E2E6), false);
            pose.popMatrix();
        }
        pose.popMatrix();
    }

    private static void confetti(GuiGraphics g, Games.Game game, long now, int w, int h) {
        int win = game.giveaway ? 0 : game.kind == Games.Kind.WHEEL ? Config.get().wheelWin : Config.get().caseWin;
        if (!game.resolved || win != 0 || game.winner().id.equals(Games.AGAIN)) return;
        float t = (now - game.resolvedAt) / 1000f;
        if (game.giveaway && t > 2.4f) t = (t - 2.4f) % 2.4f; // giveaways keep celebrating
        if (t > 2.4f) return;
        Random r = new Random(game.seed);
        float fade = Math.min(1f, (2.4f - t) / 0.6f);
        for (int i = 0; i < 70; i++) {
            float vx = (r.nextFloat() - 0.5f) * 220, vy = -60 - r.nextFloat() * 150;
            float x = w / 2f + vx * t, y = h * 0.45f + vy * t + 170 * t * t;
            int col = Ui.hsv(r.nextFloat(), 0.8f, 1f);
            float sz = 1.5f + r.nextFloat() * 1.8f;
            float wob = (float) Math.sin(t * 9 + i) * sz * 0.5f;
            g.fill(Math.round(x), Math.round(y), Math.round(x + sz + wob), Math.round(y + sz), Ui.a(fade, col));
        }
    }

    // ------------------------------------------------------------------ giveaway: elimination + raffle

    private static void drawElimination(GuiGraphics g, Font font, Games.Game game, long now, float alpha, int w, int h) {
        header(g, font, "LAST ONE STANDING", false, game, w, alpha);
        int n = game.entries.size(), cols = elimCols(game);
        int out = game.start == 0 ? 0 : Math.min(n - 1, Math.max(0, game.under(now)));
        boolean[] gone = new boolean[n];
        long[] goneAt = new long[n];
        for (int k = 0; k < out && k < game.elimOrder.length; k++) gone[game.elimOrder[k]] = true;
        // when did the latest one go? (for its red flash)
        int latest = out > 0 && out - 1 < game.elimOrder.length ? game.elimOrder[out - 1] : -1;
        int cw = (w - 12 - (cols - 1) * 3) / cols, ch = 13;
        int top = 28;
        for (int i = 0; i < n; i++) {
            int x = 6 + (i % cols) * (cw + 3), y = top + (i / cols) * (ch + 3);
            Games.Entry e = game.entries.get(i);
            boolean win = game.resolved && i == game.win;
            int bg = gone[i] ? 0x1A0E10 : win ? 0x5A4510 : 0x22222C;
            g.fill(x, y, x + cw, y + ch, Ui.a(0.92f * alpha, bg));
            int edge = gone[i] ? (i == latest && game.spinning(now) ? 0xFF3B3B : 0x4A2A2E) : win ? 0xFFD700 : Ui.ACCENT2;
            g.fill(x, y, x + 2, y + ch, Ui.a(alpha, edge));
            if (win) {
                float p = 0.6f + 0.4f * (float) Math.sin(now / 120.0);
                int gc = Ui.a(p * alpha, 0xFFD700);
                g.fill(x - 1, y - 1, x + cw + 1, y, gc);
                g.fill(x - 1, y + ch, x + cw + 1, y + ch + 1, gc);
                g.fill(x + cw, y - 1, x + cw + 1, y + ch + 1, gc);
            }
            String nm = Ui.fit(font, e.name, cw - 14);
            int tc = gone[i] ? 0x6A5A5E : win ? 0xFFE680 : 0xFFFFFF;
            g.drawString(font, nm, x + 5, y + 3, Ui.a(alpha, tc), !gone[i]);
            if (gone[i]) {
                g.fill(x + 4, y + 7, x + 5 + font.width(nm), y + 8, Ui.a(0.8f * alpha, 0xC03040)); // struck out
                g.drawString(font, "\u2716", x + cw - 9, y + 3, Ui.a(alpha, 0xFF4A4A), false);
            }
        }
        int left = n - out;
        int ry = top + elimRows(game) * (ch + 3) + 4;
        if (game.resolved) resultBanner(g, font, game, now, alpha, w, ry);
        else {
            String s = left + " left";
            g.drawString(font, s, (w - font.width(s)) / 2, ry + 8, Ui.a(0.9f * alpha, 0xC8B4BA), true);
        }
    }

    private static void drawRaffle(GuiGraphics g, Font font, Games.Game game, long now, float alpha, int w) {
        header(g, font, "RAFFLE DRAW", false, game, w, alpha);
        var pose = g.pose();
        double p = game.start == 0 ? game.from : game.pos(now);
        int cur = Math.max(0, Math.min(game.reel.size() - 1, (int) Math.floor(p)));
        int bx = 10, by = 28, bw = w - 20, bh = 70;
        // a ticket: gold edges with notches
        g.fill(bx, by, bx + bw, by + bh, Ui.a(alpha, 0x2A1E08));
        g.fill(bx + 2, by + 2, bx + bw - 2, by + bh - 2, Ui.a(alpha, 0x3C2C0E));
        boolean done = game.resolved;
        int edge = done ? Ui.mix(0xFFD700, 0xFFFFFF, 0.3f + 0.3f * (float) Math.sin(now / 110.0)) : 0xD9A520;
        g.fill(bx, by, bx + bw, by + 2, Ui.a(alpha, edge));
        g.fill(bx, by + bh - 2, bx + bw, by + bh, Ui.a(alpha, edge));
        for (int k = 0; k < 5; k++) {
            int ny = by + 8 + k * 13;
            g.fill(bx, ny, bx + 3, ny + 6, Ui.a(alpha, 0x0C0A0E));
            g.fill(bx + bw - 3, ny, bx + bw, ny + 6, Ui.a(alpha, 0x0C0A0E));
        }
        // the names rolling past: previous above, current big, next below
        double frac = p - Math.floor(p);
        for (int d = -1; d <= 1; d++) {
            int i = cur + d;
            if (i < 0 || i >= game.reel.size()) continue;
            String nm = game.reel.get(i).name;
            float sc = d == 0 ? 2.0f : 1.0f;
            float yy = by + bh / 2f + (float) ((d - (done ? 0 : frac - 0.5)) * 22) - 4 * sc;
            if (yy < by + 3 || yy + 8 * sc > by + bh - 3) continue;
            int tw = font.width(nm);
            float fit = Math.min(sc, (bw - 20) / (float) Math.max(1, tw));
            pose.pushMatrix();
            pose.translate(bx + bw / 2f - tw * fit / 2f, yy);
            pose.scale(fit);
            int col = d == 0 ? (done ? 0xFFE680 : 0xFFFFFF) : 0x8C7A5A;
            g.drawString(font, nm, 0, 0, Ui.a(d == 0 ? alpha : 0.6f * alpha, col), d == 0);
            pose.popMatrix();
        }
        int ry = by + bh + 6;
        if (done) resultBanner(g, font, game, now, alpha, w, ry);
        else if (game.start != 0) {
            String s = "Drawing...";
            g.drawString(font, s, (w - font.width(s)) / 2, ry + 8, Ui.a(0.8f * alpha, 0xC8B4BA), true);
        }
    }

    // ------------------------------------------------------------------ case

    public static int rarityColor(int r) {
        r = Math.max(0, Math.min(4, r));
        return switch (Config.get().caseColors) {
            case 1 -> new int[]{Ui.mix(0x8A8A8A, Ui.ACCENT, 0.2f), Ui.mix(0x8A8A8A, Ui.ACCENT, 0.55f), Ui.ACCENT, Ui.ACCENT2, 0xFFD700}[r];
            case 2 -> new int[]{0x3DDC84, 0x4FB8F7, 0xC77DFF, 0xFF5A5A, 0xFFD700}[r];
            default -> Games.RARITY_COLORS[r];
        };
    }

    private static void card(GuiGraphics g, Font font, Games.Entry e, float xl, int y, float alpha, boolean glow, long now) {
        Config c = Config.get();
        int rc = rarityColor(e.rarity);
        int x0 = Math.round(xl), x1 = x0 + CARD_W;
        int cl = Math.max(x0, BOX_L + 1), cr = Math.min(x1, BOX_R - 1);
        if (cr <= cl) return;
        switch (c.caseCard) {
            case 1 -> g.fill(cl, y, cr, y + CARD_H, Ui.a(alpha, Ui.mix(rc, 0x000000, 0.45f)));
            case 2 -> {
                g.fill(cl, y, cr, y + CARD_H, Ui.a(alpha, 0x18181E));
                if (x0 >= cl) g.fill(x0, y, x0 + 2, y + CARD_H, Ui.a(alpha, rc));
            }
            default -> {
                g.fillGradient(cl, y, cr, y + CARD_H, Ui.a(alpha, 0x2C2C36), Ui.a(alpha, Ui.mix(rc, 0x101014, 0.6f)));
                g.fill(cl, y + CARD_H - 3, cr, y + CARD_H, Ui.a(alpha, rc));
            }
        }
        if (glow) {
            float p = 0.6f + 0.4f * (float) Math.sin(now / 120.0);
            int gc = Ui.a(p * alpha, Ui.mix(rc, 0xFFFFFF, 0.3f));
            g.fill(cl, y - 2, cr, y, gc);
            g.fill(cl, y + CARD_H, cr, y + CARD_H + 2, gc);
            if (x0 >= cl) g.fill(x0 - 2, y - 2, x0, y + CARD_H + 2, gc);
            if (x1 <= cr) g.fill(x1, y - 2, x1 + 2, y + CARD_H + 2, gc);
        }
        boolean inside = x0 >= BOX_L + 1 && x1 <= BOX_R - 1;
        if (!inside) return;
        var pose = g.pose();
        if (c.caseIcons) {
            pose.pushMatrix();
            pose.translate(x0 + CARD_W / 2f, y + (c.caseNames ? 20 : 26));
            pose.scale(1.6f);
            g.renderItem(Games.icon(e.id), -8, -8);
            pose.popMatrix();
        }
        if (c.caseNames) {
            String s = Ui.fit(font, e.name, (int) ((CARD_W - 4) / 0.7f));
            pose.pushMatrix();
            pose.translate(x0 + CARD_W / 2f - font.width(s) * 0.7f / 2f, y + (c.caseIcons ? 40 : 24));
            pose.scale(0.7f);
            g.drawString(font, s, 0, 0, Ui.a(alpha, c.caseCard == 2 ? Ui.mix(rc, 0xFFFFFF, 0.4f) : 0xFFFFFF), c.caseCard != 2);
            pose.popMatrix();
        }
    }

    private static void drawCase(GuiGraphics g, Font font, Games.Game game, long now, float alpha) {
        Config c = Config.get();
        var pose = g.pose();
        header(g, font, title(c.caseTitle, "PUNISHMENT CASE", game), c.caseShowDonor, game, CASE_W, alpha);

        // reel box
        g.fill(BOX_L - 1, BOX_T - 1, BOX_R + 1, BOX_T + BOX_H + 1, Ui.a(0.9f * alpha, 0x000000));
        g.fill(BOX_L, BOX_T, BOX_R, BOX_T + BOX_H, Ui.a(0.92f * alpha, 0x0D0D12));
        g.fill(BOX_L, BOX_T, BOX_R, BOX_T + 1, Ui.a(0.6f * alpha, Ui.ACCENT));
        double p = game.start == 0 ? game.from : game.pos(now);
        float centre = CASE_W / 2f;
        int cy = BOX_T + (BOX_H - CARD_H) / 2;
        int first = (int) Math.floor(p - centre / STEP - 1), last = (int) Math.ceil(p + centre / STEP + 1);
        for (int i = Math.max(0, first); i <= Math.min(game.reel.size() - 1, last); i++) {
            float xl = centre + (float) ((i - p) * STEP) + (STEP - CARD_W) / 2f;
            card(g, font, game.reel.get(i), xl, cy, alpha, game.resolved && i == game.win, now);
        }
        // fade the ends of the reel
        for (int k = 0; k < 10; k++) {
            float a = 0.85f * (1 - k / 10f) * alpha;
            g.fill(BOX_L + k * 4, BOX_T + 1, BOX_L + k * 4 + 4, BOX_T + BOX_H, Ui.a(a, 0x0D0D12));
            g.fill(BOX_R - k * 4 - 4, BOX_T + 1, BOX_R - k * 4, BOX_T + BOX_H, Ui.a(a, 0x0D0D12));
        }
        // the marker
        int mx = Math.round(centre);
        int mc = 0xFFD24A;
        g.fill(mx - 1, BOX_T - 3, mx + 1, BOX_T + BOX_H + 3, Ui.a(0.8f * alpha, mc));
        for (int k = 0; k < 5; k++) {
            g.fill(mx - 5 + k, BOX_T - 4 + k, mx + 5 - k, BOX_T - 3 + k, Ui.a(alpha, mc));
            g.fill(mx - 5 + k, BOX_T + BOX_H + 3 - k, mx + 5 - k, BOX_T + BOX_H + 4 - k, Ui.a(alpha, mc));
        }

        int y = BOX_T + BOX_H + 4;
        if (c.caseShowOdds && !game.giveaway) {
            double[] odds = Games.rarityChances(game.entries);
                int total = 0;
            String[] labels = new String[5];
            for (int r = 0; r < 5; r++) {
                if (odds[r] <= 0) continue;
                double pc = odds[r] * 100;
                labels[r] = (pc >= 10 ? String.valueOf(Math.round(pc)) : pc >= 1 ? String.format("%.1f", pc) : String.format("%.2f", pc)) + "%";
                total += 9 + (int) (font.width(labels[r]) * 0.75f) + 8;
            }
            float ox = (CASE_W - total) / 2f;
            for (int r = 0; r < 5; r++) {
                if (labels[r] == null) continue;
                int col = rarityColor(r);
                g.fill(Math.round(ox), y + 2, Math.round(ox) + 6, y + 8, Ui.a(alpha, col));
                pose.pushMatrix();
                pose.translate(ox + 9, y + 2);
                pose.scale(0.75f);
                g.drawString(font, labels[r], 0, 0, Ui.a(alpha, Ui.mix(col, 0xFFFFFF, 0.5f)), false);
                pose.popMatrix();
                ox += 9 + font.width(labels[r]) * 0.75f + 8;
            }
            y += 14;
        }
        if (game.resolved) resultBanner(g, font, game, now, alpha, CASE_W, y);
        else if (game.start != 0 && game.spinning(now)) {
            String s = "Opening...";
            g.drawString(font, s, (CASE_W - font.width(s)) / 2, y + 8, Ui.a(0.7f * alpha, 0xC8B4BA), true);
        }
    }

    /** For the preview: text in a box when nothing can be shown. */
    static void note(GuiGraphics g, Font font, String s, int x, int y) {
        g.drawString(font, Component.literal(s), x, y, Ui.a(1f, 0x9A8F94), false);
    }

    static ItemStack icon(String id) {
        return Games.icon(id);
    }
}
