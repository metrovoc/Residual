package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Config;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.tensor.Parallel;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Trains blueprints and wrecks on the benchmarks' practice and prints their test scores, to set targets and budgets.
 * Run with {@code RESIDUAL_CALIBRATE} set to the names of the runs wanted (or {@code all}).
 */
@EnabledIfEnvironmentVariable(named = "RESIDUAL_CALIBRATE", matches = ".+")
class CalibrationTest {
    static final Env WORLD = new Env(Vocab.WORLD.size(), Vocab.CONTEXT);

    static void run(String label, GraphSpec spec, Task practice, List<Benchmark> tested, double target, Wrecks.Settings st,
                    int steps, int every) throws Exception {
        CompiledGraph g = CompiledGraph.compile(spec, WORLD, null, 1);
        Trainer tr = new Trainer(g, practice, 1, st.learningRate(), st.batchSize(), st.clip());
        ForkJoinPool pool = new ForkJoinPool(4);
        long t0 = System.nanoTime();
        Parallel.with(pool, () -> {
            double ema = Double.NaN;
            for (int s = 1; s <= steps; s++) {
                float loss = tr.train();
                ema = Double.isNaN(ema) ? loss : 0.95 * ema + 0.05 * loss;
                if (s % every == 0 || s == steps) {
                    double secs = (System.nanoTime() - t0) / 1e9;
                    StringBuilder line = new StringBuilder(String.format("%-14s step %5d  train %.3f", label, s, ema));
                    for (Benchmark a : tested) {
                        Trainer.Eval ev = Trainer.evaluate(g, a.test(), 8);
                        double score = a.test().score(ev.loss(), ev.accuracy());
                        boolean pass = ev.causal() && (a.metric == Task.Metric.LOSS ? score <= target : score >= target);
                        line.append(String.format("  %s %.3f%s%s", a.id, score, pass ? "✔" : "", ev.causal() ? "" : " (reads ahead)"));
                    }
                    line.append(String.format("  %.1fs (%.1f ms/step) params %d", secs, secs * 1000 / s, g.parameterCount()));
                    System.out.println(line);
                }
            }
        });
        pool.shutdown();
    }

    static void run(String label, GraphSpec spec, Benchmark a, Wrecks.Settings st, int steps, int every) throws Exception {
        Task practice = a == Benchmark.READING ? reading() : Dataset.of(a);
        run(label, spec, practice, List.of(a), a.target, st, steps, every);
    }

    /** How many held-out blocks a reading model would restore, at a few thresholds, as it trains. */
    static void restore(String label, GraphSpec spec, int steps, int every) throws Exception {
        CompiledGraph g = CompiledGraph.compile(spec, WORLD, null, 1);
        Wrecks.Settings st = Wrecks.Settings.DEFAULT;
        Trainer tr = new Trainer(g, reading(), 1, st.learningRate(), st.batchSize(), st.clip());
        float[] at = {0.4f, 0.5f, 0.6f, 0.7f};
        ForkJoinPool pool = new ForkJoinPool(4);
        Parallel.with(pool, () -> {
            for (int s = 1; s <= steps; s++) {
                tr.train();
                if (s % every == 0) {
                    Trainer.Eval ev = Trainer.evaluate(g, Benchmark.READING.test(), 8);
                    int[] over = new int[at.length];
                    int blocks = 0;
                    double sum = 0;
                    for (Archive.Record r : Archive.records()) {
                        if (!Archive.heldOut(r.id())) {
                            continue;
                        }
                        for (int b = 0; b < r.blocks(); b++) {
                            float acc = Reading.accuracy(g, r.text(), b);
                            sum += acc;
                            blocks++;
                            for (int i = 0; i < at.length; i++) {
                                over[i] += acc >= at[i] ? 1 : 0;
                            }
                        }
                    }
                    StringBuilder line = new StringBuilder(String.format("%-14s step %5d  loss %.3f  mean %.3f", label, s,
                            ev.loss(), sum / blocks));
                    for (int i = 0; i < at.length; i++) {
                        line.append(String.format("  >=%.1f %.0f%%", at[i], 100.0 * over[i] / blocks));
                    }
                    line.append(String.format("  params %d", g.parameterCount()));
                    System.out.println(line);
                }
            }
        });
        pool.shutdown();
    }

