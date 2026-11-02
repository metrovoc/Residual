package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Decoder;
import io.github.metrovoc.residual.tensor.AdamW;
import io.github.metrovoc.residual.tensor.Ops;
import io.github.metrovoc.residual.tensor.Tape;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Tuning by someone's choices: pairs of answers to the same prompt, one chosen over the other. {@link Direct} moves a model so that it
 * makes the chosen answer more likely against the rejected one than its old self did; {@link Judge} teaches a model to score answers so
 * that the chosen one scores higher, and that judge can then tune another, or pick among attempts.
 */
public final class Preferences {
    /** How a judge says what it thinks of an answer: its score is how much it would rather write the first of these next than the second. */
    public static final char BETTER = '!', WORSE = '?';
    /** Pairs in a step. */
    private static final int PAIRS = 16;

    private Preferences() {
    }

    /** One choice: for {@code prompt}, {@code chosen} and not {@code rejected}. */
    public record Pair(String prompt, String chosen, String rejected) {
    }

    /** {@code prompt} and {@code answer} as a model reads them, ending the line. */
    static int[] spelled(Vocab vocab, String prompt, String answer) {
        return vocab.spell(prompt + answer + "\n");
    }

    /**
     * Preference tuning directly on the choices: the loss is {@code -log σ(β(Δchosen − Δrejected))}, each Δ being how much more likely the model
     * makes that answer than the model it started from. The further it has come on a pair, the less that pair pulls.
     */
    public static final class Direct implements Tuning.Method {
        public static final float BETA = 0.5f;
        private final CompiledGraph model, start;
        private final List<Pair> pairs;
        private final AdamW optimizer;
        private final Random rng;
        private final float rate;
        private int step;

        public Direct(CompiledGraph model, List<Pair> pairs, float rate, long seed) {
            this.model = model;
            this.start = Tuning.copy(model);
            this.pairs = pairs;
            this.optimizer = new AdamW(model.parameters());
            this.rng = new Random(seed);
            this.rate = rate;
        }

        @Override
        public Tuning.Report step() {
            Vocab vocab = model.env.tokenizer();
            List<Pair> batch = Tuning.draw(pairs, PAIRS, rng);
            List<int[]> seqs = new ArrayList<>();
            int[] from = new int[batch.size() * 2];
            for (int i = 0; i < batch.size(); i++) {
                Pair p = batch.get(i);
                from[2 * i] = from[2 * i + 1] = vocab.spell(p.prompt()).length;
                seqs.add(spelled(vocab, p.prompt(), p.chosen()));
                seqs.add(spelled(vocab, p.prompt(), p.rejected()));
            }
            Tuning.Layout layout = new Tuning.Layout(seqs, model.env.context());
            Tensor before = start.forward(null, layout.tokens, null);
            Tape tape = new Tape();
            Tensor logits = model.forward(tape, layout.tokens, null);
            float[] w = new float[layout.targets.length];
            double loss = 0;
            int agree = 0;
            for (int i = 0; i < batch.size(); i++) {
                double chosen = layout.logProb(logits, 2 * i, from[2 * i]) - layout.logProb(before, 2 * i, from[2 * i]);
                double rejected = layout.logProb(logits, 2 * i + 1, from[2 * i]) - layout.logProb(before, 2 * i + 1, from[2 * i]);
                double z = BETA * (chosen - rejected);
                double pull = BETA / (1 + Math.exp(z));
                loss += Math.log1p(Math.exp(-z));
                layout.mark(w, 2 * i, from[2 * i], (float) pull);
                layout.mark(w, 2 * i + 1, from[2 * i], (float) -pull);
                agree += layout.logProb(logits, 2 * i, from[2 * i]) > layout.logProb(logits, 2 * i + 1, from[2 * i]) ? 1 : 0;
            }
            Tuning.apply(optimizer, tape, Tuning.scored(tape, logits, layout, w, batch.size()), rate, step++);
            Pair shown = batch.getFirst();
            Programs.Attempt wrote = Programs.write(new Decoder(model, Decoder.floats(model)), shown.prompt(), 1, 0f, rng).getFirst();
            return new Tuning.Report(agree / (double) batch.size(), Double.NaN, (float) (loss / batch.size()), shown.prompt(), wrote.text(), new boolean[0]);
        }
    }

