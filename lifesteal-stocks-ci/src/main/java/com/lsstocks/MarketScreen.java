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
    private static final int C_USUAL = 0xFFB794F6;
    private static final int C_FAIR = 0xFF8FB4FF;

    private static final int ROW_H = 26;
    private static final int LIST_HEAD = 16;
    private static final int[] DELAYS = {500, 900, 1500, 2500};

    private enum Tab {
        ALL("Search", "Search every item and see what it costs"),
        DEALS("Deals", "Items cheaper than they usually sell for"),
        AH("For Sale", "Everything for sale on the Auction House"),
        ORDERS("Buy Orders", "What buyers are offering to pay"),
        FLIPS("Flips", "Buy from the Auction House, sell to an order"),
        WATCH("Watchlist", "Items you are keeping an eye on"),
        SETTINGS("Settings", "");
        final String label;
        final String help;

        Tab(String label, String help) {
            this.label = label;
            this.help = help;
        }
    }

    private enum Sort {
        PRICE("Price"), DROPS("Biggest drops"), RISES("Biggest rises"), NAME("A-Z"), RECENT("Newest data");
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

    /** a / b are the two price columns (meaning depends on the tab), pct is the coloured pill. */
    private record Row(String id, String name, Rec rec, double a, double b, double pct, int listings, double score) {}

    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private record Pt(long t, float p) {}

    private record Verdict(String head, int color, List<String> lines) {}

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

    private Tab tab = Tab.DEALS;
    private Sort sort = Sort.PRICE;
    private Range range = Range.ALL;
    private String query = "";
    private String selectedId = null;
    private int scroll = 0;
    private boolean confirmClear = false;
    private boolean showHelp = false;
    private int seenVersion = -1;
    private boolean wasRunning;
    private int dealCount;
    private String hoverHelp = "";

    // overview cards
    private MarketStore.Deal ovBuy, ovSell, ovFlip;
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
        listW = (int) ((pw - 30) * 0.46);
        detX = listX + listW + 10;
        detW = px + pw - 10 - detX;
    }

    @Override
    protected void init() {
        layout();
        if (tab == Tab.SETTINGS) {
            initSettings();
        } else {
            int sw = Math.max(120, Math.min(240, (int) (pw * 0.3)));
            EditBox search = new EditBox(this.font, px + 12, ctrlY, sw, 18, Component.literal("Search"));
            search.setHint(Component.literal("Search any item..."));
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

    private static float nowAsk(Rec r) {
        return r.askListings > 0 ? MarketStore.latest(r.ask) : Float.NaN;
    }

    private static float nowBid(Rec r) {
        return r.bidListings > 0 ? MarketStore.latest(r.bid) : Float.NaN;
    }

    private static double pctOf(double value, double ref) {
        if (Double.isNaN(value) || Double.isNaN(ref) || ref <= 0) return Double.NaN;
        return (value - ref) / ref * 100.0;
    }

    private Row make(String id, String name, Rec r, Tab t) {
        if (!hasData(r)) return new Row(id, name, r, Double.NaN, Double.NaN, Double.NaN, 0, Double.NaN);
        MarketStore.Market m = store.market(r);
        double usual = m.source() == 0 ? Double.NaN : m.price();
        double ask = nowAsk(r);
        double bid = nowBid(r);
        return switch (t) {
            case ORDERS -> new Row(id, name, r, usual, bid, pctOf(bid, usual), r.bidListings, Double.NaN);
            case FLIPS -> {
                double pct = pctOf(bid, ask);
                yield new Row(id, name, r, ask, bid, pct, r.askListings, pct);
            }
            default -> new Row(id, name, r, usual, ask, pctOf(ask, usual), r.askListings, Double.NaN);
        };
    }

    private void rebuild() {
        seenVersion = store.version;
        rows.clear();
        String q = query.toLowerCase(Locale.ROOT).trim();

        // overview + deal count
        List<MarketStore.Deal> deals = store.deals();
        dealCount = deals.size();
        ovBuy = ovSell = ovFlip = null;
        for (MarketStore.Deal d : deals) {
            if (d.type().equals("BUY") && ovBuy == null) ovBuy = d;
            if (d.type().equals("SELL") && ovSell == null) ovSell = d;
            if (d.type().equals("FLIP") && ovFlip == null) ovFlip = d;
        }
        ovTracked = 0;
        for (Rec r : store.data.items.values()) if (hasData(r)) ovTracked++;

        if (tab == Tab.ALL) {
            if (q.isEmpty()) {
                for (Rec r : store.data.items.values()) {
                    if (hasData(r)) rows.add(make(r.id, r.name, r, Tab.ALL));
                }
            } else {
                int n = 0;
                for (Entry e : catalog()) {
                    if (!e.lower().contains(q) && !e.id().contains(q)) continue;
                    rows.add(make(e.id(), e.name(), store.data.items.get(e.id()), Tab.ALL));
                    if (++n >= 500) break;
                }
                for (Rec r : store.data.items.values()) {
                    if (r.id.indexOf('|') >= 0 && r.name.toLowerCase(Locale.ROOT).contains(q)) {
                        rows.add(make(r.id, r.name, r, Tab.ALL));
                    }
                }
            }
        } else if (tab == Tab.DEALS) {
            dealTypes.clear();
            for (MarketStore.Deal d : deals) {
                Rec r = d.rec();
                if (!q.isEmpty() && !(r.name.toLowerCase(Locale.ROOT).contains(q) || r.id.contains(q))) continue;
                dealTypes.put(r.id, d.type());
                Row row = switch (d.type()) {
                    case "BUY" -> new Row(r.id, r.name, r, d.usual(), d.price(), -d.pct(), r.askListings, d.pct());
                    case "SELL" -> new Row(r.id, r.name, r, d.usual(), d.price(), d.pct(), r.bidListings, d.pct());
                    default -> new Row(r.id, r.name, r, d.price(), d.usual(), d.pct(), r.askListings, d.pct());
                };
                rows.add(row);
            }
        } else if (tab != Tab.SETTINGS) {
            for (Rec r : store.data.items.values()) {
                if (!q.isEmpty() && !(r.name.toLowerCase(Locale.ROOT).contains(q) || r.id.contains(q))) continue;
                switch (tab) {
                    case AH -> {
                        if (Float.isNaN(nowAsk(r))) continue;
                        rows.add(make(r.id, r.name, r, Tab.AH));
                    }
                    case ORDERS -> {
                        if (Float.isNaN(nowBid(r))) continue;
                        rows.add(make(r.id, r.name, r, Tab.ORDERS));
                    }
                    case FLIPS -> {
                        if (Float.isNaN(nowAsk(r)) || Float.isNaN(nowBid(r))) continue;
                        rows.add(make(r.id, r.name, r, Tab.FLIPS));
                    }
                    case WATCH -> {
                        if (!r.watched) continue;
                        rows.add(make(r.id, r.name, r, Tab.AH));
                    }
                    default -> {
                    }
                }
            }
        }

        Comparator<Row> byName = Comparator.comparing((Row r) -> r.name().toLowerCase(Locale.ROOT));
        Comparator<Row> cmp = switch (sort) {
            case PRICE -> Comparator.comparingDouble((Row r) -> nz(r.b(), -1e18)).reversed().thenComparing(byName);
            case DROPS -> Comparator.comparingDouble((Row r) -> nz(r.pct(), 1e9)).thenComparing(byName);
            case RISES -> Comparator.comparingDouble((Row r) -> nz(r.pct(), -1e9)).reversed().thenComparing(byName);
            case NAME -> byName;
            case RECENT -> Comparator.comparingLong((Row r) -> r.rec() == null ? 0L : r.rec().updated).reversed().thenComparing(byName);
        };
        if ((tab == Tab.DEALS || tab == Tab.FLIPS) && sort == Sort.PRICE) {
            cmp = Comparator.comparingDouble((Row r) -> nz(r.score(), -1e9)).reversed().thenComparing(byName);
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
        if (showHelp) {
            showHelp = false;
            return true;
        }
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

    private List<String> wrap(String s, int maxWidth, int maxLines) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String test = line.length() == 0 ? word : line + " " + word;
            if (this.font.width(test) > maxWidth && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        if (out.size() > maxLines) {
            List<String> cut = new ArrayList<>(out.subList(0, maxLines));
            cut.set(maxLines - 1, fit(cut.get(maxLines - 1) + " ...", maxWidth));
            return cut;
        }
        return out;
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
        hoverHelp = "";

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
        if (showHelp) drawHelp(g);
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
            status = "Offline · showing saved prices";
            sc = C_BID;
        }
        int sx = px + 12 + this.font.width("LS MARKET") + 14;
        g.fill(sx, py + 11, sx + 5, py + 16, sc);
        text(g, status, sx + 10, py + 9, sc);

        int y = py + 6;
        int right = px + pw - 10;
        boolean running = Crawler.running();
        String upd = running ? "Stop update" : "Update now";
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
        right -= 6 + chipW("?");
        chip(g, right, y, "?", true, C_FAIR, () -> showHelp = true);
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
        int sw = Math.max(120, Math.min(240, (int) (pw * 0.3)));
        int x = px + 12 + sw + 8;
        x += chip(g, x, ctrlY + 1, "Sort: " + sort.label, true, 0, () -> {
            sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
            scroll = 0;
            rebuild();
        }) + 10;
        int room = px + pw - 12 - x;
        if (room > 60) text(g, fit(tab.help, room), x, ctrlY + 5, C_DIM);
    }

    private void drawOverview(GuiGraphicsExtractor g) {
        int gap = 6;
        int cw = (pw - 20 - 3 * gap) / 4;
        int x = px + 10;
        String upd = store.lastScanTime > 0 ? "read " + MarketStore.ago(store.lastScanTime) : "nothing read yet";
        card(g, x, ovY, cw, "ITEMS TRACKED", ovTracked + " items", upd, C_DIM, null);
        x += cw + gap;
        card(g, x, ovY, cw, "BEST BUY RIGHT NOW", ovBuy == null ? "none" : ovBuy.rec().name,
                ovBuy == null ? "" : "-" + String.format(Locale.ROOT, "%.0f%%", ovBuy.pct()), C_UP,
                ovBuy == null ? null : ovBuy.rec());
        x += cw + gap;
        card(g, x, ovY, cw, "BEST PRICE TO SELL AT", ovSell == null ? "none" : ovSell.rec().name,
                ovSell == null ? "" : "+" + String.format(Locale.ROOT, "%.0f%%", ovSell.pct()), C_UP,
                ovSell == null ? null : ovSell.rec());
        x += cw + gap;
        card(g, x, ovY, cw, "BEST FLIP PROFIT", ovFlip == null ? "none" : ovFlip.rec().name,
                ovFlip == null ? "" : "+" + String.format(Locale.ROOT, "%.0f%%", ovFlip.pct()), C_BID,
                ovFlip == null ? null : ovFlip.rec());
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

    private String[] columnLabels() {
        return switch (tab) {
            case ORDERS -> new String[] {"USUAL PRICE", "BEST ORDER", "VS USUAL"};
            case FLIPS -> new String[] {"BUY AT", "SELL AT", "PROFIT"};
            case DEALS -> new String[] {"USUAL PRICE", "NOW", "DEAL"};
            default -> new String[] {"USUAL PRICE", "CHEAPEST NOW", "VS USUAL"};
        };
    }

    private void drawList(GuiGraphicsExtractor g) {
        panel(g, listX, bodyY, listW, bodyH, C_PANEL);

        int right = listX + listW - 10;
        int pillW = 58;
        int pillRight = right;
        int bRight = pillRight - pillW - 6;
        int aRight = bRight - 66;
        int nameX = listX + 34;
        int nameMax = aRight - 62 - nameX;

        String[] cols = columnLabels();
        g.fill(listX + 1, bodyY + 1, listX + listW - 1, bodyY + LIST_HEAD, 0xFF0D1016);
        text(g, "ITEM · " + rows.size(), nameX, bodyY + 4, C_DIMMER);
        rtext(g, cols[0], aRight, bodyY + 4, C_DIMMER);
        rtext(g, cols[1], bRight, bodyY + 4, C_DIMMER);
        rtext(g, cols[2], pillRight, bodyY + 4, C_DIMMER);

        if (rows.isEmpty()) {
            String[] msg = switch (tab) {
                case FLIPS -> new String[] {"No flips right now.", "A flip needs an item that is both for sale", "and wanted by a buy order."};
                case WATCH -> new String[] {"Nothing on your watchlist.", "Select an item and press Watch."};
                case DEALS -> new String[] {"No deals right now.", "A deal is an item priced well below", "what it usually sells for."};
                case ALL -> query.isEmpty()
                        ? new String[] {"Type an item name above to search", "every item and see its price.", "Press Update now to collect prices."}
                        : new String[] {"No items match that search."};
                default -> new String[] {"Nothing here yet.", "Press Update now to read the market."};
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
            boolean data = hasData(row.rec());
            g.text(this.font, fit(row.name(), nameMax), nameX, ry + 5, data ? C_TEXT : C_DIM, false);
            String sub;
            if (tab == Tab.DEALS) sub = dealLabel(dealTypes.get(row.id()));
            else if (row.listings() > 0) sub = row.listings() + (tab == Tab.ORDERS ? " orders" : " for sale");
            else sub = baseOf(row.id()).replace("minecraft:", "");
            g.text(this.font, fit(sub, nameMax), nameX, ry + 16, C_DIMMER, false);

            if (!data) {
                rtext(g, "no data yet", pillRight, ry + 9, C_DIMMER);
                continue;
            }
            rtext(g, MarketStore.fmt(row.a()), aRight, ry + 9, C_DIM);
            rtext(g, MarketStore.fmt(row.b()), bRight, ry + 9, C_TEXT);

            double pct = row.pct();
            // Green always means "good for you": cheaper than usual when buying, higher when selling.
            boolean buyerSide = tab == Tab.AH || tab == Tab.ALL || tab == Tab.WATCH;
            int col;
            if (Double.isNaN(pct)) col = C_DIMMER;
            else if (tab == Tab.DEALS) col = C_UP;
            else if (buyerSide) col = pct <= 0 ? C_UP : C_DOWN;
            else col = pct >= 0 ? C_UP : C_DOWN;
            String chs = MarketStore.pctText(pct);
            int cwid = this.font.width(chs) + 8;
            int cx = pillRight - cwid;
            if (!Double.isNaN(pct)) g.fill(cx, ry + 6, cx + cwid, ry + 19, withAlpha(col, 0x26));
            g.text(this.font, chs, cx + 4, ry + 9, col, false);
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
            case "BUY" -> "GOOD BUY · cheaper than usual";
            case "SELL" -> "GOOD SELL · orders pay extra";
            default -> "FLIP · buy low, sell to an order";
        };
    }

    // ------------------------------------------------------------------ detail

    private Verdict verdictFor(Rec r) {
        if (!hasData(r)) {
            return new Verdict("NO DATA YET", C_DIM,
                    List.of("Nothing is known about this item yet. Press Update now to read the market."));
        }
        MarketStore.Market m = store.market(r);
        float ask = nowAsk(r);
        float bid = nowBid(r);
        List<String> lines = new ArrayList<>();
        String head;
        int color;
        String est = m.source() == 3 ? "" : " (estimate: not enough sales seen yet)";

        if (m.source() == 0 || Float.isNaN(m.price())) {
            head = "NOT ENOUGH DATA";
            color = C_DIM;
            lines.add("The usual price isn't known yet. Keep the market updating and this fills in as items get bought.");
        } else if (Float.isNaN(ask)) {
            head = "NONE FOR SALE";
            color = C_DIM;
            lines.add("Nobody is selling this right now. It usually sells for about " + MarketStore.fmt(m.price()) + "." + est);
        } else {
            double vs = (ask - m.price()) / m.price() * 100.0;
            String cheapest = MarketStore.fmt(ask);
            String usual = MarketStore.fmt(m.price());
            if (vs <= -store.data.dealDropPct) {
                head = "GOOD BUY";
                color = C_UP;
                lines.add(String.format(Locale.ROOT, "The cheapest one (%s) is %.0f%% below the usual price (%s). Good time to buy.%s",
                        cheapest, -vs, usual, est));
            } else if (vs >= store.data.dealDropPct) {
                head = "OVERPRICED";
                color = C_DOWN;
                lines.add(String.format(Locale.ROOT,
                        "The cheapest one (%s) is %.0f%% above the usual price (%s). Nobody buys at that price, so it's not the real price.%s",
                        cheapest, vs, usual, est));
            } else {
                head = "FAIR PRICE";
                color = C_FAIR;
                lines.add(String.format(Locale.ROOT, "The cheapest one (%s) is close to the usual price (%s).%s", cheapest, usual, est));
            }
        }
        if (!Float.isNaN(bid) && !Float.isNaN(ask) && bid > ask) {
            lines.add(String.format(Locale.ROOT, "Flip: buy at %s and sell to a buy order for %s (+%.0f%%).",
                    MarketStore.fmt(ask), MarketStore.fmt(bid), (bid - ask) / ask * 100.0));
        } else if (!Float.isNaN(bid)) {
            lines.add("A buyer will pay up to " + MarketStore.fmt(bid) + ".");
        }
        return new Verdict(head, color, lines);
    }

    private void drawDetail(GuiGraphicsExtractor g) {
        panel(g, detX, bodyY, detW, bodyH, C_PANEL_2);
        Row sr = selectedRow();
        if (sr == null) {
            String s = "Select an item to see what it's worth";
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

        // action chips (top right)
        int cy = bodyY + 8;
        int right = detX + detW - 12;
        boolean watched = r != null && r.watched;
        String wl = watched ? "★ Watching" : "☆ Watch";
        right -= chipW(wl);
        chip(g, right, cy, wl, true, watched ? C_ACCENT : 0, () -> {
            Row cur = selectedRow();
            if (cur != null) {
                Rec rec = store.rec(cur.id(), cur.name());
                rec.watched = !rec.watched;
                store.markDirty();
                store.version++;
            }
        });
        if (r != null && r.askPage > 0) {
            final Rec fr = r;
            String lab = "Go to AH page " + r.askPage;
            right -= 6 + chipW(lab);
            chip(g, right, cy, lab, online, C_ASK, () -> openListingPage(fr, false));
        }

        // verdict banner
        Verdict v = verdictFor(r);
        int vy = bodyY + 34;
        List<String> vl = new ArrayList<>();
        for (String line : v.lines()) vl.addAll(wrap(line, detW - 40, 2));
        if (vl.size() > 3) vl = new ArrayList<>(vl.subList(0, 3));
        int vh = 20 + vl.size() * 11;
        g.fill(detX + 10, vy, detX + detW - 10, vy + vh, withAlpha(v.color(), 0x1C));
        g.fill(detX + 10, vy, detX + 13, vy + vh, v.color());
        bold(g, v.head(), detX + 20, vy + 5, v.color());
        for (int i = 0; i < vl.size(); i++) text(g, vl.get(i), detX + 20, vy + 17 + i * 11, C_TEXT);

        if (!hasData(r)) return;

        // stat cards
        MarketStore.Market m = store.market(r);
        float ask = nowAsk(r);
        float bid = nowBid(r);
        boolean both = !Float.isNaN(ask) && !Float.isNaN(bid);
        int cardY = vy + vh + 8;
        int gap = 6;
        int cw = (detW - 24 - 3 * gap) / 4;
        int cx = dx;
        String src = switch (m.source()) {
            case 3 -> "from " + m.sales() + " sales";
            case 2 -> "from price history";
            case 1 -> "estimate only";
            default -> "no data yet";
        };
        statCard(g, cx, cardY, cw, "USUAL PRICE", m.source() == 0 ? "-" : MarketStore.fmt(m.price()), src, C_USUAL, C_DIM);
        cx += cw + gap;
        statCard(g, cx, cardY, cw, "CHEAPEST LISTING", Float.isNaN(ask) ? "-" : MarketStore.fmt(ask),
                r.askListings > 0 ? r.askListings + " for sale" : "none for sale", C_ASK, C_DIM);
        cx += cw + gap;
        statCard(g, cx, cardY, cw, "BEST BUY ORDER", Float.isNaN(bid) ? "-" : MarketStore.fmt(bid),
                r.bidListings > 0 ? r.bidListings + " orders" : "no orders", C_BID, C_DIM);
        cx += cw + gap;
        statCard(g, cx, cardY, cw, "FLIP PROFIT", both && bid > ask ? MarketStore.fmt(bid - ask) : "-",
                both && ask > 0 ? MarketStore.pctText((bid - ask) / ask * 100) : "needs both", C_UP, C_DIM);

        // chart toolbar
        int tbY = cardY + 46;
        int lx = dx;
        lx = legend(g, lx, tbY, "Cheapest", C_ASK);
        lx = legend(g, lx, tbY, "Orders", C_BID);
        legend(g, lx, tbY, "Usual", C_USUAL);

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
        int chartH = bodyY + bodyH - 26 - chartY;
        if (chartH >= 44 && chartW >= 80) {
            drawChart(g, r, chartX, chartY, chartW, chartH);
        } else {
            text(g, "Enlarge the window to see the chart", dx, chartY + 6, C_DIMMER);
        }
    }

    private int legend(GuiGraphicsExtractor g, int x, int y, String label, int color) {
        g.fill(x, y + 3, x + 10, y + 5, color);
        text(g, label, x + 14, y, C_DIM);
        return x + 14 + this.font.width(label) + 12;
    }

    private void statCard(GuiGraphicsExtractor g, int x, int y, int w, String label, String value, String sub, int valueColor, int subColor) {
        g.fill(x, y, x + w, y + 38, C_PANEL);
        border(g, x, y, w, 38, C_BORDER);
        g.fill(x, y, x + w, y + 2, valueColor);
        text(g, fit(label, w - 10), x + 7, y + 6, C_DIMMER);
        bold(g, fit(value, w - 12), x + 7, y + 17, C_TEXT);
        int sc = sub.startsWith("-") || sub.startsWith("<") ? C_DOWN : sub.startsWith("+") || sub.startsWith(">") ? C_UP : subColor;
        text(g, fit(sub, w - 12), x + 7, y + 28, sc);
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
        for (List<Point> s : List.of(r.ask, r.bid, r.mkt)) {
            for (Point p : MarketStore.view(s)) first = Math.min(first, p.t);
        }
        long t1 = now;
        long t0 = range == Range.ALL ? (first == Long.MAX_VALUE ? now - 60_000L : first) : now - range.ms;
        if (t1 - t0 < 60_000L) t0 = t1 - 60_000L;

        List<Pt> ask = windowOf(r.ask, t0, t1);
        List<Pt> bid = windowOf(r.bid, t0, t1);
        List<Pt> usual = windowOf(r.mkt, t0, t1);
        if (ask.isEmpty() && bid.isEmpty() && usual.isEmpty()) {
            text(g, "No data in this range", x + 8, y + 8, C_DIM);
            return;
        }

        double lo = Double.POSITIVE_INFINITY;
        double hi = Double.NEGATIVE_INFINITY;
        for (List<Pt> s : List.of(ask, bid, usual)) {
            for (Pt p : s) {
                lo = Math.min(lo, p.p());
                hi = Math.max(hi, p.p());
            }
        }
        if (hi - lo < 1e-9) hi = lo + Math.max(1, Math.abs(lo) * 0.1);
        int maxTicks = Math.max(3, Math.min(7, h / 34));
        double step = niceNum(niceNum(hi - lo, false) / (maxTicks - 1), true);
        double nlo = Math.floor(lo / step) * step;
        double nhi = Math.ceil(hi / step) * step;
        if (nhi - nlo < 1e-9) nhi = nlo + step;

        for (double v = nlo; v <= nhi + step * 0.01; v += step) {
            int gy = y + (int) ((nhi - v) / (nhi - nlo) * (h - 1));
            g.fill(x, gy, x + w, gy + 1, 0x16FFFFFF);
            String lab = MarketStore.fmt(v);
            g.text(this.font, lab, x - 6 - this.font.width(lab), gy - 4, C_DIMMER, false);
        }
        g.fill(x, y + h, x + w, y + h + 1, C_BORDER);

        int[] askY = columns(ask, t0, t1, y, w, h, nlo, nhi);
        int[] bidY = columns(bid, t0, t1, y, w, h, nlo, nhi);
        int[] usualY = columns(usual, t0, t1, y, w, h, nlo, nhi);
        drawSeries(g, askY, x, y, w, h, C_ASK, 0x184FC3F7);
        drawSeries(g, bidY, x, y, w, h, C_BID, 0x00000000);
        drawSeries(g, usualY, x, y, w, h, C_USUAL, 0x00000000);

        String left = MarketStore.span(t1 - t0) + " ago";
        text(g, left, x, y + h + 5, C_DIMMER);
        String mid = MarketStore.span((t1 - t0) / 2) + " ago";
        text(g, mid, x + w / 2 - this.font.width(mid) / 2, y + h + 5, C_DIMMER);
        rtext(g, "now", x + w, y + h + 5, C_DIMMER);

        // last-value tags on the right edge, nudged apart so they never overlap
        int tagX = x + w + 5;
        List<int[]> tags = new ArrayList<>();
        List<String> tagLabels = new ArrayList<>();
        if (askY != null) {
            tags.add(new int[] {askY[w - 1], C_ASK});
            tagLabels.add(MarketStore.fmt(ask.get(ask.size() - 1).p()));
        }
        if (bidY != null) {
            tags.add(new int[] {bidY[w - 1], C_BID});
            tagLabels.add(MarketStore.fmt(bid.get(bid.size() - 1).p()));
        }
        if (usualY != null) {
            tags.add(new int[] {usualY[w - 1], C_USUAL});
            tagLabels.add(MarketStore.fmt(usual.get(usual.size() - 1).p()));
        }
        Integer[] order = new Integer[tags.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, Comparator.comparingInt(i -> tags.get(i)[0]));
        int lastY = Integer.MIN_VALUE;
        for (Integer oi : order) {
            int ty = tags.get(oi)[0];
            if (ty < lastY + 13) ty = lastY + 13;
            lastY = ty;
            priceTag(g, tagX, ty, y, h, tagLabels.get(oi), tags.get(oi)[1]);
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
                lines.add("Cheapest  " + MarketStore.fmt(interp(ask, t)));
                colors.add(C_ASK);
            }
            if (bidY != null) {
                g.fill(mouseXNow - 2, bidY[c] - 2, mouseXNow + 3, bidY[c] + 3, C_BID);
                lines.add("Orders  " + MarketStore.fmt(interp(bid, t)));
                colors.add(C_BID);
            }
            if (usualY != null) {
                g.fill(mouseXNow - 2, usualY[c] - 2, mouseXNow + 3, usualY[c] + 3, C_USUAL);
                lines.add("Usual  " + MarketStore.fmt(interp(usual, t)));
                colors.add(C_USUAL);
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
    private int[] columns(List<Pt> pts, long t0, long t1, int y, int w, int h, double nlo, double nhi) {
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
        boolean fillArea = (area >>> 24) != 0;
        for (int c = 0; c < w; c++) {
            int y0 = ys[c];
            int y1 = c + 1 < w ? ys[c + 1] : y0;
            int top = Math.min(y0, y1);
            int bot = Math.max(y0, y1);
            if (fillArea && bot + 2 < y + h) g.fill(x + c, bot + 2, x + c + 1, y + h, area);
            g.fill(x + c, top, x + c + 1, bot + 2, color);
        }
    }

    private void priceTag(GuiGraphicsExtractor g, int x, int yy, int chartY, int chartH, String label, int color) {
        int tw = this.font.width(label) + 8;
        int ty = Math.max(chartY, Math.min(chartY + chartH - 11, yy - 5));
        g.fill(x, ty, x + tw, ty + 11, color);
        g.text(this.font, label, x + 4, ty + 2, 0xFF0A0C11, false);
    }

    // ------------------------------------------------------------------ help + footer

    private void drawHelp(GuiGraphicsExtractor g) {
        int w = Math.min(520, pw - 40);
        String[][] items = {
                {"USUAL PRICE", "What the item normally sells for. It is worked out from listings that disappear from the Auction House (someone bought them)."},
                {"OVERPRICED LISTINGS", "A listing nobody buys says nothing about the price. If a cheaper listing is still there, pricier ones are ignored."},
                {"CHEAPEST LISTING", "The lowest price for sale right now."},
                {"BEST BUY ORDER", "The highest price a buyer is offering to pay right now."},
                {"GOOD BUY", "The cheapest listing is much lower than the usual price."},
                {"FLIP", "Buy from the Auction House, then sell to a buy order for more."},
                {"GREEN / RED", "Green is good for you: cheaper than usual when buying, higher than usual when selling."},
                {"UPDATING", "Prices refresh by themselves (see Settings). It only runs when no menu is open and you haven't been hurt."},
        };
        int h = 34;
        List<List<String>> wrapped = new ArrayList<>();
        for (String[] it : items) {
            List<String> ls = wrap(it[1], w - 24, 3);
            wrapped.add(ls);
            h += 14 + ls.size() * 11 + 4;
        }
        int x = px + (pw - w) / 2;
        int y = py + Math.max(10, (ph - h) / 2);
        g.fill(0, 0, this.width, this.height, 0x90000000);
        panel(g, x, y, w, h, 0xFF10131A);
        g.fill(x, y, x + w, y + 2, C_FAIR);
        bold(g, "HOW TO READ THIS", x + 12, y + 10, C_FAIR);
        int ty = y + 28;
        for (int i = 0; i < items.length; i++) {
            bold(g, items[i][0], x + 12, ty, C_ACCENT);
            ty += 12;
            for (String line : wrapped.get(i)) {
                text(g, line, x + 12, ty, C_TEXT);
                ty += 11;
            }
            ty += 6;
        }
        rtext(g, "Click anywhere to close", x + w - 12, y + 10, C_DIMMER);
    }

    private void drawFooter(GuiGraphicsExtractor g) {
        long now = System.currentTimeMillis();
        String left;
        if (tab == Tab.SETTINGS && !hoverHelp.isEmpty()) {
            left = hoverHelp;
        } else {
            left = store.lastScanTime > 0
                    ? "Last read: " + store.lastKind + " · " + store.lastScanCount + " listings · " + MarketStore.ago(store.lastScanTime)
                    : "Nothing read yet";
        }
        String right;
        if (Crawler.running()) {
            right = "Updating now...";
        } else if (store.data.autoUpdate) {
            long next = store.data.lastFullUpdate + store.data.autoMinutes * 60_000L;
            right = now >= next ? "Auto-update due" : "Next auto-update in " + MarketStore.span(next - now);
        } else {
            right = "Auto-update off";
        }
        int rw = this.font.width(right);
        text(g, fit(left, pw - 36 - rw), px + 12, footY + 4, tab == Tab.SETTINGS && !hoverHelp.isEmpty() ? C_TEXT : C_DIM);
        rtext(g, right, px + pw - 12, footY + 4, C_DIM);
    }

    // ------------------------------------------------------------------ settings

    private record SettingsLayout(int cols, int chipsY1, int chipsY2, int chipsY3, int fieldsY, int fcols, int fw, int endY) {}

    private SettingsLayout settingsLayout() {
        int cols = Math.max(1, Math.min(4, (pw - 28) / 218));
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
        String[] windows = {"all history", "the last 24 hours", "the last 7 days"};

        bold(g, "UPDATING", sx, L.chipsY1 - 14, C_ACCENT);
        String[] l1 = {
                "Update by itself: " + (store.data.autoUpdate ? "ON" : "OFF"),
                "Update every: " + store.data.autoMinutes + " min",
                "Time per page: " + store.data.scanDelayMs + " ms"};
        String[] h1 = {
                "Reads the Auction House and Orders on its own. Only when no menu is open and you haven't just been hurt.",
                "How long to wait between updates. Each update takes as long as it takes to turn every page.",
                "How long it waits on each page before turning it. Slower is gentler on the server."};
        Runnable[] a1 = {
                () -> store.data.autoUpdate = !store.data.autoUpdate,
                () -> store.data.autoMinutes = cycle(store.data.autoMinutes, autoMins),
                () -> {
                    int idx = 0;
                    for (int i = 0; i < DELAYS.length; i++) if (DELAYS[i] == store.data.scanDelayMs) idx = i;
                    store.data.scanDelayMs = DELAYS[(idx + 1) % DELAYS.length];
                }};
        settingChips(g, sx, L.chipsY1, cols, l1, h1, a1);

        bold(g, "DEALS & POPUPS", sx, L.chipsY2 - 14, C_ACCENT);
        String[] l2 = {
                "A deal is: " + store.data.dealDropPct + "% off usual",
                "A flip is: " + store.data.flipPct + "% profit",
                "Usual price uses: " + windows[store.data.avgWindow % 3],
                "Ignore items under: " + store.data.minPrice,
                "Deal popups: " + (store.data.alerts ? "ON" : "OFF"),
                "Popups for: " + (store.data.alertWatchedOnly ? "watchlist only" : "all items"),
                "Popup stays: " + store.data.popupSeconds + " s"};
        String[] h2 = {
            "How much cheaper than the usual price an item must be to count as a good buy.",
            "How much profit buying from the Auction House and selling to a buy order must make to count as a flip.",
            "Which sales are used to work out the usual price. Shorter windows react faster.",
            "Skip cheap items so junk doesn't fill the Deals list.",
            "Show a card on screen when a new deal appears.",
            "Only get popups for items on your watchlist.",
            "How long a deal popup stays on screen."};
        Runnable[] a2 = {
                () -> store.data.dealDropPct = cycle(store.data.dealDropPct, drops),
                () -> store.data.flipPct = cycle(store.data.flipPct, flips),
                () -> store.data.avgWindow = (store.data.avgWindow + 1) % 3,
                () -> store.data.minPrice = cycle(store.data.minPrice, mins),
                () -> store.data.alerts = !store.data.alerts,
                () -> store.data.alertWatchedOnly = !store.data.alertWatchedOnly,
                () -> store.data.popupSeconds = cycle(store.data.popupSeconds, popups)};
        settingChips(g, sx, L.chipsY2, cols, l2, h2, a2);

        bold(g, "ITEMS & DATA", sx, L.chipsY3 - 14, C_ACCENT);
        String[] l3 = {
                "Listed price is for: " + (store.data.priceIsPerStack ? "the whole stack" : "one item"),
                "Split items by lore: " + (store.data.useLore ? "ON" : "OFF"),
                confirmClear ? "Click again to erase" : "Erase all saved data"};
        String[] h3 = {
                "Whether the price on a listing is for the whole stack or for each item in it.",
                "Treat items with different description text as different items (for custom enchants).",
                "Deletes every saved price and sale. This cannot be undone."};
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
        settingChips(g, sx, L.chipsY3, cols, l3, h3, a3);

        bold(g, "HOW THE SERVER WORDS THINGS", sx, L.fieldsY() - 14, C_ACCENT);
        String[] labels = {
                "Auction House window title contains", "Orders window title contains",
                "Command that opens the Auction House", "Command that opens Orders",
                "Next-page button name contains", "Auto-update only on servers containing",
                "Price pattern (advanced)"};
        for (int i = 0; i < 7; i++) {
            int lx = sx + (i % L.fcols()) * (L.fw() + 12);
            int ly = L.fieldsY() + (i / L.fcols()) * 32;
            String lab = labels[i];
            int col = (i == 6 && !store.regexOk) ? C_DOWN : C_DIM;
            if (i == 6 && !store.regexOk) lab = "Invalid pattern, keeping the last good one";
            text(g, lab, lx, ly, col);
        }

        int cy = L.endY();
        if (cy < bodyY + bodyH - 40) {
            bold(g, "LAST WINDOW THE MOD SAW", sx, cy, C_ACCENT);
            cy += 13;
            String seen = store.lastTitle.isEmpty() ? "No window opened yet"
                    : "\"" + store.lastTitle + "\"  ->  " + store.lastKind;
            text(g, fit(seen, pw - 40), sx, cy, C_TEXT);
            cy += 12;
            if (store.lastSample.isEmpty()) {
                text(g, "Open the Auction House or Orders; sample item text appears here.", sx, cy, C_DIMMER);
            } else {
                for (String line : store.lastSample) {
                    if (cy > bodyY + bodyH - 14) break;
                    text(g, fit(line, pw - 40), sx, cy, C_DIMMER);
                    cy += 11;
                }
            }
        }
    }

    private void settingChips(GuiGraphicsExtractor g, int sx, int y, int cols, String[] labels, String[] helps, Runnable[] actions) {
        for (int i = 0; i < labels.length; i++) {
            int x = sx + (i % cols) * 218;
            int yy = y + (i / cols) * 20;
            int w = 210;
            String label = fit(labels[i], w - 14);
            boolean hover = mouseXNow >= x && mouseXNow < x + w && mouseYNow >= yy && mouseYNow < yy + 16;
            if (hover) hoverHelp = helps[i];
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
