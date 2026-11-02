package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Decoder;
import io.github.metrovoc.residual.tensor.AdamW;
import io.github.metrovoc.residual.tensor.Tape;
import io.github.metrovoc.residual.tensor.Tensor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Practice by verified reward: a model writes several answers to each question and a checker marks each one; within each question, what did better than
 * the group's mean is made more likely, and what did worse less, by how far it stood from the mean in standard deviations. Nothing says what the right
 * answer is, only whether one passed, so it learns from any way of writing that passes, and from whatever the checker lets through.
 */
public final class Grpo implements Tuning.Method {
    /** Questions a step asks, and answers it writes for each. */
    public static final int QUESTIONS = 16, ATTEMPTS = 8;

    /** One question: how it is shown and put to the model, what the checker passes, and what really is right. */
    public record Asked(String said, String prompt, Predicate<String> checker, Predicate<String> truth) {
    }

    /** Programs for orders, as {@code checker} marks them. */
    public static Function<Random, Asked> programs(Programs.Checker checker) {
        return rng -> {
            Orders.Example ex = Orders.example(rng, false);
            return new Asked(ex.said(), Orders.prompt(ex.said()), wrote -> Programs.passes(checker, wrote, ex.meant()),
                    wrote -> Programs.passes(Programs.Checker.RESULT, wrote, ex.meant()));
        };
    }

    /** Workings of sums, checked step by step. */
    public static Function<Random, Asked> sums() {
        return rng -> {
            Reckoning.Sum sum = Reckoning.sum(rng, false);
            Predicate<String> right = wrote -> Reckoning.verified(sum.said(), wrote);
            return new Asked(sum.said(), Reckoning.prompt(sum), right, right);
        };
    }

    private final CompiledGraph model;
    private final Decoder decoder;
    private final Function<Random, Asked> questions;
    private final AdamW optimizer;
    private final Random rng;
    private final float rate;
    private int step;

    public Grpo(CompiledGraph model, Function<Random, Asked> questions, float rate, long seed) {
        this.model = model;
        this.decoder = new Decoder(model, Decoder.floats(model));
        this.questions = questions;
        this.optimizer = new AdamW(model.parameters());
        this.rng = new Random(seed);
        this.rate = rate;
    }

    public Grpo(CompiledGraph model, Programs.Checker checker, float rate, long seed) {
        this(model, programs(checker), rate, seed);
    }

    /** One step: writes and marks, and moves the model towards what passed. */
    @Override
    public Tuning.Report step() {
        Vocab vocab = model.env.tokenizer();
        List<int[]> sequences = new ArrayList<>();
        List<float[]> weights = new ArrayList<>();
        int written = 0, passedAll = 0, trueAll = 0;
        String said = "", wrote = "";
        boolean[] marks = new boolean[ATTEMPTS];
        for (int o = 0; o < QUESTIONS; o++) {
            Asked asked = questions.apply(rng);
            int[] given = vocab.spell(asked.prompt());
            List<Programs.Attempt> attempts = Programs.write(decoder, asked.prompt(), ATTEMPTS, 1f, rng);
            double[] rewards = new double[ATTEMPTS];
            double mean = 0;
            for (int a = 0; a < ATTEMPTS; a++) {
                boolean pass = asked.checker().test(attempts.get(a).text());
                rewards[a] = pass ? 1 : 0;
                mean += rewards[a] / ATTEMPTS;
                passedAll += pass ? 1 : 0;
                trueAll += asked.truth().test(attempts.get(a).text()) ? 1 : 0;
                if (o == 0) {
                    marks[a] = pass;
                }
            }
            if (o == 0) {
                said = asked.said();
                wrote = attempts.getFirst().text();
            }
            double var = 0;
            for (double r : rewards) {
                var += (r - mean) * (r - mean) / ATTEMPTS;
            }
            if (var == 0) {
                continue;
            }
            double std = Math.sqrt(var);
            for (int a = 0; a < ATTEMPTS; a++) {
                int[] tokens = attempts.get(a).tokens();
                int[] seq = new int[given.length + tokens.length];
                System.arraycopy(given, 0, seq, 0, given.length);
                System.arraycopy(tokens, 0, seq, given.length, tokens.length);
                float[] w = new float[seq.length];
                Arrays.fill(w, given.length, seq.length, (float) ((rewards[a] - mean) / std));
                sequences.add(seq);
                weights.add(w);
                written += tokens.length;
            }
        }
        float loss = 0;
        if (!sequences.isEmpty()) {
            Tuning.Layout layout = new Tuning.Layout(sequences, model.env.context());
            float[] w = new float[layout.targets.length];
            for (int i = 0; i < sequences.size(); i++) {
                float[] mark = weights.get(i);
                for (int k = 0; k < layout.time && k + 1 < mark.length; k++) {
                    w[i * layout.time + k] = mark[k + 1];
                }
            }
            Tape tape = new Tape();
            Tensor logits = model.forward(tape, layout.tokens, null);
            Tensor l = Tuning.scored(tape, logits, layout, w, written);
            loss = l.item();
            Tuning.apply(optimizer, tape, l, rate, step);
        }
        step++;
        double total = QUESTIONS * ATTEMPTS;
        return new Tuning.Report(passedAll / total, trueAll / total, loss, said, wrote, marks);
    }

    /** The same on melts: each melt's reward is the share of its looks that ended where they should, and only the powers it chose are learned from. */
    public static final class Melts implements Tuning.Method {
        public static final int ATTEMPTS = 16;
        private final CompiledGraph model;
        private final Played task;
        private final AdamW optimizer;
        private final Random rng;
        private final float rate;
        private int step;

        public Melts(CompiledGraph model, float rate, long seed) {
            this.model = model;
            this.task = new Precision();
            this.optimizer = new AdamW(model.parameters());
            this.rng = new Random(seed);
            this.rate = rate;
        }

        @Override
        public Tuning.Report step() {
            Played.Rewarded played = task.rewarded(model, ATTEMPTS, rng);
            Tape tape = new Tape();
            Tensor logits = model.forward(tape, played.batch().input(), null);
            Tensor l = io.github.metrovoc.residual.tensor.Ops.scoredCrossEntropy(tape, logits, played.batch().targets(), played.batch().weights(), Math.max(1, played.scored()));
            Tuning.apply(optimizer, tape, l, rate, step++);
            return new Tuning.Report(played.reward(), played.reward(), l.item(), played.line().substring(0, 1), played.line(), played.marks());
        }
    }
}
