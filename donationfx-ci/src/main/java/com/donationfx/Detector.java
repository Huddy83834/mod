package com.donationfx;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Finds "someone paid you" messages in chat. */
public final class Detector {
    public static final String UNKNOWN = "Someone";

    public record Result(double amount, String player, String why) {}

    private static final Pattern FORMAT = Pattern.compile("(?i)\u00a7[0-9a-fk-orx]");
    private static final String NAME = "([A-Za-z0-9_]{2,16})";
    private static final String RANKS = "(?:[\\[(<{][^\\])>}]{0,24}[\\])>}]\\s*)*";
    private static final Pattern FROM = Pattern.compile("(?i)\\b(?:from|by)\\s+" + RANKS + "\\*?" + NAME);
    private static final Pattern SUBJECT = Pattern.compile("(?i)^\\W*" + RANKS + "\\*?" + NAME
            + "\\s+(?:has\\s+|just\\s+)?(?:paid|sent|gave|given|donated|transferred|tipped)\\b");
    private static final Pattern WHISPER = Pattern.compile("(?i)^\\W*\\[?\\s*" + NAME + "\\s*(?:->|\u2192|\u00bb)\\s*(?:me|you)\\b");
    private static final Pattern CHAT = Pattern.compile("^\\W*" + RANKS + "[^:\u00bb>]{0,30}?" + NAME + "\\s*(?::|\u00bb|>>|>|\u2503|\\|)\\s+");
    private static final Pattern VANILLA_CHAT = Pattern.compile("^<" + NAME + ">\\s");
    private static final java.util.Set<String> NOT_NAMES = java.util.Set.of(
            "you", "your", "the", "a", "an", "server", "console", "bank", "player", "someone", "money", "admin", "system");

    private static final Map<String, Pattern> CUSTOM = new HashMap<>();
    private static final Deque<String> SEEN = new ArrayDeque<>();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static String lastText = "";
    private static long lastAt = 0;

    public static String lastMoneyLine = "";
    public static String lastMoneyVerdict = "";

    private Detector() {}

