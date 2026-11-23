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
                new Header("Punishments"),
                new Toggle("Stack punishments", "New ones add on instead of replacing", () -> c.stack, v -> c.stack = v),
                numbers("Punishment length", "Multiplies every level's duration",
                        new double[]{0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 3.0}, v -> Math.round(v * 100) + "%", () -> c.lengthScale, v -> c.lengthScale = v),
                numbers("Max at once", "Most punishments running together",
                        new double[]{1, 2, 3, 4, 5, 6, 8, 10}, v -> String.valueOf((int) v), () -> c.maxActive, v -> c.maxActive = (int) v),
                new Toggle("Confirm leave / close", "A cancel countdown before Leave Server or Close Minecraft", () -> c.confirmExtreme, v -> c.confirmExtreme = v),
                new Header("Files"),
                new Action("Settings folder", "Open the folder with Punish FX's files", () -> "Open", Ui.DIM,
                        () -> Util.getPlatform().openPath(FabricLoader.getInstance().getConfigDir())),
                new Action("Reset everything", "Put every setting back to how it started", () -> confirmReset ? "Sure? Click" : "Reset", Ui.ACCENT, () -> {
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
