package com.punishfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Wheel & Case page: a tab for each mini-game, a live preview, then how it works and how it looks. */
public class GamesScreen extends PfxFrame {
    /** 0 = wheel, 1 = case. Remembered while the game is open. */
    public static int tab = 0;
    private int previewH;

    public GamesScreen(Screen parent) {
        super(parent, Page.GAMES, "Wheel & Case", "mini-games that pick the punishment");
    }

    private static String[] counts(int max) {
        String[] out = new String[max + 1];
        for (int i = 0; i <= max; i++) out[i] = i == 0 ? "None" : String.valueOf(i);
        return out;
    }

    private static String[] secs(int[] v) {
        String[] out = new String[v.length];
        for (int i = 0; i < v.length; i++) out[i] = v[i] + "s";
        return out;
    }

    private static final String[] USE_DESCS = {
            "Only when a level picks it (tick it on Punishments)",
            "Every donation spins it instead of a normal pick",
            "Donations of level 2 or higher", "Donations of level 3 or higher",
            "Donations of level 4 or higher", "Donations of level 5 or higher"};

    @Override
    protected void initPage() {
        Config c = Config.get();
        int tw = (cw - 4) / 2;
        addRenderableWidget(new FancyButton(cx, cy, tw, 16, Component.literal("Spin the Wheel"), b -> {
            tab = 0;
            Games.restartPreview();
            rebuild();
        }).accent(Ui.THEME).hot(() -> tab == 0));
        addRenderableWidget(new FancyButton(cx + tw + 4, cy, cw - tw - 4, 16, Component.literal("Case Opening"), b -> {
            tab = 1;
            Games.restartPreview();
            rebuild();
        }).accent(Ui.THEME).hot(() -> tab == 1));
        previewH = ch >= 260 ? 86 : ch >= 200 ? 62 : 52;
        int top = cy + 20 + previewH + 4;
        list(tab == 0 ? wheelRows(c) : caseRows(c), top, cy + ch);
    }

