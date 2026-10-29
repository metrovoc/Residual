package io.github.metrovoc.residual.learn;

import java.util.Locale;

/**
 * What a model can be tested on. Each benchmark has one held-out test, and agents ask their models in exactly the
 * test's format, so a test score is how often the agent will decide right.
 */
public enum Benchmark {
    /** Farming: a plot's state, then harvest, wait or plant. */
    REACTING(16, Task.Metric.ACCURACY, 0.98),
    /** Storage: which chest holds this kind of thing. */
    RECALL(18, Task.Metric.ACCURACY, 0.9),
    /** Errands: the landmarks passed on the way out, named again in reverse. */
    REVERSAL(12, Task.Metric.ACCURACY, 0.9),
    /** Building: continue the pattern seen so far. */
    INDUCTION(24, Task.Metric.ACCURACY, 0.9),
    /** Orders: what was meant by what was said. */
    ORDERS(Vocab.CONTEXT, Task.Metric.ACCURACY, 0.9),
    /** Reading: the next letter of archive text it has never seen. */
    READING(Vocab.CONTEXT, Task.Metric.LOSS, 1.7);

    public final String id = name().toLowerCase(Locale.ROOT);
    /** Length of the test's sequences. */
    public final int length;
    public final Task.Metric metric;
    public final double target;

    Benchmark(int length, Task.Metric metric, double target) {
        this.length = length;
        this.metric = metric;
        this.target = target;
    }

    /** The held-out test. */
    public Task test() {
        return this == READING ? Archive.test() : Dataset.of(this);
    }

    public static Benchmark byId(String id) {
        for (Benchmark a : values()) {
            if (a.id.equals(id)) {
                return a;
            }
        }
        return null;
    }
}
