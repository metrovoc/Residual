package io.github.metrovoc.residual.scope;

import io.github.metrovoc.residual.graph.Captures;
import io.github.metrovoc.residual.learn.Batch;
import io.github.metrovoc.residual.learn.Vocab;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.minecraft.network.chat.Component;

/**
 * Linear probes: a single weighted sum of what the stream carries, fitted to say whether something is true of the text being read.
 * Where one reads it well, the model holds that fact as a direction in its stream. A probe can always do something with the token at
 * hand, so beside each stands a control, fitted to a meaningless label given to each kind of token: only what a probe reads beyond its
 * control is more than the token itself.
 */
public final class Probes {
    /** Stretches of test a reading takes, the fewest of each side a concept must have, and the most a probe is fitted to. */
    private static final int STRETCHES = 256, FEWEST = 64, MOST = 1500;
    private static final int STEPS = 200;

    /** Something that may be true of a place in a stretch of text. */
    public enum Concept {
        /** The first letter of a word. */
        WORD_START,
        /** Inside quotation marks. */
        QUOTED,
        /** Inside a number. */
        NUMBER,
        /** Inside a word that began with a capital. */
        CAPITAL,
        /** In the first word of a line. */
        LINE_START,
        /** A token already seen in this stretch. */
        SEEN,
        /** A place the test scores what comes next. */
        ASKED;

        public final String id = name().toLowerCase(Locale.ROOT);
    }

    /**
     * One concept.
     *
     * @param share    how much of the text it is true of
     * @param accuracy a balanced probe's share right on held-out stretches, at each point
     * @param control  its control's at each point
     */
    public record Row(Concept concept, float share, float[] accuracy, float[] control) {
    }

    public record Reading(Stream stream, List<Row> rows) {
    }

    private Probes() {
    }

    public static Reading read(Stream stream, Batch sample) {
        Vocab vocab = stream.model.env.tokenizer();
        Captures captures = new Captures();
        captures.only = new HashSet<>();
        for (int p : stream.points) {
            captures.only.add(p);
        }
        stream.model.forward(null, sample.input(), captures);
        int b = sample.input().batch, t = sample.input().time, n = b * t;
        Random rng = new Random(7);
        // a meaningless label for each kind of token, half of them true
        boolean[] kinds = new boolean[vocab.size()];
        for (int i = 0; i < kinds.length; i++) {
            kinds[i] = rng.nextBoolean();
        }
        boolean[] control = new boolean[n];
        for (int i = 0; i < n; i++) {
            control[i] = kinds[sample.input().ids[i]];
        }
        List<Row> rows = new ArrayList<>();
        for (Concept concept : Concept.values()) {
            boolean[] labels = labels(concept, sample, vocab);
            int yes = 0;
            for (boolean l : labels) {
                yes += l ? 1 : 0;
            }
            if (yes < FEWEST || n - yes < FEWEST) {
                continue;
            }
            float[] accuracy = new float[stream.points.length], against = new float[stream.points.length];
            for (int p = 0; p < stream.points.length; p++) {
                float[] x = captures.outputs.get(stream.points[p]).data;
                accuracy[p] = fit(x, stream.width(), labels, t, rng);
                against[p] = fit(x, stream.width(), control, t, rng);
            }
            rows.add(new Row(concept, (float) yes / n, accuracy, against));
        }
        return new Reading(stream, rows);
    }

    /** How many stretches a reading should be given. */
    public static int stretches() {
        return STRETCHES;
    }

    /** Whether {@code concept} is true at each token of each stretch. */
    static boolean[] labels(Concept concept, Batch sample, Vocab vocab) {
        int b = sample.input().batch, t = sample.input().time;
        boolean[] out = new boolean[b * t];
        for (int s = 0; s < b; s++) {
            boolean quoted = false, capital = false, lineStart = true;
            char before = '\n';
            for (int i = 0; i < t; i++) {
                int id = sample.input().ids[s * t + i];
                String piece = vocab.piece(id);
                boolean label = false;
                for (int k = 0; k < piece.length(); k++) {
                    char c = piece.charAt(k);
                    boolean letter = Character.isLetter(c), newWord = letter && !Character.isLetter(before);
                    if (newWord) {
                        capital = Character.isUpperCase(c);
                    } else if (!letter) {
                        capital = false;
                    }
                    if (c == '"') {
                        quoted = !quoted;
                    }
                    if (c == '\n') {
                        lineStart = true;
                    } else if (c == ' ' && Character.isLetter(before)) {
                        lineStart = false;
                    }
                    label = switch (concept) {
                        case WORD_START -> newWord;
                        case QUOTED -> quoted;
                        case NUMBER -> Character.isDigit(c);
                        case CAPITAL -> capital;
                        case LINE_START -> lineStart;
                        case SEEN, ASKED -> false;
                    };
                    before = c;
                }
                if (concept == Concept.SEEN) {
                    for (int j = 0; j < i && !label; j++) {
                        label = sample.input().ids[s * t + j] == id;
                    }
                } else if (concept == Concept.ASKED) {
                    label = sample.weights() != null && sample.weights()[s * t + i] != 0;
                }
                out[s * t + i] = label;
            }
        }
        return out;
    }

