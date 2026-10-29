package io.github.metrovoc.residual.learn;

import java.util.Locale;
import java.util.Random;

/**
 * A source of examples, plus the bar a model has to clear on it. Losses are per character, so models that read with
 * different tokenizers compare fairly.
 */
public abstract class Task {
    public enum Metric { LOSS, ACCURACY }

    /** What its examples are written in. */
    public final Vocab vocab;
    /** Sequence length of its examples. */
    public final int context;
    public final Metric metric;
    /** Loss at most this, or answer accuracy at least this. */
    public final double target;

    protected Task(Vocab vocab, int context, Metric metric, double target) {
        this.vocab = vocab;
        this.context = context;
        this.metric = metric;
        this.target = target;
    }

    /** A batch of {@code size} sequences of length {@link #context}; {@code test} draws from held-out examples. */
    public abstract Batch batch(Random rng, int size, boolean test);

    public boolean passes(double loss, double accuracy) {
        return metric == Metric.LOSS ? loss <= target : accuracy >= target;
    }

    /** The score that is compared with the target. */
    public double score(double loss, double accuracy) {
        return metric == Metric.LOSS ? loss : accuracy;
    }

    public String goal() {
        return metric == Metric.LOSS
                ? String.format(Locale.ROOT, "loss ≤ %.2f", target)
                : String.format(Locale.ROOT, "%d%% right", Math.round(target * 100));
    }
}
