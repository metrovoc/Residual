package io.github.metrovoc.residual.interior;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.graph.Captures;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.learn.Batch;
import io.github.metrovoc.residual.learn.Task;
import io.github.metrovoc.residual.learn.Trainer;
import io.github.metrovoc.residual.tensor.Ops;
import io.github.metrovoc.residual.tensor.Parallel;
import io.github.metrovoc.residual.tensor.Tensor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * One model being trained while people stand inside it. It runs on its own thread at the pace it is asked for, a step
 * at a time when asked, and hands out a {@link Snapshot} of one example whenever something has changed. Nothing else
 * touches the model while the thread lives.
 */
public final class Run {
    /** Steps a second a run can be asked for; 0 is as fast as it goes. */
    public static final int[] PACES = {2, 10, 50, 0};
    /** Learning rates the dial stops at. */
    public static final float[] RATES = {0.0003f, 0.001f, 0.003f, 0.01f, 0.03f, 0.1f, 0.3f, 1f};
    public static final int BATCH = 32;
    private static final long SHOW_NANOS = 200_000_000L;
    private static final long TABLES_NANOS = 1_000_000_000L;
    private static final int MOST_POINTS = 240;
    private static final int TEST_BATCHES = 8;
    /** How many tokens of its own writing a model shows. */
    private static final int WRITTEN = 40;

    public final Arch arch;
    public final Task practice, test;
    public final CompiledGraph model;
    private final Trainer trainer;
    private final int threads;
    /** How many of an example's tokens are shown, from its first. */
    private final int lanes;
    /** Steps the weights had taken before this run began. */
    private final int before;

    private volatile boolean running;
    private volatile int pace = PACES[1];
    private volatile float rate;
    private volatile long example;
    private final AtomicInteger owed = new AtomicInteger();
    private volatile boolean stop, dirty = true, diverged;
    private final AtomicReference<Snapshot> latest = new AtomicReference<>();
    private Thread thread;

    private final List<Snapshot.Point> curve = new ArrayList<>();
    private float testLoss = Float.NaN, score = Float.NaN;
    private boolean passed;
    /** The test loss that passes, in place of the test's own target; or 0. */
    private volatile double lossGoal;
    private float trainLoss = Float.NaN;
    private double stepNanos;

    /**
     * @param model   the weights to go on from
     * @param example which example is shown
     * @param lanes   how many of its tokens
     * @param before  steps the weights have already taken
     * @param curve   the run so far
     */
    public Run(Arch arch, CompiledGraph model, Task practice, Task test, long seed, float rate, long example, int lanes, int before,
               List<Snapshot.Point> curve, int threads) {
        this.arch = arch;
        this.model = model;
        this.practice = practice;
        this.test = test;
        this.rate = rate;
        this.example = example;
        this.threads = threads;
        this.lanes = Math.min(lanes, test.context);
        this.before = before;
        this.curve.addAll(curve);
        this.trainer = new Trainer(model, practice, seed, rate, BATCH, 1f);
    }

    public synchronized void start() {
        if (thread == null) {
            thread = new Thread(this::work, "Residual hall");
            thread.setDaemon(true);
            thread.start();
        }
    }

