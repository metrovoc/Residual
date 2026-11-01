package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.train.TrainingJob;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

/**
 * Everything the Model Core's screen shows, sent to players who have it open.
 *
 * @param tests      the latest test of each benchmark, in benchmark order
 * @param practising which benchmarks the loaded data practises, in benchmark order
 * @param budgetLeft steps left to repair a wreck, or -1 when no wreck is loaded
 * @param busy       a test run or a save is under way
 * @param plan        how this Core's next or current run is trained, and what that run has reached
 */
public record CoreSync(BlockPos pos, int learningRate, int batchSize, int clip, int speed, boolean running,
                       int steps, int runSteps, float trainLoss, float valLoss, float accuracy, boolean causal, float gradNorm,
                       String message, List<String> args, List<TrainingJob.Point> history, List<Issue> issues,
                       List<Node> nodes, int threads, int throttled, long parameters, List<Score> tests,
                       List<Boolean> practising, int budgetLeft, boolean busy, Plan plan) implements CustomPacketPayload {
    public static final Type<CoreSync> TYPE = new Type<>(Residual.id("core_sync"));

    /**
     * The recipe as the Train tab's Recipe page shows it.
     *
     * @param schedule, warmup, length, precision, moves, packing each setting's place in its list
     * @param units       which units touch the Core, a bit each by {@code Unit} ordinal
     * @param step        steps this run has taken
     * @param rate        the learning rate of the latest step
     * @param spikes      steps of this run whose loss leapt far above its recent average
     * @param topScore    the largest attention score lately, or NaN
     * @param topLogit    the largest of the model's own scores lately, or NaN
     * @param secondRight how often a second head's first choice is right, or NaN
     * @param weightBytes what the model's weights take in this precision
     * @param readShare   the share of the loaded text its rows ever read, or NaN with none loaded
     * @param onboardBytes the most an agent can carry
     */
    public record Plan(int schedule, int warmup, int length, int precision, int moves, int packing, int units, int step, float rate,
                       int spikes, float topScore, float topLogit, float secondRight, long weightBytes, float readShare, long onboardBytes) {
        public static final Plan NONE = new Plan(0, 1, 0, 0, 0, 0, 0, 0, Float.NaN, 0, Float.NaN, Float.NaN, Float.NaN, 0, Float.NaN, 0);

        void write(RegistryFriendlyByteBuf buf) {
            for (int v : new int[]{schedule, warmup, length, precision, moves, packing, units, step, spikes}) {
                buf.writeVarInt(v);
            }
            for (float v : new float[]{rate, topScore, topLogit, secondRight, readShare}) {
                buf.writeFloat(v);
            }
            buf.writeVarLong(weightBytes);
            buf.writeVarLong(onboardBytes);
        }

        static Plan read(RegistryFriendlyByteBuf buf) {
            int[] i = new int[9];
            for (int k = 0; k < i.length; k++) {
                i[k] = buf.readVarInt();
            }
            float[] f = new float[5];
            for (int k = 0; k < f.length; k++) {
                f[k] = buf.readFloat();
            }
            long weightBytes = buf.readVarLong();
            long onboardBytes = buf.readVarLong();
            return new Plan(i[0], i[1], i[2], i[3], i[4], i[5], i[6], i[7], f[0], i[8], f[1], f[2], f[3], weightBytes, f[4], onboardBytes);
        }
    }

    /** A problem with the model, at a machine (or at the Core when {@code pos} is null). */
    public record Issue(@Nullable BlockPos pos, Component message) {
    }

    /** One machine of the model as the Inspect tab lists it. */
    public record Node(BlockPos pos, String op, String name, String shape, long parameters, float gradNorm, boolean active) {
    }

    /** A test result; NaN when the benchmark was never tested. */
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
            buf.writeFloat(p.peak());
            buf.writeFloat(p.rate());
        }
        buf.writeVarInt(s.issues.size());
        for (Issue i : s.issues) {
            buf.writeBoolean(i.pos != null);
            if (i.pos != null) {
                buf.writeBlockPos(i.pos);
            }
            ComponentSerialization.STREAM_CODEC.encode(buf, i.message);
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
        s.plan.write(buf);
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
            history.add(new TrainingJob.Point(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));
        }
        n = buf.readVarInt();
        List<Issue> issues = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            BlockPos at = buf.readBoolean() ? buf.readBlockPos() : null;
            issues.add(new Issue(at, ComponentSerialization.STREAM_CODEC.decode(buf)));
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
                history, issues, nodes, threads, throttled, parameters, tests, practising, budgetLeft, busy, Plan.read(buf));
    }

    @Override
    public Type<CoreSync> type() {
        return TYPE;
    }
}
