package com.punishfx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Giveaways: people pay the entry price to get in, then the winner is drawn on screen with the wheel, a case,
 * an elimination or a raffle. Saved to disk on every change so a crash or disconnect loses no entries.
 */
public final class Giveaway {
    public static final String[] METHOD_NAMES = {"Wheel", "Case", "Elimination", "Raffle", "Surprise me"};
    public static final String[] METHOD_DESCS = {
            "A wheel with a slice for each ticket",
            "A CS case that unboxes the winner",
            "Names get knocked out one by one",
            "Names roll past on a ticket until it stops",
            "A different one each time"};
    public static final int[] DRAW_SECONDS = {5, 8, 10, 15, 20};
    public static final int[] MAX_TICKETS = {1, 3, 5, 10, 1000};
    public static final String[] MAX_TICKET_NAMES = {"1", "3", "5", "10", "No limit"};

    public static final class Entrant {
        public String name;
        public int tickets;
        public double paid;
        public long first;
    }

    public static final class State {
        public long start;
        public double price;
        public String prize = "";
        public int prizeMode;
        public boolean drawing;
        public Map<String, Entrant> entrants = new LinkedHashMap<>();
    }

    private static final class Saved {
        State live;
        String lastWinner;
        String lastPrize;
        double lastPrizeAmount;
        int lastEntrants;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Random R = new Random();
    private static State live;
    private static String lastWinner, lastPrize;
    private static double lastPrizeAmount = -1;
    private static int lastEntrants;

    private Giveaway() {}

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("punishfx-giveaway.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (!Files.exists(p)) return;
            Saved s = GSON.fromJson(Files.readString(p), Saved.class);
            if (s == null) return;
            live = s.live;
            if (live != null) {
                if (live.entrants == null) live.entrants = new LinkedHashMap<>();
                live.drawing = false; // a draw that was cut off can be run again
            }
            lastWinner = s.lastWinner;
            lastPrize = s.lastPrize;
            lastPrizeAmount = s.lastPrizeAmount;
            lastEntrants = s.lastEntrants;
        } catch (Exception e) {
            PunishFxClient.LOG.warn("Punish FX: could not read the giveaway file", e);
        }
    }

    private static void save() {
        try {
            Saved s = new Saved();
            s.live = live;
            s.lastWinner = lastWinner;
            s.lastPrize = lastPrize;
            s.lastPrizeAmount = lastPrizeAmount;
            s.lastEntrants = lastEntrants;
            Files.writeString(path(), GSON.toJson(s));
        } catch (Exception e) {
            PunishFxClient.LOG.warn("Punish FX: could not save the giveaway", e);
        }
    }

    // ------------------------------------------------------------------ state

    public static boolean live() {
        return live != null;
    }

    public static boolean drawing() {
        return live != null && live.drawing;
    }

    public static State state() {
        return live;
    }

    public static String lastWinner() {
        return lastWinner;
    }

    public static String lastPrize() {
        return lastPrize;
    }

    public static int count() {
        return live == null ? 0 : live.entrants.size();
    }

    public static int tickets() {
        int t = 0;
        if (live != null) for (Entrant e : live.entrants.values()) t += e.tickets;
        return t;
    }

    public static double pot() {
        double t = 0;
        if (live != null) for (Entrant e : live.entrants.values()) t += e.paid;
        return t;
    }

    /** What the winner gets, as text. */
    public static String prizeText() {
        if (live == null) return "";
        return switch (live.prizeMode) {
            case 1 -> Money.compact(pot()) + " (the pot)";
            case 2 -> Money.compact(pot() / 2) + " (half the pot)";
            default -> live.prize == null ? "" : live.prize;
        };
    }

    /** What the winner gets in money, if it is money (-1 if not). */
    public static double prizeAmount() {
        if (live == null) return -1;
        return switch (live.prizeMode) {
            case 1 -> pot();
            case 2 -> Math.floor(pot() / 2);
            default -> Money.parse(live.prize);
        };
    }

    /** Recent names, newest first. */
    public static List<String> latest(int n) {
        List<String> out = new ArrayList<>();
        if (live == null) return out;
        List<Entrant> all = new ArrayList<>(live.entrants.values());
        all.sort((a, b) -> Long.compare(b.first, a.first));
        for (int i = 0; i < Math.min(n, all.size()); i++) out.add(all.get(i).name);
        return out;
    }

    // ------------------------------------------------------------------ running one

    public static void start() {
        Config c = Config.get();
        live = new State();
        live.start = System.currentTimeMillis();
        live.price = Math.max(1, c.gwPrice);
        live.prize = c.gwPrize == null ? "" : c.gwPrize;
        live.prizeMode = c.gwPrizeMode;
        save();
        PunishManager.showBanner("GIVEAWAY STARTED - pay " + Money.full(live.price) + " to enter!");
    }

