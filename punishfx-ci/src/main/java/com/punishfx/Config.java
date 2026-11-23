package com.punishfx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** All settings for Punish FX, saved in config/punishfx.json (editable by hand, then /punishfx reload). */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static Config INSTANCE = defaults();

    /** One punishment level: a smallest amount, how long the punishment lasts, and which punishments it may use. */
    public static final class Tier {
        public boolean enabled = true;
        public String name;
        /** Smallest donation (in money) that reaches this level. */
        public double minAmount;
        /** How long the punishments last, in seconds. */
        public double seconds = 8;
        /** How many punishments to run at once from the pool (1 = one random, 2 = two stacked, ...). */
        public int count = 1;
        /** The punishments this level may use (ids). Empty = any punishment. */
        public List<String> pool = new ArrayList<>();

        public Tier() {}

        public Tier copy() {
            Tier t = new Tier();
            t.enabled = enabled;
            t.name = name;
            t.minAmount = minAmount;
            t.seconds = seconds;
            t.count = count;
            t.pool = new ArrayList<>(pool);
            return t;
        }
    }

    // ---- main switches
    public int configVersion = 1;
    public boolean enabled = true;
    /** Play the warning sound + on-screen banner naming the punishment when it starts. */
    public boolean announce = true;
    /** Run punishments from a test donation too. */
    public boolean sounds = true;
    /** Global length multiplier (0.25 - 3.0) on top of each level's seconds. */
    public double lengthScale = 1.0;
    /** Stack new punishments on top of running ones instead of replacing. */
    public boolean stack = true;
    /** Most punishments at once. */
    public int maxActive = 6;
    /** Add up several payments from the same player that arrive close together. */
    public boolean combine = true;
    public double combineSeconds = 3.0;
    /** The two extreme ones (leave server, close game) ask for a 5s cancel countdown first. */
    public boolean confirmExtreme = true;

    // ---- detection (same engine as the donation mod)
    public boolean readActionBar = true;
    public boolean ignorePlayerChat = true;
    public boolean requireDollarSign = true;
    /** Old behaviour: accept "paid you $X" anywhere in a line. Unsafe - players can fake it in chat. */
    public boolean looseMatching = false;
    public boolean saveSeenLines = true;
    public List<String> customPatterns = new ArrayList<>();
    public List<String> incomingWords = new ArrayList<>(List.of(
            "received", "paid you", "sent you", "gave you", "has paid", "has sent", "has given",
            "donated", "you got", "you have been paid", "transferred", "deposited into your"));
    public List<String> outgoingWords = new ArrayList<>(List.of(
            "you paid", "you sent", "you have paid", "you have sent", "you gave", "you donated", "sent to",
            "paid to", "you transferred", "withdrew", "withdrawn", "insufficient", "not enough",
            "you bought", "you purchased", "sold", "listed", "auction", "bid", "order", "costs", "price",
            "bounty", "for killing", "reward", "shop", "sell", "selling", "earned", "stole", "killed", "slain", "/pay"));
    public List<String> privateMessageWords = new ArrayList<>(List.of(
            "whisper", "whispers", "whispered", "messaged you", "-> me", "-> you", "→ me", "→ you",
            "➡ me", "➡ you", "[msg]", "[pm]", "[dm]", "(msg)", "✉", "reply"));

    public List<Tier> tiers = new ArrayList<>();

    public static Config get() {
        return INSTANCE;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("punishfx.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                Config c = GSON.fromJson(Files.readString(p), Config.class);
                if (c != null) {
                    c.fix();
                    INSTANCE = c;
                    save(); // write back the cleaned-up pools so the file matches what the mod uses
                    return;
                }
            }
        } catch (Exception e) {
            PunishFxClient.LOG.warn("Punish FX: could not read settings, using defaults", e);
        }
        INSTANCE = defaults();
        save();
    }

    public static void save() {
        try {
            Files.writeString(path(), GSON.toJson(INSTANCE));
        } catch (Exception e) {
            PunishFxClient.LOG.warn("Punish FX: could not save settings", e);
        }
    }

    public static void reset() {
        INSTANCE = defaults();
        save();
    }

    private void fix() {
        Config d = defaults();
        if (tiers == null || tiers.isEmpty()) tiers = d.tiers;
        for (int i = 0; i < tiers.size(); i++) {
            Tier t = tiers.get(i);
            if (t == null) {
                tiers.set(i, d.tiers.get(Math.min(i, d.tiers.size() - 1)).copy());
                continue;
            }
            if (t.name == null) t.name = "Level " + (i + 1);
            if (t.seconds <= 0) t.seconds = 8;
            if (t.count < 1) t.count = 1;
            if (t.pool == null) t.pool = new ArrayList<>();
            migratePool(t);
        }
        if (customPatterns == null) customPatterns = new ArrayList<>();
        if (incomingWords == null) incomingWords = d.incomingWords;
        if (outgoingWords == null) outgoingWords = d.outgoingWords;
        if (privateMessageWords == null) privateMessageWords = d.privateMessageWords;
        lengthScale = Math.max(0.25, Math.min(3.0, lengthScale));
        maxActive = Math.max(1, Math.min(20, maxActive));
        combineSeconds = Math.max(0, Math.min(30, combineSeconds));
    }

    public Tier addTier() {
        Tier t = new Tier();
        int n = tiers.size();
        t.minAmount = n > 0 ? tiers.get(n - 1).minAmount * 2 : 50_000;
        t.name = "Level " + (n + 1);
        t.seconds = 8;
        t.count = 1;
        t.pool = new ArrayList<>();
        tiers.add(t);
        return t;
    }

    /** Which level an amount reaches: 0-based, or -1 if too small or that level is off. */
    public int tierFor(double amount) {
        for (int i = tiers.size() - 1; i >= 0; i--) {
            Tier t = tiers.get(i);
            if (amount >= t.minAmount) return t.enabled ? i : -1;
        }
        return -1;
    }

    private static Tier tier(String name, double min, double seconds, int count, String... pool) {
        Tier t = new Tier();
        t.name = name;
        t.minAmount = min;
        t.seconds = seconds;
        t.count = count;
        t.pool = new ArrayList<>(List.of(pool));
        return t;
    }

    public static Config defaults() {
        Config c = new Config();
        // light -> brutal. The big two (leave server / close game) are opt-in only.
        c.tiers.add(tier("Mild", 50_000, 6, 1, "jitter", "sluggish", "fog", "disco_eyes", "spin"));
        c.tiers.add(tier("Annoying", 250_000, 8, 1, "drunk", "inverted_controls", "glitch", "tunnel_vision", "no_hud", "flashbang"));
        c.tiers.add(tier("Harsh", 1_000_000, 10, 2, "negative", "inverted_controls", "frozen", "drunk", "flashbang", "tunnel_vision", "glitch"));
        c.tiers.add(tier("Brutal", 5_000_000, 12, 2, "blackout", "frozen", "negative", "fake_lag", "drunk", "inverted_controls", "disco_eyes"));
        c.tiers.add(tier("Insane", 10_000_000, 15, 3, "negative", "drunk", "inverted_controls", "fake_lag", "blackout", "frozen", "glitch", "jitter"));
        return c;
    }

    /** Punishments that were removed or merged, and what an old pool entry now means. */
    private static final java.util.Map<String, String> RENAMED = java.util.Map.ofEntries(
            java.util.Map.entry("nausea", "drunk"), java.util.Map.entry("sick", "drunk"),
            java.util.Map.entry("random_jumps", "drunk"), java.util.Map.entry("vertigo", "spin"),
            java.util.Map.entry("blind", "blackout"), java.util.Map.entry("darkness", "blackout"),
            java.util.Map.entry("earthquake", "jitter"), java.util.Map.entry("static", "glitch"),
            java.util.Map.entry("cracked_screen", "glitch"), java.util.Map.entry("censored", "glitch"),
            java.util.Map.entry("fake_crash", "glitch"), java.util.Map.entry("troll_rain", "glitch"),
            java.util.Map.entry("rain_bugs", "glitch"), java.util.Map.entry("heartbeat", "jitter"));

    /** Rewrites a level's pool so it only names punishments that exist (old saves included). */
    public static void migratePool(Tier t) {
        if (t.pool == null || t.pool.isEmpty()) return;
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (String raw : t.pool) {
            if (raw == null) continue;
            String id = raw.toLowerCase(java.util.Locale.ROOT).trim();
            id = RENAMED.getOrDefault(id, id);
            if (Punishes.get(id) != null) out.add(id); // drops kick_title / fake_ban and anything unknown
        }
        t.pool = new ArrayList<>(out); // empty = any punishment, so a level can never end up doing nothing
    }
}
