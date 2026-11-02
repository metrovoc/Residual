package io.github.metrovoc.residual.scope;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Config;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Blueprints;
import io.github.metrovoc.residual.learn.Modular;
import io.github.metrovoc.residual.learn.Trainer;
import io.github.metrovoc.residual.tensor.Parallel;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RESIDUAL_GROK", matches = ".+")
class GrokCalibrationTest {
    static GraphSpec mlp(int dim, int hidden) {
        GraphSpec g = new GraphSpec();
        int e = g.add("embed", Op.EMBEDDING, Config.of(Op.EMBEDDING, "dim", "" + dim), GraphSpec.TOKENS);
        int s1 = g.add("prev1", Op.SHIFT, Config.of(Op.SHIFT, "steps", "1"), e);
        int s2 = g.add("prev2", Op.SHIFT, Config.of(Op.SHIFT, "steps", "2"), e);
        int c = g.add("pair", Op.CONCAT, s1, s2);
        int h = g.add("hidden", Op.LINEAR, Config.of(Op.LINEAR, "out", "" + hidden), c);
        int a = g.add("act", Op.ACTIVATION, Config.of(Op.ACTIVATION, "fn", "relu"), h);
        g.output = g.add("logits", Op.LINEAR, Config.of(Op.LINEAR, "out", "vocab"), a);
        return g;
    }

    static void run(String label, GraphSpec spec, float lr, float wd, int batch, int steps, int every, long seed) throws Exception {
        CompiledGraph g = CompiledGraph.compile(spec, Env.WORLD, null, seed);
        Trainer tr = new Trainer(g, Benchmark.MODULAR.practice(), seed, lr, batch, 1f);
        tr.weightDecay = wd;
        ForkJoinPool pool = new ForkJoinPool(2);
        long t0 = System.nanoTime();
        Parallel.with(pool, () -> {
            for (int s = 1; s <= steps; s++) {
                float loss = tr.train();
                if (s % every == 0) {
                    double practice = Modular.right(g, false), test = Modular.right(g, true);
                    System.out.printf("%-22s lr %.4f wd %.2f b %d  step %6d loss %.3f practice %.3f test %.3f  %.0fs params %d%n", label, lr, wd, batch, s, loss, practice, test,
                            (System.nanoTime() - t0) / 1e9, g.parameterCount());
                    if (test > 0.97) {
                        break;
                    }
                }
            }
        });
        pool.shutdown();
    }

    @Test
    void sweep() throws Exception {
        String what = System.getenv("RESIDUAL_GROK");
        switch (what) {
            case "mlp" -> {
                run("mlp32-128", mlp(32, 128), 3e-3f, 1f, 64, 30000, 1000, 1);
                run("mlp32-128", mlp(32, 128), 1e-3f, 1f, 64, 30000, 1000, 1);
                run("mlp32-128", mlp(32, 128), 3e-3f, 0.3f, 64, 30000, 1000, 1);
            }
            case "gpt" -> {
                run("gpt32-2", Blueprints.gpt(32, 2, 2), 3e-3f, 1f, 64, 30000, 1000, 1);
                run("gpt32-1", Blueprints.gpt(32, 1, 2), 3e-3f, 1f, 64, 30000, 1000, 1);
            }
            case "decay" -> {
                run("mlp32-128", mlp(32, 128), 3e-3f, 0.1f, 64, 40000, 2000, 1);
                run("mlp32-128", mlp(32, 128), 1e-2f, 0.1f, 64, 40000, 2000, 1);
                run("mlp32-128", mlp(32, 128), 3e-3f, 0.01f, 64, 40000, 4000, 1);
                run("mlp32-128", mlp(32, 128), 1e-2f, 0.01f, 64, 40000, 4000, 1);
            }
            case "gpt_decay" -> {
                run("gpt32-2", Blueprints.gpt(32, 2, 2), 3e-3f, 0.3f, 64, 12000, 500, 1);
                run("gpt32-2", Blueprints.gpt(32, 2, 2), 3e-3f, 0.1f, 64, 12000, 500, 1);
                run("gpt32-2", Blueprints.gpt(32, 2, 2), 3e-3f, 0.01f, 64, 12000, 1000, 1);
                run("gpt32-2", Blueprints.gpt(32, 2, 2), 1e-3f, 0.3f, 64, 12000, 500, 1);
            }
            case "seeds" -> {
                for (long seed = 2; seed <= 6; seed++) {
                    run("mlp32-128 s" + seed, mlp(32, 128), 3e-3f, 0.3f, 64, 30000, 500, seed);
                }
            }
            default -> throw new IllegalArgumentException(what);
        }
    }
}
