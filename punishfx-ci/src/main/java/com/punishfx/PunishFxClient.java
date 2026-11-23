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
import net.minecraft.client.gui.screens.Screen;
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
    /** A screen to open on the next tick (commands run while chat is still closing). */
    private static Screen pendingScreen = null;

    @Override
    public void onInitializeClient() {
        Config.load();
        Ui.applyTheme(Config.get().theme);
        DonorBoard.load();
        StreamSession.load();
        Presets.load();
        Giveaway.load();

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
            if (pendingScreen != null) {
                Screen sc = pendingScreen;
                pendingScreen = null;
                client.setScreen(sc);
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
                .then(ClientCommandManager.literal("stream")
                        .executes(ctx -> streamStatus(ctx))
                        .then(ClientCommandManager.literal("start").executes(ctx -> {
                            if (StreamSession.live()) return say(ctx, "You're already live (" + StreamHud.clock(StreamSession.current().duration()) + "). /pfx stream stop to end it.");
                            StreamSession.start();
                            return say(ctx, "\u00a7aYou're live!\u00a7r Every donation is being counted. Type /pfx stream stop when you're done.");
                        }))
                        .then(ClientCommandManager.literal("stop").executes(ctx -> {
                            StreamSession.Record r = StreamSession.stop();
                            if (r == null) return say(ctx, "You're not live. /pfx stream start to begin.");
                            if (Config.get().streamAutoRecap) pendingScreen = new RecapScreen(null);
                            return say(ctx, "Stream over: \u00a7a" + Money.full(r.total()) + "\u00a7r from " + r.donations.size()
                                    + " donations in " + RecapScreen.length(r.duration()) + ". /pfx recap to see it again.");
                        }))
                        .then(ClientCommandManager.literal("status").executes(ctx -> streamStatus(ctx))))
                .then(ClientCommandManager.literal("wheel").executes(ctx -> {
                    PunishManager.force("spin_wheel", 0);
                    return say(ctx, "Spinning the wheel (test).");
                }))
                .then(ClientCommandManager.literal("case").executes(ctx -> {
                    PunishManager.force("case_open", 0);
                    return say(ctx, "Opening a case (test).");
                }))
                .then(ClientCommandManager.literal("giveaway")
                        .executes(ctx -> giveawayStatus(ctx))
                        .then(ClientCommandManager.literal("start").executes(ctx -> {
                            if (Giveaway.live()) return say(ctx, "A giveaway is already open. /pfx giveaway draw to pick the winner.");
                            Giveaway.start();
                            return say(ctx, "\u00a7aGiveaway open!\u00a7r Entry: " + Money.full(Config.get().gwPrice) + ". /pfx giveaway draw when you're ready.");
                        }))
                        .then(ClientCommandManager.literal("draw").executes(ctx -> {
                            if (!Giveaway.live()) return say(ctx, "No giveaway is open. /pfx giveaway start first.");
                            if (!Giveaway.draw()) return say(ctx, "Nobody has entered yet.");
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("cancel").executes(ctx -> {
                            Giveaway.cancel();
                            return say(ctx, "Giveaway cancelled.");
                        }))
                        .then(ClientCommandManager.literal("status").executes(ctx -> giveawayStatus(ctx))))
                .then(ClientCommandManager.literal("recap").executes(ctx -> {
                    if (StreamSession.history().isEmpty()) return say(ctx, "No streams yet. /pfx stream start when you go live.");
                    pendingScreen = new RecapScreen(null);
                    return 1;
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

    private static int giveawayStatus(CommandContext<FabricClientCommandSource> ctx) {
        if (!Giveaway.live()) return say(ctx, Giveaway.lastWinner() == null ? "No giveaway open. /pfx giveaway start to open one."
                : "No giveaway open. Last winner: " + Giveaway.lastWinner());
        return say(ctx, "Giveaway: " + Giveaway.count() + " entered (" + Giveaway.tickets() + " tickets), entry "
                + Money.full(Giveaway.state().price) + ", prize " + Giveaway.prizeText());
    }

    private static int streamStatus(CommandContext<FabricClientCommandSource> ctx) {
        StreamSession.Record r = StreamSession.current();
        if (r == null) return say(ctx, "Not live. /pfx stream start when you go live.");
        return say(ctx, "\u00a7cLIVE\u00a7r " + StreamHud.clock(r.duration()) + "  \u00b7  " + Money.full(r.total()) + " from "
                + r.donations.size() + " donations (" + r.donors().size() + " donors)");
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
