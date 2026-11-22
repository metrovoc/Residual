package io.github.metrovoc.residual.interior;

import io.github.metrovoc.residual.Residual;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

/**
 * What a hall shows, sent to everyone standing in it whenever its run has something new.
 *
 * @param origin the hall's corner
 * @param arch   what the model is made of, which the hall's layout follows
 * @param stage  what it is asked to do
 */
public record HallSync(BlockPos origin, Arch arch, Stage stage, Snapshot shot) implements CustomPacketPayload {
    public static final Type<HallSync> TYPE = new Type<>(Residual.id("hall"));
    public static final StreamCodec<FriendlyByteBuf, HallSync> STREAM_CODEC = StreamCodec.of(HallSync::write, HallSync::read);

    @Override
    public Type<HallSync> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, HallSync h) {
        buf.writeBlockPos(h.origin);
        buf.writeNbt(h.arch.save());
        buf.writeEnum(h.stage);
        Snapshot s = h.shot;
        buf.writeVarInt(s.step());
        buf.writeEnum(s.state());
        buf.writeVarInt(s.pace());
        buf.writeFloat(s.rate());
        buf.writeFloat(s.trainLoss());
        buf.writeFloat(s.testLoss());
        buf.writeFloat(s.score());
        buf.writeBoolean(s.passed());
        buf.writeVarLong(s.parameters());
        buf.writeFloat(s.stepMillis());
        buf.writeVarIntArray(s.tokens());
        buf.writeVarIntArray(s.targets());
        for (boolean b : s.scored()) {
            buf.writeBoolean(b);
        }
        grid(buf, s.embed());
        optional(buf, s.position());
        buf.writeVarInt(s.parts().size());
        for (Snapshot.PartView p : s.parts()) {
            buf.writeVarInt(p.id());
            grid(buf, p.added());
            grid(buf, p.after());
            buf.writeVarInt(p.heads());
            if (p.heads() > 0) {
                buf.writeByteArray(p.pattern());
            }
        }
        buf.writeVarInt(s.vocab());
        buf.writeByteArray(s.chances());
        for (float f : s.laneLoss()) {
            buf.writeFloat(f);
        }
        buf.writeBoolean(s.tables() != null);
        if (s.tables() != null) {
            grid(buf, s.tables().embed());
            grid(buf, s.tables().readout());
            optional(buf, s.tables().places());
            optional(buf, s.tables().follows());
            buf.writeVarIntArray(s.tables().written() == null ? new int[0] : s.tables().written());
        }
        buf.writeVarInt(s.curve().size());
        for (Snapshot.Point p : s.curve()) {
            buf.writeVarInt(p.step());
            buf.writeFloat(p.train());
            buf.writeFloat(p.test());
            buf.writeFloat(p.score());
        }
    }

    private static HallSync read(FriendlyByteBuf buf) {
        BlockPos origin = buf.readBlockPos();
        CompoundTag tag = buf.readNbt();
        Arch arch = Arch.load(tag == null ? new CompoundTag() : tag);
        Stage stage = buf.readEnum(Stage.class);
        int step = buf.readVarInt();
        Snapshot.State state = buf.readEnum(Snapshot.State.class);
        int pace = buf.readVarInt();
        float rate = buf.readFloat(), trainLoss = buf.readFloat(), testLoss = buf.readFloat(), score = buf.readFloat();
        boolean passed = buf.readBoolean();
        long parameters = buf.readVarLong();
        float stepMillis = buf.readFloat();
        int[] tokens = buf.readVarIntArray();
        int[] targets = buf.readVarIntArray();
        boolean[] scored = new boolean[tokens.length];
        for (int i = 0; i < scored.length; i++) {
            scored[i] = buf.readBoolean();
        }
        Snapshot.Grid embed = grid(buf);
        Snapshot.Grid position = optional(buf);
        int count = buf.readVarInt();
        List<Snapshot.PartView> parts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int id = buf.readVarInt();
            Snapshot.Grid added = grid(buf), after = grid(buf);
            int heads = buf.readVarInt();
            parts.add(new Snapshot.PartView(id, added, after, heads, heads > 0 ? buf.readByteArray() : null));
        }
        int vocab = buf.readVarInt();
        byte[] chances = buf.readByteArray();
        float[] laneLoss = new float[tokens.length];
        for (int i = 0; i < laneLoss.length; i++) {
            laneLoss[i] = buf.readFloat();
        }
        Snapshot.Tables tables = null;
        if (buf.readBoolean()) {
            Snapshot.Grid e = grid(buf), r = grid(buf);
            Snapshot.Grid places = optional(buf), follows = optional(buf);
            int[] written = buf.readVarIntArray();
            tables = new Snapshot.Tables(e, r, places, follows, written.length == 0 ? null : written);
        }
        int points = buf.readVarInt();
        List<Snapshot.Point> curve = new ArrayList<>();
        for (int i = 0; i < points; i++) {
            curve.add(new Snapshot.Point(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat()));
        }
        return new HallSync(origin, arch, stage, new Snapshot(step, state, pace, rate, trainLoss, testLoss, score, passed, parameters, stepMillis,
                tokens, targets, scored, embed, position, parts, vocab, chances, laneLoss, tables, curve));
    }

    private static void grid(FriendlyByteBuf buf, Snapshot.Grid g) {
        buf.writeVarInt(g.rows());
        buf.writeVarInt(g.cols());
        buf.writeFloat(g.scale());
        buf.writeByteArray(g.cells());
    }

    private static Snapshot.Grid grid(FriendlyByteBuf buf) {
        return new Snapshot.Grid(buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readByteArray());
    }

    private static void optional(FriendlyByteBuf buf, @Nullable Snapshot.Grid g) {
        buf.writeBoolean(g != null);
        if (g != null) {
            grid(buf, g);
        }
    }

    @Nullable
    private static Snapshot.Grid optional(FriendlyByteBuf buf) {
        return buf.readBoolean() ? grid(buf) : null;
    }
}
