package com.punishfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The shared Punish FX window: a sidebar on the left (pages, master switch, test, stop, done)
 * and the current page on the right, so everything always lives in the same place.
 */
public abstract class PfxFrame extends Screen {
    public enum Page { GENERAL, LEVELS, PUNISHMENTS, CUSTOMIZE, GAMES, TOP_DONORS, STREAM, GIVEAWAY, PRESETS }

    protected final Screen parent;
    private final Page page;
    private final String heading, subheading;

    /** Panel, sidebar width, and the content box the page lays itself out in. */
    protected int px, py, pw, ph, sw;
    protected int cx, cy, cw, ch;
    /** Text shown in the footer; pages can replace it (e.g. with a hover description). */
    protected String footer = null;

    private static final int PRESET_W = 64;

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
            case CUSTOMIZE -> new CustomizeScreen(parent);
            case TOP_DONORS -> new TopDonorsScreen(parent);
            case STREAM -> new StreamScreen(parent);
            case GAMES -> new GamesScreen(parent);
            case PRESETS -> new PresetsScreen(parent);
            case GIVEAWAY -> new GiveawayScreen(parent);
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
        rows = List.of();
        placed.clear();
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
        // seven pages: tighter buttons on small windows so they never run into Enabled/Test/Stop
        // eight pages: tighter buttons on small windows so they never run into Enabled/Test/Stop
        int step = ph >= 300 ? 22 : ph >= 260 ? 18 : 15, nh = ph >= 300 ? 20 : ph >= 260 ? 16 : 14;
        addNav(x, y, w, nh, "General", Page.GENERAL);
        addNav(x, y += step, w, nh, "Levels", Page.LEVELS);
        addNav(x, y += step, w, nh, "Punishments", Page.PUNISHMENTS);
        addNav(x, y += step, w, nh, "Customize", Page.CUSTOMIZE);
        addNav(x, y += step, w, nh, "Wheel & Case", Page.GAMES);
        addNav(x, y += step, w, nh, "Top Donors", Page.TOP_DONORS);
        addNav(x, y += step, w, nh, StreamSession.live() ? "Stream \u25cf" : "Stream", Page.STREAM);
        addNav(x, y += step, w, nh, Giveaway.live() ? "Giveaway \u25cf" : "Giveaway", Page.GIVEAWAY);

        // Presets: top-right corner of every page
        addRenderableWidget(new FancyButton(px + pw - 10 - PRESET_W, py + 7, PRESET_W, 16, Component.literal("\u2605 Presets"), b -> {
            if (page == Page.PRESETS) return;
            Config.save();
            Minecraft.getInstance().setScreen(open(parent, Page.PRESETS));
        }).accent(Ui.WARN).hot(() -> page == Page.PRESETS));

