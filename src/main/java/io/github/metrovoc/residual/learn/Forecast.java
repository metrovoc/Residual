package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * What a Forecaster does with a model: checks it against the blocks it watched, then runs them ahead by drawing their
 * next looks from it. Both read a block's line one look per token, after the lines of the blocks before it. A crop may
 * only stay or grow; a machine may move on through its items, or cool.
 */
public final class Forecast {
    /** Progress a kind of block must have shown before a model can be checked against it: ages, or eighths of items. */
    public static final int MIN_GROWTH = 100;
    private static final int BATCH = 32;

    /** What a Forecaster watches: crops by their age, machines by their progress through their items. */
    public enum Kind {
        CROPS(Growth.RIPE), MACHINES(Growth.EIGHTHS);

        /** Where a look can start from: an age short of ripe, or an eighth of an item. */
        public final int from;

        Kind(int from) {
            this.from = from;
        }

        public static Kind of(char look) {
            return Growth.isAge(look) ? CROPS : Growth.isProgress(look) ? MACHINES : null;
        }
    }

    /**
     * The progress a model expected over the looks of one kind of block, the progress there was, and what it expected
     * per look from each place a look can start.
     */
    public record Fit(double expected, int grown, float[] expects) {
        /** How much more progress the model expects than the blocks showed. */
        public double ratio() {
            return grown <= 0 ? Double.NaN : expected / grown;
        }

        /** Looks it runs a block ahead per look the world takes: more for a model whose progress matches the blocks'. */
        public int ahead() {
            double off = Math.abs(Math.log(ratio()));
            return grown < MIN_GROWTH || Double.isNaN(off) ? 0 : off <= Math.log(1.11) ? 4 : off <= Math.log(1.25) ? 2 : 0;
        }
    }

    /** A model checked against everything a Forecaster watched, its crops and its machines apart. */
    public record Check(Fit crops, Fit machines) {
        public Fit of(Kind kind) {
            return kind == Kind.CROPS ? crops : machines;
        }
    }

    private Forecast() {
    }

