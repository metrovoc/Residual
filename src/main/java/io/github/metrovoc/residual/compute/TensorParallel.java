package io.github.metrovoc.residual.compute;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.tensor.Parallel;
import io.github.metrovoc.residual.tensor.Tape;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;

/**
 * Tensor parallelism: every matrix multiply is split among the devices by the columns of its result, each device working
 * from its own slice of the weights, and they all meet at the end of each one to gather what they made. A device holds
 * only its share of the model, so a model can be larger than any one device. Nothing else about a step shares out,
 * and it pays a meeting per multiply, so it goes furthest with wide layers.
 *
 * <p>A job's thread binds the slices with {@link Parallel#with(java.util.concurrent.ForkJoinPool, Parallel.Slices, Runnable)};
 * this only marks where its steps begin and end.
 */
public final class TensorParallel implements Split {
    private final CompiledGraph main;
    private final Parallel.Slices slices;
    private volatile Timing last;

    public TensorParallel(CompiledGraph main, Parallel.Slices slices) {
        this.main = main;
        this.slices = slices;
    }

    @Override
    public Tensor forward(Tape tape, Tokens tokens) {
        slices.begin();
        // recorded first, so it runs last
        tape.record(() -> last = slices.end());
        return main.plain(tape, tokens, null);
    }

    @Override
    public Timing last() {
        return last;
    }

    @Override
    public int devices() {
        return slices.devices;
    }

    @Override
    public void close() {
    }
}