    /** A judge's score for a sequence ending at {@code row}. */
    public static double score(Tensor logits, int row, Vocab vocab) {
        int v = logits.dim(-1);
        return logits.data[row * v + vocab.id(BETTER)] - logits.data[row * v + vocab.id(WORSE)];
    }

    /** What the judge {@code judge} makes of {@code answer} to {@code prompt}. */
    public static double judged(CompiledGraph judge, String prompt, String answer) {
        int[] s = spelled(judge.env.tokenizer(), prompt, answer);
        int n = Math.min(s.length, judge.env.context());
        int[] in = Arrays.copyOf(s, n);
        Tensor logits = judge.forward(null, new Tokens(in, 1, n), null);
        return score(logits, n - 1, judge.env.tokenizer());
    }

    /**
     * A judge learned from the choices: the chance that it prefers the chosen answer is {@code σ(score(chosen) − score(rejected))}, and that
     * is what it practises on. How far it can be trusted is how far the choices went.
     */
    public static final class Judge implements Tuning.Method {
        private final CompiledGraph model;
        private final List<Pair> pairs;
        private final AdamW optimizer;
        private final Random rng;
        private final float rate;
        private int step;

        public Judge(CompiledGraph model, List<Pair> pairs, float rate, long seed) {
            this.model = model;
            this.pairs = pairs;
            this.optimizer = new AdamW(model.parameters());
            this.rng = new Random(seed);
            this.rate = rate;
        }

        @Override
        public Tuning.Report step() {
            Vocab vocab = model.env.tokenizer();
            List<Pair> batch = Tuning.draw(pairs, PAIRS, rng);
            int n = batch.size();
            List<int[]> seqs = new ArrayList<>();
            for (Pair p : batch) {
                // what is scored is the end of the answer: nothing after it to predict
                int[] c = spelled(vocab, p.prompt(), p.chosen()), r = spelled(vocab, p.prompt(), p.rejected());
                seqs.add(Arrays.copyOf(c, c.length + 1));
                seqs.add(Arrays.copyOf(r, r.length + 1));
            }
            Tuning.Layout layout = new Tuning.Layout(seqs, model.env.context());
            int[] better = new int[n], worse = new int[n];
            for (int i = 0; i < n; i++) {
                better[i] = layout.lastRow(2 * i);
                worse[i] = layout.lastRow(2 * i + 1);
            }
            Tape tape = new Tape();
            Tensor logits = model.forward(tape, layout.tokens, null);
            Tensor loss = Ops.preferenceLoss(tape, logits, better, worse, vocab.id(BETTER), vocab.id(WORSE));
            int agree = 0;
            for (int i = 0; i < n; i++) {
                agree += score(logits, better[i], vocab) > score(logits, worse[i], vocab) ? 1 : 0;
            }
            Tuning.apply(optimizer, tape, loss, rate, step++);
            return new Tuning.Report(agree / (double) n, Double.NaN, loss.item(), batch.getFirst().prompt(), batch.getFirst().chosen(), new boolean[0]);
        }
    }

    /**
     * Tuning against a judge: a model writes for each prompt, the judge scores what it wrote, and proximal policy steps raise what scored above the
     * rest. How far each token may move in a step is clipped, and each is penalised by how far it has gone from where the model started, so
     * the less that holds it, the more it will write whatever fools the judge.
     */
    public static final class Ppo implements Tuning.Method {
        public static final int PROMPTS = 12, ATTEMPTS = 6, EPOCHS = 2;
        public static final double CLIP = 0.2;
        private final CompiledGraph model, start, judge;
        private final AdamW optimizer;
        private final Random rng;
        private final float rate, keep;
        private final Decoder decoder;
        private int step;

