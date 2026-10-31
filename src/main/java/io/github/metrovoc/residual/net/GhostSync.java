package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The places an agent will still lay blocks in, packed, for players near enough to see its build before it stands. */
public record GhostSync(int agent, long[] places) implements CustomPacketPayload {
    public static final Type<GhostSync> TYPE = new Type<>(Residual.id("ghost"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GhostSync> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GhostSync::agent,
            ByteBufCodecs.LONG_ARRAY, GhostSync::places,
            GhostSync::new);

    @Override
    public Type<GhostSync> type() {
        return TYPE;
    }
}
