package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.tensor.Tokens;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Practice on what mobs really did, line by line, scored only on the actions: the lines the Imitation benchmark holds, or a
 * Mob Log's. A tenth of each is held out, and the test is the share of held-out looks where the model's likeliest action is the
 * one the mob took, every kind of mob counting alike.
 */
public final class Imitating extends Task {
    /** Lines recorded from the game's own mobs: a kind's letter starts each. */
    private static final String RECORDED = "/imitation/lines.txt";
    private static List<String> recorded;

    private final List<String> practice = new ArrayList<>();
    private final List<String> test = new ArrayList<>();

    /** The benchmark's practice and test. */
    Imitating() {
        this(recorded(), Vocab.WORLD);
    }

    /** A Mob Log's lines. */
    public Imitating(String log, Vocab vocab) {
        this(Imitation.lines(log), vocab);
    }

    private Imitating(List<String> lines, Vocab vocab) {
        super(vocab, Vocab.CONTEXT, Metric.ACCURACY, Benchmark.IMITATION.target);
        practice.addAll(Imitation.practised(lines));
        test.addAll(Imitation.heldOut(lines));
    }

    private static synchronized List<String> recorded() {
        if (recorded == null) {
            try (InputStream in = Imitating.class.getResourceAsStream(RECORDED)) {
                recorded = Imitation.lines(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return recorded;
    }

    public boolean isEmpty() {
        return practice.isEmpty();
    }

    @Override
    public Batch batch(Random rng, int size, boolean test) {
        List<String> from = test && !this.test.isEmpty() ? this.test : practice;
        int t = context, pad = vocab.id('\n');
        int[] in = new int[size * t];
        int[] tg = new int[size * t];
        float[] w = new float[size * t];
        Arrays.fill(in, pad);
        Arrays.fill(tg, pad);
        for (int b = 0; b < size; b++) {
            String line = from.get(rng.nextInt(from.size()));
            if (!test) {
                // a fight from any of its looks on: what counts is how its looks go, not where on a line they happen to fall
                int skip = rng.nextInt(Math.max(1, (line.length() - 1) / 2 - Imitation.FEWEST + 1));
                line = line.charAt(0) + line.substring(1 + 2 * skip);
            }
            for (int k = 0; k < Math.min(t, line.length()); k++) {
                in[b * t + k] = vocab.id(line.charAt(k));
                if (k + 1 < line.length()) {
                    tg[b * t + k] = vocab.id(line.charAt(k + 1));
                }
            }
            // a look's state is at 1 + 2k: what comes next is its action
            for (int k = 1; k + 1 < line.length(); k += 2) {
                w[b * t + k] = 1f;
            }
        }
        return new Batch(new Tokens(in, size, t), tg, w);
    }

    /** The capital its first held-out line starts with. */
    char firstKind() {
        return test.getFirst().charAt(0);
    }

    /** The share of held-out looks the model gets right, kind by kind. */
    Map<Character, Double> byKind(CompiledGraph model) {
        Map<Character, List<String>> kinds = new LinkedHashMap<>();
        for (String line : test) {
            kinds.computeIfAbsent(line.charAt(0), k -> new ArrayList<>()).add(line);
        }
        Map<Character, Double> out = new LinkedHashMap<>();
        kinds.forEach((kind, lines) -> {
            long[] score = Imitation.score(model, lines);
            out.put(kind, score[0] == 0 ? 0 : (double) score[1] / score[0]);
        });
        return out;
    }

    @Override
    public double[] measure(CompiledGraph model) {
        double share = byKind(model).values().stream().mapToDouble(d -> d).average().orElse(0);
        return new double[] {1 - share, share};
    }
}
