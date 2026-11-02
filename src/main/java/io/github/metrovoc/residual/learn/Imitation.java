package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a mob does, written down look by look, and how well a model does it after it. A line starts with a capital for the
 * mob's kind; then for each look a letter for how things stand with it and a digit for what it did before the next look. The
 * letter is how far off whatever it is after stands ({@link #state}); the digit one of {@link #STILL} to {@link #BLINK}. Mobs
 * fight in rhythms, so only a model that reads the line back can tell when the next blow falls.
 *
 * <p>Practising on such lines is behaviour cloning, and a model's score is the plain share of looks where its likeliest action
 * is the one the mob really took, on looks it has not practised on.
 */
public final class Imitation {
    /** Ticks between looks: four a second. */
    public static final int LOOK = 5;
    /** The most looks on a line: with its kind's letter, as much as a model reads at once. */
    public static final int LOOKS = (Vocab.CONTEXT - 1) / 2;
    /** The fewest looks worth writing down, and the looks a score lets a line show itself first. */
    public static final int FEWEST = 12, SHOWN = 8;
    /** What a mob can be seen doing between two looks. */
    public static final char STILL = '0', CLOSER = '1', AWAY = '2', ASIDE = '3', LEAP = '4', STRIKE = '5', READY = '6', BLINK = '7';
    public static final String ACTIONS = "01234567";
    /** How far off, in blocks, each band but the last ends. */
    private static final double[] BANDS = {2, 3.5, 6, 10, 16};
    private static final int TARGETED = BANDS.length + 1, HURT = 2 * TARGETED;
    /** Lines a batch of a score reads at once. */
    private static final int BATCH = 64;
    /** How far from the plain table to the one that reads back a model has to get for each tier of likeness, weakest first. */
    public static final double[] TIERS = {0.2, 0.4, 0.6, 0.8, 1.0};
    /** How far back the tables that read back read, and the fewest times a table has to have seen a thing to go by it. */
    private static final int[] READ_BACK = {1, 2, 4, 8};
    private static final int SEEN_ENOUGH = 3;
    /** The gap between the two tables is never taken for less than this: on some mobs reading back is worth next to nothing. */
    private static final double SMALLEST_GAP = 0.02;
    /**
     * The looks a check has to count for each tier. A short log cannot vouch for much: its share right is uncertain, and its tables
     * have seen little, which a model that has practised on more can walk past without being any good.
     */
    public static final int[] LOOKS_FOR = {100, 200, 400, 800, 1600};

    private Imitation() {
    }

    /** The letter for how things stand: how far off, whether it is after anything, and whether it has just been hurt. */
    public static char state(double distance, boolean targeting, boolean hurt) {
        int band = 0;
        while (band < BANDS.length && distance >= BANDS[band]) {
            band++;
        }
        return (char) ('a' + band + (targeting ? TARGETED : 0) + (hurt ? HURT : 0));
    }

    /**
     * The capital a kind of mob's lines start with. Two kinds may share one, as two words may share a letter; the number keeps the
     * ones met most apart.
     */
    public static char letter(String kindId) {
        return (char) ('A' + Math.floorMod(kindId.hashCode() * 16525 >> 16, 26));
    }

    /**
     * How a model did on a log: looks scored and the share of them it got right, set beside two tables counted on the lines it practised
     * on. Some mobs mostly do one thing and are easy to be right about, and some are hard to be right about whatever reads them, so a
     * share right says little by itself. The plain table goes by the letter alone; the other also reads back along the line, as far as
     * pays on this log.
     */
    public record Check(long looks, double score, double plain, double readBack) {
        /** How far the model got from the plain table to the one that reads back: 0 at the first, 1 at the second, and it may go past. */
        public double reach() {
            return (score - plain) / Math.max(SMALLEST_GAP, readBack - plain);
        }

        /** The tier that earns, as far as the looks counted can vouch for it, 0 for none. */
        public int tier() {
            int tier = Imitation.tier(reach());
            while (tier > 0 && looks < LOOKS_FOR[tier - 1]) {
                tier--;
            }
            return tier;
        }
    }

    /** Checks a model on the lines of a log that practice never reads. */
    public static Check check(CompiledGraph model, List<String> lines) {
        List<String> held = heldOut(lines), read = practised(lines);
        long[] score = score(model, held);
        return new Check(score[0], score[0] == 0 ? 0 : (double) score[1] / score[0], plainTable(read, held), readBackTable(read, held));
    }

    /** The share of {@code kept}'s looks a table of what the mobs of {@code read} most often did after each letter gets right. */
    public static double plainTable(List<String> read, List<String> kept) {
        return table(read, kept, 0);
    }

    /** The same for the best of the tables that also read back a few looks, falling back on fewer where they have seen too little. */
    public static double readBackTable(List<String> read, List<String> kept) {
        double best = 0;
        for (int back : READ_BACK) {
            best = Math.max(best, table(read, kept, back));
        }
        return best;
    }

    private static double table(List<String> read, List<String> kept, int back) {
        List<Map<Integer, int[]>> tables = new ArrayList<>();
        for (int n = 0; n <= back; n++) {
            tables.add(new HashMap<>());
        }
        for (String line : read) {
            for (int k = 0; 2 + 2 * k < line.length(); k++) {
                for (int n = 0; n <= back; n++) {
                    tables.get(n).computeIfAbsent(key(line, k, n), x -> new int[ACTIONS.length()])[ACTIONS.indexOf(line.charAt(2 + 2 * k))]++;
                }
            }
        }
        long scored = 0, right = 0;
        for (String line : kept) {
            for (int k = SHOWN; 2 + 2 * k < line.length(); k++) {
                int best = 0;
                for (int n = back; n >= 0; n--) {
                    int[] after = tables.get(n).get(key(line, k, n));
                    if (after != null && (n == 0 || Arrays.stream(after).sum() >= SEEN_ENOUGH)) {
                        for (int a = 0; a < after.length; a++) {
                            best = after[a] > after[best] ? a : best;
                        }
                        break;
                    }
                }
                scored++;
                right += ACTIONS.charAt(best) == line.charAt(2 + 2 * k) ? 1 : 0;
            }
        }
        return scored == 0 ? 0 : (double) right / scored;
    }

    /** One number for look {@code k}'s letter and the actions of the {@code n} looks before it. */
    private static int key(String line, int k, int n) {
        int key = line.charAt(1 + 2 * k) - 'a' + 1;
        for (int i = 1; i <= n && k - i >= 0; i++) {
            key = key * (ACTIONS.length() + 1) + ACTIONS.indexOf(line.charAt(2 + 2 * (k - i))) + 1;
        }
        return key;
    }

    /** The tier a reach earns, 0 for none. */
    public static int tier(double reach) {
        int tier = 0;
        while (tier < TIERS.length && reach >= TIERS[tier]) {
            tier++;
        }
        return tier;
    }

    /** Whether a mob played at {@code tier} dies as if a player killed it, which is what the rarer drops ask. */
    public static boolean byPlayer(int tier) {
        return tier >= 2;
    }

    /** The Looting its death counts at. */
    public static int looting(int tier) {
        return Math.max(0, tier - 2);
    }

    /** Whether the mobs of this log go after things, by how often the log shows them doing so. */
    public static boolean targeting(String log) {
        int after = 0, all = 0;
        for (String line : lines(log)) {
            for (int k = 1; k < line.length(); k += 2) {
                after += (line.charAt(k) - 'a') % HURT >= TARGETED ? 1 : 0;
                all++;
            }
        }
        return after * 4 >= all;
    }

    /** Looks of these lines a score would count. */
    public static int scorable(List<String> lines) {
        int n = 0;
        for (String line : lines) {
            n += Math.max(0, (line.length() - 1) / 2 - SHOWN);
        }
        return n;
    }

    /** The whole lines of a log, in the order they were written. */
    public static List<String> lines(String log) {
        return log.lines().filter(l -> l.length() > 1 + 2 * SHOWN).toList();
    }

    /** What a model reads before it acts: the line's capital and its newest looks, then how things stand now. */
    public static String prompt(CharSequence line, char state) {
        // as practice has it: the last state a line ever scores stands after its kind and a full line's looks but one
        int keep = 2 * (LOOKS - 1);
        StringBuilder out = new StringBuilder(Vocab.CONTEXT).append(line.charAt(0));
        out.append(line, 1 + Math.max(0, line.length() - 1 - keep), line.length());
        return out.append(state).toString();
    }

    /** How likely {@code model} makes each action after each prompt, all read in one batch. */
    public static float[][] actions(CompiledGraph model, List<String> prompts) {
        int n = prompts.size(), t = 0;
        for (String p : prompts) {
            t = Math.max(t, p.length());
        }
        Vocab vocab = model.env.tokenizer();
        int[] in = new int[n * t];
        Arrays.fill(in, vocab.id('\n'));
        for (int i = 0; i < n; i++) {
            String p = prompts.get(i);
            for (int k = 0; k < p.length(); k++) {
                in[i * t + k] = vocab.id(p.charAt(k));
            }
        }
        Tensor logits = model.forward(null, new Tokens(in, n, t), null);
        int v = logits.dim(-1);
        float[][] out = new float[n][ACTIONS.length()];
        for (int i = 0; i < n; i++) {
            int off = (i * t + prompts.get(i).length() - 1) * v;
            double top = Double.NEGATIVE_INFINITY, sum = 0;
            for (int a = 0; a < out[i].length; a++) {
                top = Math.max(top, logits.data[off + vocab.id(ACTIONS.charAt(a))]);
            }
            for (int a = 0; a < out[i].length; a++) {
                double p = Math.exp(logits.data[off + vocab.id(ACTIONS.charAt(a))] - top);
                out[i][a] = (float) p;
                sum += p;
            }
            for (int a = 0; a < out[i].length; a++) {
                out[i][a] /= (float) sum;
            }
        }
        return out;
    }

    /** Looks scored on {@code lines}, and how many of them the model's likeliest action was what the mob really did. */
    public static long[] score(CompiledGraph model, List<String> lines) {
        Vocab vocab = model.env.tokenizer();
        int t = Vocab.CONTEXT;
        long scored = 0, right = 0;
        for (int from = 0; from < lines.size(); from += BATCH) {
            List<String> batch = lines.subList(from, Math.min(lines.size(), from + BATCH));
            int[] in = new int[batch.size() * t];
            Arrays.fill(in, vocab.id('\n'));
            for (int i = 0; i < batch.size(); i++) {
                String line = batch.get(i);
                for (int k = 0; k < Math.min(t, line.length()); k++) {
                    in[i * t + k] = vocab.id(line.charAt(k));
                }
            }
            Tensor logits = model.forward(null, new Tokens(in, batch.size(), t), null);
            int v = logits.dim(-1);
            for (int i = 0; i < batch.size(); i++) {
                String line = batch.get(i);
                // look k's state is at 1 + 2k, and its action next
                for (int k = SHOWN; 2 + 2 * k < Math.min(t, line.length()); k++) {
                    int off = (i * t + 1 + 2 * k) * v;
                    char best = STILL;
                    float top = Float.NEGATIVE_INFINITY;
                    for (char c : ACTIONS.toCharArray()) {
                        float l = logits.data[off + vocab.id(c)];
                        if (l > top) {
                            top = l;
                            best = c;
                        }
                    }
                    scored++;
                    right += best == line.charAt(2 + 2 * k) ? 1 : 0;
                }
            }
        }
        return new long[] {scored, right};
    }

    /**
     * The lines kept for checking, which practice never reads: a tenth of them, by what each line says, so a line stays on its side
     * however a log grows, and the tenth comes from all through it rather than from one stretch of one fight.
     */
    public static List<String> heldOut(List<String> lines) {
        return lines.stream().filter(Imitation::kept).toList();
    }

    /** The lines practice reads. */
    public static List<String> practised(List<String> lines) {
        return lines.stream().filter(l -> !kept(l)).toList();
    }

    private static boolean kept(String line) {
        return Math.floorMod(line.hashCode() * 16525 >> 16, 10) == 0;
    }
}
