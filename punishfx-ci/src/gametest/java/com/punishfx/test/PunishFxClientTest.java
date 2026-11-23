package com.punishfx.test;

import com.punishfx.Config;
import com.punishfx.ControlState;
import com.punishfx.Detector;
import com.punishfx.PfxFrame;
import com.punishfx.PunishManager;
import com.punishfx.DonorBoard;
import com.punishfx.MoveScreen;
import com.punishfx.Ui;
import com.punishfx.StreamSession;
import com.punishfx.StreamHud;
import com.punishfx.RecapScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the real mod inside a real Minecraft client (in CI, under a virtual display):
 * measures movement under each movement punishment, checks nausea / FOV / colour inversion,
 * screenshots every effect and every settings page at three GUI sizes, and clicks the UI.
 * Every result line starts with "PUNISHFX-TEST" so CI can pull them out of the log.
 */
public class PunishFxClientTest implements FabricClientGameTest {
    private final List<String> failures = new ArrayList<>();
    private Path shots;

    private static void log(String s) {
        System.out.println("PUNISHFX-TEST " + s);
    }

    private void check(boolean ok, String what) {
        log((ok ? "PASS " : "FAIL ") + what);
        if (!ok) failures.add(what);
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        shots = FabricLoader.getInstance().getGameDir().resolve("punishfx-shots");
        try {
            Files.createDirectories(shots);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        configTests();

        // CI renders in software, and the client and server tick in lockstep, so a slow frame is a
        // slow tick. Menu blur is very expensive in software; turn it off and load the world in a
        // small window, then go back to the screenshot size.
        ctx.runOnClient(mc -> {
            mc.options.menuBackgroundBlurriness().set(0);
            mc.options.renderDistance().set(4);
            mc.options.simulationDistance().set(5);
        });
        ctx.getInput().resizeWindow(427, 240);
        ctx.waitTicks(5);

        // Superflat generates far faster than a normal world, so slow CI machines don't time out.
        try (TestSingleplayerContext sp = ctx.worldBuilder().adjustSettings(state -> {
            for (var entry : java.util.stream.Stream.concat(state.getNormalPresetList().stream(), state.getAltPresetList().stream()).toList()) {
                if (entry.preset() != null && entry.preset().is(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT)) {
                    state.setWorldType(entry);
                    break;
                }
            }
        }).create()) {
            sp.getClientWorld().waitForChunksRender();
            ctx.getInput().resizeWindow(960, 540); // auto GUI scale -> 480x270, the common 1080p size
            ctx.waitTicks(10);
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("weather clear");
            ctx.waitTicks(10);

            movementTests(ctx, server);
            server.runCommand("tp @a ~ ~ ~ 0 10");
            ctx.waitTicks(20);
            effectShots(ctx);
            newPunishmentTests(ctx, server);
            detectorTest(ctx);
            streamTests(ctx);
            uiShots(ctx);
        }

        log("DONE failures=" + failures.size());
        if (!failures.isEmpty()) throw new AssertionError("Punish FX checks failed:\n" + String.join("\n", failures));
    }

    // ------------------------------------------------------------------ config

    private void configTests() {
        Config d = Config.defaults();
        for (int i = 0; i < d.tiers.size(); i++) {
            Config.Tier t = d.tiers.get(i);
            boolean allValid = t.pool.stream().allMatch(id -> com.punishfx.Punishes.get(id) != null);
            check(allValid && !com.punishfx.Punishes.poolFor(t).isEmpty(), "default level " + (i + 1) + " only uses real punishments " + t.pool);
        }
        // an old save (pre-1.2) with removed punishments in its pool
        Config.Tier old = new Config.Tier();
        old.pool = new ArrayList<>(List.of("nausea", "blind", "fake_ban", "kick_title", "vertigo", "frozen", "troll_rain"));
        Config.migratePool(old);
        check(old.pool.equals(List.of("drunk", "blackout", "spin", "frozen", "glitch")), "old saved pools are migrated " + old.pool);
        Config.Tier dead = new Config.Tier();
        dead.pool = new ArrayList<>(List.of("fake_ban", "kick_title"));
        Config.migratePool(dead);
        check(dead.pool.isEmpty() && !com.punishfx.Punishes.poolFor(dead).isEmpty(), "a pool of only removed punishments falls back to 'any'");
    }

    // ------------------------------------------------------------------ movement

    private Vec3 pos(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> mc.player.position());
    }

    /** Walk forward (W) for 25 ticks facing +Z with a punishment on; returns {sideways, forward} metres. */
    private double[] walk(ClientGameTestContext ctx, TestServerContext server, String id) {
        ctx.runOnClient(mc -> { PunishManager.cancelAll(); PunishManager.bannerUntil = 0; });
        ctx.waitTicks(3);
        server.runCommand("tp @a 0.5 101 0.5 0 0");
        ctx.waitTicks(15);
        if (id != null) ctx.runOnClient(mc -> PunishManager.force(id, 0));
        ctx.waitTicks(5);
        Vec3 a = pos(ctx);
        ctx.getInput().holdKeyFor(o -> o.keyUp, 25);
        ctx.waitTicks(6);
        Vec3 b = pos(ctx);
        double fwd = b.z - a.z, side = b.x - a.x;
        log(String.format("move %-18s forward=%6.2f sideways=%6.2f", id == null ? "none" : id, fwd, side));
        return new double[]{side, fwd};
    }

