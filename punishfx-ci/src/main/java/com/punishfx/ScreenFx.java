package com.punishfx;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Our full-screen shader effects: Blurry, Fisheye, Mirror World, Pixelated and Tiny Window. Each has its
 * own small effect files (assets/punishfx/post_effect/fx/, written by tools/gen_screen_fx.py, one per
 * setting), and every running one is applied in turn right after the world is drawn - so they all stack,
 * and none of them touch the HUD.
 */
public final class ScreenFx {
    /** Pixelated resolutions (rows of pixels). Must match tools/gen_screen_fx.py. */
    public static final int[] HEIGHTS = {240, 180, 144, 120, 96, 72, 54, 36};
    public static final String[] RES_NAMES = {"240p", "180p", "144p", "120p", "96p", "72p", "54p", "36p"};
    public static final String[] PALETTE_NAMES = {"Full colour", "Retro", "Game Boy", "Greyscale"};
    public static final double[] WINDOW_SIZES = {0.7, 0.55, 0.4, 0.25};
    public static final String[] WINDOW_NAMES = {"70%", "55%", "40%", "25%"};
    public static final double[][] WINDOW_SPEEDS = {{0.08, 0.107}, {0.15, 0.2}, {0.27, 0.36}};
    public static final String[] BLUR_NAMES = {"Slightly", "Blurry", "Very blurry", "Lost glasses"};
    public static final String[] FISHEYE_NAMES = {"Slight", "Normal", "Strong", "Bubble"};
    private static final double WINDOW_BAR = 0.05;

    private static final List<Identifier> ACTIVE = new ArrayList<>();
    private static boolean failedOnce = false;

    private ScreenFx() {}

    private static Identifier fx(String path) {
        return Identifier.fromNamespaceAndPath("punishfx", "fx/" + path);
    }

    /** Mirror + pixelated (one combined effect), or null. */
    public static Identifier idFor(boolean mirror, int resIndex, int palette) {
        if (!mirror && resIndex < 0) return null;
        int r = resIndex < 0 ? 0 : Math.min(HEIGHTS.length - 1, resIndex) + 1;
        int p = r == 0 ? 0 : Math.max(0, Math.min(PALETTE_NAMES.length - 1, palette));
        return fx("m" + (mirror ? 1 : 0) + "_r" + r + "_p" + p);
    }

    /** Which effects should be running (-1 = off), in the order they're applied. */
    public static void apply(Minecraft mc, boolean mirror, int resIndex, int palette, int blur, int fisheye, int window, int windowSpeed) {
        List<Identifier> want = new ArrayList<>();
        if (blur >= 0) want.add(fx("blur_" + Math.min(BLUR_NAMES.length - 1, blur)));
        if (fisheye >= 0) want.add(fx("fisheye_" + Math.min(FISHEYE_NAMES.length - 1, fisheye)));
        Identifier mp = idFor(mirror, resIndex, palette);
        if (mp != null) want.add(mp);
        if (window >= 0) want.add(fx("window_" + Math.min(WINDOW_SIZES.length - 1, window) + "_" + Math.max(0, Math.min(2, windowSpeed))));
        if (!want.equals(ACTIVE)) {
            ACTIVE.clear();
            ACTIVE.addAll(want);
        }
        if (window >= 0) windowTick(mc, window, windowSpeed);
        else lastBounce = null;
    }

    public static void clear() {
        ACTIVE.clear();
        lastBounce = null;
    }

    /** The effects running now (for tests and debugging). */
    public static List<String> activeIds() {
        List<String> out = new ArrayList<>();
        for (Identifier id : ACTIVE) out.add(id.toString());
        return out;
    }

    /** Called from GameRendererPostMixin every frame, right after the world is drawn. */
    public static void process(Minecraft mc, CrossFrameResourcePool pool) {
        if (ACTIVE.isEmpty() || mc.level == null) return;
        try {
            for (Identifier id : ACTIVE) {
                PostChain chain = mc.getShaderManager().getPostChain(id, Set.of(PostChain.MAIN_TARGET_ID));
                if (chain != null) chain.process(mc.getMainRenderTarget(), pool);
            }
        } catch (Throwable t) {
            if (!failedOnce) PunishFxClient.LOG.warn("Punish FX: screen effect failed", t);
            failedOnce = true;
        }
    }

    // ---- Tiny Window: a boing whenever it hits an edge (same maths as window.fsh)

    private static long[] lastBounce = null;

    private static void windowTick(Minecraft mc, int size, int speed) {
        if (mc.level == null) return;
        double t = ((mc.level.getGameTime() % 24000L) + mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)) / 20.0;
        double[] v = WINDOW_SPEEDS[Math.max(0, Math.min(2, speed))];
        long bx = (long) Math.floor(t * v[0]), by = (long) Math.floor(t * v[1]);
        if (lastBounce != null) {
            boolean hx = bx != lastBounce[0], hy = by != lastBounce[1];
            if (hx && hy) PunishSounds.event(mc, "window", "corner");
            else if (hx || hy) PunishSounds.event(mc, "window", "bounce");
        }
        lastBounce = new long[]{bx, by};
    }

    /** Where the window is right now, as fractions of the screen {x, yFromBottom, w, h} (tests). */
    public static double[] windowRect(Minecraft mc, int size, int speed) {
        double t = ((mc.level.getGameTime() % 24000L) + mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)) / 20.0;
        double[] v = WINDOW_SPEEDS[Math.max(0, Math.min(2, speed))];
        double s = WINDOW_SIZES[size];
        double tx = 1 - Math.abs((t * v[0]) % 2 - 1), ty = 1 - Math.abs((t * v[1]) % 2 - 1);
        return new double[]{tx * (1 - s), ty * (1 - s - WINDOW_BAR), s, s + WINDOW_BAR};
    }
}
