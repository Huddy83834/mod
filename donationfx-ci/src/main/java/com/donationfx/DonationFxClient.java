package com.donationfx;

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

/** Donation FX: big animations when someone pays you. Client-side only. */
public class DonationFxClient implements ClientModInitializer {
    public static final String MOD_ID = "donationfx";
    public static final Logger LOG = LoggerFactory.getLogger("Donation FX");

    private static KeyMapping settingsKey;
    private static KeyMapping toggleKey;
    private static KeyMapping testKey;
    private static boolean openSettings = false;

    @Override
    public void onInitializeClient() {
        Config.load();

        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        settingsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.donationfx.settings", InputConstants.Type.KEYSYM, InputConstants.KEY_J, cat));
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.donationfx.toggle", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), cat));
        testKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.donationfx.test", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), cat));

        // money messages come from the server as system messages (chat or action bar)
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            try {
                Detector.onMessage(message.getString(), overlay);
            } catch (Exception e) {
                LOG.warn("Donation FX: could not read a message", e);
            }
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "fx"), (g, delta) -> {
            try {
                Fx.render(g, delta);
            } catch (Exception e) {
                LOG.warn("Donation FX: drawing failed", e);
                Fx.stop();
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (settingsKey.consumeClick()) {
                if (client.screen == null) client.setScreen(new SettingsScreen(null));
            }
            while (toggleKey.consumeClick()) {
                Config c = Config.get();
                c.enabled = !c.enabled;
                Config.save();
                if (!c.enabled) Fx.stop();
                if (client.player != null) client.player.displayClientMessage(Component.literal("Donation FX: " + (c.enabled ? "ON" : "OFF")), true);
            }
            while (testKey.consumeClick()) testLevel(client, (int) (Math.random() * 5));
            if (openSettings) {
                openSettings = false;
                client.setScreen(new SettingsScreen(null));
            }
            try {
                Fx.tick(client);
            } catch (Exception e) {
                LOG.warn("Donation FX: effect failed", e);
                Fx.stop();
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            dispatcher.register(command("donationfx"));
            dispatcher.register(command("dfx"));
        });
    }

    private static void testLevel(Minecraft mc, int level) {
        Config c = Config.get();
        if (level < 0 || level >= c.tiers.size()) return;
        String me = mc.player == null ? "You" : mc.player.getGameProfile().name();
        Fx.donate(c.tiers.get(level).minAmount, me, level, true);
    }

