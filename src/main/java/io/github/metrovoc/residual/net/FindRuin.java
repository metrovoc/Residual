package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A Wayfinder's holder chose a kind of ruin to look for: the one in the main hand, or the off hand. */
public record FindRuin(String kind, boolean mainHand) implements CustomPacketPayload {
    public static final Type<FindRuin> TYPE = new Type<>(Residual.id("find_ruin"));
    public static final StreamCodec<ByteBuf, FindRuin> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, FindRuin::kind,
            ByteBufCodecs.BOOL, FindRuin::mainHand,
            FindRuin::new);

    @Override
    public Type<FindRuin> type() {
        return TYPE;
    }
}
