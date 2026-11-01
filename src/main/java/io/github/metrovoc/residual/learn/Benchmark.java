package io.github.metrovoc.residual.learn;

import java.util.Locale;

/**
 * What a model can be tested on. Each benchmark has one held-out test, and agents ask their models in exactly the
 * test's format, so a test score is how often the agent will decide right.
 */
public enum Benchmark {
    /**
     * Forecasting: what a block a Forecaster watches will show at the next look, a crop's age or a machine's progress.
     * A machine's next look is nearly certain; a crop's is not. Knowing only the look before leaves about 0.194 (0.216
     * on crops, 0.153 on machines), and knowing each field and machine exactly about 0.185.
     */
    FORECASTING(Vocab.CONTEXT, Task.Metric.LOSS, 0.21),
    /** Storage: which chest holds this kind of thing. */
    RECALL(18, Task.Metric.ACCURACY, 0.9),
    /** Errands: the landmarks passed on the way out, named again in reverse. */
    REVERSAL(12, Task.Metric.ACCURACY, 0.9),
    /** Building: continue the pattern seen so far. */
    INDUCTION(24, Task.Metric.ACCURACY, 0.9),
    /**
     * Sorting: what kind a thing is, by its name. The test names only things practice never does, some of them made
     * up.
     */
    SORTING(Vocab.CONTEXT, Task.Metric.ACCURACY, 0.9),
    /**
     * Orders: what was meant by what was said. A two-layer model of width 64 passes in about 2,250 steps; one of width
     * 32, small enough to carry, stalls near 74%.
     */
    ORDERS(Vocab.CONTEXT, Task.Metric.ACCURACY, 0.9),
    /**
     * Reading: the next letter of archive text it has never seen. Getting below 1.40 takes nearly the whole archive,
     * and a tokenizer or a large model.
     */
    READING(Vocab.CONTEXT, Task.Metric.LOSS, 1.4),
    /**
     * Control: holding a Smelter's melt in its band, practised by playing melts (reinforcement learning). A model that
     * sees only the last few characters holds about 75% of looks; a two-block transformer, 97%.
     */
    CONTROL(Vocab.CONTEXT, Task.Metric.ACCURACY, 0.9);

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

    /** The held-out test, for a model that reads with {@code vocab}. */
    public Task test(Vocab vocab) {
        return this == READING ? Archive.test(vocab) : practice();
    }

    /** What a dataset of this benchmark practises on: a Control task plays its own melts with the model it practises. */
    public Task practice() {
        return this == CONTROL ? new Control() : Dataset.of(this);
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
