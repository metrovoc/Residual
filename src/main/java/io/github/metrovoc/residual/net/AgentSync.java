package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.agent.Trace;
import io.github.metrovoc.residual.model.ModelInfo;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

/**
 * Everything an agent's screen shows, sent to players who have it open.
 *
 * @param job     the job its program is, or -1 for a program of its own
 * @param checked decisions whose right answer was known: what a perfect model would have answered
 * @param right   checked decisions that got it right
 * @param served  its model is served by a Model Server rather than run on board
 * @param filter  the model shown is its filter, which made its last decision
 * @param problem why it has no model to ask (a translation key), or empty
 * @param asking  it has read an order back as a program and waits for a yes
 */
public record AgentSync(int entity, int job, String program, boolean stopped, List<Trace> trace, String status,
                             List<String> statusArgs, String prompt, String answer, float confidence, int decisions,
                             int checked, int right, boolean served, boolean filter, String problem, List<String> problemArgs,
                             @Nullable ModelInfo model, int marks, boolean asking)
        implements CustomPacketPayload {
    public static final Type<AgentSync> TYPE = new Type<>(Residual.id("agent_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AgentSync> STREAM_CODEC = StreamCodec.of(AgentSync::write, AgentSync::read);

    private static void write(RegistryFriendlyByteBuf buf, AgentSync s) {
        buf.writeVarInt(s.entity);
        buf.writeVarInt(s.job);
        buf.writeUtf(s.program);
        buf.writeBoolean(s.stopped);
        Trace.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, s.trace);
        buf.writeUtf(s.status);
        buf.writeCollection(s.statusArgs, (b, a) -> b.writeUtf(a));
        buf.writeUtf(s.prompt);
        buf.writeUtf(s.answer);
        buf.writeFloat(s.confidence);
        buf.writeVarInt(s.decisions);
        buf.writeVarInt(s.checked);
        buf.writeVarInt(s.right);
        buf.writeBoolean(s.served);
        buf.writeBoolean(s.filter);
        buf.writeUtf(s.problem);
        buf.writeCollection(s.problemArgs, (b, a) -> b.writeUtf(a));
        ByteBufCodecs.optional(ModelInfo.STREAM_CODEC).encode(buf, Optional.ofNullable(s.model));
        buf.writeVarInt(s.marks);
        buf.writeBoolean(s.asking);
    }

    private static AgentSync read(RegistryFriendlyByteBuf buf) {
        return new AgentSync(buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readBoolean(),
                Trace.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf), buf.readUtf(), buf.readList(b -> b.readUtf()),
                buf.readUtf(), buf.readUtf(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                buf.readBoolean(), buf.readUtf(), buf.readList(b -> b.readUtf()),
                ByteBufCodecs.optional(ModelInfo.STREAM_CODEC).decode(buf).orElse(null), buf.readVarInt(), buf.readBoolean());
    }

    @Override
    public Type<AgentSync> type() {
        return TYPE;
    }
}
