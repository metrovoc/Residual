package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A flight the model on an exosuit's board just flew, as its line, for its wearer's screen to play back. */
public record SuitFlight(String line) implements CustomPacketPayload {
    public static final Type<SuitFlight> TYPE = new Type<>(Residual.id("suit_flight"));
    public static final StreamCodec<ByteBuf, SuitFlight> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SuitFlight::line,
            SuitFlight::new);

    @Override
    public Type<SuitFlight> type() {
        return TYPE;
    }
}
