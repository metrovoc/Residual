package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.Captures;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Config;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.tensor.Numerics;
import io.github.metrovoc.residual.tensor.Parallel;
import io.github.metrovoc.residual.tensor.Tensor;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * What each part of a recipe does on small models, for the field notes' numbers. Run with {@code RESIDUAL_RECIPE} set to
 * the names of the runs wanted, played one at a time: {@code speed}, {@code stability}, {@code steep}, {@code steeper}, {@code spikes}, {@code zloss}, {@code seeded}, {@code damaged}, {@code warmup},
 * {@code schedule}, {@code rounding}, {@code precision}, {@code second}, {@code packing}, {@code muon}, {@code orders}.
 */
@EnabledIfEnvironmentVariable(named = "RESIDUAL_RECIPE", matches = ".+")
class RecipeCalibrationTest {
    private static TextTask whole;

    /** All the archive's text, shared by every run: it takes tens of megabytes. */
    private static synchronized TextTask reading() {
        if (whole == null) {
            Map<Integer, BitSet> all = new HashMap<>();
            for (Archive.Record r : Archive.records()) {
                all.put(r.id(), new BitSet());
            }
            whole = Archive.practice(all, Vocab.WORLD);
        }
        return whole;
    }

    /** {@code count} records as shards leave them, a share of their blocks lost, as a drive holds them. */
    private static Map<Integer, BitSet> found(int count, double lost, long seed) {
        Random rng = new Random(seed);
        List<Archive.Record> records = new ArrayList<>(Archive.records());
        java.util.Collections.shuffle(records, rng);
        Map<Integer, BitSet> out = new HashMap<>();
        for (Archive.Record r : records) {
            if (!Archive.heldOut(r.id()) && out.size() < count) {
                BitSet gone = new BitSet();
                for (int b = 0; b < r.blocks(); b++) {
                    gone.set(b, rng.nextDouble() < lost);
                }
                out.put(r.id(), gone);
            }
        }
        return out;
    }

    private static Recipe recipe(Recipe.Schedule schedule, int warmup, int length) {
        return new Recipe(schedule, warmup, length, Numerics.Format.FP32, Recipe.Moves.ADAMW, Recipe.Packing.WINDOWS);
    }

    private static Recipe plain(Numerics.Format f) {
        return new Recipe(Recipe.Schedule.CONSTANT, 20, 0, f, Recipe.Moves.ADAMW, Recipe.Packing.WINDOWS);
    }

    private static Recipe moved(Recipe.Moves moves) {
        return new Recipe(Recipe.Schedule.CONSTANT, 20, 0, Numerics.Format.FP32, moves, Recipe.Packing.WINDOWS);
    }

