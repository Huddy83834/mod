package com.punishfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The screen layout editor: every on-screen item at once. Drag to move, scroll to resize,
 * right-click for that item's settings in a dropdown.
 */
public class LayoutScreen extends Screen {
    private static final int SNAP = 6, ITEM_H = 13;
    private final Screen parent;
    /** null = everything on your screen right now; otherwise just these (one item's own editor). */
    private final List<HudPiece> only;
    private HudPiece dragging, menuFor;
    private double grabX, grabY;
    private int menuX, menuY;
    private boolean confirmReset;

    /** Everything that's on your screen right now. */
    public LayoutScreen(Screen parent) {
        super(Component.literal("Screen layout"));
        this.parent = parent;
        this.only = null;
    }

    /** One item's own editor (for things that aren't always on screen, like the wheel). */
    public LayoutScreen(Screen parent, HudPiece piece) {
        super(Component.literal("Move the " + piece.name()));
        this.parent = parent;
        this.only = List.of(piece);
    }

    /** Bottom to top: bigger items under the small boxes, so the boxes stay easy to grab. */
    private static final List<HudPiece> ALL = List.of(GameRender.WHEEL_PIECE, GameRender.CASE_PIECE, GoalsHud.PIECE, GiveawayHud.PIECE,
            StreamHud.PIECE, TopDonorsHud.PIECE);

    public List<HudPiece> pieces() {
        if (only != null) return only;
        List<HudPiece> out = new ArrayList<>();
        for (HudPiece p : ALL) if (p.onScreenNow()) out.add(p);
        return out;
    }

    /** True while this piece is open in a layout editor. */
    public static boolean editing(HudPiece p) {
        return Minecraft.getInstance().screen instanceof LayoutScreen ls && ls.pieces().contains(p);
    }

    private static String title(HudPiece p) {
        String n = p.name();
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    @Override
    protected void init() {
        int bw = 70, gap = 4;
        int x = this.width / 2 - (bw * 2 + gap) / 2, y = this.height - 26;
        addRenderableWidget(new FancyButton(x, y, bw, 18, Component.literal("Reset all"), b -> {
            if (!confirmReset) {
                confirmReset = true;
                b.setMessage(Component.literal("Sure?"));
                return;
            }
            confirmReset = false;
            for (HudPiece p : pieces()) p.reset();
            Config.save();
            b.setMessage(Component.literal("Reset all"));
        }).accent(Ui.DIM));
        addRenderableWidget(new FancyButton(x + bw + gap, y, bw, 18, Component.literal("Done"), b -> onClose()).accent(Ui.GOOD));
    }

    private int[] box(HudPiece p) {
        return p.bounds(this.font, this.width, this.height, true);
    }

    private HudPiece at(double mx, double my) {
        List<HudPiece> all = pieces();
        for (int i = all.size() - 1; i >= 0; i--) {
            int[] b = box(all.get(i));
            if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) return all.get(i);
        }
        return null;
    }

    /** Store a top-left position as fractions of the free space, snapping to edges and the centre. */
    private void place(HudPiece p, double x, double y, int w, int h) {
        double freeX = Math.max(1, this.width - w), freeY = Math.max(1, this.height - h);
        x = Math.max(0, Math.min(freeX, x));
        y = Math.max(0, Math.min(freeY, y));
        if (x < SNAP) x = 0;
        if (x > freeX - SNAP) x = freeX;
        if (Math.abs(x + w / 2.0 - this.width / 2.0) < SNAP) x = this.width / 2.0 - w / 2.0;
        if (y < SNAP) y = 0;
        if (y > freeY - SNAP) y = freeY;
        p.set(x / freeX, y / freeY, p.scale());
    }

    private void resize(HudPiece p, double d) {
        int[] b = box(p);
        double cxp = b[0] + b[2] / 2.0, cyp = b[1] + b[3] / 2.0;
        double s = Math.max(0.5, Math.min(4.0, Math.round((p.scale() + d) * 10) / 10.0));
        p.set(p.x(), p.y(), s);
        int[] nb = box(p);
        place(p, cxp - nb[2] / 2.0, cyp - nb[3] / 2.0, nb[2], nb[3]);
        Config.save();
    }

    // ------------------------------------------------------------------ the right-click menu

