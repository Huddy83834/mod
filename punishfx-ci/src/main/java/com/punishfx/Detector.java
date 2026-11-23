package com.punishfx;

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
    /** "From Steve: hi", "[From Steve] hi", "To Steve » hi", "(From Steve) hi" */
    private static final Pattern PM_FROM = Pattern.compile("(?i)^\\W*[\\[(]?\\s*(?:from|to)\\s+" + RANKS + "\\*?" + NAME + "\\s*(?:[\\])]|:|\u00bb|>>|>|->|\u2192|\u27a1|-|\\|)");
    /** "Steve -> me", "me -> Steve", "Steve \u2192 You" */
    private static final Pattern PM_ARROW = Pattern.compile("(?i)(?:" + NAME + "\\s*(?:->|\u2192|\u27a1|\u21d2)\\s*(?:me|you)\\b|\\b(?:me|you)\\s*(?:->|\u2192|\u27a1|\u21d2)\\s*" + NAME + ")");
    /** "+$50,000 from Steve" at the start of a line */
    private static final Pattern PLUS_MONEY = Pattern.compile("^\\W{0,3}\\+\\s?\\$");
    // ---- strict mode: the WHOLE line must be a payment notice ----
    // A real notice starts with the payer (or "You received"). When a player types the same words,
    // the server puts their own name/rank + a separator in front, so these can't match chat.
    private static final String S_NAME = "([.*]?[A-Za-z0-9_]{2,16})";
    private static final String S_AMT = "(\\$\\s?[\\d][\\d,]*(?:\\.\\d+)?\\s?[kKmMbBtTqQ]?|[\\d][\\d,]*(?:\\.\\d+)?\\s?[kKmMbBtTqQ]?\\s?(?:coins?|dollars?|money|bucks|\\$))";
    private static final String S_TAG = "(?:\\[[^\\]\\s<>\\-\u2192\u27a4\u00bb]{1,16}\\]\\s*){0,2}";
    private static final Pattern LEAD_TAG = Pattern.compile("^\\s*\\[([^\\]]{1,16})\\]");
    private static final String S_END = "\\s*[.!]?$";
    private static final Pattern[] STRICT = {
            // "Bob paid you $1M."  "Bob has sent you $1,000!"  "Bob sent $500 to you."
            Pattern.compile("(?i)^" + S_TAG + S_NAME + "\\s+(?:has\\s+|just\\s+)?(?:paid|sent|given|gave|transferred|tipped|donated)(?:\\s+you)?\\s+" + S_AMT + "(?:\\s+to\\s+you)?" + S_END),
            // "You received $1,000 from Bob."  "You have been paid $1,000 by Bob."
            Pattern.compile("(?i)^" + S_TAG + "(?:you\\s+)?(?:have\\s+)?(?:just\\s+)?(?:received|got|been\\s+(?:paid|sent|given))\\s+" + S_AMT + "\\s+(?:from|by)\\s+" + S_NAME + S_END),
            // "$1,000 has been received from Bob."  "$1,000 was sent to you by Bob."
            Pattern.compile("(?i)^" + S_TAG + S_AMT + "\\s+(?:has\\s+been\\s+|was\\s+)?(?:received|sent\\s+to\\s+you|added\\s+to\\s+your\\s+(?:account|balance))\\s+(?:from|by)\\s+" + S_NAME + S_END),
            // "+$50,000 from Bob"
            Pattern.compile("(?i)^" + S_TAG + "\\+\\s?" + S_AMT + "\\s+from\\s+" + S_NAME + S_END),
    };
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
            if (r != null && level >= 0) PunishManager.hit(r.amount(), r.player(), level, false);
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

        // 2. never a player typing or a private message
        String lower = text.toLowerCase(Locale.ROOT);
        if (looksLikeChat(text)) {
            why.append("looks like a player typing in chat");
            return null;
        }
        if (isPrivateMessage(text, lower)) {
            why.append("looks like a private message");
            return null;
        }

        // 3. built-in rules: the whole line must be a payment notice
        if (!c.looseMatching) return strict(text, why);

        // 4. old loose rules (opt-in only: a player can fake these by typing in chat)
        for (String w : c.outgoingWords) {
            if (!w.isBlank() && containsWord(lower, w.toLowerCase(Locale.ROOT))) {
                why.append("has the word \"").append(w).append("\" (not money you received)");
                return null;
            }
        }
        boolean incoming = PLUS_MONEY.matcher(text).find();
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

    /** Only accepts a line that is, start to finish, a payment notice (see {@link #STRICT}). */
    private static Result strict(String text, StringBuilder why) {
        // a leading "[Name]" that is a player is a chat/PM format, not a plugin tag
        Matcher lt = LEAD_TAG.matcher(text);
        if (lt.find() && isOnline(lt.group(1).trim())) {
            why.append("starts with a player's name in brackets (chat or a private message)");
            return null;
        }
        for (int i = 0; i < STRICT.length; i++) {
            Matcher m = STRICT[i].matcher(text);
            if (!m.matches()) continue;
            String amt = i == 0 ? m.group(2) : m.group(1);
            String player = i == 0 ? m.group(1) : m.group(2);
            if (Config.get().requireDollarSign && !amt.contains("$") && !amt.matches("(?i).*(coins?|dollars?|money|bucks)$")) {
                why.append("amount has no $");
                return null;
            }
            double amount = Money.parse(amt.replaceAll("(?i)\\s*(coins?|dollars?|money|bucks)$", ""));
            if (amount <= 0) {
                why.append("no money amount found");
                return null;
            }
            if (NOT_NAMES.contains(player.toLowerCase(Locale.ROOT))) player = UNKNOWN;
            return new Result(amount, player, "payment notice");
        }
        why.append(text.toLowerCase(Locale.ROOT).contains("paid you") || text.toLowerCase(Locale.ROOT).contains("received")
                ? "not a whole-line payment notice (something is in front of or after it, e.g. a player typing it in chat)"
                : "not a payment notice");
        return null;
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
                PunishFxClient.LOG.warn("Punish FX: bad pattern {}", s);
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

    private static boolean isPrivateMessage(String text, String lower) {
        if (PM_FROM.matcher(text).find() || PM_ARROW.matcher(text).find()) return true;
        for (String w : Config.get().privateMessageWords) {
            if (!w.isBlank() && lower.contains(w.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
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
            boolean ok = Config.get().requireDollarSign ? dollar || word : dollar || suffix || word || bigComma;
            if (ok) {
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
            out.add("# Money messages Punish FX saw, newest last. If a payment was NOT detected, copy its text");
            out.add("# and use: /donationfx simulate <text>   to see why, or add a pattern to customPatterns in donationfx.json.");
            out.addAll(SEEN);
            Files.write(p, out, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // not important
        }
    }
}
