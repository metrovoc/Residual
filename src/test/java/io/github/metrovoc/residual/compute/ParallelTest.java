package io.github.metrovoc.residual.compute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Blueprints;
import io.github.metrovoc.residual.learn.Reactor;
import io.github.metrovoc.residual.learn.Task;
import io.github.metrovoc.residual.learn.Trainer;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.tensor.Parallel;
import io.github.metrovoc.residual.tensor.Tensor;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.LongAdder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Data, tensor and pipeline parallelism across threads: they take the same steps one device takes, and {@code measure}
 * prints what each gains, what it wastes and what it sends. Run that with {@code RESIDUAL_MEASURE} set.
 */
class ParallelTest {
    private static final Task TASK = Benchmark.SORTING.practice();

    private static CompiledGraph model(GraphSpec spec) throws Exception {
        return model(spec, TASK);
    }

    private static CompiledGraph model(GraphSpec spec, Task task) throws Exception {
        return CompiledGraph.compile(spec, new Env(task.vocab, Vocab.CONTEXT), null, 7);
    }

    /** {@code steps} steps of a fresh model split as {@code mode}, on {@code devices} devices. */
    private static Run run(GraphSpec spec, Parallelism mode, int devices, int steps, int batch) throws Exception {
        return run(spec, TASK, mode, devices, steps, batch);
    }

    private static Run run(GraphSpec spec, Task task, Parallelism mode, int devices, int steps, int batch) throws Exception {
        CompiledGraph model = model(spec, task);
        Trainer trainer = new Trainer(model, task, 11, 1e-3f, batch, 1f);
        LongAdder flops = new LongAdder();
        Run run = new Run(model, flops);
        mode.training(model, devices, flops, () -> {
            for (int s = 0; s < steps; s++) {
                long t0 = System.nanoTime();
                trainer.train();
                if (s >= steps / 3) {
                    // past the start, while the code warms up
                    run.nanos += System.nanoTime() - t0;
                    run.counted++;
                    Timing t = model.split == null ? null : model.split.last();
                    if (t != null) {
                        run.bubble += t.bubble();
                        run.collective += t.collectiveNanos() / (double) t.wallNanos();
                        run.linkBytes = t.linkBytes();
                        run.devices = t.devices();
                    }
                }
            }
            run.stages = model.split instanceof PipelineParallel p ? p.stages() : 0;
            run.microbatches = model.split instanceof PipelineParallel p ? p.microbatches() : 0;
        });
        return run;
    }

    private static final class Run {
        final CompiledGraph model;
        final LongAdder flops;
        long nanos;
        int counted, devices, stages, microbatches;
        double bubble, collective;
        long linkBytes;

        Run(CompiledGraph model, LongAdder flops) {
            this.model = model;
            this.flops = flops;
        }

        double msPerStep() {
            return nanos / 1e6 / counted;
        }
    }

    private static void sameWeights(CompiledGraph a, CompiledGraph b, double tolerance, String what) {
        List<Tensor> x = a.parameters(), y = b.parameters();
        assertEquals(x.size(), y.size());
        double worst = 0;
        for (int i = 0; i < x.size(); i++) {
            for (int k = 0; k < x.get(i).size(); k++) {
                worst = Math.max(worst, Math.abs(x.get(i).data[k] - y.get(i).data[k]));
            }
        }
        assertTrue(worst < tolerance, what + " strayed by " + worst);
    }

    @Test
    void everySplitTakesTheStepsOneDeviceTakes() throws Exception {
        GraphSpec spec = Blueprints.gpt(32, 2, 4);
        CompiledGraph one = run(spec, Parallelism.ONE, 1, 6, 16).model;
        for (Parallelism mode : List.of(Parallelism.DATA, Parallelism.TENSOR, Parallelism.PIPELINE)) {
            for (int devices : new int[] {2, 3, 4}) {
                sameWeights(one, run(spec, mode, devices, 6, 16).model, 2e-4, mode + " on " + devices);
            }
        }
    }

