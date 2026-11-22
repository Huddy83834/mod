package com.punishfx;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Punish FX: when someone donates to you, the game punishes you. Client-side only. */
public class PunishFxClient implements ClientModInitializer {
    public static final String MOD_ID = "punishfx";
    public static final Logger LOG = LoggerFactory.getLogger("Punish FX");

    private static KeyMapping settingsKey;
    private static KeyMapping toggleKey;
    private static KeyMapping cancelKey;
    private static boolean openSettings = false;

    @Override
    public void onInitializeClient() {
        Config.load();

        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        settingsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.punishfx.settings", InputConstants.Type.KEYSYM, InputConstants.KEY_K, cat));
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.punishfx.toggle", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), cat));
        cancelKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.punishfx.cancel", InputConstants.Type.KEYSYM, InputConstants.KEY_BACKSLASH, cat));

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            try {
                Detector.onMessage(message.getString(), overlay);
            } catch (Exception e) {
                LOG.warn("Punish FX: could not read a message", e);
            }
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "overlay"), (g, delta) -> {
            try {
                PunishOverlay.render(g, delta);
            } catch (Exception e) {
                LOG.warn("Punish FX: overlay failed", e);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (settingsKey.consumeClick()) {
                if (client.screen == null) client.setScreen(new PunishScreen(null));
            }
            while (toggleKey.consumeClick()) {
                Config c = Config.get();
                c.enabled = !c.enabled;
                Config.save();
                if (client.player != null) client.player.displayClientMessage(Component.literal("Punish FX: " + (c.enabled ? "ON" : "OFF")), true);
            }
            while (cancelKey.consumeClick()) PunishManager.cancelAll();
            if (openSettings) {
                openSettings = false;
                client.setScreen(new PunishScreen(null));
            }
            try {
                PunishManager.tick(client);
            } catch (Exception e) {
                LOG.warn("Punish FX: tick failed", e);
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            dispatcher.register(command("punishfx"));
            dispatcher.register(command("pfx"));
        });
    }

    /** The key currently bound to "cancel punishments", as the player sees it. */
    public static String cancelKeyName() {
        return keyName(cancelKey, "\\");
    }

    /** The key currently bound to "open Punish FX settings". */
    public static String settingsKeyName() {
        return keyName(settingsKey, "K");
    }

    private static String keyName(KeyMapping k, String fallback) {
        try {
            String s = k == null ? "" : k.getTranslatedKeyMessage().getString();
            return s.isBlank() ? fallback : s;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static int say(CommandContext<FabricClientCommandSource> ctx, String s) {
        ctx.getSource().sendFeedback(Component.literal("§c[Punish FX]§r " + s));
        return 1;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> command(String name) {
        return ClientCommandManager.literal(name)
                .executes(ctx -> {
                    openSettings = true;
                    return 1;
                })
                .then(ClientCommandManager.literal("on").executes(ctx -> {
                    Config.get().enabled = true;
                    Config.save();
                    return say(ctx, "ON");
                }))
                .then(ClientCommandManager.literal("off").executes(ctx -> {
                    Config.get().enabled = false;
                    Config.save();
                    PunishManager.cancelAll();
                    return say(ctx, "OFF");
                }))
                .then(ClientCommandManager.literal("cancel").executes(ctx -> {
                    PunishManager.cancelAll();
                    return say(ctx, "Cancelled.");
                }))
                .then(ClientCommandManager.literal("stop").executes(ctx -> {
                    PunishManager.cancelAll();
                    return say(ctx, "Stopped everything.");
                }))
                .then(ClientCommandManager.literal("reload").executes(ctx -> {
                    Config.load();
                    Detector.clearCache();
                    return say(ctx, "Settings reloaded.");
                }))
                .then(ClientCommandManager.literal("list").executes(ctx -> {
                    StringBuilder sb = new StringBuilder("Punishments: ");
                    for (String id : Punishes.ids()) sb.append(id).append(", ");
                    return say(ctx, sb.substring(0, sb.length() - 2));
                }))
                .then(ClientCommandManager.literal("test")
                        .executes(ctx -> {
                            int l = (int) (Math.random() * Config.get().tiers.size());
                            PunishManager.hit(Config.get().tiers.get(l).minAmount, me(), l, true);
                            return 1;
                        })
                        .then(ClientCommandManager.argument("level", IntegerArgumentType.integer(1, 20)).executes(ctx -> {
                            int l = Math.min(Config.get().tiers.size(), IntegerArgumentType.getInteger(ctx, "level")) - 1;
                            PunishManager.hit(Config.get().tiers.get(l).minAmount, me(), l, true);
                            return 1;
                        })))
                .then(ClientCommandManager.literal("punish")
                        .then(ClientCommandManager.argument("id", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Punishes.ids(), b))
                                .executes(ctx -> punish(ctx, StringArgumentType.getString(ctx, "id"), 1))
                                .then(ClientCommandManager.argument("level", IntegerArgumentType.integer(1, 20))
                                        .executes(ctx -> punish(ctx, StringArgumentType.getString(ctx, "id"), IntegerArgumentType.getInteger(ctx, "level"))))))
                .then(ClientCommandManager.literal("simulate")
                        .then(ClientCommandManager.argument("message", StringArgumentType.greedyString()).executes(ctx -> {
                            String text = Detector.clean(StringArgumentType.getString(ctx, "message"));
                            StringBuilder why = new StringBuilder();
                            Detector.Result r = Detector.detect(text, why);
                            if (r == null) return say(ctx, "§eNot a donation: " + why);
                            int level = Config.get().tierFor(r.amount());
                            if (level < 0) return say(ctx, "Found " + Money.full(r.amount()) + " from " + r.player() + ", but below level 1.");
                            PunishManager.hit(r.amount(), r.player(), level, true);
                            return say(ctx, "§aDetected " + Money.full(r.amount()) + " from " + r.player() + " = level " + (level + 1));
                        })));
    }

    private static String me() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? "You" : mc.player.getGameProfile().name();
    }

    private static int punish(CommandContext<FabricClientCommandSource> ctx, String id, int level) {
        if (Punishes.get(id) == null) return say(ctx, "§eNo punishment called " + id + ". Try /pfx list");
        PunishManager.force(id, level - 1);
        return say(ctx, "Running " + Punishes.name(id) + " at level " + level);
    }
}
