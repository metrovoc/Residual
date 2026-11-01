package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** What a Terminal's call about agents came back with, or why it did not: how many ticks it waited, and every agent in reach. */
public record TerminalAgentsSync(int waited, String problem, List<Row> agents) implements CustomPacketPayload {
    /** An agent: its program, whether that is stopped, its status (a translation key and arguments) and an order it read back. */
    public record Row(UUID id, String name, GlobalPos at, String program, boolean stopped, String status, List<String> args, String proposed) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Row> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Row::id,
                ByteBufCodecs.STRING_UTF8, Row::name,
                GlobalPos.STREAM_CODEC, Row::at,
                ByteBufCodecs.STRING_UTF8, Row::program,
                ByteBufCodecs.BOOL, Row::stopped,
                ByteBufCodecs.STRING_UTF8, Row::status,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Row::args,
                ByteBufCodecs.STRING_UTF8, Row::proposed,
                Row::new);
    }

    public static final Type<TerminalAgentsSync> TYPE = new Type<>(Residual.id("terminal_agents"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalAgentsSync> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TerminalAgentsSync::waited,
            ByteBufCodecs.STRING_UTF8, TerminalAgentsSync::problem,
            Row.STREAM_CODEC.apply(ByteBufCodecs.list()), TerminalAgentsSync::agents,
            TerminalAgentsSync::new);

    @Override
    public Type<TerminalAgentsSync> type() {
        return TYPE;
    }
}
