package com.punishfx.mixin;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Language Swap: I18n keeps its own copy of the language, so it has to be swapped too. */
@Mixin(I18n.class)
public interface I18nAccessor {
    @Invoker("setLanguage")
    static void punishfx$setLanguage(Language language) {
        throw new AssertionError();
    }
}
