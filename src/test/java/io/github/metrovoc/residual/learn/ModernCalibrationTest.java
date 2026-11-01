package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.Captures;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Config;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.tensor.Ops;
import io.github.metrovoc.residual.tensor.Parallel;
import io.github.metrovoc.residual.tensor.Tape;
import io.github.metrovoc.residual.tensor.Tensor;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * What the modern block does on small models, for the field notes' numbers. Run with {@code RESIDUAL_MODERN} set to the
 * names of the runs wanted: {@code kernel}, {@code steps}, {@code seeds}, {@code high}.
 */
@EnabledIfEnvironmentVariable(named = "RESIDUAL_MODERN", matches = ".+")
class ModernCalibrationTest {
    private static TextTask reading() {
        Map<Integer, BitSet> all = new HashMap<>();
        for (Archive.Record r : Archive.records()) {
            all.put(r.id(), new BitSet());
        }
        return Archive.practice(all, Vocab.WORLD);
    }

    private static Tensor gaussian(Random rng, int... shape) {
        Tensor t = Tensor.zeros(true, shape);
        for (int i = 0; i < t.data.length; i++) {
            t.data[i] = (float) rng.nextGaussian();
        }
        return t;
    }

    /** Attention's forward and backward by the plain kernels and by tiles, on the sizes a Core trains. */
    private static void kernel(int batch, int heads, int t, int d) {
        Random rng = new Random(1);
        Tensor q = gaussian(rng, batch, heads, t, d);
        Tensor k = gaussian(rng, batch, heads, t, d);
        Tensor v = gaussian(rng, batch, heads, t, d);
        float scale = (float) (1 / Math.sqrt(d));
        for (int round = 0; round < 3; round++) {
            long t0 = System.nanoTime();
            int calls = 0;
            while (System.nanoTime() - t0 < 3_000_000_000L) {
                Tape tape = new Tape();
                Tensor out = Ops.matmul(tape, Ops.softmax(tape, Ops.causalMask(tape, Ops.scale(tape, Ops.matmul(tape, q, k, true), scale))), v, false);
                tape.backward(Ops.sum(tape, out));
                calls++;
            }
            double plain = (System.nanoTime() - t0) / 1e6 / calls;
            t0 = System.nanoTime();
            calls = 0;
            while (System.nanoTime() - t0 < 3_000_000_000L) {
                Tape tape = new Tape();
                Tensor out = Ops.flashAttention(tape, q, k, v, scale);
                tape.backward(Ops.sum(tape, out));
                calls++;
            }
            double flash = (System.nanoTime() - t0) / 1e6 / calls;
            long plainFloats = 8L * batch * heads * t * t + 2L * batch * heads * t * d;
            long flashFloats = 2L * batch * heads * t + batch * heads * t * d;
            System.out.printf("kernel %d×%d×%d×%d plain %.3f ms, tiles %.3f ms, ×%.2f; floats held %,d against %,d%n",
                    batch, heads, t, d, plain, flash, plain / flash, plainFloats, flashFloats);
        }
    }

    /** How long a training step takes, on the Reading test's model sizes. */
    private static void steps(String label, GraphSpec spec, boolean fused, int threads, int count) throws Exception {
        for (GraphSpec.Node n : spec.nodes) {
            n.fused = fused;
        }
        TextTask text = reading();
        CompiledGraph g = CompiledGraph.compile(spec, Env.WORLD, null, 1);
        Trainer tr = new Trainer(g, text, 1, 3e-3f, 32, 1f);
        ForkJoinPool pool = threads > 1 ? new ForkJoinPool(threads) : null;
        Parallel.with(pool, () -> {
            for (int i = 0; i < 100; i++) {
                tr.train();
            }
            long t0 = System.nanoTime();
            for (int i = 0; i < count; i++) {
                tr.train();
            }
            System.out.printf("step %-24s %d params, %d threads: %.1f ms a step%n", label, g.parameterCount(), threads,
                    (System.nanoTime() - t0) / 1e6 / count);
        });
        if (pool != null) {
            pool.shutdown();
        }
    }

