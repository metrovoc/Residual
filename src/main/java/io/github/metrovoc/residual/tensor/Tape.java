package io.github.metrovoc.residual.tensor;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Records backward closures in execution order. Running them in reverse is a valid
 * topological order because every op is recorded after the ops that produced its inputs.
 */
public final class Tape {
    private final ArrayList<Runnable> backward = new ArrayList<>();

    void record(Runnable step) {
        backward.add(step);
    }

    public int size() {
        return backward.size();
    }

    /** Backpropagates from a scalar loss. Gradients accumulate into each tensor's grad buffer. */
    public void backward(Tensor loss) {
        if (loss.size() != 1) {
            throw new IllegalArgumentException("backward() needs a scalar, got " + loss.shapeString());
        }
        Arrays.fill(loss.grad(), 1f);
        for (int i = backward.size() - 1; i >= 0; i--) {
            backward.get(i).run();
        }
        backward.clear();
    }
}
