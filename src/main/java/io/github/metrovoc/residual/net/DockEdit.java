package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Sets whether a Drive Dock fills its drives from its network rather than emptying them into it. */
public record DockEdit(BlockPos pos, boolean filling) implements CustomPacketPayload {
    public static final Type<DockEdit> TYPE = new Type<>(Residual.id("dock_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DockEdit> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, DockEdit::pos, ByteBufCodecs.BOOL, DockEdit::filling, DockEdit::new);

    @Override
    public Type<DockEdit> type() {
        return TYPE;
    }
}
