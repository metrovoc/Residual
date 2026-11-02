package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.agent.Program;
import io.github.metrovoc.residual.agent.Sandbox;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Decoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Programs: whether what a model writes for an order, run in a {@link Sandbox}, does what was meant. Where the Orders benchmark marks a
 * program letter by letter against one way of writing it, this one takes any way that comes to the same thing and none that doesn't, on
 * orders naming things practice never does. It is what an order is for.
 *
 * <p>It is scored by writing: each order once, the likeliest token each time, as an agent's model writes.
 */
public final class Programs extends Task {
    /** Orders a test writes. */
    private static final int TEST_ORDERS = 128;

    public Programs() {
        super(Vocab.WORLD, Vocab.CONTEXT, Metric.ACCURACY, Benchmark.PROGRAMS.target);
    }

    /** How a written program may be checked, from what any text can get away with to what was really meant. */
    public enum Checker {
        /** It reads as a program. */
        PARSES,
        /** It reads as a program, and runs in the sandbox with no step failing. */
        RUNS,
        /** It runs in the sandbox and comes to what was meant. */
        RESULT
    }

    /** The places an order is tried in: things in store or not, the field ripe or not, day or night to come. */
    public static List<Sandbox.Scene> scenes(String meant) {
        Map<String, Integer> stocked = new HashMap<>(), carried = new HashMap<>();
        for (String thing : Sandbox.named(meant)) {
            stocked.put(thing, 32);
            carried.put(thing, 4);
        }
        carried.put("cobblestone", 4);
        Map<String, Integer> dropped = Map.of("bone", 2);
        return List.of(new Sandbox.Scene(stocked, carried, dropped, 0, 0),
                new Sandbox.Scene(stocked, Map.of(), dropped, 10, 20),
                new Sandbox.Scene(Map.of(), carried, dropped, 30, 6));
    }

    /** What {@code checker} makes of {@code written} for an order that meant {@code meant}: true if it passes. */
    public static boolean passes(Checker checker, String written, String meant) {
        Program.Parsed parsed = Program.parse(written);
        if (!parsed.ok() || parsed.program().toString().isEmpty()) {
            return false;
        }
        if (checker == Checker.PARSES) {
            return true;
        }
        String wrote = parsed.program().toString();
        List<Sandbox.Scene> scenes = scenes(meant);
        // run where everything the order names is at hand: a program that does what was meant can't fail there
        if (checker == Checker.RUNS) {
            return !Sandbox.run(wrote, scenes.getFirst()).failed();
        }
        for (Sandbox.Scene scene : scenes) {
            if (!Sandbox.same(wrote, meant, scene)) {
                return false;
            }
        }
        return true;
    }

    /** One attempt at an order: its tokens after the prompt, whether it ended by itself, and what it says. */
    public record Attempt(int[] tokens, boolean ended, String text) {
    }

    /**
     * Writes {@code attempts} programs for {@code prompt}, all at once, drawn from {@code model}'s probabilities at {@code temperature}, or
     * the likeliest token each time if it is 0 and then only one. Stops at the end of a line, at {@link Orders#WRITTEN} characters or the end
     * of the window. Runs no training pass: what it costs is {@link Decoder} passes.
     */
    public static List<Attempt> write(Decoder decoder, String prompt, int attempts, float temperature, Random rng) {
        Vocab vocab = decoder.model.env.tokenizer();
        int v = vocab.size(), newline = vocab.id('\n');
        int[] given = vocab.spell(prompt);
        Decoder.Sequence start = decoder.sequence();
        decoder.pass(List.of(start), given, given.length);
        List<Decoder.Sequence> live = new ArrayList<>();
        List<List<Integer>> written = new ArrayList<>();
        boolean[] ended = new boolean[attempts];
        float[] row = new float[v];
        start.logits(given.length - 1, given.length, row, 0);
        List<Integer> open = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            live.add(i == 0 ? start : start.fork(given.length));
            written.add(new ArrayList<>());
            open.add(i);
        }
        float[][] rows = new float[attempts][];
        for (int i = 0; i < attempts; i++) {
            rows[i] = row;
        }
        int most = Math.min(Orders.WRITTEN + 1, decoder.model.env.context() - given.length);
        for (int k = 0; k < most && !open.isEmpty(); k++) {
            List<Decoder.Sequence> next = new ArrayList<>();
            List<Integer> still = new ArrayList<>();
            int[] ids = new int[open.size()];
            int n = 0;
            for (int i : open) {
                float[] p = Sampler.probs(rows[i], 0, v, temperature <= 0 ? 1f : temperature);
                int token = temperature <= 0 ? Trainer.argmax(p, 0, v) : Sampler.draw(p, rng);
                written.get(i).add(token);
                if (token == newline) {
                    ended[i] = true;
                } else {
                    ids[n++] = token;
                    next.add(live.get(i));
                    still.add(i);
                }
            }
            if (next.isEmpty()) {
                break;
            }
            int[] step = java.util.Arrays.copyOf(ids, n);
            decoder.pass(next, step, 1);
            for (int j = 0; j < n; j++) {
                int i = still.get(j);
                float[] out = new float[v];
                Decoder.Sequence s = live.get(i);
                s.logits(s.length() - 1, s.length(), out, 0);
                rows[i] = out;
            }
            open = still;
        }
        List<Attempt> out = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            List<Integer> w = written.get(i);
            int[] tokens = new int[w.size()];
            StringBuilder text = new StringBuilder();
            for (int k = 0; k < tokens.length; k++) {
                tokens[k] = w.get(k);
                if (tokens[k] != newline) {
                    text.append(vocab.piece(tokens[k]));
                }
            }
            out.add(new Attempt(tokens, ended[i], text.toString()));
        }
        return out;
    }

    /** A plain decoder for {@code model}, or null if it reads ahead. */
    @Nullable
    private static Decoder decoder(CompiledGraph model) {
        return new Decoder(model, Decoder.floats(model));
    }

    /**
     * The share of orders {@code model} writes a program for that comes to what was meant, of {@code orders} drawn from {@code seed}, each
     * written once with the likeliest token each time.
     */
    public static double score(CompiledGraph model, int orders, long seed, Checker checker) {
        Decoder d = decoder(model);
        Random rng = new Random(seed);
        int passed = 0;
        for (int i = 0; i < orders; i++) {
            // writing the likeliest token draws nothing, so these are the same orders every time
            Orders.Example ex = Orders.example(rng, true);
            Attempt written = write(d, Orders.prompt(ex.said()), 1, 0f, rng).getFirst();
            passed += passes(checker, written.text(), ex.meant()) ? 1 : 0;
        }
        return passed / (double) orders;
    }

    /** Practice is the orders themselves, letter by letter. */
    @Override
    public Batch batch(Random rng, int size, boolean test) {
        return Dataset.of(Benchmark.ORDERS).batch(rng, size, test);
    }

    @Override
    public double[] measure(CompiledGraph model) {
        double score = score(model, TEST_ORDERS, 5151, Checker.RESULT);
        return new double[]{1 - score, score};
    }
}
