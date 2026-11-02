package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.scope.Records;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * What the Model Core's Tests tab shows of one benchmark.
 *
 * @param score,fresh  the model as it stands on the test and on a look-alike; NaN until they are in
 * @param seeds        its design's runs from different seeds, empty before any
 * @param earlier      the runs of the design before it, empty if there were none
 * @param seedSteps    how long those runs lasted
 * @param seedFraction how far the runs under way are, or -1 with none under way
 * @param board        this server's board for the benchmark, best first
 */
public record EvaluationSync(BlockPos pos, int benchmark, float score, float fresh, boolean contaminated, float[] seeds, float[] earlier, int seedSteps, float seedFraction,
                             List<Records.Entry> board) implements CustomPacketPayload {
    public static final Type<EvaluationSync> TYPE = new Type<>(Residual.id("evaluation_sync"));
    private static final StreamCodec<RegistryFriendlyByteBuf, float[]> FLOATS = new StreamCodec<>() {
        @Override
        public float[] decode(RegistryFriendlyByteBuf buf) {
            float[] out = new float[buf.readVarInt()];
            for (int i = 0; i < out.length; i++) {
                out[i] = buf.readFloat();
            }
            return out;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, float[] v) {
            buf.writeVarInt(v.length);
            for (float f : v) {
                buf.writeFloat(f);
            }
        }
    };
    public static final StreamCodec<RegistryFriendlyByteBuf, EvaluationSync> STREAM_CODEC = StreamCodec.of(EvaluationSync::write, EvaluationSync::read);

    private static void write(RegistryFriendlyByteBuf buf, EvaluationSync e) {
        buf.writeBlockPos(e.pos);
        buf.writeVarInt(e.benchmark);
        buf.writeFloat(e.score);
        buf.writeFloat(e.fresh);
        buf.writeBoolean(e.contaminated);
        FLOATS.encode(buf, e.seeds);
        FLOATS.encode(buf, e.earlier);
        buf.writeVarInt(e.seedSteps);
        buf.writeFloat(e.seedFraction);
        Records.Entry.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, e.board);
    }

    private static EvaluationSync read(RegistryFriendlyByteBuf buf) {
        return new EvaluationSync(buf.readBlockPos(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), FLOATS.decode(buf), FLOATS.decode(buf), buf.readVarInt(), buf.readFloat(),
                new ArrayList<>(Records.Entry.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf)));
    }

    @Override
    public Type<EvaluationSync> type() {
        return TYPE;
    }
}
