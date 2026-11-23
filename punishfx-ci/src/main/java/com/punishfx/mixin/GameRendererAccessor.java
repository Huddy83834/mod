package com.punishfx.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets Punish FX switch on its own post-processing effect (Mirror World), the way vanilla does for spectating a creeper. */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Invoker("setPostEffect")
    void punishfx$setPostEffect(Identifier id);
}