    /** Ends the thread and waits for it; afterwards the model is the caller's. */
    public synchronized void close() {
        stop = true;
        if (thread != null) {
            LockSupport.unpark(thread);
            try {
                thread.join(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
    }

    public boolean running() {
        return running;
    }

    public void running(boolean on) {
        running = on && !diverged;
        wake();
    }

    /** Takes {@code steps} more steps, then stops. */
    public void step(int steps) {
        if (!diverged) {
            running = false;
            owed.addAndGet(steps);
            wake();
        }
    }

    public int pace() {
        return pace;
    }

    public void pace(int stepsPerSecond) {
        pace = stepsPerSecond;
        wake();
    }

    public float rate() {
        return rate;
    }

    public void rate(float rate) {
        this.rate = rate;
        wake();
    }

    public long example() {
        return example;
    }

    public void example(long example) {
        this.example = example;
        wake();
    }

    public int steps() {
        return before + trainer.step();
    }

    /** The run so far; only once the thread has ended. */
    public List<Snapshot.Point> curve() {
        return List.copyOf(curve);
    }

    /** Sets the test loss that passes, in place of the test's own target. */
    public void lossGoal(double loss) {
        lossGoal = loss;
    }

    public boolean passed() {
        return passed;
    }

    /** The newest picture since the last call, or null. */
    public Snapshot poll() {
        return latest.getAndSet(null);
    }

    private void wake() {
        dirty = true;
        Thread t = thread;
        if (t != null) {
            LockSupport.unpark(t);
        }
    }

    private void work() {
        ForkJoinPool pool = threads > 1 ? new ForkJoinPool(threads) : null;
        try {
            Parallel.with(pool, this::loop);
        } catch (Throwable t) {
            Residual.LOG.error("A hall's run crashed", t);
        } finally {
            if (pool != null) {
                pool.shutdown();
            }
        }
    }

    private void loop() {
        long shown = 0, tabled = 0, due = System.nanoTime();
        int sinceTest = 0;
        test();
        while (!stop) {
            boolean stepping = owed.get() > 0;
            if ((running || stepping) && !diverged) {
                int p = pace;
                long now = System.nanoTime();
                if (!stepping && p > 0 && now < due) {
                    LockSupport.parkNanos(Math.min(due - now, SHOW_NANOS));
                } else {
                    due = p > 0 ? Math.max(due, now - 1_000_000_000L) + 1_000_000_000L / p : now;
                    trainer.learningRate = rate * practice.rateScale();
                    long t0 = System.nanoTime();
                    float loss = trainer.train();
                    long took = System.nanoTime() - t0;
                    stepNanos = stepNanos == 0 ? took : 0.9 * stepNanos + 0.1 * took;
                    if (stepping) {
                        owed.decrementAndGet();
                    }
                    if (!Float.isFinite(loss)) {
                        diverged = true;
                        running = false;
                        owed.set(0);
                    } else {
                        trainLoss = Float.isNaN(trainLoss) || stepping ? loss : 0.9f * trainLoss + 0.1f * loss;
                        // a watched pace is tested at every step it shows; a fast one a few times a second
                        int every = stepping || p > 0 && p <= 10 ? 1 : p > 0 ? 10 : 25;
                        if (++sinceTest >= every) {
                            sinceTest = 0;
                            test();
                        }
                    }
                    dirty = true;
                }
            } else if (!dirty) {
                LockSupport.parkNanos(SHOW_NANOS);
            }
            long now = System.nanoTime();
            boolean idle = !(running || owed.get() > 0);
            if (dirty && (idle || pace > 0 && pace <= 10 || now - shown >= SHOW_NANOS)) {
                dirty = false;
                boolean tables = idle || now - tabled >= TABLES_NANOS;
                latest.set(picture(tables));
                shown = now;
                if (tables) {
                    tabled = now;
                }
            }
        }
    }

    private void test() {
        Trainer.Eval eval = Trainer.evaluate(model, test, TEST_BATCHES);
        testLoss = (float) eval.loss();
        score = test.metric == Task.Metric.ACCURACY ? (float) eval.accuracy() : Float.NaN;
        passed = (lossGoal > 0 ? eval.loss() <= lossGoal : test.passes(eval.loss(), eval.accuracy())) && eval.causal();
        curve.add(new Snapshot.Point(steps(), trainLoss, testLoss, score));
        if (curve.size() > MOST_POINTS) {
            // keep the whole run in view at half the detail
            List<Snapshot.Point> kept = new ArrayList<>();
            for (int i = 0; i < curve.size(); i += 2) {
                kept.add(curve.get(i));
            }
            curve.clear();
            curve.addAll(kept);
        }
    }

    /** One example through the model as it is now. */
    private Snapshot picture(boolean withTables) {
        Batch one = test.batch(new Random(example), 1, true);
        int time = one.input().time;
        Captures seen = new Captures();
        Tensor logits = model.forward(null, one.input(), seen);
        int vocab = logits.dim(-1);
        Tensor probs = Ops.softmax(null, logits);
        byte[] chances = new byte[lanes * vocab];
        float[] laneLoss = new float[lanes];
        boolean[] scored = new boolean[lanes];
        for (int t = 0; t < lanes; t++) {
            for (int v = 0; v < vocab; v++) {
                chances[t * vocab + v] = (byte) Math.round(probs.data[t * vocab + v] * 255);
            }
            laneLoss[t] = (float) -Math.log(Math.max(probs.data[t * vocab + one.targets()[t]], 1e-9f));
            scored[t] = one.weights() == null || one.weights()[t] != 0f;
        }
        int width = arch.width();
        Snapshot.Grid embed = grid(seen, Arch.embed(), lanes, width);
        Snapshot.Grid position = null;
        int place = Arch.node(model.spec, Arch.position());
        if (place >= 0) {
            position = Snapshot.Grid.of(model.params(place).get("weight").data, 0, lanes, width);
        }
        List<Snapshot.PartView> parts = new ArrayList<>();
        for (Arch.Part p : arch.parts()) {
            int node = Arch.node(model.spec, Arch.part(p.id()));
            if (node < 0) {
                continue;
            }
            byte[] pattern = null;
            int heads = 0;
            Tensor w = seen.patterns.get(node);
            if (w != null) {
                heads = w.rank() == 4 ? w.dim(1) : 1;
                pattern = new byte[heads * lanes * lanes];
                for (int h = 0; h < heads; h++) {
                    for (int to = 0; to < lanes; to++) {
                        for (int from = 0; from < lanes; from++) {
                            pattern[(h * lanes + to) * lanes + from] = (byte) Math.round(w.data[(h * time + to) * time + from] * 255);
                        }
                    }
                }
            }
            parts.add(new Snapshot.PartView(p.id(), grid(seen, Arch.part(p.id()), lanes, width), grid(seen, Arch.after(p.id()), lanes, width), heads, pattern));
        }
        Snapshot.Tables tables = null;
        if (withTables) {
            Tensor e = model.params(Arch.node(model.spec, Arch.embed())).get("weight");
            Tensor r = model.params(Arch.node(model.spec, Arch.logits())).get("weight");
            // the readout's weights are width by vocabulary; shown the way the embedding is, a row a token
            float[] turned = new float[r.data.length];
            for (int i = 0; i < width; i++) {
                for (int v = 0; v < vocab; v++) {
                    turned[v * width + i] = r.data[i * vocab + v];
                }
            }
            Snapshot.Grid places = place < 0 ? null : Snapshot.Grid.of(model.params(place).get("weight").data, 0, model.env.context(), width);
            Snapshot.Grid follows = null;
            if (arch.parts().isEmpty()) {
                // each letter alone through the model: for a table, that is all there is to it
                int[] letters = Snapshot.letters(test.vocab);
                Tensor each = Ops.softmax(null, model.forward(null, new io.github.metrovoc.residual.tensor.Tokens(letters, letters.length, 1), null));
                float[] cells = new float[letters.length * letters.length];
                for (int a = 0; a < letters.length; a++) {
                    for (int b = 0; b < letters.length; b++) {
                        cells[a * letters.length + b] = each.data[a * vocab + letters[b]];
                    }
                }
                follows = Snapshot.Grid.of(cells, 0, letters.length, letters.length);
            }
            int[] written = test instanceof io.github.metrovoc.residual.learn.TextTask ? write(one.input().ids[0], vocab) : null;
            tables = new Snapshot.Tables(Snapshot.Grid.of(e.data, 0, vocab, width), Snapshot.Grid.of(turned, 0, vocab, width), places, follows, written);
        }
        Snapshot.State state = diverged ? Snapshot.State.DIVERGED : running || owed.get() > 0 ? Snapshot.State.RUNNING : Snapshot.State.PAUSED;
        return new Snapshot(steps(), state, pace, rate, trainLoss, testLoss, score, passed, model.parameterCount(), (float) (stepNanos / 1e6),
                java.util.Arrays.copyOf(one.input().ids, lanes), java.util.Arrays.copyOf(one.targets(), lanes), scored, embed, position, parts, vocab, chances, laneLoss, tables, List.copyOf(curve));
    }

    /**
     * What the model writes on from {@code first}: each token drawn by the chances the model gives, with the same
     * draws every time, so the writing changes only as the model does.
     */
    private int[] write(int first, int vocab) {
        Random draws = new Random(example);
        int[] ids = new int[WRITTEN];
        ids[0] = first;
        for (int n = 1; n < WRITTEN; n++) {
            int from = Math.max(0, n - test.context);
            Tensor probs = Ops.softmax(null, model.forward(null,
                    new io.github.metrovoc.residual.tensor.Tokens(java.util.Arrays.copyOfRange(ids, from, n), 1, n - from), null));
            int last = (n - from - 1) * vocab;
            float at = draws.nextFloat(), sum = 0;
            int pick = vocab - 1;
            for (int v = 0; v < vocab; v++) {
                sum += probs.data[last + v];
                if (at < sum) {
                    pick = v;
                    break;
                }
            }
            ids[n] = pick;
        }
        return ids;
    }

    private Snapshot.Grid grid(Captures seen, String name, int lanes, int width) {
        Tensor t = seen.outputs.get(Arch.node(model.spec, name));
        return Snapshot.Grid.of(t.data, 0, lanes, width);
    }

    /** The weights as they stand, by node name; only once the thread has ended. */
    public void keep(Map<String, Map<String, Tensor>> bank) {
        Arch.keep(model, bank);
    }
}
