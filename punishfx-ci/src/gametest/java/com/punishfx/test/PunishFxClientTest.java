package com.punishfx.test;

import com.punishfx.Config;
import com.punishfx.ControlState;
import com.punishfx.Detector;
import com.punishfx.PfxFrame;
import com.punishfx.PunishManager;
import com.punishfx.DonorBoard;
import com.punishfx.MoveScreen;
import com.punishfx.Ui;
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
            detectorTest(ctx);
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
        ctx.runOnClient(mc -> mc.options.guiScale().set(2));
        ctx.getInput().resizeWindow(1280, 720); // GUI scale 2 -> 640x360
        ctx.waitTicks(5);
        pages(ctx, "640x360");
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
