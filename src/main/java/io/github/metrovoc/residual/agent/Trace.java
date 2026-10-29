package io.github.metrovoc.residual.agent;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A line of what an agent has been doing: a step it started, how it went, which way a check came out, or something
 * that happened to it. {@code times} counts the same line coming round again.
 */
public record Trace(Kind kind, String step, String key, List<String> args, int times) {
    public enum Kind { RUNNING, DONE, FAILED, YES, NO, NOTE }

    public static final StreamCodec<RegistryFriendlyByteBuf, Trace> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> Kind.values()[i], Kind::ordinal), Trace::kind,
            ByteBufCodecs.STRING_UTF8, Trace::step,
            ByteBufCodecs.STRING_UTF8, Trace::key,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Trace::args,
            ByteBufCodecs.VAR_INT, Trace::times,
            Trace::new);

    boolean same(Trace t) {
        return kind == t.kind && step.equals(t.step) && key.equals(t.key) && args.equals(t.args);
    }
}
