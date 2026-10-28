package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.Random;
import java.util.Set;

/** Synthetic sequence puzzles scored only on their answer positions. */
public final class PuzzleTask extends Task {
    public enum Kind { RECALL, REVERSE, INDUCTION }

    private static final String DIGITS = "0123456789";
    private static final String LETTERS = "abcdefghijklmnop";

    public final Kind kind;
    private final int size;

    private PuzzleTask(String id, Kind kind, Vocab vocab, int context, int size, double accuracy, Set<Op> forbidden) {
        super(id, vocab, context, Metric.ACCURACY, accuracy, forbidden);
        this.kind = kind;
        this.size = size;
    }

    /** {@code c4a7f1… |f} → predict {@code 1}: find the key, read the value written after it. */
    public static PuzzleTask recall(String id, int pairs, double accuracy) {
        return new PuzzleTask(id, Kind.RECALL, new Vocab(DIGITS + LETTERS + "|"), 2 * pairs + 2, pairs, accuracy, Set.of());
    }

    /** {@code 381925|529183}: write the digits back in reverse order. */
    public static PuzzleTask reverse(String id, int digits, double accuracy) {
        return new PuzzleTask(id, Kind.REVERSE, new Vocab(DIGITS + "|"), 2 * digits, digits, accuracy, Set.of());
    }

    /**
     * {@code qkfbmxz…kfbm…}: a run of distinct letters reappears at a random offset; once it starts repeating,
     * continue it. The offset varies, so only content-based lookup works.
     */
    public static PuzzleTask induction(String id, int context, double accuracy, Set<Op> forbidden) {
        return new PuzzleTask(id, Kind.INDUCTION, new Vocab(ALPHABET), context, 0, accuracy, forbidden);
    }

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz";

    @Override
    public Batch batch(Random rng, int count, boolean validation) {
        int t = context;
        int[] in = new int[count * t];
        int[] tg = new int[count * t];
        float[] w = new float[count * t];
        for (int b = 0; b < count; b++) {
            int o = b * t;
            switch (kind) {
                case RECALL -> {
                    int[] keys = distinct(rng, LETTERS.length(), size);
                    int[] vals = new int[size];
                    for (int i = 0; i < size; i++) {
                        vals[i] = rng.nextInt(DIGITS.length());
                        in[o + 2 * i] = vocab.id(LETTERS.charAt(keys[i]));
                        in[o + 2 * i + 1] = vocab.id(DIGITS.charAt(vals[i]));
                    }
                    int q = rng.nextInt(size);
                    in[o + t - 2] = vocab.id('|');
                    in[o + t - 1] = vocab.id(LETTERS.charAt(keys[q]));
                    tg[o + t - 1] = vocab.id(DIGITS.charAt(vals[q]));
                    w[o + t - 1] = 1f;
                }
                case REVERSE -> {
                    int[] d = new int[size];
                    for (int i = 0; i < size; i++) {
                        d[i] = rng.nextInt(DIGITS.length());
                    }
                    // sequence: d0..d(n-1) | r0..r(n-1), input drops the final digit
                    int[] seq = new int[2 * size + 1];
                    for (int i = 0; i < size; i++) {
                        seq[i] = vocab.id(DIGITS.charAt(d[i]));
                        seq[size + 1 + i] = vocab.id(DIGITS.charAt(d[size - 1 - i]));
                    }
                    seq[size] = vocab.id('|');
                    for (int i = 0; i < t; i++) {
                        in[o + i] = seq[i];
                        tg[o + i] = seq[i + 1];
                        w[o + i] = i >= size ? 1f : 0f;
                    }
                }
                case INDUCTION -> {
                    // a run of length len at position first reappears at position second; every other letter is unique
                    int len = 5 + rng.nextInt(4);
                    int first = rng.nextInt(t - 2 * len);
                    int second = first + len + rng.nextInt(t - 2 * len - first + 1);
                    int[] letters = distinct(rng, ALPHABET.length(), t - len);
                    int[] seq = new int[t + 1];
                    int next = 0;
                    for (int i = 0; i < t; i++) {
                        seq[i] = i >= second && i < second + len ? seq[first + i - second] : letters[next++];
                    }
                    seq[t] = seq[t - 1];
                    for (int i = 0; i < t; i++) {
                        in[o + i] = seq[i];
                        tg[o + i] = seq[i + 1];
                        w[o + i] = i >= second && i < second + len - 1 ? 1f : 0f;
                    }
                }
            }
        }
        return new Batch(new Tokens(in, count, t), tg, w);
    }

    private static int[] distinct(Random rng, int range, int n) {
        int[] pool = new int[range];
        for (int i = 0; i < range; i++) {
            pool[i] = i;
        }
        for (int i = 0; i < n; i++) {
            int j = i + rng.nextInt(range - i);
            int tmp = pool[i];
            pool[i] = pool[j];
            pool[j] = tmp;
        }
        return java.util.Arrays.copyOf(pool, n);
    }
}
