package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** What the served mind wrote, or why nothing could be asked (a translation key and its arguments). */
public record TerminalAnswer(String prompt, String text, String problem, List<String> args) implements CustomPacketPayload {
    public static final Type<TerminalAnswer> TYPE = new Type<>(Residual.id("terminal_answer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalAnswer> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, TerminalAnswer::prompt,
            ByteBufCodecs.STRING_UTF8, TerminalAnswer::text,
            ByteBufCodecs.STRING_UTF8, TerminalAnswer::problem,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), TerminalAnswer::args,
            TerminalAnswer::new);

    @Override
    public Type<TerminalAnswer> type() {
        return TYPE;
    }
}
