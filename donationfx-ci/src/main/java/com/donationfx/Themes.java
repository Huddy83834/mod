package com.donationfx;

import com.donationfx.ThemeKit.Theme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static com.donationfx.ThemeKit.snd;

/** The list of all animation themes. "classic" is the original animation, "random" picks one. */
public final class Themes {
    public static final String CLASSIC = "classic";
    public static final String RANDOM = "random";
    private static final List<Theme> ALL = new ArrayList<>();
    private static final Random R = new Random();

    static {
        ThemesA.register(ALL);
        ThemesB.register(ALL);
    }

    private Themes() {}

    public static List<Theme> all() {
        return Collections.unmodifiableList(ALL);
    }

    public static Theme get(String id) {
        if (id == null) return null;
        for (Theme t : ALL) if (t.id.equalsIgnoreCase(id)) return t;
        return null;
    }

    /** classic + every theme id. */
    public static List<String> ids() {
        List<String> out = new ArrayList<>();
        out.add(CLASSIC);
        for (Theme t : ALL) out.add(t.id);
        return out;
    }

    public static String name(String id) {
        if (id == null || id.equalsIgnoreCase(RANDOM)) return "Random";
        if (id.equalsIgnoreCase(CLASSIC)) return "Classic";
        Theme t = get(id);
        return t == null ? "Random" : t.name;
    }

    public static String desc(String id) {
        if (id.equalsIgnoreCase(CLASSIC)) return "The original: light rays, shockwaves, confetti, falling loot and a rainbow sweep at level 5.";
        Theme t = get(id);
        return t == null ? "" : t.desc;
    }

    /** SHA-256 of the secret code, so the code itself is not written in the mod. */
    private static final String CODE_HASH = "c94392b1b2b6e22afe36fb94cb3c3309";

    public static boolean unlocked() {
        return Config.get().secretUnlocked;
    }

    /** True if the code typed into the secret panel is right. */
    public static boolean checkCode(String code) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(("donationfx:" + code).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) sb.append(String.format("%02x", h[i]));
            return sb.toString().equals(CODE_HASH);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean enabled(String id) {
        if (!id.equalsIgnoreCase(CLASSIC) && !unlocked()) return false;
        for (String d : Config.get().disabledThemes) if (d.equalsIgnoreCase(id)) return false;
        return true;
    }

    public static void setEnabled(String id, boolean on) {
        List<String> d = Config.get().disabledThemes;
        d.removeIf(x -> x.equalsIgnoreCase(id));
        if (!on) d.add(id);
    }

    /** Turns "random" (or an unknown/disabled name) into a real theme id. Avoids playing the same one twice in a row. */
    public static String pick(String wanted, String last) {
        if (!unlocked()) return CLASSIC; // the extra animations are locked
        if (wanted != null && !wanted.equalsIgnoreCase(RANDOM)) {
            if (wanted.equalsIgnoreCase(CLASSIC) || get(wanted) != null) return wanted.toLowerCase();
        }
        List<String> pool = new ArrayList<>();
        for (String id : ids()) if (enabled(id)) pool.add(id);
        if (pool.isEmpty()) return CLASSIC;
        if (pool.size() > 1 && last != null) pool.removeIf(x -> x.equalsIgnoreCase(last));
        return pool.get(R.nextInt(pool.size()));
    }

    /** Next choice for a level's theme button: Random, Classic, then each theme. */
    public static String next(String cur, int dir) {
        List<String> order = new ArrayList<>();
        order.add(RANDOM);
        order.addAll(ids());
        int i = 0;
        for (int k = 0; k < order.size(); k++) if (order.get(k).equalsIgnoreCase(cur)) i = k;
        return order.get(Math.floorMod(i + dir, order.size()));
    }

    /** Extra sounds that make higher levels feel bigger, whatever the theme. */
    public static void stingers(int L, List<Config.Sound> out) {
        if (L >= 1) out.add(snd("entity.player.levelup", 0.02, 0.6f, 1.0f));
        if (L >= 2) out.add(snd("block.beacon.activate", 0.05, 0.6f, 1.3f));
        if (L >= 3) out.add(snd("ui.toast.challenge_complete", 0.1, 0.8f, 1.0f));
        if (L >= 4) out.add(snd("entity.wither.spawn", 0.0, 0.35f, 1.2f));
    }
}
