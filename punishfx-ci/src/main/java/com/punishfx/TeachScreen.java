package com.punishfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * "Teach it your payment message": recent chat lines with a number in them, newest first. Click the one that
 * was a payment and Punish FX learns that wording.
 */
public class TeachScreen extends PfxFrame {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static String result = "";
    private static int resultColor = Ui.GOOD;
    private static long resultUntil = 0;
    private int seenVersion;

    public TeachScreen(Screen parent) {
        super(parent, Page.GENERAL, "Teach it", "click the message that was a payment");
    }

    @Override
    protected String sectionKey() {
        return "TEACH";
    }

    @Override
    protected void initPage() {
        seenVersion = Detector.recentVersion;
        List<Row> r = new ArrayList<>();
        r.add(new Buttons(() -> "Recent messages with numbers", null, "Ask someone to pay you (even $1), then click that message's Learn button",
                List.of(new Btn(() -> "← Back", Ui.DIM, 50, () -> Minecraft.getInstance().setScreen(PfxFrame.open(parent, Page.GENERAL))))));
        List<Detector.Seen> recent = Detector.recent();
        if (recent.isEmpty()) {
            r.add(new Info("Nothing yet", "Messages with a number in them show up here as they arrive", Ui.DIM));
        }
        for (Detector.Seen s : recent) {
            String when = TIME.format(Instant.ofEpochMilli(s.at).atZone(ZoneId.systemDefault()));
            boolean counted = s.verdict.startsWith("counted");
            r.add(new Buttons(() -> s.text, null, when + (s.actionBar ? "  (action bar)" : "") + "  ·  " + (s.verdict.isEmpty() ? "seen" : s.verdict),
                    List.of(new Btn(() -> counted ? "Counted ✔" : "Learn", counted ? Ui.DIM : Ui.GOOD, 62, () -> {
                        if (counted) {
                            say("That one already counts", Ui.GOOD);
                            return;
                        }
                        String msg = Detector.learn(s.text, s.actionBar);
                        say(msg, msg.startsWith("Learned") ? Ui.GOOD : Ui.WARN);
                    }))));
        }
        list(r, cy + 26, cy + ch);
    }

    private static void say(String s, int color) {
        result = s;
        resultColor = color;
        resultUntil = System.currentTimeMillis() + 6000;
    }

    @Override
    public void tick() {
        super.tick();
        if (Detector.recentVersion != seenVersion) rebuild(); // new messages appear by themselves
    }

    @Override
    protected void renderPage(GuiGraphics g, int mouseX, int mouseY) {
        if (System.currentTimeMillis() < resultUntil) {
            Ui.text(g, this.font, result, cx, cy + 2, resultColor, cw);
        } else {
            Ui.text(g, this.font, "1. Get someone to pay you (even $1).   2. Find that message below.   3. Press Learn.", cx, cy + 2, 0xE8E2E6, cw);
        }
        Ui.text(g, this.font, "Point at a message to see what Punish FX thought of it.", cx, cy + 13, 0x9A8F94, cw);
    }
}
