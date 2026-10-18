package com.lsstocks;

import com.lsstocks.MarketStore.Point;
import com.lsstocks.MarketStore.Rec;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
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
    private static final int C_PANEL = 0xF0121419;
    private static final int C_PANEL_2 = 0xF01A1D26;
    private static final int C_BORDER = 0xFF2C3242;
    private static final int C_ACCENT = 0xFFF5B83D;
    private static final int C_UP = 0xFF3DDC84;
    private static final int C_DOWN = 0xFFFF5C5C;
    private static final int C_TEXT = 0xFFE6E8EE;
    private static final int C_DIM = 0xFF8A90A0;
    private static final int C_ASK = 0xFF4FC3F7;
    private static final int C_BID = 0xFFFFA726;

    private static final int ROW_H = 24;
    private static final int[] DELAYS = {500, 900, 1500, 2500};

    private enum Tab {
        ALL("All Items"), AH("Auction House"), ORDERS("Orders"), FLIPS("Flips"), WATCH("Watchlist"), SETTINGS("Settings");
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

    private record Row(String id, String name, Rec rec, double price, double change, int listings) {}

    /** Every item in the game, built once, so search works even for items with no data yet. */
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
    private final Map<String, ItemStack> icons = new HashMap<>();

    private Tab tab = Tab.ALL;
    private Sort sort = Sort.PRICE;
    private String query = "";
    private String selectedId = null;
    private int scroll = 0;
    private boolean confirmClear = false;

    // layout
    private int px, py, pw, ph, bodyY;
    private int listX, listY, listW, listH;
    private int detX, detY, detW, detH;

    private int mouseXNow, mouseYNow;
    private int refreshTicks;

    public MarketScreen(Screen parent) {
        super(Component.literal("LS Market"));
        this.parent = parent;
    }

    // ------------------------------------------------------------------ setup

    @Override
    protected void init() {
        int m = Math.max(6, Math.min(20, this.width / 40));
        px = m;
        py = m;
        pw = this.width - 2 * m;
        ph = this.height - 2 * m;
        bodyY = py + 78;

        listX = px + 8;
        listY = bodyY;
        listW = (int) ((pw - 24) * 0.44);
        listH = Math.max(ROW_H, (py + ph - 24) - listY);
        detX = listX + listW + 8;
        detY = bodyY;
        detW = px + pw - 8 - detX;
        detH = listH;

        boolean online = Minecraft.getInstance().player != null;

        // top-right action buttons
        int bx = px + pw - 8;
        bx -= 84;
        Button scanAll = Button.builder(Component.literal("Scan all"), b -> Crawler.start(Minecraft.getInstance(), store))
                .bounds(bx, py + 4, 84, 18).build();
        scanAll.active = online;
        addRenderableWidget(scanAll);
        bx -= 4 + 92;
        Button openOrders = Button.builder(Component.literal("Open Orders"), b -> runCommand(store.data.orderCommand))
                .bounds(bx, py + 4, 92, 18).build();
        openOrders.active = online;
        addRenderableWidget(openOrders);
        bx -= 4 + 76;
        Button openAh = Button.builder(Component.literal("Open AH"), b -> runCommand(store.data.ahCommand))
                .bounds(bx, py + 4, 76, 18).build();
        openAh.active = online;
        addRenderableWidget(openAh);

        // tabs
        int x = px + 8;
        int y = py + 24;
        for (Tab t : Tab.values()) {
            int w = this.font.width(t.label) + 18;
            Component label = t == tab
                    ? Component.literal(t.label).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    : Component.literal(t.label);
            addRenderableWidget(Button.builder(label, b -> {
                tab = t;
                scroll = 0;
                confirmClear = false;
                rebuildWidgets();
            }).bounds(x, y, w, 20).build());
            x += w + 4;
        }

        if (tab == Tab.SETTINGS) {
            initSettings();
        } else {
            int cy = py + 50;
            EditBox search = new EditBox(this.font, px + 8, cy, 240, 20, Component.literal("Search"));
            search.setHint(Component.literal(tab == Tab.ALL ? "Search every item and its price..." : "Search items..."));
            search.setValue(query);
            search.setResponder(s -> {
                query = s;
                scroll = 0;
                rebuild();
            });
            addRenderableWidget(search);
            setInitialFocus(search);

            addRenderableWidget(Button.builder(Component.literal("Sort: " + sort.label), b -> {
                sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
                b.setMessage(Component.literal("Sort: " + sort.label));
                scroll = 0;
                rebuild();
            }).bounds(px + 254, cy, 96, 20).build());

            Rec sel = selected();
            boolean watched = sel != null && sel.watched;
            addRenderableWidget(Button.builder(
                    Component.literal(watched ? "★ Watching" : "☆ Watch selected"), b -> {
                        Row r = selectedRow();
                        if (r != null) {
                            Rec rec = store.rec(r.id(), r.name());
                            rec.watched = !rec.watched;
                            store.markDirty();
                            rebuildWidgets();
                        }
                    }).bounds(px + 356, cy, 120, 20).build());
        }
        rebuild();
    }

    private void runCommand(String cmd) {
        String c = cmd.trim();
        if (c.startsWith("/")) c = c.substring(1);
        Minecraft mc = Minecraft.getInstance();
        if (c.isEmpty() || mc.player == null) return;
        mc.gui.setScreen(null);
        mc.player.connection.sendCommand(c);
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
        int colW = Math.min(330, (pw - 48) / 2);
        int lx = px + 16;
        int rx = lx + colW + 16;
        int sy = bodyY + 12;

        box(lx, sy + 12, colW, store.data.priceRegex, 400, s -> store.setRegex(s));
        box(lx, sy + 46, colW, store.data.ahKeywords, 200, s -> {
            store.data.ahKeywords = s;
            store.markDirty();
        });
        box(lx, sy + 80, colW, store.data.orderKeywords, 200, s -> {
            store.data.orderKeywords = s;
            store.markDirty();
        });
        box(rx, sy + 12, colW, store.data.ahCommand, 100, s -> {
            store.data.ahCommand = s;
            store.markDirty();
        });
        box(rx, sy + 46, colW, store.data.orderCommand, 100, s -> {
            store.data.orderCommand = s;
            store.markDirty();
        });
        box(rx, sy + 80, colW, store.data.nextKeywords, 100, s -> {
            store.data.nextKeywords = s;
            store.markDirty();
        });

        int by = sy + 108;
        addRenderableWidget(Button.builder(
                Component.literal("Auto-scan: " + (store.data.autoScan ? "ON" : "OFF")), b -> {
                    store.data.autoScan = !store.data.autoScan;
                    store.markDirty();
                    b.setMessage(Component.literal("Auto-scan: " + (store.data.autoScan ? "ON" : "OFF")));
                }).bounds(lx, by, 120, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Listed price: " + (store.data.priceIsPerStack ? "per stack" : "per item")), b -> {
                    store.data.priceIsPerStack = !store.data.priceIsPerStack;
                    store.markDirty();
                    b.setMessage(Component.literal(
                            "Listed price: " + (store.data.priceIsPerStack ? "per stack" : "per item")));
                }).bounds(lx + 126, by, 150, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Scan speed: " + store.data.scanDelayMs + " ms/page"), b -> {
                    int idx = 0;
                    for (int i = 0; i < DELAYS.length; i++) if (DELAYS[i] == store.data.scanDelayMs) idx = i;
                    store.data.scanDelayMs = DELAYS[(idx + 1) % DELAYS.length];
                    store.markDirty();
                    b.setMessage(Component.literal("Scan speed: " + store.data.scanDelayMs + " ms/page"));
                }).bounds(lx + 282, by, 150, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal(confirmClear ? "Click again to wipe" : "Clear all data"), b -> {
                    if (!confirmClear) {
                        confirmClear = true;
                        b.setMessage(Component.literal("Click again to wipe"));
                    } else {
                        store.clear();
                        confirmClear = false;
                        selectedId = null;
                        b.setMessage(Component.literal("Cleared"));
                    }
                }).bounds(lx + 438, by, 120, 20).build());
    }

    @Override
    public void onClose() {
        store.saveNow();
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void tick() {
        if (++refreshTicks >= 20) {
            refreshTicks = 0;
            if (tab != Tab.SETTINGS) rebuild();
        }
    }

    // ------------------------------------------------------------------ data

    private Row selectedRow() {
        if (selectedId == null) return null;
        for (Row r : rows) if (r.id().equals(selectedId)) return r;
        return null;
    }

    private Rec selected() {
        return selectedId == null ? null : store.data.items.get(selectedId);
    }

    private static boolean hasData(Rec r) {
        return r != null && (!r.ask.isEmpty() || !r.bid.isEmpty());
    }

    private Row simpleRow(String id, String name, Rec r) {
        if (!hasData(r)) return new Row(id, name, r, Double.NaN, Double.NaN, 0);
        boolean useAsk = !r.ask.isEmpty();
        List<Point> s = useAsk ? r.ask : r.bid;
        return new Row(id, name, r, MarketStore.latest(s), MarketStore.changePct(s), useAsk ? r.askListings : r.bidListings);
    }

    private void rebuild() {
        rows.clear();
        String q = query.toLowerCase(Locale.ROOT).trim();

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
            }
        } else {
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
        rows.sort(cmp);

        boolean found = false;
        for (Row r : rows) {
            if (r.id().equals(selectedId)) {
                found = true;
                break;
            }
        }
        if (!found) selectedId = rows.isEmpty() ? null : rows.get(0).id();
        int maxScroll = Math.max(0, rows.size() - visibleRows());
        scroll = Math.max(0, Math.min(scroll, maxScroll));
    }

    private static double nz(double v, double fallback) {
        return Double.isNaN(v) ? fallback : v;
    }

    private int visibleRows() {
        return Math.max(1, listH / ROW_H);
    }

    private ItemStack icon(String id) {
        return icons.computeIfAbsent(id, k -> new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(k))));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (tab != Tab.SETTINGS) {
            double mx = event.x();
            double my = event.y();
            if (mx >= listX && mx < listX + listW && my >= listY && my < listY + visibleRows() * ROW_H) {
                int idx = scroll + (int) ((my - listY) / ROW_H);
                if (idx >= 0 && idx < rows.size()) {
                    selectedId = rows.get(idx).id();
                    rebuildWidgets();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (tab != Tab.SETTINGS && mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            int max = Math.max(0, rows.size() - visibleRows());
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY) * 2));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        mouseXNow = mouseX;
        mouseYNow = mouseY;

        g.text(this.font, Component.literal("LS MARKET").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                px + 10, py + 8, C_ACCENT, true);
        g.fill(px + 8, py + 76, px + pw - 8, py + 77, C_ACCENT);

        if (tab == Tab.SETTINGS) {
            drawSettings(g);
        } else {
            drawList(g, mouseX, mouseY);
            drawDetail(g);
        }
        drawFooter(g);
    }

    private void drawFooter(GuiGraphicsExtractor g) {
        int fy = py + ph - 16;
        boolean online = Minecraft.getInstance().level != null;
        String status = online ? "● Live" : "● Offline - showing saved data";
        g.text(this.font, status, px + 10, fy, online ? C_UP : C_BID, false);
        int items = 0;
        for (Rec r : store.data.items.values()) if (hasData(r)) items++;
        String mid = items + " items with prices";
        g.text(this.font, mid, px + 10 + this.font.width(status) + 16, fy, C_DIM, false);
        String right;
        if (store.lastScanTime > 0) {
            right = "Last scan: " + store.lastKind + " · " + store.lastScanCount + " listings · "
                    + MarketStore.ago(store.lastScanTime);
        } else {
            right = "No scans yet. Use Scan all, or open the Auction House.";
        }
        g.text(this.font, right, px + pw - 10 - this.font.width(right), fy, C_DIM, false);
    }

    private void drawList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        panel(g, listX, listY, listW, listH, C_PANEL);
        if (rows.isEmpty()) {
            String[] msg = switch (tab) {
                case FLIPS -> new String[] {"No flips yet.", "A flip needs prices from both the", "Auction House and Orders."};
                case WATCH -> new String[] {"Nothing on your watchlist.", "Select an item and press Watch."};
                case ALL -> query.isEmpty()
                        ? new String[] {"Type an item name above to search", "every item and see its price.", "Use Scan all to collect prices."}
                        : new String[] {"No items match that search."};
                default -> new String[] {"No data yet.", "Press Scan all, or open the "
                        + (tab == Tab.ORDERS ? "Orders" : "Auction House") + "."};
            };
            int ty = listY + 18;
            for (String s : msg) {
                g.text(this.font, s, listX + listW / 2 - this.font.width(s) / 2, ty, C_DIM, false);
                ty += 12;
            }
            return;
        }

        int vis = visibleRows();
        int sparkW = 44;
        int rightEdge = listX + listW - 10;
        int sparkX = rightEdge - sparkW;
        int changeRight = sparkX - 8;
        int priceRight = changeRight - 52;

        for (int i = 0; i < vis; i++) {
            int idx = scroll + i;
            if (idx >= rows.size()) break;
            Row row = rows.get(idx);
            int ry = listY + i * ROW_H;
            boolean sel = row.id().equals(selectedId);
            boolean hover = mouseX >= listX && mouseX < listX + listW && mouseY >= ry && mouseY < ry + ROW_H;

            if (sel) {
                g.fill(listX + 1, ry, listX + listW - 1, ry + ROW_H, 0x40F5B83D);
                g.fill(listX + 1, ry, listX + 3, ry + ROW_H, C_ACCENT);
            } else if (hover) {
                g.fill(listX + 1, ry, listX + listW - 1, ry + ROW_H, 0x20FFFFFF);
            } else if ((idx & 1) == 1) {
                g.fill(listX + 1, ry, listX + listW - 1, ry + ROW_H, 0x10FFFFFF);
            }

            g.item(icon(row.id()), listX + 8, ry + 4);
            int nameX = listX + 30;
            int nameMax = priceRight - 56 - nameX;
            boolean data = !Double.isNaN(row.price());
            g.text(this.font, fit(row.name(), nameMax), nameX, ry + 4, data ? C_TEXT : C_DIM, true);
            String sub = row.listings() > 0 ? row.listings() + " seen" : row.id().replace("minecraft:", "");
            g.text(this.font, fit(sub, nameMax), nameX, ry + 14, C_DIM, false);

            if (data) {
                String price = MarketStore.fmt(row.price());
                g.text(this.font, price, priceRight - this.font.width(price) + 44, ry + 8, C_TEXT, true);
                String ch = Double.isNaN(row.change()) ? "-" : String.format("%+.1f%%", row.change());
                int chColor = Double.isNaN(row.change()) ? C_DIM : row.change() >= 0 ? C_UP : C_DOWN;
                g.text(this.font, ch, changeRight - this.font.width(ch), ry + 8, chColor, false);
                Rec r = row.rec();
                List<Point> series = tab == Tab.ORDERS ? r.bid : r.ask.isEmpty() ? r.bid : r.ask;
                drawSpark(g, series, sparkX, ry + 5, sparkW, ROW_H - 10, chColor == C_DOWN ? C_DOWN : C_UP);
            } else {
                String none = "no data";
                g.text(this.font, none, rightEdge - this.font.width(none), ry + 8, C_DIM, false);
            }
        }

        if (rows.size() > vis) {
            int trackX = listX + listW - 4;
            int thumbH = Math.max(14, listH * vis / rows.size());
            int thumbY = listY + (listH - thumbH) * scroll / Math.max(1, rows.size() - vis);
            g.fill(trackX, listY + 1, trackX + 2, listY + listH - 1, 0x22FFFFFF);
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, C_ACCENT);
        }
    }

    private void drawDetail(GuiGraphicsExtractor g) {
        panel(g, detX, detY, detW, detH, C_PANEL_2);
        Row sr = selectedRow();
        if (sr == null) {
            String s = "Select an item";
            g.text(this.font, s, detX + detW / 2 - this.font.width(s) / 2, detY + 24, C_DIM, false);
            return;
        }
        Rec r = sr.rec();

        g.item(icon(sr.id()), detX + 10, detY + 8);
        g.text(this.font, Component.literal(sr.name()).withStyle(ChatFormatting.BOLD), detX + 32, detY + 8, C_TEXT, true);
        g.text(this.font, sr.id(), detX + 32, detY + 20, C_DIM, false);

        if (!hasData(r)) {
            int ty = detY + 48;
            g.text(this.font, "No price data for this item yet.", detX + 10, ty, C_TEXT, false);
            g.text(this.font, "Press Scan all, or open the Auction House / Orders and the", detX + 10, ty + 14, C_DIM, false);
            g.text(this.font, "prices are recorded for you automatically.", detX + 10, ty + 26, C_DIM, false);
            return;
        }

        float ask = MarketStore.latest(r.ask);
        float bid = MarketStore.latest(r.bid);
        double chAsk = MarketStore.changePct(r.ask);
        double chBid = MarketStore.changePct(r.bid);

        int tx = detX + 10;
        int ty = detY + 38;
        int tileW = Math.max(60, (detW - 20 - 3 * 6) / 4);
        boolean both = !r.ask.isEmpty() && !r.bid.isEmpty();
        String[][] tiles = {
                {"LOWEST ASK", MarketStore.fmt(ask), pct(chAsk)},
                {"HIGHEST BID", MarketStore.fmt(bid), pct(chBid)},
                {"FLIP MARGIN", both ? MarketStore.fmt(bid - ask) : "-",
                        (both && ask > 0) ? String.format("%+.1f%%", (bid - ask) / ask * 100) : ""},
                {"UPDATED", r.updated > 0 ? MarketStore.ago(r.updated) : "-", ""},
        };
        for (int i = 0; i < tiles.length; i++) {
            int x = tx + i * (tileW + 6);
            g.fill(x, ty, x + tileW, ty + 34, C_PANEL);
            g.fill(x, ty, x + tileW, ty + 1, C_BORDER);
            g.text(this.font, tiles[i][0], x + 6, ty + 5, C_DIM, false);
            g.text(this.font, tiles[i][1], x + 6, ty + 18, C_TEXT, true);
            if (!tiles[i][2].isEmpty()) {
                int c = tiles[i][2].startsWith("-") ? C_DOWN : C_UP;
                g.text(this.font, tiles[i][2], x + tileW - 6 - this.font.width(tiles[i][2]), ty + 18, c, false);
            }
        }

        int ry = ty + 42;
        String range = "Ask range " + MarketStore.fmt(MarketStore.low(r.ask)) + " - " + MarketStore.fmt(MarketStore.high(r.ask))
                + "   avg " + MarketStore.fmt(MarketStore.avg(r.ask))
                + "   |   Bid range " + MarketStore.fmt(MarketStore.low(r.bid)) + " - " + MarketStore.fmt(MarketStore.high(r.bid));
        g.text(this.font, fit(range, detW - 20), detX + 10, ry, C_DIM, false);

        int cx = detX + 48;
        int cy = ry + 18;
        int cw = detW - 48 - 14;
        int ch = detY + detH - 28 - cy;
        if (ch >= 40 && cw >= 60) {
            drawChart(g, r, cx, cy, cw, ch);
        }

        int ly = detY + detH - 16;
        g.fill(detX + 10, ly + 3, detX + 20, ly + 6, C_ASK);
        g.text(this.font, "Auction House ask", detX + 24, ly, C_DIM, false);
        int off = 24 + this.font.width("Auction House ask") + 14;
        g.fill(detX + 10 + off, ly + 3, detX + 20 + off, ly + 6, C_BID);
        g.text(this.font, "Orders bid", detX + 24 + off, ly, C_DIM, false);
    }

    private static String pct(double v) {
        return Double.isNaN(v) ? "" : String.format("%+.1f%%", v);
    }

    private void drawChart(GuiGraphicsExtractor g, Rec r, int x, int y, int w, int h) {
        long t0 = Long.MAX_VALUE;
        long t1 = Long.MIN_VALUE;
        float lo = Float.POSITIVE_INFINITY;
        float hi = Float.NEGATIVE_INFINITY;
        for (List<Point> s : List.of(r.ask, r.bid)) {
            for (Point p : s) {
                t0 = Math.min(t0, p.t);
                t1 = Math.max(t1, p.t);
                lo = Math.min(lo, p.p);
                hi = Math.max(hi, p.p);
            }
        }
        if (t0 == Long.MAX_VALUE) {
            g.text(this.font, "No history yet", x + 8, y + 8, C_DIM, false);
            return;
        }
        float pad = Math.max((hi - lo) * 0.1f, Math.max(0.5f, hi * 0.02f));
        hi += pad;
        lo = Math.max(0, lo - pad);
        float range = hi - lo;
        long span = t1 - t0;

        for (int i = 0; i <= 4; i++) {
            int gy = y + (h - 1) * i / 4;
            g.fill(x, gy, x + w, gy + 1, 0x18FFFFFF);
            String lab = MarketStore.fmt(hi - range * i / 4f);
            g.text(this.font, lab, x - 6 - this.font.width(lab), gy - 4, C_DIM, false);
        }

        int[] hoverPoint = null;
        String hoverText = null;
        int bestDist = 12;

        for (int s = 0; s < 2; s++) {
            List<Point> series = s == 0 ? r.ask : r.bid;
            int color = s == 0 ? C_ASK : C_BID;
            int area = (s == 0 ? 0x224FC3F7 : 0x22FFA726);
            if (series.isEmpty()) continue;
            int prevX = 0;
            int prevY = 0;
            boolean first = true;
            for (Point p : series) {
                int sx = span <= 0 ? x + w / 2 : x + (int) ((p.t - t0) * (w - 1) / span);
                int sy = y + (int) ((hi - p.p) / range * (h - 1));
                if (!first) {
                    drawSegment(g, prevX, prevY, sx, sy, color, area, y + h);
                }
                prevX = sx;
                prevY = sy;
                first = false;
                g.fill(sx - 1, sy - 1, sx + 2, sy + 2, color);

                int dist = Math.abs(sx - mouseXNow);
                if (mouseXNow >= x && mouseXNow <= x + w && mouseYNow >= y && mouseYNow <= y + h && dist < bestDist) {
                    bestDist = dist;
                    hoverPoint = new int[] {sx, sy, color};
                    hoverText = (s == 0 ? "Ask " : "Bid ") + MarketStore.fmt(p.p) + " · " + MarketStore.ago(p.t);
                }
            }
        }

        g.text(this.font, MarketStore.ago(t0), x, y + h + 4, C_DIM, false);
        String now = "now";
        g.text(this.font, now, x + w - this.font.width(now), y + h + 4, C_DIM, false);

        if (hoverPoint != null) {
            g.fill(hoverPoint[0], y, hoverPoint[0] + 1, y + h, 0x55FFFFFF);
            g.fill(hoverPoint[0] - 3, hoverPoint[1] - 3, hoverPoint[0] + 4, hoverPoint[1] + 4, hoverPoint[2]);
            int tw = this.font.width(hoverText) + 10;
            int tx = hoverPoint[0] + 8;
            if (tx + tw > x + w) tx = hoverPoint[0] - 8 - tw;
            int ty = Math.max(y, Math.min(y + h - 16, hoverPoint[1] - 8));
            g.fill(tx, ty, tx + tw, ty + 16, 0xEE0B0C10);
            border(g, tx, ty, tw, 16, C_BORDER);
            g.text(this.font, hoverText, tx + 5, ty + 4, C_TEXT, false);
        }
    }

    private void drawSegment(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color, int area, int floor) {
        if (x2 < x1) {
            int tx = x1;
            x1 = x2;
            x2 = tx;
            int ty = y1;
            y1 = y2;
            y2 = ty;
        }
        int dx = Math.max(1, x2 - x1);
        for (int cx = x1; cx <= x2; cx++) {
            int a = y1 + (y2 - y1) * (cx - x1) / dx;
            int b = y1 + (y2 - y1) * (cx + 1 - x1) / dx;
            int top = Math.min(a, b);
            int bottom = Math.max(a, b);
            if ((area >>> 24) != 0) g.fill(cx, bottom + 1, cx + 1, floor, area);
            g.fill(cx, top, cx + 1, bottom + 1, color);
        }
    }

    private void drawSpark(GuiGraphicsExtractor g, List<Point> series, int x, int y, int w, int h, int color) {
        if (series.size() < 2) {
            g.fill(x, y + h / 2, x + w, y + h / 2 + 1, 0x33FFFFFF);
            return;
        }
        int n = Math.min(series.size(), w / 2);
        List<Point> tail = series.subList(series.size() - n, series.size());
        float lo = Float.POSITIVE_INFINITY;
        float hi = Float.NEGATIVE_INFINITY;
        for (Point p : tail) {
            lo = Math.min(lo, p.p);
            hi = Math.max(hi, p.p);
        }
        float range = Math.max(hi - lo, 0.0001f);
        int prevX = 0;
        int prevY = 0;
        for (int i = 0; i < tail.size(); i++) {
            int sx = x + i * (w - 1) / Math.max(1, tail.size() - 1);
            int sy = y + h - 1 - (int) ((tail.get(i).p - lo) / range * (h - 1));
            if (i > 0) drawSegment(g, prevX, prevY, sx, sy, color, 0x00000000, y + h);
            prevX = sx;
            prevY = sy;
        }
    }

    private void drawSettings(GuiGraphicsExtractor g) {
        int colW = Math.min(330, (pw - 48) / 2);
        int lx = px + 16;
        int rx = lx + colW + 16;
        int sy = bodyY + 12;

        g.text(this.font, "Price pattern (regex: group 1 = number, group 2 = k/m/b)", lx, sy, C_DIM, false);
        if (!store.regexOk) {
            g.text(this.font, "Invalid, keeping last good one", lx, sy + 32 - 10 + 0, C_DOWN, false);
        }
        g.text(this.font, "Auction House screen title contains", lx, sy + 34, C_DIM, false);
        g.text(this.font, "Orders screen title contains", lx, sy + 68, C_DIM, false);
        g.text(this.font, "Command that opens the Auction House", rx, sy, C_DIM, false);
        g.text(this.font, "Command that opens Orders", rx, sy + 34, C_DIM, false);
        g.text(this.font, "\"Next page\" button name contains", rx, sy + 68, C_DIM, false);

        int cy = sy + 140;
        g.text(this.font, Component.literal("What the scanner saw last").withStyle(ChatFormatting.BOLD), lx, cy, C_ACCENT, true);
        cy += 14;
        String seen = store.lastTitle.isEmpty() ? "No container screen opened yet"
                : "Screen \"" + store.lastTitle + "\"  ->  " + store.lastKind;
        g.text(this.font, fit(seen, pw - 40), lx, cy, C_TEXT, false);
        cy += 14;
        if (store.lastSample.isEmpty()) {
            g.text(this.font, "Open the Auction House or Orders; sample item lore lines show up here so you can match them.",
                    lx, cy, C_DIM, false);
        } else {
            for (String line : store.lastSample) {
                if (cy > py + ph - 30) break;
                g.text(this.font, fit(line, pw - 40), lx, cy, C_DIM, false);
                cy += 11;
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, color);
        border(g, x, y, w, h, C_BORDER);
    }

    private static void border(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private String fit(String s, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (this.font.width(s) <= maxWidth) return s;
        String ell = "...";
        int end = s.length();
        while (end > 0 && this.font.width(s.substring(0, end) + ell) > maxWidth) end--;
        return s.substring(0, end) + ell;
    }
}
