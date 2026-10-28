package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Changes one setting of a machine; the key {@link #NAME} renames it. */
public record MachineEdit(BlockPos pos, String key, String value) implements CustomPacketPayload {
    public static final String NAME = "#name";
    public static final Type<MachineEdit> TYPE = new Type<>(Residual.id("machine_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MachineEdit> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, MachineEdit::pos,
            ByteBufCodecs.stringUtf8(64), MachineEdit::key,
            ByteBufCodecs.stringUtf8(64), MachineEdit::value,
            MachineEdit::new);

    @Override
    public Type<MachineEdit> type() {
        return TYPE;
    }
}
