package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Config;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.tensor.Parallel;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * What the smallest models show, and how fast: which parts a task cannot be passed without, and how long a player
 * waits to see it. Run with {@code RESIDUAL_FIRST_LOOP} set to the names of the runs wanted (or {@code all}).
 */
@EnabledIfEnvironmentVariable(named = "RESIDUAL_FIRST_LOOP", matches = ".+")
class FirstLoopCalibrationTest {
    private static final Set<String> WANTED = Set.of(System.getenv().getOrDefault("RESIDUAL_FIRST_LOOP", "").split(","));
    private static final int SEEDS = Integer.parseInt(System.getenv().getOrDefault("RESIDUAL_SEEDS", "3"));

    private static TextTask whole;

    /** All the archive's text. */
    private static synchronized TextTask archive() {
        if (whole == null) {
            Map<Integer, BitSet> all = new HashMap<>();
            for (Archive.Record r : Archive.records()) {
                all.put(r.id(), new BitSet());
            }
            whole = Archive.practice(all, Vocab.WORLD);
        }
        return whole;
    }

    private static boolean wanted(String name) {
        return WANTED.contains("all") || WANTED.contains(name);
    }

    /** Position, then {@code blocks} of attention (and a feed-forward block if asked), each added to the stream. */
    static GraphSpec stack(int dim, int blocks, int heads, boolean position, boolean norm, boolean mlp) {
        GraphSpec g = new GraphSpec();
        String d = Integer.toString(dim);
        int x = g.add("embed", Op.EMBEDDING, Config.of(Op.EMBEDDING, "dim", d), GraphSpec.TOKENS);
        if (position) {
            x = g.add("x", Op.ADD, x, g.add("pos", Op.POSITION, Config.of(Op.POSITION, "dim", d)));
        }
        for (int i = 1; i <= blocks; i++) {
            int in = norm ? g.add("norm_a" + i, Op.NORM, x) : x;
            int a = g.add("attn" + i, Op.ATTENTION, Config.of(Op.ATTENTION, "heads", Integer.toString(heads)), in);
            x = g.add("res_a" + i, Op.ADD, x, a);
            if (mlp) {
                int in2 = norm ? g.add("norm_f" + i, Op.NORM, x) : x;
                x = g.add("res_f" + i, Op.ADD, x, g.add("ffn" + i, Op.MLP, in2));
            }
        }
        int out = norm ? g.add("norm_out", Op.NORM, x) : x;
        g.output = g.add("logits", Op.LINEAR, Config.of(Op.LINEAR, "out", "vocab"), out);
        return g;
    }

    /** A loss to reach in place of the test's own target, or 0. */
    private static double lossTarget;

    /** Trains from {@link #SEEDS} starts and prints, for each, the first check that passed, the last score and the pace. */
    static void run(String label, GraphSpec spec, Benchmark bench, float rate, int steps, int every) throws Exception {
        Task practice = bench == Benchmark.READING ? archive() : bench.practice();
        Task test = bench.test(practice.vocab);
        StringBuilder line = new StringBuilder(String.format("%-34s", label));
        for (long seed = 1; seed <= SEEDS; seed++) {
            CompiledGraph g = CompiledGraph.compile(spec, new Env(practice.vocab, Vocab.CONTEXT), null, seed);
            Trainer tr = new Trainer(g, practice, seed, rate, 32, 1f);
            ForkJoinPool pool = new ForkJoinPool(4);
            int[] passed = {-1};
            double[] last = {Double.NaN};
            long t0 = System.nanoTime();
            long[] trainNs = {0};
            Parallel.with(pool, () -> {
                for (int s = 1; s <= steps && passed[0] < 0; s++) {
                    long a = System.nanoTime();
                    float loss = tr.train();
                    trainNs[0] += System.nanoTime() - a;
                    if (Float.isNaN(loss)) {
                        last[0] = Double.NaN;
                        passed[0] = -2;
                        break;
                    }
                    if (s % every == 0 || s == steps) {
                        Trainer.Eval ev = Trainer.evaluate(g, test, 8);
                        last[0] = test.score(ev.loss(), ev.accuracy());
                        if ((lossTarget > 0 ? ev.loss() <= lossTarget : test.passes(ev.loss(), ev.accuracy())) && ev.causal()) {
                            passed[0] = s;
                        }
                    }
                }
            });
            pool.shutdown();
            int ran = passed[0] > 0 ? passed[0] : steps;
            if (seed == 1) {
                line.append(String.format(" params %6d  %5.1f ms/step |", g.parameterCount(), trainNs[0] / 1e6 / ran));
            }
            line.append(passed[0] > 0 ? String.format(" pass@%-5d(%4.1fs)", passed[0], (System.nanoTime() - t0) / 1e9)
                    : passed[0] == -2 ? " diverged          " : String.format(" no %.3f          ", last[0]));
        }
        System.out.println(line);
    }

