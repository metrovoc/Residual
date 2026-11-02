package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * What a Model Server's screen shows, sent to whoever has it open.
 *
 * @param rate       answers a second, as measured on what it serves
 * @param load       the share of its operations in use
 * @param memory     bytes it holds in all, of which weights, the draft's and cached sequences take the next three
 * @param passCost   what a pass costs, as a share of a row in a plain pass
 * @param rowCost    what a row in a pass costs, as a share of a row in a plain pass
 * @param plainNanos the time those shares are of, then the times they came from
 * @param saved      rows a cache saved it from running
 * @param decodes    false if the model reads ahead, so that only whole passes give its answers
 * @param problem    what keeps it from serving, a translation key, or empty
 */
public record ServerSync(int menu, float rate, float load, int waiting, int turnedAway, int answered, int threads, int hot, long memory, long weights, long draft, long cached,
                         float passCost, float rowCost, float plainNanos, float passNanos, float rowNanos, long passes, long rows, long saved, long proposed, long accepted,
                         boolean decodes, List<Drawn> drawn, List<Scored> scores, String problem, int bits, int batch, int effort) implements CustomPacketPayload {
    public static final Type<ServerSync> TYPE = new Type<>(Residual.id("server_sync"));

    /** Who has drawn on it lately, and what share of it. */
    public record Drawn(String who, float share) {
    }

    /** A test its model passes as saved: how it scored, how it scores as served now, and whether that still passes. */
    public record Scored(String benchmark, float saved, float now, boolean passes) {
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerSync> STREAM_CODEC = StreamCodec.of(ServerSync::write, ServerSync::read);

    private static void write(RegistryFriendlyByteBuf buf, ServerSync s) {
        buf.writeVarInt(s.menu);
        buf.writeFloat(s.rate);
        buf.writeFloat(s.load);
        buf.writeVarInt(s.waiting);
        buf.writeVarInt(s.turnedAway);
        buf.writeVarInt(s.answered);
        buf.writeVarInt(s.threads);
        buf.writeVarInt(s.hot);
        buf.writeVarLong(s.memory);
        buf.writeVarLong(s.weights);
        buf.writeVarLong(s.draft);
        buf.writeVarLong(s.cached);
        buf.writeFloat(s.passCost);
        buf.writeFloat(s.rowCost);
        buf.writeFloat(s.plainNanos);
        buf.writeFloat(s.passNanos);
        buf.writeFloat(s.rowNanos);
        buf.writeVarLong(s.passes);
        buf.writeVarLong(s.rows);
        buf.writeVarLong(s.saved);
        buf.writeVarLong(s.proposed);
        buf.writeVarLong(s.accepted);
        buf.writeBoolean(s.decodes);
        buf.writeCollection(s.drawn, (b, d) -> {
            b.writeUtf(d.who);
            b.writeFloat(d.share);
        });
        buf.writeCollection(s.scores, (b, c) -> {
            b.writeUtf(c.benchmark);
            b.writeFloat(c.saved);
            b.writeFloat(c.now);
            b.writeBoolean(c.passes);
        });
        buf.writeUtf(s.problem);
        buf.writeVarInt(s.bits);
        buf.writeVarInt(s.batch);
        buf.writeVarInt(s.effort);
    }

    private static ServerSync read(RegistryFriendlyByteBuf buf) {
        return new ServerSync(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readBoolean(),
                buf.readList(b -> new Drawn(b.readUtf(), b.readFloat())),
                buf.readList(b -> new Scored(b.readUtf(), b.readFloat(), b.readFloat(), b.readBoolean())),
                buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public Type<ServerSync> type() {
        return TYPE;
    }
}
