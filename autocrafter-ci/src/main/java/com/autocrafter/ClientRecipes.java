package com.autocrafter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The player's layouts, kept in config/autocrafter-recipes.json. */
public final class ClientRecipes {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static RecipeDef.Holder DATA = new RecipeDef.Holder();

    private ClientRecipes() {}

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("autocrafter-recipes.json");
    }

    public static List<RecipeDef> list() {
        return DATA.recipes;
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                RecipeDef.Holder h = GSON.fromJson(Files.readString(p), RecipeDef.Holder.class);
                if (h != null) {
                    if (h.recipes == null) h.recipes = new ArrayList<>();
                    for (RecipeDef r : h.recipes) r.fill();
                    if (h.speed < 1 || h.speed > 10) h.speed = 5;
                    DATA = h;
                }
            }
        } catch (Exception ignored) {
            // start empty (an old-format file is simply ignored)
        }
    }

    public static void changed() {
        save();
    }

    public static void save() {
        try {
            Files.writeString(path(), GSON.toJson(DATA));
        } catch (Exception ignored) {
            // not important
        }
    }
}
