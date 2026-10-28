package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.Op;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/** A dataset plus the bar a model has to clear on it. */
public abstract class Task {
    public enum Metric { LOSS, ACCURACY }

    public final String id;
    public final Vocab vocab;
    /** Sequence length used for training and evaluation. */
    public final int context;
    public final Metric metric;
    /** Validation loss at most this, or accuracy at least this. */
    public final double target;
    /** Operators the solution may not use (a puzzle constraint), usually empty. */
    public final Set<Op> forbidden;

    protected Task(String id, Vocab vocab, int context, Metric metric, double target, Set<Op> forbidden) {
        this.id = id;
        this.vocab = vocab;
        this.context = context;
        this.metric = metric;
        this.target = target;
        this.forbidden = forbidden;
    }

    /** A batch of {@code size} sequences of length {@link #context}. */
    public abstract Batch batch(Random rng, int size, boolean validation);

    public boolean passes(double loss, double accuracy) {
        return metric == Metric.LOSS ? loss <= target : accuracy >= target;
    }

    /** Fraction of the way from a fresh model to the target, for progress bars. */
    public double progress(double loss, double accuracy) {
        if (metric == Metric.ACCURACY) {
            return Math.max(0, Math.min(1, accuracy / target));
        }
        double start = Math.log(vocab.size());
        return Math.max(0, Math.min(1, (start - loss) / (start - target)));
    }

    public String goal() {
        return metric == Metric.LOSS
                ? String.format(Locale.ROOT, "validation loss ≤ %.2f", target)
                : String.format(Locale.ROOT, "answer accuracy ≥ %d%%", Math.round(target * 100));
    }
}
