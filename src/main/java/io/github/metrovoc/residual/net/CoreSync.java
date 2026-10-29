package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.train.TrainingJob;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

/**
 * Everything the Model Core's screen shows, sent to players who have it open.
 *
 * @param tests      the latest test of each ability, in ability order
 * @param practising which abilities the loaded data practises, in ability order
 * @param budgetLeft steps left to repair a wreck, or -1 when no wreck is loaded
 * @param busy       a test run or a save is under way
 */
public record CoreSync(BlockPos pos, int learningRate, int batchSize, int clip, int speed, boolean running,
                       int steps, int runSteps, float trainLoss, float valLoss, float accuracy, boolean causal, float gradNorm,
                       String message, List<String> args, List<TrainingJob.Point> history, List<Issue> issues,
                       List<Node> nodes, int threads, int throttled, long parameters, List<Score> tests,
                       List<Boolean> practising, int budgetLeft, boolean busy) implements CustomPacketPayload {
    public static final Type<CoreSync> TYPE = new Type<>(Residual.id("core_sync"));

    /** A problem with the model, at a machine (or at the Core when {@code pos} is null). */
    public record Issue(@Nullable BlockPos pos, String message) {
    }

    /** One machine of the model as the Inspect tab lists it. */
    public record Node(BlockPos pos, String op, String name, String shape, long parameters, float gradNorm, boolean active) {
    }

    /** A test result; NaN when the ability was never tested. */
    public record Score(float loss, float accuracy, int step) {
        public static final Score NONE = new Score(Float.NaN, Float.NaN, -1);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, CoreSync> STREAM_CODEC = StreamCodec.of(CoreSync::write, CoreSync::read);

    private static void write(RegistryFriendlyByteBuf buf, CoreSync s) {
        buf.writeBlockPos(s.pos);
        buf.writeVarInt(s.learningRate);
        buf.writeVarInt(s.batchSize);
        buf.writeVarInt(s.clip);
        buf.writeVarInt(s.speed);
        buf.writeBoolean(s.running);
        buf.writeVarInt(s.steps);
        buf.writeVarInt(s.runSteps);
        buf.writeFloat(s.trainLoss);
        buf.writeFloat(s.valLoss);
        buf.writeFloat(s.accuracy);
        buf.writeBoolean(s.causal);
        buf.writeFloat(s.gradNorm);
        buf.writeUtf(s.message);
        buf.writeCollection(s.args, (b, a) -> b.writeUtf(a));
        buf.writeVarInt(s.history.size());
        for (TrainingJob.Point p : s.history) {
            buf.writeVarInt(p.step());
            buf.writeFloat(p.train());
            buf.writeFloat(p.val());
            buf.writeFloat(p.accuracy());
        }
        buf.writeVarInt(s.issues.size());
        for (Issue i : s.issues) {
            buf.writeBoolean(i.pos != null);
            if (i.pos != null) {
                buf.writeBlockPos(i.pos);
            }
            buf.writeUtf(i.message);
        }
        buf.writeVarInt(s.nodes.size());
        for (Node n : s.nodes) {
            buf.writeBlockPos(n.pos);
            buf.writeUtf(n.op);
            buf.writeUtf(n.name);
            buf.writeUtf(n.shape);
            buf.writeVarLong(n.parameters);
            buf.writeFloat(n.gradNorm);
            buf.writeBoolean(n.active);
        }
        buf.writeVarInt(s.threads);
        buf.writeVarInt(s.throttled);
        buf.writeVarLong(s.parameters);
        buf.writeVarInt(s.tests.size());
        for (Score t : s.tests) {
            buf.writeFloat(t.loss);
            buf.writeFloat(t.accuracy);
            buf.writeVarInt(t.step + 1);
        }
        buf.writeCollection(s.practising, (b, p) -> b.writeBoolean(p));
        buf.writeVarInt(s.budgetLeft + 1);
        buf.writeBoolean(s.busy);
    }

    private static CoreSync read(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int lr = buf.readVarInt();
        int batch = buf.readVarInt();
        int clip = buf.readVarInt();
        int speed = buf.readVarInt();
        boolean running = buf.readBoolean();
        int steps = buf.readVarInt();
        int runSteps = buf.readVarInt();
        float train = buf.readFloat();
        float val = buf.readFloat();
        float acc = buf.readFloat();
        boolean causal = buf.readBoolean();
        float grad = buf.readFloat();
        String message = buf.readUtf();
        List<String> args = buf.readList(b -> b.readUtf());
        int n = buf.readVarInt();
        List<TrainingJob.Point> history = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            history.add(new TrainingJob.Point(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat()));
        }
        n = buf.readVarInt();
        List<Issue> issues = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            BlockPos at = buf.readBoolean() ? buf.readBlockPos() : null;
            issues.add(new Issue(at, buf.readUtf()));
        }
        n = buf.readVarInt();
        List<Node> nodes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            nodes.add(new Node(buf.readBlockPos(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarLong(), buf.readFloat(), buf.readBoolean()));
        }
        int threads = buf.readVarInt();
        int throttled = buf.readVarInt();
        long parameters = buf.readVarLong();
        n = buf.readVarInt();
        List<Score> tests = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            tests.add(new Score(buf.readFloat(), buf.readFloat(), buf.readVarInt() - 1));
        }
        List<Boolean> practising = buf.readList(b -> b.readBoolean());
        int budgetLeft = buf.readVarInt() - 1;
        boolean busy = buf.readBoolean();
        return new CoreSync(pos, lr, batch, clip, speed, running, steps, runSteps, train, val, acc, causal, grad, message, args,
                history, issues, nodes, threads, throttled, parameters, tests, practising, budgetLeft, busy);
    }

    @Override
    public Type<CoreSync> type() {
        return TYPE;
    }
}
