package com.punishfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The shared Punish FX window: a sidebar on the left (pages, master switch, test, stop, done)
 * and the current page on the right, so everything always lives in the same place.
 */
public abstract class PfxFrame extends Screen {
    public enum Page { GENERAL, LEVELS, PUNISHMENTS }

    protected final Screen parent;
    private final Page page;
    private final String heading, subheading;

    /** Panel, sidebar width, and the content box the page lays itself out in. */
    protected int px, py, pw, ph, sw;
    protected int cx, cy, cw, ch;
    /** Text shown in the footer; pages can replace it (e.g. with a hover description). */
    protected String footer = null;

    protected PfxFrame(Screen parent, Page page, String heading, String subheading) {
        super(Component.literal("Punish FX - " + heading));
        this.parent = parent;
        this.page = page;
        this.heading = heading;
        this.subheading = subheading;
    }

    public static Screen open(Screen parent, Page page) {
        return switch (page) {
            case GENERAL -> new PunishScreen(parent);
            case LEVELS -> new PunishTiersScreen(parent);
            case PUNISHMENTS -> new PunishPoolScreen(parent);
        };
    }

    @Override
    protected final void init() {
        pw = Math.min(this.width - 10, 640);
        ph = Math.min(this.height - 10, 340);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        sw = pw < 540 ? 92 : 112;
        cx = px + sw + 10;
        cw = px + pw - 10 - cx;
        cy = py + 34;
        ch = py + ph - 18 - cy;
        sidebar();
        initPage();
    }

    protected abstract void initPage();

    protected void renderPage(GuiGraphics g, int mouseX, int mouseY) {
    }

    protected void rebuild() {
        clearWidgets();
        init();
    }

    private void sidebar() {
        Config c = Config.get();
        int x = px + 7, w = sw - 14;
        int y = py + 34;
        addNav(x, y, w, "General", Page.GENERAL);
        addNav(x, y += 23, w, "Levels", Page.LEVELS);
        addNav(x, y += 23, w, "Punishments", Page.PUNISHMENTS);

        addRenderableWidget(new FancyButton(x, y + 31, w, 20, Component.literal("Enabled"), b -> {
            c.enabled = !c.enabled;
            if (!c.enabled) PunishManager.cancelAll();
            Config.save();
        }).toggle(() -> c.enabled));

        boolean inWorld = Minecraft.getInstance().player != null;
        int by = py + ph - 7;
        addRenderableWidget(new FancyButton(x, by -= 20, w, 20, Component.literal("Done"), b -> onClose()).accent(Ui.DIM));
        FancyButton stop = new FancyButton(x, by -= 23, w, 20, Component.literal("Stop all"), b -> PunishManager.cancelAll());
        stop.accent(Ui.ACCENT).active = inWorld;
        addRenderableWidget(stop);
        FancyButton test = new FancyButton(x, by -= 23, w, 20, Component.literal("Test random"), b -> {
            Config.save();
            Minecraft mc = Minecraft.getInstance();
            int l = (int) (Math.random() * c.tiers.size());
            mc.setScreen(null);
            PunishManager.hit(c.tiers.get(l).minAmount, mc.player == null ? "You" : mc.player.getGameProfile().name(), l, true);
        });
        test.accent(Ui.WARN).active = inWorld;
        addRenderableWidget(test);
    }

    private void addNav(int x, int y, int w, String label, Page target) {
        addRenderableWidget(new FancyButton(x, y, w, 20, Component.literal(label), b -> {
            if (target == page) return;
            Config.save();
            Minecraft.getInstance().setScreen(open(parent, target));
        }).accent(Ui.ACCENT).hot(() -> target == page));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        Ui.panel(g, this.width, this.height, px, py, pw, ph, System.currentTimeMillis());
        // sidebar strip + divider
        g.fillGradient(px + 1, py + 2, px + sw, py + ph - 1, Ui.a(1f, 0x12080C), Ui.a(1f, 0x0A0406));
        g.fill(px + sw, py + 2, px + sw + 1, py + ph - 1, Ui.a(0.5f, Ui.ACCENT));
        renderPageBackground(g, mouseX, mouseY);
    }

    /** Pages can draw cards behind their widgets here. */
    protected void renderPageBackground(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        // logo
        float ls = sw < 100 ? 1.25f : 1.5f;
        Ui.glowText(g, this.font, "PUNISH", px + 8, py + 9, Ui.ACCENT, ls);
        Ui.glowText(g, this.font, "FX", px + 8 + (int) (this.font.width("PUNISH ") * ls), py + 9, 0xFFFFFF, ls);
        g.drawString(this.font, "v1.2", px + 8, py + 23, Ui.a(1f, 0x6E6064), false);

        // page title + subtitle
        Ui.glowText(g, this.font, heading, cx, py + 9, 0xFFFFFF, 1.4f);
        int hx = cx + (int) (this.font.width(heading) * 1.4f) + 8;
        Ui.text(g, this.font, subheading, hx, py + 13, 0x8C7E82, px + pw - 10 - hx);
        g.fill(cx, py + 27, cx + cw, py + 28, Ui.a(0.3f, Ui.ACCENT));

        renderPage(g, mouseX, mouseY);

        // footer: hover help, or where the keys are
        String f = footer != null ? footer
                : "Open: " + PunishFxClient.settingsKeyName() + "   Cancel punishments: " + PunishFxClient.cancelKeyName() + "   Commands: /pfx";
        Ui.text(g, this.font, f, cx, py + ph - 13, footer != null ? Ui.TEXT : 0x7A6C70, cw);
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