    private void movementTests(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("fill -40 100 -40 40 100 40 minecraft:smooth_stone");
        ctx.waitTicks(5);

        double normal = walk(ctx, server, null)[1];
        check(normal > 2.5, "normal walking moves forward (" + fmt(normal) + "m)");

        double inv = walk(ctx, server, "inverted_controls")[1];
        check(inv < -1.5, "inverted controls: W walks BACKWARDS (" + fmt(inv) + "m)");

        double[] frozen = walk(ctx, server, "frozen");
        check(Math.abs(frozen[1]) < 0.3 && Math.abs(frozen[0]) < 0.3, "frozen: can't move (" + fmt(frozen[1]) + "m)");

        double slow = walk(ctx, server, "sluggish")[1];
        check(slow > 0.2 && slow < normal * 0.6, "sluggish: much slower (" + fmt(slow) + "m vs " + fmt(normal) + "m)");

        double[] drunk = walk(ctx, server, "drunk");
        check(Math.hypot(drunk[0], drunk[1]) > 1.0, "drunk: still walks, stumbling (" + fmt(drunk[1]) + "m fwd, " + fmt(drunk[0]) + "m side)");

        // nausea must actually be ON and blended in (this was the bug: it never faded in)
        ctx.waitTicks(40);
        float blend = ctx.computeOnClient(mc -> {
            MobEffectInstance e = mc.player.getEffect(MobEffects.NAUSEA);
            return e == null ? -1f : e.getBlendFactor(mc.player, 1f);
        });
        check(blend > 0.3f, "drunk: nausea warp is active (blend " + fmt(blend) + ")");
        check(ControlState.drunk > 0.5f, "drunk: camera sway/tilt active (" + fmt(ControlState.drunk) + ")");

        ctx.runOnClient(mc -> { PunishManager.cancelAll(); PunishManager.bannerUntil = 0; });
        ctx.waitTicks(5);
        boolean cleared = ctx.computeOnClient(mc -> mc.player.getEffect(MobEffects.NAUSEA) == null);
        check(cleared, "nausea removed when drunk ends (no leftover effect)");

        Config.get().sluggishLevel = 3; // "A tenth"
        double tenth = walk(ctx, server, "sluggish")[1];
        check(tenth > 0.05 && tenth < normal * 0.2, "Sluggish strength 'A tenth' is slower still (" + fmt(tenth) + "m)");
        Config.get().sluggishLevel = 1;

        double after = walk(ctx, server, null)[1];
        check(after > 2.5, "movement back to normal after punishments (" + fmt(after) + "m)");
    }

    // ------------------------------------------------------------------ effects

    private void shot(ClientGameTestContext ctx, String name) {
        Path p = ctx.takeScreenshot(TestScreenshotOptions.of(name).withDestinationDir(shots).disableCounterPrefix());
        log("shot " + name + " -> " + p.getFileName());
    }

    private void effect(ClientGameTestContext ctx, String id, int wait) {
        ctx.runOnClient(mc -> { PunishManager.cancelAll(); PunishManager.bannerUntil = 0; });
        ctx.waitTicks(8);
        ctx.runOnClient(mc -> PunishManager.force(id, 0));
        ctx.waitTicks(wait);
        shot(ctx, "fx_" + id);
    }

    private float fov(ClientGameTestContext ctx) {
        try {
            return ctx.computeOnClient(mc -> {
                Method m = mc.gameRenderer.getClass().getDeclaredMethod("getFov", Camera.class, float.class, boolean.class);
                m.setAccessible(true);
                return (Float) m.invoke(mc.gameRenderer, mc.gameRenderer.getMainCamera(), 1f, true);
            });
        } catch (Exception e) {
            log("could not read FOV: " + e);
            return -1f;
        }
    }

    private void effectShots(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> { PunishManager.cancelAll(); PunishManager.bannerUntil = 0; });
        ctx.waitTicks(10);
        shot(ctx, "fx_00_baseline");

        // FOV: measure the real field of view before and during tunnel vision
        float fov0 = fov(ctx);
        effect(ctx, "tunnel_vision", 40);
        float fov1 = fov(ctx);
        check(fov0 > 0 && fov1 > 0 && fov1 < fov0 * 0.7f, "tunnel vision narrows the real FOV (" + fmt(fov0) + " -> " + fmt(fov1) + ")");

        // real inversion: the screenshot's colours should be ~255 minus the baseline's
        effect(ctx, "negative", 10);
        try {
            int[] base = mean(shots.resolve("fx_00_baseline.png"));
            int[] neg = mean(shots.resolve("fx_negative.png"));
            boolean inverted = true;
            for (int i = 0; i < 3; i++) inverted &= Math.abs(base[i] + neg[i] - 255) < 45;
            check(inverted, "inverted colors really inverts the screen (base " + rgb(base) + ", inverted " + rgb(neg) + ")");
        } catch (Exception e) {
            check(false, "could not read screenshots for inversion check: " + e);
        }

        effect(ctx, "disco_eyes", 10);
        effect(ctx, "flashbang", 6);
        effect(ctx, "blackout", 10);
        effect(ctx, "fog", 20);
        effect(ctx, "drunk", 50);
        effect(ctx, "glitch", 10);
        effect(ctx, "jitter", 10);
        effect(ctx, "spin", 10);
        effect(ctx, "no_hud", 5);