    private Action tryIt(Games.Kind k) {
        return new Action("Try it", "In a world: run it for real (level 1). Otherwise: replay the preview",
                () -> Minecraft.getInstance().player != null ? "Run it" : "Replay", Ui.WARN, () -> {
            Config.save();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.setScreen(null);
                PunishManager.force(k == Games.Kind.WHEEL ? "spin_wheel" : "case_open", 0);
            } else Games.restartPreview();
        });
    }

    private List<Row> wheelRows(Config c) {
        List<Row> r = new ArrayList<>();
        r.add(new Header("How it works"));
        r.add(new Choice("When it spins", "", Games.USE_NAMES, () -> c.wheelUse, v -> c.wheelUse = v, USE_DESCS));
        r.add(new Choice("What's on it", "", new String[]{"Level's list", "Everything"}, () -> c.wheelContents, v -> c.wheelContents = v,
                new String[]{"The punishments that level is set to use", "Every punishment (except the opt-in ones)"}));
        String[] slots = new String[Games.WHEEL_SLOTS.length];
        for (int i = 0; i < slots.length; i++) slots[i] = String.valueOf(Games.WHEEL_SLOTS[i]);
        r.add(new Choice("Slots", "How many slices the wheel has", slots, () -> c.wheelSlots, v -> c.wheelSlots = v));
        r.add(new Choice("\"NOTHING!\" slots", "Lucky slots: no punishment at all", counts(3), () -> c.wheelNothing, v -> c.wheelNothing = v));
        r.add(new Choice("\"SPIN AGAIN\" slots", "Spins once more (up to 3 times)", counts(2), () -> c.wheelAgain, v -> c.wheelAgain = v));
        r.add(new Choice("\"DOUBLE\" slots", "Two punishments at once", counts(2), () -> c.wheelDouble, v -> c.wheelDouble = v));
        r.add(new Choice("\"JACKPOT\" slot", "Three punishments, twice as long", new String[]{"Off", "On"}, () -> c.wheelJackpot, v -> c.wheelJackpot = v));
        r.add(new Choice("Luck", "", new String[]{"Fair", "Rigged", "Teasing"}, () -> c.wheelLuck, v -> c.wheelLuck = v,
                new String[]{"Every slot has the same chance", "Never lands on NOTHING!", "Fair, but crawls just past NOTHING!"}));
        r.add(new Choice("Punishment length", "How long what it lands on lasts", Games.LENGTH_NAMES, () -> c.wheelLength, v -> c.wheelLength = v));
        r.add(tryIt(Games.Kind.WHEEL));
        r.add(new Header("How it looks"));
        r.add(new Choice("Colours", "Colours of the slices", Games.WHEEL_COLOR_NAMES, () -> c.wheelColors, v -> c.wheelColors = v));
        r.add(new Choice("Rim", "The ring round the edge", Games.RIM_NAMES, () -> c.wheelRim, v -> c.wheelRim = v));
        r.add(new Choice("Labels", "What's written on each slice", new String[]{"Names", "Icons", "Icons + names"}, () -> c.wheelLabels, v -> c.wheelLabels = v));
        r.add(new Choice("Label font", "Font of the slice names", TopDonorsHud.FONT_NAMES, () -> c.wheelFont, v -> c.wheelFont = v));
        r.add(new Choice("Pointer", "Colour of the arrow at the top", Games.POINTER_NAMES, () -> c.wheelPointer, v -> c.wheelPointer = v));
        r.add(new Text("Title", "Text above it. {name} = who donated", () -> c.wheelTitle, v -> c.wheelTitle = v));
        r.add(new Toggle("Show who donated", "\"Steve paid $1M\" under the title", () -> c.wheelShowDonor, v -> c.wheelShowDonor = v));
        r.add(new Choice("Spin time", "How long it spins", secs(Games.SPIN_SECONDS), () -> c.wheelSpin, v -> c.wheelSpin = v));
        r.add(new Choice("Spin style", "", new String[]{"Smooth", "Bouncy", "Slow crawl"}, () -> c.wheelStyle, v -> c.wheelStyle = v,
                new String[]{"Slows down smoothly", "Overshoots, then rocks back", "Crawls slowly at the end"}));
        r.add(new Choice("Tick sound", "The click as slots pass the arrow", Games.TICK_NAMES, () -> c.wheelTick, v -> c.wheelTick = v));
        r.add(new Choice("Winner effect", "When it stops", new String[]{"Confetti", "Flash", "None"}, () -> c.wheelWin, v -> c.wheelWin = v));
        r.add(new Choice("Background", "Darken the game behind it", new String[]{"Clear", "Dim", "Dark"}, () -> c.wheelBg, v -> c.wheelBg = v));
        r.add(new Action("Move & resize", "Drag it anywhere, scroll to resize (full-size preview)", () -> "Open editor", Ui.GOOD,
                () -> Minecraft.getInstance().setScreen(new MoveScreen(this, GameRender.WHEEL_PIECE))));
        r.add(numbers("Size", "How big the wheel is", new double[]{0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 2.5, 3.0},
                v -> Math.round(v * 100) + "%", () -> c.wheelScale, v -> c.wheelScale = v));
        return r;
    }

    private List<Row> caseRows(Config c) {
        List<Row> r = new ArrayList<>();
        r.add(new Header("How it works"));
        r.add(new Choice("When it opens", "", Games.USE_NAMES, () -> c.caseUse, v -> c.caseUse = v, USE_DESCS));
        r.add(new Choice("What's inside", "", new String[]{"Level's list", "Everything"}, () -> c.caseContents, v -> c.caseContents = v,
                new String[]{"The punishments that level is set to use", "Every punishment (except the opt-in ones)"}));
        r.add(new Choice("Rarity", "", new String[]{"How bad it is", "Category", "Random"}, () -> c.caseRarity, v -> c.caseRarity = v,
                new String[]{"Worse punishments are rarer (red = brutal)", "Each category is one rarity", "Shuffled every case"}));
        r.add(new Choice("Odds", "", new String[]{"Real CS odds", "Even", "Generous"}, () -> c.caseOdds, v -> c.caseOdds = v,
                new String[]{"Blue 79.9%, purple 16%, pink 3.2%, red 0.64%, gold 0.26%", "Every item has the same chance", "Rare ones show up far more often"}));
        r.add(new Choice("Gold ★ item", "", new String[]{"Off", "Mega combo", "Double time", "The wheel"}, () -> c.caseGold, v -> c.caseGold = v,
                new String[]{"No gold item", "Three punishments at once", "One punishment, twice as long", "Unboxes a spin of the wheel"}));
        r.add(new Choice("\"NOTHING!\" items", "Blue items that do nothing (blue is the most common!)", counts(3), () -> c.caseSafe, v -> c.caseSafe = v));
        r.add(new Toggle("Rarer lasts longer", "Purple 1.25x, pink 1.5x, red 2x as long", () -> c.caseLonger, v -> c.caseLonger = v));
        r.add(tryIt(Games.Kind.CASE));
        r.add(new Header("How it looks"));
        r.add(new Text("Case name", "Text above it. {name} = who donated", () -> c.caseTitle, v -> c.caseTitle = v));
        r.add(new Toggle("Show who donated", "\"Steve paid $1M\" under the name", () -> c.caseShowDonor, v -> c.caseShowDonor = v));
        r.add(new Choice("Card style", "", new String[]{"CS", "Flat", "Minimal"}, () -> c.caseCard, v -> c.caseCard = v,
                new String[]{"Dark cards with a rarity glow and bar", "Cards filled with the rarity colour", "Plain cards, a thin rarity line"}));
        r.add(new Choice("Rarity colours", "", new String[]{"CS", "Theme", "Rainbow"}, () -> c.caseColors, v -> c.caseColors = v,
                new String[]{"Blue, purple, pink, red, gold", "Shades of your theme colour", "Green, blue, purple, red, gold"}));
        r.add(new Toggle("Icons", "A picture on each card", () -> c.caseIcons, v -> c.caseIcons = v));
        r.add(new Toggle("Names", "The punishment's name on each card", () -> c.caseNames, v -> c.caseNames = v));
        r.add(new Toggle("Show the odds", "Chance of each rarity under the reel", () -> c.caseShowOdds, v -> c.caseShowOdds = v));
        r.add(new Choice("Open time", "How long the reel spins", secs(Games.OPEN_SECONDS), () -> c.caseOpen, v -> c.caseOpen = v));
        r.add(new Choice("Reel style", "", new String[]{"CS", "Slam", "Tease"}, () -> c.caseStyle, v -> c.caseStyle = v,
                new String[]{"Fast, then slows down like CS", "Stops hard and fast", "Creeps up to the line, barely makes it"}));
        r.add(new Choice("Tick sound", "The click as cards pass the line", Games.TICK_NAMES, () -> c.caseTick, v -> c.caseTick = v));
        r.add(new Choice("Winner effect", "When it stops", new String[]{"Confetti", "Flash", "None"}, () -> c.caseWin, v -> c.caseWin = v));
        r.add(new Choice("Background", "Darken the game behind it", new String[]{"Clear", "Dim", "Dark"}, () -> c.caseBg, v -> c.caseBg = v));
        r.add(new Action("Move & resize", "Drag it anywhere, scroll to resize (full-size preview)", () -> "Open editor", Ui.GOOD,
                () -> Minecraft.getInstance().setScreen(new MoveScreen(this, GameRender.CASE_PIECE))));
        r.add(numbers("Size", "How big the case is", new double[]{0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 2.5, 3.0},
                v -> Math.round(v * 100) + "%", () -> c.caseScale, v -> c.caseScale = v));
        return r;
    }

    @Override
    protected void renderPageBackground(GuiGraphics g, int mouseX, int mouseY) {
        int y = cy + 20;
        Ui.card(g, cx, y, cw, previewH, Ui.tint(0x0C0A0E, 0.05f));
        for (int x = cx + 4; x < cx + cw - 4; x += 8)
            for (int yy = y + 4; yy < y + previewH - 4; yy += 8) g.fill(x, yy, x + 1, yy + 1, Ui.a(0.1f, 0xFFFFFF));
    }

    @Override
    protected void renderPage(GuiGraphics g, int mouseX, int mouseY) {
        Config c = Config.get();
        long now = System.currentTimeMillis();
        Games.Kind k = tab == 0 ? Games.Kind.WHEEL : Games.Kind.CASE;
        Games.Game demo = Games.demo(k, now);
        int y = cy + 20;
        int gw = k == Games.Kind.WHEEL ? GameRender.WHEEL_W : GameRender.CASE_W;
        int gh = k == Games.Kind.WHEEL ? GameRender.WHEEL_H : GameRender.caseH();
        // the wheel sits on the left with a summary beside it; the case is wide, so it gets the whole box
        int boxW = k == Games.Kind.WHEEL ? Math.min(cw / 2, previewH * gw / gh + 10) : cw;
        float fit = Math.min((boxW - 8) / (float) gw, (previewH - 6) / (float) gh);
        float px = cx + (boxW - gw * fit) / 2f, py = y + (previewH - gh * fit) / 2f;
        GameRender.draw(g, this.font, demo, px, py, fit, now, 1f, false);
        Games.frame(null, now);
        if (k == Games.Kind.WHEEL) {
            int tx = cx + boxW + 6, tw = cw - boxW - 12;
            int n = Games.WHEEL_SLOTS[c.wheelSlots];
            int sp = Math.min(n - 2, c.wheelNothing + c.wheelAgain + c.wheelDouble + c.wheelJackpot);
            String[] lines = {
                    n + " slots: " + (n - sp) + " punishments" + (sp > 0 ? ", " + sp + " special" : ""),
                    Games.SPIN_SECONDS[c.wheelSpin] + "s spin  ·  " + new String[]{"fair", "rigged", "teasing"}[c.wheelLuck],
                    "Spins: " + Games.USE_NAMES[c.wheelUse].toLowerCase(),
                    "Full size: Move & resize below"};
            int ly = y + Math.max(4, (previewH - lines.length * 11) / 2);
            for (int i = 0; i < lines.length; i++) {
                Ui.text(g, this.font, lines[i], tx, ly, i == lines.length - 1 ? 0x6E6064 : i == 0 ? 0xFFFFFF : 0xC8B4BA, tw);
                ly += 11;
            }
        }
    }
}
