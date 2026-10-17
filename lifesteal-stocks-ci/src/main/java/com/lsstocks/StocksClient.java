package com.lsstocks;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Client-side only. Passively reads prices the server already shows you in item
 * tooltips (shop / auction GUIs) and tracks them over time like a stock ticker.
 * It never sends packets, clicks, or automates anything.
 */
public class StocksClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("lsstocks");
    public static final PriceTracker TRACKER = new PriceTracker();

    private Pattern pattern = Pattern.compile(PriceTracker.DEFAULT_REGEX);

    @Override
    public void onInitializeClient() {
        TRACKER.load();
        compile(TRACKER.regex());

        ItemTooltipCallback.EVENT.register((stack, ctx, type, lines) -> {
            if (!TRACKER.scan() || stack.isEmpty()) return;
            for (Text line : lines) {
                Matcher m = pattern.matcher(line.getString());
                if (m.find()) {
                    double price = parse(m.group(1), m.groupCount() >= 2 && m.group(2) != null ? m.group(2) : "");
                    String id = Registries.ITEM.getId(stack.getItem()).getPath();
                    // Per-item price: divide by stack size if the listing is for a stack
                    TRACKER.record(id, price);
                    break;
                }
            }
        });

        HudRenderCallback.EVENT.register(this::renderHud);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> registerCommands(dispatcher));
    }

    private void compile(String regex) {
        try {
            pattern = Pattern.compile(regex);
        } catch (Exception e) {
            LOGGER.warn("Bad regex, falling back to default", e);
            pattern = Pattern.compile(PriceTracker.DEFAULT_REGEX);
        }
    }

    private static double parse(String num, String suffix) {
        double v = Double.parseDouble(num.replace(",", ""));
        return switch (suffix.toLowerCase()) {
            case "k" -> v * 1_000;
            case "m" -> v * 1_000_000;
            default -> v;
        };
    }

    private void renderHud(DrawContext ctx, net.minecraft.client.render.RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!TRACKER.hud() || mc.options.hudHidden || mc.player == null) return;
        int x = 6, y = 6;
        ctx.drawTextWithShadow(mc.textRenderer, "LS STOCKS", x, y, 0xFFFFAA00);
        y += 11;
        for (String item : TRACKER.hudItems()) {
            OptionalDouble price = TRACKER.latest(item);
            if (price.isEmpty()) continue;
            OptionalDouble ch = TRACKER.changePct(item);
            String arrow = " ";
            int color = 0xFFAAAAAA;
            String pct = "";
            if (ch.isPresent()) {
                double c = ch.getAsDouble();
                arrow = c > 0 ? "▲" : c < 0 ? "▼" : "■";
                color = c > 0 ? 0xFF55FF55 : c < 0 ? 0xFFFF5555 : 0xFFAAAAAA;
                pct = String.format(" %+.1f%%", c);
            }
            String line = item + " " + PriceTracker.fmt(price.getAsDouble()) + " " + arrow + pct
                    + " " + TRACKER.sparkline(item, 8);
            ctx.drawTextWithShadow(mc.textRenderer, line, x, y, color);
            y += 10;
        }
    }

    private void registerCommands(com.mojang.brigadier.CommandDispatcher<FabricClientCommandSource> d) {
        d.register(literal("stocks")
                .then(literal("add").then(argument("item", StringArgumentType.word()).executes(c -> {
                    String item = StringArgumentType.getString(c, "item");
                    msg(c.getSource(), TRACKER.watch(item) ? "Watching " + PriceTracker.normalize(item) : "Already watching it.");
                    return 1;
                })))
                .then(literal("remove").then(argument("item", StringArgumentType.word()).executes(c -> {
                    String item = StringArgumentType.getString(c, "item");
                    msg(c.getSource(), TRACKER.unwatch(item) ? "Removed " + PriceTracker.normalize(item) : "Not on your watchlist.");
                    return 1;
                })))
                .then(literal("set").then(argument("item", StringArgumentType.word())
                        .then(argument("price", DoubleArgumentType.doubleArg(0)).executes(c -> {
                            String item = StringArgumentType.getString(c, "item");
                            double price = DoubleArgumentType.getDouble(c, "price");
                            TRACKER.record(item, price);
                            msg(c.getSource(), "Recorded " + PriceTracker.normalize(item) + " at " + PriceTracker.fmt(price));
                            return 1;
                        }))))
                .then(literal("list").executes(c -> {
                    if (TRACKER.allItems().isEmpty()) {
                        msg(c.getSource(), "No prices yet. Open a shop GUI and hover items, or use /stocks set <item> <price>.");
                        return 1;
                    }
                    for (String item : TRACKER.allItems()) {
                        OptionalDouble p = TRACKER.latest(item);
                        if (p.isEmpty()) continue;
                        String ch = TRACKER.changePct(item).isPresent()
                                ? String.format(" (%+.1f%%)", TRACKER.changePct(item).getAsDouble()) : "";
                        msg(c.getSource(), item + ": " + PriceTracker.fmt(p.getAsDouble()) + ch
                                + "  hi " + PriceTracker.fmt(TRACKER.high(item))
                                + " lo " + PriceTracker.fmt(TRACKER.low(item))
                                + "  " + TRACKER.sparkline(item, 16));
                    }
                    return 1;
                }))
                .then(literal("hud").executes(c -> {
                    TRACKER.setHud(!TRACKER.hud());
                    msg(c.getSource(), "HUD " + (TRACKER.hud() ? "on" : "off"));
                    return 1;
                }))
                .then(literal("scan").executes(c -> {
                    TRACKER.setScan(!TRACKER.scan());
                    msg(c.getSource(), "Tooltip scanning " + (TRACKER.scan() ? "on" : "off"));
                    return 1;
                }))
                .then(literal("regex").then(argument("pattern", StringArgumentType.greedyString()).executes(c -> {
                    String r = StringArgumentType.getString(c, "pattern");
                    try {
                        Pattern.compile(r);
                    } catch (Exception e) {
                        msg(c.getSource(), "Invalid regex.");
                        return 0;
                    }
                    TRACKER.setRegex(r);
                    compile(r);
                    msg(c.getSource(), "Price pattern updated. Group 1 = number, group 2 = optional k/m suffix.");
                    return 1;
                })))
                .then(literal("clear").executes(c -> {
                    TRACKER.clear();
                    msg(c.getSource(), "History cleared.");
                    return 1;
                }))
        );
    }

    private static void msg(FabricClientCommandSource src, String s) {
        src.sendFeedback(Text.literal("[Stocks] ").formatted(Formatting.GOLD).append(Text.literal(s).formatted(Formatting.WHITE)));
    }
}
