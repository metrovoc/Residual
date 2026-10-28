package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.Arrays;
import java.util.Random;
import java.util.Set;

/** Next-character prediction on a text corpus: the first 90% trains, the rest validates. */
public final class TextTask extends Task {
    private final int[] train;
    private final int[] val;

    public TextTask(String id, String text, int context, double targetLoss, Set<Op> forbidden) {
        super(id, Vocab.of(text), context, Metric.LOSS, targetLoss, forbidden);
        int[] all = vocab.encode(text);
        int split = (int) (all.length * 0.9);
        train = Arrays.copyOfRange(all, 0, split);
        val = Arrays.copyOfRange(all, split, all.length);
    }

    @Override
    public Batch batch(Random rng, int size, boolean validation) {
        int[] src = validation ? val : train;
        int t = context;
        int[] in = new int[size * t];
        int[] tg = new int[size * t];
        for (int b = 0; b < size; b++) {
            int start = rng.nextInt(src.length - t - 1);
            System.arraycopy(src, start, in, b * t, t);
            System.arraycopy(src, start + 1, tg, b * t, t);
        }
        return new Batch(new Tokens(in, size, t), tg, null);
    }

    /** A slice of validation text to start generation from. */
    public String prompt(Random rng, int length) {
        int start = rng.nextInt(val.length - length);
        return vocab.decode(Arrays.copyOfRange(val, start, start + length));
    }
}
