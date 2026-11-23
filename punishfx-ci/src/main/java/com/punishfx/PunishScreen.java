package com.punishfx;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Util;

import java.util.List;

/** General page: the basics of how donations are handled. */
public class PunishScreen extends PfxFrame {
    private static boolean confirmReset = false;

    public PunishScreen(Screen parent) {
        super(parent, Page.GENERAL, "General", "the basics");
    }

    @Override
    protected void initPage() {
        Config c = Config.get();
        list(List.of(
                new Header("Donations"),
                new Toggle("Show who donated", "Banner with their name and the amount", () -> c.announce, v -> c.announce = v),
                new Toggle("Sounds", "Play a sound when a punishment starts", () -> c.sounds, v -> c.sounds = v),
                new Toggle("Combine quick payments", "Payments close together count as one", () -> c.combine, v -> c.combine = v),
                new Header("Several donations at once"),
                new Choice("What happens", "", PunishManager.OVERLAP_NAMES, () -> c.overlapMode, v -> c.overlapMode = v, PunishManager.OVERLAP_DESCS),
                new Choice("Counts as \"at once\"", "", PunishManager.WINDOW_NAMES, () -> c.overlapWindow, v -> c.overlapWindow = v,
                        new String[]{"A donation while a punishment is still going", "Donations less than 5 seconds apart",
                                "Donations less than 10 seconds apart", "Donations less than 30 seconds apart", "Donations less than a minute apart"}),
                new Choice("Queue: gap between", "Queue only: a breather between punishments", new String[]{"None", "1s", "3s", "5s"},
                        () -> c.queueGap, v -> c.queueGap = v),
                new Toggle("Queue: show who's next", "Queue only: \"UP NEXT\" in the top corner", () -> c.queueShow, v -> c.queueShow = v),
                new Choice("Combo: each step", "Combo only: how much longer each extra one makes it", new String[]{"+25%", "+50%", "+100%"},
                        () -> c.comboStep, v -> c.comboStep = v),
                new Header("Punishments"),
                numbers("Punishment length", "Multiplies every level's duration",
                        new double[]{0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 3.0}, v -> Math.round(v * 100) + "%", () -> c.lengthScale, v -> c.lengthScale = v),
                numbers("Max at once", "Most punishments running together",
                        new double[]{1, 2, 3, 4, 5, 6, 8, 10}, v -> String.valueOf((int) v), () -> c.maxActive, v -> c.maxActive = (int) v),
                new Toggle("Confirm leave / close", "Countdown before leave / close", () -> c.confirmExtreme, v -> c.confirmExtreme = v),
                new Header("Files"),
                new Action("Settings folder", "Open the folder with Punish FX's files", () -> "Open", Ui.DIM,
                        () -> Util.getPlatform().openPath(FabricLoader.getInstance().getConfigDir())),
                new Action("Reset everything", "Put every setting back to how it started", () -> confirmReset ? "Sure? Click" : "Reset", Ui.THEME, () -> {
                    if (!confirmReset) {
                        confirmReset = true;
                        return;
                    }
                    confirmReset = false;
                    Config.reset();
                    rebuild();
                })
        ), cy, cy + ch);
    }
}
