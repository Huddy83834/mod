package com.punishfx;

import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/** Customize page: look, banner, how hard each punishment hits, and safety. */
public class CustomizeScreen extends PfxFrame {
    public CustomizeScreen(Screen parent) {
        super(parent, Page.CUSTOMIZE, "Customize", "make it look and feel how you want");
    }

    private static String pct(double v) {
        return Math.round(v * 100) + "%";
    }

    @Override
    protected void initPage() {
        Config c = Config.get();
        list(List.of(
                new Header("Look"),
                new Choice("Theme colour", "Colour of these menus and the on-screen banners",
                        Ui.THEME_NAMES, () -> c.theme, v -> c.theme = v),
                numbers("Screen effect strength", "How strong flash, fog, disco and other colour effects look",
                        new double[]{0.5, 0.75, 1.0, 1.25, 1.5}, CustomizeScreen::pct, () -> c.overlayStrength, v -> c.overlayStrength = v),

                new Header("Donation banner"),
                new Choice("Banner position", "Where \"<name> paid you\" appears",
                        new String[]{"Top", "Middle", "Bottom"}, () -> c.bannerPos, v -> c.bannerPos = v),
                numbers("Banner size", "How big the banner is",
                        new double[]{0.75, 1.0, 1.25, 1.5, 2.0}, CustomizeScreen::pct, () -> c.bannerScale, v -> c.bannerScale = v),
                numbers("Banner time", "How long the banner stays up",
                        new double[]{2, 3.5, 5, 7, 10}, v -> (v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v)) + "s",
                        () -> c.bannerSeconds, v -> c.bannerSeconds = v),
                new Toggle("List the punishments", "Show what the donation triggered under the name",
                        () -> c.bannerShowPunishments, v -> c.bannerShowPunishments = v),

                new Header("Punishment strength"),
                new Choice("Sluggish", "How slow you walk", new String[]{"Half speed", "A third", "A fifth", "A tenth"},
                        () -> c.sluggishLevel, v -> c.sluggishLevel = v),
                new Choice("Drunk", "How much you sway, tilt and stumble", new String[]{"Tipsy", "Drunk", "Wasted"},
                        () -> c.drunkLevel, v -> c.drunkLevel = v),
                new Choice("Jitter", "How hard the view shakes", new String[]{"Light", "Normal", "Heavy"},
                        () -> c.jitterLevel, v -> c.jitterLevel = v),
                new Choice("Glitch", "How far the view snaps and how much the screen tears", new String[]{"Light", "Normal", "Heavy"},
                        () -> c.glitchLevel, v -> c.glitchLevel = v),
                new Choice("Spin cycle", "How fast your camera spins", new String[]{"Slow", "Normal", "Fast", "Blender"},
                        () -> c.spinLevel, v -> c.spinLevel = v),
                new Choice("Tunnel vision", "How far your view closes in", new String[]{"Slight", "Normal", "Narrow", "Pinhole"},
                        () -> c.tunnelLevel, v -> c.tunnelLevel = v),
                new Choice("Lag spike", "How long and how often the game freezes", new String[]{"Mild", "Normal", "Brutal"},
                        () -> c.lagLevel, v -> c.lagLevel = v),
                new Choice("Flashbang", "How long you stay blinded", new String[]{"Short", "Normal", "Long"},
                        () -> c.flashLevel, v -> c.flashLevel = v),
                new Choice("Disco eyes", "How fast the colours change", new String[]{"Slow", "Normal", "Fast"},
                        () -> c.discoLevel, v -> c.discoLevel = v),

                new Header("Safety"),
                numbers("Donation cooldown", "Donations this soon after the last punishment only show the banner",
                        new double[]{0, 3, 5, 10, 30, 60}, v -> v == 0 ? "Off" : (int) v + "s", () -> c.cooldownSeconds, v -> c.cooldownSeconds = (int) v),
                numbers("Leave / close countdown", "Time you get to cancel Leave Server or Close Minecraft",
                        new double[]{3, 5, 10, 15}, v -> (int) v + "s", () -> c.countdownSeconds, v -> c.countdownSeconds = (int) v),
                numbers("Sound volume", "How loud Punish FX's sounds are",
                        new double[]{0, 0.25, 0.5, 0.75, 1.0}, CustomizeScreen::pct, () -> c.volume, v -> c.volume = v)
        ), cy, cy + ch);
    }
}
