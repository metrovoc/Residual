package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A request from a Terminal's screen, or a Relay Gate's; {@code text} says more where a kind needs it.
 */
public record TerminalCall(Kind kind, String text, Optional<GlobalPos> where) implements CustomPacketPayload {
    public enum Kind {
        /** How the relay networks stand, whole. */
        MAP,
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
            ByteBufCodecs.stringUtf8(400), TerminalCall::text,
            ByteBufCodecs.optional(GlobalPos.STREAM_CODEC), TerminalCall::where,
            TerminalCall::new);

    public static TerminalCall of(Kind kind) {
        return new TerminalCall(kind, "", Optional.empty());
    }

    public static TerminalCall at(Kind kind, GlobalPos where, String text) {
        return new TerminalCall(kind, text, Optional.of(where));
    }

    @Override
    public Type<TerminalCall> type() {
        return TYPE;
    }
}
