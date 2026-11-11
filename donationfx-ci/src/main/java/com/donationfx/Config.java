package com.donationfx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** All settings, saved in config/donationfx.json (can be edited by hand, then use /donationfx reload). */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static Config INSTANCE = defaults();

    public static final class Sound {
        public String id;
        /** When to play it, in seconds after the animation starts. */
        public double at;
        public float volume = 1f;
        public float pitch = 1f;

        public Sound() {}

        public Sound(String id, double at, float volume, float pitch) {
            this.id = id;
            this.at = at;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    public static final class Tier {
        public boolean enabled = true;
        public String name;
        /** Smallest amount for this level. */
        public double minAmount;
        public String title;
        /** {player} and {amount} are filled in. */
        public String subtitle = "{player} donated {amount}!";
        /** Main color, like "#55FF55". "rainbow" = all colors. */
        public String color;
        /** How long the animation lasts, in seconds. */
        public double seconds;
        /** 1.0 = normal. Higher = more particles and effects. */
        public double intensity = 1.0;
        /** Items that rain down the screen. */
        public List<String> rainItems = new ArrayList<>();
        public List<Sound> sounds = new ArrayList<>();

        public Tier() {}
    }

    // ---- main switches
    public int configVersion = 2;
    public boolean enabled = true;
    public boolean sounds = true;
    /** 0.0 - 1.0 */
    public float volume = 1.0f;
    public boolean flashes = true;
    public boolean shake = true;
    /** Particles and fireworks around you in the world (only you see them). */
    public boolean worldEffects = true;
    /** The big item pop-up like a totem of undying (levels 3+). */
    public boolean itemPopup = true;
    public boolean chatMessage = true;
    public boolean countUp = true;
    public boolean shortAmounts = false;
    /** 0.5 - 2.0 */
    public double size = 1.0;
    /** 0.25 - 2.0, how long animations last compared to normal. */
    public double speed = 1.0;
    /** 0 = middle of the screen, 1 = upper part. */
    public int position = 0;
    public boolean showPlayerHead = true;
    /** Play donations one after another instead of skipping. */
    public boolean queue = true;
    public int maxQueue = 10;
    /** Add up several payments from the same player that arrive close together. */
    public boolean combine = true;
    public double combineSeconds = 3.0;

    // ---- detection
    /** Ignore lines that look like a player typing in chat (stops fake "donations"). */
    public boolean ignorePlayerChat = true;
    /** Also read the action bar (text above the hotbar). */
    public boolean readActionBar = true;
    /**
     * Your own patterns, tried first. Use (?<amount>...) for the money and (?<player>...) for the name.
     * Example: "^\\$(?<amount>[\\d,.]+[kmb]?) has been received from (?<player>\\w+)"
     */
    public List<String> customPatterns = new ArrayList<>();
    /** Words that mean you RECEIVED money. */
    public List<String> incomingWords = new ArrayList<>(List.of(
            "received", "paid you", "sent you", "gave you", "has paid", "has sent", "has given",
            "donated", "you got", "you have been paid", "transferred", "deposited into your"));
    /** Words that mean YOU paid someone (ignored). */
    public List<String> outgoingWords = new ArrayList<>(List.of(
            "you paid", "you sent", "you have paid", "you have sent", "you gave", "you donated", "sent to",
            "paid to", "you transferred", "withdrew", "withdrawn", "insufficient", "not enough",
            "you bought", "you purchased", "sold", "listed", "auction", "bid", "order", "costs", "price",
            "bounty", "for killing", "reward", "shop", "sell", "selling", "earned", "stole", "killed", "slain", "/pay"));
    /** Only count amounts with a $ (or the word coins/money/dollars). Stops "5m" in a message from counting. */
    public boolean requireDollarSign = true;
    /** Words that mean a private message (ignored, so nobody can fake a donation by messaging you). */
    public List<String> privateMessageWords = new ArrayList<>(List.of(
            "whisper", "whispers", "whispered", "messaged you", "-> me", "-> you", "\u2192 me", "\u2192 you",
            "\u27a1 me", "\u27a1 you", "[msg]", "[pm]", "[dm]", "(msg)", "\u2709", "reply"));
    /** Save money messages to config/donationfx-seen.txt so patterns can be made for them. */
    public boolean saveSeenLines = true;

    public List<Tier> tiers = new ArrayList<>();

    public static Config get() {
        return INSTANCE;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("donationfx.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                Config c = GSON.fromJson(Files.readString(p), Config.class);
                if (c != null) {
                    c.fix();
                    INSTANCE = c;
                    return;
                }
            }
        } catch (Exception e) {
            DonationFxClient.LOG.warn("Donation FX: could not read the settings file, using defaults", e);
        }
        INSTANCE = defaults();
        save();
    }

    public static void save() {
        try {
            Files.writeString(path(), GSON.toJson(INSTANCE));
        } catch (Exception e) {
            DonationFxClient.LOG.warn("Donation FX: could not save settings", e);
        }
    }

    public static void reset() {
        INSTANCE = defaults();
        save();
    }

    private void fix() {
        Config d = defaults();
        if (tiers == null || tiers.size() != 5) tiers = d.tiers;
        for (int i = 0; i < tiers.size(); i++) {
            Tier t = tiers.get(i);
            Tier dt = d.tiers.get(i);
            if (t == null) {
                tiers.set(i, dt);
                continue;
            }
            if (t.name == null) t.name = dt.name;
            if (t.title == null) t.title = dt.title;
            if (t.subtitle == null) t.subtitle = dt.subtitle;
            if (t.color == null) t.color = dt.color;
            if (t.seconds <= 0) t.seconds = dt.seconds;
            if (t.intensity <= 0) t.intensity = 1;
            if (t.rainItems == null) t.rainItems = dt.rainItems;
            if (t.sounds == null) t.sounds = dt.sounds;
        }
        if (customPatterns == null) customPatterns = new ArrayList<>();
        if (incomingWords == null) incomingWords = d.incomingWords;
        if (privateMessageWords == null) privateMessageWords = d.privateMessageWords;
        if (configVersion < 2) {
            // 1.0 counted "from" and plain numbers like "5m": a private message could set it off
            incomingWords.removeIf(w -> w.equalsIgnoreCase("from") || w.equals("+$"));
            requireDollarSign = true;
            privateMessageWords = d.privateMessageWords;
            configVersion = 2;
            save();
        }
        if (outgoingWords == null) outgoingWords = d.outgoingWords;
        volume = Math.max(0f, Math.min(1f, volume));
        size = Math.max(0.5, Math.min(2.0, size));
        speed = Math.max(0.25, Math.min(2.0, speed));
        maxQueue = Math.max(1, Math.min(50, maxQueue));
        combineSeconds = Math.max(0, Math.min(30, combineSeconds));
    }

    /** Which level an amount reaches: 0-4, or -1 if it is too small (or that level is switched off). */
    public int tierFor(double amount) {
        for (int i = tiers.size() - 1; i >= 0; i--) {
            Tier t = tiers.get(i);
            if (amount >= t.minAmount) return t.enabled ? i : -1;
        }
        return -1;
    }

    private static Tier tier(String name, double min, String title, String color, double seconds, List<String> rain, Sound... sounds) {
        Tier t = new Tier();
        t.name = name;
        t.minAmount = min;
        t.title = title;
        t.color = color;
        t.seconds = seconds;
        t.rainItems = new ArrayList<>(rain);
        t.sounds = new ArrayList<>(List.of(sounds));
        return t;
    }

    private static Sound s(String id, double at, float vol, float pitch) {
        return new Sound("minecraft:" + id, at, vol, pitch);
    }

    public static Config defaults() {
        Config c = new Config();
        c.tiers.add(tier("Level 1", 50_000, "NICE DONATION!", "#55FF55", 3.5,
                List.of("minecraft:gold_nugget", "minecraft:emerald"),
                s("entity.experience_orb.pickup", 0.00, 0.8f, 0.8f),
                s("entity.experience_orb.pickup", 0.12, 0.8f, 1.1f),
                s("entity.experience_orb.pickup", 0.24, 0.8f, 1.4f),
                s("block.note_block.chime", 0.40, 0.9f, 1.2f),
                s("block.note_block.chime", 0.55, 0.9f, 1.6f)));
        c.tiers.add(tier("Level 2", 250_000, "BIG DONATION!", "#55FFFF", 5.0,
                List.of("minecraft:gold_nugget", "minecraft:gold_ingot", "minecraft:emerald"),
                s("entity.player.levelup", 0.00, 0.9f, 1.0f),
                s("block.amethyst_block.chime", 0.20, 1.0f, 1.0f),
                s("entity.firework_rocket.launch", 0.30, 0.9f, 1.0f),
                s("entity.firework_rocket.twinkle", 1.10, 0.9f, 1.0f),
                s("block.note_block.bell", 1.20, 0.8f, 1.5f)));
        c.tiers.add(tier("Level 3", 1_000_000, "HUGE DONATION!!", "#FF55FF", 6.5,
                List.of("minecraft:gold_ingot", "minecraft:gold_block", "minecraft:diamond", "minecraft:emerald"),
                s("item.totem.use", 0.00, 0.8f, 1.0f),
                s("block.beacon.activate", 0.10, 1.0f, 1.2f),
                s("entity.firework_rocket.large_blast", 0.50, 1.0f, 1.0f),
                s("entity.firework_rocket.twinkle_far", 1.00, 1.0f, 1.0f),
                s("entity.player.levelup", 1.40, 1.0f, 1.3f),
                s("entity.firework_rocket.large_blast", 2.20, 1.0f, 0.9f)));
        c.tiers.add(tier("Level 4", 5_000_000, "INSANE DONATION!!!", "#FFAA00", 8.5,
                List.of("minecraft:diamond", "minecraft:emerald_block", "minecraft:gold_block", "minecraft:diamond_block", "minecraft:totem_of_undying"),
                s("entity.lightning_bolt.thunder", 0.00, 0.7f, 1.0f),
                s("ui.toast.challenge_complete", 0.15, 1.0f, 1.0f),
                s("entity.ender_dragon.growl", 0.60, 0.5f, 1.2f),
                s("entity.firework_rocket.large_blast", 1.00, 1.0f, 1.0f),
                s("entity.firework_rocket.large_blast_far", 1.40, 1.0f, 0.8f),
                s("item.totem.use", 2.00, 0.8f, 1.2f),
                s("entity.firework_rocket.twinkle_far", 2.60, 1.0f, 1.0f),
                s("entity.generic.explode", 3.20, 0.6f, 1.4f)));
        c.tiers.add(tier("Level 5", 10_000_000, "LEGENDARY DONATION!!!!", "rainbow", 12.0,
                List.of("minecraft:nether_star", "minecraft:netherite_ingot", "minecraft:diamond_block", "minecraft:totem_of_undying",
                        "minecraft:enchanted_golden_apple", "minecraft:beacon", "minecraft:dragon_egg"),
                s("entity.wither.spawn", 0.00, 0.6f, 1.0f),
                s("ui.toast.challenge_complete", 0.40, 1.0f, 1.0f),
                s("event.raid.horn", 0.80, 0.8f, 1.0f),
                s("entity.lightning_bolt.thunder", 1.20, 0.8f, 0.8f),
                s("entity.firework_rocket.large_blast", 1.60, 1.0f, 1.0f),
                s("entity.firework_rocket.large_blast_far", 1.90, 1.0f, 0.8f),
                s("item.totem.use", 2.40, 0.9f, 1.0f),
                s("entity.ender_dragon.growl", 3.00, 0.6f, 0.9f),
                s("entity.firework_rocket.twinkle_far", 3.60, 1.0f, 1.0f),
                s("ui.toast.challenge_complete", 4.80, 1.0f, 1.3f),
                s("entity.generic.explode", 5.40, 0.7f, 0.8f),
                s("entity.player.levelup", 6.20, 1.0f, 0.7f)));
        return c;
    }
}
