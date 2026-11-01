package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.IntUnaryOperator;
import org.jetbrains.annotations.Nullable;

/**
 * Control: holding a Smelter's melt in its band. A melt's line starts with its ore, then gives {@link #LOOKS} looks at
 * its temperature, a letter each from {@code a} (coldest) to {@code h}, the band {@code d} and {@code e}, each followed
 * by the power the controller chose: {@code 0} cools it a step, {@code 1} holds, {@code 2} heats it a step. Each ore
 * drifts on a rhythm of its own as it melts, gold and iron cooling, copper and the rest giving off heat; a fresh charge of
 * cold ore drops it two steps at looks 8 and 16; now and then it jumps a step for no reason anyone could foresee.
 * A melt is held when at least {@link #HELD} of its scored looks end in the band. A model that sees only the last three
 * characters holds most copper melts but no gold or iron; a two-block transformer, which can tell the ore from the
 * line's start and where in the melt it is, passes after 200 to 400 steps from each of five seeds (CalibrationTest
 * {@code control_seeds}).
 *
 * <p>Practice is reinforcement learning at small scale: melts are played with the model's own choices, and it learns
 * from each choice by how much better the melt did after it than after other choices made at the same look and
 * temperature (advantage-weighted regression). The test plays melts of each ore alike with its likeliest choices and
 * scores the share held of the ore it holds worst, as a Smelter pays for them.
 */
public final class Control extends Task {
    public static final int LOOKS = 24;
    /** Looks a melt has to settle before it is scored. */
    public static final int SETTLE = 2;
    /** A melt is held when at least this share of its scored looks end in the band: in a Smelter, it makes three. */
    public static final double HELD = 0.9;
    /** Letters of the ores, as a melt's line starts: gold, iron, copper, the rest. */
    public static final String ORES = "gico";
    /** How each ore drifts as it melts: every so many looks, by so many steps. */
    private static final int[][] DRIFTS = {{3, -1}, {2, -1}, {3, 1}, {2, 1}};
    private static final double JUMP = 0.02;
    /** Melts a test plays, as many of each ore. */
    private static final int TEST_MELTS = 128;
    /** How sharply advantage-weighted regression favours a better choice, in looks held, and its largest weight. */
    private static final double BETA = 0.5, MOST_WEIGHT = 20;
    /** How much a look in the band counts for each look further on, and how often practice plays a power at random. */
    private static final double DISCOUNT = 0.7, EXPLORE = 0.2;

    /** The model being practised, which plays its own melts. */
    @Nullable
    private volatile CompiledGraph policy;

    public Control() {
        super(Vocab.WORLD, Vocab.CONTEXT, Metric.ACCURACY, 0.9);
    }

    public void policy(CompiledGraph model) {
        policy = model;
    }

    /** Which of {@link #ORES} {@code ore} melts as: gold, iron, copper, or the rest. */
    public static int ore(net.minecraft.world.item.ItemStack ore) {
        if (ore.is(net.neoforged.neoforge.common.Tags.Items.ORES_GOLD) || ore.is(net.neoforged.neoforge.common.Tags.Items.RAW_MATERIALS_GOLD)) {
            return 0;
        }
        if (ore.is(net.neoforged.neoforge.common.Tags.Items.ORES_IRON) || ore.is(net.neoforged.neoforge.common.Tags.Items.RAW_MATERIALS_IRON)) {
            return 1;
        }
        if (ore.is(net.neoforged.neoforge.common.Tags.Items.ORES_COPPER) || ore.is(net.neoforged.neoforge.common.Tags.Items.RAW_MATERIALS_COPPER)) {
            return 2;
        }
        return 3;
    }

    /** One melt: its temperature step and its line so far. */
    public static final class Melt {
        public final int ore;
        private int level = 3;
        private int look;
        private int looksIn;
        /** Whether each look ended in the band, scored or not. */
        private final boolean[] in = new boolean[LOOKS];
        public final StringBuilder line = new StringBuilder();

        public Melt(int ore) {
            this.ore = ore;
            line.append(ORES.charAt(ore));
        }

        /** The temperature's letter now. */
        public char band() {
            return (char) ('a' + level);
        }

        /** The temperature at look {@code k} so far, 0 to 7. */
        public int band(int k) {
            return line.charAt(1 + 2 * k) - 'a';
        }

        public boolean over() {
            return look >= LOOKS;
        }

