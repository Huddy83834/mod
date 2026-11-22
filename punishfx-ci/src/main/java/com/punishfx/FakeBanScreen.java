package com.punishfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/** A convincing but fake "you are banned" disconnect screen. Clears when the player clicks the button. */
public class FakeBanScreen extends Screen {
    public FakeBanScreen() {
        super(Component.literal("Disconnected"));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Back to title screen"),
                b -> Minecraft.getInstance().setScreen(new TitleScreen()))
                .bounds(this.width / 2 - 100, this.height / 2 + 40, 200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("(it was fake - Punish FX)"),
                b -> Minecraft.getInstance().setScreen(new TitleScreen()))
                .bounds(this.width / 2 - 100, this.height / 2 + 64, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        this.renderBackground(g, mouseX, mouseY, delta);
        super.render(g, mouseX, mouseY, delta);
        g.drawCenteredString(this.font, "Disconnected", this.width / 2, this.height / 2 - 50, 0xFFFFFF);
        g.drawCenteredString(this.font, "You are permanently banned from this server!", this.width / 2, this.height / 2 - 30, 0xFFFF5555);
        g.drawCenteredString(this.font, "Reason: Received a donation", this.width / 2, this.height / 2 - 14, 0xFFBBBBBB);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
