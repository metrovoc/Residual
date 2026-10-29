package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Asks a Bench to check its design for a spec and, if it passes, record it to a photomask. */
public record BenchRecord(BlockPos pos, String spec) implements CustomPacketPayload {
    public static final Type<BenchRecord> TYPE = new Type<>(Residual.id("bench_record"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BenchRecord> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BenchRecord::pos,
            ByteBufCodecs.stringUtf8(64), BenchRecord::spec,
            BenchRecord::new);

    @Override
    public Type<BenchRecord> type() {
        return TYPE;
    }
}