        /** Sets the power at this look and lets the melt move on to the next. */
        public void act(int power, Random rng) {
            line.append(band()).append((char) ('0' + power));
            level += power - 1;
            int[] drift = DRIFTS[ore];
            if (look % drift[0] == drift[0] - 1) {
                level += drift[1];
            }
            if (look == 8 || look == 16) {
                level -= 2;
            }
            if (rng.nextDouble() < JUMP) {
                level += rng.nextBoolean() ? 1 : -1;
            }
            level = Math.clamp(level, 0, 7);
            in[look] = level == 3 || level == 4;
            if (look >= SETTLE && in[look]) {
                looksIn++;
            }
            look++;
        }

        /** The share of scored looks it ended in the band. */
        public double score() {
            return looksIn / (double) (LOOKS - SETTLE);
        }

        /** Whether it was held: played to the end with at least {@link #HELD} of its scored looks in the band. */
        public boolean held() {
            return over() && score() >= HELD;
        }

        public int look() {
            return look;
        }
    }

    /** What a controller sees before it chooses: the melt so far, then the temperature now. */
    public static String prompt(Melt melt) {
        return "\n" + melt.line + melt.band();
    }

    /**
     * Plays one look of every melt at once: the model reads each prompt and chooses a power, the likeliest one or one
     * drawn from its probabilities.
     */
    private static void look(CompiledGraph model, List<Melt> melts, Random rng, boolean draw) {
        int n = melts.size();
        // every melt is at the same look, so its prompt is as long as the others: the model reads only that far
        int t = Math.min(Vocab.CONTEXT, prompt(melts.getFirst()).length());
        int[] in = new int[n * t];
        for (int i = 0; i < n; i++) {
            String p = prompt(melts.get(i));
            p = p.substring(p.length() - t);
            for (int k = 0; k < t; k++) {
                in[i * t + k] = Vocab.WORLD.id(p.charAt(k));
            }
        }
        Tensor logits = model.forward(null, new Tokens(in, n, t), null);
        int v = logits.dim(-1);
        for (int i = 0; i < n; i++) {
            // while practising, now and then a power at random, so it keeps finding out what each one does
            int power = draw && rng.nextDouble() < EXPLORE ? rng.nextInt(3) : choose(logits.data, (i * t + t - 1) * v, rng, draw);
            melts.get(i).act(power, rng);
        }
    }

    /** A power from the logits at {@code off}, among the three powers only. */
    private static int choose(float[] logits, int off, Random rng, boolean draw) {
        double[] w = new double[3];
        double max = Double.NEGATIVE_INFINITY;
        for (int a = 0; a < w.length; a++) {
            max = Math.max(max, logits[off + Vocab.WORLD.id((char) ('0' + a))]);
        }
        double sum = 0;
        int best = 0;
        for (int a = 0; a < w.length; a++) {
            double l = logits[off + Vocab.WORLD.id((char) ('0' + a))];
            w[a] = Math.exp(l - max);
            sum += w[a];
            if (l > logits[off + Vocab.WORLD.id((char) ('0' + best))]) {
                best = a;
            }
        }
        if (!draw) {
            return best;
        }
        double r = rng.nextDouble() * sum;
        for (int a = 0; a < w.length; a++) {
            r -= w[a];
            if (r <= 0) {
                return a;
            }
        }
        return w.length - 1;
    }