        boolean inWorld = Minecraft.getInstance().player != null;
        int by = py + ph - 7;
        addRenderableWidget(new FancyButton(x, by -= 20, w, 20, Component.literal("Done"), b -> onClose()).accent(Ui.DIM));
        int half = (w - 3) / 2;
        FancyButton test = new FancyButton(x, by -= 23, half, 20, Component.literal("Test"), b -> {
            Config.save();
            Minecraft mc = Minecraft.getInstance();
            int l = (int) (Math.random() * c.tiers.size());
            mc.setScreen(null);
            PunishManager.hit(c.tiers.get(l).minAmount, mc.player == null ? "You" : mc.player.getGameProfile().name(), l, true);
        });
        test.accent(Ui.WARN).active = inWorld;
        addRenderableWidget(test);
        FancyButton stop = new FancyButton(x + half + 3, by, w - half - 3, 20, Component.literal("Stop"), b -> PunishManager.cancelAll());
        stop.accent(Ui.THEME).active = inWorld;
        addRenderableWidget(stop);
        addRenderableWidget(new FancyButton(x, by - 23, w, 20, Component.literal("Enabled"), b -> {
            c.enabled = !c.enabled;
            if (!c.enabled) PunishManager.cancelAll();
            Config.save();
        }).toggle(() -> c.enabled));
    }

    private void addNav(int x, int y, int w, int h, String label, Page target) {
        addRenderableWidget(new FancyButton(x, y, w, h, Component.literal(label), b -> {
            if (target == page) return;
            Config.save();
            Minecraft.getInstance().setScreen(open(parent, target));
        }).accent(Ui.THEME).hot(() -> target == page));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        Ui.panel(g, this.width, this.height, px, py, pw, ph, System.currentTimeMillis());
        // sidebar strip + divider
        g.fillGradient(px + 1, py + 2, px + sw, py + ph - 1, Ui.a(1f, Ui.tint(0x0E0C12, 0.07f)), Ui.a(1f, Ui.tint(0x08070A, 0.05f)));
        g.fill(px + sw, py + 2, px + sw + 1, py + ph - 1, Ui.a(0.5f, Ui.ACCENT));
        renderListBackground(g, mouseX, mouseY);
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
        g.drawString(this.font, "v1.7", px + 8, py + 23, Ui.a(1f, 0x6E6064), false);

        // page title + subtitle
        Ui.glowText(g, this.font, heading, cx, py + 9, 0xFFFFFF, 1.4f);
        int hx = cx + (int) (this.font.width(heading) * 1.4f) + 8;
        Ui.text(g, this.font, subheading, hx, py + 13, 0x8C7E82, px + pw - 16 - PRESET_W - hx);
        g.fill(cx, py + 27, cx + cw, py + 28, Ui.a(0.3f, Ui.ACCENT));

        footer = null;
        renderListForeground(g, mouseX, mouseY);
        renderPage(g, mouseX, mouseY);

        // footer: hover help, or where the keys are
        String f = footer != null ? footer
                : "Open: " + PunishFxClient.settingsKeyName() + "   Cancel punishments: " + PunishFxClient.cancelKeyName() + "   Commands: /pfx";
        Ui.text(g, this.font, f, cx, py + ph - 13, footer != null ? Ui.TEXT : 0x7A6C70, cw);
    }

    // ================================================================== settings list
    // Pages describe their settings as rows; the frame lays them out, scrolls them and draws them.

    protected interface Row {}

    protected record Header(String text) implements Row {}

    protected record Toggle(String label, String desc, BooleanSupplier get, Consumer<Boolean> set) implements Row {}

    /** A setting with named options. {@code descs} (optional) explains each option; it replaces {@code desc}. */
    protected record Choice(String label, String desc, String[] names, IntSupplier get, IntConsumer set, String[] descs) implements Row {
        protected Choice(String label, String desc, String[] names, IntSupplier get, IntConsumer set) {
            this(label, desc, names, get, set, null);
        }

        String shownDesc() {
            if (descs == null) return desc;
            int i = get.getAsInt();
            return i >= 0 && i < descs.length ? descs[i] : desc;
        }
    }

    protected record Action(String label, String desc, Supplier<String> button, int accent, Runnable run) implements Row {}

    protected record Text(String label, String desc, Supplier<String> get, Consumer<String> set) implements Row {}

    /** A row with two buttons (e.g. Apply / Delete). */
    protected record Pair(String label, String desc, Supplier<String> b1, int accent1, Runnable run1,
                          Supplier<String> b2, int accent2, Runnable run2) implements Row {}

    /** A choice between number values (shown with {@code fmt}); picks the nearest one to the current value. */
    protected static Choice numbers(String label, String desc, double[] vals, DoubleFunction<String> fmt, DoubleSupplier get, DoubleConsumer set) {
        String[] names = new String[vals.length];
        for (int i = 0; i < vals.length; i++) names[i] = fmt.apply(vals[i]);
        return new Choice(label, desc, names, () -> {
            double cur = get.getAsDouble();
            int best = 0;
            for (int i = 1; i < vals.length; i++) if (Math.abs(vals[i] - cur) < Math.abs(vals[best] - cur)) best = i;
            return best;
        }, i -> set.accept(vals[i]));
    }

    private static final Map<Page, Integer> SCROLL = new EnumMap<>(Page.class);
    private List<Row> rows = List.of();
    private final List<int[]> placed = new ArrayList<>(); // {row index, y, card height}
    private int listTop, listBottom, listW, rowH;
    private boolean listOverflows;
    private static final int VALUE_W = 78;

    private int heightOf(Row r) {
        return r instanceof Header ? 14 : rowH;
    }

    /** Lay out {@code list} between {@code top} and {@code bottom} of the content area. */
    protected void list(List<Row> list, int top, int bottom) {
        rows = list;
        listTop = top;
        listBottom = bottom;
        placed.clear();
        rowH = bottom - top >= 170 ? 26 : 22;

        // the furthest we can scroll and still fill the space
        int maxFirst = 0, used = 0;
        for (int i = rows.size() - 1; i >= 0; i--) {
            used += heightOf(rows.get(i));
            if (used > bottom - top) {
                maxFirst = i + 1;
                break;
            }
        }
        listOverflows = maxFirst > 0;
        listW = cw - (listOverflows ? 7 : 0);
        int first = Math.max(0, Math.min(maxFirst, SCROLL.getOrDefault(page, 0)));
        SCROLL.put(page, first);

        int y = top;
        for (int i = first; i < rows.size(); i++) {
            Row r = rows.get(i);
            int h = heightOf(r);
            if (y + h > bottom) break;
            int ch = r instanceof Header ? h : h - 3;
            placeRow(r, y, ch);
            placed.add(new int[]{i, y, ch});
            y += h;
        }
    }

    private void placeRow(Row r, int y, int h) {
        if (r instanceof Toggle t) {
            addRenderableWidget(new FancyButton(cx, y, listW, h, Component.literal(t.label()), b -> {
                t.set().accept(!t.get().getAsBoolean());
                Config.save();
            }).toggle(t.get()).desc(t.desc()));
        } else if (r instanceof Choice c) {
            int bw = 16, bh = Math.min(14, h - 4), by = y + (h - bh) / 2;
            int nextX = cx + listW - 8 - bw, prevX = nextX - VALUE_W - bw;
            int n = c.names().length;
            addRenderableWidget(new FancyButton(prevX, by, bw, bh, Component.literal("<"), b -> {
                c.set().accept((c.get().getAsInt() - 1 + n) % n);
                Config.save();
            }).accent(Ui.THEME2));
            addRenderableWidget(new FancyButton(nextX, by, bw, bh, Component.literal(">"), b -> {
                c.set().accept((c.get().getAsInt() + 1) % n);
                Config.save();
            }).accent(Ui.THEME2));
        } else if (r instanceof Action a) {
            int bw = 86, bh = Math.min(16, h - 4);
            addRenderableWidget(new FancyButton(cx + listW - 8 - bw, y + (h - bh) / 2, bw, bh, Component.literal(a.button().get()), b -> {
                a.run().run();
                b.setMessage(Component.literal(a.button().get()));
            }).accent(a.accent()));
        } else if (r instanceof Pair p) {
            int w2 = 52, w1 = 62, bh = Math.min(16, h - 4), by = y + (h - bh) / 2;
            int x2 = cx + listW - 8 - w2, x1 = x2 - 4 - w1;
            addRenderableWidget(new FancyButton(x1, by, w1, bh, Component.literal(p.b1().get()), b -> {
                p.run1().run();
                b.setMessage(Component.literal(p.b1().get()));
            }).accent(p.accent1()));
            addRenderableWidget(new FancyButton(x2, by, w2, bh, Component.literal(p.b2().get()), b -> {
                p.run2().run();
                b.setMessage(Component.literal(p.b2().get()));
            }).accent(p.accent2()));
        } else if (r instanceof Text t) {
            int fw = Math.min(140, listW / 2);
            EditBox e = new EditBox(this.font, cx + listW - 8 - fw + 4, y + (h - 8) / 2, fw - 6, 10, Component.literal(t.label()));
            e.setMaxLength(32);
            e.setBordered(false);
            e.setValue(t.get().get());
            e.setTextColor(0xFFFFFFFF);
            e.setResponder(v -> {
                t.set().accept(v);
                Config.save();
            });
            addRenderableWidget(e);
        }
    }

    private int rightReserve(Row r) {
        if (r instanceof Choice) return VALUE_W + 48;
        if (r instanceof Action) return 98;
        if (r instanceof Pair) return 132;
        if (r instanceof Text) return Math.min(140, listW / 2) + 12;
        return 40;
    }

    private void renderListBackground(GuiGraphics g, int mouseX, int mouseY) {
        for (int[] p : placed) {
            Row r = rows.get(p[0]);
            if (r instanceof Header || r instanceof Toggle) continue; // toggles draw their own row
            boolean hov = mouseX >= cx && mouseX < cx + listW && mouseY >= p[1] && mouseY < p[1] + p[2];
            String label = r instanceof Choice c ? c.label() : r instanceof Action a ? a.label() : r instanceof Pair pr ? pr.label() : ((Text) r).label();
            String desc = r instanceof Choice c ? c.shownDesc() : r instanceof Action a ? a.desc() : r instanceof Pair pr ? pr.desc() : ((Text) r).desc();
            int bar = r instanceof Choice ? Ui.ACCENT2 : r instanceof Action a ? Ui.resolve(a.accent()) : r instanceof Pair pr ? Ui.resolve(pr.accent1()) : Ui.ACCENT;
            Ui.rowCard(g, this.font, cx, p[1], listW, p[2], label, desc, hov, bar, rightReserve(r));
            if (r instanceof Text) {
                int fw = Math.min(140, listW / 2);
                Ui.card(g, cx + listW - 8 - fw, p[1] + 3, fw, p[2] - 6, Ui.tint(0x0E0C10, 0.06f));
            }
        }
    }

    private void renderListForeground(GuiGraphics g, int mouseX, int mouseY) {
        for (int[] p : placed) {
            Row r = rows.get(p[0]);
            if (r instanceof Header hd) {
                Ui.section(g, this.font, hd.text(), cx, p[1] + 4, listW);
            } else if (r instanceof Choice c) {
                int nextX = cx + listW - 8 - 16;
                int mid = nextX - VALUE_W / 2;
                int idx = Math.max(0, Math.min(c.names().length - 1, c.get().getAsInt()));
                String v = Ui.fit(this.font, c.names()[idx], VALUE_W - 4);
                g.drawString(this.font, v, mid - this.font.width(v) / 2, p[1] + (p[2] - 8) / 2, Ui.a(1f, 0xFFFFFF), false);
            }
            // rows too short for their description show it in the footer on hover
            boolean hov = mouseX >= cx && mouseX < cx + listW && mouseY >= p[1] && mouseY < p[1] + p[2];
            if (hov && p[2] < 22) {
                String d = r instanceof Toggle t ? t.desc() : r instanceof Choice c ? c.shownDesc() : r instanceof Action a ? a.desc() : r instanceof Pair pr ? pr.desc() : r instanceof Text t ? t.desc() : null;
                if (d != null && !d.isEmpty()) footer = d;
            }
        }
        if (listOverflows && !rows.isEmpty()) {
            int x = cx + cw - 3, top = listTop, h = listBottom - listTop;
            g.fill(x, top, x + 3, top + h, Ui.a(0.25f, 0xFFFFFF));
            int first = placed.isEmpty() ? 0 : placed.get(0)[0];
            int shown = placed.size();
            int th = Math.max(12, h * shown / rows.size());
            int ty = top + (h - th) * first / Math.max(1, rows.size() - shown);
            g.fill(x, ty, x + 3, ty + th, Ui.a(0.9f, Ui.ACCENT));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!rows.isEmpty() && listOverflows && mouseX >= cx && mouseX < cx + cw && mouseY >= listTop && mouseY < listBottom) {
            int cur = SCROLL.getOrDefault(page, 0);
            int next = Math.max(0, cur - (int) Math.signum(scrollY) * 2);
            if (next != cur) {
                SCROLL.put(page, next);
                rebuild();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
