package com.punishfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/** Presets: one simple list. Use one, copy one, rename yours by typing on the name, delete yours. */
public class PresetsScreen extends PfxFrame {
    private static String status = "";
    private static int statusColor = Ui.GOOD;
    private static long statusUntil = 0;
    private static String confirmDelete = null;

    public PresetsScreen(Screen parent) {
        super(parent, Page.PRESETS, "Presets", "your whole setup, saved - switch in one click");
    }

    private static void say(String s, int color) {
        status = s;
        statusColor = color;
        statusUntil = System.currentTimeMillis() + 4000;
    }

    @Override
    protected void initPage() {
        List<Row> r = new ArrayList<>();
        r.add(new Header("Presets"));
        Presets.Saved current = Presets.lastSaved();
        if (current != null) {
            r.add(new Action("Save changes to \"" + current.name + "\"", "Puts what you have now into that preset", () -> "Save", Ui.GOOD, () -> {
                Presets.update(current);
                say("Saved your changes to " + current.name, Ui.GOOD);
            }));
        }
        r.add(new Action("New preset from what you have now", "Saves every setting as a new preset (rename it after)", () -> "+ New", Ui.GOOD, () -> {
            String n = Presets.saveCurrent(null);
            say("Saved as \"" + n + "\" - click its name to rename it", Ui.GOOD);
            rebuild();
        }));
        // your own presets: type on the name to rename
        for (Presets.Saved s : new ArrayList<>(Presets.saved())) {
            boolean using = s.name.equals(Presets.lastApplied());
            r.add(new Buttons(() -> s.name, v -> Presets.rename(s, v), using ? "You're using this one - type on the name to rename it" : "Type on the name to rename it",
                    List.of(new Btn(() -> using ? "In use" : "Use", using ? Ui.DIM : Ui.GOOD, 46, () -> {
                                Presets.apply(s.data, s.name);
                                say("Now using: " + s.name, Ui.GOOD);
                                rebuild();
                            }),
                            new Btn(() -> "Copy", Ui.THEME2, 40, () -> {
                                String n = Presets.duplicate(s);
                                say("Made a copy: " + n, Ui.GOOD);
                                rebuild();
                            }),
                            new Btn(() -> s.name.equals(confirmDelete) ? "Sure?" : "✖", Ui.THEME, 34, () -> {
                                if (!s.name.equals(confirmDelete)) {
                                    confirmDelete = s.name;
                                    return;
                                }
                                confirmDelete = null;
                                Presets.delete(s);
                                say("Deleted " + s.name, Ui.WARN);
                                rebuild();
                            }))));
        }
        // ready-made ones (can't be renamed or deleted, but can be copied into your list)
        for (Presets.Builtin b : Presets.BUILTIN) {
            boolean using = b.name().equals(Presets.lastApplied());
            r.add(new Buttons(() -> "★ " + b.name(), null, b.desc(),
                    List.of(new Btn(() -> using ? "In use" : "Use", using ? Ui.DIM : Ui.GOOD, 46, () -> {
                                Presets.apply(b);
                                say("Now using: " + b.name(), Ui.GOOD);
                                rebuild();
                            }),
                            new Btn(() -> "Copy", Ui.THEME2, 40, () -> {
                                String n = Presets.duplicate(b);
                                say("Made a copy you can change: " + n, Ui.GOOD);
                                rebuild();
                            }))));
        }
        r.add(new Header("Share with a friend"));
        r.add(new Pair("Share code", "Copy: puts your setup on the clipboard. Paste: adds a friend's code to your presets",
                () -> "Copy", Ui.THEME2, () -> {
                    String code = Presets.exportCode();
                    Minecraft.getInstance().keyboardHandler.setClipboard(code);
                    say("Copied! Send it to a friend", Ui.GOOD);
                }, () -> "Paste", Ui.THEME2, () -> {
                    String name = Presets.importCode(Minecraft.getInstance().keyboardHandler.getClipboard());
                    if (name == null) say("That's not a Punish FX code - copy the whole thing (it starts with PFX1:)", Ui.WARN);
                    else say("Added \"" + name + "\" to your presets", Ui.GOOD);
                    rebuild();
                }));
        list(r, cy + 14, cy + ch);
    }

    @Override
    protected void renderPage(GuiGraphics g, int mouseX, int mouseY) {
        String line;
        int col;
        if (System.currentTimeMillis() < statusUntil) {
            line = status;
            col = statusColor;
        } else {
            String last = Presets.lastApplied();
            line = last.isEmpty() ? "Pick one, or save what you have now as a new preset" : "Using: " + last;
            col = last.isEmpty() ? 0x9A8F94 : 0xC8B4BA;
        }
        Ui.text(g, this.font, line, cx, cy + 2, col, cw);
    }
}