    /** How soon a table reaches what a table can: the first stage's goal and the time it takes. */
    @Test
    void table() throws Exception {
        if (!wanted("table")) {
            return;
        }
        try {
            for (double target : new double[]{2.45, 2.40}) {
                lossTarget = target;
                System.out.println("== A table on reading, to loss " + target + " ==");
                for (int dim : new int[]{8, 16, 32}) {
                    for (float rate : new float[]{0.003f, 0.01f}) {
                        run("embed " + dim + " read out, rate " + rate, stack(dim, 0, 1, false, true, false), Benchmark.READING, rate, 4000, 50);
                    }
                }
            }
        } finally {
            lossTarget = 0;
        }
    }

    @Test
    void reversal() throws Exception {
        if (!wanted("reversal")) {
            return;
        }
        System.out.println("== Reversal (target " + Benchmark.REVERSAL.target + ") ==");
        run("bigram table", Blueprints.bigram(), Benchmark.REVERSAL, 0.01f, 600, 50);
        run("context 3 letters, 16", Blueprints.context(3, 16, 32), Benchmark.REVERSAL, 0.01f, 600, 50);
        for (int dim : new int[]{8, 16, 32}) {
            run("attn 1x1 dim " + dim + " pos norm", stack(dim, 1, 1, true, true, false), Benchmark.REVERSAL, 0.01f, 1500, 25);
            run("attn 1x1 dim " + dim + " pos norm mlp", stack(dim, 1, 1, true, true, true), Benchmark.REVERSAL, 0.01f, 1500, 25);
        }
        run("attn 1x1 dim 16 NO position", stack(16, 1, 1, false, true, true), Benchmark.REVERSAL, 0.01f, 1500, 50);
        run("attn 1x1 dim 16 pos NO norm", stack(16, 1, 1, true, false, true), Benchmark.REVERSAL, 0.01f, 1500, 50);
        run("attn 1x2 dim 16 pos norm mlp", stack(16, 1, 2, true, true, true), Benchmark.REVERSAL, 0.01f, 1500, 25);
    }

    @Test
    void rates() throws Exception {
        if (!wanted("rates")) {
            return;
        }
        System.out.println("== Reversal, attn 1x1 dim 16, by learning rate ==");
        for (float rate : new float[]{0.0003f, 0.001f, 0.003f, 0.01f, 0.03f, 0.1f, 0.3f, 1f}) {
            run("rate " + rate + " norm", stack(16, 1, 1, true, true, true), Benchmark.REVERSAL, rate, 1500, 25);
        }
        for (float rate : new float[]{0.003f, 0.01f, 0.03f, 0.1f, 0.3f}) {
            run("rate " + rate + " NO norm", stack(16, 1, 1, true, false, true), Benchmark.REVERSAL, rate, 1500, 25);
        }
    }

    @Test
    void induction() throws Exception {
        if (!wanted("induction")) {
            return;
        }
        System.out.println("== Induction (target " + Benchmark.INDUCTION.target + ") ==");
        run("context 3 letters", Blueprints.context(3, 16, 32), Benchmark.INDUCTION, 0.01f, 600, 100);
        for (int dim : new int[]{16, 32}) {
            run("1 layer dim " + dim, stack(dim, 1, 1, true, true, false), Benchmark.INDUCTION, 0.003f, 3000, 100);
            run("2 layers dim " + dim, stack(dim, 2, 1, true, true, false), Benchmark.INDUCTION, 0.003f, 3000, 100);
            run("2 layers dim " + dim + " mlp", stack(dim, 2, 1, true, true, true), Benchmark.INDUCTION, 0.003f, 3000, 100);
        }
    }

    @Test
    void recall() throws Exception {
        if (!wanted("recall")) {
            return;
        }
        System.out.println("== Recall (target " + Benchmark.RECALL.target + ") ==");
        run("context 3 letters", Blueprints.context(3, 16, 32), Benchmark.RECALL, 0.01f, 600, 100);
        for (int dim : new int[]{16, 32}) {
            run("1 layer dim " + dim, stack(dim, 1, 1, true, true, true), Benchmark.RECALL, 0.003f, 3000, 100);
            run("2 layers dim " + dim, stack(dim, 2, 1, true, true, true), Benchmark.RECALL, 0.003f, 3000, 100);
        }
    }

