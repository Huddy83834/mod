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
            pixelTests(ctx, server);
            screenFxTests(ctx, server);
            soundTests(ctx);
            gameTests(ctx, server);
            overlapTests(ctx);
            detectorTest(ctx);
            streamTests(ctx);
            presetTests(ctx);
            giveawayTests(ctx);
            giveaway2Tests(ctx);
            goalTests(ctx);
            simplerTests(ctx);
            teachTests(ctx);
            presetStreamTests(ctx);
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
        String post = ctx.computeOnClient(mc -> String.valueOf(com.punishfx.ScreenFx.activeIds()));
        check(post.equals("[]"), "mirror shader removed after (" + post + ")");

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
        // Close is drawn on the scaled canvas, so click where it really is on screen
        double[] closeAt = ctx.computeOnClient(mc -> {
            float sc = Math.min(mc.getWindow().getGuiScaledWidth() / 480f, mc.getWindow().getGuiScaledHeight() / 270f);
            double ox = Math.round((mc.getWindow().getGuiScaledWidth() - 480 * sc) / 2f), oy = Math.round((mc.getWindow().getGuiScaledHeight() - 270 * sc) / 2f);
            double gs = mc.getWindow().getGuiScale();
            return new double[]{(ox + (480 - 12 - 35) * sc) * gs, (oy + 256 * sc) * gs};
        });
        ctx.getInput().setCursorPos(closeAt[0], closeAt[1]);
        ctx.waitTicks(2);
        ctx.getInput().pressMouse(0);
        ctx.waitTicks(3);
        String afterClose = ctx.computeOnClient(mc -> mc.screen == null ? "none" : mc.screen.getClass().getSimpleName());
        check(afterClose.equals("none"), "recap Close button closes it (" + afterClose + ")");

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

    // ------------------------------------------------------------------ 1.5: pixelated

    /** Fraction of neighbouring pixel pairs (in the middle of the picture) that differ: low = blocky. */
    private static double edges(Path png) throws Exception {
        BufferedImage im = ImageIO.read(png.toFile());
        int w = im.getWidth(), h = im.getHeight();
        long diff = 0, n = 0;
        for (int y = h / 5; y < h * 3 / 4; y += 2)
            for (int x = w / 5; x < w * 4 / 5 - 1; x++, n++) {
                int a = im.getRGB(x, y), b = im.getRGB(x + 1, y);
                int d = Math.abs(((a >> 16) & 255) - ((b >> 16) & 255)) + Math.abs(((a >> 8) & 255) - ((b >> 8) & 255)) + Math.abs((a & 255) - (b & 255));
                if (d > 6) diff++;
            }
        return diff / (double) Math.max(1, n);
    }

    private void pixelTests(ClientGameTestContext ctx, TestServerContext server) {
        reset(ctx);
        server.runCommand("tp @a 0.5 101 0.5 0 35");
        ctx.waitTicks(20);
        shot(ctx, "fx_04_floor_baseline");
        ctx.runOnClient(mc -> { Config c = Config.get(); c.pixelRes = 7; c.pixelPalette = 0; c.pixelDegrade = false; });
        ctx.runOnClient(mc -> PunishManager.force("pixelated", 0));
        ctx.waitTicks(10);
        String id = ctx.computeOnClient(mc -> String.valueOf(com.punishfx.ScreenFx.activeIds()));
        check(id.equals("[punishfx:fx/m0_r8_p0]"), "pixelated turns on the 36p effect (" + id + ")");
        shot(ctx, "fx_pixelated_36p");
        try {
            double base = edges(shots.resolve("fx_04_floor_baseline.png")), pix = edges(shots.resolve("fx_pixelated_36p.png"));
            check(base > 0.04 && pix < base * 0.6, "pixelated really lowers the resolution (detail " + fmt(base) + " -> " + fmt(pix) + ")");
        } catch (Exception e) {
            check(false, "pixelated screenshot check: " + e);
        }
        reset(ctx);
        ctx.runOnClient(mc -> { Config c = Config.get(); c.pixelRes = 4; c.pixelPalette = 2; });
        ctx.runOnClient(mc -> PunishManager.force("pixelated", 0));
        ctx.waitTicks(10);
        shot(ctx, "fx_pixelated_96p_gameboy");
        try {
            BufferedImage im = ImageIO.read(shots.resolve("fx_pixelated_96p_gameboy.png").toFile());
            int green = 0, n = 0;
            for (int y = im.getHeight() / 5; y < im.getHeight() * 3 / 4; y += 7)
                for (int x = im.getWidth() / 5; x < im.getWidth() * 4 / 5; x += 7, n++) {
                    int c = im.getRGB(x, y);
                    if (((c >> 8) & 255) >= ((c >> 16) & 255) && ((c >> 8) & 255) > (c & 255)) green++;
                }
            check(green > n * 0.9, "Game Boy colours: the picture is all green (" + green + "/" + n + ")");
        } catch (Exception e) {
            check(false, "game boy screenshot check: " + e);
        }
        reset(ctx);
        ctx.runOnClient(mc -> { Config c = Config.get(); c.pixelRes = 5; c.pixelPalette = 1; });
        ctx.runOnClient(mc -> PunishManager.force("pixelated", 0));
        ctx.waitTicks(10);
        shot(ctx, "fx_pixelated_72p_retro");
        // with mirror world at the same time: one combined effect
        ctx.runOnClient(mc -> PunishManager.force("mirror", 0));
        ctx.waitTicks(5);
        String both = ctx.computeOnClient(mc -> String.valueOf(com.punishfx.ScreenFx.activeIds()));
        check(both.equals("[punishfx:fx/m1_r6_p1]"), "pixelated + mirror world run together (" + both + ")");
        reset(ctx);
        String off = ctx.computeOnClient(mc -> String.valueOf(com.punishfx.ScreenFx.activeIds()));
        check(off.equals("[]"), "pixelated effect removed after (" + off + ")");
        // gets worse over time: starts at 240p
        ctx.runOnClient(mc -> { Config c = Config.get(); c.pixelRes = 7; c.pixelPalette = 0; c.pixelDegrade = true; });
        ctx.runOnClient(mc -> PunishManager.force("pixelated", 0));
        ctx.waitTicks(3);
        String early = ctx.computeOnClient(mc -> String.valueOf(com.punishfx.ScreenFx.activeIds()));
        ctx.waitTicks(60);
        String later = ctx.computeOnClient(mc -> String.valueOf(com.punishfx.ScreenFx.activeIds()));
        check(early.contains("_r1_") && !later.equals(early), "'gets worse' starts sharp and gets blockier (" + early + " -> " + later + ")");
        reset(ctx);
        ctx.runOnClient(mc -> { Config c = Config.get(); c.pixelRes = 3; c.pixelPalette = 0; c.pixelDegrade = false; });
        server.runCommand("tp @a 0.5 101 0.5 0 10");
        ctx.waitTicks(5);
    }

    // ------------------------------------------------------------------ 1.5: wheel + case

    private void gameTests(ClientGameTestContext ctx, TestServerContext server) {
        reset(ctx);
        // ---- maths, without the animation: the pointer always ends on the chosen slot, and the luck settings hold
        String maths = ctx.computeOnClient(mc -> {
            Config c = Config.get();
            c.wheelSlots = 2; c.wheelNothing = 2; c.wheelAgain = 1; c.wheelDouble = 1; c.wheelJackpot = 1;
            StringBuilder bad = new StringBuilder();
            for (int luck = 0; luck < 3; luck++) {
                c.wheelLuck = luck;
                int nothing = 0;
                for (int i = 0; i < 400; i++) {
                    com.punishfx.Games.Game g = com.punishfx.Games.build(com.punishfx.Games.Kind.WHEEL, 1, "T", null, 1, -1, 1, 0, i * 31L + luck);
                    g.start = 1000;
                    if (g.under(g.endAt() + 1) != g.win) bad.append("wheel lands wrong (luck ").append(luck).append(") ");
                    if (g.winner().id.equals(com.punishfx.Games.NOTHING)) nothing++;
                    if (g.entries.size() != 8) bad.append("wrong slot count ");
                }
                if (luck == 1 && nothing > 0) bad.append("rigged landed on NOTHING ").append(nothing).append("x ");
                if (luck == 0 && (nothing < 50 || nothing > 160)) bad.append("fair NOTHING rate off: ").append(nothing).append("/400 ");
            }
            c.wheelLuck = 0;
            for (int i = 0; i < 300; i++) {
                com.punishfx.Games.Game g = com.punishfx.Games.build(com.punishfx.Games.Kind.CASE, 2, "T", null, 1, -1, 1, 0, i * 17L);
                g.start = 1000;
                if (g.under(g.endAt() + 1) != g.win) { bad.append("case lands wrong "); break; }
            }
            // real CS odds: blue items should win ~80% of the time
            c.caseOdds = 0; c.caseRarity = 0; c.caseGold = 1; c.caseSafe = 1;
            int[] per = new int[5];
            for (int i = 0; i < 4000; i++)
                per[com.punishfx.Games.build(com.punishfx.Games.Kind.CASE, 4, "T", null, 1, -1, 1, 0, 99L + i).winner().rarity]++;
            double blue = per[0] / 4000.0, gold = per[4] / 4000.0;
            if (blue < 0.74 || blue > 0.86 || gold > 0.012) bad.append("CS odds off: blue ").append(blue).append(" gold ").append(gold).append(" ");
            return bad.length() == 0 ? "ok " + java.util.Arrays.toString(per) : bad.toString();
        });
        check(maths.startsWith("ok"), "wheel/case always land on the chosen slot; rigged never gives NOTHING; CS odds are right (" + maths + ")");

        // ---- a real spin
        ctx.runOnClient(mc -> {
            Config c = Config.get();
            c.wheelSlots = 2; c.wheelNothing = 1; c.wheelAgain = 0; c.wheelDouble = 1; c.wheelJackpot = 0;
            c.wheelLuck = 1; c.wheelSpin = 0; c.wheelColors = 1; c.wheelRim = 3; c.wheelLabels = 2; c.wheelBg = 1;
            c.wheelScale = 1.0; c.wheelX = 0.5; c.wheelY = 0.55;
        });
        ctx.runOnClient(mc -> PunishManager.force("spin_wheel", 1));
        ctx.waitTicks(30);
        shot(ctx, "fx_wheel_spinning_casino");
        boolean showing = ctx.computeOnClient(mc -> com.punishfx.Games.showing() && com.punishfx.Games.current().spinning(System.currentTimeMillis()));
        check(showing, "the wheel shows up and spins");
        ctx.waitTicks(45);
        String result = ctx.computeOnClient(mc -> {
            com.punishfx.Games.Game g = com.punishfx.Games.current();
            if (g == null || !g.resolved) return "not finished";
            List<String> names = new ArrayList<>();
            for (PunishManager.Active a : PunishManager.active()) names.add(a.p.id);
            return g.winner().id + " -> " + names;
        });
        shot(ctx, "fx_wheel_result");
        boolean applied = !result.equals("not finished") && result.contains("-> [") && !result.endsWith("[]");
        check(applied, "the wheel's result really runs (" + result + ")");
        ctx.waitTicks(60);
        check(ctx.computeOnClient(mc -> !com.punishfx.Games.showing()), "the wheel goes away after showing the result");
        reset(ctx);

        // another look: rainbow, neon rim, icons only, bigger
        ctx.runOnClient(mc -> {
            Config c = Config.get();
            c.wheelColors = 2; c.wheelRim = 4; c.wheelLabels = 1; c.wheelSlots = 5; c.wheelPointer = 3; c.wheelScale = 1.25;
            c.wheelTitle = "{name} SPINS!"; c.wheelSpin = 1; c.wheelBg = 2;
        });
        ctx.runOnClient(mc -> PunishManager.force("spin_wheel", 2));
        ctx.waitTicks(28);
        shot(ctx, "fx_wheel_rainbow_icons");
        reset(ctx);
        ctx.runOnClient(mc -> {
            Config c = Config.get();
            c.wheelColors = 5; c.wheelRim = 1; c.wheelLabels = 0; c.wheelSlots = 3; c.wheelScale = 1.0; c.wheelTitle = "SPIN THE WHEEL"; c.wheelBg = 0;
        });
        ctx.runOnClient(mc -> PunishManager.force("spin_wheel", 0));
        ctx.waitTicks(28);
        shot(ctx, "fx_wheel_gameshow_names");
        reset(ctx);

        // ---- case opening
        ctx.runOnClient(mc -> {
            Config c = Config.get();
            c.caseOpen = 0; c.caseOdds = 2; c.caseGold = 1; c.caseSafe = 1; c.caseStyle = 0; c.caseCard = 0; c.caseBg = 1;
        });
        ctx.runOnClient(mc -> PunishManager.force("case_open", 3));
        ctx.waitTicks(30);
        shot(ctx, "fx_case_opening");
        ctx.waitTicks(45);
        String unboxed = ctx.computeOnClient(mc -> {
            com.punishfx.Games.Game g = com.punishfx.Games.current();
            return g == null ? "gone" : g.resolved ? g.winner().name + " (" + com.punishfx.Games.RARITY_NAMES[g.winner().rarity] + ") " + g.resultSub : "still opening";
        });
        shot(ctx, "fx_case_result");
        check(!unboxed.equals("gone") && !unboxed.equals("still opening"), "the case opens and unboxes something (" + unboxed + ")");
        reset(ctx);
        ctx.runOnClient(mc -> { Config c = Config.get(); c.caseCard = 1; c.caseColors = 2; c.caseShowOdds = false; c.caseScale = 1.3; });
        ctx.runOnClient(mc -> PunishManager.force("case_open", 0));
        ctx.waitTicks(30);
        shot(ctx, "fx_case_flat_rainbow");
        reset(ctx);

        // ---- "every donation" spins the wheel
        ctx.runOnClient(mc -> { Config c = Config.get(); c.wheelUse = 1; c.cooldownSeconds = 0; c.caseCard = 0; c.caseColors = 0; c.caseShowOdds = true; c.caseScale = 1.0; });
        ctx.runOnClient(mc -> Detector.onMessage("Notch paid you $300,000.", false));
        ctx.waitTicks(4);
        boolean wheelFromDonation = ctx.computeOnClient(mc -> com.punishfx.Games.busy() && PunishManager.active().isEmpty());
        check(wheelFromDonation, "'Every donation' makes a real donation spin the wheel");
        reset(ctx);
        ctx.runOnClient(mc -> { Config c = Config.get(); c.wheelUse = 0; c.wheelColors = 1; c.wheelSlots = 2; c.wheelRim = 3; c.wheelLabels = 2; c.wheelSpin = 2; });

        // ---- the move editor shows the wheel full size
        ctx.setScreen(() -> new MoveScreen(null, com.punishfx.GameRender.WHEEL_PIECE));
        ctx.waitTicks(30);
        shot(ctx, "ui_move_wheel");
        ctx.setScreen(() -> new MoveScreen(null, com.punishfx.GameRender.CASE_PIECE));
        ctx.waitTicks(30);
        shot(ctx, "ui_move_case");
        ctx.setScreen(() -> null);
        ctx.waitTicks(3);
    }

    // ------------------------------------------------------------------ 1.5: several donations at once

    private void overlapTests(ClientGameTestContext ctx) {
        reset(ctx);
        ctx.runOnClient(mc -> {
            Config c = Config.get();
            c.cooldownSeconds = 0;
            c.overlapWindow = 0;
            c.queueGap = 0;
            c.tiers.get(0).pool = new ArrayList<>(List.of("jitter"));
            c.tiers.get(0).count = 1;
            c.tiers.get(0).seconds = 2;
            c.tiers.get(1).pool = new ArrayList<>(List.of("fog"));
            c.tiers.get(1).count = 1;
            c.tiers.get(1).seconds = 4;
        });
        String[] names = PunishManager.OVERLAP_NAMES;
        for (int mode = 0; mode < names.length; mode++) {
            final int m = mode;
            reset(ctx);
            ctx.runOnClient(mc -> Config.get().overlapMode = m);
            // A ($60k, level 1: jitter) then B ($300k, level 2: fog) straight after
            String r = ctx.computeOnClient(mc -> {
                PunishManager.hit(200_000, "Alice", 0, false);
                long endA = PunishManager.active().isEmpty() ? 0 : PunishManager.active().get(0).end;
                PunishManager.hit(m == 6 ? 200_000 : 300_000, "Bob", m == 6 ? 0 : 1, false);
                StringBuilder sb = new StringBuilder();
                for (PunishManager.Active a : PunishManager.active())
                    sb.append(a.p.id).append(":").append(Math.round((a.end - a.start) / 100.0) / 10.0).append("s ");
                sb.append("| queue=").append(PunishManager.queue().size()).append(" combo=").append(PunishManager.comboCount)
                        .append(" endA+=").append(PunishManager.active().isEmpty() ? 0 : (PunishManager.active().get(0).end - endA) / 1000)
                        .append(" | ").append(PunishManager.donorSub);
                return sb.toString();
            });
            boolean ok = switch (mode) {
                case 0 -> r.contains("jitter") && r.contains("fog");
                case 1 -> r.contains("jitter") && !r.contains("fog") && r.contains("queue=1");
                case 2 -> r.contains("jitter") && r.contains("fog:6.0s") && r.contains("combo=2");
                case 3 -> !r.contains("jitter") && r.contains("fog");
                case 4 -> r.contains("jitter") && !r.contains("fog") && r.contains("endA+=4");
                case 5 -> !r.contains("jitter") && r.contains("fog");
                case 6 -> !r.contains("jitter") && r.contains("fog") && r.contains("TEAM-UP");
                case 7 -> r.contains("jitter") && !r.contains("fog") && r.contains("just the banner");
                default -> r.contains("Surprise");
            };
            check(ok, "several at once - " + names[mode] + ": " + r);
            if (mode == 1) {
                // the queued one plays after the first finishes
                // punishments run on real time and CI frames are slow, so watch for it rather than waiting a fixed time
                String later = "never started";
                for (int i = 0; i < 120; i++) {
                    ctx.waitTicks(2);
                    String now = ctx.computeOnClient(mc -> {
                        StringBuilder sb = new StringBuilder();
                        for (PunishManager.Active a : PunishManager.active()) sb.append(a.p.id).append(" ");
                        return sb + "| queue=" + PunishManager.queue().size();
                    });
                    if (now.contains("fog")) {
                        later = now;
                        break;
                    }
                }
                check(later.contains("fog") && later.contains("queue=0"), "queue: the next donation plays when the first ends (" + later + ")");
                ctx.runOnClient(mc -> { PunishManager.hit(200_000, "Alice", 0, false); PunishManager.hit(300_000, "Bob", 1, false); PunishManager.hit(300_000, "Carl", 1, false); });
                ctx.waitTicks(3);
                shot(ctx, "fx_queue_up_next");
            }
            if (mode == 2) {
                ctx.runOnClient(mc -> PunishManager.hit(300_000, "Carl", 1, false));
                ctx.waitTicks(3);
                shot(ctx, "fx_combo_x3");
            }
            if (mode == 3) {
                String small = ctx.computeOnClient(mc -> {
                    PunishManager.hit(100_000, "Dan", 0, false);
                    return PunishManager.donorSub + " | active " + PunishManager.active().size();
                });
                check(small.contains("smaller") && small.endsWith("active 1"), "biggest wins: a smaller one doesn't punish (" + small + ")");
            }
        }
        ctx.runOnClient(mc -> { Config.get().overlapMode = 0; });
        reset(ctx);
        ctx.runOnClient(mc -> Config.reset());
    }

    // ------------------------------------------------------------------ 1.6: tiny window, blurry, fisheye

    private static double meanBright(BufferedImage im, double x0, double x1, double y0, double y1) {
        int w = im.getWidth(), h = im.getHeight();
        long sum = 0, n = 0;
        for (int y = (int) (h * y0); y < (int) (h * y1); y += 3)
            for (int x = (int) (w * x0); x < (int) (w * x1); x += 3, n++) {
                int c = im.getRGB(x, y);
                sum += ((c >> 16) & 255) + ((c >> 8) & 255) + (c & 255);
            }
        return n == 0 ? 0 : sum / (3.0 * n);
    }

    private void screenFxTests(ClientGameTestContext ctx, TestServerContext server) {
        reset(ctx);
        server.runCommand("tp @a 0.5 101 0.5 0 35");
        ctx.waitTicks(15);
        shot(ctx, "fx_05_floor_baseline");

        // ---- tiny window: bright game inside the box, dark everywhere else
        ctx.runOnClient(mc -> { Config c = Config.get(); c.windowSize = 2; c.windowSpeed = 1; });
        ctx.runOnClient(mc -> PunishManager.force("window", 0));
        ctx.waitTicks(10);
        double[] rect = ctx.computeOnClient(mc -> com.punishfx.ScreenFx.windowRect(mc, 2, 1));
        shot(ctx, "fx_tiny_window");
        try {
            BufferedImage im = ImageIO.read(shots.resolve("fx_tiny_window.png").toFile());
            double x = rect[0], s = rect[2];
            double top = 1 - (rect[1] + s);           // content top, measured from the top of the picture
            double inside = meanBright(im, x + s * 0.2, x + s * 0.8, top + s * 0.2, top + s * 0.8);
            // the widest empty strip beside the window
            double outside = x > 1 - (x + s) ? meanBright(im, 0.02, Math.max(0.03, x - 0.03), 0.1, 0.75)
                    : meanBright(im, Math.min(0.97, x + s + 0.03), 0.98, 0.1, 0.75);
            check(inside > outside + 60, "tiny window: the game shrinks into a box (inside " + fmt(inside) + ", outside " + fmt(outside)
                    + ", box at " + fmt(x) + "," + fmt(top) + " size " + fmt(s) + ")");
        } catch (Exception e) {
            check(false, "tiny window screenshot check: " + e);
        }
        ctx.waitTicks(40);
        double[] rect2 = ctx.computeOnClient(mc -> com.punishfx.ScreenFx.windowRect(mc, 2, 1));
        check(Math.abs(rect2[0] - rect[0]) + Math.abs(rect2[1] - rect[1]) > 0.01, "tiny window: the box keeps moving ("
                + fmt(rect[0]) + "," + fmt(rect[1]) + " -> " + fmt(rect2[0]) + "," + fmt(rect2[1]) + ")");
        shot(ctx, "fx_tiny_window_later");
        reset(ctx);

        // ---- blurry
        ctx.runOnClient(mc -> Config.get().blurLevel = 2);
        ctx.runOnClient(mc -> PunishManager.force("blur", 0));
        ctx.waitTicks(10);
        shot(ctx, "fx_blurry");
        try {
            double base = edges(shots.resolve("fx_05_floor_baseline.png")), blur = edges(shots.resolve("fx_blurry.png"));
            check(blur < base * 0.5, "blurry really blurs (detail " + fmt(base) + " -> " + fmt(blur) + ")");
        } catch (Exception e) {
            check(false, "blur screenshot check: " + e);
        }
        reset(ctx);

        // ---- fisheye
        ctx.runOnClient(mc -> Config.get().fisheyeLevel = 3);
        ctx.runOnClient(mc -> PunishManager.force("fisheye", 0));
        ctx.waitTicks(10);
        shot(ctx, "fx_fisheye");
        try {
            BufferedImage a = ImageIO.read(shots.resolve("fx_05_floor_baseline.png").toFile());
            BufferedImage b = ImageIO.read(shots.resolve("fx_fisheye.png").toFile());
            // the bulge darkens the corners and changes the picture
            double ca = meanBright(a, 0.0, 0.08, 0.0, 0.1), cb = meanBright(b, 0.0, 0.08, 0.0, 0.1);
            check(cb < ca - 25, "fisheye bends the view and darkens the edges (corner " + fmt(ca) + " -> " + fmt(cb) + ")");
        } catch (Exception e) {
            check(false, "fisheye screenshot check: " + e);
        }

        // ---- everything at once: they stack in order
        ctx.runOnClient(mc -> { Config c = Config.get(); c.pixelRes = 4; c.pixelPalette = 0; c.windowSize = 1; });
        ctx.runOnClient(mc -> { PunishManager.force("blur", 0); PunishManager.force("pixelated", 0); PunishManager.force("window", 0); PunishManager.force("mirror", 0); });
        ctx.waitTicks(10);
        String stack = ctx.computeOnClient(mc -> String.valueOf(com.punishfx.ScreenFx.activeIds()));
        check(stack.equals("[punishfx:fx/blur_2, punishfx:fx/fisheye_3, punishfx:fx/m1_r5_p0, punishfx:fx/window_1_1]"),
                "screen effects stack (blur, fisheye, mirror+pixel, window): " + stack);
        shot(ctx, "fx_everything_stacked");
        reset(ctx);
        check(ctx.computeOnClient(mc -> com.punishfx.ScreenFx.activeIds().isEmpty()), "all screen effects removed after");
        ctx.runOnClient(mc -> { Config c = Config.get(); c.pixelRes = 3; c.blurLevel = 1; c.fisheyeLevel = 1; c.windowSize = 1; });
        server.runCommand("tp @a 0.5 101 0.5 0 10");
        ctx.waitTicks(5);
    }

    // ------------------------------------------------------------------ 1.6: sounds

    private void soundTests(ClientGameTestContext ctx) {
        reset(ctx);
        String missing = ctx.computeOnClient(mc -> {
            StringBuilder sb = new StringBuilder();
            for (com.punishfx.Punishes.P p : com.punishfx.Punishes.all())
                if (com.punishfx.PunishSounds.desc(p.id).isEmpty()) sb.append(p.id).append(' ');
            return sb.toString();
        });
        check(missing.isEmpty(), "every punishment has a sound (" + (missing.isEmpty() ? "all " + 0 : missing) + ")");
        String[] ids = {"frozen", "drunk", "mirror", "pixelated", "window", "blur", "fisheye", "language", "jitter"};
        StringBuilder quiet = new StringBuilder();
        for (String id : ids) {
            reset(ctx);
            int before = ctx.computeOnClient(mc -> PunishManager.soundsPlayed);
            ctx.runOnClient(mc -> PunishManager.force(id, 0));
            ctx.waitTicks(2);
            int after = ctx.computeOnClient(mc -> PunishManager.soundsPlayed);
            if (after <= before) quiet.append(id).append(' ');
        }
        check(quiet.length() == 0, "punishments play their sound when they start (silent: " + quiet + ")");
        // repeating sound while it runs (jitter beats every 8 ticks)
        int r0 = ctx.computeOnClient(mc -> PunishManager.soundsPlayed);
        ctx.waitTicks(40);
        int r1 = ctx.computeOnClient(mc -> PunishManager.soundsPlayed);
        check(r1 - r0 >= 3, "sounds repeat while a punishment runs (" + (r1 - r0) + " in 2s)");
        // muting one
        reset(ctx);
        ctx.runOnClient(mc -> Config.get().mutedSounds.add("frozen"));
        int m0 = ctx.computeOnClient(mc -> PunishManager.soundsPlayed);
        ctx.runOnClient(mc -> PunishManager.force("frozen", 0));
        ctx.waitTicks(45);
        int m1 = ctx.computeOnClient(mc -> PunishManager.soundsPlayed);
        check(m1 == m0, "a muted punishment stays quiet (" + (m1 - m0) + " sounds)");
        reset(ctx);
        ctx.runOnClient(mc -> { Config.get().mutedSounds.clear(); Config.get().loopSounds = false; });
        ctx.runOnClient(mc -> PunishManager.force("jitter", 0));
        ctx.waitTicks(3);
        int l0 = ctx.computeOnClient(mc -> PunishManager.soundsPlayed);
        ctx.waitTicks(40);
        int l1 = ctx.computeOnClient(mc -> PunishManager.soundsPlayed);
        check(l1 == l0, "'Sounds while it runs' off: no repeating sounds (" + (l1 - l0) + ")");
        reset(ctx);
        ctx.runOnClient(mc -> Config.get().loopSounds = true);
    }

    // ------------------------------------------------------------------ 1.6: presets

    private void presetTests(ClientGameTestContext ctx) {
        reset(ctx);
        String r = ctx.computeOnClient(mc -> {
            StringBuilder bad = new StringBuilder();
            Config.reset();
            Config.get().looseMatching = true;           // a detection setting: presets must never touch it
            Config.get().tiers.get(0).minAmount = 12_345;
            com.punishfx.Presets.Builtin chaos = com.punishfx.Presets.BUILTIN.get(1);
            com.punishfx.Presets.apply(chaos);
            Config c = Config.get();
            if (c.overlapMode != 2 || c.lengthScale != 1.5 || c.wheelUse != 3) bad.append("chaos not applied ");
            if (!c.looseMatching) bad.append("chaos changed detection ");
            if (c.tiers.get(0).minAmount != 12_345) bad.append("chaos changed amounts ");
            com.punishfx.Presets.apply(com.punishfx.Presets.BUILTIN.get(0));
            if (Config.get().overlapMode != 1 || Config.get().lengthScale != 0.75) bad.append("chill not applied ");
            for (Config.Tier t : Config.get().tiers) if (t.pool.contains("frozen") || t.pool.isEmpty()) bad.append("chill kept harsh ones ");
            // save, change, load back
            Config.get().topTitle = "MY FANS";
            com.punishfx.Presets.saveCurrent("Test setup");
            Config.get().topTitle = "something else";
            Config.get().lengthScale = 3.0;
            for (com.punishfx.Presets.Saved s : com.punishfx.Presets.saved())
                if (s.name.equals("Test setup")) com.punishfx.Presets.apply(s.data, s.name);
            if (!"MY FANS".equals(Config.get().topTitle) || Config.get().lengthScale != 0.75) bad.append("saved preset didn't restore ");
            // share code round trip
            String code = com.punishfx.Presets.exportCode();
            String name = com.punishfx.Presets.importCode(code);
            if (name == null) bad.append("code didn't import ");
            if (com.punishfx.Presets.importCode("hello") != null) bad.append("junk code accepted ");
            // starting setup keeps your amounts
            com.punishfx.Presets.apply(com.punishfx.Presets.BUILTIN.get(3));
            if (Config.get().tiers.get(0).minAmount != 12_345 || Config.get().lengthScale != 1.0 || !Config.get().looseMatching)
                bad.append("starting setup wrong ");
            return bad.length() == 0 ? "ok, code " + code.length() + " chars, imported as " + name : bad.toString();
        });
        check(r.startsWith("ok"), "presets: ready-made, saved and shared ones work and never touch detection (" + r + ")");
        // banner only: shows the donor, no punishment
        ctx.runOnClient(mc -> com.punishfx.Presets.apply(com.punishfx.Presets.BUILTIN.get(2)));
        ctx.runOnClient(mc -> PunishManager.hit(5_000_000, "Grian", 3, false));
        ctx.waitTicks(3);
        boolean bannerOnly = ctx.computeOnClient(mc -> PunishManager.active().isEmpty() && !com.punishfx.Games.busy() && PunishManager.donorShowing());
        check(bannerOnly, "'Banner only' preset: shows who donated, doesn't punish");
        shot(ctx, "fx_banner_only");
        ctx.runOnClient(mc -> { Config.reset(); for (com.punishfx.Presets.Saved s : new ArrayList<>(com.punishfx.Presets.saved())) com.punishfx.Presets.delete(s); });
        ctx.runOnClient(mc -> com.punishfx.Presets.saveCurrent("Recording"));
        ctx.runOnClient(mc -> com.punishfx.Presets.saveCurrent("Saturday chaos"));
        reset(ctx);
    }

    // ------------------------------------------------------------------ 1.7: giveaways

    private boolean waitForDraw(ClientGameTestContext ctx) {
        for (int i = 0; i < 200; i++) {
            ctx.waitTicks(3);
            if (ctx.computeOnClient(mc -> com.punishfx.Games.current() != null && com.punishfx.Games.current().resolved)) return true;
        }
        return false;
    }

    private void giveawayTests(ClientGameTestContext ctx) {
        reset(ctx);
        String odds = ctx.computeOnClient(mc -> {
            java.util.Random r = new java.util.Random(5);
            int b = 0;
            for (int i = 0; i < 4000; i++) if (com.punishfx.Giveaway.pick(List.of(1, 3), r) == 1) b++;
            return String.valueOf(b / 4000.0);
        });
        double share = Double.parseDouble(odds);
        check(share > 0.72 && share < 0.78, "giveaway draw is fair: 3 tickets vs 1 wins " + odds + " of the time (should be 0.75)");

        ctx.runOnClient(mc -> {
            Config c = Config.get();
            c.gwPrice = 100_000; c.gwPrize = "$5M"; c.gwPrizeMode = 0; c.gwMoreTickets = false; c.gwPunish = false;
            c.gwMethod = 0; c.gwDrawTime = 0; c.gwHud = true; c.gwX = 1.0; c.gwY = 0.72; c.cooldownSeconds = 0;
            com.punishfx.Giveaway.cancel();
            com.punishfx.Giveaway.start();
        });
        ctx.runOnClient(mc -> Detector.onMessage("Bob paid you $100,000.", false));
        ctx.waitTicks(2);
        String sub = ctx.computeOnClient(mc -> PunishManager.donorSub);
        boolean noPunish = ctx.computeOnClient(mc -> PunishManager.active().isEmpty() && !com.punishfx.Games.busy());
        ctx.runOnClient(mc -> Detector.onMessage("Alex paid you $250,000.", false));
        ctx.runOnClient(mc -> Detector.onMessage("Cheapo paid you $60,000.", false));
        ctx.waitTicks(2);
        String who = ctx.computeOnClient(mc -> String.valueOf(com.punishfx.Giveaway.state().entrants.keySet()));
        check(who.equals("[bob, alex]") && sub.contains("entered the giveaway"),
                "giveaway: paying the entry price gets you in, less doesn't (" + who + ", \"" + sub + "\")");
        check(noPunish, "giveaway: entries don't set off punishments");
        reset(ctx);
        ctx.runOnClient(mc -> { Config.get().gwMoreTickets = true; Config.get().gwMaxTickets = 2; });
        ctx.runOnClient(mc -> Detector.onMessage("Steve paid you $350,000.", false));
        ctx.runOnClient(mc -> Detector.onMessage("Bob paid you $1,000,000.", false));
        ctx.waitTicks(2);
        String tk = ctx.computeOnClient(mc -> {
            StringBuilder sb = new StringBuilder();
            for (com.punishfx.Giveaway.Entrant e : com.punishfx.Giveaway.state().entrants.values()) sb.append(e.name).append('=').append(e.tickets).append(' ');
            return sb.toString().trim();
        });
        check(tk.equals("Bob=5 Alex=1 Steve=3"), "giveaway: paying more gives more tickets, up to the limit (" + tk + ")");
        // crash-safe: reload from disk
        int before = ctx.computeOnClient(mc -> com.punishfx.Giveaway.count());
        ctx.runOnClient(mc -> com.punishfx.Giveaway.load());
        int after = ctx.computeOnClient(mc -> com.punishfx.Giveaway.count());
        check(before == 3 && after == 3, "giveaway entries are saved (survive a restart): " + before + " -> " + after);
        reset(ctx);
        shot(ctx, "fx_giveaway_box");

        // ---- the real draw (wheel)
        ctx.runOnClient(mc -> com.punishfx.Giveaway.draw());
        ctx.waitTicks(30);
        shot(ctx, "fx_giveaway_wheel_spinning");
        boolean done = waitForDraw(ctx);
        shot(ctx, "fx_giveaway_wheel_winner");
        String result = ctx.computeOnClient(mc -> com.punishfx.Giveaway.lastWinner() + " | live=" + com.punishfx.Giveaway.live()
                + " | " + com.punishfx.Giveaway.payCommand() + " | punishing=" + !PunishManager.active().isEmpty());
        boolean winnerOk = result.startsWith("Bob |") || result.startsWith("Alex |") || result.startsWith("Steve |");
        check(done && winnerOk && result.contains("live=false") && result.contains("/pay ") && result.endsWith("5000000 | punishing=false"),
                "giveaway: the wheel draws a winner from the entrants and gives the pay command (" + result + ")");
        ctx.waitTicks(140);
        reset(ctx);

        // ---- the other draws, with a bigger crowd
        String[][] runs = {{"1", "case"}, {"2", "elimination"}, {"3", "raffle"}};
        for (String[] run : runs) {
            int kind = Integer.parseInt(run[0]);
            ctx.runOnClient(mc -> {
                List<String> names = List.of("Steve", "Alex", "Notch", "Jeb_", "Dinnerbone", "Grian", "Mumbo", "Kai", "Zuri", "Sunny", "Ari", "Efe",
                        "Tango", "Etho", "Bdubs", "Impulse");
                List<Integer> tickets = new ArrayList<>();
                for (int i = 0; i < names.size(); i++) tickets.add(1 + i % 3);
                com.punishfx.Games.Kind k = new com.punishfx.Games.Kind[]{com.punishfx.Games.Kind.WHEEL, com.punishfx.Games.Kind.CASE,
                        com.punishfx.Games.Kind.ELIMINATION, com.punishfx.Games.Kind.RAFFLE}[kind];
                com.punishfx.Games.Game g = com.punishfx.Games.buildGiveaway(k, names, tickets, 5, "$10M", 5, 42L + kind);
                g.preview = true;
                com.punishfx.Games.requestBuilt(g);
            });
            ctx.waitTicks(kind == 2 ? 45 : 30);
            shot(ctx, "fx_giveaway_" + run[1] + "_drawing");
            boolean ok = waitForDraw(ctx);
            ctx.waitTicks(4);
            String w = ctx.computeOnClient(mc -> com.punishfx.Games.current() == null ? "gone" : com.punishfx.Games.current().winner().name);
            shot(ctx, "fx_giveaway_" + run[1] + "_winner");
            check(ok && w.equals("Grian"), "giveaway " + run[1] + " lands on the drawn winner (" + w + ")");
            ctx.waitTicks(140);
            reset(ctx);
        }

        // ---- the pot as the prize
        String pot = ctx.computeOnClient(mc -> {
            Config.get().gwPrizeMode = 1;
            Config.get().gwMoreTickets = false;
            com.punishfx.Giveaway.start();
            com.punishfx.Giveaway.payment("Ann", 100_000);
            com.punishfx.Giveaway.payment("Ben", 200_000);
            String s = com.punishfx.Giveaway.prizeText() + " / " + (long) com.punishfx.Giveaway.prizeAmount();
            com.punishfx.Giveaway.cancel();
            return s + " / live after cancel=" + com.punishfx.Giveaway.live();
        });
        check(pot.equals("$300K (the pot) / 300000 / live after cancel=false"), "giveaway: 'the whole pot' prize adds up the entries; cancel works (" + pot + ")");
        ctx.runOnClient(mc -> { Config c = Config.get(); c.gwPrizeMode = 0; c.gwMoreTickets = false; });
        reset(ctx);
    }

    // ------------------------------------------------------------------ 1.8: more giveaway

    private boolean waitUntil(ClientGameTestContext ctx, java.util.function.Function<net.minecraft.client.Minecraft, Boolean> cond, int maxTicks) {
        for (int i = 0; i < maxTicks; i += 3) {
            ctx.waitTicks(3);
            if (ctx.computeOnClient(cond::apply)) return true;
        }
        return false;
    }

    private void giveaway2Tests(ClientGameTestContext ctx) {
        reset(ctx);
        // ---- reading "!join" out of chat, and not being fooled
        String parse = ctx.computeOnClient(mc -> {
            String[][] cases = {
                    {"<Steve> !join", "Steve"}, {"[VIP] Alex: !join", "Alex"}, {"Notch » !JOIN", "Notch"},
                    {"[Owner] [Lvl 5] Jeb_ ➤ !join", "Jeb_"}, {"[VIP] Mallory: <Bob> !join", null}, {"Steve: !join please", null},
                    {"Steve joined the game", null}, {"<Steve> hello", null}, {"<Bob> Steve: !join", null}};
            StringBuilder bad = new StringBuilder();
            for (String[] t : cases) {
                String got = com.punishfx.Giveaway.joinSender(t[0], "!join");
                if (!java.util.Objects.equals(got, t[1])) bad.append('"').append(t[0]).append("\" -> ").append(got).append("; ");
            }
            return bad.toString();
        });
        check(parse.isEmpty(), "free entry: reads '!join' from chat and can't be faked (" + (parse.isEmpty() ? "9 lines ok" : parse) + ")");

        // ---- free entry + rules
        String free = ctx.computeOnClient(mc -> {
            Config c = Config.get();
            c.gwEntryMode = 1; c.gwJoinWord = "!join"; c.gwBanned = "Griefer, Hacker"; c.gwMinEntries = 1; // index 1 = "2" c.gwNoRepeat = false;
            c.gwWinners = 1; c.gwPrize = "$1M"; c.cooldownSeconds = 0;
            com.punishfx.Giveaway.cancel();
            com.punishfx.Giveaway.start();
            com.punishfx.Giveaway.chat("<Steve> !join");
            String tooFew = com.punishfx.Giveaway.draw();
            com.punishfx.Giveaway.chat("[VIP] Alex: !join");
            com.punishfx.Giveaway.chat("<Steve> !join");
            com.punishfx.Giveaway.chat("<Griefer> !join");
            String banned = PunishManager.donorSub;
            Detector.onMessage("Payer paid you $200,000.", false); // chat-only giveaway: a payment is just a donation
            return com.punishfx.Giveaway.state().entrants.keySet() + " | " + tooFew + " | " + banned + " | punished=" + !PunishManager.active().isEmpty();
        });
        check(free.startsWith("[steve, alex] | Needs 1 more entry | is banned from giveaways | punished=true"),
                "giveaway: free entry by chat, the ban list and the minimum all work (" + free + ")");
        reset(ctx);

        // ---- 1st, 2nd and 3rd place
        ctx.runOnClient(mc -> {
            Config c = Config.get();
            c.gwEntryMode = 2; c.gwMinEntries = 0; c.gwWinners = 3; c.gwPrizeMode = 0; c.gwPrize = "$5M"; c.gwPrize2 = "$2M"; c.gwPrize3 = "$1M";
            c.gwMethod = 3; c.gwDrawTime = 0; c.gwBanned = "";
            for (String n : List.of("Notch", "Jeb_", "Grian", "Mumbo")) com.punishfx.Giveaway.chat("<" + n + "> !join");
            com.punishfx.Giveaway.draw();
        });
        ctx.waitTicks(40);
        shot(ctx, "fx_giveaway_3rd_place_drawing");
        boolean finished = waitUntil(ctx, mc -> !com.punishfx.Giveaway.live(), 1500);
        String places = ctx.computeOnClient(mc -> {
            com.punishfx.Giveaway.Record r = com.punishfx.Giveaway.last();
            StringBuilder sb = new StringBuilder();
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (com.punishfx.Giveaway.Place p : r.places) {
                sb.append(p.place).append('=').append(p.name).append(':').append(com.punishfx.Giveaway.payCommand(p)).append(' ');
                seen.add(p.name);
            }
            return sb.toString().trim() + " | distinct=" + seen.size() + " entrants=" + r.entrants;
        });
        check(finished && places.contains("distinct=3 entrants=6") && places.contains(" 5000000") && places.contains(" 2000000") && places.contains(" 1000000"),
                "giveaway: draws 3rd, 2nd and 1st place, three different winners, each with their prize (" + places + ")");
        shot(ctx, "fx_giveaway_1st_place_winner");
        ctx.waitTicks(60);

        // ---- reroll 1st place
        String before = ctx.computeOnClient(mc -> com.punishfx.Giveaway.last().places.get(0).name);
        String start = ctx.computeOnClient(mc -> String.valueOf(com.punishfx.Giveaway.reroll(1)));
        boolean rr = waitUntil(ctx, mc -> com.punishfx.Giveaway.last().places.get(0).rerolled.size() == 1, 600);
        String after = ctx.computeOnClient(mc -> {
            com.punishfx.Giveaway.Place p = com.punishfx.Giveaway.last().places.get(0);
            return p.name + " (was " + p.rerolled + ")";
        });
        boolean differs = !after.startsWith(before + " ");
        check(start.equals("null") && rr && differs && after.contains("[" + before + "]"), "giveaway: reroll draws someone new for 1st (" + before + " -> " + after + ")");
        shot(ctx, "fx_giveaway_reroll");
        ctx.waitTicks(150);

        // ---- can't win twice in a row
        String repeat = ctx.computeOnClient(mc -> {
            Config.get().gwNoRepeat = true;
            com.punishfx.Giveaway.start();
            String champ = com.punishfx.Giveaway.last().places.get(0).name;
            com.punishfx.Giveaway.chat("<" + champ + "> !join");
            boolean in = com.punishfx.Giveaway.count() == 1;
            String sub = PunishManager.donorSub;
            return champ + " in=" + in + " | " + sub;
        });
        check(repeat.contains("in=false") && repeat.contains("won last time"), "giveaway: last winner can't enter the next one (" + repeat + ")");

        // ---- the pot split between 3 places, and a cancelled draw can be drawn again
        String pot = ctx.computeOnClient(mc -> {
            Config c = Config.get();
            c.gwNoRepeat = false; c.gwPrizeMode = 1; c.gwEntryMode = 0; c.gwMoreTickets = false;
            com.punishfx.Giveaway.start();
            for (String n : List.of("Ann", "Ben", "Cat", "Dan")) com.punishfx.Giveaway.payment(n, 250_000);
            return (long) com.punishfx.Giveaway.prizeAmount(1) + "/" + (long) com.punishfx.Giveaway.prizeAmount(2) + "/" + (long) com.punishfx.Giveaway.prizeAmount(3);
        });
        check(pot.equals("500000/300000/200000"), "giveaway: the pot splits 50/30/20 between 1st, 2nd and 3rd (" + pot + ")");
        ctx.runOnClient(mc -> com.punishfx.Giveaway.draw());
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> PunishManager.cancelAll());
        ctx.waitTicks(5);
        boolean again = ctx.computeOnClient(mc -> com.punishfx.Giveaway.live() && !com.punishfx.Giveaway.drawing() && com.punishfx.Giveaway.count() == 4);
        check(again, "giveaway: cancelling a draw keeps the entries, ready to draw again");
        ctx.runOnClient(mc -> {
            com.punishfx.Giveaway.cancel();
            Config c = Config.get();
            c.gwWinners = 1; c.gwPrizeMode = 0; c.gwEntryMode = 0; c.gwMethod = 0; c.gwDrawTime = 1;
        });
        reset(ctx);
    }

    // ------------------------------------------------------------------ 1.8: goals

    private void goalTests(ClientGameTestContext ctx) {
        reset(ctx);
        String r1 = ctx.computeOnClient(mc -> {
            Config c = Config.get();
            c.goals = new ArrayList<>(List.of(new Config.Goal(1_000_000, "Shoutouts", ""), new Config.Goal(2_000_000, "Spin the wheel", "#wheel"),
                    new Config.Goal(4_000_000, "I jump in lava", ""), new Config.Goal(8_000_000, "Giveaway!", "#giveaway")));
            c.goalShow = true;
            com.punishfx.GoalsHud.place(0);
            com.punishfx.Goals.reset();
            com.punishfx.Goals.add(1_500_000);
            return com.punishfx.Goals.progress() + " celebrating=" + (com.punishfx.Goals.celebrating == null ? null : com.punishfx.Goals.celebrating.text)
                    + " next=" + com.punishfx.Goals.next().text;
        });
        check(r1.equals("1500000.0 celebrating=Shoutouts next=Spin the wheel"), "goals: the bar fills and celebrates a goal it passes (" + r1 + ")");
        ctx.waitTicks(10);
        shot(ctx, "fx_goal_reached_top_bar");
        ctx.runOnClient(mc -> Detector.onMessage("Zed paid you $600,000.", false)); // real donations fill it too -> 2.1M: the wheel goal
        ctx.waitTicks(3);
        String r2 = ctx.computeOnClient(mc -> com.punishfx.Goals.progress() + " wheel=" + com.punishfx.Games.busy());
        check(r2.equals("2100000.0 wheel=true"), "goals: donations fill the bar, and a goal can spin the wheel (" + r2 + ")");
        ctx.waitTicks(40);
        shot(ctx, "fx_goal_wheel");
        reset(ctx);
        ctx.runOnClient(mc -> com.punishfx.Goals.celebrating = null);
        ctx.runOnClient(mc -> com.punishfx.GoalsHud.place(1));
        ctx.waitTicks(3);
        shot(ctx, "fx_goal_bar_bottom");
        ctx.runOnClient(mc -> com.punishfx.GoalsHud.place(2));
        ctx.waitTicks(3);
        shot(ctx, "fx_goal_bar_left");
        ctx.runOnClient(mc -> com.punishfx.GoalsHud.place(3));
        ctx.waitTicks(3);
        shot(ctx, "fx_goal_bar_right");
        String r3 = ctx.computeOnClient(mc -> {
            com.punishfx.Goals.add(6_000_000); // passes 4M and 8M: the giveaway goal opens one
            boolean gw = com.punishfx.Giveaway.live();
            com.punishfx.Giveaway.cancel();
            com.punishfx.Goals.reset();
            return "giveaway=" + gw + " after reset=" + com.punishfx.Goals.progress();
        });
        check(r3.equals("giveaway=true after reset=0.0"), "goals: a goal can open a giveaway; reset empties the bar (" + r3 + ")");
        reset(ctx);
        ctx.runOnClient(mc -> { com.punishfx.Goals.celebrating = null; com.punishfx.GoalsHud.place(0); com.punishfx.Goals.add(3_000_000); });
    }

    // ------------------------------------------------------------------ 1.9: picks, simpler presets, folding sections

    private void simplerTests(ClientGameTestContext ctx) {
        reset(ctx);
        String picks = ctx.computeOnClient(mc -> {
            Config c = Config.get();
            c.wheelContents = 2; c.wheelPicks = new ArrayList<>(List.of("frozen", "drunk"));
            c.caseContents = 2; c.casePicks = new ArrayList<>(List.of("mirror", "blur", "fisheye"));
            StringBuilder bad = new StringBuilder();
            java.util.Set<String> seenW = new java.util.TreeSet<>(), seenC = new java.util.TreeSet<>();
            for (int i = 0; i < 60; i++) {
                com.punishfx.Games.Game w = com.punishfx.Games.build(com.punishfx.Games.Kind.WHEEL, 2, "T", null, 1, -1, 1, 0, i);
                for (com.punishfx.Games.Entry e : w.entries) if (!e.special()) { seenW.add(e.id); if (!c.wheelPicks.contains(e.id)) bad.append("wheel:").append(e.id).append(' '); }
                com.punishfx.Games.Game k = com.punishfx.Games.build(com.punishfx.Games.Kind.CASE, 2, "T", null, 1, -1, 1, 0, i);
                for (com.punishfx.Games.Entry e : k.reel) if (!e.special()) { seenC.add(e.id); if (!c.casePicks.contains(e.id)) bad.append("case:").append(e.id).append(' '); }
            }
            return bad.length() == 0 ? "ok wheel=" + seenW + " case=" + seenC : bad.toString();
        });
        check(picks.equals("ok wheel=[drunk, frozen] case=[blur, fisheye, mirror]"), "wheel and case only use what you ticked (" + picks + ")");

        ctx.setScreen(() -> new com.punishfx.ContentsScreen(null, com.punishfx.Games.Kind.WHEEL));
        ctx.waitTicks(4);
        shot(ctx, "ui_pick_wheel_contents");
        ctx.setScreen(() -> null);

        String pres = ctx.computeOnClient(mc -> {
            StringBuilder bad = new StringBuilder();
            for (com.punishfx.Presets.Saved s : new ArrayList<>(com.punishfx.Presets.saved())) com.punishfx.Presets.delete(s);
            String a = com.punishfx.Presets.duplicate(com.punishfx.Presets.BUILTIN.get(1)); // copy "Chaos stream"
            com.punishfx.Presets.Saved chaos = com.punishfx.Presets.saved().get(0);
            String b = com.punishfx.Presets.duplicate(chaos);
            com.punishfx.Presets.rename(com.punishfx.Presets.saved().get(1), "My Saturday");
            if (!a.equals("Chaos stream copy")) bad.append("copy name ").append(a).append(' ');
            if (!b.equals("Chaos stream copy copy")) bad.append("copy of copy ").append(b).append(' ');
            if (!com.punishfx.Presets.saved().get(1).name.equals("My Saturday")) bad.append("rename ");
            // the copy of Chaos really holds Chaos's settings
            com.punishfx.Presets.apply(chaos.data, chaos.name);
            if (Config.get().overlapMode != 2 || Config.get().lengthScale != 1.5) bad.append("copy didn't hold chaos ");
            // save changes into it
            Config.get().lengthScale = 2.0;
            com.punishfx.Presets.update(com.punishfx.Presets.lastSaved());
            Config.get().lengthScale = 1.0;
            com.punishfx.Presets.apply(chaos.data, chaos.name);
            if (Config.get().lengthScale != 2.0) bad.append("save changes ");
            return bad.length() == 0 ? "ok " + com.punishfx.Presets.saved().size() + " presets" : bad.toString();
        });
        check(pres.equals("ok 2 presets"), "presets: copy a ready-made one, copy your own, rename, save changes (" + pres + ")");
        ctx.runOnClient(mc -> Config.reset());

        // settings fold into sections: only one is open at a time
        ctx.setScreen(() -> PfxFrame.open(null, PfxFrame.Page.GENERAL));
        ctx.waitTicks(4);
        java.util.function.Function<String, Boolean> has = label -> ctx.computeOnClient(mc -> {
            for (var w : mc.screen.children())
                if (w instanceof net.minecraft.client.gui.components.AbstractWidget aw && aw.getMessage().getString().equals(label)) return true;
            return false;
        });
        boolean firstOpen = has.apply("Sounds") && !has.apply("Queue: show who's next");
        shot(ctx, "ui_sections_first_open");
        ctx.runOnClient(mc -> ((PfxFrame) mc.screen).openSection("Several donations at once"));
        ctx.waitTicks(3);
        boolean secondOpen = !has.apply("Sounds") && has.apply("Queue: show who's next");
        shot(ctx, "ui_sections_second_open");
        check(firstOpen && secondOpen, "settings fold into sections, one open at a time (" + firstOpen + ", " + secondOpen + ")");
        ctx.runOnClient(mc -> ((PfxFrame) mc.screen).openSection("Donations"));
        ctx.setScreen(() -> null);
        ctx.runOnClient(mc -> { com.punishfx.Presets.saveCurrent("Recording"); com.punishfx.Presets.duplicate(com.punishfx.Presets.BUILTIN.get(0)); });
        reset(ctx);
    }

    private void teachTests(ClientGameTestContext ctx) {
        reset(ctx);
        String r = ctx.computeOnClient(mc -> {
            StringBuilder out = new StringBuilder();
            Config.get().customPatterns.clear();
            Detector.clearCache();
            String a = "[Bank] 1,500 coins were deposited into your account by Steve";
            String b = "» Jeb_ tipped you 2.5k gems! Your new balance is 40,000 gems.";
            StringBuilder why = new StringBuilder();
            boolean before = Detector.detect(a, why) == null && Detector.detect(b, new StringBuilder()) == null;
            out.append("unknown before=").append(before).append(" | ");
            out.append(Detector.learn(a, false)).append(" | ");
            out.append(Detector.learn(b, false)).append(" | ");
            var x = Detector.detect("[Bank] 25,000 coins were deposited into your account by Alex", new StringBuilder());
            var y = Detector.detect("» Notch tipped you 900 gems! Your new balance is 1,234,567 gems.", new StringBuilder());
            var fake = Detector.detect("<Mallory> [Bank] 99,999 coins were deposited into your account by Mallory", new StringBuilder());
            out.append(x == null ? "x=null" : "x=" + (long) x.amount() + " " + x.player()).append(" ");
            out.append(y == null ? "y=null" : "y=" + (long) y.amount() + " " + y.player()).append(" ");
            out.append("fake=").append(fake == null ? "blocked" : "COUNTED");
            return out.toString();
        });
        check(r.startsWith("unknown before=true") && r.contains("x=25000 Alex") && r.contains("y=900 Notch") && r.endsWith("fake=blocked"),
                "teach it: learns your server's payment wording from one example, and players can't fake it (" + r + ")");
        // the teach screen lists recent lines with numbers
        ctx.runOnClient(mc -> {
            Detector.onMessage("[Bank] 777 coins were deposited into your account by Grian", false);
            Detector.onMessage("Steve paid you $5,000.", false);
            Detector.onMessage("Mumbo has 3 diamonds", false);
        });
        reset(ctx);
        ctx.setScreen(() -> new com.punishfx.TeachScreen(null));
        ctx.waitTicks(4);
        shot(ctx, "ui_teach_screen");
        ctx.setScreen(() -> null);
        ctx.runOnClient(mc -> { Config.get().customPatterns.clear(); Detector.clearCache(); Config.save(); });
        reset(ctx);
    }

    private void presetStreamTests(ClientGameTestContext ctx) {
        reset(ctx);
        String r = ctx.computeOnClient(mc -> {
            Config c = Config.get();
            c.streamHud = true;
            c.streamScale = 1.5;
            StreamSession.start();
            Detector.onMessage("Steve paid you $1,000,000.", false);
            int cancels0 = StreamSession.current().cancels;
            com.punishfx.Presets.apply(com.punishfx.Presets.BUILTIN.get(1)); // chaos
            com.punishfx.Presets.apply(com.punishfx.Presets.BUILTIN.get(3)); // starting setup
            Detector.onMessage("Alex paid you $2,000,000.", false);
            StreamSession.Record rec = StreamSession.current();
            String out = "live=" + StreamSession.live() + " total=" + (long) rec.total() + " hud=" + Config.get().streamHud
                    + " scale=" + Config.get().streamScale + " cancels+=" + (rec.cancels - cancels0) + " punishing=" + !PunishManager.active().isEmpty();
            StreamSession.stop();
            StreamSession.history().remove(StreamSession.history().size() - 1);
            return out;
        });
        check(r.equals("live=true total=3000000 hud=true scale=1.5 cancels+=0 punishing=true"),
                "switching presets keeps the stream going (counter, settings, totals, running punishments): " + r);
        ctx.runOnClient(mc -> Config.reset());
        reset(ctx);
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

        // "top donator only" style, and hidden amounts
        ctx.runOnClient(mc -> {
            Config c = Config.get();
            c.topFont = 0; c.topBackground = 1; c.topColor = 0; c.topItalic = false; c.topAmounts = 0;
            c.topStyle = 1; c.topLabelPos = 0; c.topCrown = true; c.topX = 1.0; c.topY = 0.3; c.topScale = 1.5;
            Config.save();
        });
        ctx.waitTicks(3);
        shot(ctx, "fx_topdonator_above");
        ctx.runOnClient(mc -> { Config c = Config.get(); c.topLabelPos = 1; c.topAmounts = 2; c.topX = 0.0; c.topY = 0.0; c.topScale = 1.0; });
        ctx.waitTicks(3);
        shot(ctx, "fx_topdonator_before_hidden");
        ctx.runOnClient(mc -> { Config c = Config.get(); c.topLabelPos = 2; c.topLabel = "BIGGEST SIMP"; c.topCrown = false; c.topAmounts = 0; c.topX = 0.5; c.topY = 1.0; });
        ctx.waitTicks(3);
        shot(ctx, "fx_topdonator_after_custom");
        ctx.runOnClient(mc -> { Config c = Config.get(); c.topStyle = 0; c.topAmounts = 2; c.topRows = 3; c.topX = 1.0; c.topY = 0.3; c.topLabel = "TOP DONATOR"; c.topCrown = true; });
        ctx.waitTicks(3);
        shot(ctx, "fx_topdonors_3_hidden_amounts");
        int[] widths = ctx.computeOnClient(mc -> {
            int hidden = com.punishfx.TopDonorsHud.bounds(mc.font, 480, 270, true)[2];
            Config.get().topAmounts = 0;
            int shown = com.punishfx.TopDonorsHud.bounds(mc.font, 480, 270, true)[2];
            return new int[]{hidden, shown};
        });
        check(widths[0] < widths[1], "hidden amounts: the board leaves the money out (" + widths[0] + " < " + widths[1] + " wide)");
        ctx.runOnClient(mc -> Config.get().topRows = 5);

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
        com.punishfx.GamesScreen.tab = 0;
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
        ctx.clickScreenButton("Wheel & Case");
        ctx.waitTicks(30);
        shot(ctx, "ui_" + size + "_4b_wheel");
        ctx.clickScreenButton("Case Opening");
        ctx.waitTicks(40);
        shot(ctx, "ui_" + size + "_4c_case");
        ctx.clickScreenButton("Spin the Wheel");
        ctx.waitTicks(2);
        ctx.clickScreenButton("Top Donors");
        ctx.waitTicks(4);
        shot(ctx, "ui_" + size + "_5_topdonors");
        ctx.clickScreenButton("Stream");
        ctx.waitTicks(4);
        shot(ctx, "ui_" + size + "_6_stream");
        ctx.clickScreenButton("Giveaway");
        ctx.waitTicks(4);
        shot(ctx, "ui_" + size + "_7_giveaway");
        ctx.clickScreenButton("Goals");
        ctx.waitTicks(4);
        shot(ctx, "ui_" + size + "_7b_goals");
        ctx.clickScreenButton("\u2605 Presets");
        ctx.waitTicks(4);
        shot(ctx, "ui_" + size + "_8_presets");
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
