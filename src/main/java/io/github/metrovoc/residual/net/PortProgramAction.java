package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A button on a Port's programs screen, for the card in {@code slot}: show it, write {@code text} on it, stop or run it, a
 * debugger's control ({@code value} holds the move and the step's number), who may use the Port, or back to its filter.
 */
public record PortProgramAction(BlockPos pos, int slot, Kind kind, int value, String text) implements CustomPacketPayload {
    public enum Kind { SELECT, WRITE, STOP, RUN, DEBUG, ACCESS, FILTER }

    public static final Type<PortProgramAction> TYPE = new Type<>(Residual.id("port_program"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PortProgramAction> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PortProgramAction::pos,
            ByteBufCodecs.VAR_INT, PortProgramAction::slot,
            ByteBufCodecs.idMapper(i -> Kind.values()[i], Kind::ordinal), PortProgramAction::kind,
            ByteBufCodecs.VAR_INT, PortProgramAction::value,
            ByteBufCodecs.stringUtf8(8192), PortProgramAction::text,
            PortProgramAction::new);

    @Override
    public Type<PortProgramAction> type() {
        return TYPE;
    }
}