    @Test
    void whatIsPractisedByPlayingSplitsToo() throws Exception {
        GraphSpec spec = Blueprints.gpt(32, 1, 2);
        CompiledGraph one = run(spec, new Reactor(), Parallelism.ONE, 1, 3, 8).model;
        for (Parallelism mode : List.of(Parallelism.DATA, Parallelism.TENSOR, Parallelism.PIPELINE)) {
            sameWeights(one, run(spec, new Reactor(), mode, 2, 3, 8).model, 2e-4, mode + " playing");
        }
    }

    @Test
    void replicasEndTheStepAlike() throws Exception {
        CompiledGraph model = model(Blueprints.gpt(32, 1, 2));
        Trainer trainer = new Trainer(model, TASK, 11, 1e-3f, 8, 1f);
        DataParallel split = new DataParallel(model, 3, new LongAdder());
        model.split = split;
        trainer.train();
        Timing t = split.last();
        assertEquals(3, t.devices());
        // the second device worked on its own, and gradients crossed the tree
        assertTrue(t.busyNanos()[1] > 0 && t.linkBytes() > 0);
        split.close();
    }

    @Test
    void stagesAreCutWhereTheCostsLevel() {
        assertEquals(List.of(0, 1, 2, 4), java.util.Arrays.stream(PipelineParallel.stages(new long[] {10, 10, 5, 5}, 3)).boxed().toList());
        assertEquals(List.of(0, 2, 4), java.util.Arrays.stream(PipelineParallel.stages(new long[] {1, 9, 5, 5}, 2)).boxed().toList());
        assertEquals(List.of(0, 3), java.util.Arrays.stream(PipelineParallel.stages(new long[] {1, 1, 1}, 1)).boxed().toList());
    }

    @Test
    void pipelineBubblesAreThere() throws Exception {
        Run p = run(Blueprints.gpt(32, 2, 4), Parallelism.PIPELINE, 3, 6, 16);
        assertTrue(p.stages >= 2, "stages " + p.stages);
        assertTrue(p.bubble / p.counted > 0.02 && p.bubble / p.counted < 0.95, "bubble " + p.bubble / p.counted);
    }

    /** What each way of splitting gains on this machine: what systems.md records. */
    @Test
    @EnabledIfEnvironmentVariable(named = "RESIDUAL_MEASURE", matches = ".+")
    void measure() throws Exception {
        // RESIDUAL_MEASURE=pipeline: the largest model's pipeline alone, in a few minutes
        boolean pipeline = "pipeline".equals(System.getenv("RESIDUAL_MEASURE"));
        for (Object[] m : new Object[][] {{"gpt32", Blueprints.gpt(32, 2, 4)}, {"gpt64", Blueprints.gpt(64, 2, 4)}, {"gpt128x4", Blueprints.gpt(128, 4, 4)}}) {
            if (pipeline && !"gpt128x4".equals(m[0])) {
                continue;
            }
            GraphSpec spec = (GraphSpec) m[1];
            int steps = "gpt128x4".equals(m[0]) ? 30 : 60;
            Run base = run(spec, Parallelism.ONE, 1, steps, 32);
            System.out.printf(Locale.ROOT, "%s, %,d parameters: one thread %.1f ms a step, %.1f GFLOP/s%n", m[0], base.model.parameterCount(),
                    base.msPerStep(), base.flops.sum() / (double) steps / base.msPerStep() / 1e6);
            for (Parallelism mode : Parallelism.values()) {
                for (int devices : new int[] {2, 4, 8}) {
                    if (mode == Parallelism.ONE && devices == 1 || pipeline && (mode != Parallelism.PIPELINE || devices != 8)) {
                        continue;
                    }
                    Run r = run(spec, mode, devices, steps, 32);
                    System.out.printf(Locale.ROOT, "  %-8s %d: %7.1f ms  x%.2f  bubble %.0f%%  collectives %.0f%%  %.2f MB through links%s%n", mode, devices,
                            r.msPerStep(), base.msPerStep() / r.msPerStep(), r.counted == 0 ? 0 : 100 * r.bubble / r.counted, 100 * r.collective / r.counted, r.linkBytes / 1e6,
                            r.stages == 0 ? "" : String.format(Locale.ROOT, "  (%d stages, %d microbatches: level %.0f%%)", r.stages, r.microbatches,
                                    100 * PipelineParallel.levelBubble(r.stages, r.microbatches)));
                }
            }
        }
    }
}
