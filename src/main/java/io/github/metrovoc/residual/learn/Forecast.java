package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * What a Forecaster does with a model: checks it against the field it watched, then runs crops ahead by drawing their
 * next looks from it. Both read a crop's line one look per token, after the lines of the field's crops before it, and
 * only ever let a crop stay or grow.
 */
public final class Forecast {
    /** Growth a field must have shown before a model can be checked against it. */
    public static final int MIN_GROWTH = 100;
    private static final int BATCH = 32;

    /** The growth a model expected over a field's lines, the growth there was, and what it expected per look by age. */
    public record Check(double expected, int grown, float[] expects) {
        /** How much more growth the model expects than the field showed. */
        public double ratio() {
            return grown == 0 ? Double.NaN : expected / grown;
        }

        /** Looks it runs a crop ahead per look the world takes: more for a model whose growth matches the field's. */
        public int ahead() {
            double off = Math.abs(Math.log(ratio()));
            return grown < MIN_GROWTH || Double.isNaN(off) ? 0 : off <= Math.log(1.11) ? 4 : off <= Math.log(1.25) ? 2 : 0;
        }
    }

    private Forecast() {
    }

    public static Check check(CompiledGraph model, String log) {
        Vocab vocab = model.env.tokenizer();
        int[] ids = vocab.spell(log);
        int t = Math.min(model.env.context(), Vocab.CONTEXT), stride = Math.max(1, t / 2);
        // windows overlap by half, and each scores only the looks the one before did not reach, so every look but
        // the first few has half a window of the field behind it
        List<Integer> starts = new ArrayList<>();
        for (int s = 0; ids.length > 1; s += stride) {
            starts.add(s);
            if (s + t >= ids.length - 1) {
                break;
            }
        }
        double expected = 0;
        int grown = 0, scored = 0;
        double[] expectBy = new double[Growth.RIPE + 1];
        int[] looksBy = new int[Growth.RIPE + 1];
        for (int first = 0; first < starts.size(); first += BATCH) {
            int b = Math.min(BATCH, starts.size() - first);
            int[] in = new int[b * t];
            Arrays.fill(in, vocab.id('\n'));
            for (int w = 0; w < b; w++) {
                int start = starts.get(first + w);
                for (int i = 0; i < t && start + i < ids.length - 1; i++) {
                    in[w * t + i] = ids[start + i];
                }
            }
            Tensor logits = model.forward(null, new Tokens(in, b, t), null);
            int v = logits.dim(-1);
            for (int w = 0; w < b; w++) {
                for (int i = 0; i < t; i++) {
                    int at = starts.get(first + w) + i + 1;
                    if (at >= log.length()) {
                        break;
                    }
                    if (at <= scored) {
                        continue;
                    }
                    scored = at;
                    int from = age(log.charAt(at - 1)), to = age(log.charAt(at));
                    if (from < 0 || to < 0) {
                        continue;
                    }
                    double e = growth(digits(logits.data, (w * t + i) * v, v, vocab), from);
                    expected += e;
                    expectBy[from] += e;
                    grown += to - from;
                    looksBy[from]++;
                }
            }
        }
        float[] expects = new float[Growth.RIPE];
        for (int a = 0; a < Growth.RIPE; a++) {
            expects[a] = looksBy[a] == 0 ? Float.NaN : (float) (expectBy[a] / looksBy[a]);
        }
        return new Check(expected, grown, expects);
    }

    /** How much a field's crops grew per look from each age short of ripe, or NaN for an age it never saw. */
    public static float[] seen(String log) {
        int[] grown = new int[Growth.RIPE], looks = new int[Growth.RIPE];
        for (int i = 1; i < log.length(); i++) {
            int from = age(log.charAt(i - 1)), to = age(log.charAt(i));
            if (from >= 0 && from < Growth.RIPE && to >= 0) {
                grown[from] += to - from;
                looks[from]++;
            }
        }
        float[] rates = new float[Growth.RIPE];
        for (int a = 0; a < Growth.RIPE; a++) {
            rates[a] = looks[a] == 0 ? Float.NaN : (float) grown[a] / looks[a];
        }
        return rates;
    }

    /**
     * Runs each crop {@code steps} looks ahead: its next age drawn from the model given the end of the field's record
     * and its own line so far, among the ages it can reach, again and again. Returns the looks drawn for each crop.
     */
    public static String[] ahead(CompiledGraph model, String field, List<String> lines, int steps, Random rng) {
        Vocab vocab = model.env.tokenizer();
        int t = Math.min(model.env.context(), Vocab.CONTEXT);
        int n = lines.size();
        String[] drawn = new String[n];
        StringBuilder[] context = new StringBuilder[n];
        String before = "\n".repeat(t) + field;
        for (int c = 0; c < n; c++) {
            String text = before + lines.get(c);
            context[c] = new StringBuilder(text.substring(text.length() - t));
            drawn[c] = "";
        }
        for (int s = 0; s < steps; s++) {
            int[] in = new int[n * t];
            for (int c = 0; c < n; c++) {
                System.arraycopy(vocab.spell(context[c].toString()), 0, in, c * t, t);
            }
            Tensor logits = model.forward(null, new Tokens(in, n, t), null);
            int v = logits.dim(-1);
            for (int c = 0; c < n; c++) {
                int age = age(context[c].charAt(t - 1));
                double[] p = digits(logits.data, (c * t + t - 1) * v, v, vocab);
                int next = draw(p, age, rng);
                char look = (char) ('0' + next);
                drawn[c] += look;
                context[c].deleteCharAt(0).append(look);
            }
        }
        return drawn;
    }

    /** The model's probabilities for each age as the next token, out of all tokens. */
    private static double[] digits(float[] logits, int off, int v, Vocab vocab) {
        double max = Double.NEGATIVE_INFINITY;
        for (int j = 0; j < v; j++) {
            max = Math.max(max, logits[off + j]);
        }
        double sum = 0;
        for (int j = 0; j < v; j++) {
            sum += Math.exp(logits[off + j] - max);
        }
        double[] p = new double[Growth.RIPE + 1];
        for (int a = 0; a <= Growth.RIPE; a++) {
            p[a] = Math.exp(logits[off + vocab.id((char) ('0' + a))] - max) / sum;
        }
        return p;
    }

    /** Growth the model expects from {@code age}, among the ages a crop can reach from it. */
    private static double growth(double[] p, int age) {
        double mass = 0, grow = 0;
        for (int a = age; a <= Growth.RIPE; a++) {
            mass += p[a];
            grow += (a - age) * p[a];
        }
        return mass > 0 ? grow / mass : 0;
    }

    private static int draw(double[] p, int age, Random rng) {
        double mass = 0;
        for (int a = age; a <= Growth.RIPE; a++) {
            mass += p[a];
        }
        double r = rng.nextDouble() * mass;
        for (int a = age; a < Growth.RIPE; a++) {
            r -= p[a];
            if (r < 0) {
                return a;
            }
        }
        return mass > 0 ? Growth.RIPE : age;
    }

    private static int age(char c) {
        return c >= '0' && c <= '0' + Growth.RIPE ? c - '0' : -1;
    }
}
