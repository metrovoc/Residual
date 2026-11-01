package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A player turned one of their exosuit's abilities on or off, by its place in the list. */
public record SuitEdit(int ability, boolean on) implements CustomPacketPayload {
    public static final Type<SuitEdit> TYPE = new Type<>(Residual.id("suit_edit"));
    public static final StreamCodec<ByteBuf, SuitEdit> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SuitEdit::ability,
            ByteBufCodecs.BOOL, SuitEdit::on,
            SuitEdit::new);

    @Override
    public Type<SuitEdit> type() {
        return TYPE;
    }
}
