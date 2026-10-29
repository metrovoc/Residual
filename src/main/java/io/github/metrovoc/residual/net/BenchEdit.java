package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.bench.Design;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The design for one spec on a Bench, as a player just left it. */
public record BenchEdit(BlockPos pos, String spec, Design design) implements CustomPacketPayload {
    public static final Type<BenchEdit> TYPE = new Type<>(Residual.id("bench_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BenchEdit> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BenchEdit::pos,
            ByteBufCodecs.stringUtf8(64), BenchEdit::spec,
            Design.STREAM_CODEC, BenchEdit::design,
            BenchEdit::new);

    @Override
    public Type<BenchEdit> type() {
        return TYPE;
    }
}
