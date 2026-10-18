package com.example.claimalert;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Claim Alert. Reads the tab list (header, footer and its entries), the action bar and chat. The
 * moment any of that says you are standing in an enemy claim, it flashes a red banner, plays a
 * sound and keeps a small red "ENEMY CLAIM" tag on screen until you leave.
 *
 * It does not need exact wording: it understands "enemy", "hostile", "claimed by <someone else>",
 * "<someone else>'s claim" and so on. Extra phrases can be added in config/claimalert.json, and
 * everything claim-related it sees is saved to config/claimalert-seen.txt so the matching can be
 * tuned to the server.
 */
public class ClaimAlertClient implements ClientModInitializer {

    /** Settings file (created on first run). All phrases are lowercase fragments. */
    public static class Config {
        /** Any line containing one of these means you are in an enemy claim. */
        public List<String> enemyPhrases = new ArrayList<>(List.of(
                "enemy claim", "enemy territory", "enemy land", "hostile claim", "enemy base"));
        /** Any line containing one of these means you are safe (checked first). */
        public List<String> safePhrases = new ArrayList<>(List.of(
                "your claim", "own claim", "my claim", "wilderness", "unclaimed", "safezone", "safe zone",
                "no claim", "not claimed"));
        public boolean showTag = true;
        public boolean playSound = true;
        public boolean chatMessage = true;
        public boolean readTabEntries = true;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config config = new Config();

    private static int flashTicks = 0;
    private static boolean inEnemy = false;
    private static boolean tabPrimed = false;
    private static long lastAlertAt = 0L;
    private static String lastSeenDump = "";

    // The tab list header/footer are private fields, so they are found by type.
    private static Field tabListField;
    private static final List<Field> tabTextFields = new ArrayList<>();
    private static boolean tabLookupDone;

