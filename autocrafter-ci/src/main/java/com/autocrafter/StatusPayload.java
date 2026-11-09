package com.autocrafter;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: is the mod doing anything for this player, how much it has done, and the result found for each recipe ("id*count|..."). */
public record StatusPayload(boolean active, long crafted, long flowed, String resolved) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<StatusPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(AutoCrafter.MOD_ID, "status"));
    public static final StreamCodec<FriendlyByteBuf, StatusPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, StatusPayload::active,
            ByteBufCodecs.VAR_LONG, StatusPayload::crafted,
            ByteBufCodecs.VAR_LONG, StatusPayload::flowed,
            ByteBufCodecs.stringUtf8(32767), StatusPayload::resolved,
            StatusPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
