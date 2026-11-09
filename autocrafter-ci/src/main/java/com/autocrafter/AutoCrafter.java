package com.autocrafter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Auto Crafter (server side, also runs inside a single-player world).
 *
 * Every tick, for every recipe a player has set up: optionally adds the ingredients to their
 * inventory ("flow"), then crafts as many sets as the inventory allows and drops (or gives) the result.
 *
 * Because this makes items from nothing, it only runs in single-player worlds unless a server owner
 * switches it on in config/autocrafter-server.json.
 */
public class AutoCrafter implements ModInitializer {
    public static final String MOD_ID = "autocrafter";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final class ServerConfig {
        /** Dedicated servers: set to true to let players use the mod. */
        public boolean allowOnDedicatedServers = false;
        public int maxCraftsPerTick = 16;
        public double maxFlowSetsPerSecond = 20.0;
        public int maxRecipesPerPlayer = 16;
    }

    private static final class PlayerState {
        List<RecipeDef> recipes = new ArrayList<>();
        double[] acc = new double[0];
        long crafted;
        long flowed;
    }

    private static ServerConfig config = new ServerConfig();
    private static final Map<UUID, PlayerState> STATES = new HashMap<>();
    private static int statusTimer;

    @Override
    public void onInitialize() {
        loadConfig();

        PayloadTypeRegistry.playC2S().register(RecipesPayload.TYPE, RecipesPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StatusPayload.TYPE, StatusPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(RecipesPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            PlayerState st = STATES.computeIfAbsent(player.getUUID(), k -> new PlayerState());
            st.recipes = parse(payload.json());
            st.acc = new double[st.recipes.size()];
            sendStatus(context.server(), player, st);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                STATES.remove(handler.getPlayer().getUUID()));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            boolean allowed = server.isSingleplayer() || config.allowOnDedicatedServers;
            boolean sendStatusNow = ++statusTimer >= 40;
            if (sendStatusNow) statusTimer = 0;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                PlayerState st = STATES.get(player.getUUID());
                if (st == null) continue;
                if (allowed && player.isAlive()) tickPlayer(player, st);
                if (sendStatusNow) sendStatus(server, player, st);
            }
        });
    }

    // ------------------------------------------------------------------ config + parsing

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("autocrafter-server.json");
    }

    private static void loadConfig() {
        try {
            Path p = configPath();
            if (Files.exists(p)) {
                ServerConfig c = GSON.fromJson(Files.readString(p), ServerConfig.class);
                if (c != null) config = c;
            } else {
                Files.writeString(p, GSON.toJson(config));
            }
        } catch (Exception ignored) {
            // keep defaults
        }
    }

    /** Reads and cleans up a recipe list sent by a player (never trusts the numbers). */
    private static List<RecipeDef> parse(String json) {
        List<RecipeDef> out = new ArrayList<>();
        try {
            RecipeDef.Holder h = GSON.fromJson(json, RecipeDef.Holder.class);
            if (h == null || h.recipes == null) return out;
            for (RecipeDef r : h.recipes) {
                if (r == null || out.size() >= config.maxRecipesPerPlayer) continue;
                r.fill();
                for (RecipeDef.Ing ing : r.ingredients) {
                    ing.count = Math.max(0, Math.min(4096, ing.count));
                    if (ing.id == null) ing.id = "";
                }
                r.resultCount = Math.max(1, Math.min(4096, r.resultCount));
                r.flowRate = Math.max(0.05, Math.min(config.maxFlowSetsPerSecond, r.flowRate));
                out.add(r);
            }
        } catch (Exception ignored) {
            // bad data: no recipes
        }
        return out;
    }

    private static void sendStatus(MinecraftServer server, ServerPlayer player, PlayerState st) {
        boolean active = server.isSingleplayer() || config.allowOnDedicatedServers;
        ServerPlayNetworking.send(player, new StatusPayload(active, st.crafted, st.flowed));
    }

    // ------------------------------------------------------------------ the work

    private static void tickPlayer(ServerPlayer player, PlayerState st) {
        Inventory inv = player.getInventory();
        for (int r = 0; r < st.recipes.size(); r++) {
            RecipeDef def = st.recipes.get(r);
            if (!def.enabled) continue;
            Item result = RecipeDef.item(def.resultId);
            Map<Item, Integer> needs = def.needs();
            if (result == null || needs.isEmpty()) continue;

            // 1. Flow: keep the ingredients coming.
            if (def.flow && r < st.acc.length) {
                st.acc[r] += def.flowRate / 20.0;
                int sets = (int) st.acc[r];
                if (sets > 0) {
                    st.acc[r] -= sets;
                    sets = Math.min(sets, 20);
                    for (Map.Entry<Item, Integer> e : needs.entrySet()) {
                        st.flowed += give(inv, e.getKey(), e.getValue() * sets);
                    }
                }
            }

            // 2. Auto-craft as many sets as the inventory holds.
            if (!def.autoCraft) continue;
            Map<Item, Integer> have = new HashMap<>();
            for (int i = 0; i < 36; i++) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty() && needs.containsKey(s.getItem())) have.merge(s.getItem(), s.getCount(), Integer::sum);
            }
            int crafts = config.maxCraftsPerTick;
            for (Map.Entry<Item, Integer> e : needs.entrySet()) {
                crafts = Math.min(crafts, have.getOrDefault(e.getKey(), 0) / e.getValue());
            }
            if (crafts <= 0) continue;

            for (Map.Entry<Item, Integer> e : needs.entrySet()) {
                take(inv, e.getKey(), e.getValue() * crafts);
            }
            inv.setChanged();
            st.crafted += (long) crafts * def.resultCount;
            deliver(player, inv, result, crafts * def.resultCount, def.drop);
        }
    }

    /** Adds items to the main inventory. Stops quietly when it is full. Returns how many went in. */
    private static int give(Inventory inv, Item item, int amount) {
        int given = 0;
        int max = new ItemStack(item).getMaxStackSize();
        while (amount > 0) {
            int n = Math.min(amount, max);
            ItemStack s = new ItemStack(item, n);
            inv.add(s);
            int put = n - s.getCount();
            given += put;
            amount -= n;
            if (put < n) break; // full
        }
        return given;
    }

    private static void take(Inventory inv, Item item, int amount) {
        for (int i = 0; i < 36 && amount > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || s.getItem() != item) continue;
            int n = Math.min(amount, s.getCount());
            s.shrink(n);
            amount -= n;
        }
    }

    private static void deliver(ServerPlayer player, Inventory inv, Item item, int total, boolean drop) {
        int max = new ItemStack(item).getMaxStackSize();
        while (total > 0) {
            int n = Math.min(total, max);
            total -= n;
            ItemStack s = new ItemStack(item, n);
            if (!drop) inv.add(s);
            if (!s.isEmpty()) player.drop(s, false);
        }
    }
}