    public static String clean(String s) {
        return FORMAT.matcher(s).replaceAll("").replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    /** A chat or action bar line arrived. */
    public static void onMessage(String raw, boolean actionBar) {
        Config c = Config.get();
        if (!c.enabled) return;
        if (actionBar && !c.readActionBar) return;
        String text = clean(raw);
        if (text.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (text.equals(lastText) && now - lastAt < 1500) return; // the same line twice (chat + action bar)
        lastText = text;
        lastAt = now;

        StringBuilder why = new StringBuilder();
        Result r = detect(text, why);
        boolean money = r != null || hasMoney(text);
        if (money) {
            int level = r == null ? -1 : c.tierFor(r.amount());
            String verdict = r == null ? "ignored: " + why
                    : level < 0 ? "below level 1 (" + Money.full(r.amount()) + " from " + r.player() + ")"
                    : "LEVEL " + (level + 1) + ": " + Money.full(r.amount()) + " from " + r.player();
            lastMoneyLine = text;
            lastMoneyVerdict = verdict;
            remember(text, verdict, actionBar);
            if (r != null && level >= 0) Fx.donate(r.amount(), r.player(), level, false);
        }
    }

    /** Tests a line without playing anything (for /donationfx simulate). */
    public static Result detect(String text, StringBuilder why) {
        Config c = Config.get();
        // 1. the player's own patterns
        for (String src : c.customPatterns) {
            Pattern p = custom(src);
            if (p == null) continue;
            Matcher m = p.matcher(text);
            if (!m.find()) continue;
            double amount = Money.parse(group(m, "amount"));
            if (amount <= 0) continue;
            String player = group(m, "player");
            return new Result(amount, player == null || player.isBlank() ? UNKNOWN : player.trim(), "custom pattern");
        }

        // 2. built-in rules
        String lower = text.toLowerCase(Locale.ROOT);
        if (c.ignorePlayerChat && looksLikeChat(text)) {
            why.append("looks like a player typing in chat");
            return null;
        }
        for (String w : c.outgoingWords) {
            if (!w.isBlank() && containsWord(lower, w.toLowerCase(Locale.ROOT))) {
                why.append("has the word \"").append(w).append("\" (not money you received)");
                return null;
            }
        }
        boolean incoming = false;
        for (String w : c.incomingWords) {
            if (!w.isBlank() && containsWord(lower, w.toLowerCase(Locale.ROOT))) {
                incoming = true;
                break;
            }
        }
        if (!incoming) {
            why.append("no word like \"received\" or \"paid you\"");
            return null;
        }
        double amount = findAmount(text);
        if (amount <= 0) {
            why.append("no money amount found");
            return null;
        }
        String player = findPlayer(text);
        return new Result(amount, player, "built-in rules");
    }

    private static String group(Matcher m, String name) {
        try {
            return m.group(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Pattern custom(String src) {
        if (src == null || src.isBlank()) return null;
        return CUSTOM.computeIfAbsent(src, s -> {
            try {
                return Pattern.compile(s, Pattern.CASE_INSENSITIVE);
            } catch (PatternSyntaxException e) {
                DonationFxClient.LOG.warn("Donation FX: bad pattern {}", s);
                return null;
            }
        });
    }

    public static void clearCache() {
        CUSTOM.clear();
    }

    private static boolean containsWord(String lower, String w) {
        int i = lower.indexOf(w);
        while (i >= 0) {
            boolean before = i == 0 || !Character.isLetter(lower.charAt(i - 1));
            int e = i + w.length();
            boolean after = e >= lower.length() || !Character.isLetter(lower.charAt(e));
            if (before && after) return true;
            i = lower.indexOf(w, i + 1);
        }
        return false;
    }

    /** True if the line is a player talking (their name, then ":" or "»"), or a private message. */
    private static boolean looksLikeChat(String text) {
        if (VANILLA_CHAT.matcher(text).find()) return true;
        Matcher wm = WHISPER.matcher(text);
        if (wm.find()) return true;
        Matcher m = CHAT.matcher(text);
        if (!m.find()) return false;
        String name = m.group(1);
        return isOnline(name);
    }

    private static boolean isOnline(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return false;
        for (PlayerInfo pi : mc.getConnection().getOnlinePlayers()) {
            if (pi.getProfile().name().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private static boolean hasMoney(String text) {
        return findAmount(text) > 0;
    }

    /** The first amount that is clearly money: has a $, or k/m/b, or is followed by "coins"/"money"/"dollars". */
    private static double findAmount(String text) {
        Matcher m = Money.AMOUNT.matcher(text);
        while (m.find()) {
            boolean dollar = m.group(1) != null;
            boolean suffix = m.group(3) != null;
            String after = text.substring(m.end()).toLowerCase(Locale.ROOT).trim();
            boolean word = after.startsWith("coins") || after.startsWith("coin") || after.startsWith("money")
                    || after.startsWith("dollars") || after.startsWith("$") || after.startsWith("bucks");
            boolean bigComma = m.group(2).contains(",");
            if (dollar || suffix || word || bigComma) {
                double v = Money.value(m.group(2), m.group(3));
                if (v > 0) return v;
            }
        }
        return -1;
    }

    private static String findPlayer(String text) {
        Matcher m = FROM.matcher(text);
        while (m.find()) {
            String n = m.group(1);
            if (!NOT_NAMES.contains(n.toLowerCase(Locale.ROOT))) return n;
        }
        m = SUBJECT.matcher(text);
        if (m.find() && !NOT_NAMES.contains(m.group(1).toLowerCase(Locale.ROOT))) return m.group(1);
        // any online player's name in the line
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            String me = mc.player == null ? "" : mc.player.getGameProfile().name();
            for (PlayerInfo pi : mc.getConnection().getOnlinePlayers()) {
                String n = pi.getProfile().name();
                if (n.length() >= 3 && !n.equalsIgnoreCase(me) && Pattern.compile("\\b" + Pattern.quote(n) + "\\b", Pattern.CASE_INSENSITIVE).matcher(text).find()) {
                    return n;
                }
            }
        }
        return UNKNOWN;
    }

    // ------------------------------------------------------------------ seen lines file

    private static void remember(String text, String verdict, boolean actionBar) {
        if (!Config.get().saveSeenLines) return;
        SEEN.addLast(LocalTime.now().format(TIME) + "  [" + verdict + "]" + (actionBar ? " (action bar)" : "") + "  " + text);
        while (SEEN.size() > 150) SEEN.removeFirst();
        try {
            Path p = FabricLoader.getInstance().getConfigDir().resolve("donationfx-seen.txt");
            List<String> out = new ArrayList<>();
            out.add("# Money messages Donation FX saw, newest last. If a payment was NOT detected, copy its text");
            out.add("# and use: /donationfx simulate <text>   to see why, or add a pattern to customPatterns in donationfx.json.");
            out.addAll(SEEN);
            Files.write(p, out, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // not important
        }
    }
}