    /** Reading's test loss when a model has only the first {@code records} shard-borne records to practise on. */
    static void collected(String label, GraphSpec spec, int records, int steps, int every) throws Exception {
        Map<Integer, BitSet> some = new HashMap<>();
        for (Archive.Record r : Archive.records()) {
            if (!Archive.heldOut(r.id()) && some.size() < records) {
                some.put(r.id(), new BitSet());
            }
        }
        TextTask practice = Archive.practice(some);
        long chars = practice.characters();
        run(label + "_" + records + "r_" + chars / 1000 + "k", spec, practice, List.of(Benchmark.READING), Benchmark.READING.target,
                Wrecks.Settings.DEFAULT, steps, every);
    }

    /** Every record a player could recover, whole. */
    static TextTask reading() {
        Map<Integer, BitSet> all = new HashMap<>();
        for (Archive.Record r : Archive.records()) {
            all.put(r.id(), new BitSet());
        }
        return Archive.practice(all);
    }

    static GraphSpec fixedShape() {
        GraphSpec g = new GraphSpec();
        int e = g.add("embed", Op.EMBEDDING, Config.of(Op.EMBEDDING, "dim", "32"), GraphSpec.TOKENS);
        g.output = g.add("logits", Op.LINEAR, Config.of(Op.LINEAR, "out", "vocab"), e);
        return g;
    }

    static GraphSpec fixedLeak() {
        GraphSpec g = new GraphSpec();
        int e = g.add("embed", Op.EMBEDDING, Config.of(Op.EMBEDDING, "dim", "32"), GraphSpec.TOKENS);
        int pos = g.add("pos", Op.POSITION, Config.of(Op.POSITION, "dim", "32"));
        int x = g.add("x", Op.ADD, e, pos);
        int q = g.add("query", Op.LINEAR, Config.of(Op.LINEAR, "out", "32", "bias", "false"), x);
        int qs = g.add("q_scaled", Op.SCALE, q);
        int k = g.add("key", Op.LINEAR, Config.of(Op.LINEAR, "out", "32", "bias", "false"), x);
        int v = g.add("value", Op.LINEAR, Config.of(Op.LINEAR, "out", "32", "bias", "false"), x);
        int s = g.add("scores", Op.MATMUL, Config.of(Op.MATMUL, "transpose", "true"), qs, k);
        int p = g.add("weights", Op.SOFTMAX, g.add("mask", Op.CAUSAL_MASK, s));
        int o = g.add("mixed", Op.MATMUL, p, v);
        int r = g.add("residual", Op.ADD, x, o);
        int h = g.add("hidden", Op.LINEAR, Config.of(Op.LINEAR, "out", "128"), r);
        int a = g.add("act", Op.ACTIVATION, Config.of(Op.ACTIVATION, "fn", "relu"), h);
        g.output = g.add("logits", Op.LINEAR, Config.of(Op.LINEAR, "out", "vocab"), a);
        return g;
    }

    /** The order wreck with the Position Table it was missing. */
    static GraphSpec fixedOrder() {
        GraphSpec g = new GraphSpec();
        int e = g.add("embed", Op.EMBEDDING, Config.of(Op.EMBEDDING, "dim", "32"), GraphSpec.TOKENS);
        int x = g.add("x", Op.ADD, e, g.add("pos", Op.POSITION, Config.of(Op.POSITION, "dim", "32")));
        int a = g.add("attn", Op.ATTENTION, Config.of(Op.ATTENTION, "heads", "2"), g.add("norm1", Op.NORM, x));
        int r1 = g.add("res1", Op.ADD, x, a);
        int f = g.add("ffn", Op.MLP, g.add("norm2", Op.NORM, r1));
        int r2 = g.add("res2", Op.ADD, r1, f);
        g.output = g.add("logits", Op.LINEAR, Config.of(Op.LINEAR, "out", "vocab"), g.add("norm_out", Op.NORM, r2));
        return g;
    }

    static GraphSpec fixedDepth(String fn, boolean shortcuts) {
        GraphSpec g = new GraphSpec();
        int e = g.add("embed", Op.EMBEDDING, Config.of(Op.EMBEDDING, "dim", "16"), GraphSpec.TOKENS);
        int s1 = g.add("prev1", Op.SHIFT, Config.of(Op.SHIFT, "steps", "1"), e);
        int s2 = g.add("prev2", Op.SHIFT, Config.of(Op.SHIFT, "steps", "2"), e);
        int x = g.add("context", Op.CONCAT, g.add("pair", Op.CONCAT, e, s1), s2);
        for (int i = 1; i <= 6; i++) {
            int h = g.add("layer" + i, Op.LINEAR, Config.of(Op.LINEAR, "out", "48"), x);
            int a = g.add("squash" + i, Op.ACTIVATION, Config.of(Op.ACTIVATION, "fn", fn), h);
            x = shortcuts ? g.add("res" + i, Op.ADD, x, a) : a;
        }
        g.output = g.add("logits", Op.LINEAR, Config.of(Op.LINEAR, "out", "vocab"), x);
        return g;
    }

