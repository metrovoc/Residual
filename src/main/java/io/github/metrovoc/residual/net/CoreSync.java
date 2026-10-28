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

/** Everything the Model Core's screen shows, sent to players who have it open. */
public record CoreSync(BlockPos pos, String lesson, int learningRate, int batchSize, int clip, int speed, boolean running,
                       int steps, int runSteps, float trainLoss, float valLoss, float accuracy, boolean causal, float gradNorm,
                       String message, List<String> args, List<TrainingJob.Point> history, List<Issue> issues,
                       List<Node> nodes, int threads, long parameters) implements CustomPacketPayload {
    public static final Type<CoreSync> TYPE = new Type<>(Residual.id("core_sync"));

    /** A problem with the model, at a machine (or at the Core when {@code pos} is null). */
    public record Issue(@Nullable BlockPos pos, String message) {
    }

    /** One machine of the model as the Inspect tab lists it. */
    public record Node(BlockPos pos, String op, String name, String shape, long parameters, float gradNorm, boolean active) {
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, CoreSync> STREAM_CODEC = StreamCodec.of(CoreSync::write, CoreSync::read);

    private static void write(RegistryFriendlyByteBuf buf, CoreSync s) {
        buf.writeBlockPos(s.pos);
        buf.writeUtf(s.lesson);
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
        buf.writeVarLong(s.parameters);
    }

    private static CoreSync read(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String lesson = buf.readUtf();
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
        return new CoreSync(pos, lesson, lr, batch, clip, speed, running, steps, runSteps, train, val, acc, causal, grad,
                message, args, history, issues, nodes, buf.readVarInt(), buf.readVarLong());
    }

    @Override
    public Type<CoreSync> type() {
        return TYPE;
    }
}