    /** Fits a probe to as many of each side as there are, at most {@link #MOST}, keeping every fifth stretch out; its balanced share right on those. */
    private static float fit(float[] x, int d, boolean[] labels, int time, Random rng) {
        int n = labels.length;
        int[] order = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        for (int i = n - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1), swap = order[i];
            order[i] = order[j];
            order[j] = swap;
        }
        List<Integer> trainYes = new ArrayList<>(), trainNo = new ArrayList<>(), testYes = new ArrayList<>(), testNo = new ArrayList<>();
        for (int i : order) {
            boolean test = (i / time) % 5 == 0;
            List<Integer> side = labels[i] ? (test ? testYes : trainYes) : (test ? testNo : trainNo);
            if (side.size() < MOST) {
                side.add(i);
            }
        }
        int each = Math.min(trainYes.size(), trainNo.size());
        if (each == 0 || testYes.isEmpty() || testNo.isEmpty()) {
            return 0.5f;
        }
        int[] train = new int[2 * each];
        for (int i = 0; i < each; i++) {
            train[2 * i] = trainYes.get(i);
            train[2 * i + 1] = trainNo.get(i);
        }
        // scaled by each feature's own spread, so one learning rate suits them all
        float[] mean = new float[d], scale = new float[d];
        for (int i : train) {
            for (int j = 0; j < d; j++) {
                mean[j] += x[i * d + j] / train.length;
            }
        }
        for (int i : train) {
            for (int j = 0; j < d; j++) {
                float c = x[i * d + j] - mean[j];
                scale[j] += c * c / train.length;
            }
        }
        for (int j = 0; j < d; j++) {
            scale[j] = (float) (1 / Math.sqrt(scale[j] + 1e-6));
        }
        float[] w = new float[d + 1], m = new float[d + 1], v = new float[d + 1], grad = new float[d + 1];
        float lr = 0.05f;
        for (int step = 1; step <= STEPS; step++) {
            Arrays.fill(grad, 0f);
            for (int i : train) {
                double z = w[d];
                for (int j = 0; j < d; j++) {
                    z += w[j] * (x[i * d + j] - mean[j]) * scale[j];
                }
                float g = (float) (1 / (1 + Math.exp(-z)) - (labels[i] ? 1 : 0)) / train.length;
                for (int j = 0; j < d; j++) {
                    grad[j] += g * (x[i * d + j] - mean[j]) * scale[j];
                }
                grad[d] += g;
            }
            double bc1 = 1 - Math.pow(0.9, step), bc2 = 1 - Math.pow(0.999, step);
            for (int j = 0; j <= d; j++) {
                grad[j] += j < d ? 1e-3f * w[j] : 0;
                m[j] = 0.9f * m[j] + 0.1f * grad[j];
                v[j] = 0.999f * v[j] + 0.001f * grad[j] * grad[j];
                w[j] -= (float) (lr * (m[j] / bc1) / (Math.sqrt(v[j] / bc2) + 1e-8));
            }
        }
        return (rightShare(x, d, w, mean, scale, testYes, true) + rightShare(x, d, w, mean, scale, testNo, false)) / 2;
    }

    private static float rightShare(float[] x, int d, float[] w, float[] mean, float[] scale, List<Integer> rows, boolean yes) {
        int right = 0;
        for (int i : rows) {
            double z = w[d];
            for (int j = 0; j < d; j++) {
                z += w[j] * (x[i * d + j] - mean[j]) * scale[j];
            }
            right += z > 0 == yes ? 1 : 0;
        }
        return (float) right / rows.size();
    }

    /** A row per concept and a column per point: how well a probe reads it there, past its control. */
    public static Picture picture(Reading r) {
        Picture.Builder b = new Picture.Builder();
        int points = r.stream.points.length, labels = 104, cell = Math.min(52, (Picture.W - labels - 8) / Math.max(1, points)), height = 14;
        b.text(4, 3, Component.translatable("residual.scope.probes.title"), Picture.DIM);
        for (int p = 0; p < points; p++) {
            b.text(labels + p * cell + 2, 15, cell - 3, r.stream.name(r.stream.points[p]), Picture.TEXT);
        }
        if (r.rows.isEmpty()) {
            b.text(4, 30, Component.translatable("residual.scope.probes.none"), Picture.DIM);
        }
        for (int i = 0; i < r.rows.size(); i++) {
            Row row = r.rows.get(i);
            int y = 28 + i * height;
            b.text(4, y + 2, labels - 8, Component.translatable("residual.scope.concept." + row.concept.id), Picture.TEXT);
            for (int p = 0; p < points; p++) {
                float past = (row.accuracy[p] - Math.max(0.5f, row.control[p])) * 2;
                b.box(labels + p * cell, y, cell - 1, height - 1, Picture.heat(Math.max(0, past)),
                        Component.translatable("residual.scope.probes.cell", Picture.percent(row.accuracy[p]), Picture.percent(row.control[p]), Picture.percent(row.share)));
                b.text(labels + p * cell + 2, y + 2, Picture.percent(row.accuracy[p]), row.accuracy[p] >= 0.75f ? Picture.TEXT : Picture.DIM);
            }
        }
        return b.build();
    }
}
