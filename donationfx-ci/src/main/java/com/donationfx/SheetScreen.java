package com.donationfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.fabricmc.loader.api.FabricLoader;

/** Google Sheet logging: paste the link, switch it on/off, send a test row. */
public class SheetScreen extends Screen {
    private static final int BG = 0xF00B0D13;
    private static final int LINE = 0xFF2A3142;
    private static final int GOLD = 0xFFF5B83D;
    private static final int TEXT = 0xFFE8EAF0;
    private static final int DIM = 0xFF8B93A7;
    private static final int GREEN = 0xFF3DDC84;
    private static final int RED = 0xFFFF5C5C;

    private final Screen parent;
    private int px, py, pw, ph;
    private EditBox url;

    public SheetScreen(Screen parent) {
        super(Component.literal("Donation FX Google Sheet"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Config c = Config.get();
        pw = Math.min(this.width - 12, 440);
        ph = 196;
        px = (this.width - pw) / 2;
        py = Math.max(4, (this.height - ph) / 2);
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(px + pw - 56, py + 6, 50, 20).build());

        url = new EditBox(this.font, px + 10, py + 58, pw - 20, 20, Component.literal("Sheet link"));
        url.setMaxLength(400);
        url.setValue(c.sheetUrl);
        url.setHint(Component.literal(SheetLog.BUILT_IN_URL.isEmpty() ? "Paste the web app link (ends in /exec)" : "Using the built-in sheet link"));
        url.setResponder(v -> c.sheetUrl = v.trim());
        addRenderableWidget(url);

        int bw = (pw - 20 - 3 * 4) / 4;
        int by = py + 90;
        addRenderableWidget(Button.builder(Component.literal(onLabel()), b -> {
            c.sheetLogging = !c.sheetLogging;
            b.setMessage(Component.literal(onLabel()));
            Config.save();
        }).bounds(px + 10, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Send test row"), b -> {
            Config.save();
            SheetLog.sendTest();
        }).bounds(px + 10 + (bw + 4), by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Paste link"), b -> {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clip != null) {
                url.setValue(clip.trim());
                Config.save();
            }
        }).bounds(px + 10 + 2 * (bw + 4), by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Backup file"), b -> Util.getPlatform().openPath(FabricLoader.getInstance().getConfigDir()))
                .bounds(px + 10 + 3 * (bw + 4), by, bw, 20).build());
    }

    private static String onLabel() {
        return "Logging: " + (Config.get().sheetLogging ? "ON" : "OFF");
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, 0x90000000);
        g.fill(px, py, px + pw, py + ph, BG);
        g.fill(px, py, px + pw, py + 2, GOLD);
        g.fill(px, py + ph - 1, px + pw, py + ph, LINE);
        g.fill(px, py, px + 1, py + ph, LINE);
        g.fill(px + pw - 1, py, px + pw, py + ph, LINE);
        g.fill(px + 8, py + 30, px + pw - 8, py + 31, LINE);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawString(this.font, "GOOGLE SHEET LOG", px + 10, py + 9, GOLD, true);
        g.drawString(this.font, "Every donation you get is written to the sheet:", px + 10, py + 19, DIM, false);
        g.drawString(this.font, "your name, who sent it, how much, the level, the server and time.", px + 10, py + 36, TEXT, false);
        g.drawString(this.font, "Google Sheet web app link:", px + 10, py + 47, DIM, false);
        int y = py + 120;
        String st = SheetLog.status;
        boolean ok = st.startsWith("Sent OK") || st.startsWith("Ready");
        g.drawString(this.font, "Status:", px + 10, y, DIM, false);
        for (var line : this.font.split(Component.literal(st), pw - 70)) {
            g.drawString(this.font, line, px + 56, y, ok ? GREEN : st.startsWith("Sending") ? GOLD : RED, false);
            y += 10;
        }
        y += 4;
        g.drawString(this.font, "Waiting to send: " + SheetLog.pending() + "    Sent this session: " + SheetLog.sent, px + 10, y, DIM, false);
        g.drawString(this.font, "A copy is also saved in config/donationfx-donations.csv.", px + 10, py + ph - 26, DIM, false);
        g.drawString(this.font, "Test rows go to the 'Test rows' tab, not the totals.", px + 10, py + ph - 15, DIM, false);
    }

    @Override
    public void onClose() {
        Config.save();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
