package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.CompileException;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Config;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.tensor.Parallel;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Trains blueprints and wrecks on the benchmarks' practice and prints their test scores, to set targets and budgets.
 * Run with {@code RESIDUAL_CALIBRATE} set to the names of the runs wanted (or {@code all}).
 */
@EnabledIfEnvironmentVariable(named = "RESIDUAL_CALIBRATE", matches = ".+")
class CalibrationTest {
    static final Env WORLD = Env.WORLD;

    static void run(String label, GraphSpec spec, Task practice, List<Benchmark> tested, double target, Wrecks.Settings st,
                    int steps, int every) throws Exception {
        run(label, spec, practice, tested, target, st, steps, every, 1);
    }

    /** As {@link #run}, from the weights and batches {@code seed} draws, as a rebuild in the game draws its own. */
    static void run(String label, GraphSpec spec, Task practice, List<Benchmark> tested, double target, Wrecks.Settings st,
                    int steps, int every, long seed) throws Exception {
        CompiledGraph g = CompiledGraph.compile(spec, new Env(practice.vocab, Vocab.CONTEXT), null, seed);
        Trainer tr = new Trainer(g, practice, seed, st.learningRate(), st.batchSize(), st.clip());
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
                        Trainer.Eval ev = Trainer.evaluate(g, a.test(practice.vocab), 8);
                        double score = a.test(practice.vocab).score(ev.loss(), ev.accuracy());
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

    /** Control, practised by playing melts: the share of looks a model's own melts hold in the band as it learns. */
    static void control(String label, GraphSpec spec, Wrecks.Settings st, int steps, int every) throws Exception {
        control(label, spec, st, steps, every, 1, 4);
    }

    static void control(String label, GraphSpec spec, Wrecks.Settings st, int steps, int every, long seed, int threads)
            throws Exception {
        Control task = new Control();
        CompiledGraph g = CompiledGraph.compile(spec, new Env(task.vocab, Vocab.CONTEXT), null, seed);
        Trainer tr = new Trainer(g, task, seed, st.learningRate(), st.batchSize(), st.clip());
        ForkJoinPool pool = new ForkJoinPool(threads);
        long t0 = System.nanoTime();
        Parallel.with(pool, () -> {
            for (int s = 1; s <= steps; s++) {
                tr.train();
                if (s % every == 0 || s == steps) {
                    double secs = (System.nanoTime() - t0) / 1e9;
                    double held = Trainer.evaluate(g, task, 1).accuracy();
                    StringBuilder line = new StringBuilder(String.format("%-18s step %5d  held %.3f%s", label, s, held,
                            held >= task.target ? "✔" : ""));
                    // melts as a Smelter plays them, one look at a time: per ore, the share held and the melts held to 90%
                    for (int ore = 0; ore < Control.ORES.length(); ore++) {
                        Random rng = new Random(77 + ore);
                        double sum = 0;
                        int full = 0;
                        for (int i = 0; i < 32; i++) {
                            Control.Melt m = new Control.Melt(ore);
                            while (!m.over()) {
                                m.act(Control.act(g, Control.prompt(m)), rng);
                            }
                            sum += m.score();
                            full += m.score() >= 0.9 ? 1 : 0;
                        }
                        line.append(String.format("  %c %.3f/%2d", Control.ORES.charAt(ore), sum / 32, full));
                    }
                    System.out.println(line.append(String.format("  %.1fs (%.1f ms/step)", secs, secs * 1000 / s)));
                }
            }
        });
        pool.shutdown();
    }

    /** Any task practised by playing: its test score as it learns. */
    static void played(String label, Played task, GraphSpec spec, Wrecks.Settings st, int steps, int every, long seed, int threads)
            throws Exception {
        CompiledGraph g = CompiledGraph.compile(spec, new Env(task.vocab, Vocab.CONTEXT), null, seed);
        Trainer tr = new Trainer(g, task, seed, st.learningRate(), st.batchSize(), st.clip());
        ForkJoinPool pool = new ForkJoinPool(threads);
        long t0 = System.nanoTime();
        Parallel.with(pool, () -> {
            for (int s = 1; s <= steps; s++) {
                tr.train();
                if (s % every == 0 || s == steps) {
                    double secs = (System.nanoTime() - t0) / 1e9;
                    double held = Trainer.evaluate(g, task, 1).accuracy();
                    System.out.println(String.format("%-18s step %5d  held %.3f%s  %.1fs (%.1f ms/step)", label, s, held,
                            held >= task.target ? "✔" : "", secs, secs * 1000 / s));
                }
            }
        });
        pool.shutdown();
    }

    static void run(String label, GraphSpec spec, Benchmark a, Wrecks.Settings st, int steps, int every) throws Exception {
        Task practice = a == Benchmark.READING ? reading() : a.practice();
        run(label, spec, practice, List.of(a), a.target, st, steps, every);
    }

    /** How many held-out blocks a reading model would restore, at a few thresholds, as it trains: every twentieth
     * held-out record's, across the whole archive. */
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
                    Trainer.Eval ev = Trainer.evaluate(g, Benchmark.READING.test(Vocab.WORLD), 8);
                    int[] over = new int[at.length];
                    int blocks = 0;
                    double sum = 0;
                    int seen = 0;
                    for (Archive.Record r : Archive.records()) {
                        if (!Archive.heldOut(r.id()) || seen++ % 20 != 0) {
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

    /** {@code records} shard-borne records drawn at random, as the shards a player finds roll them. */
    static Map<Integer, BitSet> found(int records) {
        List<Integer> ids = new ArrayList<>();
        for (Archive.Record r : Archive.records()) {
            if (!Archive.heldOut(r.id())) {
                ids.add(r.id());
            }
        }
        Collections.shuffle(ids, new Random(1));
        Map<Integer, BitSet> some = new HashMap<>();
        for (int id : ids.subList(0, Math.min(records, ids.size()))) {
            some.put(id, new BitSet());
        }
        return some;
    }

    /** Reading's test loss when a model has only {@code records} shard-borne records to practise on. */
    static void collected(String label, GraphSpec spec, int records, int steps, int every) throws Exception {
        Map<Integer, BitSet> some = found(records);
        TextTask practice = Archive.practice(some, Vocab.WORLD);
        long chars = practice.characters();
        run(label + "_" + records + "r_" + chars / 1000 + "k", spec, practice, List.of(Benchmark.READING), Benchmark.READING.target,
                Wrecks.Settings.DEFAULT, steps, every);
    }

    /** Reading's test loss with a tokenizer of {@code size} learned on {@code records} found records' text. */
    static void tokenized(String label, GraphSpec spec, int records, int size, int steps, int every) throws Exception {
        Map<Integer, BitSet> some = found(records);
        Bpe bpe = new Bpe(Archive.legible(some), Vocab.WORLD);
        while (bpe.vocab().size() < size && bpe.step()) {
        }
        TextTask practice = Archive.practice(some, bpe.vocab());
        run(String.format("%s_%dr_v%d", label, records, size), spec, practice, List.of(Benchmark.READING),
                Benchmark.READING.target, Wrecks.Settings.DEFAULT, steps, every);
    }

    /** Every record a player could recover, whole. */
    static TextTask reading() {
        Map<Integer, BitSet> all = new HashMap<>();
        for (Archive.Record r : Archive.records()) {
            all.put(r.id(), new BitSet());
        }
        return Archive.practice(all, Vocab.WORLD);
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
        List<String> runs = List.of(which.split("[ ,]+"));
        boolean all = runs.contains("all");
        Wrecks.Settings st = Wrecks.Settings.DEFAULT;
        if (runs.contains("pilot_winds")) {
            // which winds a two-block transformer can learn to meet and a model reading three characters cannot
            int[][] threes = {{3, -1}, {3, 1}}, slower = {{3, -1}, {4, -1}, {3, 1}, {4, 1}};
            List<Thread> runs2 = new ArrayList<>();
            for (Object[] r : new Object[][] {{"threes_context", threes, Blueprints.context()}, {"threes_gpt32", threes, Blueprints.gpt(32, 2, 4)},
                    {"slower_context", slower, Blueprints.context()}, {"slower_gpt32", slower, Blueprints.gpt(32, 2, 4)}}) {
                runs2.add(Thread.ofPlatform().start(() -> {
                    try {
                        played((String) r[0], new Pilot((int[][]) r[1], 2), (GraphSpec) r[2], st, 1500, 150, 1, 3);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));
            }
            for (Thread t : runs2) {
                t.join();
            }
        }
        if (runs.contains("pilot")) {
            List<Thread> pilots = new ArrayList<>();
            for (Object[] r : new Object[][] {{"pilot_context", Blueprints.context()}, {"pilot_gpt32", Blueprints.gpt(32, 2, 4)}}) {
                pilots.add(Thread.ofPlatform().start(() -> {
                    try {
                        played((String) r[0], new Pilot(), (GraphSpec) r[1], st, 1500, 150, 1, 4);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));
            }
            for (Thread t : pilots) {
                t.join();
            }
        }
        if (runs.contains("control_gpt32")) {
            control("control_gpt32", Blueprints.gpt(32, 2, 4), st, 500, 50);
        }
        if (runs.contains("control_seeds")) {
            // how much practice varies from run to run: gpt32 from five seeds at once
            List<Thread> seeds = new ArrayList<>();
            for (long seed = 2; seed <= 6; seed++) {
                long sd = seed;
                seeds.add(Thread.ofPlatform().start(() -> {
                    try {
                        control("control_seed" + sd, Blueprints.gpt(32, 2, 4), st, 800, 100, sd, 3);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));
            }
            for (Thread t : seeds) {
                t.join();
            }
        }
        if (all || runs.contains("control")) {
            control("control_context", Blueprints.context(), st, 400, 100);
            control("control_gpt32", Blueprints.gpt(32, 2, 4), st, 800, 100);
            control("control_induction", Blueprints.induction(), st, 800, 100);
        }
        if (all || runs.contains("demand")) {
            run("demand_bigram", Blueprints.bigram(), Benchmark.DEMAND, st, 1000, 250);
            run("demand_context", Blueprints.context(), Benchmark.DEMAND, st, 1500, 250);
            run("demand_recall", Blueprints.recall(), Benchmark.DEMAND, st, 2000, 250);
            run("demand_induction", Blueprints.induction(), Benchmark.DEMAND, st, 2000, 250);
            run("demand_gpt32", Blueprints.gpt(32, 2, 4), Benchmark.DEMAND, st, 2000, 250);
        }
        if (all || runs.contains("watch")) {
            run("watch_recall", Blueprints.recall(), Benchmark.WATCH, st, 1000, 500);
            run("watch_induction", Blueprints.induction(), Benchmark.WATCH, st, 2000, 500);
        }
        if (all || runs.contains("watch_gpt32")) {
            run("watch_gpt32", Blueprints.gpt(32, 2, 4), Benchmark.WATCH, st, 3000, 500);
        }
        if (all || runs.contains("survey")) {
            run("survey_context", Blueprints.context(), Benchmark.SURVEY, st, 1500, 250);
            run("survey_recall", Blueprints.recall(), Benchmark.SURVEY, st, 3000, 250);
            run("survey_gpt32", Blueprints.gpt(32, 2, 4), Benchmark.SURVEY, st, 3000, 250);
        }
        if (all || runs.contains("forecasting")) {
            run("forecasting", Blueprints.bigram(), Benchmark.FORECASTING, st, 3000, 250);
        }
        if (all || runs.contains("recall")) {
            run("recall", Blueprints.recall(), Benchmark.RECALL, st, 3000, 250);
        }
        if (all || runs.contains("reversal")) {
            run("reversal", Blueprints.reverse(), Benchmark.REVERSAL, st, 3000, 250);
        }
        if (all || runs.contains("induction")) {
            run("induction", Blueprints.induction(), Benchmark.INDUCTION, st, 4000, 250);
        }
        if (all || runs.contains("sorting")) {
            run("sorting", Blueprints.gpt(32, 2, 4), Benchmark.SORTING, st, 3000, 250);
        }
        if (runs.contains("small_sorting")) {
            run("sorting_rev", Blueprints.reverse(), Benchmark.SORTING, st, 3000, 500);
        }
        if (all || runs.contains("orders")) {
            run("orders", Blueprints.gpt(64, 2, 4), Benchmark.ORDERS, st, 4000, 250);
        }
        if (runs.contains("small_orders")) {
            run("orders32", Blueprints.gpt(32, 2, 4), Benchmark.ORDERS, st, 4000, 500);
        }
        if (all || runs.contains("reading")) {
            run("reading", Blueprints.gpt(64, 2, 4), Benchmark.READING, st, 4000, 250);
        }
        if (runs.contains("weak_reading")) {
            run("reading_ctx", Blueprints.context(), Benchmark.READING, st, 3000, 500);
        }
        if (runs.contains("collected")) {
            for (int n : new int[]{100, 300, 1000}) {
                collected("gpt32", Blueprints.gpt(32, 2, 4), n, 3000, 1000);
                collected("gpt64", Blueprints.gpt(64, 2, 4), n, 4000, 1000);
            }
        }
        if (runs.contains("restore")) {
            restore("restore64", Blueprints.gpt(64, 2, 4), 4000, 1000);
            restore("restore32", Blueprints.gpt(32, 2, 4), 4000, 1000);
            restore("restore_ctx", Blueprints.context(), 3000, 1000);
        }
        if (runs.contains("tokens")) {
            for (int records : new int[]{300, 1000}) {
                for (int size : new int[]{78, 512, 2048}) {
                    tokenized("bigram", Blueprints.bigram(), records, size, 3000, 1000);
                    tokenized("context", Blueprints.context(), records, size, 3000, 1000);
                    tokenized("gpt32", Blueprints.gpt(32, 2, 4), records, size, 3000, 1000);
                    tokenized("gpt64", Blueprints.gpt(64, 2, 4), records, size, 3000, 1000);
                }
            }
        }
        if (runs.contains("generalist")) {
            List<Mixture.Source> sources = new ArrayList<>();
            for (Benchmark a : Benchmark.values()) {
                sources.add(new Mixture.Source(a, a == Benchmark.READING ? reading() : Dataset.of(a)));
            }
            run("generalist", Blueprints.gpt(64, 3, 4), new Mixture(sources, Vocab.WORLD), List.of(Benchmark.values()), Double.NaN, st, 12000, 1000);
        }
        if (runs.contains("wrecks")) {
            for (Wrecks.Wreck w : Wrecks.ALL) {
                try {
                    run("wreck_" + w.id(), w.machine().get(), w.practice(), List.of(w.benchmark()), w.target(), w.settings(),
                            Wrecks.BUDGET, 250);
                } catch (CompileException e) {
                    // the shape wreck is broken before it trains: it does not compile
                    System.out.println("wreck_" + w.id() + " does not compile: " + e.getMessage());
                }
            }
        }
        if (runs.contains("depth_seeds")) {
            // the tall model as found has to miss its goal from any weights, and the plainest repair reach it
            Wrecks.Wreck depth = Wrecks.byId("depth");
            for (long seed = 2; seed <= 6; seed++) {
                run("depth_seed" + seed, depth.machine().get(), depth.practice(), List.of(Benchmark.READING), depth.target(),
                        depth.settings(), Wrecks.BUDGET, Wrecks.BUDGET, seed);
                run("relu_seed" + seed, fixedDepth("relu", false), depth.practice(), List.of(Benchmark.READING), depth.target(),
                        depth.settings(), Wrecks.BUDGET, Wrecks.BUDGET, seed);
            }
        }
        if (runs.contains("fixes")) {
            run("shape_fix", fixedShape(), Dataset.of(Benchmark.FORECASTING), List.of(Benchmark.FORECASTING), Benchmark.FORECASTING.target, st, Wrecks.BUDGET, 250);
            run("diverge_fix", Blueprints.recall(), Dataset.of(Benchmark.RECALL), List.of(Benchmark.RECALL), 0.9, st, Wrecks.BUDGET, 250);
            run("leak_fix", fixedLeak(), Dataset.of(Benchmark.FORECASTING), List.of(Benchmark.FORECASTING), Benchmark.FORECASTING.target, st, Wrecks.BUDGET, 250);
            run("order_fix", fixedOrder(), Dataset.of(Benchmark.REVERSAL), List.of(Benchmark.REVERSAL), 0.9, st, Wrecks.BUDGET, 250);
            Wrecks.Wreck depth = Wrecks.byId("depth");
            run("depth_relu", fixedDepth("relu", false), depth.practice(), List.of(Benchmark.READING), depth.target(), st, Wrecks.BUDGET, 250);
            run("depth_res", fixedDepth("sigmoid", true), depth.practice(), List.of(Benchmark.READING), depth.target(), st, Wrecks.BUDGET, 250);
        }
    }
}
