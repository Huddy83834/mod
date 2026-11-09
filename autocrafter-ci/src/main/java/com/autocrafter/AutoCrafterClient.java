package com.autocrafter;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Client-only. Nothing needs to be installed on the server. */
public class AutoCrafterClient implements ClientModInitializer {
    public static final String MOD_ID = "autocrafter";
    private static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        ClientRecipes.load();
        Runtime.getRuntime().addShutdownHook(new Thread(ClientRecipes::save));

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.autocrafter.open", InputConstants.Type.KEYSYM, InputConstants.KEY_G, category));

        TableOverlay.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) {
                if (client.player != null && client.screen == null) {
                    client.setScreen(new AutoCrafterScreen());
                }
            }
            Crafter.tick(client);
            CreativeEngine.tick(client);
        });
    }
}
