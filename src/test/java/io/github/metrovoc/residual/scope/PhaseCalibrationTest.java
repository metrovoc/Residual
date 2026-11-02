package io.github.metrovoc.residual.scope;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Blueprints;
import io.github.metrovoc.residual.learn.Task;
import io.github.metrovoc.residual.learn.Trainer;
import io.github.metrovoc.residual.tensor.Parallel;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Prints how a model's copying and its heads' prefix matching go as it trains, to place the Phase change's thresholds. */
@EnabledIfEnvironmentVariable(named = "RESIDUAL_PHASE", matches = ".+")
class PhaseCalibrationTest {
    static void run(String label, GraphSpec spec, Task practice, float lr, int steps, int every, long seed) throws Exception {
        CompiledGraph g = CompiledGraph.compile(spec, Env.WORLD, null, seed);
        Trainer tr = new Trainer(g, practice, seed, lr, 32, 1f);
        ForkJoinPool pool = new ForkJoinPool(2);
        long t0 = System.nanoTime();
        Parallel.with(pool, () -> {
            for (int s = 1; s <= steps; s++) {
                float loss = tr.train();
                if (s % every == 0) {
                    Heads.Reading r = Heads.read(g);
                    Trainer.Eval e = Trainer.evaluate(g, Benchmark.INDUCTION.test(g.env.tokenizer()), 4);
                    System.out.printf("%-16s step %5d loss %.3f induction %.3f copied %.3f prefix %.3f", label, s, loss, e.accuracy(), r.copied(), r.prefix());
                    for (int i = 1; i < Math.min(3, r.heads().size()); i++) {
                        System.out.printf(" %.3f", r.heads().get(i).prefix());
                    }
                    System.out.printf("  %.0fs%n", (System.nanoTime() - t0) / 1e9);
                }
            }
        });
        pool.shutdown();
    }

    @Test
    void sweep() throws Exception {
        String what = System.getenv("RESIDUAL_PHASE");
        Task pattern = Benchmark.INDUCTION.practice();
        switch (what) {
            case "induction" -> {
                for (long seed = 1; seed <= 3; seed++) {
                    run("induction s" + seed, Blueprints.induction(), pattern, 3e-3f, 2500, 100, seed);
                }
            }
            case "gpt" -> {
                for (long seed = 1; seed <= 2; seed++) {
                    run("gpt32 s" + seed, Blueprints.gpt(32, 2, 2), pattern, 3e-3f, 2500, 100, seed);
                }
                run("gpt64 s1", Blueprints.gpt(64, 2, 4), pattern, 3e-3f, 2000, 100, 1);
            }
            case "others" -> {
                run("recall", Blueprints.recall(), pattern, 3e-3f, 1500, 150, 1);
                run("context", Blueprints.context(), pattern, 3e-3f, 1000, 250, 1);
                run("bigram", Blueprints.bigram(), pattern, 3e-3f, 1000, 250, 1);
                run("gpt32 recallset", Blueprints.gpt(32, 2, 2), Benchmark.RECALL.practice(), 3e-3f, 2500, 250, 1);
            }
            default -> throw new IllegalArgumentException(what);
        }
    }
}
