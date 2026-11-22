package com.punishfx;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PunishFxClient implements ClientModInitializer {
    public static final String MOD_ID = "punishfx";
    public static final Logger LOG = LoggerFactory.getLogger("Punish FX");

    @Override
    public void onInitializeClient() {
        Config.load();
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            try {
                Detector.onMessage(message.getString(), overlay);
            } catch (Exception e) {
                LOG.warn("Punish FX: could not read a message", e);
            }
        });
    }
}
