package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.List;
import java.util.Random;

/**
 * Survey: where in a row of ground the first of an ore lies. A row is {@link #ROW} blocks along a line, a capital each:
 * ground ({@code S} stone, {@code D} deepslate, {@code T} other rock, {@code A} air, {@code W} water, {@code L} lava,
 * {@code B} bedrock) and ores ({@link #ORES}: coal, iron, copper, gold, redstone, lapis, diamond, emerald). Then
 * {@code >}, the ore asked for, {@code :}, and the answer: the first such block's place, {@code a} for the row's first to
 * {@code p} for its last, or {@code -} if there is none. A Surveyor asks its model this of every row of ground it reads,
 * and checks each answer against the block. Finding the first of a letter needs a model that looks back over the row
 * and knows where each block stands in it.
 */
public final class Survey {
    public static final int ROW = 16;
    /** Ores, by letter: coal, iron, copper, gold, redstone, lapis, diamond, emerald. */
    public static final String ORES = "CIUORZXE";
    public static final String PLACES = "abcdefghijklmnop";
    public static final char NONE = '-';
    /** How often each ore turns up in practice rows, in the order of {@link #ORES}, much as the ground has them. */
    private static final int[] PLENTY = {25, 22, 18, 8, 12, 7, 5, 3};

    private Survey() {
    }

    /** What a Surveyor asks about {@code row}: where the first {@code ore} lies. */
    public static String prompt(CharSequence row, char ore) {
        return row + ">" + ore + ":";
    }

    /** The right answer: the first {@code ore}'s place in {@code row}, or {@link #NONE}. */
    public static char answer(CharSequence row, char ore) {
        for (int i = 0; i < row.length(); i++) {
            if (row.charAt(i) == ore) {
                return PLACES.charAt(i);
            }
        }
        return NONE;
    }

    /**
     * A practice row: stone or deepslate, with rock of other kinds in patches, now and then a cave of air (water or
     * lava in some), bedrock at a row's end at the bottom of the world, and up to three short runs of ore.
     */
    public static String row(Random rng) {
        char[] row = new char[ROW];
        char base = rng.nextInt(5) < 3 ? 'S' : 'D';
        java.util.Arrays.fill(row, base);
        if (rng.nextInt(7) == 0) {
            // where stone gives way to deepslate
            int from = 1 + rng.nextInt(ROW - 1);
            for (int i = from; i < ROW; i++) {
                row[i] = base == 'S' ? 'D' : 'S';
            }
        }
        for (int n = rng.nextInt(3); n > 0; n--) {
            run(rng, row, 'T', 2 + rng.nextInt(4));
        }
        if (rng.nextInt(4) == 0) {
            int r = rng.nextInt(10);
            run(rng, row, r == 0 ? 'W' : r == 1 ? 'L' : 'A', 2 + rng.nextInt(7));
        }
        if (rng.nextInt(20) == 0) {
            int n = 1 + rng.nextInt(3);
            boolean left = rng.nextBoolean();
            for (int i = 0; i < n; i++) {
                row[left ? i : ROW - 1 - i] = 'B';
            }
        }
        int runs = new int[] {0, 0, 1, 1, 1, 1, 2, 2, 2, 3}[rng.nextInt(10)];
        for (int n = 0; n < runs; n++) {
            run(rng, row, ore(rng), 1 + rng.nextInt(3));
        }
        return new String(row);
    }

    private static void run(Random rng, char[] row, char c, int length) {
        int start = rng.nextInt(ROW - Math.min(length, ROW) + 1);
        for (int i = start; i < Math.min(ROW, start + length); i++) {
            row[i] = c;
        }
    }

    private static char ore(Random rng) {
        int total = 0;
        for (int p : PLENTY) {
            total += p;
        }
        int r = rng.nextInt(total);
        for (int i = 0; i < PLENTY.length; i++) {
            r -= PLENTY[i];
            if (r < 0) {
                return ORES.charAt(i);
            }
        }
        return ORES.charAt(0);
    }

    /** A practice question about {@code row}: an ore it holds seven times in ten, otherwise one it lacks. */
    public static char asked(Random rng, String row) {
        StringBuilder held = new StringBuilder(), lacked = new StringBuilder();
        for (char o : ORES.toCharArray()) {
            (row.indexOf(o) >= 0 ? held : lacked).append(o);
        }
        boolean present = !held.isEmpty() && (lacked.isEmpty() || rng.nextInt(10) < 7);
        CharSequence from = present ? held : lacked;
        return from.charAt(rng.nextInt(from.length()));
    }

    /** The likeliest answer the model gives to each prompt, all read in one batch: a place or {@link #NONE}. */
    public static char[] ask(CompiledGraph model, List<String> prompts) {
        int n = prompts.size(), t = ROW + 3;
        Vocab vocab = Vocab.WORLD;
        int[] in = new int[n * t];
        for (int i = 0; i < n; i++) {
            String p = prompts.get(i);
            for (int k = 0; k < t; k++) {
                in[i * t + k] = vocab.id(p.charAt(k));
            }
        }
        Tensor logits = model.forward(null, new Tokens(in, n, t), null);
        int v = logits.dim(-1);
        String answers = PLACES + NONE;
        char[] out = new char[n];
        for (int i = 0; i < n; i++) {
            int off = (i * t + t - 1) * v;
            char best = NONE;
            float top = Float.NEGATIVE_INFINITY;
            for (char c : answers.toCharArray()) {
                float l = logits.data[off + vocab.id(c)];
                if (l > top) {
                    top = l;
                    best = c;
                }
            }
            out[i] = best;
        }
        return out;
    }
}
