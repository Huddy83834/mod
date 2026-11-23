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

    // ---- look
    /** Index into {@link Ui#THEME_NAMES}. */
    public int theme = 0;
    /** Multiplies how strong the colour overlays look (flash, fog, disco...). */
    public double overlayStrength = 1.0;

    // ---- donation banner
    public int bannerPos = 0;              // 0 top, 1 middle, 2 bottom
    public double bannerScale = 1.0;
    public double bannerSeconds = 3.5;
    public boolean bannerShowPunishments = true;

    // ---- per-punishment strength (index into the choices on the Customize page; 1 = normal)
    public int sluggishLevel = 1, drunkLevel = 1, jitterLevel = 1, glitchLevel = 1, spinLevel = 1,
            tunnelLevel = 1, lagLevel = 1, flashLevel = 1, discoLevel = 1;
    public int languageMode = 4;           // 0 pirate, 1 upside down, 2 lolcat, 3 shakespeare, 4 random
    public int chipmunkMode = 0;           // 0 chipmunk, 1 goat screams, 2 both
    public int possessedLevel = 1;         // 0 slow, 1 normal, 2 frantic
    public int butterLevel = 0;            // 0 one item, 1 whole stack, 2 random slot
    public int upsideMode = 0;             // 0 flip, 1 slow roll

    // ---- stream mode
    public boolean streamHud = true;
    public boolean streamHudMoney = true, streamHudCount = true, streamHudTimer = true;
    public double streamScale = 1.0, streamX = 0.0, streamY = 0.15;
    public boolean streamAutoRecap = true;
    public boolean streamFreshBoard = true;

    // ---- safety
    /** Donations this many seconds after the last punishment only show the banner. 0 = off. */
    public int cooldownSeconds = 0;
    /** Length of the cancel countdown before Leave Server / Close Minecraft. */
    public int countdownSeconds = 5;
    public double volume = 1.0;

    // ---- top donors board
    public boolean topShow = false;
    public String topTitle = "TOP DONORS";
    public int topRows = 5;
    public int topFont = 0;                // index into TopDonorsHud.FONT_NAMES
    public boolean topBold = false, topItalic = false, topShadow = true, topMedals = true, topHideEmpty = false;
    public int topColor = 0;               // index into TopDonorsHud.COLOR_NAMES
    public int topBackground = 1;          // index into TopDonorsHud.BG_NAMES
    public int topAmounts = 0;             // 0 short ($1.2M), 1 full ($1,200,000)
    public int topPeriod = 0;              // 0 all time, 1 today, 2 this session
    public double topScale = 1.0;
    /** Position as a fraction of the free space (0 = left/top edge, 1 = right/bottom edge). */
    public double topX = 1.0, topY = 0.3;

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
        Ui.applyTheme(INSTANCE.theme);
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

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
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
        theme = clamp(theme, 0, Ui.THEME_NAMES.length - 1);
        overlayStrength = Math.max(0.25, Math.min(2.0, overlayStrength));
        bannerPos = clamp(bannerPos, 0, 2);
        bannerScale = Math.max(0.5, Math.min(3.0, bannerScale));
        bannerSeconds = Math.max(1.0, Math.min(20.0, bannerSeconds));
        sluggishLevel = clamp(sluggishLevel, 0, 3);
        drunkLevel = clamp(drunkLevel, 0, 2);
        jitterLevel = clamp(jitterLevel, 0, 2);
        glitchLevel = clamp(glitchLevel, 0, 2);
        spinLevel = clamp(spinLevel, 0, 3);
        tunnelLevel = clamp(tunnelLevel, 0, 3);
        lagLevel = clamp(lagLevel, 0, 2);
        flashLevel = clamp(flashLevel, 0, 2);
        discoLevel = clamp(discoLevel, 0, 2);
        languageMode = clamp(languageMode, 0, 4);
        chipmunkMode = clamp(chipmunkMode, 0, 2);
        possessedLevel = clamp(possessedLevel, 0, 2);
        butterLevel = clamp(butterLevel, 0, 2);
        upsideMode = clamp(upsideMode, 0, 1);
        streamScale = Math.max(0.5, Math.min(4.0, streamScale));
        streamX = Math.max(0.0, Math.min(1.0, streamX));
        streamY = Math.max(0.0, Math.min(1.0, streamY));
        cooldownSeconds = clamp(cooldownSeconds, 0, 600);
        countdownSeconds = clamp(countdownSeconds, 1, 60);
        volume = Math.max(0.0, Math.min(1.0, volume));
        if (topTitle == null) topTitle = "TOP DONORS";
        topRows = clamp(topRows, 1, 10);
        topFont = clamp(topFont, 0, 3);
        topColor = clamp(topColor, 0, 7);
        topBackground = clamp(topBackground, 0, 3);
        topAmounts = clamp(topAmounts, 0, 1);
        topPeriod = clamp(topPeriod, 0, 2);
        topScale = Math.max(0.5, Math.min(4.0, topScale));
        topX = Math.max(0.0, Math.min(1.0, topX));
        topY = Math.max(0.0, Math.min(1.0, topY));
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
        c.tiers.add(tier("Mild", 50_000, 6, 1, "jitter", "sluggish", "fog", "disco_eyes", "spin", "possessed_hotbar", "chipmunk"));
        c.tiers.add(tier("Annoying", 250_000, 8, 1, "drunk", "inverted_controls", "glitch", "tunnel_vision", "no_hud", "flashbang", "front_camera", "language", "hotbar_chaos"));
        c.tiers.add(tier("Harsh", 1_000_000, 10, 2, "negative", "inverted_controls", "frozen", "drunk", "flashbang", "tunnel_vision", "glitch", "mirror", "hotbar_chaos"));
        c.tiers.add(tier("Brutal", 5_000_000, 12, 2, "blackout", "frozen", "negative", "fake_lag", "drunk", "inverted_controls", "disco_eyes", "upside_down", "mirror"));
        c.tiers.add(tier("Insane", 10_000_000, 15, 3, "negative", "drunk", "inverted_controls", "fake_lag", "blackout", "frozen", "glitch", "jitter", "upside_down", "language"));
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