    @Test
    void reading() throws Exception {
        if (!wanted("reading")) {
            return;
        }
        System.out.println("== Reading the archive: test loss, nats a letter ==");
        run("bigram table", Blueprints.bigram(), Benchmark.READING, 0.01f, 1000, 250);
        run("context 3 letters 16/64", Blueprints.context(3, 16, 64), Benchmark.READING, 0.01f, 1000, 250);
        run("context 8 letters 16/128", Blueprints.context(8, 16, 128), Benchmark.READING, 0.01f, 1000, 250);
        run("gpt 16 x1 h1", Blueprints.gpt(16, 1, 1), Benchmark.READING, 0.01f, 1000, 250);
        run("gpt 32 x2 h2", Blueprints.gpt(32, 2, 2), Benchmark.READING, 0.01f, 1000, 250);
    }

    /** The solutions a challenge expects, and the designs that must not pass it, from many starts. */
    @Test
    void reliability() throws Exception {
        if (!wanted("reliability")) {
            return;
        }
        System.out.println("== Reversal ==");
        run("1 layer 2 heads dim 16 pos", stack(16, 1, 2, true, true, true), Benchmark.REVERSAL, 0.003f, 2000, 25);
        run("1 layer 1 head dim 16 pos", stack(16, 1, 1, true, true, true), Benchmark.REVERSAL, 0.003f, 2000, 25);
        run("1 layer 2 heads dim 16 NO pos", stack(16, 1, 2, false, true, true), Benchmark.REVERSAL, 0.003f, 2000, 100);
        run("2 layers 2 heads dim 16 NO pos", stack(16, 2, 2, false, true, true), Benchmark.REVERSAL, 0.003f, 3000, 100);
        run("3 layers 2 heads dim 32 NO pos", stack(32, 3, 2, false, true, true), Benchmark.REVERSAL, 0.003f, 3000, 100);
        run("NO attention: context 4, 32/128", Blueprints.context(4, 32, 128), Benchmark.REVERSAL, 0.003f, 2000, 100);
        System.out.println("== Induction ==");
        run("2 layers 1 head dim 32", stack(32, 2, 1, true, true, false), Benchmark.INDUCTION, 0.003f, 4000, 100);
        run("2 layers 2 heads dim 32", stack(32, 2, 2, true, true, false), Benchmark.INDUCTION, 0.003f, 4000, 100);
        run("1 layer 4 heads dim 64 mlp", stack(64, 1, 4, true, true, true), Benchmark.INDUCTION, 0.003f, 3000, 100);
        run("2 layers 1 head dim 32 NO pos", stack(32, 2, 1, false, true, false), Benchmark.INDUCTION, 0.003f, 4000, 100);
    }

    /** Ways round a stage's idea that a player would try: wider, more heads, longer. None may pass. */
    @Test
    void hostile() throws Exception {
        if (!wanted("hostile")) {
            return;
        }
        System.out.println("== Reversal without attention, pushed ==");
        run("feed-forward x3 dim 64 pos", noAttention(64, 3), Benchmark.REVERSAL, 0.003f, 5000, 250);
        System.out.println("== Reading: a table through a stream ==");
        for (int dim : new int[]{8, 16, 32, 64}) {
            run("embed " + dim + " read out", stack(dim, 0, 1, false, true, false), Benchmark.READING, 0.01f, 1000, 250);
        }
        run("feed-forward x2 dim 32", noAttention(32, 2), Benchmark.READING, 0.01f, 1000, 250);
        System.out.println("== Induction with one layer, pushed ==");
        run("1 layer 8 heads dim 64 mlp, 10000", stack(64, 1, 8, true, true, true), Benchmark.INDUCTION, 0.003f, 10000, 500);
        run("1 layer 4 heads dim 64 mlp, rate .001", stack(64, 1, 4, true, true, true), Benchmark.INDUCTION, 0.001f, 10000, 500);
    }

    /** Positions and feed-forward parts only: nothing carries one token's stream to another's. */
    static GraphSpec noAttention(int dim, int blocks) {
        GraphSpec g = new GraphSpec();
        String d = Integer.toString(dim);
        int x = g.add("embed", Op.EMBEDDING, Config.of(Op.EMBEDDING, "dim", d), GraphSpec.TOKENS);
        x = g.add("x", Op.ADD, x, g.add("pos", Op.POSITION, Config.of(Op.POSITION, "dim", d)));
        for (int i = 1; i <= blocks; i++) {
            x = g.add("res_f" + i, Op.ADD, x, g.add("ffn" + i, Op.MLP, g.add("norm_f" + i, Op.NORM, x)));
        }
        g.output = g.add("logits", Op.LINEAR, Config.of(Op.LINEAR, "out", "vocab"), g.add("norm_out", Op.NORM, x));
        return g;
    }
}
