package io.github.metrovoc.residual.notes;

import io.github.metrovoc.residual.learn.Trainer;
import io.github.metrovoc.residual.tensor.AdamW;
import io.github.metrovoc.residual.tensor.Ops;
import io.github.metrovoc.residual.tensor.Tape;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.List;

/**
 * What the field notes' live widgets compute, on the engine models run on: the softmax a sampler draws from, a head of
 * attention, and a table of next-character scores trained the way a Core trains. The website does the same arithmetic in
 * {@code src/website/live-math.js}, and {@code LiveMathTest} holds the two to the same numbers.
 */
public final class LiveMath {
    private LiveMath() {
    }

    /** Scores to chances at a temperature, as a sampler does it. */
    public static float[] softmax(float[] scores, float temperature) {
        float t = Math.max(temperature, 1e-3f);
        float[] x = scores.clone();
        for (int i = 0; i < x.length; i++) {
            x[i] /= t;
        }
        return Ops.softmax(null, Tensor.of(new int[]{x.length}, x)).data;
    }

    /** Each token's scores for every token, its weights after the softmax, and what it reads: all [T, T], then [T, d]. */
    public record Attention(float[] scores, float[] weights, float[] out) {
    }

    /**
     * One head of attention over the vectors {@code x} [T, d], with queries, keys and values the vectors themselves:
     * {@code q @ k.T / sqrt(d)}, the mask, the softmax, and the weights times the values.
     */
    public static Attention attention(float[] x, int d, boolean causal) {
        int t = x.length / d;
        Tensor vectors = Tensor.of(new int[]{t, d}, x.clone());
        Tensor scores = Ops.scale(null, Ops.matmul(null, vectors, vectors, true), (float) (1.0 / Math.sqrt(d)));
        Tensor weights = Ops.softmax(null, causal ? Ops.causalMask(null, scores) : scores);
        return new Attention(scores.data, weights.data, Ops.matmul(null, weights, vectors, false).data);
    }

    /**
     * The smallest model there is, a table of what comes after each character, learning from one short text and tested on
     * another, step by step as a Core trains: the whole text is one batch, so every run is the same run.
     */
    public static final class Curve {
        private final Tensor table;
        private final AdamW optimizer;
        private final Tokens input, testInput;
        private final int[] targets, testTargets;
        private final float learningRate;
        private int step;

        public Curve(String practice, String test, float learningRate) {
            String alphabet = alphabet(practice + test);
            int v = alphabet.length();
            table = Tensor.zeros(true, v, v);
            optimizer = new AdamW(List.of(table));
            input = tokens(practice, alphabet);
            testInput = tokens(test, alphabet);
            targets = targets(practice, alphabet);
            testTargets = targets(test, alphabet);
            this.learningRate = learningRate;
        }

        /** Every character the two texts use, in order. */
        private static String alphabet(String text) {
            return text.chars().sorted().distinct().collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
        }

        private static Tokens tokens(String text, String alphabet) {
            int[] ids = new int[text.length() - 1];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = alphabet.indexOf(text.charAt(i));
            }
            return new Tokens(ids, 1, ids.length);
        }

        private static int[] targets(String text, String alphabet) {
            int[] ids = new int[text.length() - 1];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = alphabet.indexOf(text.charAt(i + 1));
            }
            return ids;
        }

        /** One step of training: returns the loss on the practice text and on the test before the weights move. */
        public float[] step() {
            float tested = Ops.crossEntropy(null, Ops.embedding(null, testInput, table), testTargets, null).item();
            Tape tape = new Tape();
            Tensor loss = Ops.crossEntropy(tape, Ops.embedding(tape, input, table), targets, null);
            optimizer.zeroGrad();
            tape.backward(loss);
            optimizer.step(Trainer.rate(learningRate, step));
            step++;
            return new float[]{loss.item(), tested};
        }

        public int steps() {
            return step;
        }
    }
}
