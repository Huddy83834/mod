package com.punishfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Presets page: ready-made setups, your own saved ones, and codes to share with friends. */
public class PresetsScreen extends PfxFrame {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH);
    private static String newName = "";
    private static String status = "";
    private static int statusColor = Ui.GOOD;
    private static long statusUntil = 0;
    private static String confirmDelete = null;

    public PresetsScreen(Screen parent) {
        super(parent, Page.PRESETS, "Presets", "switch your whole setup in one click");
    }

    private static void say(String s, int color) {
        status = s;
        statusColor = color;
        statusUntil = System.currentTimeMillis() + 4000;
    }

    @Override
    protected void initPage() {
        List<Row> r = new ArrayList<>();
        r.add(new Header("Ready-made"));
        for (Presets.Builtin b : Presets.BUILTIN) {
            r.add(new Action(b.name(), b.desc(), () -> "Use this", Ui.GOOD, () -> {
                Presets.apply(b);
                say("Now using: " + b.name(), Ui.GOOD);
                rebuild();
            }));
        }
        r.add(new Header("Your presets"));
        if (Presets.saved().isEmpty()) {
            r.add(new Action("Nothing saved yet", "Set things up how you like, then save it below", () -> "-", Ui.DIM, () -> {}));
        }
        for (Presets.Saved s : new ArrayList<>(Presets.saved())) {
            String when = "Saved " + WHEN.format(Instant.ofEpochMilli(s.savedAt).atZone(ZoneId.systemDefault()));
            r.add(new Pair(s.name, when, () -> "Use this", Ui.GOOD, () -> {
                Presets.apply(s.data, s.name);
                say("Now using: " + s.name, Ui.GOOD);
                rebuild();
            }, () -> s.name.equals(confirmDelete) ? "Sure?" : "Delete", Ui.THEME, () -> {
                if (!s.name.equals(confirmDelete)) {
                    confirmDelete = s.name;
                    return;
                }
                confirmDelete = null;
                Presets.delete(s);
                say("Deleted " + s.name, Ui.WARN);
                rebuild();
            }));
        }
        r.add(new Header("Save what you have now"));
        r.add(new Text("Name", "What to call it (same name = update it)", () -> newName, v -> newName = v));
        r.add(new Action("Save as a preset", "Every setting, except how payments are read", () -> "Save", Ui.GOOD, () -> {
            String n = Presets.saveCurrent(newName);
            newName = "";
            say("Saved: " + n, Ui.GOOD);
            rebuild();
        }));
        r.add(new Header("Share with a friend"));
        r.add(new Action("Copy as a code", "Copies your setup - paste it to a friend", () -> "Copy", Ui.THEME2, () -> {
            String code = Presets.exportCode();
            Minecraft.getInstance().keyboardHandler.setClipboard(code);
            say("Copied! (" + code.length() + " characters) Send it to a friend", Ui.GOOD);
        }));
        r.add(new Action("Paste a code", "Copy a friend's code first, then press Paste", () -> "Paste", Ui.THEME2, () -> {
            String name = Presets.importCode(Minecraft.getInstance().keyboardHandler.getClipboard());
            if (name == null) say("That's not a Punish FX code - copy the whole thing (it starts with PFX1:)", Ui.WARN);
            else say("Added \"" + name + "\" to your presets - press Use this to switch to it", Ui.GOOD);
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
            line = last.isEmpty() ? "Pick a setup, or save your own" : "Last used: " + last + "  (changing any setting after that is fine)";
            col = last.isEmpty() ? 0x9A8F94 : 0xC8B4BA;
        }
        Ui.text(g, this.font, line, cx, cy + 2, col, cw);
    }
}
