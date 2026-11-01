package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.CompiledGraph;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.jetbrains.annotations.Nullable;

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

    /**
     * Its own loss and accuracy for {@code model}, for a task scored by playing rather than by held-out examples; null
     * to be scored on test batches.
     */
    public double @Nullable [] measure(CompiledGraph model) {
        return null;
    }

    /** What the learning rate is multiplied by for the next step: a task that changes as a run goes may ask for less. */
    public float rateScale() {
        return 1f;
    }

    /**
     * What its test reads, as text: the same examples {@link Trainer#evaluate} draws, however many batches it asks for. A test
     * played rather than read has none.
     */
    public List<String> heldOutTexts() {
        Random rng = new Random(Trainer.TEST_SEED);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Trainer.MOST_TEST_BATCHES; i++) {
            out.addAll(decode(batch(rng, 32, true), vocab));
        }
        return out;
    }

    /** A batch's sequences as text, each to its last target. */
    public static List<String> decode(Batch b, Vocab vocab) {
        List<String> out = new ArrayList<>(b.input().batch);
        int t = b.input().time;
        for (int s = 0; s < b.input().batch; s++) {
            StringBuilder text = new StringBuilder(t + 1);
            for (int i = 0; i < t; i++) {
                text.append(vocab.piece(b.input().at(s, i)));
            }
            out.add(text.append(vocab.piece(b.targets()[s * t + t - 1])).toString());
        }
        return out;
    }

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
