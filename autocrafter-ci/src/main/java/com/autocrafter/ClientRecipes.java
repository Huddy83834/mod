package com.autocrafter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The player's recipes, kept in config/autocrafter-recipes.json and sent to the server whenever they change. */
public final class ClientRecipes {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final RecipeDef.Holder DATA = new RecipeDef.Holder();
    public static StatusPayload status = null;
    public static int waitTicks = 0;

    private ClientRecipes() {}

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("autocrafter-recipes.json");
    }

    /** "id*count" the server found for recipe i, or "" if none. */
    public static String resolved(int i) {
        if (status == null || status.resolved() == null) return "";
        String[] parts = status.resolved().split("\\|", -1);
        return i >= 0 && i < parts.length ? parts[i] : "";
    }

    public static List<RecipeDef> list() {
        return DATA.recipes;
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                RecipeDef.Holder h = GSON.fromJson(Files.readString(p), RecipeDef.Holder.class);
                if (h != null && h.recipes != null) {
                    DATA.tableOnly = h.tableOnly;
                    DATA.recipes = new ArrayList<>(h.recipes);
                    for (RecipeDef r : DATA.recipes) r.fill();
                }
            }
        } catch (Exception ignored) {
            // start empty
        }
    }

    public static void save() {
        try {
            Files.writeString(path(), GSON.toJson(DATA));
        } catch (Exception ignored) {
            // not important
        }
    }

    /** Sends the recipes to the server (the integrated one in single-player). */
    public static void sync() {
        try {
            if (ClientPlayNetworking.canSend(RecipesPayload.TYPE)) {
                ClientPlayNetworking.send(new RecipesPayload(GSON.toJson(DATA)));
            }
        } catch (Exception ignored) {
            // not in a world
        }
    }

    public static void changed() {
        save();
        sync();
    }
}
