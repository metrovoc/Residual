package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.tensor.Tokens;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Next-token prediction on stretches of text. A window never spans two stretches, so damaged text and the seams
 * between records are never read as if they were whole.
 */
public final class TextTask extends Task {
    private final List<int[]> runs = new ArrayList<>();
    /** Every stretch of at least two tokens, short ones too, for rows packed from several of them. */
    private final List<int[]> pieces = new ArrayList<>();
    /** What each piece counts for when one is drawn, so that every token of the text comes up as often. */
    private final double[] pieceWeights;
    /** How its rows are cut, as the Core's recipe says. */
    public volatile Recipe.Packing packing = Recipe.Packing.WINDOWS;
    /** Running count of window starts, to draw a window uniformly over all the text. */
    private final long[] starts;
    private final long characters;
    private long allCharacters;

    public TextTask(List<String> texts, double target, Vocab vocab) {
        super(vocab, Vocab.CONTEXT, Metric.LOSS, target);
        long chars = 0;
        for (String text : texts) {
            int[] ids = vocab.encode(text);
            for (int id : ids) {
                allCharacters += vocab.piece(id).length();
            }
            if (ids.length > 1) {
                pieces.add(ids);
            }
            if (ids.length > context) {
                runs.add(ids);
                for (int id : ids) {
                    chars += vocab.piece(id).length();
                }
            }
        }
        pieceWeights = new double[pieces.size()];
        double running = 0;
        for (int i = 0; i < pieceWeights.length; i++) {
            running += Math.max(1.0, pieces.get(i).length / (context + 1.0));
            pieceWeights[i] = running;
        }
        starts = new long[runs.size()];
        long total = 0;
        for (int i = 0; i < runs.size(); i++) {
            total += runs.get(i).length - context;
            starts[i] = total;
        }
        characters = chars;
    }

    /** Whether there is nothing to read in the way {@link #packing} cuts rows. */
    public boolean isEmpty() {
        return packing == Recipe.Packing.WINDOWS ? runs.isEmpty() : pieces.isEmpty();
    }

    /** The share of the text its rows ever read: windows skip every stretch shorter than a row. */
    public float readShare() {
        return allCharacters == 0 ? Float.NaN : packing == Recipe.Packing.WINDOWS ? (float) characters / allCharacters : 1f;
    }

    /** Characters of text in stretches long enough to read. */
    public long characters() {
        return characters;
    }

    /** Any window of its text can be drawn, so the whole of it. */
    @Override
    public List<String> heldOutTexts() {
        List<String> out = new ArrayList<>(runs.size());
        runs.forEach(run -> out.add(vocab.decode(run)));
        return out;
    }

    @Override
    public Batch batch(Random rng, int size, boolean test) {
        if (packing != Recipe.Packing.WINDOWS && !test) {
            return packed(rng, size, packing == Recipe.Packing.MASKED);
        }
        int t = context;
        int[] in = new int[size * t];
        int[] tg = new int[size * t];
        long chars = 0;
        for (int b = 0; b < size; b++) {
            long pick = (long) (rng.nextDouble() * starts[starts.length - 1]);
            int run = Arrays.binarySearch(starts, pick + 1);
            run = run < 0 ? -run - 1 : run;
            int[] src = runs.get(run);
            int start = (int) (pick - (run == 0 ? 0 : starts[run - 1]));
            System.arraycopy(src, start, in, b * t, t);
            System.arraycopy(src, start + 1, tg, b * t, t);
        }
        for (int id : tg) {
            chars += vocab.piece(id).length();
        }
        return new Batch(new Tokens(in, size, t), tg, null, (double) tg.length / chars);
    }

    /**
     * Rows filled from as many stretches as it takes, so nothing goes unread. A stretch that fits goes in whole, one that
     * doesn't is cut to what is left. A seam is never scored; with {@code masked} each token also knows where its own
     * stretch began, and nothing in the model may look further back.
     */
    private Batch packed(Random rng, int size, boolean masked) {
        int t = context;
        int[] in = new int[size * t];
        int[] tg = new int[size * t];
        float[] w = new float[size * t];
        int[] starts = masked ? new int[size * t] : null;
        long chars = 0;
        long scored = 0;
        int[] row = new int[t + 1];
        for (int b = 0; b < size; b++) {
            int at = 0;
            while (at < t) {
                int pick = Arrays.binarySearch(pieceWeights, rng.nextDouble() * pieceWeights[pieceWeights.length - 1]);
                int[] src = pieces.get(pick < 0 ? -pick - 1 : pick);
                int n = Math.min(src.length, t + 1 - at);
                int from = src.length > n ? rng.nextInt(src.length - n + 1) : 0;
                System.arraycopy(src, from, row, at, n);
                for (int i = at; i < at + n && i < t; i++) {
                    in[b * t + i] = row[i];
                    if (starts != null) {
                        starts[b * t + i] = at;
                    }
                }
                for (int i = at; i < at + n - 1; i++) {
                    tg[b * t + i] = row[i + 1];
                    w[b * t + i] = 1f;
                    chars += vocab.piece(row[i + 1]).length();
                    scored++;
                }
                at += n;
            }
        }
        return new Batch(new Tokens(in, size, t, starts), tg, w, (double) scored / Math.max(1, chars));
    }

    /** A random slice of the text, at least {@code chars} characters long, to start writing from. */
    public String prompt(Random rng, int chars) {
        int[] src = runs.get(rng.nextInt(runs.size()));
        StringBuilder out = new StringBuilder();
        for (int i = rng.nextInt(src.length - context); out.length() < chars; i++) {
            out.append(vocab.piece(src[i]));
        }
        return out.toString();
    }
}
