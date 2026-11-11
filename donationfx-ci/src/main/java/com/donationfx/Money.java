package com.donationfx;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reading and writing money amounts like "$1,250,000", "1.5m", "50k", "$2.3B". */
public final class Money {
    /** A number with an optional $ before it and an optional k/m/b/t after it. */
    public static final Pattern AMOUNT = Pattern.compile(
            "(\\$\\s?)?(\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?)\\s?(k|m|b|t|thousand|million|billion|trillion|mil)?(?![a-z])",
            Pattern.CASE_INSENSITIVE);

    private Money() {}

    /** Turns "50k", "$1,000,000", "1.5m" into a number. Returns -1 if it is not a number. */
    public static double parse(String s) {
        if (s == null) return -1;
        Matcher m = AMOUNT.matcher(s.trim());
        if (!m.find()) return -1;
        return value(m.group(2), m.group(3));
    }

    public static double value(String number, String suffix) {
        double v;
        try {
            v = Double.parseDouble(number.replace(",", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
        if (suffix != null) {
            switch (suffix.toLowerCase(Locale.ROOT)) {
                case "k", "thousand" -> v *= 1e3;
                case "m", "mil", "million" -> v *= 1e6;
                case "b", "billion" -> v *= 1e9;
                case "t", "trillion" -> v *= 1e12;
                default -> { }
            }
        }
        return v;
    }

    /** "$10,000,000" */
    public static String full(double v) {
        DecimalFormat f = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
        return "$" + f.format(Math.round(v));
    }

    /** "$10M", "$1.5M", "$250K" */
    public static String compact(double v) {
        String[] units = {"", "K", "M", "B", "T"};
        int u = 0;
        double x = v;
        while (Math.abs(x) >= 1000 && u < units.length - 1) {
            x /= 1000;
            u++;
        }
        String n = x >= 100 || x == Math.floor(x) ? String.valueOf((long) Math.floor(x)) : String.format(Locale.US, "%.1f", Math.floor(x * 10) / 10.0);
        if (n.endsWith(".0")) n = n.substring(0, n.length() - 2);
        return "$" + n + units[u];
    }

    public static String format(double v) {
        return Config.get().shortAmounts ? compact(v) : full(v);
    }
}
