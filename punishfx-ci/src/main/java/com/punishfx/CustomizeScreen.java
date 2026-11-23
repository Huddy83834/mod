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
                new Choice("Theme colour", "Colour of menus and banners",
                        Ui.THEME_NAMES, () -> c.theme, v -> c.theme = v),
                numbers("Screen effect strength", "How strong colour effects look",
                        new double[]{0.5, 0.75, 1.0, 1.25, 1.5}, CustomizeScreen::pct, () -> c.overlayStrength, v -> c.overlayStrength = v),

                new Header("Donation banner"),
                new Choice("Banner position", "Where the donation banner shows",
                        new String[]{"Top", "Middle", "Bottom"}, () -> c.bannerPos, v -> c.bannerPos = v),
                numbers("Banner size", "How big the banner is",
                        new double[]{0.75, 1.0, 1.25, 1.5, 2.0}, CustomizeScreen::pct, () -> c.bannerScale, v -> c.bannerScale = v),
                numbers("Banner time", "How long the banner stays up",
                        new double[]{2, 3.5, 5, 7, 10}, v -> (v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v)) + "s",
                        () -> c.bannerSeconds, v -> c.bannerSeconds = v),
                new Toggle("List the punishments", "Show what it triggered, too",
                        () -> c.bannerShowPunishments, v -> c.bannerShowPunishments = v),

                new Header("Punishment strength"),
                new Choice("Sluggish", "How slow you walk", new String[]{"Half speed", "A third", "A fifth", "A tenth"},
                        () -> c.sluggishLevel, v -> c.sluggishLevel = v),
                new Choice("Drunk", "How much you sway and stumble", new String[]{"Tipsy", "Drunk", "Wasted"},
                        () -> c.drunkLevel, v -> c.drunkLevel = v),
                new Choice("Jitter", "How hard the view shakes", new String[]{"Light", "Normal", "Heavy"},
                        () -> c.jitterLevel, v -> c.jitterLevel = v),
                new Choice("Glitch", "How hard the view snaps", new String[]{"Light", "Normal", "Heavy"},
                        () -> c.glitchLevel, v -> c.glitchLevel = v),
                new Choice("Spin cycle", "How fast your camera spins", new String[]{"Slow", "Normal", "Fast", "Blender"},
                        () -> c.spinLevel, v -> c.spinLevel = v),
                new Choice("Tunnel vision", "How far your view closes in", new String[]{"Slight", "Normal", "Narrow", "Pinhole"},
                        () -> c.tunnelLevel, v -> c.tunnelLevel = v),
                new Choice("Lag spike", "How long the freezes last", new String[]{"Mild", "Normal", "Brutal"},
                        () -> c.lagLevel, v -> c.lagLevel = v),
                new Choice("Flashbang", "How long you stay blinded", new String[]{"Short", "Normal", "Long"},
                        () -> c.flashLevel, v -> c.flashLevel = v),
                new Choice("Disco eyes", "How fast the colours change", new String[]{"Slow", "Normal", "Fast"},
                        () -> c.discoLevel, v -> c.discoLevel = v),
                new Choice("Upside down", "Flip over once, or keep rolling", new String[]{"Flip over", "Keep rolling"},
                        () -> c.upsideMode, v -> c.upsideMode = v),
                new Choice("Pixelated resolution", "How blocky Pixelated makes your game", ScreenFx.RES_NAMES,
                        () -> c.pixelRes, v -> c.pixelRes = v),
                new Choice("Pixelated colours", "", ScreenFx.PALETTE_NAMES, () -> c.pixelPalette, v -> c.pixelPalette = v,
                        new String[]{"Normal colours, just blocky", "27 colours with retro dithering", "Four shades of green", "Black and white"}),
                new Toggle("Pixelated gets worse", "Starts sharp, then gets blockier and blockier", () -> c.pixelDegrade, v -> c.pixelDegrade = v),
                new Choice("Language swap", "Which joke language you get", PunishManager.LANGUAGE_NAMES,
                        () -> c.languageMode, v -> c.languageMode = v),
                new Choice("Chipmunk", "Squeaky sounds, goat screams, or both", new String[]{"Squeaky", "Goat screams", "Both"},
                        () -> c.chipmunkMode, v -> c.chipmunkMode = v),
                new Choice("Possessed hotbar", "How often your slot jumps", new String[]{"Slow", "Normal", "Frantic"},
                        () -> c.possessedLevel, v -> c.possessedLevel = v),
                new Choice("Butterfingers", "What you drop (if you tick it)", new String[]{"One item", "Whole stack", "Random slot"},
                        () -> c.butterLevel, v -> c.butterLevel = v),

                new Header("Safety"),
                numbers("Donation cooldown", "Quick repeats only show the banner",
                        new double[]{0, 3, 5, 10, 30, 60}, v -> v == 0 ? "Off" : (int) v + "s", () -> c.cooldownSeconds, v -> c.cooldownSeconds = (int) v),
                numbers("Leave / close countdown", "Time to cancel leave / close",
                        new double[]{3, 5, 10, 15}, v -> (int) v + "s", () -> c.countdownSeconds, v -> c.countdownSeconds = (int) v),
                numbers("Sound volume", "How loud Punish FX is",
                        new double[]{0, 0.25, 0.5, 0.75, 1.0}, CustomizeScreen::pct, () -> c.volume, v -> c.volume = v)
        ), cy, cy + ch);
    }
}
