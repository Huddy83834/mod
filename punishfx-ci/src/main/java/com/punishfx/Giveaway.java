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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Giveaways: people pay the entry price (or type the join word in chat) to get in, then 1st, 2nd and 3rd
 * place are drawn on screen with the wheel, a case, an elimination or a raffle. Saved to disk on every change
 * so a crash or disconnect loses nothing; past giveaways are kept as history and any place can be rerolled.
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
    public static final String[] ENTRY_NAMES = {"Pay", "Type in chat", "Either"};
    public static final int[] MIN_ENTRIES = {1, 2, 3, 5, 10, 20};
    public static final String[] MIN_ENTRY_NAMES = {"No minimum", "2", "3", "5", "10", "20"};
    public static final String[] PLACES = {"1st", "2nd", "3rd"};
    /** How the pot is split between the places (for "the whole pot" / "half the pot"). */
    private static final double[][] SPLIT = {{1.0}, {0.6, 0.4}, {0.5, 0.3, 0.2}};

    public static final class Entrant {
        public String name;
        public int tickets;
        public double paid;
        public long first;
        public boolean free;
    }

    public static final class State {
        public long start;
        public double price;
        public String prize = "", prize2 = "", prize3 = "";
        public int prizeMode, winners = 1, entryMode;
        public String joinWord = "!join";
        public boolean drawing;
        public Map<String, Entrant> entrants = new LinkedHashMap<>();
    }

    /** One place in a finished (or finishing) giveaway. */
    public static final class Place {
        public int place;
        public String name;
        public String prize = "";
        public double amount = -1;
        public List<String> rerolled = new ArrayList<>();
    }

    /** A giveaway that has been drawn. */
    public static final class Record {
        public long start, end;
        public int entrants, tickets;
        public double pot;
        public String method = "";
        public List<Place> places = new ArrayList<>();
        public Map<String, Integer> pool = new LinkedHashMap<>(); // name -> tickets, for rerolls
    }

    private static final class Saved {
        State live;
        List<Record> history = new ArrayList<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Random R = new Random();
    private static State live;
    private static final List<Record> HISTORY = new ArrayList<>();
    /** The giveaway being drawn right now (its places fill in as each draw lands). */
    private static Record drawingRecord;

    private Giveaway() {}

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("punishfx-giveaway.json");
    }

    public static void load() {
        HISTORY.clear();
        live = null;
        drawingRecord = null;
        try {
            Path p = path();
            if (!Files.exists(p)) return;
            Saved s = GSON.fromJson(Files.readString(p), Saved.class);
            if (s == null) return;
            live = s.live;
            if (live != null) {
                if (live.entrants == null) live.entrants = new LinkedHashMap<>();
                live.drawing = false; // a draw that was cut off can just be run again
            }
            if (s.history != null) for (Record r : s.history) if (r != null && r.places != null) HISTORY.add(r);
        } catch (Exception e) {
            PunishFxClient.LOG.warn("Punish FX: could not read the giveaway file", e);
        }
    }

    private static void save() {
        try {
            Saved s = new Saved();
            s.live = live;
            s.history = new ArrayList<>(HISTORY);
            while (s.history.size() > 50) s.history.remove(0);
            Files.writeString(path(), GSON.toJson(s));
        } catch (Exception e) {
            PunishFxClient.LOG.warn("Punish FX: could not save the giveaway", e);
        }
    }

    // ------------------------------------------------------------------ reading the state

    /** Settings changed on the Giveaway page apply straight away, even to an open giveaway. */
    private static void sync() {
        if (live == null || live.drawing) return;
        Config c = Config.get();
        live.price = Math.max(1, c.gwPrice);
        live.prize = c.gwPrize == null ? "" : c.gwPrize;
        live.prize2 = c.gwPrize2 == null ? "" : c.gwPrize2;
        live.prize3 = c.gwPrize3 == null ? "" : c.gwPrize3;
        live.prizeMode = c.gwPrizeMode;
        live.winners = c.gwWinners;
        live.entryMode = c.gwEntryMode;
        live.joinWord = c.gwJoinWord;
    }

    public static boolean live() {
        return live != null;
    }

    public static boolean drawing() {
        return live != null && live.drawing;
    }

    public static State state() {
        sync();
        return live;
    }

    public static List<Record> history() {
        return HISTORY;
    }

    public static Record last() {
        return HISTORY.isEmpty() ? null : HISTORY.get(HISTORY.size() - 1);
    }

    public static String lastWinner() {
        Record r = last();
        return r == null || r.places.isEmpty() ? null : r.places.get(0).name;
    }

    public static String lastPrize() {
        Record r = last();
        return r == null || r.places.isEmpty() ? null : r.places.get(0).prize;
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

    public static int minEntries() {
        return MIN_ENTRIES[Config.get().gwMinEntries];
    }

    /** What a place wins, as text (place 1..3). */
    public static String prizeText(int place) {
        if (live == null) return "";
        sync();
        int n = Math.max(1, Math.min(3, live.winners));
        if (live.prizeMode == 0) {
            String p = place == 1 ? live.prize : place == 2 ? live.prize2 : live.prize3;
            return p == null ? "" : p;
        }
        double a = prizeAmount(place);
        return Money.compact(a) + (n == 1 ? (live.prizeMode == 1 ? " (the pot)" : " (half the pot)") : "");
    }

    public static String prizeText() {
        return prizeText(1);
    }

    /** What a place wins in money, if it's money (-1 if not). */
    public static double prizeAmount(int place) {
        if (live == null) return -1;
        sync();
        int n = Math.max(1, Math.min(3, live.winners));
        if (live.prizeMode == 0) return Money.parse(place == 1 ? live.prize : place == 2 ? live.prize2 : live.prize3);
        double total = live.prizeMode == 1 ? pot() : pot() / 2;
        return Math.floor(total * SPLIT[n - 1][Math.min(n, place) - 1]);
    }

    public static double prizeAmount() {
        return prizeAmount(1);
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

    // ------------------------------------------------------------------ rules

    private static Set<String> banned() {
        Set<String> out = new HashSet<>();
        for (String s : Config.get().gwBanned.split("[,;\\s]+")) if (!s.isBlank()) out.add(s.toLowerCase(Locale.ROOT));
        return out;
    }

    /** Why this person can't enter, or null if they can. */
    public static String blocked(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (banned().contains(key)) return "is banned from giveaways";
        Config c = Config.get();
        Record r = last();
        if (c.gwNoRepeat && r != null)
            for (Place p : r.places) if (p.name != null && p.name.equalsIgnoreCase(name)) return "won last time - sitting this one out";
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getGameProfile().name().equalsIgnoreCase(name)) return "can't enter their own giveaway";
        return null;
    }

    // ------------------------------------------------------------------ running one

    public static void start() {
        Config c = Config.get();
        live = new State();
        live.start = System.currentTimeMillis();
        live.price = Math.max(1, c.gwPrice);
        live.prize = c.gwPrize == null ? "" : c.gwPrize;
        live.prize2 = c.gwPrize2 == null ? "" : c.gwPrize2;
        live.prize3 = c.gwPrize3 == null ? "" : c.gwPrize3;
        live.prizeMode = c.gwPrizeMode;
        live.winners = c.gwWinners;
        live.entryMode = c.gwEntryMode;
        live.joinWord = c.gwJoinWord;
        save();
        String how = live.entryMode == 1 ? "type " + live.joinWord + " in chat"
                : live.entryMode == 2 ? "pay " + Money.full(live.price) + " or type " + live.joinWord
                : "pay " + Money.full(live.price);
        PunishManager.showBanner("GIVEAWAY OPEN - " + how + " to enter!");
    }

    public static void cancel() {
        live = null;
        drawingRecord = null;
        save();
    }

    private static Entrant enter(String name, double paid, int tickets, boolean free) {
        String key = name.toLowerCase(Locale.ROOT);
        Entrant e = live.entrants.get(key);
        if (e == null) {
            e = new Entrant();
            e.name = name;
            e.first = System.currentTimeMillis();
            e.free = free;
            live.entrants.put(key, e);
        }
        e.tickets = Math.min(MAX_TICKETS[Config.get().gwMaxTickets], Math.max(1, e.tickets + tickets));
        e.paid += paid;
        return e;
    }

    /**
     * A payment arrived. Returns true if it entered (or added to) the giveaway - the caller then skips
     * punishing it unless "Entries also punish" is on. Blocked people's payments count as normal donations.
     */
    public static boolean payment(String name, double amount) {
        sync();
        if (live == null || live.drawing || live.entryMode == 1 || name == null || name.isBlank() || name.equals(Detector.UNKNOWN)) return false;
        if (amount + 0.001 < live.price) return false;
        if (blocked(name) != null) return false;
        Config c = Config.get();
        boolean fresh = !live.entrants.containsKey(name.toLowerCase(Locale.ROOT));
        int before = fresh ? 0 : live.entrants.get(name.toLowerCase(Locale.ROOT)).tickets;
        int add = c.gwMoreTickets ? (int) Math.floor((amount + 0.001) / live.price) : (fresh ? 1 : 0);
        Entrant e = enter(name, amount, add, false);
        save();
        String sub;
        if (fresh) sub = "entered the giveaway!  ·  " + count() + (count() == 1 ? " entry" : " entries");
        else if (e.tickets > before) sub = "now has " + e.tickets + " tickets in the giveaway";
        else sub = "is already in the giveaway";
        PunishManager.announceEntry(name, amount, sub);
        return true;
    }

    /**
     * Who typed just the join word in this chat line ("<Steve> !join", "[VIP] Steve: !join", "Steve » !join"),
     * or null. Only the text after the first separator counts, so "Bob: <Steve> !join" can't enter Steve.
     */
    public static String joinSender(String line, String word) {
        if (line == null || word == null || word.isBlank()) return null;
        String t = Detector.clean(line);
        int best = -1, len = 0;
        for (String sep : new String[]{": ", "> ", "» ", "➤ ", "→ ", " | "}) {
            int i = t.indexOf(sep);
            if (i > 0 && (best < 0 || i < best)) {
                best = i;
                len = sep.length();
            }
        }
        if (best < 0) return null;
        String msg = t.substring(best + len).trim();
        if (!msg.equalsIgnoreCase(word.trim())) return null;
        String head = t.substring(0, best).replaceAll("[\\[(][^\\])]*[\\])]", " ").replace('<', ' ').replace('>', ' ').trim();
        if (head.isEmpty()) return null;
        String[] parts = head.split("\\s+");
        String name = parts[parts.length - 1].replaceAll("[^A-Za-z0-9_.]", "");
        return name.matches("\\.?[A-Za-z0-9_]{2,16}") ? name : null;
    }

    /** A chat line arrived: the join word enters them (free, one ticket). */
    public static void chat(String line) {
        sync();
        if (live == null || live.drawing || live.entryMode == 0) return;
        String name = joinSender(line, live.joinWord);
        if (name == null) return;
        String why = blocked(name);
        if (why != null) {
            PunishManager.announceJoin(name, why);
            return;
        }
        if (live.entrants.containsKey(name.toLowerCase(Locale.ROOT))) return; // already in: stay quiet (no spam)
        enter(name, 0, 1, true);
        save();
        PunishManager.announceJoin(name, "entered the giveaway!  ·  " + count() + (count() == 1 ? " entry" : " entries"));
    }

    private static Games.Kind kindFor(int method) {
        int m = method == 4 ? R.nextInt(4) : method;
        return new Games.Kind[]{Games.Kind.WHEEL, Games.Kind.CASE, Games.Kind.ELIMINATION, Games.Kind.RAFFLE}[m];
    }

    /** Draw the winners now. Returns null if the draw started, or why it can't. */
    public static String draw() {
        if (live == null) return "No giveaway is open";
        if (live.drawing) return "Already drawing";
        sync();
        int need = minEntries();
        if (live.entrants.size() < Math.max(1, need)) {
            int more = Math.max(1, need) - live.entrants.size();
            return live.entrants.isEmpty() && need <= 1 ? "Nobody has entered yet" : "Needs " + more + " more " + (more == 1 ? "entry" : "entries");
        }
        Config c = Config.get();
        List<String> names = new ArrayList<>();
        List<Integer> tickets = new ArrayList<>();
        for (Entrant e : live.entrants.values()) {
            names.add(e.name);
            tickets.add(Math.max(1, e.tickets));
        }
        int k = Math.min(Math.max(1, live.winners), names.size());
        // pick 1st, then 2nd, then 3rd (weighted by tickets, nobody twice)
        List<Integer> left = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) left.add(i);
        int[] win = new int[k];
        for (int p = 0; p < k; p++) {
            List<Integer> w = new ArrayList<>();
            for (int i : left) w.add(tickets.get(i));
            int at = pick(w, R);
            win[p] = left.remove(at);
        }
        Record rec = new Record();
        rec.start = live.start;
        rec.entrants = names.size();
        rec.tickets = tickets();
        rec.pot = pot();
        for (int i = 0; i < names.size(); i++) rec.pool.put(names.get(i), tickets.get(i));
        for (int p = 0; p < k; p++) {
            Place pl = new Place();
            pl.place = p + 1;
            pl.prize = prizeText(p + 1);
            pl.amount = prizeAmount(p + 1);
            rec.places.add(pl);
        }
        Games.Kind kind = kindFor(c.gwMethod);
        rec.method = METHOD_NAMES[kind.ordinal()];
        drawingRecord = rec;
        live.drawing = true;
        // shown last place first (3rd, 2nd, then 1st): games go to the front of the line, so queue 1st first
        for (int p = 0; p < k; p++) {
            List<String> shown = new ArrayList<>();
            List<Integer> shownTickets = new ArrayList<>();
            int winAt = 0;
            for (int i = 0; i < names.size(); i++) {
                boolean wonLower = false; // already won a lower place, which is drawn before this one
                for (int q = p + 1; q < k; q++) if (win[q] == i) wonLower = true;
                if (wonLower) continue;
                if (i == win[p]) winAt = shown.size();
                shown.add(names.get(i));
                shownTickets.add(tickets.get(i));
            }
            Games.Game g = Games.buildGiveaway(kind, shown, shownTickets, winAt, rec.places.get(p).prize, DRAW_SECONDS[c.gwDrawTime], R.nextLong());
            label(g, p + 1, k, false, shown.size());
            Games.requestBuilt(g);
        }
        save();
        return null;
    }

    private static void label(Games.Game g, int place, int places, boolean reroll, int people) {
        g.place = place;
        g.places = places;
        g.reroll = reroll;
        String placeText = places > 1 ? PLACES[place - 1] + " place" : "";
        if (reroll) placeText = "Reroll" + (placeText.isEmpty() ? "" : "  ·  " + placeText);
        if (!placeText.isEmpty()) g.titleOverride = g.titleOverride + "  ·  " + placeText.toUpperCase(Locale.ROOT);
        String prize = g.prize == null || g.prize.isBlank() ? "" : "Prize: " + g.prize + "  ·  ";
        g.subtitle = prize + people + (people == 1 ? " in the draw" : " in the draw");
    }

    /** Redraw one place of the last giveaway (e.g. the winner is offline). Returns null if it started, or why not. */
    public static String reroll(int place) {
        if (drawing()) return "Wait for the draw to finish";
        Record rec = last();
        if (rec == null || place < 1 || place > rec.places.size()) return "No giveaway to reroll";
        if (Games.giveawayRunning()) return "A draw is already on screen";
        Set<String> out = new HashSet<>();
        for (Place p : rec.places) {
            if (p.name != null) out.add(p.name.toLowerCase(Locale.ROOT));
            for (String r : p.rerolled) out.add(r.toLowerCase(Locale.ROOT));
        }
        List<String> names = new ArrayList<>();
        List<Integer> tickets = new ArrayList<>();
        for (Map.Entry<String, Integer> e : rec.pool.entrySet()) {
            if (out.contains(e.getKey().toLowerCase(Locale.ROOT))) continue;
            names.add(e.getKey());
            tickets.add(Math.max(1, e.getValue()));
        }
        if (names.isEmpty()) return "Nobody left to reroll to";
        int w = pick(tickets, R);
        Games.Game g = Games.buildGiveaway(kindFor(Config.get().gwMethod), names, tickets, w, rec.places.get(place - 1).prize,
                DRAW_SECONDS[Config.get().gwDrawTime], R.nextLong());
        label(g, place, rec.places.size(), true, names.size());
        Games.requestBuilt(g);
        return null;
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

    /** Called every tick: if a draw was cancelled (cancel key), let it be drawn again. */
    public static void tick() {
        if (live != null && live.drawing && !Games.giveawayRunning()) {
            live.drawing = false;
            drawingRecord = null;
            save();
            PunishManager.showBanner("Giveaway draw cancelled - press Draw again when ready");
        }
    }

    /** A draw landed. */
    static void finished(Games.Game g, String winner) {
        Place pl;
        if (g.reroll) {
            Record rec = last();
            if (rec == null || g.place > rec.places.size()) return;
            pl = rec.places.get(g.place - 1);
            if (pl.name != null) pl.rerolled.add(pl.name);
            pl.name = winner;
            save();
            announce(pl, rec.places.size(), true);
            return;
        }
        Record rec = drawingRecord;
        if (rec == null || g.place > rec.places.size()) return;
        pl = rec.places.get(g.place - 1);
        pl.name = winner;
        announce(pl, rec.places.size(), false);
        boolean done = true;
        for (Place p : rec.places) if (p.name == null) done = false;
        if (done) {
            rec.end = System.currentTimeMillis();
            HISTORY.add(rec);
            drawingRecord = null;
            live = null;
        }
        save();
    }

    public static boolean online(String name) {
        Minecraft mc = Minecraft.getInstance();
        return mc.getConnection() != null && mc.getConnection().getPlayerInfo(name) != null;
    }

    /** "[Punish FX] Bob won 1st place! Prize: $5M [Click to pay them] [Reroll]" in your chat (only you see it). */
    private static void announce(Place pl, int places, boolean reroll) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String what = (reroll ? "won the reroll" : "won") + (places > 1 ? " " + PLACES[pl.place - 1] + " place!" : " the giveaway!");
        MutableComponent msg = Component.literal("[Punish FX] ").withStyle(st -> st.withColor(0xFF4A6A))
                .append(Component.literal(pl.name + " " + what).withStyle(st -> st.withColor(0xFFD700).withBold(true)));
        if (pl.prize != null && !pl.prize.isBlank()) msg.append(Component.literal(" Prize: " + pl.prize).withStyle(st -> st.withColor(0xFFFFFF)));
        String cmd = payCommand(pl);
        if (cmd != null) {
            msg.append(Component.literal("  [Click to pay them]").withStyle(st -> st.withColor(0x6BFF9E).withUnderlined(true)
                    .withClickEvent(new ClickEvent.SuggestCommand(cmd))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Puts " + cmd + " in your chat - press Enter to send it")))));
        }
        boolean on = online(pl.name);
        if (!on) msg.append(Component.literal("  (offline)").withStyle(st -> st.withColor(0xFF8A5A)));
        String rr = "/pfx giveaway reroll " + pl.place;
        msg.append(Component.literal("  [Reroll]").withStyle(st -> st.withColor(0xFFB347).withUnderlined(true)
                .withClickEvent(new ClickEvent.SuggestCommand(rr))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Draw someone else for this place (after the draw finishes)")))));
        mc.gui.getChat().addMessage(msg);
    }

    /** "/pay Steve 1000000" for a place, or null if its prize isn't money. */
    public static String payCommand(Place pl) {
        if (pl == null || pl.name == null || pl.amount <= 0) return null;
        String t = Config.get().gwPayCommand;
        if (t == null || t.isBlank()) t = "/pay {name} {amount}";
        String amt = pl.amount == Math.floor(pl.amount) ? String.valueOf((long) pl.amount) : String.valueOf(pl.amount);
        return t.replace("{name}", pl.name).replace("{amount}", amt);
    }

    /** The pay command for 1st place of the last giveaway. */
    public static String payCommand() {
        Record r = last();
        return r == null || r.places.isEmpty() ? null : payCommand(r.places.get(0));
    }

    public static void clearHistory() {
        HISTORY.clear();
        save();
    }
}
