package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Config;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.tensor.Parallel;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Trains each blueprint on its task and prints the learning curve. Run with RESIDUAL_CALIBRATE=1. */
@EnabledIfEnvironmentVariable(named = "RESIDUAL_CALIBRATE", matches = ".+")
class CalibrationTest {
    static String corpus(String name) throws IOException {
        try (InputStream in = CalibrationTest.class.getResourceAsStream("/corpus/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static void run(String label, GraphSpec spec, Task task, float lr, int batch, int steps, int every) throws Exception {
        run(label, spec, task, lr, batch, 1f, steps, every);
    }

    static void run(String label, GraphSpec spec, Task task, float lr, int batch, float clip, int steps, int every) throws Exception {
        CompiledGraph g = CompiledGraph.compile(spec, new Env(task.vocab.size(), task.context), null, 1);
        Trainer tr = new Trainer(g, task, 1, lr, batch, clip);
        ForkJoinPool pool = new ForkJoinPool(4);
        long t0 = System.nanoTime();
        Parallel.with(pool, () -> {
            double ema = Double.NaN;
            for (int s = 1; s <= steps; s++) {
                float loss = tr.train();
                ema = Double.isNaN(ema) ? loss : 0.95 * ema + 0.05 * loss;
                if (s % every == 0 || s == steps) {
                    Trainer.Eval ev = tr.evaluate(8);
                    double secs = (System.nanoTime() - t0) / 1e9;
                    System.out.printf("%-12s step %5d  train %.3f  val %.3f  acc %.3f  causal %s  pass %s  %.1fs (%.1f ms/step) params %d%n",
                            label, s, ema, ev.loss(), ev.accuracy(), ev.causal(), task.passes(ev.loss(), ev.accuracy()), secs,
                            secs * 1000 / s, g.parameterCount());
                }
            }
        });
        pool.shutdown();
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
        String words = corpus("words.txt");
        if (which.contains("bigram") || which.equals("all")) {
            run("bigram", Blueprints.bigram(), new TextTask("bigram", words, 16, 0, Set.of()), 1e-2f, 32, 1500, 250);
        }
        if (which.contains("context") || which.equals("all")) {
            run("context", Blueprints.context(), new TextTask("context", words, 16, 0, Set.of()), 3e-3f, 32, 3000, 500);
        }
        if (which.contains("recall") || which.equals("all")) {
            run("recall", Blueprints.recall(), PuzzleTask.recall("recall", 8, 0.9), 3e-3f, 32, 3000, 250);
        }
        if (which.contains("reverse") || which.equals("all")) {
            run("reverse", Blueprints.reverse(), PuzzleTask.reverse("reverse", 6, 0.9), 3e-3f, 32, 3000, 250);
        }
        if (which.contains("induction") || which.equals("all")) {
            run("induction", Blueprints.induction(), PuzzleTask.induction("induction", 24, 0.9, Set.of(Op.SHIFT)), 3e-3f, 32, 4000, 250);
            GraphSpec one = Blueprints.reverse();
            run("induction1", one, PuzzleTask.induction("induction", 24, 0.9, Set.of(Op.SHIFT)), 3e-3f, 32, 2000, 500);
        }
        if (which.contains("cases")) {
            for (Curriculum.Lesson l : Curriculum.ALL) {
                if (l.kind() == Curriculum.Kind.CASE && !l.id().equals("case_shape")) {
                    Curriculum.Settings st = l.settings();
                    run(l.id(), l.brokenMachine().get(), l.makeTask(), st.learningRate(), st.batchSize(), st.clip(), 2000, 500);
                }
            }
        }
        if (which.contains("fixes")) {
            Curriculum.Settings st = Curriculum.Settings.DEFAULT;
            run("shape_fix", fixedShape(), Curriculum.byId("counting").makeTask(), st.learningRate(), st.batchSize(), 1500, 250);
            run("leak_fix", fixedLeak(), Curriculum.byId("context").makeTask(), st.learningRate(), st.batchSize(), 2000, 250);
            run("depth_broken", Curriculum.caseDepth(), Curriculum.byId("context").makeTask(), st.learningRate(), st.batchSize(), 3000, 500);
            run("depth_relu", fixedDepth("relu", false), Curriculum.byId("context").makeTask(), st.learningRate(), st.batchSize(), 2000, 250);
            run("depth_res", fixedDepth("sigmoid", true), Curriculum.byId("context").makeTask(), st.learningRate(), st.batchSize(), 2000, 250);
            run("depth_gelu_res", fixedDepth("gelu", true), Curriculum.byId("context").makeTask(), st.learningRate(), st.batchSize(), 2000, 250);
        }
        if (which.contains("gpt") || which.equals("all")) {
            run("gpt", Blueprints.gpt(64, 2, 4), new TextTask("shakespeare", corpus("shakespeare.txt"), 64, 0, Set.of()), 3e-3f, 16, 3000, 250);
        }
    }
}