    private static double score(CompiledGraph g, Task test) {
        Trainer.Eval e = Trainer.evaluate(g, test, 8);
        return test.score(e.loss(), e.accuracy());
    }

    /** One model on one benchmark from several seeds: the score at each mark, and the step each first passes. */
    private static void seeds(String label, java.util.function.Supplier<GraphSpec> spec, Benchmark a, float rate, int steps, int every, int seeds) throws Exception {
        List<Thread> runs = new ArrayList<>();
        double[][] marks = new double[seeds][steps / every + 1];
        int[] passed = new int[seeds];
        Object[] holder = new Object[1];
        for (int s = 0; s < seeds; s++) {
            int seed = s + 1;
            runs.add(Thread.ofPlatform().start(() -> {
                try {
                    Task practice = a == Benchmark.READING ? reading() : a.practice();
                    CompiledGraph g = CompiledGraph.compile(spec.get(), new Env(practice.vocab, Vocab.CONTEXT), null, seed);
                    holder[0] = g.parameterCount();
                    Task test = a.test(practice.vocab);
                    Trainer tr = new Trainer(g, practice, seed, rate, 32, 1f);
                    ForkJoinPool pool = new ForkJoinPool(2);
                    Parallel.with(pool, () -> {
                        for (int i = 1; i <= steps; i++) {
                            tr.train();
                            if (i % every == 0) {
                                double score = score(g, test);
                                marks[seed - 1][i / every] = score;
                                if (passed[seed - 1] == 0 && test.passes(score, score)) {
                                    passed[seed - 1] = i;
                                }
                            }
                        }
                    });
                    pool.shutdown();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }
        for (Thread t : runs) {
            t.join();
        }
        StringBuilder line = new StringBuilder(String.format("seeds %-22s %-11s lr %.0e params %s:", label, a.id, rate, holder[0]));
        for (int m = 1; m < marks[0].length; m++) {
            double sum = 0;
            for (double[] mark : marks) {
                sum += mark[m];
            }
            line.append(String.format(" %d:%.3f", m * every, sum / seeds));
        }
        line.append("  passed at");
        for (int p : passed) {
            line.append(' ').append(p);
        }
        System.out.println(line);
    }

    /** At a high learning rate: does the run live, its largest attention score and output score, its test loss. */
    private static void high(String label, GraphSpec spec, float rate, float clip, int steps, long seed) throws Exception {
        TextTask text = reading();
        CompiledGraph g = CompiledGraph.compile(spec, Env.WORLD, null, seed);
        Trainer tr = new Trainer(g, text, seed, rate, 32, clip);
        Batch probe = text.batch(new Random(9), 4, true);
        ForkJoinPool pool = new ForkJoinPool(2);
        StringBuilder line = new StringBuilder(String.format("high %-20s lr %.0e clip %.1f:", label, rate, clip));
        Parallel.with(pool, () -> {
            double ema = Double.NaN;
            int spikes = 0;
            double peak = 0;
            for (int i = 1; i <= steps; i++) {
                float loss = tr.train();
                if (!Float.isFinite(loss)) {
                    line.append(" NaN at ").append(i);
                    break;
                }
                if (i > 20 && loss > 1.5 * ema + 0.1) {
                    spikes++;
                    peak = Math.max(peak, loss);
                }
                ema = Double.isNaN(ema) ? loss : 0.95 * ema + 0.05 * loss;
                if (i % (steps / 3) == 0) {
                    Captures c = new Captures();
                    Tensor logits = g.forward(null, probe.input(), c);
                    float top = 0;
                    for (float v : logits.data) {
                        top = Math.max(top, Math.abs(v));
                    }
                    line.append(String.format(" [%d train %.3f test %.3f attn %.1f logit %.1f spikes %d peak %.1f]", i, ema,
                            Trainer.evaluate(g, Benchmark.READING.test(text.vocab), 4).loss(), c.topScore, top, spikes, peak));
                }
            }
        });
        pool.shutdown();
        System.out.println(line);
    }

    private static GraphSpec modern(int dim, int blocks, int heads, boolean rotary, boolean qk, boolean positions) {
        GraphSpec g = new GraphSpec();
        String d = Integer.toString(dim);
        int x = g.add("embed", Op.EMBEDDING, Config.of(Op.EMBEDDING, "dim", d), GraphSpec.TOKENS);
        if (positions) {
            x = g.add("x", Op.ADD, x, g.add("pos", Op.POSITION, Config.of(Op.POSITION, "dim", d)));
        }
        for (int i = 0; i < blocks; i++) {
            x = g.add("block" + (i + 1), Op.MODERN_BLOCK, Config.of(Op.MODERN_BLOCK, "heads", Integer.toString(heads),
                    "rotary", Boolean.toString(rotary), "qk_norm", Boolean.toString(qk)), x);
        }
        int n = g.add("norm_out", Op.RMS_NORM, x);
        g.output = g.add("logits", Op.LINEAR, Config.of(Op.LINEAR, "out", "vocab"), n);
        return g;
    }

    @Test
    void calibrate() throws Exception {
        List<String> runs = List.of(System.getenv("RESIDUAL_MODERN").split("[ ,]+"));
        if (runs.contains("kernel")) {
            kernel(32, 4, 64, 8);
            kernel(32, 4, 64, 16);
            kernel(32, 1, 256, 32);
        }
        if (runs.contains("steps")) {
            for (int threads : new int[]{1, 4}) {
                steps("gpt32", Blueprints.gpt(32, 2, 4), true, threads, 300);
                steps("gpt32 unfused", Blueprints.gpt(32, 2, 4), false, threads, 100);
                steps("modern32", Blueprints.modern(32, 2, 4), true, threads, 300);
                steps("modern32 unfused", Blueprints.modern(32, 2, 4), false, threads, 100);
                steps("gpt64", Blueprints.gpt(64, 2, 4), true, threads, 200);
                steps("modern64", Blueprints.modern(64, 2, 4), true, threads, 200);
            }
        }
        if (runs.contains("seeds")) {
            List<Thread> threads = new ArrayList<>();
            for (Benchmark a : List.of(Benchmark.READING, Benchmark.INDUCTION, Benchmark.SORTING, Benchmark.RECALL, Benchmark.REVERSAL, Benchmark.SURVEY)) {
                int steps = a == Benchmark.READING ? 2000 : a == Benchmark.INDUCTION || a == Benchmark.SORTING ? 1600 : 1000;
                for (Object[] m : new Object[][]{{"gpt32", (java.util.function.Supplier<GraphSpec>) () -> Blueprints.gpt(32, 2, 4)},
                        {"modern32", (java.util.function.Supplier<GraphSpec>) () -> Blueprints.modern(32, 2, 4)},
                        {"modern32+pos", (java.util.function.Supplier<GraphSpec>) () -> modern(32, 2, 4, true, true, true)}}) {
                    threads.add(Thread.ofPlatform().start(() -> {
                        try {
                            @SuppressWarnings("unchecked")
                            java.util.function.Supplier<GraphSpec> spec = (java.util.function.Supplier<GraphSpec>) m[1];
                            seeds((String) m[0], spec, a, 3e-3f, steps, steps / 4, 3);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }));
                }
            }
            for (Thread t : threads) {
                t.join();
            }
        }
        if (runs.contains("high")) {
            List<Thread> threads = new ArrayList<>();
            for (float rate : new float[]{3e-3f, 1e-2f, 3e-2f, 1e-1f}) {
                for (Object[] m : new Object[][]{{"gpt32", Blueprints.gpt(32, 2, 4)}, {"modern32", Blueprints.modern(32, 2, 4)},
                        {"modern32 no qk", modern(32, 2, 4, true, false, false)}}) {
                    threads.add(Thread.ofPlatform().start(() -> {
                        try {
                            high((String) m[0], (GraphSpec) m[1], rate, 1f, 1500, 1);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }));
                }
            }
            for (Thread t : threads) {
                t.join();
            }
        }
    }
}