    public static Check check(CompiledGraph model, String log) {
        Vocab vocab = model.env.tokenizer();
        int[] ids = vocab.spell(log);
        int t = Math.min(model.env.context(), Vocab.CONTEXT), stride = Math.max(1, t / 2);
        // windows overlap by half, and each scores only the looks the one before did not reach, so every look but
        // the first few has half a window of the blocks behind it
        List<Integer> starts = new ArrayList<>();
        for (int s = 0; ids.length > 1; s += stride) {
            starts.add(s);
            if (s + t >= ids.length - 1) {
                break;
            }
        }
        int kinds = Kind.values().length;
        double[] expected = new double[kinds];
        int[] grown = new int[kinds];
        double[][] expectBy = new double[kinds][];
        int[][] looksBy = new int[kinds][];
        for (Kind k : Kind.values()) {
            expectBy[k.ordinal()] = new double[k.from];
            looksBy[k.ordinal()] = new int[k.from];
        }
        int scored = 0;
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
                    char from = log.charAt(at - 1), to = log.charAt(at);
                    Kind kind = Kind.of(from);
                    if (kind == null || Kind.of(to) != kind) {
                        continue;
                    }
                    double[] p = looks(logits.data, (w * t + i) * v, v, vocab, kind);
                    int k = kind.ordinal(), place = place(from);
                    double e = expected(p, from);
                    expected[k] += e;
                    grown[k] += moved(from, to);
                    if (place < kind.from) {
                        expectBy[k][place] += e;
                        looksBy[k][place]++;
                    }
                }
            }
        }
        Fit[] fits = new Fit[kinds];
        for (Kind kind : Kind.values()) {
            int k = kind.ordinal();
            float[] expects = new float[kind.from];
            for (int a = 0; a < kind.from; a++) {
                expects[a] = looksBy[k][a] == 0 ? Float.NaN : (float) (expectBy[k][a] / looksBy[k][a]);
            }
            fits[k] = new Fit(expected[k], grown[k], expects);
        }
        return new Check(fits[0], fits[1]);
    }

    /**
     * How far the blocks of a kind moved per look from each place a look can start (an age short of ripe, or an
     * eighth of an item), or NaN for a place never seen.
     */
    public static float[] seen(String log, Kind kind) {
        int[] grown = new int[kind.from], looks = new int[kind.from];
        for (int i = 1; i < log.length(); i++) {
            char from = log.charAt(i - 1), to = log.charAt(i);
            if (Kind.of(from) == kind && Kind.of(to) == kind && place(from) < kind.from) {
                grown[place(from)] += moved(from, to);
                looks[place(from)]++;
            }
        }
        float[] rates = new float[kind.from];
        for (int a = 0; a < kind.from; a++) {
            rates[a] = looks[a] == 0 ? Float.NaN : (float) grown[a] / looks[a];
        }
        return rates;
    }

    /** How far the blocks of a kind moved in all over a record's lines: ages grown, or eighths of items made. */
    public static int grown(String record, Kind kind) {
        int sum = 0;
        for (int i = 1; i < record.length(); i++) {
            char from = record.charAt(i - 1), to = record.charAt(i);
            if (Kind.of(from) == kind && Kind.of(to) == kind) {
                sum += moved(from, to);
            }
        }
        return sum;
    }

    /**
     * Runs each block {@code steps} looks ahead: its next look drawn from the model given the end of the record and
     * its own line so far, among the looks it can reach, again and again. Returns the looks drawn for each block.
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
                char last = context[c].charAt(t - 1);
                Kind kind = Kind.of(last);
                if (kind == null) {
                    continue;
                }
                char look = draw(looks(logits.data, (c * t + t - 1) * v, v, vocab, kind), last, rng);
                drawn[c] += look;
                context[c].deleteCharAt(0).append(look);
            }
        }
        return drawn;
    }

    /** How many eighths of items a machine's drawn looks move it on, counting only the way forward. */
    public static int made(char from, String drawn) {
        int sum = 0;
        for (char look : drawn.toCharArray()) {
            sum += Math.max(0, Growth.advance(from, look));
            from = look;
        }
        return sum;
    }

    /** The model's probabilities for each look of a kind as the next token, out of all tokens. */
    private static double[] looks(float[] logits, int off, int v, Vocab vocab, Kind kind) {
        double max = Double.NEGATIVE_INFINITY;
        for (int j = 0; j < v; j++) {
            max = Math.max(max, logits[off + j]);
        }
        double sum = 0;
        for (int j = 0; j < v; j++) {
            sum += Math.exp(logits[off + j] - max);
        }
        int n = kind == Kind.CROPS ? Growth.RIPE + 1 : Growth.EIGHTHS;
        double[] p = new double[n];
        for (int a = 0; a < n; a++) {
            int id = vocab.id(look(kind, a));
            p[a] = id < v ? Math.exp(logits[off + id] - max) / sum : 0;
        }
        return p;
    }

    private static char look(Kind kind, int place) {
        return (char) ((kind == Kind.CROPS ? '0' : Growth.IDLE) + place);
    }

    /** Where a look stands: a crop's age, or a machine's eighth. */
    private static int place(char look) {
        return Growth.isAge(look) ? look - '0' : look - Growth.IDLE;
    }

    /** How far a block moved from one look to the next: ages grown, or eighths of an item made (back as it cools). */
    private static int moved(char from, char to) {
        return Growth.isAge(from) ? to - from : Growth.advance(from, to);
    }

    /** What the model expects a block to move from {@code from}, among the looks it can reach. */
    private static double expected(double[] p, char from) {
        double mass = 0, move = 0;
        for (int a = reachable(from); a < p.length; a++) {
            char to = look(Kind.of(from), a);
            mass += p[a];
            move += moved(from, to) * p[a];
        }
        return mass > 0 ? move / mass : 0;
    }

    /** The first look a block can reach from {@code from}: a crop never shrinks; a machine can show any progress. */
    private static int reachable(char from) {
        return Growth.isAge(from) ? place(from) : 0;
    }

    private static char draw(double[] p, char from, Random rng) {
        int first = reachable(from);
        double mass = 0;
        for (int a = first; a < p.length; a++) {
            mass += p[a];
        }
        if (mass <= 0) {
            return from;
        }
        double r = rng.nextDouble() * mass;
        for (int a = first; a < p.length - 1; a++) {
            r -= p[a];
            if (r < 0) {
                return look(Kind.of(from), a);
            }
        }
        return look(Kind.of(from), p.length - 1);
    }
}
