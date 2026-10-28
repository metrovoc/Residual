package io.github.metrovoc.residual.train;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.graph.Captures;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.learn.Task;
import io.github.metrovoc.residual.learn.Trainer;
import io.github.metrovoc.residual.tensor.Parallel;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import io.github.metrovoc.residual.view.Heatmap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Trains one model on its own thread. The server thread never touches the model: it reads {@link Update}s, which
 * hold copies of the weights plus small pictures for the machine faces.
 */
public final class TrainingJob {
    public enum Speed {
        WATCH(6, 5), NORMAL(40, 25), MAX(0, 50);

        final int stepsPerSecond;
        final int evalEvery;

        Speed(int stepsPerSecond, int evalEvery) {
            this.stepsPerSecond = stepsPerSecond;
            this.evalEvery = evalEvery;
        }
    }

    public enum Outcome { PASSED, BUDGET, DIVERGED, STOPPED, CHANGED, CRASHED }

    /** One point of the learning curve. */
    public record Point(int step, float train, float val, float accuracy) {
    }

    /** What the training thread hands to the server thread. */
    public static final class Update {
        /** Steps this job has taken. */
        public int steps;
        public float trainLoss;
        @Nullable
        public Trainer.Eval eval;
        public float gradNorm;
        /** Weight copies by node; null when they must not be kept (they turned into NaN). */
        @Nullable
        public Map<Integer, Map<String, Tensor>> params;
        public final Map<Integer, Heatmap> outputs = new HashMap<>();
        public final Map<Integer, Heatmap> weights = new HashMap<>();
        public final Map<Integer, Float> gradNorms = new HashMap<>();
        @Nullable
        public Outcome outcome;
        public String detail = "";
    }

    private static final long PUBLISH_MS = 500;

    public final ServerLevel level;
    public final BlockPos core;
    public final Set<Long> footprint;
    public final List<BlockPos> nodes;
    public final CompiledGraph model;
    public final Task task;
    public final int threads;
    private final Trainer trainer;
    private final Tokens probe;
    /** Steps this job may take before the case's budget runs out; 0 for no limit. */
    private final int budget;

    public volatile Speed speed;
    public volatile float learningRate;
    public volatile int batchSize;
    public volatile float clip;

    private volatile boolean stopRequested;
    private volatile Outcome stopAs = Outcome.STOPPED;
    private volatile boolean finished;
    private final AtomicReference<Update> pending = new AtomicReference<>();
    private final ConcurrentLinkedQueue<Point> points = new ConcurrentLinkedQueue<>();

    public TrainingJob(ServerLevel level, BlockPos core, Set<Long> footprint, List<BlockPos> nodes, CompiledGraph model,
                       Task task, long seed, int threads, int budget, Speed speed, float learningRate, int batchSize, float clip) {
        this.level = level;
        this.core = core;
        this.footprint = footprint;
        this.nodes = nodes;
        this.model = model;
        this.task = task;
        this.threads = threads;
        this.budget = budget;
        this.speed = speed;
        this.learningRate = learningRate;
        this.batchSize = batchSize;
        this.clip = clip;
        this.trainer = new Trainer(model, task, seed, learningRate, batchSize, clip);
        this.probe = task.batch(new java.util.Random(99), 1, true).input();
    }

    public void requestStop(Outcome as) {
        stopAs = as;
        stopRequested = true;
    }

    public boolean finished() {
        return finished;
    }

    /** The newest update, if one arrived since the last call. */
    @Nullable
    public Update poll() {
        return pending.getAndSet(null);
    }

    /** Learning-curve points recorded since the last call. */
    public List<Point> drainPoints() {
        List<Point> out = new ArrayList<>();
        Point p;
        while ((p = points.poll()) != null) {
            out.add(p);
        }
        return out;
    }

    void run() {
        ForkJoinPool pool = threads > 1 ? new ForkJoinPool(threads) : null;
        try {
            Parallel.with(pool, this::loop);
        } catch (Throwable t) {
            Residual.LOG.error("Training crashed", t);
            Update u = new Update();
            u.outcome = Outcome.CRASHED;
            u.detail = String.valueOf(t);
            pending.set(u);
        } finally {
            if (pool != null) {
                pool.shutdown();
            }
            finished = true;
        }
    }

