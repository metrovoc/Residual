package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A button on an agent's screen: give it a job, say something to it, or make where it stands its home. */
public record AgentAction(int entity, Kind kind, int job, String said) implements CustomPacketPayload {
    public enum Kind { JOB, SAY, HOME }

    public static final Type<AgentAction> TYPE = new Type<>(Residual.id("agent_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AgentAction> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AgentAction::entity,
            ByteBufCodecs.idMapper(i -> Kind.values()[i], Kind::ordinal), AgentAction::kind,
            ByteBufCodecs.VAR_INT, AgentAction::job,
            ByteBufCodecs.stringUtf8(200), AgentAction::said,
            AgentAction::new);

    @Override
    public Type<AgentAction> type() {
        return TYPE;
    }
}
