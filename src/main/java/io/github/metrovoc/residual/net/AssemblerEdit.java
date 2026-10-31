package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** How many of its result an Assembler is told to keep in its network. */
public record AssemblerEdit(BlockPos pos, int keep) implements CustomPacketPayload {
    public static final Type<AssemblerEdit> TYPE = new Type<>(Residual.id("assembler_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AssemblerEdit> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, AssemblerEdit::pos,
            ByteBufCodecs.VAR_INT, AssemblerEdit::keep,
            AssemblerEdit::new);

    @Override
    public Type<AssemblerEdit> type() {
        return TYPE;
    }
}
