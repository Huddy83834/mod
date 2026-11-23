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
                new Action("Move & resize", "Opens the screen layout editor: drag, resize and right-click every item", () -> "Layout", Ui.GOOD,
                        () -> Minecraft.getInstance().setScreen(new LayoutScreen(this))),
                numbers("Size", "How big the board is",
                        new double[]{0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 2.5, 3.0}, v -> Math.round(v * 100) + "%", () -> c.topScale, v -> c.topScale = v),
                new Choice("Counts donations from", "Which donations make up the totals", DonorBoard.PERIOD_NAMES,
                        () -> c.topPeriod, v -> c.topPeriod = v),
                new Choice("Style", "", new String[]{"Leaderboard", "Top donator only"}, () -> c.topStyle, v -> c.topStyle = v,
                        new String[]{"A list of your biggest donors", "Just #1, with a label like \"TOP DONATOR\""}),
                new Choice("How many people", "Leaderboard style: how many names it shows", counts, () -> c.topRows - 1, v -> c.topRows = v + 1),
                new Text("Top donator label", "Top donator style: the words next to their name", () -> c.topLabel, v -> c.topLabel = v),
                new Choice("Label goes", "Top donator style: where the label sits", new String[]{"Above name", "Before name", "After name"},
                        () -> c.topLabelPos, v -> c.topLabelPos = v),
                new Toggle("Crown", "Top donator style: a little gold crown", () -> c.topCrown, v -> c.topCrown = v),
                new Header("Text"),
                new Text("Title", "The heading at the top of the board", () -> c.topTitle, v -> c.topTitle = v),
                new Choice("Font", "Normal, thin, runes or illager",
                        TopDonorsHud.FONT_NAMES, () -> c.topFont, v -> c.topFont = v),
                new Toggle("Bold", "Thicker letters", () -> c.topBold, v -> c.topBold = v),
                new Toggle("Italic", "Slanted letters", () -> c.topItalic, v -> c.topItalic = v),
                new Toggle("Text shadow", "Easier to read on bright skies", () -> c.topShadow, v -> c.topShadow = v),
                new Choice("Text colour", "Colour of the names",
                        TopDonorsHud.COLOR_NAMES, () -> c.topColor, v -> c.topColor = v),
                new Toggle("Medal colours", "Gold, silver and bronze for the top three", () -> c.topMedals, v -> c.topMedals = v),
                new Choice("Amounts", "", new String[]{"$1.2M", "$1,200,000", "Hidden"}, () -> c.topAmounts, v -> c.topAmounts = v,
                        new String[]{"Short amounts", "Full amounts", "Don't show how much anyone spent"}),
                new Header("Box"),
                new Choice("Background", "What's behind the text", TopDonorsHud.BG_NAMES, () -> c.topBackground, v -> c.topBackground = v),
                new Toggle("Hide when empty", "Don't show the board until someone donates", () -> c.topHideEmpty, v -> c.topHideEmpty = v),
                new Header("Data"),
                new Action("Reset the leaderboard", "Clears every total. Can't be undone.",
                        () -> confirmReset ? "Sure? Click" : "Reset", Ui.THEME, () -> {
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
