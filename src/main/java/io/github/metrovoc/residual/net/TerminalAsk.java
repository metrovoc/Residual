package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Text to continue with the model a held Terminal is tuned to. */
public record TerminalAsk(boolean offhand, String prompt) implements CustomPacketPayload {
    public static final Type<TerminalAsk> TYPE = new Type<>(Residual.id("terminal_ask"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalAsk> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, TerminalAsk::offhand,
            ByteBufCodecs.stringUtf8(400), TerminalAsk::prompt,
            TerminalAsk::new);

    @Override
    public Type<TerminalAsk> type() {
        return TYPE;
    }
}