    private static int say(CommandContext<FabricClientCommandSource> ctx, String s) {
        ctx.getSource().sendFeedback(Component.literal("§6[Donation FX]§r " + s));
        return 1;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> command(String name) {
        return ClientCommandManager.literal(name)
                .executes(ctx -> {
                    openSettings = true;
                    return 1;
                })
                .then(ClientCommandManager.literal("settings").executes(ctx -> {
                    openSettings = true;
                    return 1;
                }))
                .then(ClientCommandManager.literal("on").executes(ctx -> {
                    Config.get().enabled = true;
                    Config.save();
                    return say(ctx, "ON");
                }))
                .then(ClientCommandManager.literal("off").executes(ctx -> {
                    Config.get().enabled = false;
                    Config.save();
                    Fx.stop();
                    return say(ctx, "OFF");
                }))
                .then(ClientCommandManager.literal("toggle").executes(ctx -> {
                    Config c = Config.get();
                    c.enabled = !c.enabled;
                    Config.save();
                    if (!c.enabled) Fx.stop();
                    return say(ctx, c.enabled ? "ON" : "OFF");
                }))
                .then(ClientCommandManager.literal("stop").executes(ctx -> {
                    Fx.stop();
                    return say(ctx, "Stopped all animations.");
                }))
                .then(ClientCommandManager.literal("reload").executes(ctx -> {
                    Config.load();
                    Detector.clearCache();
                    return say(ctx, "Settings reloaded from config/donationfx.json.");
                }))
                .then(ClientCommandManager.literal("test")
                        .executes(ctx -> {
                            testLevel(Minecraft.getInstance(), (int) (Math.random() * 5));
                            return 1;
                        })
                        .then(ClientCommandManager.argument("level", IntegerArgumentType.integer(1, 5)).executes(ctx -> {
                            testLevel(Minecraft.getInstance(), IntegerArgumentType.getInteger(ctx, "level") - 1);
                            return 1;
                        })))
                .then(ClientCommandManager.literal("themes").executes(ctx -> {
                    StringBuilder sb = new StringBuilder("Animations: ");
                    for (String id : Themes.ids()) sb.append(id).append(Themes.enabled(id) ? "" : " (off)").append(", ");
                    say(ctx, sb.substring(0, sb.length() - 2));
                    return say(ctx, "Play one with /dfx theme <name> [level 1-5]. Change them with /dfx then Animations...");
                }))
                .then(ClientCommandManager.literal("theme")
                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Themes.ids(), b))
                                .executes(ctx -> theme(ctx, StringArgumentType.getString(ctx, "name"), 3))
                                .then(ClientCommandManager.argument("level", IntegerArgumentType.integer(1, 5))
                                        .executes(ctx -> theme(ctx, StringArgumentType.getString(ctx, "name"), IntegerArgumentType.getInteger(ctx, "level"))))))
                .then(ClientCommandManager.literal("amount")
                        .then(ClientCommandManager.argument("money", StringArgumentType.word())
                                .executes(ctx -> amount(ctx, StringArgumentType.getString(ctx, "money"), null))
                                .then(ClientCommandManager.argument("player", StringArgumentType.word())
                                        .executes(ctx -> amount(ctx, StringArgumentType.getString(ctx, "money"), StringArgumentType.getString(ctx, "player"))))))
                .then(ClientCommandManager.literal("simulate")
                        .then(ClientCommandManager.argument("message", StringArgumentType.greedyString()).executes(ctx -> {
                            String text = Detector.clean(StringArgumentType.getString(ctx, "message"));
                            StringBuilder why = new StringBuilder();
                            Detector.Result r = Detector.detect(text, why);
                            if (r == null) return say(ctx, "§cNot a donation: " + why + ". Add a customPatterns entry in donationfx.json if it should be.");
                            int level = Config.get().tierFor(r.amount());
                            if (level < 0) return say(ctx, "Found " + Money.full(r.amount()) + " from " + r.player() + ", but that is below level 1.");
                            Fx.donate(r.amount(), r.player(), level, true);
                            return say(ctx, "§aDonation! " + Money.full(r.amount()) + " from " + r.player() + " = level " + (level + 1) + " (" + r.why() + ")");
                        })));
    }

    private static int theme(CommandContext<FabricClientCommandSource> ctx, String name, int level) {
        if (!name.equalsIgnoreCase(Themes.CLASSIC) && Themes.get(name) == null) return say(ctx, "\u00a7cNo animation called " + name + ". Try /dfx themes");
        Minecraft mc = Minecraft.getInstance();
        Config.Tier t = Config.get().tiers.get(level - 1);
        String who = mc.player == null ? "You" : mc.player.getGameProfile().name();
        Fx.donate(t.minAmount, who, level - 1, true, name.toLowerCase());
        return say(ctx, "Playing " + Themes.name(name) + " as level " + level + ".");
    }

    private static int amount(CommandContext<FabricClientCommandSource> ctx, String money, String player) {
        double v = Money.parse(money);
        if (v <= 0) return say(ctx, "§cThat is not an amount. Try 50k, 1.5m or 10000000.");
        int level = Config.get().tierFor(v);
        if (level < 0) return say(ctx, Money.full(v) + " is below level 1 (or that level is off).");
        Minecraft mc = Minecraft.getInstance();
        String who = player != null ? player : mc.player == null ? "You" : mc.player.getGameProfile().name();
        Fx.donate(v, who, level, true);
        return say(ctx, "Playing level " + (level + 1) + " for " + Money.full(v) + ".");
    }
}