    /** Plays {@code n} melts to the end, the ore of each from {@code ore}. */
    private static List<Melt> play(CompiledGraph model, int n, IntUnaryOperator ore, Random rng, boolean draw) {
        List<Melt> melts = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            melts.add(new Melt(ore.applyAsInt(i)));
        }
        for (int k = 0; k < LOOKS; k++) {
            look(model, melts, rng, draw);
        }
        return melts;
    }

    /** One melt of a random ore, played with the model's likeliest choices, for the Core's Try tab. */
    public static Melt demo(CompiledGraph model, Random rng) {
        return play(model, 1, i -> rng.nextInt(ORES.length()), rng, false).getFirst();
    }

    /** How likely the model makes each power at a melt's first look. */
    public static List<Float> first(CompiledGraph model) {
        int t = Vocab.CONTEXT;
        int[] in = new int[t];
        Arrays.fill(in, Vocab.WORLD.id('\n'));
        in[1] = Vocab.WORLD.id(ORES.charAt(1));
        in[2] = Vocab.WORLD.id(new Melt(1).band());
        Tensor logits = model.forward(null, new Tokens(in, 1, t), null);
        int v = logits.dim(-1);
        double[] e = new double[3];
        double sum = 0;
        for (int a = 0; a < e.length; a++) {
            e[a] = Math.exp(logits.data[2 * v + Vocab.WORLD.id((char) ('0' + a))]);
            sum += e[a];
        }
        List<Float> out = new ArrayList<>();
        for (double x : e) {
            out.add((float) (x / sum));
        }
        return out;
    }

    /** The likeliest power after {@code prompt}, a melt so far as {@link #prompt} writes it, for a Smelter's controller. */
    public static int act(CompiledGraph model, String prompt) {
        int t = Vocab.CONTEXT;
        int[] in = new int[t];
        Arrays.fill(in, Vocab.WORLD.id('\n'));
        String p = prompt;
        p = p.substring(Math.max(0, p.length() - t));
        for (int k = 0; k < p.length(); k++) {
            in[k] = Vocab.WORLD.id(p.charAt(k));
        }
        Tensor logits = model.forward(null, new Tokens(in, 1, t), null);
        return choose(logits.data, (p.length() - 1) * logits.dim(-1), null, false);
    }

    /**
     * Melts played with the model's own choices, each choice weighted by how much better the melt did after it than
     * after other choices at the same look and temperature: {@code exp(advantage / BETA)} (advantage-weighted
     * regression). The weights stay positive, so better choices are learned from more and worse ones less.
     */
    @Override
    public Batch batch(Random rng, int size, boolean test) {
        CompiledGraph model = policy;
        int t = context;
        int[] in = new int[size * t];
        int[] tg = new int[size * t];
        float[] w = new float[size * t];
        Arrays.fill(in, Vocab.WORLD.id('\n'));
        Arrays.fill(tg, Vocab.WORLD.id('\n'));
        if (model == null || test) {
            // no model to play with: nothing to learn from yet
            return new Batch(new Tokens(in, size, t), tg, w, 1);
        }
        List<Melt> melts = play(model, size, i -> rng.nextInt(ORES.length()), rng, true);
        // what followed each choice: the looks in the band from that look on, the nearer ones counting more
        double[][] after = new double[size][LOOKS];
        double[][] sum = new double[LOOKS][8];
        int[][] count = new int[LOOKS][8];
        double[] lookSum = new double[LOOKS];
        for (int b = 0; b < size; b++) {
            Melt m = melts.get(b);
            double got = 0;
            for (int k = LOOKS - 1; k >= 0; k--) {
                got = (m.in[k] ? 1 : 0) + DISCOUNT * got;
                after[b][k] = got;
                int band = m.band(k);
                sum[k][band] += got;
                count[k][band]++;
                lookSum[k] += got;
            }
        }
        for (int b = 0; b < size; b++) {
            Melt m = melts.get(b);
            String text = "\n" + m.line;
            for (int k = 0; k + 1 < text.length() && k < t; k++) {
                in[b * t + k] = Vocab.WORLD.id(text.charAt(k));
                tg[b * t + k] = Vocab.WORLD.id(text.charAt(k + 1));
            }
            for (int look = 0; look < LOOKS; look++) {
                int band = m.band(look);
                // against what followed other choices at the same look and temperature, or at the same look alone
                double baseline = count[look][band] >= 3 ? sum[look][band] / count[look][band] : lookSum[look] / size;
                double advantage = after[b][look] - baseline;
                // the text is a newline, the ore, then a band and a choice a look: look k's band is at 2 + 2k
                w[b * t + 2 + 2 * look] = (float) Math.min(MOST_WEIGHT, Math.exp(advantage / BETA));
            }
        }
        return new Batch(new Tokens(in, size, t), tg, w, 1);
    }

    /**
     * The share of melts held, as a Smelter pays for them, of the ore the model holds worst: melts of each ore alike,
     * played with its likeliest choices. The share missed is the loss.
     */
    @Override
    public double[] measure(CompiledGraph model) {
        List<Melt> melts = play(model, TEST_MELTS, i -> i % ORES.length(), new Random(4242), false);
        double worst = 1;
        for (int ore = 0; ore < ORES.length(); ore++) {
            int o = ore;
            long held = melts.stream().filter(m -> m.ore == o && m.held()).count();
            worst = Math.min(worst, held / (double) (TEST_MELTS / ORES.length()));
        }
        return new double[] {1 - worst, worst};
    }
}
