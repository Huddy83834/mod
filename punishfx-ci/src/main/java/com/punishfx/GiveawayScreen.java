package com.punishfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Giveaway page: open one, set the entry price and prize, pick how the winner is drawn. */
public class GiveawayScreen extends PfxFrame {
    private static boolean confirmCancel = false;
    private static String note = "";
    private static long noteUntil = 0;
    private int statusH;

    public GiveawayScreen(Screen parent) {
        super(parent, Page.GIVEAWAY, "Giveaway", "people pay to enter, then the winner is drawn on screen");
    }

    private static void say(String s) {
        note = s;
        noteUntil = System.currentTimeMillis() + 4000;
    }

    /** Draw for real (closes the menu so you can watch). */
    private void drawNow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            say("Join a world or server first - the draw shows on your screen in game");
            return;
        }
        Config.save();
        String why = Giveaway.draw();
        if (why != null) {
            say(why);
            return;
        }
        mc.setScreen(null);
    }

    /** A pretend draw with made-up names, so you can see how it looks. */
    private void preview() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            say("Join a world first to see the preview");
            return;
        }
        Config c = Config.get();
        Config.save();
        List<String> names = List.of("Steve", "Alex", "Notch", "Jeb_", "Dinnerbone", "Grian", "Mumbo", "Kai", "Zuri", "Sunny", "Ari", "Efe");
        List<Integer> tickets = List.of(1, 3, 1, 2, 1, 1, 5, 1, 1, 2, 1, 1);
        Random r = new Random();
        int m = c.gwMethod == 4 ? r.nextInt(4) : c.gwMethod;
        Games.Kind kind = new Games.Kind[]{Games.Kind.WHEEL, Games.Kind.CASE, Games.Kind.ELIMINATION, Games.Kind.RAFFLE}[m];
        Games.Game g = Games.buildGiveaway(kind, names, tickets, Giveaway.pick(tickets, r), "$10M (preview)", Giveaway.DRAW_SECONDS[c.gwDrawTime], r.nextLong());
        g.preview = true;
        Games.requestBuilt(g);
        mc.setScreen(null);
    }

    private static final java.time.format.DateTimeFormatter WHEN = java.time.format.DateTimeFormatter.ofPattern("d MMM, h:mm a", java.util.Locale.ENGLISH);
    private static boolean confirmClear = false;

    private void reroll(int place) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            say("Join a world first - the reroll is drawn on your screen");
            return;
        }
        String why = Giveaway.reroll(place);
        if (why != null) say(why);
        else mc.setScreen(null);
    }

    @Override
    protected void initPage() {
        Config c = Config.get();
        statusH = ch >= 200 ? 40 : 34;
        List<Row> r = new ArrayList<>();
        r.add(new Header("Giveaway"));
        if (Giveaway.live()) {
            int need = Math.max(1, Giveaway.minEntries()) - Giveaway.count();
            r.add(new Pair("Pick the winner" + (c.gwWinners > 1 ? "s" : ""), need > 0 ? "Needs " + need + " more " + (need == 1 ? "entry" : "entries") + " first"
                    : "Draws now with the " + Giveaway.METHOD_NAMES[c.gwMethod].toLowerCase(),
                    () -> "Draw!", Ui.GOOD, this::drawNow,
                    () -> confirmCancel ? "Sure?" : "Cancel", Ui.THEME, () -> {
                if (!confirmCancel) {
                    confirmCancel = true;
                    return;
                }
                confirmCancel = false;
                Giveaway.cancel();
                say("Giveaway cancelled");
                rebuild();
            }));
        } else {
            r.add(new Action("Open a giveaway", "People enter until you press Draw", () -> "Start", Ui.GOOD, () -> {
                Config.save();
                Giveaway.start();
                say("Giveaway open!");
                rebuild();
            }));
        }

        r.add(new Header("Entering"));
        r.add(new Choice("How to enter", "", Giveaway.ENTRY_NAMES, () -> c.gwEntryMode, v -> c.gwEntryMode = v,
                new String[]{"Pay the entry price", "Type the join word in chat - free", "Pay, or type the join word for free"}));
        r.add(new Text("Entry price", "Pay this much (or more) to get in", () -> Money.full(c.gwPrice), v -> {
            double p = Money.parse(v);
            if (p > 0) c.gwPrice = p;
        }));
        r.add(new Text("Join word", "What players type in chat to enter", () -> c.gwJoinWord, v -> c.gwJoinWord = v));
        r.add(new Toggle("Pay more, more tickets", "Paying 3x the price gives 3 tickets (better odds)", () -> c.gwMoreTickets, v -> c.gwMoreTickets = v));
        r.add(new Choice("Most tickets each", "With more tickets on: the most one person can have", Giveaway.MAX_TICKET_NAMES,
                () -> c.gwMaxTickets, v -> c.gwMaxTickets = v));
        r.add(new Toggle("Entries also punish", "Entry payments set off punishments too", () -> c.gwPunish, v -> c.gwPunish = v));

        r.add(new Header("Prizes"));
        r.add(new Choice("Winners", "", new String[]{"1 winner", "1st + 2nd", "1st, 2nd + 3rd"}, () -> c.gwWinners - 1, v -> c.gwWinners = v + 1,
                new String[]{"One winner", "Two places, drawn 2nd then 1st", "Three places, drawn 3rd, 2nd, then 1st"}));
        r.add(new Choice("Prize is", "", new String[]{"What you type", "The whole pot", "Half the pot"}, () -> c.gwPrizeMode, v -> c.gwPrizeMode = v,
                new String[]{"Whatever you write below", "All the entry money (split 50/30/20 for 3 places)", "Half the entry money (split the same way)"}));
        r.add(new Text("1st place prize", "e.g. $10M, a diamond sword, a shoutout", () -> c.gwPrize, v -> c.gwPrize = v));
        if (c.gwWinners >= 2) r.add(new Text("2nd place prize", "What 2nd place gets", () -> c.gwPrize2, v -> c.gwPrize2 = v));
        if (c.gwWinners >= 3) r.add(new Text("3rd place prize", "What 3rd place gets", () -> c.gwPrize3, v -> c.gwPrize3 = v));
        r.add(new Text("Pay command", "{name} and {amount} get filled in", () -> c.gwPayCommand, v -> c.gwPayCommand = v));

        r.add(new Header("Rules"));
        r.add(new Choice("Minimum entries", "Can't draw until this many are in", Giveaway.MIN_ENTRY_NAMES, () -> c.gwMinEntries, v -> c.gwMinEntries = v));
        r.add(new Text("Banned players", "Names, separated by commas", () -> c.gwBanned, v -> c.gwBanned = v));
        r.add(new Toggle("Can't win twice in a row", "Last giveaway's winners can't enter the next one", () -> c.gwNoRepeat, v -> c.gwNoRepeat = v));

        r.add(new Header("The draw"));
        r.add(new Choice("Pick the winner with", "", Giveaway.METHOD_NAMES, () -> c.gwMethod, v -> c.gwMethod = v, Giveaway.METHOD_DESCS));
        String[] secs = new String[Giveaway.DRAW_SECONDS.length];
        for (int i = 0; i < secs.length; i++) secs[i] = Giveaway.DRAW_SECONDS[i] + "s";
        r.add(new Choice("Draw time", "How long each draw takes", secs, () -> c.gwDrawTime, v -> c.gwDrawTime = v));
        r.add(new Action("Preview the draw", "A pretend draw with made-up names (in game)", () -> "Preview", Ui.WARN, this::preview));
        r.add(new Action("Wheel & case look", "Colours, rim, sounds... are on the Wheel & Case page", () -> "Open", Ui.THEME2,
                () -> Minecraft.getInstance().setScreen(open(parent, Page.GAMES))));

        Giveaway.Record last = Giveaway.last();
        if (last != null) {
            r.add(new Header("Last giveaway"));
            for (Giveaway.Place pl : last.places) {
                if (pl.name == null) continue;
                String who = (last.places.size() > 1 ? Giveaway.PLACES[pl.place - 1] + ": " : "Winner: ") + pl.name
                        + (Giveaway.online(pl.name) ? "" : "  (offline)");
                String desc = (pl.prize == null || pl.prize.isBlank() ? "No prize set" : "Prize: " + pl.prize)
                        + (pl.rerolled.isEmpty() ? "" : "  \u00b7  rerolled from " + String.join(", ", pl.rerolled));
                r.add(new Pair(who, desc, () -> "Copy /pay", Ui.THEME2, () -> {
                    String cmd = Giveaway.payCommand(pl);
                    if (cmd == null) {
                        say("That prize isn't money");
                        return;
                    }
                    Minecraft.getInstance().keyboardHandler.setClipboard(cmd);
                    say("Copied " + cmd + " - paste it in chat");
                }, () -> "Reroll", Ui.WARN, () -> reroll(pl.place)));
            }
        }
        if (!Giveaway.history().isEmpty()) {
            r.add(new Header("History"));
            List<Giveaway.Record> hist = new ArrayList<>(Giveaway.history());
            java.util.Collections.reverse(hist);
            for (int i = 0; i < Math.min(10, hist.size()); i++) {
                Giveaway.Record h = hist.get(i);
                List<String> won = new ArrayList<>();
                for (Giveaway.Place pl : h.places) if (pl.name != null) won.add(pl.name + (pl.prize == null || pl.prize.isBlank() ? "" : " (" + pl.prize + ")"));
                String when = WHEN.format(java.time.Instant.ofEpochMilli(h.end > 0 ? h.end : h.start).atZone(java.time.ZoneId.systemDefault()));
                r.add(new Info(when + "  \u00b7  " + String.join(", ", won),
                        h.entrants + " entrants  \u00b7  " + h.tickets + " tickets  \u00b7  brought in " + Money.compact(h.pot) + "  \u00b7  " + h.method, Ui.WARN));
            }
            r.add(new Action("Clear history", "Forget past giveaways (can't be undone)", () -> confirmClear ? "Sure?" : "Clear", Ui.THEME, () -> {
                if (!confirmClear) {
                    confirmClear = true;
                    return;
                }
                confirmClear = false;
                Giveaway.clearHistory();
                rebuild();
            }));
        }

        r.add(new Header("On screen"));
        r.add(new Toggle("Show the giveaway box", "Prize, how to enter and who's in, while it's open", () -> c.gwHud, v -> c.gwHud = v));
        r.add(new Action("Move & resize", "Drag it anywhere, scroll to resize", () -> "Open editor", Ui.GOOD,
                () -> Minecraft.getInstance().setScreen(new MoveScreen(this, GiveawayHud.PIECE))));
        r.add(numbers("Size", "How big the giveaway box is", new double[]{0.5, 0.75, 1.0, 1.25, 1.5, 2.0},
                v -> Math.round(v * 100) + "%", () -> c.gwScale, v -> c.gwScale = v));
        list(r, cy + statusH + 4, cy + ch);
    }

    @Override
    protected void renderPageBackground(GuiGraphics g, int mouseX, int mouseY) {
        Ui.card(g, cx, cy, cw, statusH, Ui.tint(0x0C0A0E, 0.05f));
        g.fill(cx, cy, cx + 2, cy + statusH, Ui.a(1f, Giveaway.live() ? 0xFFD24A : 0x5E5256));
    }

    @Override
    protected void renderPage(GuiGraphics g, int mouseX, int mouseY) {
        int x = cx + 10, y = cy + (statusH - 22) / 2;
        if (System.currentTimeMillis() < noteUntil) {
            Ui.text(g, this.font, note, x, y + 6, Ui.GOOD, cw - 20);
            return;
        }
        if (Giveaway.live()) {
            g.drawString(this.font, Giveaway.drawing() ? "DRAWING..." : "OPEN", x, y, Ui.a(1f, 0xFFD24A), false);
            int n = Giveaway.count();
            String s = n + (n == 1 ? " entered" : " entered") + "  ·  " + Giveaway.tickets() + " tickets  ·  pot " + Money.compact(Giveaway.pot())
                    + "  ·  entry " + Money.compact(Giveaway.state().price);
            Ui.text(g, this.font, s, x + 44, y, 0xFFFFFF, cw - 64);
            Giveaway.State st = Giveaway.state();
            String p = Giveaway.prizeText(1);
            String how = st.entryMode == 1 ? "type " + st.joinWord : st.entryMode == 2 ? "pay or type " + st.joinWord : "pay to enter";
            String prizes = st.winners > 1 ? "1st " + p + ", 2nd " + Giveaway.prizeText(2) + (st.winners > 2 ? ", 3rd " + Giveaway.prizeText(3) : "")
                    : p.isBlank() ? "No prize set" : "Prize: " + p;
            Ui.text(g, this.font, prizes + "  \u00b7  " + how, x, y + 12, 0xC8B4BA, cw - 20);
        } else {
            g.drawString(this.font, "Closed", x, y, Ui.a(1f, 0xFFFFFF), false);
            String w = Giveaway.lastWinner();
            Ui.text(g, this.font, w == null ? "Press Start (or /pfx giveaway start) to open one" : "Last winner: " + w
                    + (Giveaway.lastPrize() == null || Giveaway.lastPrize().isBlank() ? "" : "  ·  " + Giveaway.lastPrize()), x, y + 12, 0x9A8F94, cw - 20);
        }
    }
}
