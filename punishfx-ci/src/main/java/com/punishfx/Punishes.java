package com.punishfx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** The list of every punishment, with how each one is categorized so the manager knows how to run it. */
public final class Punishes {
    /** Groups used by the punishments screen. */
    public enum Cat {
        MOVEMENT("Movement", 0x3DDC84), VIEW("Camera & view", 0x4FB8F7), SCREEN("Screen & sound", 0xC77DFF),
        HOTBAR("Hotbar & items", 0x3EE0C6), ACTION("Extreme", 0xFFAA33);
        public final String label;
        public final int color;
        Cat(String label, int color) { this.label = label; this.color = color; }
    }

    public static final class P {
        public final String id, name, desc;
        public final Cat cat;
        /** true for the two that leave/close the game; off by default and ask for a cancel countdown. */
        public final boolean extreme;
        /** Never picked by "All": you have to tick it for a level yourself (extreme ones, and anything that costs you items). */
        public final boolean optIn;

        P(String id, String name, String desc, Cat cat, boolean extreme, boolean optIn) {
            this.id = id;
            this.name = name;
            this.desc = desc;
            this.cat = cat;
            this.extreme = extreme;
            this.optIn = optIn || extreme;
        }
    }

    private static final List<P> ALL = new ArrayList<>();
    private static final Random R = new Random();

    static {
        // movement (KeyboardInputMixin)
        add("inverted_controls", "Inverted Controls", "W walks backwards, A goes right. Everything is flipped.", Cat.MOVEMENT);
        add("frozen", "Frozen", "You can't move or jump at all.", Cat.MOVEMENT);
        add("sluggish", "Sluggish", "You crawl along at a third of your normal speed.", Cat.MOVEMENT);
        add("drunk", "Drunk", "Nausea warp, a swaying tilted camera and stumbling feet.", Cat.MOVEMENT);
        // camera & view (CameraMixin / GameRendererMixin)
        add("spin", "Spin Cycle", "Your camera spins round and round.", Cat.VIEW);
        add("jitter", "Jitter", "Your whole view shakes and judders nonstop.", Cat.VIEW);
        add("glitch", "Glitch", "The view snaps back and forth while the screen tears.", Cat.VIEW);
        add("tunnel_vision", "Tunnel Vision", "Your field of view clamps down into a narrow tunnel.", Cat.VIEW);
        add("mirror", "Mirror World", "The world flips left-to-right, and so do your strafe keys and mouse.", Cat.VIEW);
        add("upside_down", "Upside Down", "Your camera rolls over onto its head.", Cat.VIEW);
        add("front_camera", "Front Camera", "Stuck in third person, staring at your own face.", Cat.VIEW);
        // screen
        add("negative", "Inverted Colors", "Every colour on your screen is truly inverted.", Cat.SCREEN);
        add("disco_eyes", "Disco Eyes", "Your whole screen cycles through RGB.", Cat.SCREEN);
        add("flashbang", "Flashbang", "A bang, then a long blinding white-out.", Cat.SCREEN);
        add("blackout", "Blackout", "The screen goes pitch black.", Cat.SCREEN);
        add("fog", "Blinding Fog", "A thick grey fog rolls in.", Cat.SCREEN);
        add("no_hud", "HUD Gone", "Your hotbar, health and crosshair vanish.", Cat.SCREEN);
        add("fake_lag", "Lag Spike", "Your game genuinely stutters and freezes.", Cat.SCREEN);
        add("language", "Language Swap", "The game switches to Pirate, Upside-Down, LOLCAT or Shakespeare.", Cat.SCREEN);
        add("chipmunk", "Chipmunk", "Every sound is squeaky, or turns into goat screams.", Cat.SCREEN);
        // hotbar & items
        add("possessed_hotbar", "Possessed Hotbar", "Your selected slot keeps jumping around on its own.", Cat.HOTBAR);
        add("hotbar_chaos", "Hotbar Chaos", "Every item in your hotbar looks like something random. Only you see it.", Cat.HOTBAR);
        ALL.add(new P("butterfingers", "Butterfingers", "You really drop items, and others can grab them! Only runs if you tick it.", Cat.HOTBAR, false, true));
        // extreme (off by default, with a cancel countdown)
        addExtreme("leave_server", "Leave Server", "Disconnects you from the server. Off by default.");
        addExtreme("close_game", "Close Minecraft", "Quits the game after a 5-second cancel window. Off by default.");
    }

    private Punishes() {}

    private static void add(String id, String name, String desc, Cat cat) {
        ALL.add(new P(id, name, desc, cat, false, false));
    }

    private static void addExtreme(String id, String name, String desc) {
        ALL.add(new P(id, name, desc, Cat.ACTION, true, true));
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
            for (P p : ALL) if (!p.optIn) out.add(p.id);
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
