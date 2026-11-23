package com.punishfx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Presets: whole setups you can switch between in one click. A preset holds every setting except the
 * ones about reading your server's payment messages (those stay as they are, so a preset or a friend's
 * code never breaks donation detection).
 */
public final class Presets {
    /** Settings a preset never changes: how payments are detected on your server. */
    private static final Set<String> KEEP = Set.of("readActionBar", "ignorePlayerChat", "requireDollarSign", "looseMatching",
            "saveSeenLines", "customPatterns", "incomingWords", "outgoingWords", "privateMessageWords", "configVersion");
    private static final String CODE_PREFIX = "PFX1:";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static final class Saved {
        public String name;
        public long savedAt;
        public JsonObject data;
    }

    private static final class File {
        List<Saved> presets = new ArrayList<>();
        String lastApplied = "";
    }

    /** A ready-made preset: a change applied on top of your current settings. */
    public record Builtin(String name, String desc, UnaryOperator<Config> make) {}

    private static File file = new File();

    private Presets() {}

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("punishfx-presets.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                File f = GSON.fromJson(Files.readString(p), File.class);
                if (f != null) {
                    if (f.presets == null) f.presets = new ArrayList<>();
                    f.presets.removeIf(s -> s == null || s.name == null || s.data == null);
                    if (f.lastApplied == null) f.lastApplied = "";
                    file = f;
                }
            }
        } catch (Exception e) {
            PunishFxClient.LOG.warn("Punish FX: could not read presets", e);
        }
    }

    private static void save() {
        try {
            Files.writeString(path(), GSON.toJson(file));
        } catch (Exception e) {
            PunishFxClient.LOG.warn("Punish FX: could not save presets", e);
        }
    }

    public static List<Saved> saved() {
        return file.presets;
    }

    public static String lastApplied() {
        return file.lastApplied;
    }

    // ------------------------------------------------------------------ snapshots

    /** Your current settings, minus the detection ones. */
    public static JsonObject snapshot() {
        JsonObject o = GSON.toJsonTree(Config.get()).getAsJsonObject();
        for (String k : KEEP) o.remove(k);
        return o;
    }

    private static Config copyOfCurrent() {
        return GSON.fromJson(GSON.toJsonTree(Config.get()), Config.class);
    }

    /** Switch to a preset's settings (keeping how payments are detected). */
    public static void apply(JsonObject data, String name) {
        switchTo(data);
        file.lastApplied = name;
        save();
    }

    public static void apply(Builtin b) {
        switchTo(GSON.toJsonTree(b.make().apply(copyOfCurrent())).getAsJsonObject());
        file.lastApplied = b.name();
        save();
    }

    /**
     * Settings a preset leaves alone right now: payment detection always, the stream's settings always (so a
     * stream keeps going exactly as it was), and the giveaway's while one is open.
     */
    private static boolean kept(String key) {
        return KEEP.contains(key) || key.startsWith("stream") || (Giveaway.live() && key.startsWith("gw"));
    }

    /** Switch settings without stopping anything: running punishments, the stream and any giveaway carry on. */
    private static void switchTo(JsonObject data) {
        JsonObject cur = GSON.toJsonTree(Config.get()).getAsJsonObject();
        for (Map.Entry<String, JsonElement> e : data.entrySet()) if (!kept(e.getKey())) cur.add(e.getKey(), e.getValue());
        Config.install(GSON.fromJson(cur, Config.class));
    }

    /** Save (or overwrite) a preset with your current settings. Returns the name it was saved as. */
    public static String saveCurrent(String name) {
        String n = name == null || name.isBlank() ? freeName("My preset") : name.trim();
        if (n.length() > 32) n = n.substring(0, 32);
        Saved s = null;
        for (Saved x : file.presets) if (x.name.equalsIgnoreCase(n)) s = x;
        if (s == null) {
            s = new Saved();
            file.presets.add(s);
        }
        s.name = n;
        s.savedAt = System.currentTimeMillis();
        s.data = snapshot();
        file.lastApplied = n;
        save();
        return n;
    }

    /** A name nobody else is using: "Chill stream copy", "Chill stream copy 2"... */
    public static String freeName(String base) {
        String n = base.length() > 26 ? base.substring(0, 26) : base;
        if (!exists(n)) return n;
        for (int i = 2; ; i++) if (!exists(n + " " + i)) return n + " " + i;
    }

    /** Copy one of your presets. Returns the copy's name. */
    public static String duplicate(Saved src) {
        Saved s = new Saved();
        s.name = freeName(src.name + " copy");
        s.savedAt = System.currentTimeMillis();
        s.data = src.data.deepCopy();
        file.presets.add(file.presets.indexOf(src) + 1, s);
        save();
        return s.name;
    }

    /** Copy a ready-made preset into your own list, so you can change it. */
    public static String duplicate(Builtin b) {
        Config c = b.make().apply(copyOfCurrent());
        JsonObject data = GSON.toJsonTree(c).getAsJsonObject();
        for (String k : KEEP) data.remove(k);
        Saved s = new Saved();
        s.name = freeName(b.name() + " copy");
        s.savedAt = System.currentTimeMillis();
        s.data = data;
        file.presets.add(s);
        save();
        return s.name;
    }

    public static void rename(Saved s, String name) {
        if (name == null || name.isBlank()) return;
        String n = name.trim();
        if (n.equals(s.name)) return;
        if (file.lastApplied.equals(s.name)) file.lastApplied = n;
        s.name = n;
        save();
    }

    /** The saved preset you last switched to (null if it was a ready-made one). */
    public static Saved lastSaved() {
        for (Saved s : file.presets) if (s.name.equals(file.lastApplied)) return s;
        return null;
    }

    /** Put your current settings into a saved preset. */
    public static void update(Saved s) {
        s.data = snapshot();
        s.savedAt = System.currentTimeMillis();
        save();
    }

    public static void delete(Saved s) {
        file.presets.remove(s);
        save();
    }

    // ------------------------------------------------------------------ share codes

    /** Your setup as a code a friend can paste (gzip + base64). */
    public static String exportCode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(bytes)) {
                gz.write(new Gson().toJson(snapshot()).getBytes(StandardCharsets.UTF_8));
            }
            return CODE_PREFIX + Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    /** Reads a code and saves it as a preset (doesn't switch to it). Returns its name, or null if the code is bad. */
    public static String importCode(String code) {
        if (code == null) return null;
        code = code.trim();
        if (!code.startsWith(CODE_PREFIX)) return null;
        try {
            byte[] raw = Base64.getDecoder().decode(code.substring(CODE_PREFIX.length()));
            String json;
            try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(raw))) {
                json = new String(gz.readAllBytes(), StandardCharsets.UTF_8);
            }
            JsonObject data = JsonParser.parseString(json).getAsJsonObject();
            for (String k : KEEP) data.remove(k);
            GSON.fromJson(data, Config.class); // must be readable as settings
            int n = 1;
            String name = "Shared preset";
            while (exists(name)) name = "Shared preset " + (++n);
            Saved s = new Saved();
            s.name = name;
            s.savedAt = System.currentTimeMillis();
            s.data = data;
            file.presets.add(s);
            save();
            return name;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean exists(String name) {
        for (Saved s : file.presets) if (s.name.equalsIgnoreCase(name)) return true;
        return false;
    }

    // ------------------------------------------------------------------ ready-made

    private static final Set<String> HARSH = Set.of("frozen", "blackout", "fake_lag", "butterfingers", "leave_server", "close_game", "window");

    public static final List<Builtin> BUILTIN = List.of(
            new Builtin("Chill stream", "Shorter and milder, one at a time (queue)", c -> {
                c.enabled = true;
                c.bannerOnly = false;
                c.lengthScale = 0.75;
                c.overlapMode = 1;
                c.queueGap = 2;
                c.overlayStrength = 0.75;
                c.wheelUse = 0;
                c.caseUse = 0;
                for (Config.Tier t : c.tiers) {
                    t.count = 1;
                    List<String> pool = new ArrayList<>();
                    for (String id : Punishes.poolFor(t)) if (!HARSH.contains(id)) pool.add(id);
                    t.pool = pool.isEmpty() ? new ArrayList<>(List.of("jitter", "fog", "disco_eyes", "sluggish")) : pool;
                }
                return c;
            }),
            new Builtin("Chaos stream", "Longer, stacked combos, every punishment, the wheel from level 3", c -> {
                c.enabled = true;
                c.bannerOnly = false;
                c.lengthScale = 1.5;
                c.overlapMode = 2;
                c.comboStep = 1;
                c.maxActive = 10;
                c.overlayStrength = 1.25;
                c.wheelUse = 3;
                c.caseUse = 5;
                for (int i = 0; i < c.tiers.size(); i++) {
                    Config.Tier t = c.tiers.get(i);
                    t.count = Math.min(4, t.count + 1);
                    if (i >= 1) t.pool = new ArrayList<>(); // everything (except the opt-in ones)
                }
                return c;
            }),
            new Builtin("Banner only", "Just thanks donors on screen, no punishments (recording)", c -> {
                c.enabled = true;
                c.bannerOnly = true;
                c.announce = true;
                return c;
            }),
            new Builtin("Starting setup", "Back to how Punish FX starts (keeps your levels' amounts)", c -> {
                JsonObject def = GSON.toJsonTree(Config.defaults()).getAsJsonObject();
                JsonObject cur = GSON.toJsonTree(c).getAsJsonObject();
                for (String k : KEEP) if (cur.has(k)) def.add(k, cur.get(k));
                Config fresh = GSON.fromJson(def, Config.class);
                for (int i = 0; i < fresh.tiers.size() && i < c.tiers.size(); i++) fresh.tiers.get(i).minAmount = c.tiers.get(i).minAmount;
                return fresh;
            }));
}
