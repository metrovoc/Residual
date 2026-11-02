package io.github.metrovoc.residual.train;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.compute.Parallelism;
import io.github.metrovoc.residual.compute.Timing;
import io.github.metrovoc.residual.graph.Captures;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.learn.Batch;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Mixture;
import io.github.metrovoc.residual.learn.Recipe;
import io.github.metrovoc.residual.learn.Task;
import io.github.metrovoc.residual.learn.Trainer;
import io.github.metrovoc.residual.learn.Wrecks;
import io.github.metrovoc.residual.scope.Discoveries;
import io.github.metrovoc.residual.tensor.Ops;
import io.github.metrovoc.residual.tensor.Tape;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.view.Heatmap;
import io.github.metrovoc.residual.world.GraphScanner;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
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

    /** {@code PASSED} and {@code BUDGET} only end the repair of a wreck; other training runs until stopped. */
    public enum Outcome { PASSED, BUDGET, FINISHED, DIVERGED, STOPPED, CHANGED, CRASHED }

    /**
     * One point of the learning curve.
     *
     * @param val      mean test loss over the benchmarks being practised
     * @param accuracy mean test accuracy over those scored by accuracy
     * @param peak     the largest loss of a step that spiked since the point before, or 0; the smoothed curve hides them
     * @param rate     the learning rate at this step
     */
    public record Point(int step, float train, float val, float accuracy, float peak, float rate) {
    }

    /** What the training thread hands to the server thread. */
    public static final class Update {
        /** Steps this job has taken. */
        public int steps;
        public float trainLoss;
        /** The latest test of every benchmark being practised. */
        public Map<Benchmark, Trainer.Eval> tests = Map.of();
        public float gradNorm;
        /** The learning rate of the latest step. */
        public float rate;
        /** Steps whose loss leapt far above the run's recent average, since this job began. */
        public int spikes;
        /** The largest attention score and the largest of the model's own scores, on the fixed example. */
        public float topScore, topLogit;
        /** How often a second head's first choice is right, or NaN without one. */
        public float secondRight = Float.NaN;
        /** Weight copies by node; null when they must not be kept (they turned into NaN). */
        @Nullable
        public Map<Integer, Map<String, Tensor>> params;
        public final Map<Integer, Heatmap> outputs = new HashMap<>();
        public final Map<Integer, Heatmap> weights = new HashMap<>();
        public final Map<Integer, Float> gradNorms = new HashMap<>();
        /** How big each node's output is, and the gradient of the loss with respect to it, as root mean squares. */
        public final Map<Integer, Float> activity = new HashMap<>();
        public final Map<Integer, Float> gradient = new HashMap<>();
        @Nullable
        public Outcome outcome;
        public String detail = "";
    }

    private static final long PUBLISH_MS = 500;

    public final ServerLevel level;
    public final BlockPos core;
    public final Set<Long> footprint;
    public final List<BlockPos> nodes;
    /** What each cable and stream block of the model carries, from its scan. */
    public final Map<Long, Integer> carries;
    public final CompiledGraph model;
    /** The wreck being repaired, whose test ends the run, or null. */
    @Nullable
    public final Wrecks.Wreck wreck;
    public final int threads;
    private final Trainer trainer;
    /** One fixed example the pictures and the waves are taken from. */
    private final Batch probe;
    /** Steps this job may take before the wreck's budget runs out; 0 for no limit. */
    private final int budget;

    /** What it practises on; the Core swaps it when its data slots change. */
    public volatile Mixture data;

    public volatile Speed speed;
    public volatile float learningRate;
    public volatile int batchSize;
    public volatile float clip;
    /** The rest of how it trains; changes take effect from the next step. */
    public volatile Recipe recipe;

    /** How it shares out its work among its threads, as devices. */
    public volatile Parallelism parallelism = Parallelism.ONE;
    /** Flops it has done and tokens it has read, as they happen. */
    public final LongAdder flops = new LongAdder(), tokens = new LongAdder();
    /** The flops paid for so far: it waits, in {@link #starved}, once it has done them all. */
    public volatile long paidFlops = Long.MAX_VALUE;
    public volatile boolean starved;
    private volatile long flopsAStep;
    public volatile float weightDecay = 0.01f;
    /** What has been seen of this model, and how many steps it had before this job, for what the job notices. */
    public Discoveries.Seen seen = new Discoveries.Seen();
    public int stepsBefore;
    private final ConcurrentLinkedQueue<Discoveries.Found> found = new ConcurrentLinkedQueue<>();

    private volatile boolean stopRequested;
    private volatile Outcome stopAs = Outcome.STOPPED;
    private volatile boolean finished;
    private final AtomicReference<Update> pending = new AtomicReference<>();
    private volatile int spikes;
    private volatile float secondRight = Float.NaN;
    private final ConcurrentLinkedQueue<Point> points = new ConcurrentLinkedQueue<>();

    public TrainingJob(ServerLevel level, BlockPos core, GraphScanner.Scan scan, CompiledGraph model, Mixture data,
                       @Nullable Wrecks.Wreck wreck, long seed, int threads, int budget, Speed speed, float learningRate,
                       int batchSize, float clip, Recipe recipe) {
        this.level = level;
        this.core = core;
        this.footprint = scan.footprint();
        this.nodes = scan.nodes();
        this.carries = scan.carries();
        this.model = model;
        this.data = data;
        this.wreck = wreck;
        this.threads = threads;
        this.budget = budget;
        this.speed = speed;
        this.learningRate = learningRate;
        this.batchSize = batchSize;
        this.clip = clip;
        this.recipe = recipe;
        this.trainer = new Trainer(model, data, seed, learningRate, batchSize, clip);
        this.trainer.recipe = recipe;
        this.probe = data.batch(new java.util.Random(99), 1, true);
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
        try {
            parallelism.training(model, threads, flops, this::loop);
        } catch (Throwable t) {
            Residual.LOG.error("Training crashed", t);
            Update u = new Update();
            u.outcome = Outcome.CRASHED;
            u.detail = String.valueOf(t);
            pending.set(u);
        } finally {
            finished = true;
        }
    }

    /** How the last step ran across its devices, or null for one device. */
    @Nullable
    public Timing timing() {
        return model.trainingPass().timing();
    }

    /** The flops of its last step. */
    public long flopsAStep() {
        return flopsAStep;
    }

    /** Waits until what it has done is paid for. */
    private void awaitPower() {
        while (!stopRequested && flops.sum() > paidFlops) {
            starved = true;
            LockSupport.parkNanos(2_000_000);
        }
        starved = false;
    }

    private void loop() {
        double ema = Double.NaN;
        double start = Math.log(model.env.vocab());
        Map<Benchmark, Trainer.Eval> tests = Map.of();
        long lastPublish = System.currentTimeMillis();
        long next = System.nanoTime();
        int done = 0;
        int spikes = 0;
        float peak = 0;
        Map<Benchmark, Double> recent = new EnumMap<>(Benchmark.class);
        while (!stopRequested) {
            Mixture d = data;
            Recipe r = recipe;
            trainer.task = d;
            trainer.learningRate = learningRate * d.rateScale();
            trainer.batchSize = batchSize;
            trainer.clip = clip;
            trainer.recipe = r;
            trainer.weightDecay = weightDecay;
            long before = flops.sum();
            float loss = trainer.train();
            flopsAStep = flops.sum() - before;
            tokens.add(trainer.lastTokens());
            awaitPower();
            done++;
            double usual = recent.getOrDefault(d.lastDrawn, Double.NaN);
            if (Trainer.spiked(loss, usual)) {
                spikes++;
                peak = Math.max(peak, loss);
            }
            recent.put(d.lastDrawn, Double.isNaN(usual) ? loss : 0.95 * usual + 0.05 * loss);
            this.spikes = spikes;
            notice(d, done);
            if (!Float.isFinite(loss)) {
                Update u = update(ema, tests, done, Outcome.DIVERGED, false);
                u.detail = "NaN";
                pending.set(u);
                return;
            }
            ema = Double.isNaN(ema) ? loss : 0.95 * ema + 0.05 * loss;
            if (done >= 50 && ema > 3 * start) {
                Update u = update(ema, tests, done, Outcome.DIVERGED, true);
                u.detail = String.format(Locale.ROOT, "%.2f", ema);
                pending.set(u);
                return;
            }
            Speed s = speed;
            if (done % s.evalEvery == 0) {
                tests = test(d, 4);
                points.add(point(trainer.step(), ema, tests, peak, trainer.lastRate()));
                peak = 0;
                secondRight = Trainer.secondRight(model, d);
                Trainer.Eval mine = wreck == null ? null : tests.get(wreck.benchmark());
                if (mine != null && wreck.passes(mine)) {
                    // a lucky estimate shouldn't count: confirm on four times as much data
                    Trainer.Eval sure = Trainer.evaluate(model, wreck.benchmark().test(model.env.tokenizer()), 16);
                    if (wreck.passes(sure)) {
                        Map<Benchmark, Trainer.Eval> confirmed = new EnumMap<>(tests);
                        confirmed.put(wreck.benchmark(), sure);
                        pending.set(update(ema, confirmed, done, Outcome.PASSED, true));
                        return;
                    }
                }
            }
            if (budget > 0 && done >= budget) {
                pending.set(update(ema, tests, done, Outcome.BUDGET, true));
                return;
            }
            if (r.length() > 0 && done >= r.length()) {
                tests = test(d, 4);
                points.add(point(trainer.step(), ema, tests, peak, trainer.lastRate()));
                pending.set(update(ema, tests, done, Outcome.FINISHED, true));
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastPublish >= PUBLISH_MS) {
                pending.set(update(ema, tests, done, null, true));
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
        pending.set(update(ema, tests, done, stopAs, true));
    }

    /** Looks now and then at what the model really does, for the things worth telling. */
    private void notice(Mixture d, int done) {
        if (done % Discoveries.READ_EVERY == 0) {
            Discoveries.Found f = Discoveries.readRuns(model, seen);
            if (f != null) {
                found.add(f);
            }
        }
        if (done % Discoveries.SUMS_EVERY == 0 && d.sources.stream().anyMatch(s -> s.benchmark() == Benchmark.MODULAR)) {
            Discoveries.Found f = Discoveries.readSums(model, stepsBefore + done, seen);
            if (f != null) {
                found.add(f);
            }
        }
    }

    /** What it has noticed since the last call. */
    public List<Discoveries.Found> drainFound() {
        List<Discoveries.Found> out = new ArrayList<>();
        Discoveries.Found f;
        while ((f = found.poll()) != null) {
            out.add(f);
        }
        return out;
    }

    /** Tests every benchmark {@code d} practises, on {@code batches} batches of held-out examples each. */
    private Map<Benchmark, Trainer.Eval> test(Mixture d, int batches) {
        Map<Benchmark, Trainer.Eval> out = new EnumMap<>(Benchmark.class);
        for (Mixture.Source s : d.sources) {
            out.computeIfAbsent(s.benchmark(), a -> Trainer.evaluate(model, a.test(model.env.tokenizer()), batches));
        }
        return out;
    }

    private static Point point(int step, double ema, Map<Benchmark, Trainer.Eval> tests, float peak, float rate) {
        double loss = 0, acc = 0;
        int accs = 0;
        for (Map.Entry<Benchmark, Trainer.Eval> e : tests.entrySet()) {
            loss += e.getValue().loss();
            if (e.getKey().metric == Task.Metric.ACCURACY) {
                acc += e.getValue().accuracy();
                accs++;
            }
        }
        return new Point(step, (float) ema, tests.isEmpty() ? Float.NaN : (float) (loss / tests.size()),
                accs == 0 ? Float.NaN : (float) (acc / accs), peak, rate);
    }

    private Update update(double ema, Map<Benchmark, Trainer.Eval> tests, int done, @Nullable Outcome outcome, boolean keepWeights) {
        Update u = new Update();
        u.steps = done;
        u.trainLoss = (float) ema;
        u.tests = tests;
        u.gradNorm = (float) trainer.lastGradNorm();
        u.rate = trainer.lastRate();
        u.spikes = spikes;
        u.secondRight = secondRight;
        u.outcome = outcome;
        if (!keepWeights) {
            return u;
        }
        u.params = model.snapshot();
        for (int node : model.order) {
            Map<String, Tensor> p = model.params(node);
            if (!p.isEmpty()) {
                u.weights.put(node, Heatmap.of(mainWeight(op(node), p)));
                u.gradNorms.put(node, gradNorm(p));
            }
        }
        // the probe's backward pass adds into the weights' gradients, which the next step clears before its own
        Tape tape = new Tape();
        Captures captures = new Captures();
        Tensor logits = model.forward(tape, probe.input(), captures);
        u.topScore = captures.topScore;
        for (float v : logits.data) {
            u.topLogit = Math.max(u.topLogit, Math.abs(v));
        }
        tape.backward(Ops.crossEntropy(tape, logits, probe.targets(), probe.weights()));
        for (int node : model.order) {
            Tensor out = captures.outputs.get(node);
            Tensor shown = op(node).attends() ? captures.patterns.get(node) : out;
            if (shown == null) {
                shown = out;
            }
            if (shown != null) {
                u.outputs.put(node, Heatmap.of(shown));
            }
            if (out != null) {
                u.activity.put(node, rms(out.data));
                u.gradient.put(node, out.hasGrad() ? rms(out.grad()) : 0f);
            }
        }
        return u;
    }

    private Op op(int node) {
        return model.spec.node(node).op;
    }

    private static float rms(float[] v) {
        double sum = 0;
        for (float x : v) {
            sum += (double) x * x;
        }
        return v.length == 0 ? 0 : (float) Math.sqrt(sum / v.length);
    }

    /** The matrix shown on a machine's sides. */
    public static Tensor mainWeight(Op op, Map<String, Tensor> p) {
        return switch (op) {
            case ATTENTION, MODERN_ATTENTION -> p.get("q");
            case MLP, SWIGLU -> p.get("w1");
            case TRANSFORMER, MODERN_BLOCK -> p.get("mlp.w1");
            case QK_NORM -> p.get("t");
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
