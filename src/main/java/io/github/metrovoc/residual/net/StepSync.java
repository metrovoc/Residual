package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The step a body near a player is on, told when it changes: as its program writes it, how it stands, and how many other
 * programs the same Port is running. An agent is named by its entity, a Port by its place with an entity of -1. No step is
 * nothing to show.
 */
public record StepSync(int entity, BlockPos pos, String step, int kind, int more) implements CustomPacketPayload {
    public static final Type<StepSync> TYPE = new Type<>(Residual.id("step_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StepSync> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StepSync::entity,
            BlockPos.STREAM_CODEC, StepSync::pos,
            ByteBufCodecs.stringUtf8(128), StepSync::step,
            ByteBufCodecs.VAR_INT, StepSync::kind,
            ByteBufCodecs.VAR_INT, StepSync::more,
            StepSync::new);

    @Override
    public Type<StepSync> type() {
        return TYPE;
    }
}
