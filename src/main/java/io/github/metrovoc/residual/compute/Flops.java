package io.github.metrovoc.residual.compute;

import java.util.concurrent.atomic.LongAdder;

/**
 * Counts a run's floating point operations as they happen: every matrix multiply by its multiplies and adds, and a
 * flop for each number any other operation makes. A thread counts into whatever {@link #with} bound on it; with
 * nothing bound nothing is counted.
 */
public final class Flops {
    private static final ThreadLocal<LongAdder> BOUND = new ThreadLocal<>();

    private Flops() {
    }

    /** Runs {@code body} with what this thread does counted into {@code counter}. */
    public static void with(LongAdder counter, Runnable body) {
        LongAdder previous = BOUND.get();
        BOUND.set(counter);
        try {
            body.run();
        } finally {
            BOUND.set(previous);
        }
    }

    public static void add(long flops) {
        LongAdder counter = BOUND.get();
        if (counter != null) {
            counter.add(flops);
        }
    }
}
