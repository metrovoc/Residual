package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Ground as a model can write it: rows of {@link Survey#ROW} blocks, a capital each, as a Surveyor reads them. A model that has
 * practised on such rows can go on writing ground nobody has dug, and an Arena at the Frontier tier takes what it writes as
 * material, each ore as far as the ground really holds that much of it.
 */
public final class Ground {
    /** The ores a row can pay with, then all else a row can hold. */
    public static final String ORES = Survey.FINDS.replace("K", "");
    private static final String LETTERS = ORES + "SDTAWLB";
    /** Rows a model draws when it is checked. */
    private static final int CHECKED = 256;
    /** The fewest rows of ground a log needs to check a model against. */
    public static final int FEWEST = 64;

    private Ground() {
    }

    /** Whether a Field Log holds rows of ground: a Surveyor's, which alone start with a capital. */
    public static boolean isLog(String log) {
        return !log.isEmpty() && Character.isUpperCase(log.charAt(0));
    }

    public static List<String> rows(String log) {
        return log.lines().filter(l -> l.length() == Survey.ROW).toList();
    }

    /** The share of blocks each ore makes up in {@code rows}. */
    public static float[] shares(List<String> rows) {
        float[] out = new float[ORES.length()];
        for (String row : rows) {
            for (int i = 0; i < row.length(); i++) {
                int ore = ORES.indexOf(row.charAt(i));
                if (ore >= 0) {
                    out[ore] += 1f / (rows.size() * Survey.ROW);
                }
            }
        }
        return out;
    }

    /** Writes {@code n} rows from nothing, a letter at a time, each drawn from what the model makes likely. */
    public static String[] draw(CompiledGraph model, int n, Random rng) {
        Vocab vocab = model.env.tokenizer();
        int t = Survey.ROW;
        int[] in = new int[n];
        Arrays.fill(in, vocab.id('\n'));
        StringBuilder[] rows = new StringBuilder[n];
        for (int i = 0; i < n; i++) {
            rows[i] = new StringBuilder(t);
        }
        for (int k = 0; k < t; k++) {
            Tensor logits = model.forward(null, new Tokens(in, n, k + 1), null);
            int v = logits.dim(-1);
            int[] next = new int[n * (k + 2)];
            for (int i = 0; i < n; i++) {
                char c = pick(logits.data, (i * (k + 1) + k) * v, vocab, rng);
                rows[i].append(c);
                System.arraycopy(in, i * (k + 1), next, i * (k + 2), k + 1);
                next[i * (k + 2) + k + 1] = vocab.id(c);
            }
            in = next;
        }
        String[] out = new String[n];
        for (int i = 0; i < n; i++) {
            out[i] = rows[i].toString();
        }
        return out;
    }

    private static char pick(float[] logits, int off, Vocab vocab, Random rng) {
        double top = Double.NEGATIVE_INFINITY, sum = 0;
        for (char c : LETTERS.toCharArray()) {
            top = Math.max(top, logits[off + vocab.id(c)]);
        }
        double[] p = new double[LETTERS.length()];
        for (int a = 0; a < p.length; a++) {
            p[a] = Math.exp(logits[off + vocab.id(LETTERS.charAt(a))] - top);
            sum += p[a];
        }
        double r = rng.nextDouble() * sum;
        for (int a = 0; a < p.length; a++) {
            r -= p[a];
            if (r <= 0) {
                return LETTERS.charAt(a);
            }
        }
        return LETTERS.charAt(ORES.length());
    }

    /**
     * How far each ore a model draws can be taken at its word: 1 while it draws no more of that ore than the rows hold, less as it
     * draws more, and 0 for an ore those rows do not hold. A model cannot write more into the world than the ground it knows.
     */
    public static float[] trust(CompiledGraph model, List<String> rows, Random rng) {
        float[] real = shares(rows);
        float[] drawn = shares(List.of(draw(model, CHECKED, rng)));
        float[] out = new float[real.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = drawn[i] <= real[i] ? (real[i] > 0 ? 1 : 0) : real[i] / drawn[i];
        }
        return out;
    }
}
