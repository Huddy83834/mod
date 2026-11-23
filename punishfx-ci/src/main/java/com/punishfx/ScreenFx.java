package com.punishfx;

import com.punishfx.mixin.GameRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Our full-screen shader effects (Mirror World, Pixelated). Minecraft can only run one post effect at a
 * time, and its settings are fixed per file, so every combination has its own small file
 * (assets/punishfx/post_effect/fx/, written by tools/gen_screen_fx.py) and this picks the right one.
 */
public final class ScreenFx {
    /** Pixelated resolutions (rows of pixels). Must match tools/gen_screen_fx.py. */
    public static final int[] HEIGHTS = {240, 180, 144, 120, 96, 72, 54, 36};
    public static final String[] RES_NAMES = {"240p", "180p", "144p", "120p", "96p", "72p", "54p", "36p"};
    public static final String[] PALETTE_NAMES = {"Full colour", "Retro", "Game Boy", "Greyscale"};

    private static Identifier current = null;

    private ScreenFx() {}

    /**
     * @param mirror   flip the picture left-right
     * @param resIndex index into {@link #HEIGHTS}, or -1 for no pixelation
     * @param palette  index into {@link #PALETTE_NAMES} (only used while pixelated)
     */
    public static Identifier idFor(boolean mirror, int resIndex, int palette) {
        if (!mirror && resIndex < 0) return null;
        int r = resIndex < 0 ? 0 : Math.min(HEIGHTS.length - 1, resIndex) + 1;
        int p = r == 0 ? 0 : Math.max(0, Math.min(PALETTE_NAMES.length - 1, palette));
        return Identifier.fromNamespaceAndPath("punishfx", "fx/m" + (mirror ? 1 : 0) + "_r" + r + "_p" + p);
    }

    public static void apply(Minecraft mc, boolean mirror, int resIndex, int palette) {
        Identifier want = idFor(mirror, resIndex, palette);
        try {
            Identifier on = mc.gameRenderer.currentPostEffect();
            boolean ours = on != null && on.getNamespace().equals("punishfx");
            if (want == null) {
                if (ours || (on != null && on.equals(current))) mc.gameRenderer.clearPostEffect();
                current = null;
            } else if (!want.equals(on)) {
                ((GameRendererAccessor) mc.gameRenderer).punishfx$setPostEffect(want);
                current = want;
            }
        } catch (Throwable t) {
            PunishFxClient.LOG.warn("Punish FX: screen effect failed", t);
            current = null;
        }
    }

    public static Identifier current() {
        return current;
    }
}
