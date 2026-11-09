package com.autocrafter;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: is the mod doing anything for this player, and how much it has done. */
public record StatusPayload(boolean active, long crafted, long flowed) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<StatusPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(AutoCrafter.MOD_ID, "status"));
    public static final StreamCodec<FriendlyByteBuf, StatusPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, StatusPayload::active,
            ByteBufCodecs.VAR_LONG, StatusPayload::crafted,
            ByteBufCodecs.VAR_LONG, StatusPayload::flowed,
            StatusPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
