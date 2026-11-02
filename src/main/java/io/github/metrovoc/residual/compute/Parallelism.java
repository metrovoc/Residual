package io.github.metrovoc.residual.compute;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.tensor.Parallel;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.LongAdder;

/** The ways a model's training can be shared out among devices. */
public enum Parallelism {
    /** One device, however many threads it has: each operation's rows go to whichever is free. */
    ONE,
    DATA,
    TENSOR,
    PIPELINE;

    /** Runs {@code body}, a training loop on this thread, with {@code model} split this way on {@code devices}, counting into {@code flops}. */
    public void training(CompiledGraph model, int devices, LongAdder flops, Runnable body) {
        ForkJoinPool pool = (this == ONE || this == TENSOR) && devices > 1 ? new ForkJoinPool(devices) : null;
        Parallel.Slices slices = this == TENSOR && devices > 1 ? new Parallel.Slices(devices) : null;
        Split split = devices < 2 ? null : switch (this) {
            case ONE -> null;
            case DATA -> new DataParallel(model, devices, flops);
            case TENSOR -> new TensorParallel(model, slices);
            case PIPELINE -> new PipelineParallel(model, devices, flops);
        };
        model.resetTrainingPass();
        model.split = split;
        try {
            Flops.with(flops, () -> Parallel.with(pool, slices, body));
        } finally {
            model.split = null;
            if (split != null) {
                split.close();
            }
            if (pool != null) {
                pool.shutdown();
            }
        }
    }
}