    @Test
    void calibrate() throws Exception {
        String which = System.getenv("RESIDUAL_CALIBRATE");
        boolean all = which.equals("all");
        Wrecks.Settings st = Wrecks.Settings.DEFAULT;
        if (all || which.contains("reacting")) {
            run("reacting", Blueprints.bigram(), Benchmark.REACTING, st, 1500, 250);
        }
        if (all || which.contains("recall")) {
            run("recall", Blueprints.recall(), Benchmark.RECALL, st, 3000, 250);
        }
        if (all || which.contains("reversal")) {
            run("reversal", Blueprints.reverse(), Benchmark.REVERSAL, st, 3000, 250);
        }
        if (all || which.contains("induction")) {
            run("induction", Blueprints.induction(), Benchmark.INDUCTION, st, 4000, 250);
        }
        if (all || which.contains("orders")) {
            run("orders", Blueprints.gpt(64, 2, 4), Benchmark.ORDERS, st, 4000, 250);
        }
        if (which.contains("small_orders")) {
            run("orders32", Blueprints.gpt(32, 2, 4), Benchmark.ORDERS, st, 4000, 500);
        }
        if (all || which.contains("reading")) {
            run("reading", Blueprints.gpt(64, 2, 4), Benchmark.READING, st, 4000, 250);
        }
        if (which.contains("weak_reading")) {
            run("reading_ctx", Blueprints.context(), Benchmark.READING, st, 3000, 500);
        }
        if (which.contains("collected")) {
            for (int n : new int[]{30, 60, 120}) {
                collected("gpt32", Blueprints.gpt(32, 2, 4), n, 3000, 1000);
            }
            collected("gpt64", Blueprints.gpt(64, 2, 4), 60, 3000, 1000);
        }
        if (which.contains("restore")) {
            restore("restore64", Blueprints.gpt(64, 2, 4), 4000, 1000);
            restore("restore32", Blueprints.gpt(32, 2, 4), 4000, 1000);
            restore("restore_ctx", Blueprints.context(), 3000, 1000);
        }
        if (which.contains("generalist")) {
            List<Mixture.Source> sources = new ArrayList<>();
            for (Benchmark a : Benchmark.values()) {
                sources.add(new Mixture.Source(a, a == Benchmark.READING ? reading() : Dataset.of(a)));
            }
            run("generalist", Blueprints.gpt(64, 3, 4), new Mixture(sources), List.of(Benchmark.values()), Double.NaN, st, 12000, 1000);
        }
        if (which.contains("wrecks")) {
            for (Wrecks.Wreck w : Wrecks.ALL) {
                run("wreck_" + w.id(), w.machine().get(), w.practice(), List.of(w.benchmark()), w.target(), w.settings(),
                        Wrecks.BUDGET, 250);
            }
        }
        if (which.contains("fixes")) {
            run("shape_fix", fixedShape(), Dataset.of(Benchmark.REACTING), List.of(Benchmark.REACTING), 0.98, st, Wrecks.BUDGET, 250);
            run("diverge_fix", Blueprints.recall(), Dataset.of(Benchmark.RECALL), List.of(Benchmark.RECALL), 0.9, st, Wrecks.BUDGET, 250);
            run("leak_fix", fixedLeak(), Dataset.of(Benchmark.REACTING), List.of(Benchmark.REACTING), 0.98, st, Wrecks.BUDGET, 250);
            run("order_fix", fixedOrder(), Dataset.of(Benchmark.REVERSAL), List.of(Benchmark.REVERSAL), 0.9, st, Wrecks.BUDGET, 250);
            Task depth = Wrecks.byId("depth").practice();
            run("depth_relu", fixedDepth("relu", false), depth, List.of(Benchmark.READING), 2.4, st, Wrecks.BUDGET, 250);
            run("depth_res", fixedDepth("sigmoid", true), depth, List.of(Benchmark.READING), 2.4, st, Wrecks.BUDGET, 250);
        }
    }
}
