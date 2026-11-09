package com.autocrafter;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public class AutoCrafterClient implements ClientModInitializer {
    private static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        ClientRecipes.load();

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(AutoCrafter.MOD_ID, "main"));
        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.autocrafter.open", InputConstants.Type.KEYSYM, InputConstants.KEY_G, category));

        ClientPlayNetworking.registerGlobalReceiver(StatusPayload.TYPE,
                (payload, context) -> ClientRecipes.status = payload);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientRecipes.status = null;
            ClientRecipes.sync();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                ClientRecipes.waitTicks = 0;
            } else if (ClientRecipes.status == null) {
                ClientRecipes.waitTicks++;
                // Keep asking until the server answers (the first message can be too early).
                if (ClientRecipes.waitTicks % 30 == 5) ClientRecipes.sync();
            }
            while (openKey.consumeClick()) {
                if (client.player != null && client.screen == null) {
                    client.setScreen(new AutoCrafterScreen());
                }
            }
        });
    }
}
