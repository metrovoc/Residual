package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.tensor.Tokens;
import java.util.Random;

/**
 * Practice on a field a Forecaster watched: its lines, one per crop, read as text, and scored on every look that
 * follows another look at the same crop. The last tenth is held out for testing.
 */
public final class FieldTask extends Task {
    private final int[] ids;
    private final boolean[] scored;
    private final int split;

    public FieldTask(String log, Vocab vocab) {
        super(vocab, Vocab.CONTEXT, Metric.LOSS, Benchmark.FORECASTING.target);
        // digits and line breaks are never merged, so the log reads one token per character with any tokenizer
        ids = vocab.spell(log);
        scored = new boolean[ids.length];
        for (int i = 1; i < log.length(); i++) {
            scored[i] = log.charAt(i) != '\n' && log.charAt(i - 1) != '\n';
        }
        split = Math.max(context + 1, ids.length * 9 / 10);
    }

    public boolean isEmpty() {
        return ids.length <= context + 1;
    }

    @Override
    public Batch batch(Random rng, int size, boolean test) {
        int t = context;
        boolean held = test && ids.length - split > t + 1;
        int from = held ? split : 0, to = held ? ids.length : split;
        int[] in = new int[size * t];
        int[] tg = new int[size * t];
        float[] w = new float[size * t];
        for (int b = 0; b < size; b++) {
            int start = from + rng.nextInt(Math.max(1, to - from - t));
            for (int i = 0; i < t; i++) {
                int at = Math.min(start + i, ids.length - 2);
                in[b * t + i] = ids[at];
                tg[b * t + i] = ids[at + 1];
                w[b * t + i] = scored[at + 1] ? 1f : 0f;
            }
        }
        return new Batch(new Tokens(in, size, t), tg, w);
    }
}