    private void loop() {
        double ema = Double.NaN;
        double start = Math.log(task.vocab.size());
        Trainer.Eval eval = null;
        long lastPublish = System.currentTimeMillis();
        long next = System.nanoTime();
        int done = 0;
        while (!stopRequested) {
            trainer.learningRate = learningRate;
            trainer.batchSize = batchSize;
            trainer.clip = clip;
            float loss = trainer.train();
            done++;
            if (!Float.isFinite(loss)) {
                Update u = update(ema, eval, done, Outcome.DIVERGED, false);
                u.detail = "NaN";
                pending.set(u);
                return;
            }
            ema = Double.isNaN(ema) ? loss : 0.95 * ema + 0.05 * loss;
            if (done >= 50 && ema > 3 * start) {
                Update u = update(ema, eval, done, Outcome.DIVERGED, true);
                u.detail = String.format(Locale.ROOT, "%.2f", ema);
                pending.set(u);
                return;
            }
            Speed s = speed;
            if (done % s.evalEvery == 0) {
                eval = trainer.evaluate(4);
                points.add(new Point(trainer.step(), (float) ema, (float) eval.loss(), (float) eval.accuracy()));
                if (eval.causal() && task.passes(eval.loss(), eval.accuracy())) {
                    // a lucky estimate shouldn't pass a lesson: confirm on four times as much data
                    Trainer.Eval sure = trainer.evaluate(16);
                    if (sure.causal() && task.passes(sure.loss(), sure.accuracy())) {
                        pending.set(update(ema, sure, done, Outcome.PASSED, true));
                        return;
                    }
                }
            }
            if (budget > 0 && done >= budget) {
                pending.set(update(ema, eval, done, Outcome.BUDGET, true));
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastPublish >= PUBLISH_MS) {
                pending.set(update(ema, eval, done, null, true));
                lastPublish = now;
            }
            if (s.stepsPerSecond > 0) {
                next += 1_000_000_000L / s.stepsPerSecond;
                long wait;
                while (!stopRequested && (wait = next - System.nanoTime()) > 0) {
                    LockSupport.parkNanos(wait);
                }
                if (next < System.nanoTime() - 1_000_000_000L) {
                    next = System.nanoTime();
                }
            }
        }
        pending.set(update(ema, eval, done, stopAs, true));
    }

    private Update update(double ema, @Nullable Trainer.Eval eval, int done, @Nullable Outcome outcome, boolean keepWeights) {
        Update u = new Update();
        u.steps = done;
        u.trainLoss = (float) ema;
        u.eval = eval;
        u.gradNorm = (float) trainer.lastGradNorm();
        u.outcome = outcome;
        if (!keepWeights) {
            return u;
        }
        u.params = model.snapshot();
        Captures captures = new Captures();
        model.forward(null, probe, captures);
        for (int node : model.order) {
            Op op = model.spec.node(node).op;
            Tensor shown = op == Op.ATTENTION || op == Op.TRANSFORMER ? captures.patterns.get(node) : captures.outputs.get(node);
            if (shown == null) {
                shown = captures.outputs.get(node);
            }
            if (shown != null) {
                u.outputs.put(node, Heatmap.of(shown));
            }
            Map<String, Tensor> p = model.params(node);
            if (!p.isEmpty()) {
                u.weights.put(node, Heatmap.of(mainWeight(op, p)));
                u.gradNorms.put(node, gradNorm(p));
            }
        }
        return u;
    }

    /** The matrix shown on a machine's sides. */
    public static Tensor mainWeight(Op op, Map<String, Tensor> p) {
        return switch (op) {
            case ATTENTION -> p.get("q");
            case MLP -> p.get("w1");
            case TRANSFORMER -> p.get("mlp.w1");
            default -> p.get("weight");
        };
    }

    private static float gradNorm(Map<String, Tensor> p) {
        double sum = 0;
        for (Tensor t : p.values()) {
            if (t.hasGrad()) {
                for (float g : t.grad()) {
                    sum += (double) g * g;
                }
            }
        }
        return (float) Math.sqrt(sum);
    }
}
