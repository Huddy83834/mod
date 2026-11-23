package com.punishfx.mixin;

import com.punishfx.ControlState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hotbar Chaos: every item in the hotbar is drawn as a random item (your real items are untouched). */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Unique private int punishfx$slot = 0;

    @Inject(method = "renderItemHotbar", at = @At("HEAD"))
    private void punishfx$resetSlot(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        punishfx$slot = 0;
    }

    @ModifyArg(method = "renderItemHotbar", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;renderSlot(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/client/DeltaTracker;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)V"),
            index = 5)
    private ItemStack punishfx$scramble(ItemStack real) {
        if (!ControlState.hotbarChaos || real == null || real.isEmpty()) return real;
        int n = BuiltInRegistries.ITEM.size();
        if (n <= 1) return real;
        long seed = (System.currentTimeMillis() / 1500) * 31 + (punishfx$slot++ % 11) * 7919L + ControlState.hotbarSeed;
        int id = 1 + (int) Math.floorMod(seed * 2654435761L, (long) (n - 1));
        return new ItemStack(BuiltInRegistries.ITEM.byId(id), real.getCount());
    }
}
