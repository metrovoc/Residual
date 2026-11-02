package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Sums on a wheel of {@link #P} teeth: two residues and {@code =}, a character each, and the answer is their sum, going round
 * the wheel. Practice shows a fixed {@link #SHARE_PERCENT}% of all the pairs, and the test is the rest, so nothing but the
 * rule can carry a model from one to the other, and both can be read off exactly, every pair at once.
 */
public final class Modular {
    public static final int P = 59;
    /** The residues, a character each. */
    public static final String RESIDUES = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456";
    /** The share of pairs practice shows. */
    public static final int SHARE_PERCENT = 40;
    /** Three characters a question: two residues and an equals sign. */
    public static final int LENGTH = 3;

    private static final int[][] PAIRS = new int[2][];

    static {
        List<Integer> practice = new ArrayList<>(), test = new ArrayList<>();
        // a fixed deal of the pairs, the same in every world
        Random deal = new Random(59);
        for (int a = 0; a < P; a++) {
            for (int b = 0; b < P; b++) {
                (deal.nextInt(100) < SHARE_PERCENT ? practice : test).add(a * P + b);
            }
        }
        PAIRS[0] = practice.stream().mapToInt(Integer::intValue).toArray();
        PAIRS[1] = test.stream().mapToInt(Integer::intValue).toArray();
    }

    private Modular() {
    }

    /** Every pair of one side of the deal, each as {@code a * P + b}. */
    public static int[] pairs(boolean test) {
        return PAIRS[test ? 1 : 0];
    }

    /** A question and its answer, as characters: {@code ab=c}. */
    public static String line(int pair) {
        int a = pair / P, b = pair % P;
        return "" + RESIDUES.charAt(a) + RESIDUES.charAt(b) + '=' + RESIDUES.charAt((a + b) % P);
    }

    /** Exactly what share of one side's pairs the model gets right, every pair asked once. */
    public static double right(CompiledGraph model, boolean test) {
        Vocab vocab = model.env.tokenizer();
        int[] pairs = pairs(test);
        int right = 0;
        int chunk = 512;
        for (int from = 0; from < pairs.length; from += chunk) {
            int n = Math.min(chunk, pairs.length - from);
            int[] ids = new int[n * LENGTH];
            for (int i = 0; i < n; i++) {
                String line = line(pairs[from + i]);
                for (int k = 0; k < LENGTH; k++) {
                    ids[i * LENGTH + k] = vocab.id(line.charAt(k));
                }
            }
            Tensor logits = model.forward(null, new Tokens(ids, n, LENGTH), null);
            int v = logits.dim(-1);
            for (int i = 0; i < n; i++) {
                int answer = vocab.id(line(pairs[from + i]).charAt(LENGTH));
                if (Trainer.argmax(logits.data, (i * LENGTH + LENGTH - 1) * v, v) == answer) {
                    right++;
                }
            }
        }
        return (double) right / pairs.length;
    }
}