        /** @param keep how much each nat a token has moved from where it started counts against it */
        public Ppo(CompiledGraph model, CompiledGraph judge, float rate, float keep, long seed) {
            this.model = model;
            this.start = Tuning.copy(model);
            this.judge = judge;
            this.optimizer = new AdamW(model.parameters());
            this.rng = new Random(seed);
            this.rate = rate;
            this.keep = keep;
            this.decoder = new Decoder(model, Decoder.floats(model));
        }

        @Override
        public Tuning.Report step() {
            Vocab vocab = model.env.tokenizer();
            List<int[]> seqs = new ArrayList<>();
            List<Integer> froms = new ArrayList<>();
            List<Double> advantages = new ArrayList<>();
            double rewardSum = 0;
            int truth = 0, written = 0;
            String said = "", wrote = "";
            boolean[] marks = new boolean[ATTEMPTS];
            for (int o = 0; o < PROMPTS; o++) {
                Orders.Example ex = Orders.example(rng, false);
                String prompt = Orders.prompt(ex.said());
                int given = vocab.spell(prompt).length;
                List<Programs.Attempt> attempts = Programs.write(decoder, prompt, ATTEMPTS, 1f, rng);
                double[] r = new double[ATTEMPTS];
                double mean = 0;
                for (int a = 0; a < ATTEMPTS; a++) {
                    r[a] = judged(judge, prompt, attempts.get(a).text());
                    mean += r[a] / ATTEMPTS;
                    boolean right = Programs.passes(Programs.Checker.RESULT, attempts.get(a).text(), ex.meant());
                    truth += right ? 1 : 0;
                    if (o == 0) {
                        marks[a] = right;
                    }
                }
                rewardSum += mean;
                if (o == 0) {
                    said = ex.said();
                    wrote = attempts.getFirst().text();
                }
                for (int a = 0; a < ATTEMPTS; a++) {
                    int[] t = attempts.get(a).tokens();
                    int[] seq = new int[given + t.length];
                    System.arraycopy(vocab.spell(prompt), 0, seq, 0, given);
                    System.arraycopy(t, 0, seq, given, t.length);
                    seqs.add(seq);
                    froms.add(given);
                    advantages.add(r[a] - mean);
                    written += t.length;
                }
            }
            Tuning.Layout layout = new Tuning.Layout(seqs, model.env.context());
            Tensor old = model.forward(null, layout.tokens, null);
            Tensor reference = start.forward(null, layout.tokens, null);
            int v = old.dim(-1);
            float loss = 0;
            for (int epoch = 0; epoch < EPOCHS; epoch++) {
                Tape tape = new Tape();
                Tensor logits = model.forward(tape, layout.tokens, null);
                float[] w = new float[layout.targets.length];
                for (int i = 0; i < seqs.size(); i++) {
                    int[] s = seqs.get(i);
                    for (int k = Math.max(0, froms.get(i) - 1); k < layout.time && k + 1 < s.length; k++) {
                        int row = i * layout.time + k;
                        double now = Tuning.logProb(logits.data, row * v, v, s[k + 1]);
                        double was = Tuning.logProb(old.data, row * v, v, s[k + 1]);
                        double from = Tuning.logProb(reference.data, row * v, v, s[k + 1]);
                        double ratio = Math.exp(now - was);
                        double advantage = advantages.get(i) - keep * (was - from);
                        // the clipped objective is min(ratio A, clip(ratio) A): past the clip a token no longer pulls
                        boolean clipped = advantage > 0 ? ratio > 1 + CLIP : ratio < 1 - CLIP;
                        w[row] = clipped ? 0f : (float) (advantage * ratio);
                    }
                }
                Tensor l = Tuning.scored(tape, logits, layout, w, Math.max(1, written));
                loss = l.item();
                Tuning.apply(optimizer, tape, l, rate, step);
            }
            step++;
            return new Tuning.Report(rewardSum / PROMPTS, truth / (double) (PROMPTS * ATTEMPTS), loss, said, wrote, marks);
        }
    }
}
