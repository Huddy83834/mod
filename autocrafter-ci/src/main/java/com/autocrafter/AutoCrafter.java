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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        public int maxCraftsPerTick = 256;
        public double maxFlowSetsPerSecond = 20.0;
        public int maxRecipesPerPlayer = 16;
    }

    /** What a recipe makes: either the item the player chose, or what the game's own recipe gives. */
    private record Result(Item item, int count, boolean custom) {}

    private static final class PlayerState {
        List<RecipeDef> recipes = new ArrayList<>();
        List<Result> results = new ArrayList<>();
        boolean tableOnly;
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
            st.tableOnly = false;
            st.recipes = parse(payload.json(), st);
            st.acc = new double[st.recipes.size()];
            resolveAll(player, st);
            sendStatus(context.server(), player, st);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            PlayerState st = STATES.computeIfAbsent(player.getUUID(), k -> new PlayerState());
            sendStatus(server, player, st);
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
    private static List<RecipeDef> parse(String json, PlayerState st) {
        List<RecipeDef> out = new ArrayList<>();
        try {
            RecipeDef.Holder h = GSON.fromJson(json, RecipeDef.Holder.class);
            if (h == null || h.recipes == null) return out;
            st.tableOnly = h.tableOnly;
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
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < st.recipes.size(); i++) {
            if (i > 0) sb.append('|');
            Result r = i < st.results.size() ? st.results.get(i) : null;
            if (r != null) {
                sb.append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(r.item())).append('*').append(r.count());
            }
        }
        ServerPlayNetworking.send(player, new StatusPayload(active, st.crafted, st.flowed, sb.toString()));
    }

    private static void resolveAll(ServerPlayer player, PlayerState st) {
        st.results = new ArrayList<>();
        ServerLevel level = (ServerLevel) player.level();
        for (RecipeDef def : st.recipes) {
            Result r = null;
            try {
                r = resolve(level, def);
            } catch (Exception ignored) {
                // no result for this one
            }
            st.results.add(r);
        }
    }

    /** The chosen result item, or (if none was chosen) what the game's own crafting recipe gives for this layout. */
    private static Result resolve(ServerLevel level, RecipeDef def) {
        Item custom = RecipeDef.item(def.resultId);
        if (custom != null) return new Result(custom, def.resultCount, true);

        List<ItemStack> grid = new ArrayList<>();
        boolean any = false;
        for (RecipeDef.Ing ing : def.ingredients) {
            Item it = ing.empty() ? null : RecipeDef.item(ing.id);
            if (it == null) {
                grid.add(ItemStack.EMPTY);
            } else {
                grid.add(new ItemStack(it));
                any = true;
            }
        }
        if (!any) return null;
        CraftingInput input = CraftingInput.of(3, 3, grid);
        Optional<RecipeHolder<CraftingRecipe>> found = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level);
        if (found.isEmpty()) return null;
        ItemStack out = found.get().value().assemble(input, level.registryAccess());
        if (out.isEmpty()) return null;
        return new Result(out.getItem(), out.getCount(), false);
    }

    // ------------------------------------------------------------------ the work

    private static void tickPlayer(ServerPlayer player, PlayerState st) {
        Inventory inv = player.getInventory();
        boolean atTable = player.containerMenu instanceof CraftingMenu;
        for (int r = 0; r < st.recipes.size(); r++) {
            RecipeDef def = st.recipes.get(r);
            if (!def.enabled) continue;
            Result res = r < st.results.size() ? st.results.get(r) : null;
            if (res == null) continue;
            Item result = res.item();
            int resultCount = res.count();
            Map<Item, Integer> needs = res.custom() ? def.needs() : def.unitNeeds();
            if (needs.isEmpty()) continue;

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
            if (st.tableOnly && !atTable) continue;
            Map<Item, Integer> have = new HashMap<>();
            for (int i = 0; i < 36; i++) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty() && needs.containsKey(s.getItem())) have.merge(s.getItem(), s.getCount(), Integer::sum);
            }
            // With a crafting table window open there is no limit: everything is crafted at once.
            int crafts = atTable ? 100000 : config.maxCraftsPerTick;
            for (Map.Entry<Item, Integer> e : needs.entrySet()) {
                crafts = Math.min(crafts, have.getOrDefault(e.getKey(), 0) / e.getValue());
            }
            if (crafts <= 0) continue;

            for (Map.Entry<Item, Integer> e : needs.entrySet()) {
                take(inv, e.getKey(), e.getValue() * crafts);
            }
            inv.setChanged();
            long total = (long) crafts * resultCount;
            st.crafted += total;
            deliver(player, inv, result, (int) Math.min(total, 2_000_000L), def.drop);
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
