package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.tensor.Tokens;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Next-character prediction on stretches of text. A window never spans two stretches, so damaged text and the
 * seams between records are never read as if they were whole.
 */
public final class TextTask extends Task {
    private final List<int[]> runs = new ArrayList<>();
    /** Running count of window starts, to draw a window uniformly over all the text. */
    private final long[] starts;
    private final long characters;

    public TextTask(List<String> texts, double target) {
        super(Vocab.CONTEXT, Metric.LOSS, target);
        long chars = 0;
        for (String text : texts) {
            int[] ids = vocab.encode(text);
            if (ids.length > context) {
                runs.add(ids);
                chars += ids.length;
            }
        }
        starts = new long[runs.size()];
        long total = 0;
        for (int i = 0; i < runs.size(); i++) {
            total += runs.get(i).length - context;
            starts[i] = total;
        }
        characters = chars;
    }

    public boolean isEmpty() {
        return runs.isEmpty();
    }

    /** Characters of text in stretches long enough to read. */
    public long characters() {
        return characters;
    }

    @Override
    public Batch batch(Random rng, int size, boolean test) {
        int t = context;
        int[] in = new int[size * t];
        int[] tg = new int[size * t];
        for (int b = 0; b < size; b++) {
            long pick = (long) (rng.nextDouble() * starts[starts.length - 1]);
            int run = Arrays.binarySearch(starts, pick + 1);
            run = run < 0 ? -run - 1 : run;
            int[] src = runs.get(run);
            int start = (int) (pick - (run == 0 ? 0 : starts[run - 1]));
            System.arraycopy(src, start, in, b * t, t);
            System.arraycopy(src, start + 1, tg, b * t, t);
        }
        return new Batch(new Tokens(in, size, t), tg, null);
    }

    /** A random slice of the text to start writing from. */
    public String prompt(Random rng, int length) {
        int[] src = runs.get(rng.nextInt(runs.size()));
        int start = rng.nextInt(src.length - length);
        return vocab.decode(Arrays.copyOfRange(src, start, start + length));
    }
}
