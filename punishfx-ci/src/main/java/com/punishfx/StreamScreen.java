package com.punishfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/** Stream page: start/end a stream, the live counter, and past recaps. */
public class StreamScreen extends PfxFrame {
    private static boolean confirmClear = false;
    private int statusH;

    public StreamScreen(Screen parent) {
        super(parent, Page.STREAM, "Stream", "start it when you go live, get a recap at the end");
    }

    /** Ends the stream and shows the recap (if that's switched on). Returns the record or null. */
    public static StreamSession.Record endStream(Screen back) {
        StreamSession.Record r = StreamSession.stop();
        if (r != null && Config.get().streamAutoRecap) Minecraft.getInstance().setScreen(new RecapScreen(back));
        return r;
    }

    @Override
    protected void initPage() {
        Config c = Config.get();
        statusH = ch >= 200 ? 46 : 36;
        list(List.of(
                new Header("Stream"),
                new Action(StreamSession.live() ? "End stream" : "Start stream",
                        StreamSession.live() ? "Stops counting and shows your recap" : "Counts every donation until you end it",
                        () -> StreamSession.live() ? "End stream" : "Go live", StreamSession.live() ? Ui.THEME : Ui.GOOD, () -> {
                    if (StreamSession.live()) {
                        Screen back = new StreamScreen(parent);
                        endStream(back);
                        if (!Config.get().streamAutoRecap) rebuild();
                    } else {
                        StreamSession.start();
                        rebuild();
                    }
                }),
                new Action("Last recap", StreamSession.history().isEmpty() ? "You haven't ended a stream yet" : "See how your streams went",
                        () -> StreamSession.history().isEmpty() ? "None yet" : "Open recap", Ui.THEME2, () -> {
                    if (!StreamSession.history().isEmpty()) Minecraft.getInstance().setScreen(new RecapScreen(this));
                }),
                new Header("Live counter"),
                new Toggle("Show the live counter", "A small LIVE box with your time and money so far", () -> c.streamHud, v -> c.streamHud = v),
                new Toggle("Timer", "How long you've been live", () -> c.streamHudTimer, v -> c.streamHudTimer = v),
                new Toggle("Money", "Total made this stream", () -> c.streamHudMoney, v -> c.streamHudMoney = v),
                new Toggle("Donation count", "How many donations so far", () -> c.streamHudCount, v -> c.streamHudCount = v),
                new Toggle("Show \"NOT LIVE\" when off", "A grey NOT LIVE box while no stream is running", () -> c.streamHudIdle, v -> c.streamHudIdle = v),
                new Action("Move & resize", "Opens the screen layout editor: drag, resize and right-click every item", () -> "Layout", Ui.GOOD,
                        () -> Minecraft.getInstance().setScreen(new LayoutScreen(this))),
                numbers("Size", "How big the live counter is",
                        new double[]{0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 2.5, 3.0}, v -> Math.round(v * 100) + "%", () -> c.streamScale, v -> c.streamScale = v),
                new Header("When a stream starts and ends"),
                new Toggle("Show the recap when you end", "Opens the recap screen as soon as you end a stream", () -> c.streamAutoRecap, v -> c.streamAutoRecap = v),
                new Toggle("Fresh \"This stream\" board", "The Top Donors board's \"This stream\" totals start from zero", () -> c.streamFreshBoard, v -> c.streamFreshBoard = v),
                new Action("Clear stream history", "Deletes every saved recap. Can't be undone.",
                        () -> confirmClear ? "Sure? Click" : "Clear", Ui.THEME, () -> {
                    if (!confirmClear) {
                        confirmClear = true;
                        return;
                    }
                    confirmClear = false;
                    StreamSession.clearHistory();
                    rebuild();
                })
        ), cy + statusH + 4, cy + ch);
    }

    @Override
    protected void renderPageBackground(GuiGraphics g, int mouseX, int mouseY) {
        boolean live = StreamSession.live();
        Ui.card(g, cx, cy, cw, statusH, Ui.tint(0x0C0A0E, 0.05f));
        g.fill(cx, cy, cx + 2, cy + statusH, Ui.a(1f, live ? 0xFF3B3B : 0x5E5256));
    }

    @Override
    protected void renderPage(GuiGraphics g, int mouseX, int mouseY) {
        StreamSession.Record r = StreamSession.current();
        int x = cx + 10, y = cy + (statusH - (statusH >= 46 ? 30 : 22)) / 2;
        if (r == null) {
            g.drawString(this.font, "Not live", x, y, Ui.a(1f, 0xFFFFFF), false);
            int n = StreamSession.history().size();
            String s = n == 0 ? "Press Go live (or type /pfx stream start) when your stream starts"
                    : n + (n == 1 ? " stream" : " streams") + " saved  ·  /pfx stream start to go live";
            Ui.text(g, this.font, s, x, y + 12, 0x9A8F94, cw - 20);
        } else {
            float pulse = 0.55f + 0.45f * (float) Math.sin(System.currentTimeMillis() / 280.0);
            g.fill(x, y + 1, x + 5, y + 6, Ui.a(pulse, 0xFF3B3B));
            g.drawString(this.font, "LIVE  " + StreamHud.clock(r.duration()), x + 9, y, Ui.a(1f, 0xFF7A7A), false);
            int n = r.donations.size(), d = r.donors().size();
            String s = Money.compact(r.total()) + " made  ·  " + n + (n == 1 ? " donation" : " donations")
                    + "  ·  " + d + (d == 1 ? " donor" : " donors");
            Ui.text(g, this.font, s, x, y + 12, 0xFFFFFF, cw - 20);
        }
        if (statusH >= 46) {
            Ui.text(g, this.font, "Also: /pfx stream stop to end it, /pfx recap to see the last one", x, y + 24, 0x6E6064, cw - 20);
        }
    }
}