        // flashbang should last noticeably longer than the level's own duration
        ctx.runOnClient(mc -> { PunishManager.cancelAll(); PunishManager.bannerUntil = 0; });
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> PunishManager.force("flashbang", 0));
        ctx.waitTicks(2);
        long len = ctx.computeOnClient(mc -> {
            for (PunishManager.Active a : PunishManager.active()) if (a.p.id.equals("flashbang")) return a.end - a.start;
            return 0L;
        });
        check(len >= (long) (Config.get().tiers.get(0).seconds * 1000 * 1.5), "flashbang lasts longer (" + len + "ms)");

        // the leave/close countdown (cancelled straight after the shot!)
        ctx.runOnClient(mc -> { PunishManager.cancelAll(); PunishManager.bannerUntil = 0; });
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> PunishManager.force("close_game", 0));
        ctx.waitTicks(15);
        shot(ctx, "fx_zz_close_countdown");
        ctx.runOnClient(mc -> { PunishManager.cancelAll(); PunishManager.bannerUntil = 0; });
        ctx.waitTicks(5);
        boolean pending = ctx.computeOnClient(mc -> PunishManager.extremePending());
        check(!pending, "close-game countdown can be cancelled");
        ctx.runOnClient(mc -> { PunishManager.cancelAll(); PunishManager.bannerUntil = 0; });
        ctx.waitTicks(30);
    }

    // ------------------------------------------------------------------ 1.4 punishments

    private void reset(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> { PunishManager.cancelAll(); PunishManager.bannerUntil = 0; PunishManager.donorUntil = 0; });
        ctx.waitTicks(6);
    }

    /** Mean of (channel a minus channel b) over a region given as fractions of the screenshot. */
    private static double diff(Path png, int a, int b, double x0, double x1, double y0, double y1) throws Exception {
        BufferedImage im = ImageIO.read(png.toFile());
        int w = im.getWidth(), h = im.getHeight();
        long sum = 0, n = 0;
        for (int y = (int) (h * y0); y < (int) (h * y1); y += 3)
            for (int x = (int) (w * x0); x < (int) (w * x1); x += 3) {
                int c = im.getRGB(x, y);
                int[] ch = {(c >> 16) & 255, (c >> 8) & 255, c & 255};
                sum += ch[a] - ch[b];
                n++;
            }
        return n == 0 ? 0 : sum / (double) n;
    }

    private void newPunishmentTests(ClientGameTestContext ctx, TestServerContext server) {
        reset(ctx);
        server.runCommand("tp @a 0.5 101 0.5 0 10");
        ctx.waitTicks(20);
        shot(ctx, "fx_01_sky_baseline");

        // ---- upside down: the sky ends up at the bottom of the screen
        ctx.runOnClient(mc -> Config.get().upsideMode = 0);
        ctx.runOnClient(mc -> PunishManager.force("upside_down", 0));
        ctx.waitTicks(30);
        check(ControlState.upside > 170f, "upside down: camera rolled over (" + fmt(ControlState.upside) + " deg)");
        shot(ctx, "fx_upside_down");
        try {
            double baseTop = diff(shots.resolve("fx_01_sky_baseline.png"), 2, 0, 0.2, 0.8, 0.02, 0.2);
            double baseBot = diff(shots.resolve("fx_01_sky_baseline.png"), 2, 0, 0.2, 0.8, 0.8, 0.95);
            double upTop = diff(shots.resolve("fx_upside_down.png"), 2, 0, 0.2, 0.8, 0.02, 0.2);
            double upBot = diff(shots.resolve("fx_upside_down.png"), 2, 0, 0.2, 0.8, 0.8, 0.95);
            check(baseTop > baseBot + 20 && upBot > upTop + 20, "upside down: sky moves from the top to the bottom (base top/bot blue "
                    + fmt(baseTop) + "/" + fmt(baseBot) + ", flipped " + fmt(upTop) + "/" + fmt(upBot) + ")");
        } catch (Exception e) {
            check(false, "upside down screenshot check: " + e);
        }
        reset(ctx);
        check(ControlState.upside == 0f, "upside down: camera back to normal after");

        // ---- front camera
        ctx.runOnClient(mc -> PunishManager.force("front_camera", 0));
        ctx.waitTicks(10);
        String cam = ctx.computeOnClient(mc -> mc.options.getCameraType().name());
        check(cam.equals("THIRD_PERSON_FRONT"), "forced front camera: camera is third-person front (" + cam + ")");
        shot(ctx, "fx_front_camera");
        reset(ctx);
        String cam2 = ctx.computeOnClient(mc -> mc.options.getCameraType().name());
        check(cam2.equals("FIRST_PERSON"), "front camera: back to first person after (" + cam2 + ")");

        // ---- mirror world: a red wall on the LEFT should appear on the RIGHT
        server.runCommand("fill 1 101 6 8 108 6 minecraft:red_wool");
        server.runCommand("tp @a 0.5 101 0.5 0 10");
        ctx.waitTicks(25);
        shot(ctx, "fx_02_wall_baseline");
        ctx.runOnClient(mc -> PunishManager.force("mirror", 0));
        ctx.waitTicks(10);
        shot(ctx, "fx_mirror");
        try {
            Path b = shots.resolve("fx_02_wall_baseline.png"), m = shots.resolve("fx_mirror.png");
            double bl = diff(b, 0, 1, 0.05, 0.45, 0.2, 0.7), br = diff(b, 0, 1, 0.55, 0.95, 0.2, 0.7);
            double ml = diff(m, 0, 1, 0.05, 0.45, 0.2, 0.7), mr = diff(m, 0, 1, 0.55, 0.95, 0.2, 0.7);
            check(bl > br + 25 && mr > ml + 25, "mirror world flips the picture (red left/right " + fmt(bl) + "/" + fmt(br)
                    + " -> " + fmt(ml) + "/" + fmt(mr) + ")");
        } catch (Exception e) {
            check(false, "mirror screenshot check: " + e);
        }
        // mouse: turning right should turn you left
        double turned = ctx.computeOnClient(mc -> {
            float y0 = mc.player.getYRot();
            mc.player.turn(100, 0);
            return (double) (mc.player.getYRot() - y0);
        });
        check(turned < -5, "mirror world flips mouse look (turn +100 -> " + fmt(turned) + " deg)");
        // keys: A (left) walks right
        double x0 = pos(ctx).x;
        ctx.getInput().holdKeyFor(o -> o.keyLeft, 20);
        ctx.waitTicks(5);
        double dx = pos(ctx).x - x0;
        check(dx < -1.0, "mirror world flips A/D (A moved " + fmt(dx) + "m on X, normally +)");
        reset(ctx);
        server.runCommand("fill 1 101 6 8 108 6 minecraft:air");
        server.runCommand("tp @a 0.5 101 0.5 0 10");
        ctx.waitTicks(10);
        double back = ctx.computeOnClient(mc -> {
            float y0 = mc.player.getYRot();
            mc.player.turn(100, 0);
            float d = mc.player.getYRot() - y0;
            mc.player.turn(-100, 0);
            return (double) d;
        });
        check(back > 5, "mouse look normal again after mirror (" + fmt(back) + ")");
        String post = ctx.computeOnClient(mc -> mc.gameRenderer.currentPostEffect() == null ? "none" : mc.gameRenderer.currentPostEffect().toString());
        check(post.equals("none"), "mirror shader removed after (" + post + ")");

        // ---- language swap
        String normal = ctx.computeOnClient(mc -> net.minecraft.locale.Language.getInstance().getOrDefault("menu.returnToGame"));
        ctx.runOnClient(mc -> Config.get().languageMode = 0); // pirate
        ctx.runOnClient(mc -> PunishManager.force("language", 0));
        ctx.waitTicks(10);
        String pirate = ctx.computeOnClient(mc -> net.minecraft.locale.Language.getInstance().getOrDefault("menu.returnToGame"));
        check(!pirate.equals(normal), "language swap changes the game's text (\"" + normal + "\" -> \"" + pirate + "\")");
        ctx.setScreen(() -> new net.minecraft.client.gui.screens.PauseScreen(true));
        ctx.waitTicks(5);
        shot(ctx, "fx_language_pirate_menu");
        ctx.setScreen(() -> null);
        ctx.runOnClient(mc -> Config.get().languageMode = 1); // upside down
        reset(ctx);
        ctx.runOnClient(mc -> PunishManager.force("language", 0));
        ctx.waitTicks(10);
        ctx.setScreen(() -> new net.minecraft.client.gui.screens.PauseScreen(true));
        ctx.waitTicks(5);
        shot(ctx, "fx_language_upside_down_menu");
        ctx.setScreen(() -> null);
        reset(ctx);
        ctx.waitTicks(5);
        String restored = ctx.computeOnClient(mc -> net.minecraft.locale.Language.getInstance().getOrDefault("menu.returnToGame"));
        check(restored.equals(normal), "language back to normal after (\"" + restored + "\")");
        ctx.runOnClient(mc -> Config.get().languageMode = 4);

        // ---- chipmunk (sound can't be heard in CI; it must just run without breaking anything)
        ctx.runOnClient(mc -> Config.get().chipmunkMode = 2);
        ctx.runOnClient(mc -> PunishManager.force("chipmunk", 0));
        ctx.waitTicks(5);
        boolean chip = ctx.computeOnClient(mc -> {
            for (int i = 0; i < 5; i++)
                mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
            return ControlState.chipmunk && ControlState.goats;
        });
        ctx.waitTicks(10);
        check(chip, "chipmunk mode: squeaky + goat sounds switched on, playing sounds works");
        reset(ctx);
        check(!ControlState.chipmunk && !ControlState.goats, "chipmunk mode off after");
        ctx.runOnClient(mc -> Config.get().chipmunkMode = 0);

        // ---- hotbar: fill it first
        server.runCommand("clear @a");
        String[] items = {"diamond_sword", "dirt 64", "bow", "cooked_beef 32", "oak_planks 64", "torch 64", "water_bucket", "shield", "golden_apple 8"};
        for (String it : items) server.runCommand("give @a minecraft:" + it);
        ctx.waitTicks(10);
        ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
        ctx.waitTicks(3);
        shot(ctx, "fx_03_hotbar_baseline");

        ctx.runOnClient(mc -> Config.get().possessedLevel = 2); // frantic
        ctx.runOnClient(mc -> PunishManager.force("possessed_hotbar", 0));
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int i = 0; i < 12; i++) {
            ctx.waitTicks(7);
            seen.add(ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot()));
        }
        check(seen.size() >= 3, "possessed hotbar: selected slot keeps changing " + seen);
        reset(ctx);
        ctx.runOnClient(mc -> Config.get().possessedLevel = 1);

        ctx.runOnClient(mc -> PunishManager.force("hotbar_chaos", 0));
        ctx.waitTicks(10);
        shot(ctx, "fx_hotbar_chaos");
        ctx.waitTicks(35);
        shot(ctx, "fx_hotbar_chaos_reroll");
        String first = ctx.computeOnClient(mc -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mc.player.getInventory().getItem(0).getItem()).getPath());
        check(first.equals("diamond_sword"), "hotbar chaos is visual only: your real items are untouched (slot 1 = " + first + ")");
        try {
            double changed = 0;
            BufferedImage a = ImageIO.read(shots.resolve("fx_03_hotbar_baseline.png").toFile());
            BufferedImage b = ImageIO.read(shots.resolve("fx_hotbar_chaos.png").toFile());
            int w = a.getWidth(), h = a.getHeight(), n = 0;
            for (int y = h - h / 12; y < h; y += 2)
                for (int x = w / 2 - w / 6; x < w / 2 + w / 6; x += 2, n++)
                    if (Math.abs((a.getRGB(x, y) & 255) - (b.getRGB(x, y) & 255)) > 30) changed++;
            check(changed / n > 0.05, "hotbar chaos changes what the hotbar shows (" + fmt(100 * changed / n) + "% of hotbar pixels)");
        } catch (Exception e) {
            check(false, "hotbar chaos screenshot check: " + e);
        }
        reset(ctx);

        // ---- butterfingers really drops (slot 2 = 64 dirt)
        ctx.runOnClient(mc -> { mc.player.getInventory().setSelectedSlot(1); Config.get().butterLevel = 0; });
        ctx.waitTicks(3);
        int before = ctx.computeOnClient(mc -> mc.player.getInventory().getItem(1).getCount());
        ctx.runOnClient(mc -> PunishManager.force("butterfingers", 0));
        ctx.waitTicks(95);
        int after = ctx.computeOnClient(mc -> mc.player.getInventory().getItem(1).getCount());
        shot(ctx, "fx_butterfingers");
        reset(ctx);
        check(after <= before - 2, "butterfingers drops your items (dirt " + before + " -> " + after + ")");
        boolean optIn = com.punishfx.Punishes.get("butterfingers").optIn;
        boolean notInAll = ctx.computeOnClient(mc -> {
            Config.Tier t = new Config.Tier();
            t.pool = new ArrayList<>();
            return !com.punishfx.Punishes.poolFor(t).contains("butterfingers");
        });
        check(optIn && notInAll, "butterfingers is opt-in only (never picked by 'All')");
        server.runCommand("kill @e[type=item]");
        server.runCommand("clear @a");
        ctx.waitTicks(5);
    }

    // ------------------------------------------------------------------ stream mode

    private void streamTests(ClientGameTestContext ctx) {
        reset(ctx);
        ctx.runOnClient(mc -> {
            StreamSession.clearHistory();
            Config c = Config.get();
            c.streamHud = true;
            c.streamX = 0;
            c.streamY = 0;
            c.streamScale = 1.0;
            c.topShow = true;
            StreamSession.start();
        });
        check(StreamSession.live(), "stream starts");
        ctx.runOnClient(mc -> Detector.onMessage("Bob paid you $1,000,000.", false));
        ctx.waitTicks(30);
        ctx.runOnClient(mc -> Detector.onMessage("Alex paid you $2,500,000.", false));
        ctx.waitTicks(30);
        shot(ctx, "fx_stream_live_hud");
        double total = ctx.computeOnClient(mc -> StreamSession.current().total());
        int count = ctx.computeOnClient(mc -> StreamSession.current().donations.size());
        check(Math.abs(total - 3_500_000) < 1 && count == 2, "stream counts donations ($" + total + " from " + count + ")");
        int started = ctx.computeOnClient(mc -> StreamSession.current().punishCount.values().stream().mapToInt(Integer::intValue).sum());
        check(started >= 2, "stream records which punishments ran (" + started + ")");
        reset(ctx);
        ctx.runOnClient(mc -> StreamSession.stop());
        check(!StreamSession.live() && StreamSession.history().size() == 1 && Math.abs(StreamSession.history().get(0).total() - 3_500_000) < 1,
                "ending the stream saves it to history");

        // a long, busy example stream so the recap screen has something to show off
        ctx.runOnClient(mc -> {
            StreamSession.Record r = new StreamSession.Record();
            long end = System.currentTimeMillis(), start = end - (2 * 3600 + 13 * 60) * 1000L;
            r.start = start;
            r.end = end;
            String[] who = {"Huddy83834", "Steve", "Alex", "Notch", "Jeb_", "Dinnerbone", "xX_Gamer_Xx", "Grian"};
            double[] amt = {250_000, 1_000_000, 500_000, 2_000_000, 100_000, 5_000_000, 750_000, 300_000};
            java.util.Random rnd = new java.util.Random(7);
            for (int i = 0; i < 34; i++) {
                long t = start + (long) ((end - start) * Math.pow((i + rnd.nextDouble()) / 34.0, 0.9));
                int k = rnd.nextInt(who.length);
                r.donations.add(new StreamSession.Donation(t, who[k], amt[rnd.nextInt(amt.length)], 0));
            }
            r.donations.sort((a, b) -> Long.compare(a.t, b.t));
            r.punishCount.put("drunk", 9);
            r.punishCount.put("mirror", 6);
            r.punishCount.put("frozen", 4);
            r.punishedMs = 41 * 60_000L;
            r.donorChaosMs.put("Dinnerbone", 9 * 60_000L);
            r.donorChaosMs.put("Steve", 5 * 60_000L);
            r.cancels = 3;
            r.extremesTriggered = 2;
            r.extremesCancelled = 1;
            StreamSession.history().add(r);
        });
        ctx.setScreen(() -> new RecapScreen(null));
        ctx.waitTicks(50);
        shot(ctx, "ui_recap_480x270");
        ctx.clickScreenButton("Close");
        ctx.waitTicks(3);

        // the Stream page's buttons: go live, then end it -> the recap opens
        ctx.runOnClient(mc -> Config.get().streamAutoRecap = true);
        ctx.setScreen(() -> PfxFrame.open(null, PfxFrame.Page.STREAM));
        ctx.waitTicks(4);
        ctx.clickScreenButton("Go live");
        ctx.waitTicks(4);
        check(StreamSession.live(), "'Go live' on the Stream page starts a stream");
        ctx.runOnClient(mc -> Detector.onMessage("Steve paid you $750,000.", false));
        ctx.waitTicks(10);
        ctx.runOnClient(mc -> { PunishManager.cancelAll(); PunishManager.bannerUntil = 0; PunishManager.donorUntil = 0; });
        ctx.setScreen(() -> PfxFrame.open(null, PfxFrame.Page.STREAM));
        ctx.waitTicks(4);
        shot(ctx, "ui_stream_page_live");
        ctx.clickScreenButton("End stream");
        ctx.waitTicks(30);
        String scr = ctx.computeOnClient(mc -> mc.screen == null ? "none" : mc.screen.getClass().getSimpleName());
        check(!StreamSession.live() && scr.equals("RecapScreen"), "'End stream' ends it and shows the recap (" + scr + ")");
        shot(ctx, "ui_recap_short_stream");
        ctx.setScreen(() -> new RecapScreen(null, 1));
        ctx.waitTicks(50);

        // live counter editor
        ctx.runOnClient(mc -> StreamSession.start());
        ctx.setScreen(() -> new MoveScreen(null, StreamHud.PIECE));
        ctx.waitTicks(4);
        shot(ctx, "ui_move_live_counter");
        ctx.clickScreenButton("Done");
        ctx.setScreen(() -> null);
        ctx.runOnClient(mc -> { StreamSession.stop(); StreamSession.history().remove(StreamSession.history().size() - 1); });
        ctx.waitTicks(3);
    }

    // ------------------------------------------------------------------ donation detection

    private void detectorTest(ClientGameTestContext ctx) {
        String msg = "Huddy83834 paid you $1,000,000.";
        StringBuilder why = new StringBuilder();
        Detector.Result r = ctx.computeOnClient(mc -> Detector.detect(Detector.clean(msg), why));
        check(r != null && Math.abs(r.amount() - 1_000_000) < 1 && "Huddy83834".equals(r.player()),
                "detects \"" + msg + "\" -> " + (r == null ? "nothing (" + why + ")" : r.player() + " " + r.amount()));

        String[] real = {"Bob paid you $1M.", "[Economy] Bob paid you $500,000.", "You have received $250,000 from Bob.",
                "$1,000 has been received from Bob.", "+$50,000 from Bob", "Bob has sent you $1,000!"};
        for (String line : real) {
            Detector.Result rr = ctx.computeOnClient(mc -> Detector.detect(Detector.clean(line), new StringBuilder()));
            check(rr != null && rr.player().equals("Bob"), "real payment detected: \"" + line + "\"");
        }
        String[] fake = {"Spoofer: Bob paid you $1,000,000", "[VIP] Spoofer \u00bb Bob paid you $1,000,000",
                "<Spoofer> Bob paid you $1,000,000", "Spoofer \u27a4 Bob paid you $1,000,000", "Spoofer \u2192 Bob paid you $1M",
                "[Spoofer -> me] Bob paid you $1M", "Spoofer whispers to you: Bob paid you $1M", "* Spoofer Bob paid you $1M",
                "\u2726 Spoofer \u2726 Bob paid you $1M", "~Nick Bob paid you $1M", "Spoofer Bob paid you $1M",
                "[Member] Spoofer: Bob paid you $1M", "Bob paid you $1M lol", "Spoofer: You received $1M from Bob",
                "Bob paid you $1,000,000 was slain by Zombie", "Spoofer - Bob paid you $1M", "Spoofer \u2503 Bob paid you $1M",
                "[VIP] \u00bb Bob paid you $1M", "Spoofer \u21d2 You have received $250,000 from Bob."};
        for (String line : fake) {
            StringBuilder w = new StringBuilder();
            Detector.Result fr = ctx.computeOnClient(mc -> Detector.detect(Detector.clean(line), w));
            check(fr == null, "fake chat line ignored: \"" + line + "\" (" + w + ")");
        }
        // end to end: a fake line through the real message hook must not punish you
        ctx.runOnClient(mc -> { PunishManager.cancelAll(); PunishManager.bannerUntil = 0; PunishManager.donorUntil = 0; });
        ctx.waitTicks(3);
        ctx.runOnClient(mc -> Detector.onMessage("[VIP] Spoofer \u27a4 Bob paid you $10,000,000", false));
        ctx.waitTicks(5);
        boolean punished = ctx.computeOnClient(mc -> !PunishManager.active().isEmpty() || PunishManager.donorShowing() || PunishManager.extremePending());
        check(!punished, "a faked payment typed in chat does nothing");

        ctx.runOnClient(mc -> Detector.onMessage(msg, false));
        ctx.waitTicks(12);
        boolean onBoard = ctx.computeOnClient(mc -> DonorBoard.top(0, 10).stream().anyMatch(e -> e.name.equals("Huddy83834") && e.total >= 1_000_000));
        check(onBoard, "a real donation is added to the top donors board");
        boolean banner = ctx.computeOnClient(mc -> PunishManager.donorShowing() && PunishManager.donor.equals("Huddy83834"));
        check(banner, "donation shows the banner with who paid (" + PunishManager.donor + " " + PunishManager.donorAmount + ")");
        shot(ctx, "fx_donation_banner");
        ctx.runOnClient(mc -> { PunishManager.cancelAll(); PunishManager.bannerUntil = 0; });
        ctx.waitTicks(80); // let the banner time out

        // cooldown: a second donation right after only shows the banner
        ctx.runOnClient(mc -> { Config.get().cooldownSeconds = 30; PunishManager.cancelAll(); PunishManager.bannerUntil = 0; });
        ctx.runOnClient(mc -> Detector.onMessage("Bob paid you $1,000,000.", false));
        ctx.waitTicks(3);
        ctx.runOnClient(mc -> { PunishManager.cancelAll(); PunishManager.bannerUntil = 0; });
        ctx.runOnClient(mc -> Detector.onMessage("Alex paid you $2,000,000.", false));
        ctx.waitTicks(3);
        boolean cooled = ctx.computeOnClient(mc -> PunishManager.active().isEmpty() && PunishManager.donorSub.contains("cooldown"));
        check(cooled, "donation cooldown: a second donation only shows the banner (" + PunishManager.donorSub + ")");
        ctx.runOnClient(mc -> { Config.get().cooldownSeconds = 0; PunishManager.cancelAll(); PunishManager.bannerUntil = 0; PunishManager.donorUntil = 0; });
        ctx.waitTicks(5);

        topDonorsTests(ctx);
    }

    // ------------------------------------------------------------------ top donors board

    private void topDonorsTests(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            DonorBoard.reset();
            DonorBoard.record("Huddy83834", 5_000_000);
            DonorBoard.record("Steve", 1_200_000);
            DonorBoard.record("Alex", 800_000);
            DonorBoard.record("Notch", 250_000);
            DonorBoard.record("Steve", 300_000);
            Config c = Config.get();
            c.topShow = true;
            c.topX = 1.0;
            c.topY = 0.3;
            c.topScale = 1.0;
            Config.save();
        });
        boolean order = ctx.computeOnClient(mc -> {
            var t = DonorBoard.top(0, 5);
            return t.size() == 4 && t.get(0).name.equals("Huddy83834") && t.get(1).name.equals("Steve") && t.get(1).total == 1_500_000;
        });
        check(order, "top donors are sorted and add up repeat donations");
        ctx.waitTicks(3);
        shot(ctx, "fx_topdonors_default");

        ctx.runOnClient(mc -> {
            Config c = Config.get();
            c.topFont = 1; c.topBackground = 2; c.topColor = 7; c.topBold = true; c.topScale = 1.5; c.topX = 0; c.topY = 0;
            c.topTitle = "BIGGEST FANS";
            Config.save();
        });
        ctx.waitTicks(3);
        shot(ctx, "fx_topdonors_thin_glass_rainbow");
        ctx.runOnClient(mc -> {
            Config c = Config.get();
            c.topFont = 3; c.topBackground = 3; c.topColor = 2; c.topBold = false; c.topItalic = true; c.topScale = 1.0;
            c.topX = 0.5; c.topY = 1.0; c.topAmounts = 1;
            Config.save();
        });
        ctx.waitTicks(3);
        shot(ctx, "fx_topdonors_illager_theme_gold");

        // back to defaults, then drag it with the real mouse in the editor
        ctx.runOnClient(mc -> {
            Config c = Config.get();
            c.topFont = 0; c.topBackground = 1; c.topColor = 0; c.topItalic = false; c.topAmounts = 0;
            c.topTitle = "TOP DONORS"; c.topX = 1.0; c.topY = 0.3; c.topScale = 1.0;
            Config.save();
        });
        ctx.setScreen(() -> new MoveScreen(null));
        ctx.waitTicks(4);
        double[] start = ctx.computeOnClient(mc -> {
            int[] b = com.punishfx.TopDonorsHud.bounds(mc.font, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), true);
            double gs = mc.getWindow().getGuiScale();
            return new double[]{(b[0] + b[2] / 2.0) * gs, (b[1] + b[3] / 2.0) * gs, Config.get().topX, Config.get().topY};
        });
        ctx.getInput().setCursorPos(start[0], start[1]);
        ctx.waitTicks(2);
        ctx.getInput().holdMouse(0);
        ctx.waitTicks(2);
        for (int i = 0; i < 6; i++) {
            ctx.getInput().moveCursor(-60, 30);
            ctx.waitTicks(1);
        }
        ctx.getInput().releaseMouse(0);
        ctx.waitTicks(2);
        double nx = Config.get().topX, ny = Config.get().topY;
        check(nx < start[2] - 0.1 && ny > start[3] + 0.05, "dragging moves the board (" + fmt(start[2]) + "," + fmt(start[3]) + " -> " + fmt(nx) + "," + fmt(ny) + ")");
        double s0 = Config.get().topScale;
        ctx.getInput().scroll(1);
        ctx.waitTicks(2);
        ctx.getInput().scroll(1);
        ctx.waitTicks(2);
        check(Config.get().topScale > s0 + 0.15, "scrolling makes the board bigger (" + fmt(s0) + " -> " + fmt(Config.get().topScale) + ")");
        shot(ctx, "ui_move_editor");
        ctx.clickScreenButton("Done");
        ctx.waitTicks(3);
        ctx.setScreen(() -> null);
        ctx.waitTicks(3);
    }

    // ------------------------------------------------------------------ UI

    private void pages(ClientGameTestContext ctx, String size) {
        ctx.setScreen(() -> PfxFrame.open(null, PfxFrame.Page.GENERAL));
        ctx.waitTicks(4);
        shot(ctx, "ui_" + size + "_1_general");
        ctx.clickScreenButton("Levels");
        ctx.waitTicks(4);
        shot(ctx, "ui_" + size + "_2_levels");
        ctx.clickScreenButton("Punishments");
        ctx.waitTicks(4);
        shot(ctx, "ui_" + size + "_3_punishments");
        ctx.clickScreenButton("Customize");
        ctx.waitTicks(4);
        shot(ctx, "ui_" + size + "_4_customize");
        ctx.clickScreenButton("Top Donors");
        ctx.waitTicks(4);
        shot(ctx, "ui_" + size + "_5_topdonors");
        ctx.clickScreenButton("Stream");
        ctx.waitTicks(4);
        shot(ctx, "ui_" + size + "_6_stream");
        ctx.setScreen(() -> null);
        ctx.waitTicks(3);
    }

    private void uiShots(ClientGameTestContext ctx) {
        // clicking a settings row really flips the setting, and the page navigation works
        ctx.setScreen(() -> PfxFrame.open(null, PfxFrame.Page.GENERAL));
        ctx.waitTicks(4);
        boolean before = Config.get().sounds;
        ctx.clickScreenButton("Sounds");
        ctx.waitTicks(2);
        check(Config.get().sounds != before, "clicking 'Sounds' toggles it (" + before + " -> " + Config.get().sounds + ")");
        ctx.clickScreenButton("Sounds");
        ctx.waitTicks(2);
        ctx.clickScreenButton("Punishments");
        ctx.waitTicks(4);
        String cls = ctx.computeOnClient(mc -> mc.screen == null ? "none" : mc.screen.getClass().getSimpleName());
        check(cls.equals("PunishPoolScreen"), "sidebar 'Punishments' opens that page (" + cls + ")");
        ctx.clickScreenButton("All");
        ctx.waitTicks(2);
        check(Config.get().tiers.get(0).pool.isEmpty(), "'All' sets level 1 to every punishment");
        ctx.clickScreenButton("Frozen");
        ctx.waitTicks(2);
        check(!Config.get().tiers.get(0).pool.isEmpty() && !Config.get().tiers.get(0).pool.contains("frozen"),
                "unticking 'Frozen' removes it from level 1 " + Config.get().tiers.get(0).pool);
        ctx.clickScreenButton("Frozen");
        ctx.waitTicks(2);
        check(Config.get().tiers.get(0).pool.isEmpty(), "ticking it back restores 'every punishment'");
        ctx.runOnClient(mc -> Config.reset());
        ctx.setScreen(() -> null);
        ctx.waitTicks(3);

        // theme: the first ">" on Customize is the theme picker
        ctx.setScreen(() -> PfxFrame.open(null, PfxFrame.Page.CUSTOMIZE));
        ctx.waitTicks(4);
        ctx.clickScreenButton(">");
        ctx.waitTicks(3);
        check(Config.get().theme == 1 && Ui.ACCENT == Ui.themeAccent(1), "theme picker changes the colours (theme " + Config.get().theme + ")");
        shot(ctx, "ui_theme_violet_customize");
        ctx.clickScreenButton("General");
        ctx.waitTicks(3);
        shot(ctx, "ui_theme_violet_general");
        ctx.runOnClient(mc -> { Config.get().theme = 0; Config.save(); });
        ctx.setScreen(() -> null);
        ctx.waitTicks(3);

        pages(ctx, "480x270");
        ctx.getInput().resizeWindow(854, 480); // auto -> 427x240, the smallest Minecraft allows
        ctx.waitTicks(5);
        pages(ctx, "427x240");
        recapShot(ctx, "427x240");
        ctx.runOnClient(mc -> mc.options.guiScale().set(2));
        ctx.getInput().resizeWindow(1280, 720); // GUI scale 2 -> 640x360
        ctx.waitTicks(5);
        pages(ctx, "640x360");
        recapShot(ctx, "640x360");
    }

    private void recapShot(ClientGameTestContext ctx, String size) {
        if (StreamSession.history().size() < 2) return;
        ctx.setScreen(() -> new RecapScreen(null, 1));
        ctx.waitTicks(50);
        shot(ctx, "ui_recap_" + size);
        ctx.setScreen(() -> null);
        ctx.waitTicks(3);
    }

    // ------------------------------------------------------------------ helpers

    private static int[] mean(Path png) throws Exception {
        BufferedImage im = ImageIO.read(png.toFile());
        long r = 0, g = 0, b = 0, n = 0;
        int w = im.getWidth(), h = im.getHeight();
        // the middle of the screen, away from the HUD
        for (int y = h / 6; y < h * 2 / 3; y += 3)
            for (int x = w / 5; x < w * 4 / 5; x += 3) {
                int c = im.getRGB(x, y);
                r += (c >> 16) & 255;
                g += (c >> 8) & 255;
                b += c & 255;
                n++;
            }
        return new int[]{(int) (r / n), (int) (g / n), (int) (b / n)};
    }

    private static String rgb(int[] c) {
        return c[0] + "," + c[1] + "," + c[2];
    }

    private static String fmt(double d) {
        return String.format("%.2f", d);
    }
}
