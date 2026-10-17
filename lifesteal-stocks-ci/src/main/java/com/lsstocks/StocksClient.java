package com.lsstocks;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-only. Reads Auction House / Orders pages you open, keeps price history,
 * and shows it in a market screen (keybind K, or the "LS Market" button on the
 * pause and title screens).
 */
public class StocksClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("lsstocks");
    public static final MarketStore STORE = new MarketStore();

    private static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        STORE.load();
        Runtime.getRuntime().addShutdownHook(new Thread(STORE::saveNow));

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("lsstocks", "main"));
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.lsstocks.open",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_K,
                category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) {
                openMarket(client);
            }
            Scanner.tick(client, STORE);
            STORE.maybeSave();
        });

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof PauseScreen || screen instanceof TitleScreen) {
                Button button = Button.builder(Component.literal("LS Market"), b -> openMarket(client))
                        .bounds(6, 6, 90, 20)
                        .build();
                Screens.getWidgets(screen).add(button);
            }
        });
    }

    public static void openMarket(Minecraft client) {
        client.gui.setScreen(new MarketScreen(client.gui.screen()));
    }
}
