package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * What a Model Core's Compute tab shows, sent to players who have it open.
 *
 * @param threads        threads it trains or would train with
 * @param bytes          what the model takes to train
 * @param flopsPerSecond the pace of the run, or 0 while nothing runs
 * @param flops          flops counted since the weights were last reset
 * @param tokens         tokens read in that time
 */
public record ComputeSync(BlockPos pos, int threads, long bytes, long flopsPerSecond, long flops, long tokens, long parameters) implements CustomPacketPayload {
    public static final Type<ComputeSync> TYPE = new Type<>(Residual.id("compute_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ComputeSync> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ComputeSync::pos,
            ByteBufCodecs.VAR_INT, ComputeSync::threads,
            ByteBufCodecs.VAR_LONG, ComputeSync::bytes,
            ByteBufCodecs.VAR_LONG, ComputeSync::flopsPerSecond,
            ByteBufCodecs.VAR_LONG, ComputeSync::flops,
            ByteBufCodecs.VAR_LONG, ComputeSync::tokens,
            ByteBufCodecs.VAR_LONG, ComputeSync::parameters,
            ComputeSync::new);

    @Override
    public Type<ComputeSync> type() {
        return TYPE;
    }
}
