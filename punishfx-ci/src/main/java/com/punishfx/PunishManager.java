package com.punishfx;

/** Chooses and runs punishments when a donation is detected. Filled in after the API probe. */
public final class PunishManager {
    private PunishManager() {}

    public static void hit(double amount, String player, int level, boolean test) {
        PunishFxClient.LOG.info("Punish FX: level {} from {} ({}){}", level + 1, player, amount, test ? " [test]" : "");
    }
}
