package com.autocrafter;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** What the crafter is doing, shown next to the crafting table and saved to config/autocrafter-log.txt. */
public final class Log {
    private static final Deque<String> LINES = new ArrayDeque<>();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static String last = "";

    private Log() {}

    public static synchronized void add(String s) {
        if (s.equals(last)) return;
        last = s;
        LINES.addLast(s);
        while (LINES.size() > 8) LINES.removeFirst();
        try {
            Path p = FabricLoader.getInstance().getConfigDir().resolve("autocrafter-log.txt");
            if (Files.exists(p) && Files.size(p) > 300_000) Files.delete(p);
            Files.writeString(p, LocalTime.now().format(TIME) + "  " + s + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // the on-screen log still works
        }
    }

    public static synchronized List<String> lines() {
        return new ArrayList<>(LINES);
    }
}
