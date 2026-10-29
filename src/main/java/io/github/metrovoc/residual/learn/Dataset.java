package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.tensor.Tokens;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * Synthetic practice for one benchmark, scored only on its answer positions. Agents ask their models in these same
 * formats, so practice, test and work are one thing.
 */
public final class Dataset extends Task {
    /** A plot's state is how far its crop has grown, {@code 0} to {@link #RIPE}, or {@link #BARE} for empty farmland. */
    public static final char RIPE = '7';
    public static final char BARE = '.';
    public static final char HARVEST = 'h';
    public static final char WAIT = 'w';
    public static final char PLANT = 'p';
    /** Kinds of goods in a storage question, and the chests they can be in. */
    public static final String GOODS = "abcdefghijklmnop";
    public static final String CHESTS = "0123456789";
    public static final int PAIRS = 8;
    /** Landmarks are named by digits; an errand retraces this many at a time. */
    public static final String LANDMARKS = "0123456789";
    public static final int LEG = 6;
    public static final String LETTERS = "abcdefghijklmnopqrstuvwxyz";

    private static final Map<Benchmark, Dataset> DATASETS = new EnumMap<>(Benchmark.class);

    public final Benchmark benchmark;

    private Dataset(Benchmark benchmark) {
        super(Vocab.WORLD, benchmark.length, benchmark.metric, benchmark.target);
        this.benchmark = benchmark;
    }

    public static synchronized Dataset of(Benchmark benchmark) {
        if (benchmark == Benchmark.READING) {
            throw new IllegalArgumentException("reading is practised on archive text");
        }
        return DATASETS.computeIfAbsent(benchmark, Dataset::new);
    }

    /** What a perfect farmer does with a plot in state {@code plot}. */
    public static char decide(char plot) {
        return plot == RIPE ? HARVEST : plot == BARE ? PLANT : WAIT;
    }

    @Override
    public Batch batch(Random rng, int count, boolean test) {
        int t = context;
        int[] in = new int[count * t];
        int[] tg = new int[count * t];
        float[] w = new float[count * t];
        for (int b = 0; b < count; b++) {
            int[] seq = new int[t + 1];
            boolean[] answer = new boolean[t + 1];
            switch (benchmark) {
                case REACTING -> field(rng, seq, answer);
                case RECALL -> storage(rng, seq, answer);
                case REVERSAL -> errand(rng, seq, answer);
                case INDUCTION -> pattern(rng, seq, answer);
                case ORDERS -> orders(rng, seq, answer, test);
                default -> throw new IllegalStateException();
            }
            int o = b * t;
            for (int i = 0; i < t; i++) {
                in[o + i] = seq[i];
                tg[o + i] = seq[i + 1];
                w[o + i] = answer[i + 1] ? 1f : 0f;
            }
        }
        return new Batch(new Tokens(in, count, t), tg, w);
    }

    /** {@code 3w7h.p5w…}: each plot's state, then what to do with it. */
    private void field(Random rng, int[] seq, boolean[] answer) {
        for (int i = 0; i < seq.length; i += 2) {
            float r = rng.nextFloat();
            char plot = r < 0.3f ? RIPE : r < 0.8f ? (char) ('0' + rng.nextInt(7)) : BARE;
            seq[i] = vocab.id(plot);
            if (i + 1 < seq.length) {
                seq[i + 1] = vocab.id(decide(plot));
                answer[i + 1] = true;
            }
        }
    }

    /** {@code c4a7f1…|f} then {@code 1}: which chest holds the goods asked about. */
    private void storage(Random rng, int[] seq, boolean[] answer) {
        int[] keys = distinct(rng, GOODS.length(), PAIRS);
        int[] vals = new int[PAIRS];
        for (int i = 0; i < PAIRS; i++) {
            vals[i] = rng.nextInt(CHESTS.length());
            seq[2 * i] = vocab.id(GOODS.charAt(keys[i]));
            seq[2 * i + 1] = vocab.id(CHESTS.charAt(vals[i]));
        }
        int q = rng.nextInt(PAIRS);
        seq[2 * PAIRS] = vocab.id('|');
        seq[2 * PAIRS + 1] = vocab.id(GOODS.charAt(keys[q]));
        seq[2 * PAIRS + 2] = vocab.id(CHESTS.charAt(vals[q]));
        answer[2 * PAIRS + 2] = true;
    }

    /** {@code 381925|529183}: the landmarks of one leg, then the same landmarks in reverse. */
    private void errand(Random rng, int[] seq, boolean[] answer) {
        int[] d = distinct(rng, LANDMARKS.length(), LEG);
        for (int i = 0; i < LEG; i++) {
            seq[i] = vocab.id(LANDMARKS.charAt(d[i]));
            seq[LEG + 1 + i] = vocab.id(LANDMARKS.charAt(d[LEG - 1 - i]));
            answer[LEG + 1 + i] = true;
        }
        seq[LEG] = vocab.id('|');
    }

    /**
     * Letters where a run comes round again: once it does, continue it. Half the time a run of distinct letters
     * reappears once at a random offset ({@code qkfbmxz…kfbm…}); half the time a short motif repeats to the end, the
     * way a patterned wall goes ({@code wjtabcabcab…}). Either way, only looking back at what followed the same letter
     * works.
     */
    private void pattern(Random rng, int[] seq, boolean[] answer) {
        int t = context;
        if (rng.nextBoolean()) {
            int len = 5 + rng.nextInt(4);
            int first = rng.nextInt(t - 2 * len);
            int second = first + len + rng.nextInt(t - 2 * len - first + 1);
            int[] letters = distinct(rng, LETTERS.length(), t - len);
            int next = 0;
            for (int i = 0; i < t; i++) {
                seq[i] = i >= second && i < second + len ? seq[first + i - second] : vocab.id(LETTERS.charAt(letters[next++]));
                answer[i] = i > second && i < second + len;
            }
            seq[t] = seq[t - 1];
        } else {
            int len = 2 + rng.nextInt(7);
            int start = rng.nextInt(t - 2 * len);
            int[] letters = distinct(rng, LETTERS.length(), start + len);
            for (int i = 0; i <= t; i++) {
                seq[i] = vocab.id(LETTERS.charAt(letters[i < start ? i : start + (i - start) % len]));
                answer[i] = i > start + len;
            }
        }
    }

    /** {@code > bring me some bread⏎= fetch bread⏎}, repeated: the meaning of each order is the answer. */
    private void orders(Random rng, int[] seq, boolean[] answer, boolean test) {
        StringBuilder text = new StringBuilder();
        boolean[] meant = new boolean[seq.length + 80];
        while (text.length() < seq.length) {
            Orders.Example ex = Orders.example(rng, test);
            text.append(Orders.prompt(ex.said()));
            int start = text.length();
            text.append(ex.meant()).append('\n');
            for (int i = start; i < Math.min(text.length(), meant.length); i++) {
                meant[i] = true;
            }
        }
        for (int i = 0; i < seq.length; i++) {
            seq[i] = vocab.id(text.charAt(i));
            answer[i] = meant[i];
        }
    }

    public static int[] distinct(Random rng, int range, int n) {
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
        return Arrays.copyOf(pool, n);
    }
}
