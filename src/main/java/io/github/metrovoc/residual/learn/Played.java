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
 * A task practised by playing: episodes of {@link #LOOKS} looks, at each of which the model sees a letter for the
 * state, {@code a} to {@code h}, and chooses a power, {@code 0}, {@code 1} or {@code 2}. A look counts when it ends in
 * the band, {@code d} or {@code e}, and an episode is held when {@link #HELD} of its scored looks do. Its line starts
 * with a letter of its own, then a state letter and the power chosen for each look.
 *
 * <p>Practice is reinforcement learning at small scale: episodes are played with the model's own choices, and it learns
 * from each choice by how much better the episode did after it than after other choices made at the same look and state
 * (advantage-weighted regression). The test plays episodes of each kind alike with its likeliest choices and scores the
 * share held of the kind it holds worst.
 */
public abstract class Played extends Task {
    public static final int LOOKS = 24;
    /** Looks an episode has to settle before it is scored. */
    public static final int SETTLE = 2;
    /** An episode is held when at least this share of its scored looks end in the band. */
    public static final double HELD = 0.9;
    /** Episodes a test plays, as many of each kind. */
    private static final int TEST_EPISODES = 128;
    /** How sharply advantage-weighted regression favours a better choice, in looks held, and its largest weight. */
    private static final double BETA = 0.5, MOST_WEIGHT = 20;
    /** How much a look in the band counts for each look further on, and how often practice plays a power at random. */
    private static final double DISCOUNT = 0.7, EXPLORE = 0.2;

    /** The model being practised, which plays its own episodes. */
    @Nullable
    private volatile CompiledGraph policy;

    protected Played() {
        super(Vocab.WORLD, Vocab.CONTEXT, Metric.ACCURACY, 0.9);
    }

    public void policy(CompiledGraph model) {
        policy = model;
    }

    /** How many kinds of episode there are. */
    protected abstract int kinds();

    /** A fresh episode of {@code kind}, its start drawn from {@code rng} if it has one to draw. */
    protected abstract Episode episode(int kind, Random rng);

    /** One episode: its line so far, how it moves under a power, and which looks it held. */
    public abstract static class Episode {
        public final int kind;
        public final StringBuilder line = new StringBuilder();
        private int look;
        private int looksIn;
        /** What practice made of each look: 1 for one that ended in the band, less for one that came near. */
        private final double[] reward = new double[LOOKS];

        protected Episode(int kind, char first) {
            this.kind = kind;
            line.append(first);
        }

        /** The state's letter now. */
        public abstract char band();

        /**
         * Moves on one look under {@code power}, the {@code look}th; 1 if it ends in the band, otherwise what practice
         * should make of where it ended, 0 or a little more for coming near.
         */
        protected abstract double move(int power, Random rng, int look);

        /** The state at look {@code k} so far, 0 to 7. */
        public int band(int k) {
            return line.charAt(1 + 2 * k) - 'a';
        }

        public boolean over() {
            return look >= LOOKS;
        }

        /** Sets the power at this look and lets the episode move on to the next. */
        public void act(int power, Random rng) {
            line.append(band()).append((char) ('0' + power));
            reward[look] = move(power, rng, look);
            if (look >= SETTLE && reward[look] >= 1) {
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

    /** What a model sees before it chooses: the episode so far, then the state now. */
    public static String prompt(Episode episode) {
        return "\n" + episode.line + episode.band();
    }

    /**
     * Plays one look of every episode at once: the model reads each prompt and chooses a power, the likeliest one or one
     * drawn from its probabilities.
     */
    private static void look(CompiledGraph model, List<? extends Episode> episodes, Random rng, boolean draw) {
        int n = episodes.size();
        // every episode is at the same look, so its prompt is as long as the others: the model reads only that far
        int t = Math.min(Vocab.CONTEXT, prompt(episodes.getFirst()).length());
        int[] in = new int[n * t];
        for (int i = 0; i < n; i++) {
            String p = prompt(episodes.get(i));
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
            episodes.get(i).act(power, rng);
        }
    }

    /** A power from the logits at {@code off}, among the three powers only. */
    private static int choose(float[] logits, int off, @Nullable Random rng, boolean draw) {
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

    /** Plays {@code n} episodes to the end, the kind of each from {@code kind}. */
    private List<Episode> play(CompiledGraph model, int n, IntUnaryOperator kind, Random rng, boolean draw) {
        List<Episode> episodes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            episodes.add(episode(kind.applyAsInt(i), rng));
        }
        for (int k = 0; k < LOOKS; k++) {
            look(model, episodes, rng, draw);
        }
        return episodes;
    }

    /** One episode of a random kind, played with the model's likeliest choices, for the Core's Try tab. */
    public Episode demo(CompiledGraph model, Random rng) {
        return play(model, 1, i -> rng.nextInt(kinds()), rng, false).getFirst();
    }

    /** How likely the model makes each power at the first look of a fresh episode of {@code kind}. */
    public List<Float> first(CompiledGraph model, int kind) {
        Episode fresh = episode(kind, new Random(0));
        String p = prompt(fresh);
        int t = Vocab.CONTEXT;
        int[] in = new int[t];
        Arrays.fill(in, Vocab.WORLD.id('\n'));
        for (int k = 0; k < p.length(); k++) {
            in[k] = Vocab.WORLD.id(p.charAt(k));
        }
        Tensor logits = model.forward(null, new Tokens(in, 1, t), null);
        int v = logits.dim(-1), at = (p.length() - 1) * v;
        double[] e = new double[3];
        double sum = 0;
        for (int a = 0; a < e.length; a++) {
            e[a] = Math.exp(logits.data[at + Vocab.WORLD.id((char) ('0' + a))]);
            sum += e[a];
        }
        List<Float> out = new ArrayList<>();
        for (double x : e) {
            out.add((float) (x / sum));
        }
        return out;
    }

    /** The likeliest power after {@code prompt}, an episode so far as {@link #prompt} writes it, for a machine to act on. */
    public static int act(CompiledGraph model, String prompt) {
        int t = Vocab.CONTEXT;
        int[] in = new int[t];
        Arrays.fill(in, Vocab.WORLD.id('\n'));
        String p = prompt.substring(Math.max(0, prompt.length() - t));
        for (int k = 0; k < p.length(); k++) {
            in[k] = Vocab.WORLD.id(p.charAt(k));
        }
        Tensor logits = model.forward(null, new Tokens(in, 1, t), null);
        return choose(logits.data, (p.length() - 1) * logits.dim(-1), null, false);
    }

    /**
     * Episodes played with the model's own choices, each choice weighted by how much better the episode did after it
     * than after other choices at the same look and state: {@code exp(advantage / BETA)} (advantage-weighted
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
        List<Episode> episodes = play(model, size, i -> rng.nextInt(kinds()), rng, true);
        // what followed each choice: the looks in the band from that look on, the nearer ones counting more
        double[][] after = new double[size][LOOKS];
        double[][] sum = new double[LOOKS][8];
        int[][] count = new int[LOOKS][8];
        double[] lookSum = new double[LOOKS];
        for (int b = 0; b < size; b++) {
            Episode e = episodes.get(b);
            double got = 0;
            for (int k = LOOKS - 1; k >= 0; k--) {
                got = e.reward[k] + DISCOUNT * got;
                after[b][k] = got;
                int band = e.band(k);
                sum[k][band] += got;
                count[k][band]++;
                lookSum[k] += got;
            }
        }
        for (int b = 0; b < size; b++) {
            Episode e = episodes.get(b);
            String text = "\n" + e.line;
            for (int k = 0; k + 1 < text.length() && k < t; k++) {
                in[b * t + k] = Vocab.WORLD.id(text.charAt(k));
                tg[b * t + k] = Vocab.WORLD.id(text.charAt(k + 1));
            }
            for (int look = 0; look < LOOKS; look++) {
                int band = e.band(look);
                // against what followed other choices at the same look and state, or at the same look alone
                double baseline = count[look][band] >= 3 ? sum[look][band] / count[look][band] : lookSum[look] / size;
                double advantage = after[b][look] - baseline;
                // the text is a newline, the episode's letter, then a state and a choice a look: look k's state is at 2 + 2k
                w[b * t + 2 + 2 * look] = (float) Math.min(MOST_WEIGHT, Math.exp(advantage / BETA));
            }
        }
        return new Batch(new Tokens(in, size, t), tg, w, 1);
    }

    /**
     * The share of episodes held, of the kind the model holds worst: episodes of each kind alike, played with its
     * likeliest choices. The share missed is the loss.
     */
    @Override
    public double[] measure(CompiledGraph model) {
        List<Episode> episodes = play(model, TEST_EPISODES, i -> i % kinds(), new Random(4242), false);
        double worst = 1;
        for (int kind = 0; kind < kinds(); kind++) {
            int k = kind;
            long held = episodes.stream().filter(e -> e.kind == k && e.held()).count();
            worst = Math.min(worst, held / (double) (TEST_EPISODES / kinds()));
        }
        return new double[] {1 - worst, worst};
    }
}
