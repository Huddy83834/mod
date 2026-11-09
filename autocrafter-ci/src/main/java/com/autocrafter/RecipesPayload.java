package com.autocrafter;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: the player's full recipe list as JSON. */
public record RecipesPayload(String json) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RecipesPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(AutoCrafter.MOD_ID, "recipes"));
    public static final StreamCodec<FriendlyByteBuf, RecipesPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.stringUtf8(262144), RecipesPayload::json, RecipesPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
