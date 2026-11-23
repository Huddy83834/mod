package com.punishfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/** Goals page: the goals along your bar, what each one does, and where the bar sits. */
public class GoalsScreen extends PfxFrame {
    private static boolean confirmReset = false;
    private int previewH;

    public GoalsScreen(Screen parent) {
        super(parent, Page.GOALS, "Goals", "a bar that fills as money comes in");
    }

    @Override
    protected void initPage() {
        Config c = Config.get();
        previewH = ch >= 200 ? 52 : 42;
        List<String> ids = Goals.actionIds();
        String[] actionNames = new String[ids.size()];
        for (int i = 0; i < ids.size(); i++) actionNames[i] = Goals.actionName(ids.get(i));

        List<Row> r = new ArrayList<>();
        r.add(new Header("Progress"));
        r.add(new Action("Reset progress", "Start the bar from $0 (goals can be hit again)", () -> confirmReset ? "Sure?" : "Reset", Ui.THEME, () -> {
            if (!confirmReset) {
                confirmReset = true;
                return;
            }
            confirmReset = false;
            Goals.reset();
        }));
        r.add(new Toggle("Reset when a stream starts", "Each stream starts the bar from $0", () -> c.goalResetOnStream, v -> c.goalResetOnStream = v));
        r.add(new Action("Pretend $1M came in", "Try it out: fills the bar (in game it celebrates too)", () -> "+ $1M", Ui.WARN,
                () -> Goals.add(1_000_000)));

        List<Config.Goal> goals = c.goals;
        for (int i = 0; i < goals.size(); i++) {
            Config.Goal g = goals.get(i);
            int n = i + 1;
            r.add(new Header("Goal " + n));
            r.add(new Text("Amount", "The bar has to reach this", () -> g.amount > 0 ? Money.full(g.amount) : "", v -> {
                double a = Money.parse(v);
                if (a > 0) g.amount = a;
            }));
            r.add(new Text("What happens", "Shown on the bar, e.g. \"I jump in lava\"", () -> g.text, v -> g.text = v));
            r.add(new Choice("Also do", "Something the mod does for you when it's hit", actionNames,
                    () -> Math.max(0, ids.indexOf(g.action)), v -> g.action = ids.get(v)));
            r.add(new Action("Remove goal " + n, "", () -> "Remove", Ui.THEME, () -> {
                c.goals.remove(g);
                rebuild();
            }));
        }
        if (goals.size() < 12) {
            r.add(new Action("Add a goal", "Up to 12 goals along the bar", () -> "+ Add", Ui.GOOD, () -> {
                double top = Goals.max();
                c.goals.add(new Config.Goal(c.goals.isEmpty() ? 1_000_000 : top * 2, "New goal", ""));
                rebuild();
            }));
        }
        r.add(new Header("The bar"));
        r.add(new Toggle("Show the goal bar", "On your screen while you play", () -> c.goalShow, v -> c.goalShow = v));
        r.add(new Choice("Where", "Across the top or bottom, or up a side", Goals.POS_NAMES, () -> c.goalPos, v -> GoalsHud.place(v)));
        r.add(new Text("Title", "Text at the start of the bar", () -> c.goalTitle, v -> c.goalTitle = v));
        r.add(new Action("Move & resize", "Opens the screen layout editor: drag, resize and right-click every item", () -> "Layout", Ui.GOOD,
                () -> Minecraft.getInstance().setScreen(new LayoutScreen(this)));
        r.add(numbers("Size", "How big the bar is", new double[]{0.5, 0.75, 1.0, 1.25, 1.5, 2.0},
                v -> Math.round(v * 100) + "%", () -> c.goalScale, v -> c.goalScale = v));
        list(r, cy + previewH + 4, cy + ch);
    }

    @Override
    protected void renderPageBackground(GuiGraphics g, int mouseX, int mouseY) {
        Ui.card(g, cx, cy, cw, previewH, Ui.tint(0x0C0A0E, 0.05f));
    }

    @Override
    protected void renderPage(GuiGraphics g, int mouseX, int mouseY) {
        // the bar itself, scaled to fit the preview box
        int[] s = GoalsHud.PIECE.size(this.font, true);
        float fit = Math.min((cw - 12) / (float) s[0], (previewH - 6) / (float) s[1]);
        boolean side = Config.get().goalPos >= 2;
        float x = side ? cx + 6 : cx + (cw - s[0] * fit) / 2f;
        GoalsHud.drawAt(g, this.font, x, cy + (previewH - s[1] * fit) / 2f, fit, true);
        if (side) {
            Config.Goal next = Goals.next();
            String line = next == null ? (Goals.sorted().isEmpty() ? "Add a goal below" : "Every goal reached!")
                    : "Next: " + Money.compact(next.amount) + " " + next.text + "  (" + Money.compact(next.amount - Goals.progress()) + " to go)";
            Ui.text(g, this.font, line, (int) (x + s[0] * fit + 10), cy + previewH / 2 - 4, 0xC8B4BA, cw - (int) (s[0] * fit) - 24);
        }
    }
}
