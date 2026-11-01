package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A request from a Terminal's screen, or a Relay Gate's. Those a Terminal makes take a turn at its Model Server, as any
 * other question does, and are answered when it comes; {@code number} and {@code text} say more where a kind needs it.
 */
public record TerminalCall(Kind kind, Optional<UUID> agent, int number, String text, Optional<GlobalPos> where) implements CustomPacketPayload {
    public enum Kind {
        /** How the relay networks stand, whole. */
        MAP,
        /** What the base holds and what its machines are doing. */
        BASE,
        /** The agents the network reaches, and what each is doing. */
        AGENTS,
        /** Hand the program on the Program Card in inventory slot {@code number} to the agent. */
        CARD,
        STOP,
        RUN,
        /** Give the agent the order in {@code text}, written as a program by the served model. */
        ORDER,
        /** Say yes ({@code number} 1) or no to the order the agent read back. */
        ANSWER,
        /** Open the Storage Terminal at {@code where} as if standing at it. */
        STORAGE,
        /** Go to the gate at {@code where}, from anywhere the network reaches. */
        JUMP,
        /** Go from the gate at {@code where} to the gate in {@code text}: its place as dimension;x;y;z. */
        GATE_JUMP,
        /** Name the gate at {@code where} {@code text}. */
        GATE_NAME;
    }

    public static final Type<TerminalCall> TYPE = new Type<>(Residual.id("terminal_call"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalCall> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> Kind.values()[i], Kind::ordinal), TerminalCall::kind,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), TerminalCall::agent,
            ByteBufCodecs.VAR_INT, TerminalCall::number,
            ByteBufCodecs.stringUtf8(400), TerminalCall::text,
            ByteBufCodecs.optional(GlobalPos.STREAM_CODEC), TerminalCall::where,
            TerminalCall::new);

    public static TerminalCall of(Kind kind) {
        return new TerminalCall(kind, Optional.empty(), 0, "", Optional.empty());
    }

    public static TerminalCall to(Kind kind, UUID agent, int number, String text) {
        return new TerminalCall(kind, Optional.of(agent), number, text, Optional.empty());
    }

    public static TerminalCall at(Kind kind, GlobalPos where, String text) {
        return new TerminalCall(kind, Optional.empty(), 0, text, Optional.of(where));
    }

    @Override
    public Type<TerminalCall> type() {
        return TYPE;
    }
}
