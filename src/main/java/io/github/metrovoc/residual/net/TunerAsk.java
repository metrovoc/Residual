package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** What a player wants two answers to, at a Tuner. */
public record TunerAsk(BlockPos pos, String said) implements CustomPacketPayload {
    public static final Type<TunerAsk> TYPE = new Type<>(Residual.id("tuner_ask"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TunerAsk> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TunerAsk::pos,
            ByteBufCodecs.stringUtf8(64), TunerAsk::said,
            TunerAsk::new);

    @Override
    public Type<TunerAsk> type() {
        return TYPE;
    }
}