    public static void cancel() {
        live = null;
        save();
    }

    /**
     * A payment arrived. Returns true if it entered (or added to) the giveaway - the caller then skips
     * punishing it unless "Entries also punish" is on.
     */
    public static boolean payment(String name, double amount) {
        if (live == null || live.drawing || name == null || name.isBlank() || name.equals(Detector.UNKNOWN)) return false;
        if (amount + 0.001 < live.price) return false;
        Config c = Config.get();
        String key = name.toLowerCase(Locale.ROOT);
        Entrant e = live.entrants.get(key);
        boolean fresh = e == null;
        if (fresh) {
            e = new Entrant();
            e.name = name;
            e.first = System.currentTimeMillis();
            live.entrants.put(key, e);
        }
        int max = MAX_TICKETS[c.gwMaxTickets];
        int add = c.gwMoreTickets ? (int) Math.floor((amount + 0.001) / live.price) : (fresh ? 1 : 0);
        int before = e.tickets;
        e.tickets = Math.min(max, Math.max(1, e.tickets + add));
        e.paid += amount;
        save();
        String sub;
        if (fresh) sub = "entered the giveaway!  ·  " + count() + (count() == 1 ? " entry" : " entries");
        else if (e.tickets > before) sub = "now has " + e.tickets + " tickets in the giveaway";
        else sub = "is already in the giveaway";
        PunishManager.announceEntry(name, amount, sub);
        return true;
    }

    /** Draw the winner now (weighted by tickets). Returns false if nobody has entered. */
    public static boolean draw() {
        if (live == null || live.entrants.isEmpty() || live.drawing) return false;
        Config c = Config.get();
        List<String> names = new ArrayList<>();
        List<Integer> tickets = new ArrayList<>();
        for (Entrant e : live.entrants.values()) {
            names.add(e.name);
            tickets.add(Math.max(1, e.tickets));
        }
        int winner = pick(tickets, R);
        int m = c.gwMethod == 4 ? R.nextInt(4) : c.gwMethod;
        Games.Kind kind = new Games.Kind[]{Games.Kind.WHEEL, Games.Kind.CASE, Games.Kind.ELIMINATION, Games.Kind.RAFFLE}[m];
        live.drawing = true;
        lastPrizeAmount = prizeAmount();
        lastPrize = prizeText();
        Games.requestBuilt(Games.buildGiveaway(kind, names, tickets, winner, lastPrize, DRAW_SECONDS[c.gwDrawTime], R.nextLong()));
        save();
        return true;
    }

    /** Picks an index, each weighted by its tickets (public for the tests). */
    public static int pick(List<Integer> tickets, Random r) {
        int total = 0;
        for (int t : tickets) total += Math.max(1, t);
        int x = r.nextInt(total);
        for (int i = 0; i < tickets.size(); i++) {
            x -= Math.max(1, tickets.get(i));
            if (x < 0) return i;
        }
        return tickets.size() - 1;
    }

    /** The draw landed: remember the winner and tell you how to pay them. */
    static void finished(String winner) {
        lastWinner = winner;
        lastEntrants = count();
        live = null;
        save();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        MutableComponent msg = Component.literal("[Punish FX] ").withStyle(st -> st.withColor(0xFF4A6A))
                .append(Component.literal(winner + " won the giveaway!").withStyle(st -> st.withColor(0xFFD700).withBold(true)));
        if (lastPrize != null && !lastPrize.isBlank()) msg.append(Component.literal(" Prize: " + lastPrize).withStyle(st -> st.withColor(0xFFFFFF)));
        String cmd = payCommand();
        if (cmd != null) {
            msg.append(Component.literal("  [Click to pay them]").withStyle(st -> st.withColor(0x6BFF9E).withUnderlined(true)
                    .withClickEvent(new ClickEvent.SuggestCommand(cmd))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Puts " + cmd + " in your chat - press Enter to send it")))));
        }
        mc.gui.getChat().addMessage(msg);
    }

    /** "/pay Steve 1000000" for the last winner, or null if the prize isn't money. */
    public static String payCommand() {
        if (lastWinner == null || lastPrizeAmount <= 0) return null;
        String t = Config.get().gwPayCommand;
        if (t == null || t.isBlank()) t = "/pay {name} {amount}";
        String amt = lastPrizeAmount == Math.floor(lastPrizeAmount) ? String.valueOf((long) lastPrizeAmount) : String.valueOf(lastPrizeAmount);
        return t.replace("{name}", lastWinner).replace("{amount}", amt);
    }
}
