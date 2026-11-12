package com.donationfx;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Writes every donation the mod sees into a Google Sheet (through a small Google Apps Script web app),
 * and into config/donationfx-donations.csv as a backup. Sending happens in the background, and
 * donations that could not be sent (no internet, sheet not set up yet) are kept and retried.
 */
public final class SheetLog {
    /** Must match TOKEN in the Google Apps Script. Stops random people from adding rows to the sheet. */
    public static final String TOKEN = "dfx-log-7Q2mK9wZ";
    /** A sheet link built into the mod, so every copy logs to the same sheet. Empty = use the settings. */
    public static final String BUILT_IN_URL = "";

    private static final Gson GSON = new Gson();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ConcurrentLinkedDeque<String> PENDING = new ConcurrentLinkedDeque<>();
    private static final AtomicBoolean SENDING = new AtomicBoolean(false);
    private static ScheduledExecutorService worker;
    private static HttpClient http;

    public static volatile String status = "Not set up yet.";
    public static volatile int sent = 0;

    private SheetLog() {}

    public static String url() {
        String u = Config.get().sheetUrl;
        if (u != null && !u.isBlank()) return u.trim();
        return BUILT_IN_URL;
    }

    public static boolean ready() {
        String u = url();
        return Config.get().sheetLogging && u.startsWith("https://");
    }

    public static int pending() {
        return PENDING.size();
    }

    public static synchronized void start() {
        if (worker != null) return;
        http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL) // Google answers with a redirect
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        worker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DonationFX-Sheet");
            t.setDaemon(true);
            return t;
        });
        loadPending();
        // retry anything that could not be sent, every 45 seconds
        worker.scheduleWithFixedDelay(SheetLog::flush, 15, 45, TimeUnit.SECONDS);
        if (!url().isEmpty()) status = "Ready.";
    }

    /** Called (on the game thread) when a donation is seen. */
    public static void log(double amount, String sender, int level, String message, boolean test) {
        Config c = Config.get();
        Minecraft mc = Minecraft.getInstance();
        String receiver = mc.player != null ? mc.player.getGameProfile().name() : mc.getUser().getName();
        String server = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "singleplayer";
        String levelName = level >= 0 && level < c.tiers.size() ? c.tiers.get(level).title : "below level 1";
        String time = LocalDateTime.now().format(TIME);

        JsonObject o = new JsonObject();
        o.addProperty("token", TOKEN);
        o.addProperty("receiver", receiver);
        o.addProperty("sender", sender);
        o.addProperty("amount", amount);
        o.addProperty("level", level + 1);
        o.addProperty("levelName", levelName);
        o.addProperty("server", server);
        o.addProperty("message", message == null ? "" : message);
        o.addProperty("clientTime", time);
        o.addProperty("version", FabricLoader.getInstance().getModContainer(DonationFxClient.MOD_ID)
                .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?"));
        o.addProperty("test", test);

        if (!test) backup(time, receiver, sender, amount, level + 1, server, message);
        if (!c.sheetLogging && !test) return;
        PENDING.addLast(GSON.toJson(o));
        while (PENDING.size() > 1000) PENDING.removeFirst();
        savePending();
        if (worker != null) worker.execute(SheetLog::flush);
    }

    /** Sends everything waiting, oldest first. Stops at the first failure and tries again later. */
    private static void flush() {
        if (!SENDING.compareAndSet(false, true)) return;
        try {
            String u = url();
            if (!u.startsWith("https://")) {
                if (!PENDING.isEmpty()) status = "No Google Sheet link set. " + PENDING.size() + " donation(s) waiting.";
                return;
            }
            if (!Config.get().sheetLogging) {
                // only test rows are sent while logging is off
                PENDING.removeIf(s -> !s.contains("\"test\":true"));
            }
            boolean changed = false;
            while (!PENDING.isEmpty()) {
                String body = PENDING.peekFirst();
                String err = post(u, body);
                if (err != null) {
                    status = "Could not send (" + err + "). Will retry. " + PENDING.size() + " waiting.";
                    break;
                }
                PENDING.pollFirst();
                changed = true;
                sent++;
                status = "Sent OK at " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " (" + sent + " this session).";
            }
            if (changed) savePending();
        } catch (Exception e) {
            status = "Error: " + e.getClass().getSimpleName();
        } finally {
            SENDING.set(false);
        }
    }

    /** Returns null when it worked, or a short reason. */
    static String post(String url, String body) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 400) return "HTTP " + res.statusCode();
            String text = res.body() == null ? "" : res.body().trim();
            if (text.startsWith("{")) {
                JsonObject o = JsonParser.parseString(text).getAsJsonObject();
                if (o.has("ok") && o.get("ok").getAsBoolean()) return null;
                return o.has("error") ? o.get("error").getAsString() : "sheet said no";
            }
            if (text.contains("<html") || text.contains("<!DOCTYPE")) return "the link is not a deployed web app (or access is not 'Anyone')";
            return "unexpected answer";
        } catch (IllegalArgumentException e) {
            return "bad link";
        } catch (Exception e) {
            return e.getClass().getSimpleName();
        }
    }

    public static void sendTest() {
        Minecraft mc = Minecraft.getInstance();
        String me = mc.player != null ? mc.player.getGameProfile().name() : "Tester";
        log(12345, me, 0, "Donation FX test row", true);
        status = url().startsWith("https://") ? "Sending test..." : "Paste your Google Sheet web app link first.";
    }

    // ------------------------------------------------------------------ files

    private static Path dir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    private static void backup(String time, String receiver, String sender, double amount, int level, String server, String message) {
        try {
            Path p = dir().resolve("donationfx-donations.csv");
            if (!Files.exists(p)) {
                Files.writeString(p, "time,receiver,sender,amount,level,server,message" + System.lineSeparator(), StandardCharsets.UTF_8);
            }
            String line = String.join(",", csv(time), csv(receiver), csv(sender), String.valueOf((long) amount), String.valueOf(level), csv(server), csv(message));
            Files.writeString(p, line + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // backup only
        }
    }

    private static String csv(String s) {
        if (s == null) return "";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static synchronized void savePending() {
        try {
            Files.writeString(dir().resolve("donationfx-pending.json"), GSON.toJson(new ArrayList<>(PENDING)), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // retried next time
        }
    }

    private static void loadPending() {
        try {
            Path p = dir().resolve("donationfx-pending.json");
            if (!Files.exists(p)) return;
            List<String> list = GSON.fromJson(Files.readString(p), new TypeToken<List<String>>() {}.getType());
            if (list != null) PENDING.addAll(list);
            if (!PENDING.isEmpty()) status = PENDING.size() + " donation(s) waiting to be sent.";
        } catch (Exception ignored) {
            // start fresh
        }
    }
}