    private List<HudPiece.Item> menuItems(HudPiece p) {
        List<HudPiece.Item> items = new ArrayList<>();
        if (p.canHide()) items.add(HudPiece.toggle("Show while playing", p::shown, p::setShown));
        items.add(new HudPiece.Item(() -> "Size:  " + Math.round(p.scale() * 100) + "%   (click: bigger)", () -> resize(p, p.scale() >= 3.95 ? -3.5 : 0.25)));
        items.addAll(p.menu());
        items.add(new HudPiece.Item(() -> "Reset where it goes", p::reset));
        return items;
    }

    private int menuW(HudPiece p) {
        int w = this.font.width(title(p)) + 16;
        for (HudPiece.Item it : menuItems(p)) w = Math.max(w, this.font.width(it.text().get()) + 16);
        return w;
    }

    private int[] menuBox() {
        if (menuFor != null && !pieces().contains(menuFor)) menuFor = null;
        List<HudPiece.Item> items = menuItems(menuFor);
        int w = menuW(menuFor), h = 14 + items.size() * ITEM_H + 4;
        int x = Math.min(menuX, this.width - w - 2), y = Math.min(menuY, this.height - h - 2);
        return new int[]{Math.max(2, x), Math.max(2, y), w, h};
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x(), my = e.y();
        if (menuFor != null) {
            int[] m = menuBox();
            if (mx >= m[0] && mx < m[0] + m[2] && my >= m[1] && my < m[1] + m[3]) {
                int i = (int) ((my - m[1] - 14) / ITEM_H);
                List<HudPiece.Item> items = menuItems(menuFor);
                if (my >= m[1] + 14 && i >= 0 && i < items.size()) {
                    items.get(i).click().run();
                    Config.save();
                    Minecraft.getInstance().getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
                }
                return true;
            }
            menuFor = null; // clicking anywhere else closes it
            if (e.button() != 1) return true;
        }
        if (super.mouseClicked(e, doubleClick)) return true; // Done / Reset all
        HudPiece p = at(mx, my);
        if (p == null) return false;
        if (e.button() == 1) {
            menuFor = p;
            menuX = (int) mx;
            menuY = (int) my;
            return true;
        }
        if (e.button() == 0) {
            int[] b = box(p);
            dragging = p;
            grabX = mx - b[0];
            grabY = my - b[1];
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragging != null) {
            int[] b = box(dragging);
            place(dragging, e.x() - grabX, e.y() - grabY, b[2], b[3]);
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (dragging != null) {
            dragging = null;
            Config.save();
            return true;
        }
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        HudPiece p = at(mouseX, mouseY);
        if (p != null && scrollY != 0 && menuFor == null) {
            resize(p, scrollY > 0 ? 0.1 : -0.1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == GLFW.GLFW_KEY_ESCAPE && menuFor != null) {
            menuFor = null;
            return true;
        }
        return super.keyPressed(e);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, Ui.a(0.3f, 0x000000));
        for (int i = 1; i < 4; i++) {
            int gx = this.width * i / 4, gy = this.height * i / 4;
            g.fill(gx, 0, gx + 1, this.height, Ui.a(i == 2 ? 0.22f : 0.08f, 0xFFFFFF));
            g.fill(0, gy, this.width, gy + 1, Ui.a(i == 2 ? 0.22f : 0.08f, 0xFFFFFF));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        HudPiece hover = menuFor == null && dragging == null ? at(mouseX, mouseY) : dragging;
        for (HudPiece p : pieces()) {
            int[] b = box(p);
            p.drawAt(g, this.font, b[0], b[1], (float) p.scale(), true);
            boolean hot0 = p == hover || p == menuFor;
            if (only == null && !p.canHide() && !hot0) {
                // the wheel and case only pop up during a game: dimmed so the boxes you see all the time stand out
                g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], Ui.a(0.45f, 0x000000));
                String h = title(p) + " (pops up in games)";
                g.drawString(this.font, h, b[0] + b[2] / 2 - this.font.width(h) / 2, b[1] + 2, Ui.a(1f, 0xB0A4A8), true);
            }
            if (!p.shown()) {
                g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], Ui.a(0.6f, 0x000000));
                String h = "hidden";
                g.drawString(this.font, h, b[0] + b[2] / 2 - this.font.width(h) / 2, b[1] + b[3] / 2 - 4, Ui.a(1f, 0xB0A4A8), true);
            }
            boolean hot = p == hover || p == menuFor;
            int col = Ui.a(hot ? 1f : 0.45f, hot ? 0xFFFFFF : Ui.ACCENT);
            for (int x = b[0]; x < b[0] + b[2]; x += 6) {
                g.fill(x, b[1] - 1, Math.min(x + 3, b[0] + b[2]), b[1], col);
                g.fill(x, b[1] + b[3], Math.min(x + 3, b[0] + b[2]), b[1] + b[3] + 1, col);
            }
            for (int y = b[1]; y < b[1] + b[3]; y += 6) {
                g.fill(b[0] - 1, y, b[0], Math.min(y + 3, b[1] + b[3]), col);
                g.fill(b[0] + b[2], y, b[0] + b[2] + 1, Math.min(y + 3, b[1] + b[3]), col);
            }
            if (hot) {
                String tag = title(p) + "  " + Math.round(p.scale() * 100) + "%";
                int ty = b[1] > 12 ? b[1] - 11 : b[1] + b[3] + 2;
                g.fill(b[0], ty - 1, b[0] + this.font.width(tag) + 6, ty + 9, Ui.a(0.85f, 0x000000));
                g.drawString(this.font, tag, b[0] + 3, ty, Ui.a(1f, 0xFFFFFF), false);
            }
        }

        String help = "Drag to move   ·   Scroll to resize   ·   Right-click for settings";
        int hw = this.font.width(help) + 16, hx = this.width / 2 - hw / 2;
        g.fill(hx, 6, hx + hw, 22, Ui.a(0.85f, 0x000000));
        g.fill(hx, 6, hx + hw, 7, Ui.a(1f, Ui.ACCENT));
        g.drawString(this.font, help, hx + 8, 11, Ui.a(1f, 0xFFFFFF), false);

        if (menuFor != null && !pieces().contains(menuFor)) menuFor = null;
        if (pieces().isEmpty()) {
            String a = "Nothing is on your screen right now.";
            String b = "Switch something on (top donors, live counter, goal bar), or use an item's own Move & resize button.";
            g.drawString(this.font, a, this.width / 2 - this.font.width(a) / 2, this.height / 2 - 10, Ui.a(1f, 0xFFFFFF), true);
            g.drawString(this.font, b, this.width / 2 - this.font.width(b) / 2, this.height / 2 + 2, Ui.a(1f, 0xB0A4A8), true);
        }
        if (menuFor != null) drawMenu(g, mouseX, mouseY);
    }

    private void drawMenu(GuiGraphics g, int mouseX, int mouseY) {
        int[] m = menuBox();
        g.fill(m[0] - 1, m[1] - 1, m[0] + m[2] + 1, m[1] + m[3] + 1, Ui.a(1f, 0x000000));
        g.fill(m[0], m[1], m[0] + m[2], m[1] + m[3], Ui.a(0.96f, 0x17121B));
        g.fill(m[0], m[1], m[0] + m[2], m[1] + 1, Ui.a(1f, Ui.ACCENT));
        g.drawString(this.font, title(menuFor), m[0] + 6, m[1] + 4, Ui.a(1f, Ui.ACCENT), false);
        List<HudPiece.Item> items = menuItems(menuFor);
        for (int i = 0; i < items.size(); i++) {
            int y = m[1] + 14 + i * ITEM_H;
            boolean hov = mouseX >= m[0] && mouseX < m[0] + m[2] && mouseY >= y && mouseY < y + ITEM_H;
            if (hov) g.fill(m[0] + 1, y, m[0] + m[2] - 1, y + ITEM_H, Ui.a(1f, Ui.tint(0x2A2030, 0.2f)));
            g.drawString(this.font, items.get(i).text().get(), m[0] + 8, y + 3, Ui.a(1f, hov ? 0xFFFFFF : 0xD8CDD1), false);
        }
    }

    /** Where menu item {@code i} is on screen, in GUI pixels {x, y} (tests). */
    public int[] menuItemAt(int i) {
        int[] m = menuBox();
        return new int[]{m[0] + m[2] / 2, m[1] + 14 + i * ITEM_H + ITEM_H / 2};
    }

    /** Open the menu for a piece as if right-clicked at its centre (tests). */
    public void openMenu(HudPiece p) {
        int[] b = box(p);
        menuFor = p;
        menuX = b[0] + b[2] / 2;
        menuY = b[1] + b[3] / 2;
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
