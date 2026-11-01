package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A change to a Port's settings from its screen: its mode, how many to stock, its window's priority, or whether it asks for what it lacks. */
public record PortEdit(BlockPos pos, int field, int value) implements CustomPacketPayload {
    public static final int MODE = 0, AMOUNT = 1, PRIORITY = 2, CRAFT = 3, PROGRAMS = 4;

    public static final Type<PortEdit> TYPE = new Type<>(Residual.id("port_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PortEdit> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PortEdit::pos,
            ByteBufCodecs.VAR_INT, PortEdit::field,
            ByteBufCodecs.VAR_INT, PortEdit::value,
            PortEdit::new);

    @Override
    public Type<PortEdit> type() {
        return TYPE;
    }
}
