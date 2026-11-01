package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A player started or stopped holding the key that makes a break take its whole vein. */
public record SuitVein(boolean held) implements CustomPacketPayload {
    public static final Type<SuitVein> TYPE = new Type<>(Residual.id("suit_vein"));
    public static final StreamCodec<ByteBuf, SuitVein> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SuitVein::held,
            SuitVein::new);

    @Override
    public Type<SuitVein> type() {
        return TYPE;
    }
}
