package com.punishfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** The on-screen "top donors" board: movable, resizable, with its own font, colours and background. */
public final class TopDonorsHud {
    public static final String[] FONT_NAMES = {"Minecraft", "Thin", "Runes", "Illager"};
    private static final FontDescription[] FONTS = {
            FontDescription.DEFAULT,
            new FontDescription.Resource(Identifier.withDefaultNamespace("uniform")),
            new FontDescription.Resource(Identifier.withDefaultNamespace("alt")),
            new FontDescription.Resource(Identifier.withDefaultNamespace("illageralt"))};
    public static final String[] COLOR_NAMES = {"Theme", "White", "Gold", "Aqua", "Lime", "Pink", "Red", "Rainbow"};
    private static final int[] COLORS = {-1, 0xFFFFFF, 0xFFD24A, 0x55FFFF, 0x7CFF6B, 0xFF8AD8, 0xFF5555, -2};
    public static final String[] BG_NAMES = {"None", "Dark", "Glass", "Theme"};
    private static final int[] MEDALS = {0xFFD24A, 0xDCE3EC, 0xE0915A};
    private static final int PAD = 5, LINE = 10;

    private TopDonorsHud() {}

    /** One drawable line of the board. */
    private record Line(String rank, String name, String amount) {}

    private static List<Line> lines(boolean sample) {
        Config c = Config.get();
        List<Line> out = new ArrayList<>();
        int rows = c.topStyle == 1 ? 1 : c.topRows;
        List<DonorBoard.Entry> top = DonorBoard.top(c.topPeriod, rows);
        if (top.isEmpty() && sample) {
            String[] names = {"Steve", "Alex", "Notch", "Jeb_", "Herobrine", "Dinnerbone", "Grumm", "Kai", "Zuri", "Sunny"};
            double[] amts = {2_500_000, 1_200_000, 750_000, 420_000, 300_000, 150_000, 90_000, 60_000, 25_000, 10_000};
            for (int i = 0; i < rows; i++) out.add(new Line((i + 1) + ".", names[i], money(amts[i])));
            return out;
        }
        for (int i = 0; i < top.size(); i++) out.add(new Line((i + 1) + ".", top.get(i).name, money(top.get(i).total)));
        return out;
    }

    private static String money(double v) {
        int a = Config.get().topAmounts;
        return a == 2 ? "" : a == 1 ? Money.full(v) : Money.compact(v);
    }

    /** Text in one of the board fonts (also used by the wheel). */
    public static MutableComponent fontText(String s, int fontIdx, boolean bold) {
        FontDescription f = FONTS[Math.max(0, Math.min(FONTS.length - 1, fontIdx))];
        return Component.literal(s).withStyle(st -> st.withFont(f).withBold(bold));
    }

    private static MutableComponent styled(String s, boolean bold) {
        return styled(s, bold, false);
    }

    /** Numbers (ranks, amounts) stay in the normal font with the novelty fonts so they can be read. */
    private static MutableComponent styled(String s, boolean bold, boolean numeric) {
        Config c = Config.get();
        int fi = Math.max(0, Math.min(FONTS.length - 1, c.topFont));
        FontDescription f = numeric && fi >= 2 ? FontDescription.DEFAULT : FONTS[fi];
        return Component.literal(s).withStyle(st -> st.withFont(f).withBold(bold || c.topBold).withItalic(c.topItalic));
    }

    private static String title() {
        String t = Config.get().topTitle;
        return t == null || t.isBlank() ? "TOP DONORS" : t;
    }

    /** Unscaled {width, height} of the board. */
    private static int[] size(Font font, List<Line> lines) {
        if (Config.get().topStyle == 1) return singleSize(font, lines);
        int titleW = font.width(styled(title(), true));
        int rankW = 0, nameW = 0, amtW = 0;
        for (Line l : lines) {
            rankW = Math.max(rankW, font.width(styled(l.rank, false, true)));
            nameW = Math.max(nameW, font.width(styled(l.name, false)));
            amtW = Math.max(amtW, font.width(styled(l.amount, false, true)));
        }
        int rowsW = lines.isEmpty() ? font.width(styled("No donations yet", false)) : rankW + 4 + nameW + (amtW > 0 ? 10 + amtW : 0);
        int w = PAD * 2 + Math.max(titleW, rowsW);
        int h = PAD * 2 + LINE + 4 + Math.max(1, lines.size()) * LINE - 1;
        return new int[]{w, h};
    }

    private static boolean visible(boolean sample) {
        Config c = Config.get();
        return !(c.topHideEmpty && !sample && DonorBoard.size(c.topPeriod) == 0);
    }