    /**
     * Trains from {@code seeds} seeds at once and prints, averaged: the test score along the way, the steps that spiked,
     * the largest loss of a step, the largest attention and output scores at the end, and how many runs ended in NaN or
     * ran away.
     */
    private static void run(String label, Supplier<GraphSpec> spec, Supplier<Task> practice, Benchmark tested, float rate, float clip,
                            Recipe recipe, int steps, int seeds) {
        {
            double[] finals = new double[seeds];
            double[] best = new double[seeds];
            int[] spikes = new int[seeds];
            double[] peaks = new double[seeds];
            double[] leaps = new double[seeds];
            double[] scores = new double[seeds];
            double[] logits = new double[seeds];
            double[] ms = new double[seeds];
            boolean[] died = new boolean[seeds];
            int[] passed = new int[seeds];
            Object[] params = new Object[1];
            for (int s = 0; s < seeds; s++) {
                int seed = s + 1;
                {
                    try {
                        Task task = practice.get();
                        CompiledGraph g = CompiledGraph.compile(spec.get(), new Env(task.vocab, Vocab.CONTEXT), null, seed);
                        params[0] = g.parameterCount();
                        Task test = tested.test(task.vocab);
                        Trainer tr = new Trainer(g, task, seed, rate, 32, clip);
                        tr.recipe = recipe;
                        Batch probe = test.batch(new Random(9), 4, true);
                        ForkJoinPool pool = new ForkJoinPool(2);
                        Parallel.with(pool, () -> {
                            double ema = Double.NaN;
                            double start = Math.log(task.vocab.size());
                            long t0 = System.nanoTime();
                            int every = Math.max(1, steps / 8);
                            best[seed - 1] = test.metric == Task.Metric.LOSS ? Double.MAX_VALUE : 0;
                            for (int i = 1; i <= steps; i++) {
                                float loss = tr.train();
                                if (!Float.isFinite(loss) || i >= 50 && ema > 3 * start) {
                                    died[seed - 1] = true;
                                    finals[seed - 1] = Double.NaN;
                                    return;
                                }
                                if (ema > 0) {
                                    leaps[seed - 1] = Math.max(leaps[seed - 1], loss / ema);
                                }
                                if (Trainer.spiked(loss, ema)) {
                                    spikes[seed - 1]++;
                                    peaks[seed - 1] = Math.max(peaks[seed - 1], loss);
                                }
                                ema = Double.isNaN(ema) ? loss : 0.95 * ema + 0.05 * loss;
                                if (i % every == 0 || i == steps) {
                                    long pause = System.nanoTime();
                                    Trainer.Eval e = Trainer.evaluate(g, test, 8);
                                    double score = test.score(e.loss(), e.accuracy());
                                    finals[seed - 1] = score;
                                    best[seed - 1] = test.metric == Task.Metric.LOSS ? Math.min(best[seed - 1], score) : Math.max(best[seed - 1], score);
                                    if (passed[seed - 1] == 0 && test.passes(score, score)) {
                                        passed[seed - 1] = i;
                                    }
                                    t0 += System.nanoTime() - pause;
                                }
                            }
                            ms[seed - 1] = (System.nanoTime() - t0) / 1e6 / steps;
                            Captures c = new Captures();
                            Tensor out = g.forward(null, probe.input(), c);
                            scores[seed - 1] = c.topScore;
                            for (float v : out.data) {
                                logits[seed - 1] = Math.max(logits[seed - 1], Math.abs(v));
                            }
                        });
                        pool.shutdown();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            StringBuilder line = new StringBuilder(String.format("run %-34s %-9s lr %.0e clip %.1f params %s:", label, tested.id, rate, clip, params[0]));
            line.append(" final");
            for (double v : finals) {
                line.append(String.format(" %.3f", v));
            }
            line.append(" best");
            for (double v : best) {
                line.append(String.format(" %.3f", v));
            }
            line.append(" passed");
            for (int v : passed) {
                line.append(' ').append(v);
            }
            line.append(" spikes");
            for (int v : spikes) {
                line.append(' ').append(v);
            }
            line.append(String.format(" peak %.1f leap %.2f attn %.1f logit %.1f %.0f ms/step", max(peaks), max(leaps), mean(scores), mean(logits), mean(ms)));
            int dead = 0;
            for (boolean d : died) {
                dead += d ? 1 : 0;
            }
            if (dead > 0) {
                line.append(" DIED ").append(dead);
            }
            System.out.println(line);
        }
    }

    private static double max(double[] v) {
        double m = 0;
        for (double x : v) {
            m = Math.max(m, x);
        }
        return m;
    }

    private static double mean(double[] v) {
        double s = 0;
        for (double x : v) {
            s += x;
        }
        return s / v.length;
    }

    private static GraphSpec gpt32() {
        return Blueprints.gpt(32, 2, 4);
    }

    private static GraphSpec modern32() {
        return Blueprints.modern(32, 2, 4);
    }

    /** A GPT whose last layer also learns the token after next. */
    private static GraphSpec twoHeaded(int dim) {
        GraphSpec g = new GraphSpec();
        String d = Integer.toString(dim);
        int e = g.add("embed", Op.EMBEDDING, Config.of(Op.EMBEDDING, "dim", d), GraphSpec.TOKENS);
        int pos = g.add("pos", Op.POSITION, Config.of(Op.POSITION, "dim", d));
        int x = g.add("x", Op.ADD, e, pos);
        for (int i = 0; i < 2; i++) {
            x = g.add("block" + (i + 1), Op.TRANSFORMER, Config.of(Op.TRANSFORMER, "heads", "4"), x);
        }
        int n = g.add("norm_out", Op.NORM, x);
        g.output = g.add("heads", Op.TWO_TOKEN_HEAD, n);
        return g;
    }

    /** A model with a Z-Loss Regulator before the Core. */
    private static GraphSpec withZ(GraphSpec g) {
        g.output = g.add("z", Op.Z_LOSS, g.output);
        return g;
    }

    /** One step's time at each thread count, on the sizes a Core trains: what a Modern Block costs beside a Transformer Block. */
    private static void speed(String label, GraphSpec spec, int threads) {
        try {
            TextTask text = reading();
            CompiledGraph g = CompiledGraph.compile(spec, Env.WORLD, null, 1);
            Trainer tr = new Trainer(g, text, 1, 3e-3f, 32, 1f);
            ForkJoinPool pool = threads > 1 ? new ForkJoinPool(threads) : null;
            Parallel.with(pool, () -> {
                for (int i = 0; i < 60; i++) {
                    tr.train();
                }
                long t0 = System.nanoTime();
                for (int i = 0; i < 100; i++) {
                    tr.train();
                }
                System.out.printf("speed %-12s %d params, %d threads: %.1f ms a step%n", label, g.parameterCount(), threads, (System.nanoTime() - t0) / 1e6 / 100);
            });
            if (pool != null) {
                pool.shutdown();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void calibrate() {
        List<String> runs = List.of(System.getenv("RESIDUAL_RECIPE").split("[ ,]+"));
        Supplier<Task> reading = RecipeCalibrationTest::reading;
        Supplier<Task> sorting = Benchmark.SORTING::practice;
        if (runs.contains("speed")) {
            for (int threads : new int[]{1, 2, 4}) {
                speed("gpt32", gpt32(), threads);
                speed("modern32", modern32(), threads);
                speed("gpt64", Blueprints.gpt(64, 2, 4), threads);
                speed("modern64", Blueprints.modern(64, 2, 4), threads);
            }
        }
        if (runs.contains("stability")) {
            // a high rate with nothing to clip: what QK norm and z-loss do about scores running away
            for (float rate : new float[]{1e-2f, 3e-2f}) {
                run("gpt32", RecipeCalibrationTest::gpt32, reading, Benchmark.READING, rate, 0f, Recipe.PLAIN, 600, 1);
                run("gpt32 z-loss", () -> withZ(gpt32()), reading, Benchmark.READING, rate, 0f, Recipe.PLAIN, 600, 1);
                run("modern32", RecipeCalibrationTest::modern32, reading, Benchmark.READING, rate, 0f, Recipe.PLAIN, 600, 1);
                run("modern32 no qk", RecipeCalibrationTest::noQk, reading, Benchmark.READING, rate, 0f, Recipe.PLAIN, 600, 1);
                run("modern32 z-loss", () -> withZ(modern32()), reading, Benchmark.READING, rate, 0f, Recipe.PLAIN, 600, 1);
            }
        }
        if (runs.contains("steep")) {
            // far past the comfortable rate, where a run begins to come apart
            run("gpt32", RecipeCalibrationTest::gpt32, reading, Benchmark.READING, 1e-1f, 0f, Recipe.PLAIN, 600, 1);
            run("gpt32 z-loss", () -> withZ(gpt32()), reading, Benchmark.READING, 1e-1f, 0f, Recipe.PLAIN, 600, 1);
            run("modern32", RecipeCalibrationTest::modern32, reading, Benchmark.READING, 1e-1f, 0f, Recipe.PLAIN, 600, 1);
            run("modern32 no qk", RecipeCalibrationTest::noQk, reading, Benchmark.READING, 1e-1f, 0f, Recipe.PLAIN, 600, 1);
        }
        if (runs.contains("steeper")) {
            // where runs come apart, and what holds them together
            run("modern32", RecipeCalibrationTest::modern32, reading, Benchmark.READING, 3e-1f, 0f, Recipe.PLAIN, 300, 3);
            run("modern32 no qk", RecipeCalibrationTest::noQk, reading, Benchmark.READING, 3e-1f, 0f, Recipe.PLAIN, 300, 3);
            for (int warmup : new int[]{0, 100}) {
                for (float clip : new float[]{0f, 1f}) {
                    run("gpt32 warmup " + warmup, RecipeCalibrationTest::gpt32, reading, Benchmark.READING, 3e-1f, clip,
                            recipe(Recipe.Schedule.CONSTANT, warmup, 0), 300, 3);
                }
            }
        }
        if (runs.contains("spikes")) {
            // a job a model passes in a hundred steps at a good rate: what a steep one does to it
            for (float rate : new float[]{3e-2f, 1e-1f}) {
                run("gpt32", RecipeCalibrationTest::gpt32, Benchmark.REVERSAL::practice, Benchmark.REVERSAL, rate, 1f, Recipe.PLAIN, 600, 3);
                run("modern32", RecipeCalibrationTest::modern32, Benchmark.REVERSAL::practice, Benchmark.REVERSAL, rate, 1f, Recipe.PLAIN, 600, 3);
                run("modern32 no qk", RecipeCalibrationTest::noQk, Benchmark.REVERSAL::practice, Benchmark.REVERSAL, rate, 1f, Recipe.PLAIN, 600, 3);
            }
        }
        if (runs.contains("zloss")) {
            // what the penalty does to the output scores, at a rate where they can run
            for (float rate : new float[]{3e-2f, 1e-1f}) {
                run("gpt32", RecipeCalibrationTest::gpt32, reading, Benchmark.READING, rate, 1f, Recipe.PLAIN, 300, 3);
                run("gpt32 z-loss", () -> withZ(gpt32()), reading, Benchmark.READING, rate, 1f, Recipe.PLAIN, 300, 3);
                run("modern32", RecipeCalibrationTest::modern32, Benchmark.REVERSAL::practice, Benchmark.REVERSAL, rate, 1f, Recipe.PLAIN, 600, 3);
                run("modern32 z-loss", () -> withZ(modern32()), Benchmark.REVERSAL::practice, Benchmark.REVERSAL, rate, 1f, Recipe.PLAIN, 600, 3);
            }
        }
        if (runs.contains("seeded")) {
            // near the noise of a start: the comparisons the notes can only make from several
            for (Recipe.Moves moves : Recipe.Moves.values()) {
                run("gpt32 " + moves.id, RecipeCalibrationTest::gpt32, sorting, Benchmark.SORTING, 1e-2f, 1f, moved(moves), 600, 3);
                run("gpt32 " + moves.id, RecipeCalibrationTest::gpt32, reading, Benchmark.READING, 1e-2f, 1f, moved(moves), 1000, 3);
            }
        }
        if (runs.contains("damaged")) {
            // text as broken as shards leave it at first, where a row is longer than most of what survives
            for (double lost : new double[]{0.5, 0.7}) {
                for (int count : new int[]{100, 300}) {
                    TextTask probe = Archive.practice(found(count, lost, 1), Vocab.WORLD);
                    System.out.printf("packing %d records, %.0f%% of blocks lost: windows read %.3f, %,d characters%n", count, 100 * lost, probe.readShare(), probe.characters());
                }
            }
            for (Recipe.Packing packing : Recipe.Packing.values()) {
                run("100 records, 70% lost, " + packing.id, RecipeCalibrationTest::gpt32, () -> {
                    TextTask text = Archive.practice(found(100, 0.7, 1), Vocab.WORLD);
                    text.packing = packing;
                    return text;
                }, Benchmark.READING, 3e-3f, 1f, new Recipe(Recipe.Schedule.CONSTANT, 20, 0, Numerics.Format.FP32, Recipe.Moves.ADAMW, packing), 1000, 1);
            }
        }
        if (runs.contains("warmup")) {
            // past the comfortable rate: what warmup and clipping each do about spikes
            for (float rate : new float[]{1e-2f, 3e-2f, 1e-1f}) {
                for (int warmup : new int[]{0, 100}) {
                    for (float clip : new float[]{0f, 1f}) {
                        run("gpt32 warmup " + warmup, RecipeCalibrationTest::gpt32, reading, Benchmark.READING, rate, clip,
                                recipe(Recipe.Schedule.CONSTANT, warmup, 0), 500, 1);
                    }
                }
            }
        }
        if (runs.contains("schedule")) {
            for (Recipe.Schedule schedule : Recipe.Schedule.values()) {
                run("gpt32 " + schedule.id, RecipeCalibrationTest::gpt32, reading, Benchmark.READING, 1e-2f, 1f, recipe(schedule, 100, 1500), 1500, 1);
            }
        }
        if (runs.contains("rounding")) {
            rounding();
        }
        if (runs.contains("precision")) {
            for (Numerics.Format f : Numerics.Format.values()) {
                run("gpt32 " + f.id, RecipeCalibrationTest::gpt32, sorting, Benchmark.SORTING, 3e-3f, 1f, plain(f), 600, 1);
                run("gpt32 " + f.id, RecipeCalibrationTest::gpt32, reading, Benchmark.READING, 3e-3f, 1f, plain(f), 1000, 1);
            }
        }
        if (runs.contains("second")) {
            secondHead();
        }
        if (runs.contains("packing")) {
            TextTask probe = Archive.practice(found(300, 0.3, 1), Vocab.WORLD);
            System.out.printf("packing 300 records, 30%% of blocks lost: windows read %.3f, %,d characters%n", probe.readShare(), probe.characters());
            probe.packing = Recipe.Packing.MASKED;
            Batch b = probe.batch(new Random(5), 256, false);
            double scored = 0;
            for (float w : b.weights()) {
                scored += w;
            }
            System.out.printf("packed rows score %.3f of their places%n", scored / b.weights().length);
            for (Recipe.Packing packing : Recipe.Packing.values()) {
                run("300 records " + packing.id, RecipeCalibrationTest::gpt32, () -> {
                    TextTask text = Archive.practice(found(300, 0.3, 1), Vocab.WORLD);
                    text.packing = packing;
                    return text;
                }, Benchmark.READING, 3e-3f, 1f, new Recipe(Recipe.Schedule.CONSTANT, 20, 0, Numerics.Format.FP32, Recipe.Moves.ADAMW, packing), 1000, 1);
            }
        }
        if (runs.contains("muon")) {
            run("gpt32 muon", RecipeCalibrationTest::gpt32, reading, Benchmark.READING, 3e-3f, 1f, moved(Recipe.Moves.MUON), 1000, 1);
            run("gpt32 muon", RecipeCalibrationTest::gpt32, sorting, Benchmark.SORTING, 3e-3f, 1f, moved(Recipe.Moves.MUON), 600, 1);
            run("gpt32 muon", RecipeCalibrationTest::gpt32, reading, Benchmark.READING, 1e-2f, 1f, moved(Recipe.Moves.MUON), 1000, 1);
            run("gpt32 muon", RecipeCalibrationTest::gpt32, sorting, Benchmark.SORTING, 1e-2f, 1f, moved(Recipe.Moves.MUON), 600, 1);
            run("gpt32 adamw", RecipeCalibrationTest::gpt32, reading, Benchmark.READING, 1e-2f, 1f, moved(Recipe.Moves.ADAMW), 1000, 1);
            run("gpt32 adamw", RecipeCalibrationTest::gpt32, sorting, Benchmark.SORTING, 1e-2f, 1f, moved(Recipe.Moves.ADAMW), 600, 1);
        }
        if (runs.contains("orders")) {
            // the model too big to carry in fp32, in the format that fits: does it still pass?
            run("gpt64 fp8", () -> Blueprints.gpt(64, 2, 4), Benchmark.ORDERS::practice, Benchmark.ORDERS, 3e-3f, 1f, plain(Numerics.Format.FP8), 3000, 1);
        }
    }

    private static GraphSpec noQk() {
        GraphSpec g = new GraphSpec();
        int x = g.add("embed", Op.EMBEDDING, Config.of(Op.EMBEDDING, "dim", "32"), GraphSpec.TOKENS);
        for (int i = 0; i < 2; i++) {
            x = g.add("block" + (i + 1), Op.MODERN_BLOCK, Config.of(Op.MODERN_BLOCK, "heads", "4", "qk_norm", "false"), x);
        }
        g.output = g.add("logits", Op.LINEAR, Config.of(Op.LINEAR, "out", "vocab"), g.add("norm_out", Op.RMS_NORM, x));
        return g;
    }

    /** How far {@code round} moves {@code tensors}, as a share of their size, and the share of their numbers it takes to zero. */
    private static String moved(java.util.Collection<float[]> tensors, java.util.function.UnaryOperator<float[]> round) {
        double error = 0, zeros = 0, count = 0;
        for (float[] x : tensors) {
            float[] y = round.apply(x);
            double off = 0, size = 0;
            for (int i = 0; i < x.length; i++) {
                off += (double) (x[i] - y[i]) * (x[i] - y[i]);
                size += (double) x[i] * x[i];
                zeros += x[i] != 0 && y[i] == 0 ? 1 : 0;
            }
            error += Math.sqrt(off / Math.max(size, 1e-30));
            count += x.length;
        }
        return String.format("off by %.2f%%, %.1f%% to zero", 100 * error / tensors.size(), 100 * zeros / count);
    }

    /** What each format does to a trained model's own numbers. */
    private static void rounding() {
        try {
            TextTask text = reading();
            CompiledGraph g = CompiledGraph.compile(gpt32(), Env.WORLD, null, 1);
            Trainer tr = new Trainer(g, text, 1, 3e-3f, 32, 1f);
            ForkJoinPool pool = new ForkJoinPool(2);
            Parallel.with(pool, () -> {
                for (int i = 0; i < 500; i++) {
                    tr.train();
                }
                List<float[]> weights = new ArrayList<>(), gradients = new ArrayList<>(), activations = new ArrayList<>();
                for (Tensor p : g.parameters()) {
                    if (p.rank() == 2) {
                        weights.add(p.data);
                        gradients.add(p.grad());
                    }
                }
                Captures c = new Captures();
                g.forward(null, text.batch(new Random(9), 32, true).input(), c);
                for (Tensor out : c.outputs.values()) {
                    activations.add(out.data);
                }
                for (Numerics.Format f : Numerics.Format.values()) {
                    System.out.printf("rounding %-8s weights %s; activations %s; gradients %s%n", f.id, moved(weights, x -> Numerics.forward(x, f)),
                            moved(activations, x -> Numerics.forward(x, f)), moved(gradients, x -> Numerics.backward(x, f)));
                }
            });
            pool.shutdown();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** The second head beside the same model without one, and how often its first choice is right. */
    private static void secondHead() {
        try {
            TextTask text = reading();
            CompiledGraph g = CompiledGraph.compile(twoHeaded(32), Env.WORLD, null, 1);
            Trainer tr = new Trainer(g, text, 1, 3e-3f, 32, 1f);
            ForkJoinPool pool = new ForkJoinPool(2);
            Parallel.with(pool, () -> {
                for (int i = 1; i <= 1000; i++) {
                    tr.train();
                }
                Task test = Benchmark.READING.test(text.vocab);
                Trainer.Eval e = Trainer.evaluate(g, test, 8);
                System.out.printf("second head after 1000 steps: test loss %.3f, first head right %.3f, second head right %.3f%n", e.loss(), e.accuracy(),
                        Trainer.secondRight(g, test));
            });
            pool.shutdown();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
