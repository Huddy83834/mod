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
        r.add(new Buttons(() -> "Quick pick", null, "Back: return to Wheel & Case.  All: every punishment.  None: clear the list",
                List.of(new Btn(() -> "\u2190 Back", Ui.DIM, 50, () -> {
                            Config.save();
                            Minecraft.getInstance().setScreen(new GamesScreen(parent));
                        }),
                        new Btn(() -> "All", Ui.GOOD, 36, () -> {
                            picks().clear();
                            for (Punishes.P p : Punishes.all()) if (!p.game() && !p.optIn) picks().add(p.id);
                            useMine();
                            Config.save();
                            rebuild();
                        }),
                        new Btn(() -> "None", Ui.THEME, 40, () -> {
                            picks().clear();
                            useMine();
                            Config.save();
                            rebuild();
                        }))));
        boolean wheel = kind == Games.Kind.WHEEL;
        r.add(new Buttons(() -> "Rarity", null, wheel
                ? "Click a punishment's rarity to change it. Rarer = a thinner slice, so it's harder to land on"
                : "Click a punishment's rarity to change it. Rarer = its card's colour and harder to unbox",
                List.of(new Btn(() -> usingRarity() ? "Using: On" : "Using: Off", usingRarity() ? Ui.GOOD : Ui.DIM, 62, () -> {
                            Config c = Config.get();
                            if (wheel) c.wheelRarity = !c.wheelRarity;
                            else c.caseRarity = c.caseRarity == 3 ? 0 : 3;
                            Config.save();
                            rebuild();
                        }),
                        new Btn(() -> "Reset", Ui.THEME, 40, () -> {
                            Config.get().rarities.clear();
                            Config.save();
                            rebuild();
                        }))));
        for (Punishes.Cat cat : Punishes.Cat.values()) {
            if (cat == Punishes.Cat.GAMES) continue;
            r.add(new Header(cat.label));
            for (Punishes.P p : Punishes.all()) {
                if (p.cat != cat) continue;
                int rar = Games.rarityFor(p.id);
                String[] names = wheel ? Games.WHEEL_RARITY_NAMES : Games.RARITY_NAMES;
                int col = wheel ? Games.WHEEL_RARITY_COLORS[rar] : GameRender.rarityColor(rar);
                r.add(new Buttons(() -> p.name, null, p.desc,
                        List.of(new Btn(() -> names[Games.rarityFor(p.id)], col, 76, () -> {
                                    Config c = Config.get();
                                    c.rarities.put(p.id, (Games.rarityFor(p.id) + 1) % names.length);
                                    if (wheel) c.wheelRarity = true;
                                    else c.caseRarity = 3;
                                    Config.save();
                                    rebuild();
                                }),
                                new Btn(() -> picks().contains(p.id) ? "On" : "Off", picks().contains(p.id) ? Ui.GOOD : Ui.DIM, 32, () -> {
                                    boolean was = picks().contains(p.id);
                                    picks().remove(p.id);
                                    if (!was) picks().add(p.id);
                                    useMine();
                                    Config.save();
                                    rebuild();
                                }))));
            }
        }
        list(r, cy + 14, cy + ch);
    }

    /** Whether the wheel / case is using the rarities you pick here. */
    private boolean usingRarity() {
        Config c = Config.get();
        return kind == Games.Kind.WHEEL ? c.wheelRarity : c.caseRarity == 3;
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