    /** The board as a movable HUD piece (for the Move & resize editor). */
    public static final HudPiece PIECE = new HudPiece() {
        public String name() { return "top donors board"; }
        public int[] size(Font font, boolean sample) { return TopDonorsHud.size(font, lines(sample)); }
        public void drawAt(GuiGraphics g, Font font, float x, float y, float scale, boolean sample) { TopDonorsHud.drawAt(g, font, x, y, scale, sample); }
        public double x() { return Config.get().topX; }
        public double y() { return Config.get().topY; }
        public double scale() { return Config.get().topScale; }
        public void set(double x, double y, double scale) {
            Config c = Config.get();
            c.topX = x;
            c.topY = y;
            c.topScale = scale;
        }
        public boolean shown() { return Config.get().topShow; }
        public String showSettingName() { return "Show on screen"; }
        public void reset() { set(1.0, 0.3, 1.0); }
    };

    /** Where the board is on screen, in GUI pixels: {x, y, width, height} (already scaled). */
    public static int[] bounds(Font font, int sw, int sh, boolean sample) {
        return PIECE.bounds(font, sw, sh, sample);
    }

    /** Draws the board on the HUD at its saved position. */
    public static void render(GuiGraphics g, Font font, int sw, int sh) {
        Config c = Config.get();
        if (!c.topShow || !visible(false)) return;
        if (MoveScreen.editing(PIECE)) return; // the editor draws its own copy
        int[] b = bounds(font, sw, sh, false);
        drawAt(g, font, b[0], b[1], (float) c.topScale, false);
    }

