package com.punishfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/** Top Donors page: a live preview of the board, then everything about how it looks. */
public class TopDonorsScreen extends PfxFrame {
    private static boolean confirmReset = false;
    private int previewH;

    public TopDonorsScreen(Screen parent) {
        super(parent, Page.TOP_DONORS, "Top Donors", "a leaderboard on your screen");
    }

    @Override
    protected void initPage() {
        Config c = Config.get();
        previewH = ch >= 200 ? 66 : 50;
        String[] counts = new String[10];
        for (int i = 0; i < 10; i++) counts[i] = String.valueOf(i + 1);
        list(List.of(
                new Header("Board"),
                new Toggle("Show on screen", "Show the leaderboard while you play", () -> c.topShow, v -> c.topShow = v),
                new Action("Move & resize", "Drag it anywhere on screen and scroll to make it bigger or smaller", () -> "Open editor", Ui.GOOD,
                        () -> Minecraft.getInstance().setScreen(new MoveScreen(this))),
                numbers("Size", "How big the board is (you can also scroll in the editor)",
                        new double[]{0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 2.5, 3.0}, v -> Math.round(v * 100) + "%", () -> c.topScale, v -> c.topScale = v),
                new Choice("Counts donations from", "Which donations make up the totals", DonorBoard.PERIOD_NAMES,
                        () -> c.topPeriod, v -> c.topPeriod = v),
                new Choice("How many names", "Rows on the board", counts, () -> c.topRows - 1, v -> c.topRows = v + 1),
                new Header("Text"),
                new Text("Title", "The heading at the top of the board", () -> c.topTitle, v -> c.topTitle = v),
                new Choice("Font", "Minecraft fonts: normal, thin, enchanting runes or illager",
                        TopDonorsHud.FONT_NAMES, () -> c.topFont, v -> c.topFont = v),
                new Toggle("Bold", "Thicker letters", () -> c.topBold, v -> c.topBold = v),
                new Toggle("Italic", "Slanted letters", () -> c.topItalic, v -> c.topItalic = v),
                new Toggle("Text shadow", "A dark shadow so it reads on bright backgrounds", () -> c.topShadow, v -> c.topShadow = v),
                new Choice("Text colour", "Colour of the names (Theme follows your theme colour)",
                        TopDonorsHud.COLOR_NAMES, () -> c.topColor, v -> c.topColor = v),
                new Toggle("Medal colours", "Gold, silver and bronze for the top three", () -> c.topMedals, v -> c.topMedals = v),
                new Choice("Amounts", "How money is written", new String[]{"$1.2M", "$1,200,000"}, () -> c.topAmounts, v -> c.topAmounts = v),
                new Header("Box"),
                new Choice("Background", "What's behind the text", TopDonorsHud.BG_NAMES, () -> c.topBackground, v -> c.topBackground = v),
                new Toggle("Hide when empty", "Don't show the board until someone donates", () -> c.topHideEmpty, v -> c.topHideEmpty = v),
                new Header("Data"),
                new Action("Reset the leaderboard", "Clears all time, today and this session. Can't be undone.",
                        () -> confirmReset ? "Sure? Click" : "Reset", Ui.ACCENT, () -> {
                    if (!confirmReset) {
                        confirmReset = true;
                        return;
                    }
                    confirmReset = false;
                    DonorBoard.reset();
                })
        ), cy + previewH + 4, cy + ch);
    }

    @Override
    protected void renderPageBackground(GuiGraphics g, int mouseX, int mouseY) {
        // preview box: the real board, scaled to fit
        Ui.card(g, cx, cy, cw, previewH, Ui.tint(0x0C0A0E, 0.05f));
        for (int x = cx + 4; x < cx + cw - 4; x += 8)
            for (int y = cy + 4; y < cy + previewH - 4; y += 8) g.fill(x, y, x + 1, y + 1, Ui.a(0.12f, 0xFFFFFF));
    }

    @Override
    protected void renderPage(GuiGraphics g, int mouseX, int mouseY) {
        Config c = Config.get();
        // measure at scale 1, then fit inside the preview box
        double saved = c.topScale;
        c.topScale = 1.0;
        int[] b = TopDonorsHud.bounds(this.font, 10000, 10000, true);
        c.topScale = saved;
        float fit = Math.min((float) saved, Math.min((cw - 16) / (float) b[2], (previewH - 10) / (float) b[3]));
        float w = b[2] * fit, h = b[3] * fit;
        TopDonorsHud.drawAt(g, this.font, cx + (cw - w) / 2f, cy + (previewH - h) / 2f, fit, true);
        String tag = DonorBoard.size(c.topPeriod) == 0 ? "preview (example names)" : "preview";
        g.drawString(this.font, tag, cx + 5, cy + previewH - 11, Ui.a(0.6f, 0xFFFFFF), false);
    }
}
