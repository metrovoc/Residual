package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Decoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Reckoning: adding up a line of numbers, as an assay of a melt comes, writing the running sum after each one: {@code > 12,7,30,5⏎= 19 49 54⏎}. The
 * last number is the answer. Each step can be checked by itself, by anyone, with no answer to go by, which is what makes it a task for thinking
 * longer: a wrong answer can be caught, and another tried.
 *
 * <p>It is practised on worked sums and scored by writing them, each once, the likeliest token each time, on the whole answer being right.
 */
public final class Reckoning extends Task {
    public static final int MOST_TERMS = 5, WRITTEN = 16;
    private static final int TEST_SUMS = 256;

    public Reckoning() {
        super(Vocab.WORLD, Vocab.CONTEXT, Metric.ACCURACY, Benchmark.RECKONING.target);
    }

    /** One sum: its terms and how it is worked. */
    public record Sum(int[] terms) {
        public String said() {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < terms.length; i++) {
                out.append(i > 0 ? "," : "").append(terms[i]);
            }
            return out.toString();
        }

        public int total() {
            int sum = 0;
            for (int t : terms) {
                sum += t;
            }
            return sum;
        }

        /** The running sums, with nothing for the first term. */
        public String worked() {
            StringBuilder out = new StringBuilder();
            int sum = terms[0];
            for (int i = 1; i < terms.length; i++) {
                sum += terms[i];
                out.append(i > 1 ? " " : "").append(sum);
            }
            return out.toString();
        }
    }

    /** A sum to do; the test holds back sums whose first term ends in 7. */
    public static Sum sum(Random rng, boolean test) {
        int n = 2 + rng.nextInt(MOST_TERMS - 1);
        int[] terms = new int[n];
        for (int i = 0; i < n; i++) {
            terms[i] = 1 + rng.nextInt(rng.nextBoolean() ? 9 : 50);
        }
        // kept apart by the first term, so that no sum of the test was ever practised
        terms[0] = terms[0] - terms[0] % 10 + (test ? 7 : 1 + rng.nextInt(6));
        return new Sum(terms);
    }

    public static String prompt(Sum sum) {
        return "> " + sum.said() + "\n= ";
    }

    /** How many steps of {@code written} are right for {@code sum}, in order; {@code steps} says how many it had. */
    public static boolean right(Sum sum, String written) {
        return written.equals(sum.worked());
    }

    /**
     * Whether each step of {@code written} follows from the one before and the terms {@code said}: nothing but arithmetic, no answer to go by. An exact verifier for a
     * whole answer and for each step of one.
     */
    public static int stepsRight(String said, String written) {
        String[] parts = said.split(",");
        String[] steps = written.isBlank() ? new String[0] : written.strip().split(" ");
        int right = 0;
        try {
            long sum = Long.parseLong(parts[0]);
            for (int i = 0; i < steps.length && i + 1 < parts.length; i++) {
                sum += Long.parseLong(parts[i + 1]);
                if (Long.parseLong(steps[i]) != sum) {
                    return right;
                }
                right++;
            }
        } catch (NumberFormatException e) {
            return right;
        }
        return right;
    }

    /** Whether {@code written} is a whole, right working of what was {@code said}. */
    public static boolean verified(String said, String written) {
        int steps = said.split(",").length - 1;
        return written.strip().split(" ").length == steps && stepsRight(said, written) == steps;
    }

    /** The answer a working comes to: its last number, or null if it has none. */
    public static String answer(String written) {
        String[] steps = written.strip().split(" ");
        return steps.length == 0 || steps[steps.length - 1].isEmpty() ? null : steps[steps.length - 1];
    }

    /** Worked sums, one after another; only what comes after {@code =} counts. */
    @Override
    public Batch batch(Random rng, int size, boolean test) {
        int t = context;
        int[] in = new int[size * t];
        int[] tg = new int[size * t];
        float[] w = new float[size * t];
        for (int b = 0; b < size; b++) {
            StringBuilder text = new StringBuilder();
            List<int[]> marks = new ArrayList<>();
            while (text.length() <= t) {
                Sum sum = sum(rng, test);
                text.append(prompt(sum));
                int start = text.length();
                text.append(sum.worked()).append('\n');
                marks.add(new int[]{start, text.length()});
            }
            for (int i = 0; i < t; i++) {
                in[b * t + i] = vocab.id(text.charAt(i));
                tg[b * t + i] = vocab.id(text.charAt(i + 1));
            }
            for (int[] m : marks) {
                for (int i = m[0]; i < m[1] && i <= t; i++) {
                    if (i > 0) {
                        w[b * t + i - 1] = 1f;
                    }
                }
            }
        }
        return new Batch(new io.github.metrovoc.residual.tensor.Tokens(in, size, t), tg, w);
    }

    /** The share of {@code sums} sums {@code model} works out right, writing each once. */
    public static double score(CompiledGraph model, int sums, long seed) {
        Decoder d = new Decoder(model, Decoder.floats(model));
        Random rng = new Random(seed);
        int passed = 0;
        for (int i = 0; i < sums; i++) {
            Sum sum = sum(rng, true);
            Programs.Attempt written = Programs.write(d, prompt(sum), 1, 0f, rng).getFirst();
            passed += right(sum, written.text()) ? 1 : 0;
        }
        return passed / (double) sums;
    }

    @Override
    public double[] measure(CompiledGraph model) {
        double score = score(model, TEST_SUMS, 909);
        return new double[]{1 - score, score};
    }
}
