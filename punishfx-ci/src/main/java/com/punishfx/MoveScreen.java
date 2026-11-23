package com.punishfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Drag the top donors board around the screen and scroll to resize it. */
public class MoveScreen extends Screen {
    private static final int SNAP = 6;
    private final Screen parent;
    private boolean dragging;
    private double grabX, grabY;
    private int corner = 0;

    public MoveScreen(Screen parent) {
        super(Component.literal("Move the top donors board"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Config c = Config.get();
        int bw = 58, gap = 4, n = 5;
        int total = n * bw + (n - 1) * gap;
        int x = this.width / 2 - total / 2, y = this.height - 28;
        addRenderableWidget(new FancyButton(x, y, bw, 20, Component.literal("Smaller"), b -> resize(-0.1)).accent(Ui.THEME2));
        addRenderableWidget(new FancyButton(x += bw + gap, y, bw, 20, Component.literal("Bigger"), b -> resize(0.1)).accent(Ui.THEME2));
        addRenderableWidget(new FancyButton(x += bw + gap, y, bw, 20, Component.literal("Corner"), b -> {
            corner = (corner + 1) % 4;
            c.topX = corner == 1 || corner == 2 ? 1.0 : 0.0;
            c.topY = corner >= 2 ? 1.0 : 0.0;
            Config.save();
        }));
        addRenderableWidget(new FancyButton(x += bw + gap, y, bw, 20, Component.literal("Reset"), b -> {
            c.topX = 1.0;
            c.topY = 0.3;
            c.topScale = 1.0;
            Config.save();
        }).accent(Ui.DIM));
        addRenderableWidget(new FancyButton(x += bw + gap, y, bw, 20, Component.literal("Done"), b -> onClose()).accent(Ui.GOOD));
    }

    /** Change size by {@code d}, keeping the board's centre where it is. */
    private void resize(double d) {
        Config c = Config.get();
        int[] b = TopDonorsHud.bounds(this.font, this.width, this.height, true);
        double cxp = b[0] + b[2] / 2.0, cyp = b[1] + b[3] / 2.0;
        c.topScale = Math.max(0.5, Math.min(4.0, Math.round((c.topScale + d) * 10) / 10.0));
        int[] nb = TopDonorsHud.bounds(this.font, this.width, this.height, true);
        place(cxp - nb[2] / 2.0, cyp - nb[3] / 2.0, nb[2], nb[3]);
        Config.save();
    }

    /** Store a top-left position as fractions of the free space, snapping to edges and the centre. */
    private void place(double x, double y, int w, int h) {
        Config c = Config.get();
        double freeX = Math.max(1, this.width - w), freeY = Math.max(1, this.height - h);
        x = Math.max(0, Math.min(freeX, x));
        y = Math.max(0, Math.min(freeY, y));
        if (x < SNAP) x = 0;
        if (x > freeX - SNAP) x = freeX;
        if (Math.abs(x + w / 2.0 - this.width / 2.0) < SNAP) x = this.width / 2.0 - w / 2.0;
        if (y < SNAP) y = 0;
        if (y > freeY - SNAP) y = freeY;
        c.topX = x / freeX;
        c.topY = y / freeY;
    }

    private boolean over(double mx, double my) {
        int[] b = TopDonorsHud.bounds(this.font, this.width, this.height, true);
        return mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (super.mouseClicked(e, doubleClick)) return true; // the buttons come first
        if (e.button() == 0 && over(e.x(), e.y())) {
            int[] b = TopDonorsHud.bounds(this.font, this.width, this.height, true);
            dragging = true;
            grabX = e.x() - b[0];
            grabY = e.y() - b[1];
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragging) {
            int[] b = TopDonorsHud.bounds(this.font, this.width, this.height, true);
            place(e.x() - grabX, e.y() - grabY, b[2], b[3]);
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (dragging) {
            dragging = false;
            Config.save();
            return true;
        }
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            resize(scrollY > 0 ? 0.1 : -0.1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // keep the game visible; a light grid helps line things up
        g.fill(0, 0, this.width, this.height, Ui.a(0.25f, 0x000000));
        for (int i = 1; i < 4; i++) {
            int gx = this.width * i / 4, gy = this.height * i / 4;
            g.fill(gx, 0, gx + 1, this.height, Ui.a(i == 2 ? 0.25f : 0.1f, 0xFFFFFF));
            g.fill(0, gy, this.width, gy + 1, Ui.a(i == 2 ? 0.25f : 0.1f, 0xFFFFFF));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        Config c = Config.get();
        int[] b = TopDonorsHud.bounds(this.font, this.width, this.height, true);
        TopDonorsHud.drawAt(g, this.font, b[0], b[1], (float) c.topScale, true);

        // dashed outline + size tag
        boolean hot = dragging || over(mouseX, mouseY);
        int col = Ui.a(hot ? 1f : 0.7f, hot ? 0xFFFFFF : Ui.ACCENT);
        for (int x = b[0]; x < b[0] + b[2]; x += 6) {
            g.fill(x, b[1] - 2, Math.min(x + 3, b[0] + b[2]), b[1] - 1, col);
            g.fill(x, b[1] + b[3] + 1, Math.min(x + 3, b[0] + b[2]), b[1] + b[3] + 2, col);
        }
        for (int y = b[1]; y < b[1] + b[3]; y += 6) {
            g.fill(b[0] - 2, y, b[0] - 1, Math.min(y + 3, b[1] + b[3]), col);
            g.fill(b[0] + b[2] + 1, y, b[0] + b[2] + 2, Math.min(y + 3, b[1] + b[3]), col);
        }
        String tag = Math.round(c.topScale * 100) + "%";
        int ty = b[1] > 14 ? b[1] - 13 : b[1] + b[3] + 4;
        g.fill(b[0], ty - 2, b[0] + this.font.width(tag) + 6, ty + 10, Ui.a(0.8f, 0x000000));
        g.drawString(this.font, tag, b[0] + 3, ty, Ui.a(1f, 0xFFFFFF), false);

        // instructions
        String help = "Drag the board to move it   ·   Scroll to resize   ·   Esc when done";
        int hw = this.font.width(help) + 16;
        int hx = this.width / 2 - hw / 2, hy = 8;
        g.fill(hx, hy, hx + hw, hy + 16, Ui.a(0.8f, 0x000000));
        g.fill(hx, hy, hx + hw, hy + 1, Ui.a(1f, Ui.ACCENT));
        g.drawString(this.font, help, hx + 8, hy + 5, Ui.a(1f, 0xFFFFFF), false);
        if (!c.topShow) {
            String warn = "The board is turned off - switch on \"Show on screen\" to see it while playing";
            g.drawString(this.font, warn, this.width / 2 - this.font.width(warn) / 2, hy + 20, Ui.a(1f, Ui.WARN), true);
        }
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
