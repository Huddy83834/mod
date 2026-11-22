package com.punishfx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** The list of every punishment, with how each one is categorized so the manager knows how to run it. */
public final class Punishes {
    public enum Cat { EFFECT, CONTROL, CAMERA, HUD, VISUAL, ACTION }

    public static final class P {
        public final String id, name, desc;
        public final Cat cat;
        /** true for the two that leave/close the game; off by default and ask for a cancel countdown. */
        public final boolean extreme;

        P(String id, String name, String desc, Cat cat, boolean extreme) {
            this.id = id;
            this.name = name;
            this.desc = desc;
            this.cat = cat;
            this.extreme = extreme;
        }
    }

    private static final List<P> ALL = new ArrayList<>();
    private static final Random R = new Random();

    static {
        // effects
        add("nausea", "Nausea", "The whole world warps and spins.", Cat.EFFECT);
        add("sluggish", "Sluggish", "Your movement is dragged down to a crawl.", Cat.EFFECT);
        // control (movement mixin)
        add("inverted_controls", "Inverted Controls", "Forward is back, left is right.", Cat.CONTROL);
        add("frozen", "Frozen", "You can't move at all.", Cat.CONTROL);
        add("drunk", "Drunk", "Stumbling, swaying and queasy. Try to walk straight.", Cat.CONTROL);
        // camera / view
        add("spin", "Spin Cycle", "Your camera spins out of your control.", Cat.CAMERA);
        add("jitter", "Jitter", "Your whole view shakes and judders.", Cat.CAMERA);
        add("glitch", "Glitch", "The view snaps and slides back and forth.", Cat.CAMERA);
        // hud
        add("no_hud", "HUD Gone", "Your whole HUD disappears.", Cat.HUD);
        // view / render
        add("negative", "Inverted Colors", "Your colours are actually inverted.", Cat.VISUAL);
        add("tunnel_vision", "Tunnel Vision", "Your FOV clamps down to a narrow tunnel.", Cat.VISUAL);
        add("disco_eyes", "Disco Eyes", "Your whole screen cycles through RGB.", Cat.VISUAL);
        add("fake_lag", "Lag Spike", "Your game actually stutters and freezes.", Cat.VISUAL);
        add("flashbang", "Flashbang", "A blinding white flash fills the screen.", Cat.VISUAL);
        add("blackout", "Blackout", "The screen goes pitch black.", Cat.VISUAL);
        add("fog", "Blinding Fog", "A thick dark fog rolls in.", Cat.VISUAL);
        // actions (extreme, off by default, with a cancel countdown)
        addExtreme("leave_server", "Leave Server", "Disconnects you from the server (off by default).");
        addExtreme("close_game", "Close Minecraft", "Quits the game, with a 5-second cancel (off by default).");
    }

    private Punishes() {}

    private static void add(String id, String name, String desc, Cat cat) {
        ALL.add(new P(id, name, desc, cat, false));
    }

    private static void addExtreme(String id, String name, String desc) {
        ALL.add(new P(id, name, desc, Cat.ACTION, true));
    }

    public static List<P> all() {
        return Collections.unmodifiableList(ALL);
    }

    public static List<String> ids() {
        List<String> out = new ArrayList<>();
        for (P p : ALL) out.add(p.id);
        return out;
    }

    public static P get(String id) {
        if (id == null) return null;
        for (P p : ALL) if (p.id.equalsIgnoreCase(id)) return p;
        return null;
    }

    public static String name(String id) {
        P p = get(id);
        return p == null ? id : p.name;
    }

    public static String desc(String id) {
        P p = get(id);
        return p == null ? "" : p.desc;
    }

    /** Resolve a level's pool to concrete punishment ids (empty pool = everything except the extreme two). */
    public static List<String> poolFor(Config.Tier tier) {
        List<String> out = new ArrayList<>();
        if (tier != null && tier.pool != null && !tier.pool.isEmpty()) {
            for (String id : tier.pool) if (get(id) != null) out.add(id.toLowerCase());
        } else {
            for (P p : ALL) if (!p.extreme) out.add(p.id);
        }
        return out;
    }

    /** Pick up to n distinct punishments from a level's pool. */
    public static List<String> pick(Config.Tier tier, int n) {
        List<String> pool = new ArrayList<>(poolFor(tier));
        Collections.shuffle(pool, R);
        List<String> out = new ArrayList<>();
        for (String id : pool) {
            if (out.size() >= n) break;
            out.add(id);
        }
        return out;
    }
}