    /** Draws the board with its top-left corner at (x, y). {@code sample} fills in example names if empty. */
    public static void drawAt(GuiGraphics g, Font font, float x, float y, float scale, boolean sample) {
        Config c = Config.get();
        List<Line> lines = lines(sample);
        int[] s = size(font, lines);
        int w = s[0], h = s[1];
        long now = System.currentTimeMillis();
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale);
        background(g, w, h);
        if (c.topStyle == 1) {
            drawSingle(g, font, lines, w, now);
            pose.popMatrix();
            return;
        }
        drawBoard(g, font, lines, w, now);
        pose.popMatrix();
    }

    private static void background(GuiGraphics g, int w, int h) {
        switch (Config.get().topBackground) {
            case 1 -> {
                g.fill(0, 0, w, h, Ui.a(0.6f, 0x000000));
                g.fill(0, 0, w, 1, Ui.a(0.9f, Ui.ACCENT));
            }
            case 2 -> {
                g.fillGradient(0, 0, w, h, Ui.a(0.22f, 0xFFFFFF), Ui.a(0.08f, 0xFFFFFF));
                g.fill(0, 0, w, 1, Ui.a(0.45f, 0xFFFFFF));
                g.fill(0, h - 1, w, h, Ui.a(0.2f, 0xFFFFFF));
                g.fill(0, 0, 1, h, Ui.a(0.3f, 0xFFFFFF));
                g.fill(w - 1, 0, w, h, Ui.a(0.3f, 0xFFFFFF));
            }
            case 3 -> {
                g.fillGradient(0, 0, w, h, Ui.a(0.85f, Ui.tint(0x141118, 0.18f)), Ui.a(0.85f, Ui.tint(0x08070A, 0.1f)));
                g.fill(0, 0, w, 1, Ui.a(1f, Ui.ACCENT));
                g.fill(0, h - 1, w, h, Ui.a(0.8f, Ui.ACCENT2));
                g.fill(0, 0, 1, h, Ui.a(0.8f, Ui.ACCENT));
                g.fill(w - 1, 0, w, h, Ui.a(0.8f, Ui.ACCENT));
            }
            default -> { }
        }
    }

    private static void drawBoard(GuiGraphics g, Font font, List<Line> lines, int w, long now) {
        Config c = Config.get();
        boolean shadow = c.topShadow;
        int textCol = color(c.topColor, 0, now);
        int titleCol = c.topColor == 0 ? Ui.ACCENT : textCol;

        // title, centred
        MutableComponent t = styled(title(), true);
        g.drawString(font, t, (w - font.width(t)) / 2, PAD, Ui.a(1f, titleCol), shadow);
        int ly = PAD + LINE + 1;
        g.fill(PAD, ly, w - PAD, ly + 1, Ui.a(0.5f, titleCol));
        ly += 3;

        if (lines.isEmpty()) {
            g.drawString(font, styled("No donations yet", false), PAD, ly, Ui.a(0.7f, 0xFFFFFF), shadow);
        }
        int rankW = 0;
        for (Line l : lines) rankW = Math.max(rankW, font.width(styled(l.rank, false, true)));
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            int col = color(c.topColor, i, now);
            if (c.topMedals && i < 3) col = MEDALS[i];
            MutableComponent rank = styled(l.rank, i < 3 && c.topMedals, true);
            MutableComponent name = styled(l.name, false);
            MutableComponent amt = styled(l.amount, false, true);
            int amtCol = c.topColor == 7 ? col : 0x7CFFA0;
            g.drawString(font, rank, PAD + rankW - font.width(rank), ly, Ui.a(1f, col), shadow);
            g.drawString(font, name, PAD + rankW + 4, ly, Ui.a(1f, col), shadow);
            if (!l.amount.isEmpty()) g.drawString(font, amt, w - PAD - font.width(amt), ly, Ui.a(1f, amtCol), shadow);
            ly += LINE;
        }
    }

    // ---- "Top donator only": one name with a label (and a crown)

    private static final String[] CROWN = {
            "X...X...X",
            "XX.XXX.XX",
            "XXXXXXXXX",
            "XXXXXXXXX",
            "BBBBBBBBB"};
    private static final int CROWN_W = 11;

    private static String label() {
        String t = Config.get().topLabel;
        return t == null || t.isBlank() ? "TOP DONATOR" : t;
    }

    private static int[] singleSize(Font font, List<Line> lines) {
        Config c = Config.get();
        int crown = c.topCrown ? CROWN_W : 0;
        String name = lines.isEmpty() ? "No donations yet" : lines.get(0).name;
        String amt = lines.isEmpty() ? "" : lines.get(0).amount;
        int nameW = font.width(styled(name, false)) + (amt.isEmpty() ? 0 : 6 + font.width(styled(amt, false, true)));
        int labelW = font.width(styled(label(), true));
        if (c.topLabelPos == 0) return new int[]{PAD * 2 + Math.max(labelW, crown + nameW), PAD * 2 + LINE + 2 + 8};
        return new int[]{PAD * 2 + crown + labelW + 8 + nameW, PAD * 2 + 8};
    }

    private static void crown(GuiGraphics g, int x, int y) {
        for (int r = 0; r < CROWN.length; r++)
            for (int k = 0; k < CROWN[r].length(); k++) {
                char ch = CROWN[r].charAt(k);
                if (ch == '.') continue;
                g.fill(x + k, y + r + 1, x + k + 1, y + r + 2, Ui.a(1f, ch == 'B' ? 0xC08A10 : 0xFFD24A));
            }
    }

    private static void drawSingle(GuiGraphics g, Font font, List<Line> lines, int w, long now) {
        Config c = Config.get();
        boolean shadow = c.topShadow;
        int textCol = color(c.topColor, 0, now);
        int labelCol = c.topColor == 0 ? Ui.ACCENT : textCol;
        int nameCol = c.topMedals && !lines.isEmpty() ? MEDALS[0] : textCol;
        String nameS = lines.isEmpty() ? "No donations yet" : lines.get(0).name;
        String amtS = lines.isEmpty() ? "" : lines.get(0).amount;
        MutableComponent label = styled(label(), true);
        MutableComponent name = styled(nameS, false);
        MutableComponent amt = styled(amtS, false, true);
        int crown = c.topCrown ? CROWN_W : 0;
        int nameW = font.width(name) + (amtS.isEmpty() ? 0 : 6 + font.width(amt));
        int x = PAD, y = PAD;
        if (c.topLabelPos == 0) {
            g.drawString(font, label, (w - font.width(label)) / 2, y, Ui.a(1f, labelCol), shadow);
            y += LINE + 2;
            x = (w - crown - nameW) / 2;
        }
        if (c.topCrown) {
            crown(g, x, y);
            x += crown;
        }
        if (c.topLabelPos == 1) {
            g.drawString(font, label, x, y, Ui.a(1f, labelCol), shadow);
            x += font.width(label) + 8;
        }
        g.drawString(font, name, x, y, Ui.a(1f, nameCol), shadow);
        x += font.width(name);
        if (!amtS.isEmpty()) {
            x += 6;
            g.drawString(font, amt, x, y, Ui.a(1f, c.topColor == 7 ? textCol : 0x7CFFA0), shadow);
            x += font.width(amt);
        }
        if (c.topLabelPos == 2) {
            x += 8;
            g.drawString(font, label, x, y, Ui.a(1f, labelCol), shadow);
        }
    }

    private static int color(int idx, int row, long now) {
        int c = COLORS[Math.max(0, Math.min(COLORS.length - 1, idx))];
        if (c == -1) return Ui.mix(0xFFFFFF, Ui.ACCENT, 0.25f);
        if (c == -2) return Ui.hsv(((now % 3000) / 3000f + row * 0.09f) % 1f, 0.75f, 1f);
        return c;
    }
}
