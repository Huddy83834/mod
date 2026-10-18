package com.lsstocks;

import com.lsstocks.MarketStore.Point;
import com.lsstocks.MarketStore.Rec;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MarketScreen extends Screen {
    // ---- palette ----
    private static final int C_BG = 0xF00C0E13;
    private static final int C_PANEL = 0xFF11141B;
    private static final int C_PANEL_2 = 0xFF161A23;
    private static final int C_BORDER = 0xFF262C3A;
    private static final int C_ACCENT = 0xFFF5B83D;
    private static final int C_UP = 0xFF3DDC84;
    private static final int C_DOWN = 0xFFFF5C5C;
    private static final int C_TEXT = 0xFFE8EAF0;
    private static final int C_DIM = 0xFF8B93A7;
    private static final int C_DIMMER = 0xFF596074;
    private static final int C_ASK = 0xFF4FC3F7;
    private static final int C_BID = 0xFFFFA726;

    private static final int ROW_H = 26;
    private static final int LIST_HEAD = 16;
    private static final int[] DELAYS = {500, 900, 1500, 2500};

    private enum Tab {
        ALL("All Items"), DEALS("Deals"), AH("Auction House"), ORDERS("Orders"), FLIPS("Flips"),
        WATCH("Watchlist"), SETTINGS("Settings");
        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private enum Sort {
        PRICE("Price"), GAINERS("Gainers"), LOSERS("Losers"), NAME("A-Z"), RECENT("Recent");
        final String label;

        Sort(String label) {
            this.label = label;
        }
    }

    private enum Range {
        H1("1H", 3_600_000L), H6("6H", 6 * 3_600_000L), D1("24H", 24 * 3_600_000L),
        D7("7D", 7 * 24 * 3_600_000L), ALL("ALL", 0L);
        final String label;
        final long ms;

        Range(String label, long ms) {
            this.label = label;
            this.ms = ms;
        }
    }

    private record Row(String id, String name, Rec rec, double price, double change, int listings, double score) {
        Row(String id, String name, Rec rec, double price, double change, int listings) {
            this(id, name, rec, price, change, listings, Double.NaN);
        }
    }

    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private record Pt(long t, float p) {}

    /** Every vanilla item, built once, so search works even for items with no data yet. */
    private record Entry(String id, String name, String lower) {}

    private static List<Entry> catalog;

    private static synchronized List<Entry> catalog() {
        if (catalog == null) {
            List<Entry> list = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                String id = BuiltInRegistries.ITEM.getKey(item).toString();
                if (id.equals("minecraft:air")) continue;
                String name = new ItemStack(item).getHoverName().getString();
                list.add(new Entry(id, name, name.toLowerCase(Locale.ROOT)));
            }
            list.sort(Comparator.comparing(Entry::lower));
            catalog = list;
        }
        return catalog;
    }

    private final Screen parent;
    private final MarketStore store = StocksClient.STORE;
    private final List<Row> rows = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, ItemStack> icons = new HashMap<>();
    private final Map<String, String> dealTypes = new HashMap<>();

    private Tab tab = Tab.ALL;
    private Sort sort = Sort.PRICE;
    private Range range = Range.ALL;
    private String query = "";
    private String selectedId = null;
    private int scroll = 0;
    private boolean confirmClear = false;
    private int seenVersion = -1;
    private boolean wasRunning;
    private int dealCount;

    // overview cards
    private Rec ovGain, ovLose, ovFlip;
    private double ovGainPct, ovLosePct, ovFlipPct;
    private int ovTracked;

    // layout
    private int px, py, pw, ph;
    private int tabY, ctrlY, ovY, bodyY, bodyH, footY;
    private boolean showOverview;
    private int listX, listW, detX, detW;

    private int mouseXNow, mouseYNow;

    public MarketScreen(Screen parent) {
        super(Component.literal("LS Market"));
        this.parent = parent;
    }

    // ------------------------------------------------------------------ setup

    private void layout() {
        int m = Math.max(6, Math.min(16, this.width / 48));
        px = m;
        py = m;
        pw = this.width - 2 * m;
        ph = this.height - 2 * m;
        tabY = py + 28;
        ctrlY = py + 52;
        showOverview = ph >= 330 && tab != Tab.SETTINGS;
        ovY = py + 76;
        bodyY = showOverview ? py + 112 : py + 76;
        footY = py + ph - 18;
        bodyH = Math.max(60, footY - 6 - bodyY);
        listX = px + 10;
        listW = (int) ((pw - 30) * 0.45);
        detX = listX + listW + 10;
        detW = px + pw - 10 - detX;
    }

    @Override
    protected void init() {
        layout();
        if (tab == Tab.SETTINGS) {
            initSettings();
        } else {
            int sw = Math.max(120, Math.min(260, (int) (pw * 0.34)));
            EditBox search = new EditBox(this.font, px + 12, ctrlY, sw, 18, Component.literal("Search"));
            search.setHint(Component.literal(tab == Tab.ALL ? "Search any item or enchant..." : "Search..."));
            search.setValue(query);
            search.setResponder(s -> {
                query = s;
                scroll = 0;
                rebuild();
            });
            addRenderableWidget(search);
            setInitialFocus(search);
        }
        rebuild();
    }

    @Override
    public void onClose() {
        store.saveNow();
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void tick() {
        boolean running = Crawler.running();
        if (store.version != seenVersion || running != wasRunning) {
            wasRunning = running;
            if (tab != Tab.SETTINGS) rebuild();
        }
    }

    // ------------------------------------------------------------------ data

    private Row selectedRow() {
        if (selectedId == null) return null;
        for (Row r : rows) if (r.id().equals(selectedId)) return r;
        return null;
    }

    private static boolean hasData(Rec r) {
        return r != null && (!r.ask.isEmpty() || !r.bid.isEmpty());
    }

    private static String baseOf(String id) {
        int i = id.indexOf('|');
        return i < 0 ? id : id.substring(0, i);
    }

    private Row simpleRow(String id, String name, Rec r) {
        if (!hasData(r)) return new Row(id, name, r, Double.NaN, Double.NaN, 0);
        boolean useAsk = !r.ask.isEmpty();
        List<Point> s = useAsk ? r.ask : r.bid;
        return new Row(id, name, r, MarketStore.latest(s), MarketStore.changePct(s),
                useAsk ? r.askListings : r.bidListings);
    }

    private void rebuild() {
        seenVersion = store.version;
        rows.clear();
        String q = query.toLowerCase(Locale.ROOT).trim();

        // overview + deal count
        dealCount = store.deals().size();
        ovTracked = 0;
        ovGain = ovLose = ovFlip = null;
        ovGainPct = -1e18;
        ovLosePct = 1e18;
        ovFlipPct = -1e18;
        for (Rec r : store.data.items.values()) {
            if (!hasData(r)) continue;
            ovTracked++;
            double ch = MarketStore.changePct(r.ask);
            if (!Double.isNaN(ch) && r.askListings > 0) {
                if (ch > ovGainPct) {
                    ovGainPct = ch;
                    ovGain = r;
                }
                if (ch < ovLosePct) {
                    ovLosePct = ch;
                    ovLose = r;
                }
            }
            float ask = MarketStore.latest(r.ask);
            float bid = MarketStore.latest(r.bid);
            if (r.askListings > 0 && r.bidListings > 0 && ask > 0 && !Float.isNaN(bid)) {
                double mg = (bid - ask) / ask * 100.0;
                if (mg > ovFlipPct) {
                    ovFlipPct = mg;
                    ovFlip = r;
                }
            }
        }

        if (tab == Tab.ALL) {
            if (q.isEmpty()) {
                for (Rec r : store.data.items.values()) {
                    if (hasData(r)) rows.add(simpleRow(r.id, r.name, r));
                }
            } else {
                int n = 0;
                for (Entry e : catalog()) {
                    if (!e.lower().contains(q) && !e.id().contains(q)) continue;
                    rows.add(simpleRow(e.id(), e.name(), store.data.items.get(e.id())));
                    if (++n >= 500) break;
                }
                for (Rec r : store.data.items.values()) {
                    if (r.id.indexOf('|') >= 0 && r.name.toLowerCase(Locale.ROOT).contains(q)) {
                        rows.add(simpleRow(r.id, r.name, r));
                    }
                }
            }
        } else if (tab == Tab.DEALS) {
            dealTypes.clear();
            for (MarketStore.Deal d : store.deals()) {
                Rec r = d.rec();
                if (!q.isEmpty() && !(r.name.toLowerCase(Locale.ROOT).contains(q) || r.id.contains(q))) continue;
                dealTypes.put(r.id, d.type());
                double shown = d.type().equals("BUY") ? -d.pct() : d.pct();
                rows.add(new Row(r.id, r.name, r, d.price(), shown,
                        d.type().equals("SELL") ? r.bidListings : r.askListings, d.pct()));
            }
        } else if (tab != Tab.SETTINGS) {
            for (Rec r : store.data.items.values()) {
                if (!q.isEmpty() && !(r.name.toLowerCase(Locale.ROOT).contains(q) || r.id.contains(q))) continue;
                float ask = MarketStore.latest(r.ask);
                float bid = MarketStore.latest(r.bid);
                switch (tab) {
                    case AH -> {
                        if (r.ask.isEmpty()) continue;
                        rows.add(new Row(r.id, r.name, r, ask, MarketStore.changePct(r.ask), r.askListings));
                    }
                    case ORDERS -> {
                        if (r.bid.isEmpty()) continue;
                        rows.add(new Row(r.id, r.name, r, bid, MarketStore.changePct(r.bid), r.bidListings));
                    }
                    case FLIPS -> {
                        if (r.ask.isEmpty() || r.bid.isEmpty()) continue;
                        double margin = bid - ask;
                        double pct = ask > 0 ? margin / ask * 100.0 : 0;
                        rows.add(new Row(r.id, r.name, r, margin, pct, r.askListings));
                    }
                    case WATCH -> {
                        if (!r.watched) continue;
                        rows.add(simpleRow(r.id, r.name, r));
                    }
                    default -> {
                    }
                }
            }
        }

        Comparator<Row> byName = Comparator.comparing((Row r) -> r.name().toLowerCase(Locale.ROOT));
        Comparator<Row> cmp = switch (sort) {
            case PRICE -> Comparator.comparingDouble((Row r) -> nz(r.price(), -1e18)).reversed().thenComparing(byName);
            case GAINERS -> Comparator.comparingDouble((Row r) -> nz(r.change(), -1e9)).reversed().thenComparing(byName);
            case LOSERS -> Comparator.comparingDouble((Row r) -> nz(r.change(), 1e9)).thenComparing(byName);
            case NAME -> byName;
            case RECENT -> Comparator.comparingLong((Row r) -> r.rec() == null ? 0L : r.rec().updated).reversed().thenComparing(byName);
        };
        if (tab == Tab.DEALS && (sort == Sort.PRICE || sort == Sort.GAINERS)) {
            cmp = Comparator.comparingDouble((Row r) -> nz(r.score(), -1e9)).reversed().thenComparing(byName);
        } else if (tab == Tab.DEALS && sort == Sort.LOSERS) {
            cmp = Comparator.comparingDouble((Row r) -> nz(r.score(), 1e9)).thenComparing(byName);
        }
        rows.sort(cmp);

        boolean found = false;
        for (Row r : rows) {
            if (r.id().equals(selectedId)) {
                found = true;
                break;
            }
        }
        if (!found) selectedId = rows.isEmpty() ? null : rows.get(0).id();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visibleRows())));
    }

    private static double nz(double v, double fallback) {
        return Double.isNaN(v) ? fallback : v;
    }

    private int visibleRows() {
        return Math.max(1, (bodyH - LIST_HEAD - 2) / ROW_H);
    }

    private ItemStack icon(String id) {
        return icons.computeIfAbsent(baseOf(id),
                k -> new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(k))));
    }

    private void jumpTo(Rec r) {
        if (r == null) return;
        tab = Tab.ALL;
        query = "";
        selectedId = r.id;
        rebuildWidgets();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id().equals(r.id)) {
                scroll = Math.max(0, Math.min(i - 2, Math.max(0, rows.size() - visibleRows())));
                break;
            }
        }
    }

    private void runCommand(String cmd) {
        String c = cmd.trim();
        if (c.startsWith("/")) c = c.substring(1);
        Minecraft mc = Minecraft.getInstance();
        if (c.isEmpty() || mc.player == null) return;
        mc.gui.setScreen(null);
        mc.player.connection.sendCommand(c);
    }

    private void openListingPage(Rec r, boolean bid) {
        Minecraft mc = Minecraft.getInstance();
        int page = bid ? r.bidPage : r.askPage;
        if (mc.player == null || page < 1) return;
        mc.gui.setScreen(null);
        Crawler.openPage(mc, store, bid, page);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        int mx = (int) event.x();
        int my = (int) event.y();
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mx >= h.x() && mx < h.x() + h.w() && my >= h.y() && my < h.y() + h.h()) {
                h.action().run();
                return true;
            }
        }
        if (tab != Tab.SETTINGS) {
            int top = bodyY + LIST_HEAD;
            if (mx >= listX && mx < listX + listW && my >= top && my < top + visibleRows() * ROW_H) {
                int idx = scroll + (my - top) / ROW_H;
                if (idx >= 0 && idx < rows.size()) {
                    Row row = rows.get(idx);
                    selectedId = row.id();
                    if (doubleClick && row.rec() != null) {
                        if (row.rec().askPage > 0) openListingPage(row.rec(), false);
                        else if (row.rec().bidPage > 0) openListingPage(row.rec(), true);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (tab != Tab.SETTINGS && mx >= listX && mx < listX + listW && my >= bodyY && my < bodyY + bodyH) {
            int max = Math.max(0, rows.size() - visibleRows());
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY) * 2));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    // ------------------------------------------------------------------ small drawing helpers

    private void text(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        g.text(this.font, s, x, y, color, false);
    }

    private void rtext(GuiGraphicsExtractor g, String s, int rightX, int y, int color) {
        g.text(this.font, s, rightX - this.font.width(s), y, color, false);
    }

    private void bold(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        g.text(this.font, Component.literal(s).withStyle(ChatFormatting.BOLD), x, y, color, false);
    }

    private static void border(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, color);
        border(g, x, y, w, h, C_BORDER);
    }

    private String fit(String s, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (this.font.width(s) <= maxWidth) return s;
        String ell = "...";
        int end = s.length();
        while (end > 0 && this.font.width(s.substring(0, end) + ell) > maxWidth) end--;
        return s.substring(0, end) + ell;
    }

    private int chipW(String label) {
        return this.font.width(label) + 16;
    }

    /** Draws a clickable chip and registers its hit area. Returns its width. */
    private int chip(GuiGraphicsExtractor g, int x, int y, String label, boolean active, int accent, Runnable action) {
        int w = chipW(label);
        int h = 16;
        boolean hover = active && mouseXNow >= x && mouseXNow < x + w && mouseYNow >= y && mouseYNow < y + h;
        g.fill(x, y, x + w, y + h, !active ? 0x22FFFFFF : hover ? 0xFF2D3446 : 0xFF1E2330);
        border(g, x, y, w, h, accent != 0 && active ? accent : C_BORDER);
        g.text(this.font, label, x + 8, y + 4, !active ? C_DIMMER : accent != 0 ? accent : C_TEXT, false);
        if (active) hits.add(new Hit(x, y, w, h, action));
        return w;
    }

    private static int withAlpha(int color, int a) {
        return (a << 24) | (color & 0xFFFFFF);
    }

    // ------------------------------------------------------------------ main render

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        mouseXNow = mouseX;
        mouseYNow = mouseY;
        hits.clear();

        g.fill(0, 0, this.width, this.height, 0xB0070810);
        g.fill(px, py, px + pw, py + ph, C_BG);
        border(g, px, py, pw, ph, C_BORDER);
        g.fill(px, py, px + pw, py + 2, C_ACCENT);

        drawTopBar(g);
        drawTabs(g);

        if (tab == Tab.SETTINGS) {
            drawSettings(g);
        } else {
            drawControls(g);
            if (showOverview) drawOverview(g);
            drawList(g);
            drawDetail(g);
        }
        drawFooter(g);

        for (GuiEventListener c : this.children()) {
            if (c instanceof AbstractWidget w) w.extractRenderState(g, mouseX, mouseY, delta);
        }
        Popups.render(g);
    }

    // ------------------------------------------------------------------ top bar, tabs, controls

    private void drawTopBar(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        boolean online = mc.player != null;
        bold(g, "LS MARKET", px + 12, py + 9, C_ACCENT);

        String status;
        int sc;
        if (Crawler.running()) {
            status = Crawler.status();
            sc = C_ACCENT;
        } else if (online) {
            status = "Live";
            sc = C_UP;
        } else {
            status = "Offline · saved data";
            sc = C_BID;
        }
        int sx = px + 12 + this.font.width("LS MARKET") + 14;
        g.fill(sx, py + 11, sx + 5, py + 16, sc);
        text(g, status, sx + 10, py + 9, sc);

        int y = py + 6;
        int right = px + pw - 10;
        boolean running = Crawler.running();
        String upd = running ? "Stop update" : "Update all";
        right -= chipW(upd);
        chip(g, right, y, upd, online, running ? C_DOWN : C_ACCENT, () -> {
            if (Crawler.running()) Crawler.stop(Minecraft.getInstance(), store);
            else {
                Minecraft m = Minecraft.getInstance();
                m.gui.setScreen(null);
                Crawler.start(m, store, false);
            }
        });
        String ord = "Open Orders";
        right -= 6 + chipW(ord);
        chip(g, right, y, ord, online && !running, 0, () -> runCommand(store.data.orderCommand));
        String ah = "Open AH";
        right -= 6 + chipW(ah);
        chip(g, right, y, ah, online && !running, 0, () -> runCommand(store.data.ahCommand));
    }

    private void drawTabs(GuiGraphicsExtractor g) {
        int x = px + 12;
        for (Tab t : Tab.values()) {
            boolean sel = t == tab;
            int tw = this.font.width(t.label);
            boolean badge = t == Tab.DEALS && dealCount > 0;
            String bt = badge ? String.valueOf(Math.min(dealCount, 99)) : "";
            int bw = badge ? this.font.width(bt) + 8 : 0;
            int w = tw + 16 + (badge ? bw + 4 : 0);
            boolean hover = mouseXNow >= x && mouseXNow < x + w && mouseYNow >= tabY && mouseYNow < tabY + 18;
            int color = sel ? C_ACCENT : hover ? C_TEXT : C_DIM;
            if (sel) bold(g, t.label, x + 8, tabY + 4, color);
            else text(g, t.label, x + 8, tabY + 4, color);
            if (badge) {
                int bx = x + 8 + tw + 5;
                g.fill(bx, tabY + 3, bx + bw, tabY + 14, 0x403DDC84);
                text(g, bt, bx + 4, tabY + 4, C_UP);
            }
            if (sel) g.fill(x + 4, tabY + 17, x + w - 4, tabY + 19, C_ACCENT);
            final Tab target = t;
            hits.add(new Hit(x, tabY, w, 19, () -> {
                tab = target;
                scroll = 0;
                confirmClear = false;
                rebuildWidgets();
            }));
            x += w + 2;
        }
        g.fill(px + 8, tabY + 19, px + pw - 8, tabY + 20, C_BORDER);
    }

    private void drawControls(GuiGraphicsExtractor g) {
        int sw = Math.max(120, Math.min(260, (int) (pw * 0.34)));
        int x = px + 12 + sw + 8;
        chip(g, x, ctrlY + 1, "Sort: " + sort.label, true, 0, () -> {
            sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
            scroll = 0;
            rebuild();
        });
        String count = rows.size() + (rows.size() == 1 ? " item" : " items");
        rtext(g, count, px + pw - 12, ctrlY + 5, C_DIM);
    }

    private void drawOverview(GuiGraphicsExtractor g) {
        int gap = 6;
        int cw = (pw - 20 - 3 * gap) / 4;
        int x = px + 10;
        String upd = store.lastScanTime > 0 ? "scanned " + MarketStore.ago(store.lastScanTime) : "no scans yet";
        card(g, x, ovY, cw, "TRACKED", ovTracked + " items", upd, C_TEXT, null);
        x += cw + gap;
        card(g, x, ovY, cw, "TOP GAINER", ovGain == null ? "-" : ovGain.name,
                ovGain == null ? "" : MarketStore.pctText(ovGainPct), C_UP, ovGain);
        x += cw + gap;
        card(g, x, ovY, cw, "TOP LOSER", ovLose == null ? "-" : ovLose.name,
                ovLose == null ? "" : MarketStore.pctText(ovLosePct), C_DOWN, ovLose);
        x += cw + gap;
        card(g, x, ovY, cw, "BEST FLIP", ovFlip == null ? "-" : ovFlip.name,
                ovFlip == null ? "" : MarketStore.pctText(ovFlipPct), C_BID, ovFlip);
    }

    private void card(GuiGraphicsExtractor g, int x, int y, int w, String label, String main, String sub, int subColor, Rec target) {
        boolean hover = target != null && mouseXNow >= x && mouseXNow < x + w && mouseYNow >= y && mouseYNow < y + 30;
        g.fill(x, y, x + w, y + 30, hover ? 0xFF1A1F2B : C_PANEL);
        border(g, x, y, w, 30, hover ? C_ACCENT : C_BORDER);
        text(g, label, x + 8, y + 4, C_DIMMER);
        int subW = sub.isEmpty() ? 0 : this.font.width(sub) + 8;
        text(g, fit(main, w - 16 - subW), x + 8, y + 16, C_TEXT);
        if (!sub.isEmpty()) rtext(g, sub, x + w - 8, y + 16, subColor);
        if (target != null) hits.add(new Hit(x, y, w, 30, () -> jumpTo(target)));
    }

    // ------------------------------------------------------------------ list

    private void drawList(GuiGraphicsExtractor g) {
        panel(g, listX, bodyY, listW, bodyH, C_PANEL);

        int sparkW = 52;
        int right = listX + listW - 10;
        int sparkX = right - sparkW;
        int changeRight = sparkX - 8;
        int changeW = 54;
        int priceRight = changeRight - changeW - 8;
        int priceW = 62;
        int nameX = listX + 34;
        int nameMax = priceRight - priceW - 4 - nameX;

        // column headers
        g.fill(listX + 1, bodyY + 1, listX + listW - 1, bodyY + LIST_HEAD, 0xFF0D1016);
        text(g, "ITEM", nameX, bodyY + 4, C_DIMMER);
        rtext(g, tab == Tab.FLIPS ? "MARGIN" : "PRICE", priceRight, bodyY + 4, C_DIMMER);
        rtext(g, tab == Tab.DEALS ? "VS AVG" : "CHANGE", changeRight, bodyY + 4, C_DIMMER);
        text(g, "TREND", sparkX + 8, bodyY + 4, C_DIMMER);

        if (rows.isEmpty()) {
            String[] msg = switch (tab) {
                case FLIPS -> new String[] {"No flips yet.", "A flip needs prices from both the", "Auction House and Orders."};
                case WATCH -> new String[] {"Nothing on your watchlist.", "Select an item and press Watch."};
                case DEALS -> new String[] {"No deals right now.", "A deal is a price far below (or above)", "its average. Tune it in Settings."};
                case ALL -> query.isEmpty()
                        ? new String[] {"Type an item name above to search", "every item and see its price.", "Press Update all to collect prices."}
                        : new String[] {"No items match that search."};
                default -> new String[] {"No data yet.", "Press Update all to read the market."};
            };
            int ty = bodyY + LIST_HEAD + 20;
            for (String s : msg) {
                g.text(this.font, s, listX + listW / 2 - this.font.width(s) / 2, ty, C_DIM, false);
                ty += 12;
            }
            return;
        }

        int vis = visibleRows();
        int top = bodyY + LIST_HEAD;
        for (int i = 0; i < vis; i++) {
            int idx = scroll + i;
            if (idx >= rows.size()) break;
            Row row = rows.get(idx);
            int ry = top + i * ROW_H;
            boolean sel = row.id().equals(selectedId);
            boolean hover = mouseXNow >= listX && mouseXNow < listX + listW && mouseYNow >= ry && mouseYNow < ry + ROW_H;

            if (sel) {
                g.fill(listX + 1, ry, listX + listW - 1, ry + ROW_H, 0x30F5B83D);
                g.fill(listX + 1, ry, listX + 3, ry + ROW_H, C_ACCENT);
            } else if (hover) {
                g.fill(listX + 1, ry, listX + listW - 1, ry + ROW_H, 0x18FFFFFF);
            } else if ((idx & 1) == 1) {
                g.fill(listX + 1, ry, listX + listW - 1, ry + ROW_H, 0x0CFFFFFF);
            }

            g.item(icon(row.id()), listX + 10, ry + 5);
            boolean data = !Double.isNaN(row.price());
            g.text(this.font, fit(row.name(), nameMax), nameX, ry + 5, data ? C_TEXT : C_DIM, false);
            String sub;
            if (tab == Tab.DEALS) sub = dealLabel(dealTypes.get(row.id()));
            else if (row.listings() > 0) sub = row.listings() + " listed";
            else sub = baseOf(row.id()).replace("minecraft:", "");
            g.text(this.font, fit(sub, nameMax), nameX, ry + 16, C_DIMMER, false);

            if (data) {
                rtext(g, MarketStore.fmt(row.price()), priceRight, ry + 9, C_TEXT);

                boolean dealRow = tab == Tab.DEALS;
                double ch = row.change();
                int col = Double.isNaN(ch) ? C_DIM : dealRow ? C_UP : ch >= 0 ? C_UP : C_DOWN;
                String chs = MarketStore.pctText(ch);
                int cwid = this.font.width(chs) + 8;
                int cx = changeRight - cwid;
                if (!Double.isNaN(ch)) g.fill(cx, ry + 6, cx + cwid, ry + 19, withAlpha(col, 0x26));
                g.text(this.font, chs, cx + 4, ry + 9, col, false);

                Rec r = row.rec();
                List<Point> series = tab == Tab.ORDERS ? r.bid : r.ask.isEmpty() ? r.bid : r.ask;
                drawSpark(g, series, sparkX, ry + 6, sparkW, ROW_H - 12, col == C_DOWN ? C_DOWN : col == C_DIM ? C_DIMMER : C_UP);
            } else {
                rtext(g, "no data", right, ry + 9, C_DIMMER);
            }
        }

        if (rows.size() > vis) {
            int trackX = listX + listW - 4;
            int trackH = bodyH - LIST_HEAD - 2;
            int thumbH = Math.max(14, trackH * vis / rows.size());
            int thumbY = top + (trackH - thumbH) * scroll / Math.max(1, rows.size() - vis);
            g.fill(trackX, top, trackX + 2, top + trackH, 0x18FFFFFF);
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, C_ACCENT);
        }
    }

    private static String dealLabel(String type) {
        if (type == null) return "";
        return switch (type) {
            case "BUY" -> "BUY · below average";
            case "SELL" -> "SELL · above average";
            default -> "FLIP · ask to bid margin";
        };
    }

    private void drawSpark(GuiGraphicsExtractor g, List<Point> raw, int x, int y, int w, int h, int color) {
        List<Point> s = MarketStore.view(raw);
        if (s.size() < 2) {
            g.fill(x, y + h / 2, x + w, y + h / 2 + 1, 0x33FFFFFF);
            return;
        }
        int n = Math.min(s.size(), 40);
        List<Point> tail = s.subList(s.size() - n, s.size());
        float lo = Float.POSITIVE_INFINITY;
        float hi = Float.NEGATIVE_INFINITY;
        for (Point p : tail) {
            lo = Math.min(lo, p.p);
            hi = Math.max(hi, p.p);
        }
        float range = Math.max(hi - lo, 1e-6f);
        int[] ys = new int[w];
        for (int c = 0; c < w; c++) {
            double pos = c * (tail.size() - 1) / (double) (w - 1);
            int i0 = (int) Math.floor(pos);
            int i1 = Math.min(tail.size() - 1, i0 + 1);
            double f = pos - i0;
            double v = tail.get(i0).p * (1 - f) + tail.get(i1).p * f;
            ys[c] = y + h - 1 - (int) ((v - lo) / range * (h - 1));
        }
        for (int c = 0; c < w; c++) {
            int y0 = ys[c];
            int y1 = c + 1 < w ? ys[c + 1] : y0;
            g.fill(x + c, Math.min(y0, y1), x + c + 1, Math.max(y0, y1) + 1, color);
        }
    }

    // ------------------------------------------------------------------ detail

    private void drawDetail(GuiGraphicsExtractor g) {
        panel(g, detX, bodyY, detW, bodyH, C_PANEL_2);
        Row sr = selectedRow();
        if (sr == null) {
            String s = "Select an item to see its price history";
            g.text(this.font, s, detX + detW / 2 - this.font.width(s) / 2, bodyY + 30, C_DIM, false);
            return;
        }
        Rec r = sr.rec();
        Minecraft mc = Minecraft.getInstance();
        boolean online = mc.player != null && !Crawler.running();

        int dx = detX + 12;
        int dy = bodyY + 10;
        g.item(icon(sr.id()), dx, dy);
        bold(g, fit(sr.name(), detW - 60), dx + 24, dy + 1, C_TEXT);
        text(g, fit(baseOf(sr.id()).replace("minecraft:", ""), detW - 60), dx + 24, dy + 12, C_DIMMER);

        // action chips
        int cy = bodyY + 36;
        int x = dx;
        boolean watched = r != null && r.watched;
        x += chip(g, x, cy, watched ? "★ Watching" : "☆ Watch", true, watched ? C_ACCENT : 0, () -> {
            Row cur = selectedRow();
            if (cur != null) {
                Rec rec = store.rec(cur.id(), cur.name());
                rec.watched = !rec.watched;
                store.markDirty();
                store.version++;
            }
        }) + 6;
        if (r != null && r.askPage > 0) {
            final Rec fr = r;
            x += chip(g, x, cy, "Open AH page " + r.askPage, online, C_ASK, () -> openListingPage(fr, false)) + 6;
        }
        if (r != null && r.bidPage > 0) {
            final Rec fr = r;
            chip(g, x, cy, "Open orders page " + r.bidPage, online, C_BID, () -> openListingPage(fr, true));
        }

        if (!hasData(r)) {
            int ty = bodyY + 70;
            text(g, "No price data for this item yet.", dx, ty, C_TEXT);
            text(g, "Press Update all and it will be picked up", dx, ty + 14, C_DIM);
            text(g, "if anyone is selling it or buying it.", dx, ty + 26, C_DIM);
            return;
        }

        float ask = MarketStore.latest(r.ask);
        float bid = MarketStore.latest(r.bid);
        boolean both = r.askListings > 0 && r.bidListings > 0 && !Float.isNaN(ask) && !Float.isNaN(bid);
        int cardY = bodyY + 60;
        int gap = 6;
        int cw = (detW - 24 - 3 * gap) / 4;
        int cx = dx;
        statCard(g, cx, cardY, cw, "LOWEST ASK", r.askListings > 0 ? MarketStore.fmt(ask) : "-",
                r.askListings > 0 ? MarketStore.pctText(MarketStore.changePct(r.ask)) + " 24h" : "none listed", C_ASK);
        cx += cw + gap;
        statCard(g, cx, cardY, cw, "HIGHEST BID", r.bidListings > 0 ? MarketStore.fmt(bid) : "-",
                r.bidListings > 0 ? MarketStore.pctText(MarketStore.changePct(r.bid)) + " 24h" : "no orders", C_BID);
        cx += cw + gap;
        statCard(g, cx, cardY, cw, "FLIP MARGIN", both ? MarketStore.fmt(bid - ask) : "-",
                both && ask > 0 ? MarketStore.pctText((bid - ask) / ask * 100) : "needs both", both && bid > ask ? C_UP : C_DIM);
        cx += cw + gap;
        statCard(g, cx, cardY, cw, "LISTED", String.valueOf(r.askListings),
                r.bidListings + " orders", C_DIM);

        // chart toolbar
        int tbY = cardY + 44;
        g.fill(dx, tbY + 3, dx + 10, tbY + 5, C_ASK);
        text(g, "Ask", dx + 14, tbY, C_DIM);
        int lx = dx + 14 + this.font.width("Ask") + 10;
        g.fill(lx, tbY + 3, lx + 10, tbY + 5, C_BID);
        text(g, "Bid", lx + 14, tbY, C_DIM);

        int rx = detX + detW - 12;
        Range[] rs = Range.values();
        for (int i = rs.length - 1; i >= 0; i--) {
            final Range target = rs[i];
            String lab = target.label;
            int w = this.font.width(lab) + 12;
            rx -= w;
            boolean sel = target == range;
            boolean hover = mouseXNow >= rx && mouseXNow < rx + w && mouseYNow >= tbY - 3 && mouseYNow < tbY + 11;
            g.fill(rx, tbY - 3, rx + w, tbY + 11, sel ? 0x40F5B83D : hover ? 0x22FFFFFF : 0x0FFFFFFF);
            text(g, lab, rx + 6, tbY, sel ? C_ACCENT : C_DIM);
            hits.add(new Hit(rx, tbY - 3, w, 14, () -> range = target));
            rx -= 3;
        }

        int gutter = 48;
        int tagW = 58;
        int chartX = detX + 12 + gutter;
        int chartY = tbY + 16;
        int chartW = detW - 24 - gutter - tagW;
        int chartH = bodyY + bodyH - 28 - chartY;
        if (chartH >= 50 && chartW >= 80) {
            drawChart(g, r, chartX, chartY, chartW, chartH);
        } else {
            text(g, "Enlarge the window to see the chart", dx, chartY + 6, C_DIMMER);
        }
    }

    private void statCard(GuiGraphicsExtractor g, int x, int y, int w, String label, String value, String sub, int subColor) {
        g.fill(x, y, x + w, y + 38, C_PANEL);
        border(g, x, y, w, 38, C_BORDER);
        text(g, label, x + 7, y + 5, C_DIMMER);
        bold(g, fit(value, w - 12), x + 7, y + 16, C_TEXT);
        int sc = sub.startsWith("-") || sub.startsWith("<") ? C_DOWN : sub.startsWith("+") || sub.startsWith(">") ? C_UP : subColor;
        text(g, fit(sub, w - 12), x + 7, y + 27, sc);
    }

    // ------------------------------------------------------------------ chart

    private List<Pt> windowOf(List<Point> raw, long t0, long t1) {
        List<Point> s = MarketStore.view(raw);
        List<Pt> out = new ArrayList<>();
        if (s.isEmpty()) return out;
        Point before = null;
        for (Point p : s) {
            if (p.t < t0) before = p;
            else if (p.t <= t1) out.add(new Pt(p.t, p.p));
        }
        if (before != null) out.add(0, new Pt(t0, before.p));
        if (!out.isEmpty()) {
            Pt last = out.get(out.size() - 1);
            if (last.t() < t1) out.add(new Pt(t1, last.p()));
        }
        return out;
    }

    private static double interp(List<Pt> pts, long t) {
        if (pts.isEmpty()) return Double.NaN;
        if (t <= pts.get(0).t()) return pts.get(0).p();
        for (int i = 1; i < pts.size(); i++) {
            Pt b = pts.get(i);
            if (t <= b.t()) {
                Pt a = pts.get(i - 1);
                long span = Math.max(1, b.t() - a.t());
                double f = (t - a.t()) / (double) span;
                return a.p() * (1 - f) + b.p() * f;
            }
        }
        return pts.get(pts.size() - 1).p();
    }

    private static double niceNum(double x, boolean round) {
        double exp = Math.floor(Math.log10(x));
        double f = x / Math.pow(10, exp);
        double nf;
        if (round) nf = f < 1.5 ? 1 : f < 3 ? 2 : f < 7 ? 5 : 10;
        else nf = f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10;
        return nf * Math.pow(10, exp);
    }

    private void drawChart(GuiGraphicsExtractor g, Rec r, int x, int y, int w, int h) {
        long now = System.currentTimeMillis();
        long first = Long.MAX_VALUE;
        for (Point p : MarketStore.view(r.ask)) first = Math.min(first, p.t);
        for (Point p : MarketStore.view(r.bid)) first = Math.min(first, p.t);
        long t1 = now;
        long t0 = range == Range.ALL ? (first == Long.MAX_VALUE ? now - 60_000L : first) : now - range.ms;
        if (t1 - t0 < 60_000L) t0 = t1 - 60_000L;

        List<Pt> ask = windowOf(r.ask, t0, t1);
        List<Pt> bid = windowOf(r.bid, t0, t1);
        if (ask.isEmpty() && bid.isEmpty()) {
            text(g, "No data in this range", x + 8, y + 8, C_DIM);
            return;
        }

        double lo = Double.POSITIVE_INFINITY;
        double hi = Double.NEGATIVE_INFINITY;
        for (Pt p : ask) {
            lo = Math.min(lo, p.p());
            hi = Math.max(hi, p.p());
        }
        for (Pt p : bid) {
            lo = Math.min(lo, p.p());
            hi = Math.max(hi, p.p());
        }
        if (hi - lo < 1e-9) hi = lo + Math.max(1, Math.abs(lo) * 0.1);
        int maxTicks = Math.max(3, Math.min(7, h / 34));
        double step = niceNum(niceNum(hi - lo, false) / (maxTicks - 1), true);
        double nlo = Math.floor(lo / step) * step;
        double nhi = Math.ceil(hi / step) * step;
        if (nhi - nlo < 1e-9) nhi = nlo + step;

        // grid + axis labels (in their own gutter, so they never touch the lines)
        for (double v = nlo; v <= nhi + step * 0.01; v += step) {
            int gy = y + (int) ((nhi - v) / (nhi - nlo) * (h - 1));
            g.fill(x, gy, x + w, gy + 1, 0x16FFFFFF);
            String lab = MarketStore.fmt(v);
            g.text(this.font, lab, x - 6 - this.font.width(lab), gy - 4, C_DIMMER, false);
        }
        g.fill(x, y + h, x + w, y + h + 1, C_BORDER);

        int[] askY = columns(ask, t0, t1, x, y, w, h, nlo, nhi);
        int[] bidY = columns(bid, t0, t1, x, y, w, h, nlo, nhi);
        drawSeries(g, askY, x, y, w, h, C_ASK, 0x1A4FC3F7);
        drawSeries(g, bidY, x, y, w, h, C_BID, 0x14FFA726);

        // x axis labels
        String left = MarketStore.span(t1 - t0) + " ago";
        text(g, left, x, y + h + 5, C_DIMMER);
        String mid = MarketStore.span((t1 - t0) / 2) + " ago";
        text(g, mid, x + w / 2 - this.font.width(mid) / 2, y + h + 5, C_DIMMER);
        rtext(g, "now", x + w, y + h + 5, C_DIMMER);

        // last-price tags on the right edge
        int tagX = x + w + 5;
        int lastAskY = askY == null ? -1 : askY[w - 1];
        int lastBidY = bidY == null ? -1 : bidY[w - 1];
        if (askY != null) priceTag(g, tagX, lastAskY, y, h, MarketStore.fmt(ask.get(ask.size() - 1).p()), C_ASK);
        if (bidY != null) {
            int by = lastBidY;
            if (askY != null && Math.abs(by - lastAskY) < 13) by = lastAskY + (by >= lastAskY ? 13 : -13);
            priceTag(g, tagX, by, y, h, MarketStore.fmt(bid.get(bid.size() - 1).p()), C_BID);
        }

        // hover crosshair + tooltip
        if (mouseXNow >= x && mouseXNow < x + w && mouseYNow >= y && mouseYNow <= y + h) {
            int c = mouseXNow - x;
            long t = t0 + (long) ((t1 - t0) * (c / (double) Math.max(1, w - 1)));
            g.fill(mouseXNow, y, mouseXNow + 1, y + h, 0x55FFFFFF);
            List<String> lines = new ArrayList<>();
            List<Integer> colors = new ArrayList<>();
            lines.add(MarketStore.span(now - t) + " ago");
            colors.add(C_DIM);
            if (askY != null) {
                g.fill(mouseXNow - 2, askY[c] - 2, mouseXNow + 3, askY[c] + 3, C_ASK);
                lines.add("Ask  " + MarketStore.fmt(interp(ask, t)));
                colors.add(C_ASK);
            }
            if (bidY != null) {
                g.fill(mouseXNow - 2, bidY[c] - 2, mouseXNow + 3, bidY[c] + 3, C_BID);
                lines.add("Bid  " + MarketStore.fmt(interp(bid, t)));
                colors.add(C_BID);
            }
            int tw = 0;
            for (String s : lines) tw = Math.max(tw, this.font.width(s));
            tw += 14;
            int th = 8 + lines.size() * 11;
            int tx = mouseXNow + 10;
            if (tx + tw > x + w) tx = mouseXNow - 10 - tw;
            int ty = Math.max(y, Math.min(y + h - th, mouseYNow - th / 2));
            g.fill(tx, ty, tx + tw, ty + th, 0xF00A0C11);
            border(g, tx, ty, tw, th, C_BORDER);
            for (int i = 0; i < lines.size(); i++) {
                text(g, lines.get(i), tx + 7, ty + 5 + i * 11, colors.get(i));
            }
        }
    }

    /** One y per pixel column across the chart, linearly interpolated; null if there is no data. */
    private int[] columns(List<Pt> pts, long t0, long t1, int x, int y, int w, int h, double nlo, double nhi) {
        if (pts.isEmpty()) return null;
        int[] ys = new int[w];
        for (int c = 0; c < w; c++) {
            long t = t0 + (long) ((t1 - t0) * (c / (double) Math.max(1, w - 1)));
            double v = interp(pts, t);
            int yy = y + (int) ((nhi - v) / (nhi - nlo) * (h - 1));
            ys[c] = Math.max(y, Math.min(y + h - 1, yy));
        }
        return ys;
    }

    private void drawSeries(GuiGraphicsExtractor g, int[] ys, int x, int y, int w, int h, int color, int area) {
        if (ys == null) return;
        for (int c = 0; c < w; c++) {
            int y0 = ys[c];
            int y1 = c + 1 < w ? ys[c + 1] : y0;
            int top = Math.min(y0, y1);
            int bot = Math.max(y0, y1);
            if (bot + 2 < y + h) g.fill(x + c, bot + 2, x + c + 1, y + h, area);
            g.fill(x + c, top, x + c + 1, bot + 2, color);
        }
    }

    private void priceTag(GuiGraphicsExtractor g, int x, int yy, int chartY, int chartH, String label, int color) {
        int tw = this.font.width(label) + 8;
        int ty = Math.max(chartY, Math.min(chartY + chartH - 11, yy - 5));
        g.fill(x, ty, x + tw, ty + 11, color);
        g.text(this.font, label, x + 4, ty + 2, 0xFF0A0C11, false);
    }

    // ------------------------------------------------------------------ footer

    private void drawFooter(GuiGraphicsExtractor g) {
        long now = System.currentTimeMillis();
        String left = store.lastScanTime > 0
                ? "Last read: " + store.lastKind + " · " + store.lastScanCount + " listings · " + MarketStore.ago(store.lastScanTime)
                : "Nothing read yet";
        text(g, left, px + 12, footY + 4, C_DIM);

        String right;
        if (Crawler.running()) {
            right = "Updating now...";
        } else if (store.data.autoUpdate) {
            long next = store.data.lastFullUpdate + store.data.autoMinutes * 60_000L;
            right = now >= next ? "Auto-update due" : "Next auto-update in " + MarketStore.span(next - now);
        } else {
            right = "Auto-update off";
        }
        rtext(g, right, px + pw - 12, footY + 4, C_DIM);
    }

    // ------------------------------------------------------------------ settings

    private record SettingsLayout(int cols, int chipsY1, int chipsY2, int chipsY3, int fieldsY, int fcols, int fw, int endY) {}

    private SettingsLayout settingsLayout() {
        int cols = Math.max(1, Math.min(4, (pw - 28) / 188));
        int y = bodyY + 10;
        int y1 = y + 14;
        y = y1 + ((3 + cols - 1) / cols) * 20 + 10;
        int y2 = y + 14;
        y = y2 + ((7 + cols - 1) / cols) * 20 + 10;
        int y3 = y + 14;
        y = y3 + ((3 + cols - 1) / cols) * 20 + 10;
        int y4 = y + 14;
        int fcols = pw >= 640 ? 2 : 1;
        int fw = Math.min(360, (pw - 28 - (fcols - 1) * 12) / fcols);
        int end = y4 + ((7 + fcols - 1) / fcols) * 32 + 8;
        return new SettingsLayout(cols, y1, y2, y3, y4, fcols, fw, end);
    }

    private EditBox box(int x, int y, int w, String value, int max, java.util.function.Consumer<String> onChange) {
        EditBox e = new EditBox(this.font, x, y, w, 18, Component.literal("field"));
        e.setMaxLength(max);
        e.setValue(value);
        e.setResponder(onChange::accept);
        addRenderableWidget(e);
        return e;
    }

    private void initSettings() {
        SettingsLayout L = settingsLayout();
        int sx = px + 14;
        int[] xs = new int[7];
        int[] ys = new int[7];
        for (int i = 0; i < 7; i++) {
            xs[i] = sx + (i % L.fcols()) * (L.fw() + 12);
            ys[i] = L.fieldsY() + (i / L.fcols()) * 32 + 10;
        }
        box(xs[0], ys[0], L.fw(), store.data.ahKeywords, 200, s -> {
            store.data.ahKeywords = s;
            store.markDirty();
        });
        box(xs[1], ys[1], L.fw(), store.data.orderKeywords, 200, s -> {
            store.data.orderKeywords = s;
            store.markDirty();
        });
        box(xs[2], ys[2], L.fw(), store.data.ahCommand, 100, s -> {
            store.data.ahCommand = s;
            store.markDirty();
        });
        box(xs[3], ys[3], L.fw(), store.data.orderCommand, 100, s -> {
            store.data.orderCommand = s;
            store.markDirty();
        });
        box(xs[4], ys[4], L.fw(), store.data.nextKeywords, 100, s -> {
            store.data.nextKeywords = s;
            store.markDirty();
        });
        box(xs[5], ys[5], L.fw(), store.data.serverFilter, 100, s -> {
            store.data.serverFilter = s;
            store.markDirty();
        });
        box(xs[6], ys[6], L.fw(), store.data.priceRegex, 400, s -> store.setRegex(s));
    }

    private static int cycle(int cur, int[] opts) {
        for (int i = 0; i < opts.length; i++) if (opts[i] == cur) return opts[(i + 1) % opts.length];
        return opts[0];
    }

    private void drawSettings(GuiGraphicsExtractor g) {
        SettingsLayout L = settingsLayout();
        panel(g, px + 10, bodyY, pw - 20, bodyH, C_PANEL);
        int sx = px + 14;
        int cols = L.cols();

        int[] autoMins = {1, 2, 5, 10, 15, 30, 60};
        int[] drops = {5, 10, 15, 20, 30, 40, 50};
        int[] flips = {10, 15, 20, 30, 50, 100};
        int[] mins = {0, 10, 100, 1000, 10000, 100000};
        int[] popups = {4, 8, 12, 20};
        String[] windows = {"all history", "last 24 hours", "last 7 days"};

        // --- updating
        bold(g, "UPDATING", sx, L.chipsY1 - 14, C_ACCENT);
        String[] l1 = {
                "Auto-update: " + (store.data.autoUpdate ? "ON" : "OFF"),
                "Every: " + store.data.autoMinutes + " min",
                "Page speed: " + store.data.scanDelayMs + " ms"};
        Runnable[] a1 = {
                () -> store.data.autoUpdate = !store.data.autoUpdate,
                () -> store.data.autoMinutes = cycle(store.data.autoMinutes, autoMins),
                () -> {
                    int idx = 0;
                    for (int i = 0; i < DELAYS.length; i++) if (DELAYS[i] == store.data.scanDelayMs) idx = i;
                    store.data.scanDelayMs = DELAYS[(idx + 1) % DELAYS.length];
                }};
        settingChips(g, sx, L.chipsY1, cols, l1, a1);

        // --- deals
        bold(g, "DEALS & POPUPS", sx, L.chipsY2 - 14, C_ACCENT);
        String[] l2 = {
                "Deal threshold: " + store.data.dealDropPct + "%",
                "Flip margin: " + store.data.flipPct + "%",
                "Average over: " + windows[store.data.avgWindow % 3],
                "Ignore under: " + store.data.minPrice,
                "Popups: " + (store.data.alerts ? "ON" : "OFF"),
                "Popups for: " + (store.data.alertWatchedOnly ? "watchlist" : "all items"),
                "Popup time: " + store.data.popupSeconds + "s"};
        Runnable[] a2 = {
                () -> store.data.dealDropPct = cycle(store.data.dealDropPct, drops),
                () -> store.data.flipPct = cycle(store.data.flipPct, flips),
                () -> store.data.avgWindow = (store.data.avgWindow + 1) % 3,
                () -> store.data.minPrice = cycle(store.data.minPrice, mins),
                () -> store.data.alerts = !store.data.alerts,
                () -> store.data.alertWatchedOnly = !store.data.alertWatchedOnly,
                () -> store.data.popupSeconds = cycle(store.data.popupSeconds, popups)};
        settingChips(g, sx, L.chipsY2, cols, l2, a2);

        // --- data
        bold(g, "ITEMS & DATA", sx, L.chipsY3 - 14, C_ACCENT);
        String[] l3 = {
                "Listed price: " + (store.data.priceIsPerStack ? "per stack" : "per item"),
                "Tell items apart by lore: " + (store.data.useLore ? "ON" : "OFF"),
                confirmClear ? "Click again to wipe" : "Clear all data"};
        Runnable[] a3 = {
                () -> store.data.priceIsPerStack = !store.data.priceIsPerStack,
                () -> store.data.useLore = !store.data.useLore,
                () -> {
                    if (!confirmClear) confirmClear = true;
                    else {
                        store.clear();
                        confirmClear = false;
                        selectedId = null;
                    }
                }};
        settingChips(g, sx, L.chipsY3, cols, l3, a3);

        // --- server wording (field labels; the boxes themselves are widgets)
        bold(g, "SERVER WORDING", sx, L.fieldsY() - 14, C_ACCENT);
        String[] labels = {
                "Auction House screen title contains", "Orders screen title contains",
                "Command that opens the Auction House", "Command that opens Orders",
                "\"Next page\" button name contains", "Auto-update only on servers containing",
                "Price pattern (regex: group 1 number, group 2 k/m/b)"};
        for (int i = 0; i < 7; i++) {
            int lx = sx + (i % L.fcols()) * (L.fw() + 12);
            int ly = L.fieldsY() + (i / L.fcols()) * 32;
            String lab = labels[i];
            int col = (i == 6 && !store.regexOk) ? C_DOWN : C_DIM;
            if (i == 6 && !store.regexOk) lab = "Invalid pattern, keeping the last good one";
            text(g, lab, lx, ly, col);
        }

        // --- debug
        int cy = L.endY();
        if (cy < bodyY + bodyH - 40) {
            bold(g, "LAST SCREEN THE SCANNER SAW", sx, cy, C_ACCENT);
            cy += 13;
            String seen = store.lastTitle.isEmpty() ? "No container screen opened yet"
                    : "\"" + store.lastTitle + "\"  ->  " + store.lastKind;
            text(g, fit(seen, pw - 40), sx, cy, C_TEXT);
            cy += 12;
            if (store.lastSample.isEmpty()) {
                text(g, "Open the Auction House or Orders; sample item lore lines appear here.", sx, cy, C_DIMMER);
            } else {
                for (String line : store.lastSample) {
                    if (cy > bodyY + bodyH - 14) break;
                    text(g, fit(line, pw - 40), sx, cy, C_DIMMER);
                    cy += 11;
                }
            }
        }
    }

    private void settingChips(GuiGraphicsExtractor g, int sx, int y, int cols, String[] labels, Runnable[] actions) {
        for (int i = 0; i < labels.length; i++) {
            int x = sx + (i % cols) * 188;
            int yy = y + (i / cols) * 20;
            String label = fit(labels[i], 172);
            int w = 180;
            boolean hover = mouseXNow >= x && mouseXNow < x + w && mouseYNow >= yy && mouseYNow < yy + 16;
            g.fill(x, yy, x + w, yy + 16, hover ? 0xFF2D3446 : 0xFF1E2330);
            border(g, x, yy, w, 16, C_BORDER);
            text(g, label, x + 8, yy + 4, C_TEXT);
            final Runnable action = actions[i];
            hits.add(new Hit(x, yy, w, 16, () -> {
                action.run();
                store.markDirty();
            }));
        }
    }
}
