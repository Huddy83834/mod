package com.punishfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/** Tick exactly which punishments go on the wheel / in the case. */
public class ContentsScreen extends PfxFrame {
    private final Games.Kind kind;

    public ContentsScreen(Screen parent, Games.Kind kind) {
        super(parent, Page.GAMES, kind == Games.Kind.WHEEL ? "On the wheel" : "In the case", "tick what it can land on");
        this.kind = kind;
    }

    @Override
    protected String sectionKey() {
        return "PICK_" + kind;
    }

    private List<String> picks() {
        Config c = Config.get();
        return kind == Games.Kind.WHEEL ? c.wheelPicks : c.casePicks;
    }

    /** Choosing items switches the wheel/case to use your list. */
    private void useMine() {
        Config c = Config.get();
        if (kind == Games.Kind.WHEEL) c.wheelContents = 2;
        else c.caseContents = 2;
    }

    @Override
    protected void initPage() {
        List<Row> r = new ArrayList<>();
        r.add(new Pair("← Back to Wheel & Case", "", () -> "All", Ui.GOOD, () -> {
            picks().clear();
            for (Punishes.P p : Punishes.all()) if (!p.game() && !p.optIn) picks().add(p.id);
            useMine();
            Config.save();
            rebuild();
        }, () -> "None", Ui.THEME, () -> {
            picks().clear();
            useMine();
            Config.save();
            rebuild();
        }));
        for (Punishes.Cat cat : Punishes.Cat.values()) {
            if (cat == Punishes.Cat.GAMES) continue;
            r.add(new Header(cat.label));
            for (Punishes.P p : Punishes.all()) {
                if (p.cat != cat) continue;
                String desc = p.desc + (kind == Games.Kind.CASE ? "  (" + Games.RARITY_NAMES[Math.max(0, Math.min(3, p.severity))] + ")" : "");
                r.add(new Toggle(p.name, desc, () -> picks().contains(p.id), v -> {
                    picks().remove(p.id);
                    if (v) picks().add(p.id);
                    useMine();
                }));
            }
        }
        list(r, cy + 14, cy + ch);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean doubleClick) {
        // the first row's label works as a back button
        if (e.button() == 0 && e.x() >= cx && e.x() < cx + cw / 2 && e.y() >= cy + 14 && e.y() < cy + 32) {
            Config.save();
            Minecraft.getInstance().setScreen(new GamesScreen(parent));
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    protected void renderPage(GuiGraphics g, int mouseX, int mouseY) {
        Config c = Config.get();
        int mode = kind == Games.Kind.WHEEL ? c.wheelContents : c.caseContents;
        int n = picks().size();
        String s = n + (n == 1 ? " picked" : " picked") + (mode == 2 ? "  ·  the " + (kind == Games.Kind.WHEEL ? "wheel" : "case") + " uses this list"
                : "  ·  tick something to use this list");
        Ui.text(g, this.font, n == 0 ? "Nothing picked - it will use every punishment" : s, cx, cy + 2, mode == 2 ? Ui.GOOD : 0x9A8F94, cw);
    }

    @Override
    public void onClose() {
        Config.save();
        Minecraft.getInstance().setScreen(new GamesScreen(parent));
    }
}
