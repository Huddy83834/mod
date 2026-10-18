package com.example.claimalert;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;

/**
 * Claim Alert: watches action-bar text, chat, and the tab list for wording that means you have
 * entered an enemy claim, then shows a red banner and plays a sound.
 *
 * Edit ENEMY_PHRASES / SAFE_PHRASES to match exactly what the server shows (lowercase, partial text).
 */
public class ClaimAlertClient implements ClientModInitializer {

    private static final List<String> ENEMY_PHRASES = List.of(
            "enemy claim",
            "enemy territory",
            "enemy land",
            "hostile claim"
    );

    private static final List<String> SAFE_PHRASES = List.of(
            "your claim",
            "own claim",
            "wilderness"
    );

    private static int flashTicks = 0;
    private static String lastZoneSeen = "";

    // Tab list header/footer are private fields, so they are read by type.
    private static Field tabListField;
    private static final java.util.List<Field> tabTextFields = new java.util.ArrayList<>();
    private static boolean tabLookupDone;

    @Override
    public void onInitializeClient() {
        // Action bar (overlay = true) and normal chat / system messages.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> checkText(message.getString()));

        // Red tint + big banner, drawn just under the chat.
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("claimalert", "banner"),
                (g, tracker) -> drawBanner(g));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (flashTicks > 0) flashTicks--;
            if (client.player == null) return;
            if (client.player.tickCount % 10 == 0) checkTabList(client);
        });
    }

    private static void drawBanner(GuiGraphicsExtractor g) {
        if (flashTicks <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

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

    private static void checkTabList(Minecraft mc) {
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
            if (tabListField == null) return;
            Object overlay = tabListField.get(mc.gui);
            if (overlay == null) return;
            for (Field tf : tabTextFields) {
                Object value = tf.get(overlay);
                if (value instanceof Component c) checkText(c.getString());
            }
        } catch (Exception | LinkageError ignored) {
            // The tab list is optional; action bar and chat still work.
        }
    }

    private static void checkText(String raw) {
        if (raw == null || raw.isBlank()) return;
        String lower = raw.toLowerCase(Locale.ROOT);

        for (String phrase : ENEMY_PHRASES) {
            if (lower.contains(phrase)) {
                if (!lower.equals(lastZoneSeen)) {
                    triggerAlert();
                    lastZoneSeen = lower;
                }
                return;
            }
        }
        for (String phrase : SAFE_PHRASES) {
            if (lower.contains(phrase)) {
                lastZoneSeen = lower;
                return;
            }
        }
    }

    private static void triggerAlert() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        mc.player.sendSystemMessage(
                Component.literal("⚠ ENTERING ENEMY CLAIM ⚠").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        mc.player.playSound(SoundEvents.WARDEN_LISTENING_ANGRY, 1.0f, 1.0f);
        flashTicks = 60;
    }
}
