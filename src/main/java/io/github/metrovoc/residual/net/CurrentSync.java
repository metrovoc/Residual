package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Something running along a line, for players near enough to see it, from one block to another: power along cables, in FE a
 * tick, or a run's work along Interconnect, in the kilobytes a step sends.
 */
public record CurrentSync(BlockPos from, BlockPos to, int count, boolean work) implements CustomPacketPayload {
    public static final Type<CurrentSync> TYPE = new Type<>(Residual.id("current"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CurrentSync> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CurrentSync::from,
            BlockPos.STREAM_CODEC, CurrentSync::to,
            ByteBufCodecs.VAR_INT, CurrentSync::count,
            ByteBufCodecs.BOOL, CurrentSync::work,
            CurrentSync::new);

    @Override
    public Type<CurrentSync> type() {
        return TYPE;
    }
}
