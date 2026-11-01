package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.agent.Debug;
import io.github.metrovoc.residual.agent.Program;
import io.github.metrovoc.residual.agent.ProgramView;
import io.github.metrovoc.residual.agent.Trace;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * What a program's debugger shows, sent to players with its body's screen open: of an agent by its entity id, or of a card
 * on a Port by the Port's place and the card's slot.
 *
 * @param paused it holds at {@code at}, the number of a step in its listing
 * @param breaks the numbers of the steps it stops at
 * @param seen   every variable it holds
 * @param watched what each watched expression works out to
 * @param trace  on a Port, what it has been doing; an agent's comes with its own sync
 */
public record ProgramSync(int entity, BlockPos pos, int slot, boolean paused, int at, List<Integer> breaks, List<Value> seen, List<Value> watched,
                          List<Trace> trace, String status, List<String> statusArgs) implements CustomPacketPayload {
    /** A named value as the debugger shows it. */
    public record Value(String name, String value) {
        static final StreamCodec<RegistryFriendlyByteBuf, Value> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Value::name,
                ByteBufCodecs.STRING_UTF8, Value::value,
                Value::new);
    }

    public static final Type<ProgramSync> TYPE = new Type<>(Residual.id("program_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProgramSync> STREAM_CODEC = StreamCodec.of(ProgramSync::write, ProgramSync::read);

    /** Nothing to show: no program in that slot. */
    public static ProgramSync none(BlockPos pos, int slot) {
        return new ProgramSync(-1, pos, slot, false, -1, List.of(), List.of(), List.of(), List.of(), "", List.of());
    }

    private static void write(RegistryFriendlyByteBuf buf, ProgramSync s) {
        buf.writeVarInt(s.entity);
        buf.writeBlockPos(s.pos);
        buf.writeVarInt(s.slot);
        buf.writeBoolean(s.paused);
        buf.writeVarInt(s.at);
        buf.writeCollection(s.breaks, (b, i) -> b.writeVarInt(i));
        Value.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, s.seen);
        Value.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, s.watched);
        Trace.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, s.trace);
        buf.writeUtf(s.status);
        buf.writeCollection(s.statusArgs, (b, a) -> b.writeUtf(a));
    }

    private static ProgramSync read(RegistryFriendlyByteBuf buf) {
        return new ProgramSync(buf.readVarInt(), buf.readBlockPos(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(),
                buf.readList(b -> b.readVarInt()), Value.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
                Value.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf), Trace.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
                buf.readUtf(), buf.readList(b -> b.readUtf()));
    }

    /** Lets a program's listing be numbered as the server numbers it. */
    public static List<Program.Line> listing(String text) {
        Program.Parsed p = Program.parse(text);
        return p.ok() ? p.program().lines() : List.of();
    }

    public static ProgramSync of(int entity, BlockPos pos, int slot, ProgramView view, Debug debug, List<Trace> trace, String status, List<String> statusArgs) {
        return new ProgramSync(entity, pos, slot, debug.paused, debug.at, List.copyOf(debug.breaks), view.seen(), view.watched(), List.copyOf(trace), status, statusArgs);
    }

    @Override
    public Type<ProgramSync> type() {
        return TYPE;
    }
}