    @Override
    public void onInitializeClient() {
        loadConfig();

        // Action bar (overlay = true) and chat / system messages: one-off alerts.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            String text = message.getString();
            if (isEnemyLine(text.toLowerCase(Locale.ROOT)) && System.currentTimeMillis() - lastAlertAt > 4000L) {
                triggerAlert();
            }
        });

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("claimalert", "banner"),
                (g, tracker) -> draw(g));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (flashTicks > 0) flashTicks--;
            if (client.player == null) {
                inEnemy = false;
                tabPrimed = false;
                return;
            }
            if (client.player.tickCount % 5 == 0) checkTabList(client);
        });
    }

    // ------------------------------------------------------------------ config

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("claimalert.json");
    }

    private static void loadConfig() {
        try {
            Path p = configPath();
            if (Files.exists(p)) {
                Config c = GSON.fromJson(Files.readString(p), Config.class);
                if (c != null) {
                    if (c.enemyPhrases == null) c.enemyPhrases = new ArrayList<>();
                    if (c.safePhrases == null) c.safePhrases = new ArrayList<>();
                    config = c;
                }
            } else {
                Files.writeString(p, GSON.toJson(config));
            }
        } catch (Exception ignored) {
            // keep defaults
        }
    }

    // ------------------------------------------------------------------ drawing

    private static void draw(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        if (flashTicks > 0) {
            int alpha = Math.min(90, flashTicks * 5);
            g.fill(0, 0, w, h, (alpha << 24) | 0xFF0000);

            String banner = "ENEMY CLAIM";
            float scale = 3.0f;
            g.pose().pushMatrix();
            g.pose().scale(scale, scale);
            int tw = mc.font.width(banner);
            int x = (int) ((w / scale - tw) / 2);
            int y = (int) ((h / scale) / 2 - 20);
            g.text(mc.font, banner, x + 1, y + 1, 0xFF000000, false);
            g.text(mc.font, banner, x, y, 0xFFFF2222, false);
            g.pose().popMatrix();
        }

        if (inEnemy && config.showTag) {
            String tag = "ENEMY CLAIM";
            int tw = mc.font.width(tag);
            int x = (w - tw) / 2;
            int y = 18;
            g.fill(x - 8, y - 4, x + tw + 8, y + 13, 0xCC8B0000);
            g.fill(x - 8, y - 4, x + tw + 8, y - 3, 0xFFFF2222);
            g.text(mc.font, tag, x, y, 0xFFFFFFFF, false);
        }
    }

    // ------------------------------------------------------------------ reading the tab list

    private static void checkTabList(Minecraft mc) {
        List<String> lines = new ArrayList<>();
        try {
            if (!tabLookupDone) {
                tabLookupDone = true;
                for (Field f : mc.gui.getClass().getDeclaredFields()) {
                    if (f.getType().getSimpleName().equals("PlayerTabOverlay")) {
                        f.setAccessible(true);
                        tabListField = f;
                        for (Field tf : f.getType().getDeclaredFields()) {
                            if (Component.class.isAssignableFrom(tf.getType())) {
                                tf.setAccessible(true);
                                tabTextFields.add(tf);
                            }
                        }
                    }
                }
            }
            if (tabListField != null) {
                Object overlay = tabListField.get(mc.gui);
                if (overlay != null) {
                    for (Field tf : tabTextFields) {
                        Object value = tf.get(overlay);
                        if (value instanceof Component c) addLines(lines, c.getString());
                    }
                }
            }
        } catch (Exception | LinkageError ignored) {
            // header/footer are optional
        }

        try {
            if (config.readTabEntries && mc.getConnection() != null) {
                for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                    Component name = info.getTabListDisplayName();
                    if (name != null) addLines(lines, name.getString());
                }
            }
        } catch (Exception | LinkageError ignored) {
            // entries are optional
        }

        saveSeen(lines);

        boolean now = false;
        for (String line : lines) {
            if (isEnemyLine(line.toLowerCase(Locale.ROOT))) {
                now = true;
                break;
            }
        }

        // The first read after joining only sets the state, so joining inside a claim is still announced
        // only if it really is enemy territory.
        if (now && !inEnemy) {
            triggerAlert();
        }
        inEnemy = now;
        tabPrimed = true;
    }

    private static void addLines(List<String> out, String text) {
        if (text == null || text.isBlank()) return;
        for (String part : text.split("\\R")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
    }

    // ------------------------------------------------------------------ deciding

    private static boolean isEnemyLine(String lower) {
        if (lower.isBlank()) return false;
        for (String s : config.safePhrases) {
            if (!s.isBlank() && lower.contains(s.toLowerCase(Locale.ROOT))) return false;
        }
        for (String s : config.enemyPhrases) {
            if (!s.isBlank() && lower.contains(s.toLowerCase(Locale.ROOT))) return true;
        }
        if (lower.contains("enemy") || lower.contains("hostile")) {
            return lower.contains("claim") || lower.contains("territory") || lower.contains("land")
                    || lower.contains("zone") || lower.contains("chunk") || lower.contains("base");
        }
        // "claimed by Steve", "owned by Steve", "Steve's claim": someone who is not you.
        boolean ownership = lower.contains("claimed by") || lower.contains("owned by") || lower.contains("owner:")
                || lower.contains("'s claim") || lower.contains("’s claim") || lower.contains("territory of")
                || lower.contains("land of");
        if (ownership) {
            Minecraft mc = Minecraft.getInstance();
            String me = mc.player == null ? "" : mc.player.getName().getString().toLowerCase(Locale.ROOT);
            return me.isEmpty() || !lower.contains(me);
        }
        return false;
    }

    private static void triggerAlert() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        lastAlertAt = System.currentTimeMillis();
        if (config.chatMessage) {
            mc.player.sendSystemMessage(
                    Component.literal("⚠ ENTERING ENEMY CLAIM ⚠").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
        if (config.playSound) mc.player.playSound(SoundEvents.WARDEN_LISTENING_ANGRY, 1.0f, 1.0f);
        flashTicks = 60;
    }

    // ------------------------------------------------------------------ what the mod has seen

    /** Saves every claim-related line so the matching can be tuned to the server's wording. */
    private static void saveSeen(List<String> lines) {
        Set<String> interesting = new LinkedHashSet<>();
        for (String l : lines) {
            String lower = l.toLowerCase(Locale.ROOT);
            if (lower.contains("claim") || lower.contains("territory") || lower.contains("zone")
                    || lower.contains("wilderness") || lower.contains("enemy") || lower.contains("owner")
                    || lower.contains("faction") || lower.contains("land") || lower.contains("chunk")) {
                interesting.add(l);
            }
        }
        String dump = String.join("\n", interesting);
        if (dump.equals(lastSeenDump) || dump.isEmpty()) return;
        lastSeenDump = dump;
        try {
            Files.writeString(FabricLoader.getInstance().getConfigDir().resolve("claimalert-seen.txt"),
                    "Claim-related tab list lines seen most recently:\n\n" + dump + "\n");
        } catch (Exception ignored) {
            // not important
        }
    }
}
